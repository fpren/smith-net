package com.guildofsmiths.trademesh.ui.plan

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.guildofsmiths.trademesh.data.JobRepository
import com.guildofsmiths.trademesh.data.JobStorage
import com.guildofsmiths.trademesh.engine.BoundaryEngine
import com.guildofsmiths.trademesh.planner.OnlineResolver
import com.guildofsmiths.trademesh.planner.OnlineResolver.EnhancedJobData
import com.guildofsmiths.trademesh.planner.PlannerController
import com.guildofsmiths.trademesh.planner.types.*
import com.guildofsmiths.trademesh.ui.jobboard.Job as BoardJob
import com.guildofsmiths.trademesh.ui.jobboard.JobStatus as BoardJobStatus
import com.guildofsmiths.trademesh.ui.jobboard.Priority
import com.guildofsmiths.trademesh.ui.jobboard.Material as BoardMaterial
import com.guildofsmiths.trademesh.ui.jobboard.Task as BoardTask
import com.guildofsmiths.trademesh.ui.jobboard.TaskStatus as BoardTaskStatus
import com.guildofsmiths.trademesh.ui.jobboard.CrewMember
import com.guildofsmiths.trademesh.data.TaskStorage
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * PlanViewModel - UI layer for the deterministic Planner
 * 
 * Bridges PlannerController to Compose UI.
 * Handles debounced persistence and state synchronization.
 */
class PlanViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "PlanViewModel"
        private const val DEBOUNCE_MS = 500L
    }

    // ============================================================
    // CONTROLLER
    // ============================================================

    private val controller = PlannerController(application.applicationContext)

    // ============================================================
    // UI STATE
    // ============================================================

    private val _content = MutableStateFlow("")
    val content: StateFlow<String> = _content.asStateFlow()

    private val _state = MutableStateFlow(PlannerStateEnum.EMPTY)
    val state: StateFlow<PlannerStateEnum> = _state.asStateFlow()

    private val _executionItems = MutableStateFlow<List<ExecutionItem>>(emptyList())
    val executionItems: StateFlow<List<ExecutionItem>> = _executionItems.asStateFlow()

    private val _selectedItemIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedItemIds: StateFlow<Set<String>> = _selectedItemIds.asStateFlow()

    private val _lastError = MutableStateFlow<CompileError?>(null)
    val lastError: StateFlow<CompileError?> = _lastError.asStateFlow()

    private val _jobs = MutableStateFlow<List<PlannerJob>>(emptyList())
    val jobs: StateFlow<List<PlannerJob>> = _jobs.asStateFlow()

    // Debounce job for persistence
    private var persistJob: Job? = null

    // ============================================================
    // DERIVED STATE (for backward compatibility)
    // ============================================================

    // For compatibility with existing PlanScreen
    val planText: StateFlow<String> = _content
    
    val isProcessing: StateFlow<Boolean> = MutableStateFlow(false).also { flow ->
        viewModelScope.launch {
            _state.collect { state ->
                (flow as MutableStateFlow).value = state == PlannerStateEnum.COMPILING || 
                                                    state == PlannerStateEnum.TRANSFERRING
            }
        }
    }
    
    val isCompiled: StateFlow<Boolean> = MutableStateFlow(false).also { flow ->
        viewModelScope.launch {
            _state.collect { state ->
                (flow as MutableStateFlow).value = state == PlannerStateEnum.COMPILED
            }
        }
    }
    
    val selectedItems: StateFlow<Set<String>> = _selectedItemIds
    
    val canTransfer: StateFlow<Boolean> = MutableStateFlow(false).also { flow ->
        viewModelScope.launch {
            _state.collect { state ->
                (flow as MutableStateFlow).value = state == PlannerStateEnum.COMPILED && 
                                                    _selectedItemIds.value.isNotEmpty()
            }
        }
    }

    // Empty annotations for backward compatibility
    val annotations: StateFlow<List<com.guildofsmiths.trademesh.ui.Annotation>> = 
        MutableStateFlow(emptyList())

    // ============================================================
    // INITIALIZATION
    // ============================================================

    init {
        loadPersisted()
    }

    private fun loadPersisted() {
        viewModelScope.launch {
            controller.initialize()
            syncStateFromController()
            loadJobs()
        }
    }
    
    /**
     * Load a job from Job Board into the Planner for document generation
     * This converts the BoardJob data into ExecutionItems and sets COMPILED state
     */
    fun loadJobFromBoard(jobId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            Log.i(TAG, "Loading job from Job Board: $jobId")
            
            // Load the job from storage
            val allJobs = JobStorage.loadJobs()
            val job = allJobs.find { it.id == jobId }
            
            if (job == null) {
                Log.w(TAG, "Job not found: $jobId")
                return@launch
            }
            
            // Load tasks for this job
            val tasks = TaskStorage.loadTasks(jobId)
            val now = System.currentTimeMillis()
            
            // Convert job data to ExecutionItems
            val items = mutableListOf<ExecutionItem>()
            var itemIndex = 0
            
            // Add tasks
            tasks.forEach { task ->
                items.add(ExecutionItem(
                    id = "task_${itemIndex}",
                    type = ExecutionItemType.TASK,
                    index = itemIndex++,
                    lineNumber = itemIndex,
                    section = "Tasks",
                    source = "- ${task.title}",
                    parsed = ParsedItemData.Task(task.title),
                    createdAt = now
                ))
            }
            
            // Add materials
            job.materials.forEachIndexed { _, material ->
                items.add(ExecutionItem(
                    id = "material_${itemIndex}",
                    type = ExecutionItemType.MATERIAL,
                    index = itemIndex++,
                    lineNumber = itemIndex,
                    section = "Materials",
                    source = "- ${material.name} | ${material.quantity} ${material.unit}",
                    parsed = ParsedItemData.Material(
                        description = material.name,
                        quantity = material.quantity.toString(),
                        unit = material.unit
                    ),
                    createdAt = now
                ))
            }
            
            // Add crew as labor
            job.crew.forEach { crew ->
                items.add(ExecutionItem(
                    id = "labor_${itemIndex}",
                    type = ExecutionItemType.LABOR,
                    index = itemIndex++,
                    lineNumber = itemIndex,
                    section = "Labor",
                    source = "- ${crew.name} | ${crew.occupation}",
                    parsed = ParsedItemData.Labor(
                        description = "${crew.name} - ${crew.occupation}",
                        hours = "8",
                        role = crew.occupation
                    ),
                    createdAt = now
                ))
            }
            
            // Create enhanced job data for document generation
            val enhancedData = OnlineResolver.EnhancedJobData(
                jobTitle = job.title,
                clientName = job.clientName,
                location = job.location,
                jobType = "Service",
                primaryTrade = "General",
                urgency = "Normal",
                scope = job.description,
                scopeDetails = listOf(job.description),
                tasks = tasks.map { it.title },
                materials = job.materials.map { 
                    OnlineResolver.MaterialItem(it.name, it.quantity.toString(), it.unit, it.unitCost)
                },
                labor = job.crew.map {
                    OnlineResolver.LaborEntry(it.occupation, 8.0, 85.0)
                },
                crewSize = job.crewSize,
                estimatedDays = 1,
                phases = emptyList(),
                estimatedLaborCost = job.crew.size * 8 * 85.0,
                estimatedMaterialCost = job.materials.sumOf { it.totalCost },
                estimatedTotal = (job.crew.size * 8 * 85.0) + job.materials.sumOf { it.totalCost },
                depositRequired = "50%",
                warranty = "1 year workmanship",
                safetyRequirements = listOf("Standard PPE required"),
                codeRequirements = emptyList(),
                permitRequired = false,
                inspectionRequired = false,
                assumptions = emptyList(),
                exclusions = emptyList(),
                notes = job.workLog.map { it.text },
                detectedKeywords = emptyList(),
                tradeClassification = "General",
                researchSources = emptyList()
            )
            
            // Store for document generation
            OnlineResolver.setLastJobData(enhancedData)
            
            // Update state on main thread
            viewModelScope.launch(Dispatchers.Main) {
                _executionItems.value = items
                _selectedItemIds.value = items.map { it.id }.toSet()
                _state.value = PlannerStateEnum.COMPILED
                
                // Generate and display the job summary
                val summary = com.guildofsmiths.trademesh.ui.GOSDocumentGenerator.generateCompileSummary(items)
                _content.value = summary
                
                // #region agent log
                Log.e("DEBUG_DOC", """{"hypothesisId":"H5","location":"PlanViewModel.kt:loadJobFromBoard-complete","message":"Job loaded into planner","data":{"jobId":"$jobId","jobTitle":"${job.title.take(30).replace("\"", "\\\"")}","itemsCount":${items.size},"tasksCount":${items.count { it.type == ExecutionItemType.TASK }},"materialsCount":${items.count { it.type == ExecutionItemType.MATERIAL }},"laborCount":${items.count { it.type == ExecutionItemType.LABOR }},"summaryLength":${summary.length}}}""")
                // #endregion
                
                Log.i(TAG, "Job loaded successfully: ${job.title} with ${items.size} items")
            }
        }
    }

    // ============================================================
    // STATE SYNC
    // ============================================================

    private fun syncStateFromController() {
        val data = controller.getData()
        _content.value = data.content
        _state.value = data.state
        _executionItems.value = data.executionItems
        _selectedItemIds.value = data.selectedItemIds
        _lastError.value = data.lastError
    }

    private suspend fun loadJobs() {
        _jobs.value = controller.getJobs()
    }

    // ============================================================
    // ACTIONS
    // ============================================================

    /**
     * Update plan text content (resets state to DRAFT)
     */
    fun updatePlanText(text: String) {
        controller.onContentChange(text)
        syncStateFromController()
        persistDebounced()
    }
    
    /**
     * Display-only content update - does NOT reset compile state
     * Used for showing compile summary without losing compiled state
     */
    fun displayContent(text: String) {
        _content.value = text
        // Do NOT call controller.onContentChange() - that would reset state
        // Do NOT persist - this is display only
    }

    // Alias for backward compatibility
    fun setContent(text: String) = updatePlanText(text)

    /**
     * Compile the plan (MAIN PATH)
     * 
     * MAIN COMPILE PIPELINE:
     * - LOCAL procedures ONLY
     * - DETERMINISTIC
     * - Schema locked
     * - NO online lookup
     * 
     * AUTO-SCAFFOLD: If user input does not contain the canonical "# PLAN" header,
     * automatically wrap the input into a minimal valid structure before compilation.
     * This transformation is:
     * - Deterministic (simple text operation)
     * - Visible in the canvas (user sees the scaffolded content)
     * - Applied only once per compile attempt
     */
    fun compilePlan() {
        viewModelScope.launch {
            val canCompile = _state.value == PlannerStateEnum.DRAFT && _content.value.isNotEmpty()
            if (!canCompile) return@launch

            Log.i(TAG, "COMPILE (MAIN): Starting local-only compile path")
            
            // AUTO-SCAFFOLD: Check if content needs scaffolding
            val currentContent = _content.value.trim()
            val scaffoldedContent = autoScaffoldIfNeeded(currentContent)
            
            // If scaffolding was applied, update the canvas so user sees it
            if (scaffoldedContent != currentContent) {
                Log.i(TAG, "COMPILE (MAIN): Auto-scaffold applied - wrapping into canonical structure")
                controller.onContentChange(scaffoldedContent)
                syncStateFromController()
                // Brief delay so user can see the transformation
                delay(100)
            }

            _state.value = PlannerStateEnum.COMPILING
            
            // LOCAL COMPILE ONLY - NO online resolver
            val result = controller.compile()
            
            syncStateFromController()
            
            // Persist after compilation
            controller.persist()

            when (result) {
                is CompileResult.Success -> {
                    Log.i(TAG, "COMPILE (MAIN): Success - ${result.items.size} items")
                }
                is CompileResult.Failure -> {
                    Log.w(TAG, "COMPILE (MAIN): Failed - ${result.error.code} - ${result.error.message}")
                }
            }
        }
    }

    /**
     * AUTO-SCAFFOLD: Wrap user input into minimal valid plan structure if needed.
     * 
     * Rules:
     * - If content already starts with "# PLAN", return unchanged
     * - If content has "## Tasks" but no "# PLAN", prepend "# PLAN\n"
     * - Otherwise, wrap entire content as tasks:
     *   # PLAN
     *   ## Tasks
     *   <user content>
     * 
     * This is deterministic and predictable.
     */
    private fun autoScaffoldIfNeeded(content: String): String {
        val trimmed = content.trim()
        if (trimmed.isEmpty()) return trimmed
        
        val lines = trimmed.lines()
        val normalizedFirstLine = lines.firstOrNull()?.trim() ?: ""
        
        // Case 1: Already has # PLAN header - no scaffolding needed
        if (normalizedFirstLine == "# PLAN") {
            return content
        }
        
        // Case 2: Has ## Tasks section but missing # PLAN - just prepend header
        val hasTasksSection = lines.any { it.trim() == "## Tasks" }
        if (hasTasksSection) {
            return "# PLAN\n$content"
        }
        
        // Case 3: Freeform text - wrap as tasks
        // Each non-empty line becomes a task
        return buildString {
            appendLine("# PLAN")
            appendLine("## Tasks")
            append(trimmed)
        }
    }

    // Alias for backward compatibility
    fun compile() = compilePlan()

    /**
     * TEST compile - experimental online-assisted compile path (ISOLATED)
     * 
     * TEST EXECUTION PIPELINE:
     * - Online search ENABLED
     * - Playwright/CLI tools invoked
     * - NO local side effects until compile
     * - Preview/validation output
     * 
     * FLOW:
     * TEST ⧉ →
     *   if online:
     *     run OnlineResolver(inputText) - uses Playwright backend
     *     inject resolver output as visible plaintext scaffold
     *   else:
     *     skip resolver, use local scaffold
     *   then:
     *     call local compiler (same as MAIN path)
     * 
     * RULES:
     * - Resolver output is written into the canvas (user-visible)
     * - Resolver never blocks compilation
     * - Resolver failure silently falls back to local compile
     * - No prices, approvals, or execution flags from resolver
     * - Test output is for PREVIEW ONLY - no unlocks
     * 
     * This is ISOLATED from the MAIN COMPILE path.
     */
    fun testCompile() {
        viewModelScope.launch {
            val canCompile = _state.value == PlannerStateEnum.DRAFT && _content.value.isNotEmpty()
            if (!canCompile) return@launch

            Log.i(TAG, "TEST ⧉: Starting ISOLATED test compile path")
            Log.i(TAG, "TEST ⧉: Online search ENABLED for this path")
            
            // Get current content
            val currentContent = _content.value.trim()
            
            // Check connectivity for online resolution
            val isOnline = BoundaryEngine.isOnline.value
            Log.i(TAG, "TEST ⧉: Connectivity status - online=$isOnline")
            
            // ════════════════════════════════════════════════════════════════
            // STEP 1: ONLINE RESOLUTION (TEST PATH ONLY)
            // This step ONLY runs in TEST path, not in MAIN COMPILE
            // ════════════════════════════════════════════════════════════════
            val resolvedContent = if (isOnline) {
                Log.i(TAG, "TEST ⧉: ONLINE - invoking OnlineResolver (Playwright backend)")
                
                val resolveResult = OnlineResolver.resolve(currentContent)
                
                when (resolveResult) {
                    is OnlineResolver.ResolveResult.Success -> {
                        Log.i(TAG, "TEST ⧉: OnlineResolver SUCCESS - got enhanced scaffold")
                        resolveResult.enhancedText
                    }
                    is OnlineResolver.ResolveResult.Failure -> {
                        // Silent fallback - use local scaffold
                        Log.w(TAG, "TEST ⧉: OnlineResolver FAILED (${resolveResult.reason}) - falling back to local scaffold")
                        autoScaffoldIfNeeded(currentContent)
                    }
                }
            } else {
                // Offline - skip resolver, use local scaffold
                Log.i(TAG, "TEST ⧉: OFFLINE - skipping OnlineResolver, using local scaffold only")
                autoScaffoldIfNeeded(currentContent)
            }
            
            // ════════════════════════════════════════════════════════════════
            // STEP 2: INJECT RESOLVED CONTENT INTO CANVAS (user-visible)
            // The user sees exactly what was resolved/scaffolded
            // ════════════════════════════════════════════════════════════════
            if (resolvedContent != currentContent) {
                Log.i(TAG, "TEST ⧉: Injecting resolved content into canvas (visible to user)")
                controller.onContentChange(resolvedContent)
                syncStateFromController()
                // Brief delay so user can see the transformation
                delay(200)
            }
            
            // ════════════════════════════════════════════════════════════════
            // STEP 3: LOCAL COMPILER (same as MAIN path)
            // After online resolution, we still use the deterministic local compiler
            // ════════════════════════════════════════════════════════════════
            _state.value = PlannerStateEnum.COMPILING
            Log.i(TAG, "TEST ⧉: Running local compiler on resolved content")
            
            val result = controller.compile()
            syncStateFromController()
            
            // Persist after compilation
            controller.persist()

            when (result) {
                is CompileResult.Success -> {
                    Log.i(TAG, "TEST ⧉: SUCCESS - ${result.items.size} items compiled")
                    Log.i(TAG, "TEST ⧉: Output is PREVIEW ONLY - validate before using COMPILE")
                }
                is CompileResult.Failure -> {
                    Log.w(TAG, "TEST ⧉: FAILED - ${result.error.code} - ${result.error.message}")
                }
            }
        }
    }

    /**
     * Toggle selection of an execution item
     */
    fun toggleExecutionItem(itemId: String) {
        if (_state.value != PlannerStateEnum.COMPILED) return
        
        controller.toggleItemSelection(itemId)
        syncStateFromController()
        
        // Update canTransfer
        viewModelScope.launch {
            // Force update of derived state
            _selectedItemIds.value = controller.getSelectedIds()
        }
    }

    // Alias for backward compatibility
    fun toggleSelection(itemId: String) = toggleExecutionItem(itemId)

    /**
     * Select all execution items
     */
    fun selectAllItems() {
        if (_state.value != PlannerStateEnum.COMPILED) return
        
        controller.selectAllItems()
        syncStateFromController()
    }

    // Alias for backward compatibility
    fun selectAll() = selectAllItems()

    /**
     * Deselect all execution items
     */
    fun deselectAllItems() {
        if (_state.value != PlannerStateEnum.COMPILED) return
        
        controller.deselectAllItems()
        syncStateFromController()
    }

    // Alias for backward compatibility
    fun deselectAll() = deselectAllItems()

    /**
     * Transfer selected items to Job Board
     * 
     * FIXED: Creates ONE unified BoardJob with nested tasks and materials,
     * instead of creating separate flat jobs for each ExecutionItem.
     */
    fun transferSelectedItems() {
        viewModelScope.launch {
            val canTransfer = _state.value == PlannerStateEnum.COMPILED && 
                              _selectedItemIds.value.isNotEmpty()
            if (!canTransfer) return@launch

            _state.value = PlannerStateEnum.TRANSFERRING

            val result = controller.transfer()
            syncStateFromController()

            when (result) {
                is TransferResult.Success -> {
                    Log.i(TAG, "Transfer successful: ${result.jobs.size} PlannerJobs received")
                    
                    // Check if we have enhanced job data from TEST ⧉ online research
                    val enhancedJobData = OnlineResolver.getLastJobData()
                    // #region agent log
                    Log.e("DEBUG_H5", """{"hypothesisId":"H5","location":"PlanViewModel.kt:transferSelectedItems","message":"Transfer - checking enhanced data","data":{"hasEnhancedData":${enhancedJobData != null},"plannerJobsCount":${result.jobs.size},"tasksCount":${enhancedJobData?.tasks?.size ?: -1},"materialsCount":${enhancedJobData?.materials?.size ?: -1},"laborCount":${enhancedJobData?.labor?.size ?: -1}}}""")
                    // #endregion
                    
                    val now = System.currentTimeMillis()
                    val unifiedJobId = UUID.randomUUID().toString()
                    
                    // Get source plan hash for linking back
                    val sourcePlanHash = result.jobs.firstOrNull()?.sourcePlanHash ?: ""
                    
                    val unifiedJob: BoardJob
                    val boardTasks: List<BoardTask>
                    
                    if (enhancedJobData != null) {
                        // ════════════════════════════════════════════════════════════
                        // ENHANCED TRANSFER: Use full job data from online research
                        // ════════════════════════════════════════════════════════════
                        Log.i(TAG, "Using enhanced job data from TEST ⧉ research")
                        
                        // Convert materials
                        val boardMaterials = enhancedJobData.materials.map { mat ->
                            BoardMaterial(
                                name = mat.name,
                                notes = "",
                                checked = false,
                                quantity = mat.quantity?.toDoubleOrNull() ?: 1.0,
                                unit = mat.unit ?: "ea",
                                unitCost = mat.estimatedCost ?: 0.0,
                                totalCost = (mat.quantity?.toDoubleOrNull() ?: 1.0) * (mat.estimatedCost ?: 0.0)
                            )
                        }
                        
                        // Build labor estimate for expenses
                        val laborExpenses = enhancedJobData.labor.joinToString("\n") { lab ->
                            "${lab.hours.toInt()}h ${lab.role} @ \$${String.format("%.2f", lab.rate)}/hr"
                        }
                        
                        // Build full description
                        val description = buildString {
                            appendLine(enhancedJobData.scope)
                            appendLine()
                            appendLine("Scope Details:")
                            enhancedJobData.scopeDetails.forEach { detail ->
                                appendLine("• $detail")
                            }
                        }
                        
                        // Determine priority from urgency
                        val priority = when (enhancedJobData.urgency.lowercase()) {
                            "urgent" -> Priority.URGENT
                            "high" -> Priority.HIGH
                            "moderate" -> Priority.MEDIUM
                            "low" -> Priority.LOW
                            else -> Priority.MEDIUM
                        }
                        
                        // Create crew members from labor entries
                        val crewMembers = enhancedJobData.labor.mapIndexed { idx, labor ->
                            CrewMember(
                                name = "${labor.role} #${idx + 1}",
                                occupation = labor.role,
                                task = "" // Will be assigned via task distribution
                            )
                        }
                        
                        // Create unified job with all enhanced data
                        unifiedJob = BoardJob(
                            id = unifiedJobId,
                            title = enhancedJobData.jobTitle,
                            description = description,
                            clientName = enhancedJobData.clientName,
                            location = enhancedJobData.location,
                            status = BoardJobStatus.BACKLOG,
                            priority = priority,
                            createdBy = "planner",
                            createdAt = now,
                            updatedAt = now,
                            materials = boardMaterials,
                            crew = crewMembers,
                            toolsNeeded = enhancedJobData.safetyRequirements.joinToString(", "),
                            expenses = buildString {
                                appendLine("Est. Labor: \$${String.format("%.2f", enhancedJobData.estimatedLaborCost)}")
                                appendLine("Est. Materials: \$${String.format("%.2f", enhancedJobData.estimatedMaterialCost)}")
                                appendLine("Est. Total: \$${String.format("%.2f", enhancedJobData.estimatedTotal)}")
                                appendLine()
                                appendLine("Labor Breakdown:")
                                append(laborExpenses)
                            },
                            crewSize = enhancedJobData.crewSize,
                            estimatedStartDate = now,
                            estimatedEndDate = now + (enhancedJobData.estimatedDays * 24 * 60 * 60 * 1000L),
                            tags = listOfNotNull(
                                "plan:$sourcePlanHash".takeIf { sourcePlanHash.isNotEmpty() },
                                "trade:${enhancedJobData.primaryTrade}",
                                if (enhancedJobData.permitRequired) "permit-required" else null,
                                if (enhancedJobData.inspectionRequired) "inspection-required" else null
                            )
                        )
                        
                        // Create tasks from enhanced data with auto-distribution
                        // Build list of assignable worker IDs based on labor entries
                        val workerIds = if (enhancedJobData.crewSize > 1 && enhancedJobData.labor.isNotEmpty()) {
                            enhancedJobData.labor.mapIndexed { idx, labor ->
                                "crew_${labor.role.lowercase().replace(" ", "_")}_$idx"
                            }
                        } else {
                            emptyList()
                        }
                        
                        boardTasks = enhancedJobData.tasks.mapIndexed { index, taskTitle ->
                            // Auto-distribute tasks round-robin if crew size > 1
                            val assignedTo = if (workerIds.isNotEmpty()) {
                                workerIds[index % workerIds.size]
                            } else {
                                null
                            }
                            
                            BoardTask(
                                id = UUID.randomUUID().toString(),
                                jobId = unifiedJobId,
                                title = taskTitle,
                                description = null,
                                status = BoardTaskStatus.PENDING,
                                assignedTo = assignedTo,
                                createdBy = "planner",
                                createdAt = now,
                                updatedAt = now,
                                order = index
                            )
                        }
                        
                        // Log auto-distribution
                        if (workerIds.isNotEmpty()) {
                            Log.i(TAG, "Auto-distributed ${boardTasks.size} tasks among ${workerIds.size} workers")
                        }
                        
                        // Clear the enhanced job data after use
                        OnlineResolver.clearLastJobData()
                    } else {
                        // ════════════════════════════════════════════════════════════
                        // BASIC TRANSFER: Use data from PlannerJobs (regular COMPILE)
                        // ════════════════════════════════════════════════════════════
                        Log.i(TAG, "Using basic transfer (no enhanced data)")
                        
                        // Separate items by type
                        val taskJobs = result.jobs.filter { it.type == ExecutionItemType.TASK }
                        val materialJobs = result.jobs.filter { it.type == ExecutionItemType.MATERIAL }
                        val laborJobs = result.jobs.filter { it.type == ExecutionItemType.LABOR }
                        
                        // Build job title from first task or default
                        val jobTitle = taskJobs.firstOrNull()?.title?.take(100) ?: "Plan Transfer"
                        
                        // Build description
                        val description = buildString {
                            if (taskJobs.size > 1) {
                                appendLine("Scope: ${taskJobs.size} tasks")
                                taskJobs.take(3).forEach { task ->
                                    appendLine("• ${task.title}")
                                }
                                if (taskJobs.size > 3) {
                                    appendLine("• ... and ${taskJobs.size - 3} more")
                                }
                            } else {
                                append(taskJobs.firstOrNull()?.let { 
                                    (it.details as? JobDetails.Task)?.description 
                                } ?: "Transferred from Plan")
                            }
                        }
                        
                        // Convert materials to BoardMaterial list
                        val boardMaterials = materialJobs.map { matJob ->
                            val details = matJob.details as? JobDetails.Material
                            BoardMaterial(
                                name = matJob.title,
                                notes = details?.description ?: "",
                                checked = false,
                                quantity = details?.quantity?.toDoubleOrNull() ?: 1.0,
                                unit = details?.unit ?: "ea"
                            )
                        }
                        
                        // Calculate labor estimate for expenses field
                        val laborEstimate = laborJobs.mapNotNull { labJob ->
                            val details = labJob.details as? JobDetails.Labor
                            val hours = details?.hours?.toDoubleOrNull() ?: 0.0
                            if (hours > 0) "${details?.hours}h ${details?.role ?: details?.description}" else null
                        }.joinToString(", ")
                        
                        // Create unified BoardJob
                        unifiedJob = BoardJob(
                            id = unifiedJobId,
                            title = jobTitle,
                            description = description,
                            status = BoardJobStatus.BACKLOG,
                            priority = Priority.MEDIUM,
                            createdBy = "planner",
                            createdAt = now,
                            updatedAt = now,
                            materials = boardMaterials,
                            toolsNeeded = "",
                            expenses = if (laborEstimate.isNotEmpty()) "Labor: $laborEstimate" else "",
                            tags = if (sourcePlanHash.isNotEmpty()) listOf("plan:$sourcePlanHash") else emptyList()
                        )
                        
                        // Create tasks from PlannerJobs
                        boardTasks = taskJobs.mapIndexed { index, taskJob ->
                            BoardTask(
                                id = UUID.randomUUID().toString(),
                                jobId = unifiedJobId,
                                title = taskJob.title,
                                description = (taskJob.details as? JobDetails.Task)?.description,
                                status = BoardTaskStatus.PENDING,
                                createdBy = "planner",
                                createdAt = now,
                                updatedAt = now,
                                order = index
                            )
                        }
                    }
                    
                    // Load existing jobs and append the unified job
                    val existingJobs = JobStorage.loadJobs()
                    val allJobs = existingJobs + unifiedJob
                    val saveSuccess = JobStorage.saveJobs(allJobs)
                    
                    // #region agent log
                    Log.e("DEBUG_H5", """{"hypothesisId":"H5","location":"PlanViewModel.kt:transferSelectedItems-save","message":"Saving unified job","data":{"jobId":"$unifiedJobId","jobTitle":"${unifiedJob.title.replace("\"", "'")}","taskCount":${boardTasks.size},"materialCount":${unifiedJob.materials.size},"crewSize":${unifiedJob.crewSize},"hasCrew":${unifiedJob.crew.isNotEmpty()}}}""")
                    // #endregion
                    if (saveSuccess) {
                        Log.i(TAG, "✓ Persisted unified job to JobStorage (total: ${allJobs.size})")
                    } else {
                        Log.e(TAG, "✗ FAILED to persist job to JobStorage!")
                    }
                    
                    // Add to legacy JobRepository for in-memory compatibility
                    JobRepository.addJob(
                        JobRepository.SimpleJob(
                            id = unifiedJobId,
                            title = unifiedJob.title,
                            status = "BACKLOG"
                        )
                    )
                    
                    // Store tasks in TaskStorage (persisted separately, linked by jobId)
                    if (boardTasks.isNotEmpty()) {
                        TaskStorage.saveTasks(unifiedJobId, boardTasks)
                        Log.i(TAG, "✓ Persisted ${boardTasks.size} tasks for job $unifiedJobId")
                    }
                    
                    // Reload jobs
                    loadJobs()
                    
                    // Persist planner state
                    controller.persist()
                }
                is TransferResult.Failure -> {
                    Log.e(TAG, "Transfer failed: ${result.error.code} - ${result.error.message}")
                }
            }
        }
    }

    // Alias for backward compatibility
    fun transferSelected() = transferSelectedItems()

    /**
     * Save draft (debounced)
     */
    fun saveDraft() {
        persistDebounced()
    }

    /**
     * Clear draft and reset state
     */
    fun clearDraft() {
        controller.clear()
        syncStateFromController()
        
        viewModelScope.launch {
            controller.persist()
        }
    }
    
    /**
     * Edit original plan - restores the original plan content for editing.
     * Unlike clearDraft(), this preserves the original plan text so the user
     * can make incremental changes instead of starting over.
     * 
     * Works like AI chat "edit prompt" - allows modifying and recompiling.
     */
    fun editOriginalPlan() {
        // Get the original content from controller (preserved during compilation)
        val originalContent = controller.getData().content
        
        // Reset to draft state but keep the content
        controller.resetToDraft()
        syncStateFromController()
        
        // Restore the content to the UI
        _content.value = originalContent
        
        viewModelScope.launch {
            controller.persist()
        }
        
        Log.i(TAG, "Restored original plan for editing: ${originalContent.length} chars")
    }

    // ============================================================
    // PERSISTENCE
    // ============================================================

    private fun persistDebounced() {
        persistJob?.cancel()
        persistJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            controller.persist()
            Log.d(TAG, "Persisted planner state")
        }
    }

    // ============================================================
    // BACKWARD COMPATIBILITY
    // ============================================================

    /**
     * Convert ExecutionItem to legacy format for UI
     */
    fun getExecutionItemsForUi(): List<com.guildofsmiths.trademesh.ui.ExecutionItem> {
        return _executionItems.value.map { item ->
            com.guildofsmiths.trademesh.ui.ExecutionItem(
                id = item.id,
                title = item.source,
                description = when (val parsed = item.parsed) {
                    is ParsedItemData.Task -> parsed.description
                    is ParsedItemData.Material -> "${parsed.quantity ?: ""} ${parsed.unit ?: ""} ${parsed.description}".trim()
                    is ParsedItemData.Labor -> "${parsed.hours ?: ""}h ${parsed.role ?: parsed.description}".trim()
                },
                estimatedHours = null,
                toolsNeeded = emptyList(),
                start = 0,
                end = 0
            )
        }
    }
}
