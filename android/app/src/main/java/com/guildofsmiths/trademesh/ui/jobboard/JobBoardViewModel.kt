package com.guildofsmiths.trademesh.ui.jobboard

import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guildofsmiths.trademesh.BuildConfig
import com.guildofsmiths.trademesh.data.JobRepository
import com.guildofsmiths.trademesh.data.TimeEntryRepository
import com.guildofsmiths.trademesh.data.UserPreferences
import com.guildofsmiths.trademesh.service.AuthService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.UUID

/**
 * C-11: Job Board ViewModel
 * Manages job board state - uses local storage with optional backend sync
 */
class JobBoardViewModel(application: android.app.Application) : AndroidViewModel(application) {

    private val client = OkHttpClient()
    private val baseUrl = BuildConfig.BACKEND_URL

    companion object {
        const val SMITHAI_ENTERPRISE_SEED_JOB_ID = "smithai-enterprise-seed-v1"
    }

    // ════════════════════════════════════════════════════════════════════
    // STATE
    // ════════════════════════════════════════════════════════════════════

    private val _jobs = MutableStateFlow<List<Job>>(emptyList())
    val jobs: StateFlow<List<Job>> = _jobs.asStateFlow()
    
    // Archived jobs (separate from active jobs)
    private val _archivedJobs = MutableStateFlow<List<Job>>(emptyList())
    val archivedJobs: StateFlow<List<Job>> = _archivedJobs.asStateFlow()
    
    // Show archive view
    private val _showArchive = MutableStateFlow(false)
    val showArchive: StateFlow<Boolean> = _showArchive.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _selectedJob = MutableStateFlow<Job?>(null)
    val selectedJob: StateFlow<Job?> = _selectedJob.asStateFlow()

    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()

    // Local task storage per job
    private val localTasks = mutableMapOf<String, MutableList<Task>>()

    init {
        // Restore persisted jobs/tasks first so the dashboard isn't empty on
        // restart. Backend sync (loadJobs) merges in any newer remote data.
        restorePersistedState()
        loadJobs()
        if (com.guildofsmiths.trademesh.data.BuildFlags.SEED_DEMO_DATA) {
            seedDemoJobsIfEmpty()
        }
        // Wire AISupervisor callback to store daily logs
        com.guildofsmiths.trademesh.ai.AISupervisor.onDailyLogGenerated = { jobId, log ->
            addDailyLog(jobId, log)
        }
        registerSmithAIToolBridge()
        // Internal roadmap seed -- dev/demo only. Beta/prod builds must not plant
        // the "Build SmithAI Enterprise Tier" job on real users' boards.
        if (com.guildofsmiths.trademesh.data.BuildFlags.SEED_DEMO_DATA) {
            seedSmithAIEnterpriseJobIfNeeded()
        }
        // Auto-persist on every job-list change. Tasks are mutated through
        // the localTasks map directly; their writers call persistTasks()
        // explicitly below.
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(_jobs, _archivedJobs) { _, _ -> Unit }.collect {
                persistJobs()
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // PERSISTENCE
    // Lightweight JSON snapshot of the fields needed to navigate jobs and
    // their tasks across app restarts. Materials/expenses/photos/work-log
    // are intentionally NOT persisted — they're either reload-from-backend
    // or fine to be ephemeral. Called from every mutator below.
    // ════════════════════════════════════════════════════════════════════

    private fun restorePersistedState() {
        val jobsJson = UserPreferences.getJobs()
        if (jobsJson != null) {
            try {
                val arr = JSONArray(jobsJson)
                val list = mutableListOf<Job>()
                for (i in 0 until arr.length()) list += parsePersistedJob(arr.getJSONObject(i))
                if (list.isNotEmpty()) {
                    _jobs.value = list
                }
            } catch (e: Exception) {
                android.util.Log.w("JobBoardVM", "restore jobs failed", e)
            }
        }
        val archivedJson = UserPreferences.getArchivedJobs()
        if (archivedJson != null) {
            try {
                val arr = JSONArray(archivedJson)
                val list = mutableListOf<Job>()
                for (i in 0 until arr.length()) list += parsePersistedJob(arr.getJSONObject(i))
                if (list.isNotEmpty()) {
                    _archivedJobs.value = list
                }
            } catch (e: Exception) {
                android.util.Log.w("JobBoardVM", "restore archived jobs failed", e)
            }
        }
        val tasksJson = UserPreferences.getLocalTasks()
        if (tasksJson != null) {
            try {
                val obj = JSONObject(tasksJson)
                obj.keys().forEach { jobId ->
                    val arr = obj.getJSONArray(jobId)
                    val tasks = mutableListOf<Task>()
                    for (i in 0 until arr.length()) tasks += parsePersistedTask(arr.getJSONObject(i))
                    if (tasks.isNotEmpty()) localTasks[jobId] = tasks
                }
            } catch (e: Exception) {
                android.util.Log.w("JobBoardVM", "restore tasks failed", e)
            }
        }
        // Sync to shared repository so Time Clock job picker sees restored jobs.
        syncToRepository()
    }

    private fun persistJobs() {
        UserPreferences.saveJobs(jobsToJsonArray(_jobs.value).toString())
        UserPreferences.saveArchivedJobs(jobsToJsonArray(_archivedJobs.value).toString())
    }

    private fun persistTasks() {
        val root = JSONObject()
        localTasks.forEach { (jobId, tasks) ->
            val arr = JSONArray()
            tasks.forEach { arr.put(taskToJson(it)) }
            root.put(jobId, arr)
        }
        UserPreferences.saveLocalTasks(root.toString())
    }

    private fun jobsToJsonArray(jobs: List<Job>): JSONArray {
        val arr = JSONArray()
        jobs.forEach { arr.put(jobToJson(it)) }
        return arr
    }

    private fun jobToJson(job: Job): JSONObject = JSONObject().apply {
        put("id", job.id)
        put("title", job.title)
        put("description", job.description)
        put("clientName", job.clientName ?: "")
        put("clientPhone", job.clientPhone)
        put("clientAddress", job.clientAddress)
        put("status", job.status.name)
        put("stage", job.stage.name)
        put("priority", job.priority.name)
        put("createdBy", job.createdBy)
        put("createdAt", job.createdAt)
        put("updatedAt", job.updatedAt)
        put("toolsNeeded", job.toolsNeeded)
        put("expensesNote", job.expensesNote)
        put("crewSize", job.crewSize)
        put("hourlyRate", job.hourlyRate)
        put("isArchived", job.isArchived)
        if (job.archivedAt != null) put("archivedAt", job.archivedAt)
        if (job.archiveReason != null) put("archiveReason", job.archiveReason)
        if (job.estimatedStartDate != null) put("estimatedStartDate", job.estimatedStartDate)
        if (job.estimatedEndDate != null) put("estimatedEndDate", job.estimatedEndDate)
        if (job.proposalId != null) put("proposalId", job.proposalId)
        val eq = JSONArray()
        job.equipmentList.forEach { eq.put(it) }
        put("equipmentList", eq)
    }

    private fun parsePersistedJob(json: JSONObject): Job {
        val equipment = mutableListOf<String>()
        val eqArr = json.optJSONArray("equipmentList")
        if (eqArr != null) for (i in 0 until eqArr.length()) equipment.add(eqArr.getString(i))
        val clientName = json.optString("clientName", "")
        return Job(
            id = json.getString("id"),
            title = json.optString("title", ""),
            description = json.optString("description", ""),
            clientName = clientName.ifBlank { null },
            clientPhone = json.optString("clientPhone", ""),
            clientAddress = json.optString("clientAddress", ""),
            status = runCatching { JobStatus.valueOf(json.optString("status", "TODO")) }
                .getOrDefault(JobStatus.TODO),
            stage = runCatching { JobStage.valueOf(json.optString("stage", "LEAD")) }
                .getOrDefault(JobStage.LEAD),
            priority = runCatching { Priority.valueOf(json.optString("priority", "MEDIUM")) }
                .getOrDefault(Priority.MEDIUM),
            createdBy = json.optString("createdBy", ""),
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
            toolsNeeded = json.optString("toolsNeeded", ""),
            expensesNote = json.optString("expensesNote", ""),
            crewSize = json.optInt("crewSize", 1),
            hourlyRate = json.optDouble("hourlyRate", 0.0),
            isArchived = json.optBoolean("isArchived", false),
            archivedAt = if (json.has("archivedAt")) json.getLong("archivedAt") else null,
            archiveReason = if (json.has("archiveReason")) json.getString("archiveReason") else null,
            estimatedStartDate = if (json.has("estimatedStartDate")) json.getLong("estimatedStartDate") else null,
            estimatedEndDate = if (json.has("estimatedEndDate")) json.getLong("estimatedEndDate") else null,
            proposalId = if (json.has("proposalId")) json.getString("proposalId") else null,
            equipmentList = equipment
        )
    }

    private fun taskToJson(task: Task): JSONObject = JSONObject().apply {
        put("id", task.id)
        put("jobId", task.jobId)
        put("title", task.title)
        put("status", task.status.name)
        put("createdBy", task.createdBy)
        put("createdAt", task.createdAt)
        put("updatedAt", task.updatedAt)
        put("order", task.order)
        if (task.completedAt != null) put("completedAt", task.completedAt)
    }

    private fun parsePersistedTask(json: JSONObject): Task = Task(
        id = json.getString("id"),
        jobId = json.getString("jobId"),
        title = json.optString("title", ""),
        status = runCatching { TaskStatus.valueOf(json.optString("status", "PENDING")) }
            .getOrDefault(TaskStatus.PENDING),
        createdBy = json.optString("createdBy", ""),
        createdAt = json.optLong("createdAt", 0L),
        updatedAt = json.optLong("updatedAt", 0L),
        order = json.optInt("order", 0),
        completedAt = if (json.has("completedAt")) json.getLong("completedAt") else null
    )

    /**
     * Seeds demo jobs so the dashboard isn't empty on fresh install.
     * Remove this when persistent local storage is wired up.
     */
    private fun seedDemoJobsIfEmpty() {
        if (_jobs.value.isNotEmpty()) return
        val now = System.currentTimeMillis()
        val day = 86_400_000L
        val userId = UserPreferences.getUserId()

        _jobs.value = listOf(
            Job(
                id = "demo-1",
                title = "200A Panel Upgrade",
                clientName = "Maria Rodriguez",
                clientPhone = "718-555-0142",
                clientAddress = "847 Flatbush Ave, Brooklyn NY",
                description = "Replace 100A Federal Pacific with 200A Square D. Run new feeder from meter to panel.",
                stage = JobStage.IN_PROGRESS,
                status = JobStatus.IN_PROGRESS,
                priority = Priority.HIGH,
                crewSize = 2,
                hourlyRate = 85.0,
                materials = listOf(
                    Material(name = "200A Square D Panel", quantity = 1.0, unit = "ea", unitCost = 420.0),
                    Material(name = "2/0 THHN Copper", quantity = 60.0, unit = "ft", unitCost = 3.50),
                    Material(name = "Breakers assorted", quantity = 12.0, unit = "ea", unitCost = 18.0)
                ),
                depositCollected = 300.0,
                depositNote = "Check #2041",
                createdBy = userId,
                createdAt = now - 3 * day,
                updatedAt = now - 2 * 3600_000L
            ),
            Job(
                id = "demo-2",
                title = "Kitchen Remodel Rough-In",
                clientName = "Tony Bianchi",
                clientPhone = "347-555-0298",
                clientAddress = "1220 Ocean Pkwy, Brooklyn NY",
                description = "Rough-in electrical for kitchen remodel: 6 new circuits, dedicated 50A range, under-cabinet LED.",
                stage = JobStage.PROPOSAL,
                status = JobStatus.TODO,
                priority = Priority.MEDIUM,
                crewSize = 1,
                hourlyRate = 85.0,
                estimatedHours = 18.0,
                materials = listOf(
                    Material(name = "12/2 Romex", quantity = 250.0, unit = "ft", unitCost = 0.65),
                    Material(name = "6/3 Range Cable", quantity = 30.0, unit = "ft", unitCost = 4.20)
                ),
                createdBy = userId,
                createdAt = now - 1 * day,
                updatedAt = now - 1 * day
            ),
            Job(
                id = "demo-3",
                title = "Bathroom GFI Install",
                clientName = "Angela Park",
                clientPhone = "917-555-0811",
                clientAddress = "55 W 125th St, Apt 4B, Manhattan NY",
                description = "Install GFCI outlets in two bathrooms per NEC 210.8. Replace old 2-prong with grounded GFCI.",
                stage = JobStage.INVOICE,
                status = JobStatus.DONE,
                priority = Priority.LOW,
                crewSize = 1,
                hourlyRate = 85.0,
                materials = listOf(
                    Material(name = "20A GFCI Outlet", quantity = 4.0, unit = "ea", unitCost = 22.0),
                    Material(name = "Decora Wall Plate", quantity = 4.0, unit = "ea", unitCost = 3.50)
                ),
                createdBy = userId,
                createdAt = now - 7 * day,
                updatedAt = now - 1 * day
            )
        )
        syncToRepository()
    }
    
    private fun syncToRepository() {
        val simpleJobs = _jobs.value.map { job ->
            JobRepository.SimpleJob(
                id = job.id,
                title = job.title,
                status = job.status.name
            )
        }
        JobRepository.updateJobs(simpleJobs)
    }

    // ════════════════════════════════════════════════════════════════════
    // JOB OPERATIONS
    // ════════════════════════════════════════════════════════════════════

    fun loadJobs() {
        _isLoading.value = true
        _error.value = null

        // Try backend first, fall back to local
        viewModelScope.launch {
            try {
                loadJobsFromBackend()
            } catch (e: Exception) {
                // Backend not available - use local jobs
                _isLoading.value = false
                surfaceLoadFailureIfNoLocalData()
            }
        }
    }

    /**
     * Error rule: a failed/unreachable backend load is NOT a user-facing error
     * as long as the user still has usable local data to look at (jobs
     * restored from disk, or already loaded this session) — that's the
     * offline-fallback path SmithNet is designed around. `_error` is only
     * set when the load fails AND `_jobs` is empty, i.e. the user would
     * otherwise be staring at a blank board with no explanation.
     *
     * `internal` (not `private`) solely so JobBoardViewModelLoadFailureTest
     * can exercise this rule directly — the surrounding OkHttp Callback
     * wiring (real async network I/O on a non-injectable client) isn't
     * unit-testable without a bigger DI refactor; this keeps the actual
     * decision logic under test even though the plumbing around it isn't.
     */
    internal fun surfaceLoadFailureIfNoLocalData() {
        if (_jobs.value.isEmpty()) {
            _error.value = "Couldn't load jobs."
        }
    }

    private fun loadJobsFromBackend() {
        val token = AuthService.getAccessToken()
        if (token == null) {
            _isLoading.value = false
            // Not logged in yet - not a failure, just nothing to sync.
            return
        }

        val request = Request.Builder()
            .url("$baseUrl/api/jobs")
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                _isLoading.value = false
                // Keep local jobs; only surface an error if there's nothing local to show.
                surfaceLoadFailureIfNoLocalData()
            }

            override fun onResponse(call: Call, response: Response) {
                _isLoading.value = false
                if (response.isSuccessful) {
                    try {
                        val json = JSONObject(response.body?.string() ?: "{}")
                        val jobsArray = json.optJSONArray("jobs") ?: JSONArray()
                        val jobsList = mutableListOf<Job>()

                        for (i in 0 until jobsArray.length()) {
                            jobsList.add(parseJob(jobsArray.getJSONObject(i)))
                        }

                        // Merge with local jobs
                        val localIds = _jobs.value.map { it.id }.toSet()
                        val merged = _jobs.value.toMutableList()
                        jobsList.forEach { job ->
                            if (job.id !in localIds) {
                                merged.add(job)
                            }
                        }
                        _jobs.value = merged
                    } catch (e: Exception) {
                        // Parse error - keep local jobs; only surface if nothing local.
                        surfaceLoadFailureIfNoLocalData()
                    }
                } else {
                    // Non-2xx from backend - keep local jobs; only surface if nothing local.
                    surfaceLoadFailureIfNoLocalData()
                }
            }
        })
    }

    fun createJob(
        title: String,
        description: String = "",
        priority: Priority = Priority.MEDIUM,
        toolsNeeded: String = "",
        expensesNote: String = "",
        crewSize: Int = 1,
        crew: List<CrewMember> = emptyList(),
        materials: List<Material> = emptyList(),
        estimatedStartDate: Long? = null,
        estimatedEndDate: Long? = null,
        clientName: String? = null,
        clientPhone: String = "",
        clientAddress: String = "",
        hourlyRate: Double = 0.0,
        equipmentList: List<String> = emptyList(),
        stage: JobStage = JobStage.LEAD,
        taskDescriptions: List<String> = emptyList(),
        proposalId: String? = null,
        status: JobStatus = JobStatus.TODO
    ) {
        val userId = UserPreferences.getUserId()
        val now = System.currentTimeMillis()

        val newJob = Job(
            id = UUID.randomUUID().toString(),
            title = title,
            description = description,
            status = status,
            priority = priority,
            createdBy = userId,
            createdAt = now,
            updatedAt = now,
            toolsNeeded = toolsNeeded,
            expensesNote = expensesNote,
            crewSize = crewSize,
            crew = crew,
            materials = materials,
            estimatedStartDate = estimatedStartDate,
            estimatedEndDate = estimatedEndDate,
            clientName = clientName,
            clientPhone = clientPhone,
            clientAddress = clientAddress,
            hourlyRate = hourlyRate,
            equipmentList = equipmentList,
            stage = stage,
            proposalId = proposalId
        )

        // Add to local list immediately
        _jobs.value = _jobs.value + newJob

        // Seed tasks from proposal/wizard so they appear on the Job Board
        val cleanTasks = taskDescriptions.map { it.trim() }.filter { it.isNotBlank() }
        if (cleanTasks.isNotEmpty()) {
            val seeded = cleanTasks.mapIndexed { index, title ->
                Task(
                    id = UUID.randomUUID().toString(),
                    jobId = newJob.id,
                    title = title,
                    status = TaskStatus.PENDING,
                    createdBy = userId,
                    createdAt = now,
                    updatedAt = now,
                    order = index
                )
            }
            localTasks[newJob.id] = seeded.toMutableList()
            persistTasks()
        }

        // Sync to shared repository for Time Clock
        syncToRepository()

        // Try to sync to backend
        syncJobToBackend(newJob)
    }
    
    // ════════════════════════════════════════════════════════════════════
    // ARCHIVE OPERATIONS
    // ════════════════════════════════════════════════════════════════════
    
    fun toggleArchiveView() {
        _showArchive.value = !_showArchive.value
    }
    
    fun archiveJob(jobId: String, reason: String = "Completed") {
        if (jobId == SMITHAI_ENTERPRISE_SEED_JOB_ID) {
            UserPreferences.setSmithAIEnterpriseJobCancelled()
        }
        val now = System.currentTimeMillis()

        // Find the job
        val jobToArchive = _jobs.value.find { it.id == jobId } ?: return
        
        // Create archived version with archive metadata
        val archivedJob = jobToArchive.copy(
            isArchived = true,
            archivedAt = now,
            archiveReason = reason,
            updatedAt = now
        )
        
        // Move from active to archived
        _jobs.value = _jobs.value.filter { it.id != jobId }
        _archivedJobs.value = _archivedJobs.value + archivedJob
        
        // Close detail view if this job was selected
        if (_selectedJob.value?.id == jobId) {
            _selectedJob.value = null
        }
        
        // Sync to repository
        syncToRepository()
    }
    
    fun restoreJob(jobId: String) {
        val now = System.currentTimeMillis()
        
        // Find the archived job
        val jobToRestore = _archivedJobs.value.find { it.id == jobId } ?: return
        
        // Remove archive metadata and restore
        val restoredJob = jobToRestore.copy(
            isArchived = false,
            archivedAt = null,
            archiveReason = null,
            updatedAt = now
        )
        
        // Move from archived back to active
        _archivedJobs.value = _archivedJobs.value.filter { it.id != jobId }
        _jobs.value = _jobs.value + restoredJob
        
        // Sync to repository
        syncToRepository()
    }
    
    fun deleteArchivedJob(jobId: String) {
        if (jobId == SMITHAI_ENTERPRISE_SEED_JOB_ID) {
            UserPreferences.setSmithAIEnterpriseJobCancelled()
        }
        _archivedJobs.value = _archivedJobs.value.filter { it.id != jobId }
        localTasks.remove(jobId)
        persistTasks()
    }

    /**
     * Backfill helper: rewrite a job's lifecycle fields (status / stage /
     * updatedAt / completedAt) without going through the stage-machine.
     * Used by the one-time lifecycle migration to align Job timestamps
     * with the actual TimeEntry history (e.g. work that happened yesterday
     * but was created via a free-text clock-in today).
     */
    fun setClient(jobId: String, name: String, phone: String, address: String) {
        val now = System.currentTimeMillis()
        _jobs.value = _jobs.value.map { job ->
            if (job.id == jobId) job.copy(
                clientName = name.ifBlank { null },
                clientPhone = phone,
                clientAddress = address,
                updatedAt = now
            ) else job
        }
        _selectedJob.value?.let { sel ->
            if (sel.id == jobId) {
                _selectedJob.value = sel.copy(
                    clientName = name.ifBlank { null },
                    clientPhone = phone,
                    clientAddress = address,
                    updatedAt = now
                )
            }
        }
        persistJobs()
    }

    fun updateJobLifecycle(
        jobId: String,
        status: JobStatus,
        stage: JobStage,
        createdAt: Long? = null,
        updatedAt: Long,
        completedAt: Long?
    ) {
        _jobs.value = _jobs.value.map { job ->
            if (job.id == jobId) job.copy(
                status = status,
                stage = stage,
                createdAt = createdAt ?: job.createdAt,
                updatedAt = updatedAt,
                completedAt = completedAt ?: job.completedAt
            ) else job
        }
    }
    
    fun addRelatedMessages(jobId: String, messageIds: List<String>, channelId: String?) {
        _jobs.value = _jobs.value.map { job ->
            if (job.id == jobId) {
                job.copy(
                    relatedMessageIds = (job.relatedMessageIds + messageIds).distinct(),
                    relatedChannelId = channelId ?: job.relatedChannelId,
                    updatedAt = System.currentTimeMillis()
                )
            } else job
        }
        
        // Also update in archived jobs
        _archivedJobs.value = _archivedJobs.value.map { job ->
            if (job.id == jobId) {
                job.copy(
                    relatedMessageIds = (job.relatedMessageIds + messageIds).distinct(),
                    relatedChannelId = channelId ?: job.relatedChannelId,
                    updatedAt = System.currentTimeMillis()
                )
            } else job
        }
    }

    private fun syncJobToBackend(job: Job) {
        val token = AuthService.getAccessToken() ?: return

        val json = JSONObject().apply {
            put("title", job.title)
            put("description", job.description)
            put("priority", job.priority.name.lowercase())
        }

        val request = Request.Builder()
            .url("$baseUrl/api/jobs")
            .header("Authorization", "Bearer $token")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Keep local job even if backend fails
            }
            override fun onResponse(call: Call, response: Response) {
                // Job synced or failed - either way we have local copy
            }
        })
    }

    fun moveJob(jobId: String, newStatus: JobStatus) {
        // Update locally immediately
        _jobs.value = _jobs.value.map { job ->
            if (job.id == jobId) job.copy(status = newStatus, updatedAt = System.currentTimeMillis()) 
            else job
        }
        
        // Update selected job if it's the one being moved
        _selectedJob.value?.let { selected ->
            if (selected.id == jobId) {
                _selectedJob.value = selected.copy(status = newStatus)
            }
        }
        
        // Sync to shared repository
        syncToRepository()

        // Try to sync to backend
        val token = AuthService.getAccessToken() ?: return
        
        val json = JSONObject().apply {
            put("newStatus", newStatus.name.lowercase())
        }

        val request = Request.Builder()
            .url("$baseUrl/api/jobs/$jobId/move")
            .header("Authorization", "Bearer $token")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {}
        })
    }

    /**
     * One-time seed: adds a "Build SmithAI Enterprise Tier" job so we dogfood
     * SmithAI by tracking its own roadmap inside the app. Runs once per device
     * AND respects a "cancelled" flag so deletion is permanent — once the user
     * deletes or archives the seed job, it never comes back, even after
     * uninstall+reinstall (assuming Android Auto Backup keeps SharedPreferences).
     * Local-only: never routes through Hetzner sync, so it stays per-profile.
     */
    private fun seedSmithAIEnterpriseJobIfNeeded() {
        if (UserPreferences.isSmithAIEnterpriseJobSeeded()) return
        if (UserPreferences.isSmithAIEnterpriseJobCancelled()) return
        val userId = UserPreferences.getUserId()
        val now = System.currentTimeMillis()
        val seedJob = Job(
            id = SMITHAI_ENTERPRISE_SEED_JOB_ID,
            title = "Build SmithAI Enterprise Tier",
            description = "Mutate-existing tools (edit/delete time entries, edit job fields) and crew/team awareness for SmithAI conversations. Add-only writes already shipped at the Advanced tier on 2026-05-03; this is the next tier up.",
            status = JobStatus.TODO,
            stage = JobStage.LEAD,
            priority = Priority.MEDIUM,
            createdBy = userId,
            createdAt = now,
            updatedAt = now,
            tags = listOf("smithai", "enterprise", "roadmap")
        )
        _jobs.value = _jobs.value + seedJob
        val seededTasks = listOf(
            "Add update_time_entry tool (with before/after diff in approval card)",
            "Add delete_time_entry tool",
            "Add update_job_fields tool (edit title, client, address)",
            "Add delete_job tool (wraps existing JobBoardViewModel.deleteJob)",
            "Add query_crew_status tool",
            "Add query_crew_messages tool",
            "Add assign_to_crew tool",
            "Wire SmithAITierGate to real entitlements endpoint",
            "Capture pre-mutation hash in audit log for edits",
            "Re-derive Ledger artifacts when a Ledger-input row is mutated"
        )
        val taskList = seededTasks.mapIndexed { index, title ->
            Task(
                id = UUID.randomUUID().toString(),
                jobId = seedJob.id,
                title = title,
                status = TaskStatus.PENDING,
                createdBy = userId,
                createdAt = now,
                updatedAt = now,
                order = index
            )
        }
        localTasks[seedJob.id] = taskList.toMutableList()
        persistTasks()
        UserPreferences.setSmithAIEnterpriseJobSeeded()
        syncToRepository()
    }

    private fun registerSmithAIToolBridge() {
        com.guildofsmiths.trademesh.ai.SmithAIToolBridge.jobsSnapshot = {
            _jobs.value.map { job ->
                com.guildofsmiths.trademesh.ai.SmithAIToolBridge.JobSnapshot(
                    id = job.id,
                    title = job.title,
                    stage = job.stage.name,
                    clientName = job.clientName,
                    clientPhone = job.clientPhone.takeIf { it.isNotBlank() },
                    clientAddress = job.clientAddress.takeIf { it.isNotBlank() },
                    dueDate = job.dueDate,
                    updatedAt = job.updatedAt
                )
            }
        }
        com.guildofsmiths.trademesh.ai.SmithAIToolBridge.clientsSnapshot = {
            com.guildofsmiths.trademesh.data.ClientRepository.getClients(_jobs.value).map { c ->
                com.guildofsmiths.trademesh.ai.SmithAIToolBridge.ClientSnapshot(
                    name = c.name,
                    phone = c.phone,
                    address = c.address,
                    activeJobCount = c.activeJobCount,
                    totalJobCount = c.jobCount
                )
            }
        }
        com.guildofsmiths.trademesh.ai.SmithAIToolBridge.createJob = { title, clientName, address, stageRaw ->
            try {
                val parsedStage = stageRaw?.let { runCatching { JobStage.valueOf(it.uppercase()) }.getOrNull() } ?: JobStage.LEAD
                createJob(
                    title = title,
                    clientName = clientName,
                    clientAddress = address ?: "",
                    stage = parsedStage
                )
                val created = _jobs.value.lastOrNull { it.title == title }
                if (created != null) {
                    com.guildofsmiths.trademesh.ai.SmithAIToolBridge.CreateJobResult.Created(created.id, created.title)
                } else {
                    com.guildofsmiths.trademesh.ai.SmithAIToolBridge.CreateJobResult.Failed("Job did not appear in active list")
                }
            } catch (e: Exception) {
                com.guildofsmiths.trademesh.ai.SmithAIToolBridge.CreateJobResult.Failed(e.message ?: "unknown error")
            }
        }
        com.guildofsmiths.trademesh.ai.SmithAIToolBridge.updateJobStage = { jobId, newStageRaw ->
            try {
                val newStage = JobStage.valueOf(newStageRaw.uppercase())
                if (_jobs.value.none { it.id == jobId }) {
                    com.guildofsmiths.trademesh.ai.SmithAIToolBridge.UpdateStageResult.Failed("Job not found")
                } else {
                    moveJobStage(jobId, newStage)
                    com.guildofsmiths.trademesh.ai.SmithAIToolBridge.UpdateStageResult.Updated(jobId, newStage.name)
                }
            } catch (e: IllegalArgumentException) {
                com.guildofsmiths.trademesh.ai.SmithAIToolBridge.UpdateStageResult.Failed("Unknown stage: $newStageRaw")
            } catch (e: Exception) {
                com.guildofsmiths.trademesh.ai.SmithAIToolBridge.UpdateStageResult.Failed(e.message ?: "unknown error")
            }
        }
    }

    fun moveJobStage(jobId: String, newStage: JobStage) {
        // Capture old stage before updating
        val oldJob = _jobs.value.find { it.id == jobId }
        val oldStage = oldJob?.stage

        _jobs.value = _jobs.value.map { job ->
            if (job.id == jobId) job.copy(stage = newStage, updatedAt = System.currentTimeMillis())
            else job
        }
        _selectedJob.value?.let { selected ->
            if (selected.id == jobId) {
                _selectedJob.value = selected.copy(stage = newStage)
            }
        }
        syncToRepository()

        // Trigger AI stage change hook
        val updatedJob = _jobs.value.find { it.id == jobId }
        if (updatedJob != null && oldStage != null && oldStage != newStage) {
            com.guildofsmiths.trademesh.ai.AISupervisor.onStageChange(updatedJob, oldStage, newStage)
        }
    }

    fun selectJob(job: Job?) {
        _selectedJob.value = job
        if (job != null) {
            // Load tasks for this job
            _tasks.value = localTasks[job.id] ?: emptyList()
            loadTasksFromBackend(job.id)
        } else {
            _tasks.value = emptyList()
        }
    }

    // Toggle material checked state
    fun toggleMaterial(jobId: String, materialIndex: Int) {
        _jobs.value = _jobs.value.map { job ->
            if (job.id == jobId) {
                val updatedMaterials = job.materials.toMutableList()
                if (materialIndex < updatedMaterials.size) {
                    val material = updatedMaterials[materialIndex]
                    updatedMaterials[materialIndex] = material.copy(
                        checked = !material.checked,
                        checkedAt = if (!material.checked) System.currentTimeMillis() else null
                    )
                }
                job.copy(materials = updatedMaterials, updatedAt = System.currentTimeMillis())
            } else job
        }
        // Update selected job
        _selectedJob.value = _jobs.value.find { it.id == jobId }
    }

    /**
     * Toggle task between PENDING and DONE. Used as the legacy 2-state
     * checkbox path; the 3-state UI prefers startTask/completeTask.
     */
    fun toggleTask(taskId: String) {
        val jobId = _selectedJob.value?.id ?: return
        val tasks = localTasks[jobId] ?: return

        val updatedTasks = tasks.map { task ->
            if (task.id == taskId) {
                task.copy(
                    status = if (task.status == TaskStatus.DONE) TaskStatus.PENDING else TaskStatus.DONE,
                    completedAt = if (task.status != TaskStatus.DONE) System.currentTimeMillis() else null,
                    updatedAt = System.currentTimeMillis()
                )
            } else task
        }

        localTasks[jobId] = updatedTasks.toMutableList()
        _tasks.value = updatedTasks
        persistTasks()
    }

    /**
     * Mark a task IN_PROGRESS. Auto-pauses any other IN_PROGRESS task on
     * the same job so at most one task is active at a time.
     */
    fun startTask(taskId: String) {
        val jobId = _selectedJob.value?.id ?: return
        val tasks = localTasks[jobId] ?: return
        val now = System.currentTimeMillis()

        val updatedTasks = tasks.map { task ->
            when {
                task.id == taskId -> task.copy(status = TaskStatus.IN_PROGRESS, updatedAt = now)
                task.status == TaskStatus.IN_PROGRESS ->
                    task.copy(status = TaskStatus.PENDING, updatedAt = now)
                else -> task
            }
        }
        localTasks[jobId] = updatedTasks.toMutableList()
        _tasks.value = updatedTasks
        persistTasks()
    }

    /** Mark a task DONE without affecting other tasks. */
    fun completeTask(taskId: String) {
        val jobId = _selectedJob.value?.id ?: return
        val tasks = localTasks[jobId] ?: return
        val now = System.currentTimeMillis()

        val updatedTasks = tasks.map { task ->
            if (task.id == taskId) {
                task.copy(
                    status = TaskStatus.DONE,
                    completedAt = now,
                    updatedAt = now
                )
            } else task
        }
        localTasks[jobId] = updatedTasks.toMutableList()
        _tasks.value = updatedTasks
        persistTasks()
    }

    // Add work log entry
    fun addWorkLog(jobId: String, text: String) {
        val userId = UserPreferences.getUserId()
        val entry = WorkLogEntry(
            text = text,
            timestamp = System.currentTimeMillis(),
            author = userId
        )
        
        _jobs.value = _jobs.value.map { job ->
            if (job.id == jobId) {
                job.copy(
                    workLog = job.workLog + entry,
                    updatedAt = System.currentTimeMillis()
                )
            } else job
        }
        // Update selected job
        _selectedJob.value = _jobs.value.find { it.id == jobId }
        persistJobs()
    }

    // Append a photo URI to a job
    fun addPhoto(jobId: String, uri: String) {
        _jobs.value = _jobs.value.map { job ->
            if (job.id == jobId) {
                job.copy(photos = job.photos + uri, updatedAt = System.currentTimeMillis())
            } else job
        }
        _selectedJob.value = _jobs.value.find { it.id == jobId }
    }

    // Add a new material to a job
    fun addMaterial(jobId: String, material: Material) {
        _jobs.value = _jobs.value.map { job ->
            if (job.id == jobId) {
                val updatedMaterials = job.materials.toMutableList()
                updatedMaterials.add(material)
                job.copy(materials = updatedMaterials, updatedAt = System.currentTimeMillis())
            } else job
        }
        _selectedJob.value = _jobs.value.find { it.id == jobId }
    }

    // Update material details without changing checked state
    fun updateMaterial(jobId: String, materialIndex: Int, updated: Material) {
        _jobs.value = _jobs.value.map { job ->
            if (job.id == jobId) {
                val updatedMaterials = job.materials.toMutableList()
                if (materialIndex < updatedMaterials.size) {
                    updatedMaterials[materialIndex] = updated
                }
                job.copy(materials = updatedMaterials, updatedAt = System.currentTimeMillis())
            } else job
        }
        _selectedJob.value = _jobs.value.find { it.id == jobId }
    }

    // Update material with cost information (for invoice generation)
    fun updateMaterialCost(
        jobId: String,
        materialIndex: Int,
        quantity: Double,
        unit: String,
        unitCost: Double,
        totalCost: Double,
        vendor: String
    ) {
        _jobs.value = _jobs.value.map { job ->
            if (job.id == jobId) {
                val updatedMaterials = job.materials.toMutableList()
                if (materialIndex < updatedMaterials.size) {
                    val material = updatedMaterials[materialIndex]
                    updatedMaterials[materialIndex] = material.copy(
                        checked = true,
                        checkedAt = System.currentTimeMillis(),
                        quantity = quantity,
                        unit = unit,
                        unitCost = unitCost,
                        totalCost = totalCost,
                        vendor = vendor
                    )
                }
                job.copy(materials = updatedMaterials, updatedAt = System.currentTimeMillis())
            } else job
        }
        // Update selected job
        _selectedJob.value = _jobs.value.find { it.id == jobId }
    }

    // ════════════════════════════════════════════════════════════════════
    // JOB EXPENSES (BOL-style line items)
    // ════════════════════════════════════════════════════════════════════

    fun addExpense(jobId: String, expense: JobExpense) {
        _jobs.value = _jobs.value.map { job ->
            if (job.id == jobId) {
                job.copy(
                    expenses = job.expenses + expense,
                    updatedAt = System.currentTimeMillis()
                )
            } else job
        }
        _selectedJob.value = _jobs.value.find { it.id == jobId }
        syncToRepository()
    }

    fun updateExpense(jobId: String, expenseId: String, mutate: (JobExpense) -> JobExpense) {
        _jobs.value = _jobs.value.map { job ->
            if (job.id == jobId) {
                job.copy(
                    expenses = job.expenses.map { if (it.id == expenseId) mutate(it) else it },
                    updatedAt = System.currentTimeMillis()
                )
            } else job
        }
        _selectedJob.value = _jobs.value.find { it.id == jobId }
        syncToRepository()
    }

    fun deleteExpense(jobId: String, expenseId: String) {
        _jobs.value = _jobs.value.map { job ->
            if (job.id == jobId) {
                job.copy(
                    expenses = job.expenses.filter { it.id != expenseId },
                    updatedAt = System.currentTimeMillis()
                )
            } else job
        }
        _selectedJob.value = _jobs.value.find { it.id == jobId }
        syncToRepository()
    }

    fun addExpenses(jobId: String, expenses: List<JobExpense>) {
        if (expenses.isEmpty()) return
        _jobs.value = _jobs.value.map { job ->
            if (job.id == jobId) {
                job.copy(
                    expenses = job.expenses + expenses,
                    updatedAt = System.currentTimeMillis()
                )
            } else job
        }
        _selectedJob.value = _jobs.value.find { it.id == jobId }
        syncToRepository()
    }

    fun updateDeposit(jobId: String, amount: Double, note: String?) {
        _jobs.value = _jobs.value.map { job ->
            if (job.id == jobId) {
                job.copy(
                    depositCollected = amount,
                    depositNote = note,
                    updatedAt = System.currentTimeMillis()
                )
            } else job
        }
        _selectedJob.value = _jobs.value.find { it.id == jobId }
        syncToRepository()
    }

    // ════════════════════════════════════════════════════════════════════
    // INVOICE GENERATION
    // ════════════════════════════════════════════════════════════════════

    private val _generatedInvoice = MutableStateFlow<com.guildofsmiths.trademesh.ui.invoice.Invoice?>(null)
    val generatedInvoice: StateFlow<com.guildofsmiths.trademesh.ui.invoice.Invoice?> = _generatedInvoice.asStateFlow()

    // Tracks whether the user explicitly shared the current preview. If
    // they dismiss without sharing, the outbox pushes a DISCARD so the
    // backend row gets deleted; if they share, we mark it sent instead.
    private var generatedShared: Boolean = false

    private val invoicesOutbox: com.guildofsmiths.trademesh.data.invoice.InvoicesOutbox by lazy {
        val ctx = getApplication<android.app.Application>().applicationContext
        com.guildofsmiths.trademesh.data.invoice.InvoicesOutbox(
            dao = com.guildofsmiths.trademesh.db.AppDatabase.getInstance(ctx).pendingInvoicePushDao(),
            scheduler = com.guildofsmiths.trademesh.data.invoice.InvoicesOutbox.WorkManagerScheduler(ctx),
        )
    }

    fun generateInvoice(job: Job) {
        viewModelScope.launch {
            val userName = UserPreferences.getUserName()
            val timeEntries = TimeEntryRepository.getEntriesForJob(job.id, job.title)

            val invoice = com.guildofsmiths.trademesh.ui.invoice.InvoiceGenerator.generateFromJob(
                job = job,
                timeEntries = timeEntries,
                providerName = userName,
                providerTrade = "Tradesperson – Guild of Smiths",
                hourlyRate = if (job.hourlyRate > 0) job.hourlyRate else 85.0
            )

            generatedShared = false
            _generatedInvoice.value = invoice

            // Push to backend paper trail; safe if offline (queue persists).
            invoicesOutbox.enqueueCreate(invoice)
        }
    }

    /** Called by the screen when the user actually shares the invoice. */
    fun markShared(invoiceId: String) {
        generatedShared = true
        viewModelScope.launch { invoicesOutbox.enqueueMarkSent(invoiceId) }
    }

    fun clearInvoice() {
        val inv = _generatedInvoice.value
        _generatedInvoice.value = null
        // If the user dismissed without sharing, discard the row.
        if (inv != null && !generatedShared) {
            viewModelScope.launch { invoicesOutbox.enqueueDiscard(inv.id) }
        }
        generatedShared = false
    }

    // ════════════════════════════════════════════════════════════════════
    // DAILY LOG OPERATIONS
    // ════════════════════════════════════════════════════════════════════

    fun addDailyLog(jobId: String, log: DailyJobLog) {
        _jobs.value = _jobs.value.map { job ->
            if (job.id == jobId) {
                // Deduplicate by date — merge if log for same date exists
                val existingIndex = job.dailyLogs.indexOfFirst { it.date == log.date }
                val updatedLogs = if (existingIndex >= 0) {
                    job.dailyLogs.toMutableList().apply { set(existingIndex, log) }
                } else {
                    job.dailyLogs + log
                }
                job.copy(dailyLogs = updatedLogs, updatedAt = System.currentTimeMillis())
            } else job
        }
        _selectedJob.value = _jobs.value.find { it.id == jobId }
    }

    fun triggerManualSummary(jobId: String) {
        val job = _jobs.value.find { it.id == jobId } ?: return
        viewModelScope.launch {
            try {
                val log = com.guildofsmiths.trademesh.ai.DailyLogGenerator.generateLog(job)
                addDailyLog(jobId, log)
            } catch (e: Exception) {
                android.util.Log.e("JobBoardVM", "Manual summary failed: ${e.message}")
            }
        }
    }

    fun assignCrewToJob(jobId: String, crewMember: com.guildofsmiths.trademesh.data.CrewPresenceInfo) {
        _jobs.value = _jobs.value.map { job ->
            if (job.id == jobId) {
                val newCrewMember = CrewMember(
                    name = crewMember.name,
                    occupation = crewMember.trade,
                    task = ""
                )
                val updatedCrew = job.crew + newCrewMember
                job.copy(crew = updatedCrew, updatedAt = System.currentTimeMillis())
            } else job
        }
        _selectedJob.value = _jobs.value.find { it.id == jobId }
        syncToRepository()
    }

    fun deleteJob(jobId: String) {
        if (jobId == SMITHAI_ENTERPRISE_SEED_JOB_ID) {
            UserPreferences.setSmithAIEnterpriseJobCancelled()
        }
        // Remove locally
        _jobs.value = _jobs.value.filter { it.id != jobId }
        localTasks.remove(jobId)
        _selectedJob.value = null
        
        // Sync to repository
        syncToRepository()
        
        // Try to sync to backend
        val token = AuthService.getAccessToken() ?: return
        
        val request = Request.Builder()
            .url("$baseUrl/api/jobs/$jobId")
            .header("Authorization", "Bearer $token")
            .delete()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {}
        })
    }

    // ════════════════════════════════════════════════════════════════════
    // TASK OPERATIONS
    // ════════════════════════════════════════════════════════════════════

    private fun loadTasksFromBackend(jobId: String) {
        val token = AuthService.getAccessToken() ?: return

        val request = Request.Builder()
            .url("$baseUrl/api/jobs/$jobId/tasks")
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    try {
                        val json = JSONObject(response.body?.string() ?: "{}")
                        val tasksArray = json.optJSONArray("tasks") ?: JSONArray()
                        val tasksList = mutableListOf<Task>()

                        for (i in 0 until tasksArray.length()) {
                            tasksList.add(parseTask(tasksArray.getJSONObject(i)))
                        }

                        // Merge with local tasks
                        val local = localTasks[jobId] ?: mutableListOf()
                        val localIds = local.map { it.id }.toSet()
                        tasksList.forEach { task ->
                            if (task.id !in localIds) {
                                local.add(task)
                            }
                        }
                        localTasks[jobId] = local
                        persistTasks()

                        if (_selectedJob.value?.id == jobId) {
                            _tasks.value = local
                        }
                    } catch (e: Exception) {}
                }
            }
        })
    }

    fun createTask(jobId: String, title: String) {
        val userId = UserPreferences.getUserId()
        val now = System.currentTimeMillis()
        
        val newTask = Task(
            id = UUID.randomUUID().toString(),
            jobId = jobId,
            title = title,
            status = TaskStatus.PENDING,
            createdBy = userId,
            createdAt = now,
            updatedAt = now,
            order = (localTasks[jobId]?.size ?: 0)
        )

        // Add locally
        val tasks = localTasks.getOrPut(jobId) { mutableListOf() }
        tasks.add(newTask)
        persistTasks()

        if (_selectedJob.value?.id == jobId) {
            _tasks.value = tasks.toList()
        }

        // Try to sync to backend
        val token = AuthService.getAccessToken() ?: return
        
        val json = JSONObject().apply {
            put("jobId", jobId)
            put("title", title)
        }

        val request = Request.Builder()
            .url("$baseUrl/api/tasks")
            .header("Authorization", "Bearer $token")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {}
        })
    }

    // ════════════════════════════════════════════════════════════════════
    // PARSING
    // ════════════════════════════════════════════════════════════════════

    private fun parseJob(json: JSONObject): Job {
        val assignedToArray = json.optJSONArray("assignedTo") ?: JSONArray()
        val assignedTo = mutableListOf<String>()
        for (i in 0 until assignedToArray.length()) {
            assignedTo.add(assignedToArray.getString(i))
        }

        val tagsArray = json.optJSONArray("tags") ?: JSONArray()
        val tags = mutableListOf<String>()
        for (i in 0 until tagsArray.length()) {
            tags.add(tagsArray.getString(i))
        }

        return Job(
            id = json.getString("id"),
            title = json.getString("title"),
            description = json.optString("description", ""),
            projectId = json.optString("projectId", null),
            clientName = json.optString("clientName", null),
            location = json.optString("location", null),
            status = try {
                JobStatus.valueOf(json.getString("status").uppercase())
            } catch (e: Exception) {
                JobStatus.BACKLOG
            },
            priority = try {
                Priority.valueOf(json.getString("priority").uppercase())
            } catch (e: Exception) {
                Priority.MEDIUM
            },
            createdBy = json.getString("createdBy"),
            assignedTo = assignedTo,
            createdAt = json.getLong("createdAt"),
            updatedAt = json.getLong("updatedAt"),
            dueDate = if (json.has("dueDate") && !json.isNull("dueDate")) json.optLong("dueDate") else null,
            completedAt = if (json.has("completedAt") && !json.isNull("completedAt")) json.optLong("completedAt") else null,
            tags = tags
        )
    }

    private fun parseTask(json: JSONObject): Task {
        val checklistArray = json.optJSONArray("checklist") ?: JSONArray()
        val checklist = mutableListOf<ChecklistItem>()
        for (i in 0 until checklistArray.length()) {
            val item = checklistArray.getJSONObject(i)
            checklist.add(
                ChecklistItem(
                    id = item.getString("id"),
                    text = item.getString("text"),
                    checked = item.optBoolean("checked", false),
                    checkedAt = if (item.has("checkedAt") && !item.isNull("checkedAt")) item.optLong("checkedAt") else null,
                    checkedBy = if (item.has("checkedBy") && !item.isNull("checkedBy")) item.optString("checkedBy") else null
                )
            )
        }

        return Task(
            id = json.getString("id"),
            jobId = json.getString("jobId"),
            title = json.getString("title"),
            description = if (json.has("description") && !json.isNull("description")) json.optString("description") else null,
            status = try {
                TaskStatus.valueOf(json.getString("status").uppercase())
            } catch (e: Exception) {
                TaskStatus.PENDING
            },
            assignedTo = if (json.has("assignedTo") && !json.isNull("assignedTo")) json.optString("assignedTo") else null,
            createdBy = json.getString("createdBy"),
            createdAt = json.getLong("createdAt"),
            updatedAt = json.getLong("updatedAt"),
            completedAt = if (json.has("completedAt") && !json.isNull("completedAt")) json.optLong("completedAt") else null,
            order = json.optInt("order", 0),
            checklist = checklist
        )
    }
}
