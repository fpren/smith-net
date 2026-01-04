package com.guildofsmiths.trademesh.ui.jobboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guildofsmiths.trademesh.data.JobRepository
import com.guildofsmiths.trademesh.data.JobStorage
import com.guildofsmiths.trademesh.data.TaskStorage
import com.guildofsmiths.trademesh.data.TimeEntryRepository
import com.guildofsmiths.trademesh.data.UserPreferences
import com.guildofsmiths.trademesh.data.SharedJobRepository
import com.guildofsmiths.trademesh.data.CollaborationMode
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
import com.guildofsmiths.trademesh.planner.types.ExecutionItem
import com.guildofsmiths.trademesh.utils.ExecutionItemSerializer

/**
 * C-11: Job Board ViewModel
 * Manages job board state - uses local storage with optional backend sync
 */
class JobBoardViewModel : ViewModel() {

    private val client = OkHttpClient()
    private val baseUrl = "http://10.0.2.2:3001"

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
        // Load persisted jobs first
        loadFromStorage()
        // Then try backend sync
        loadJobs()
        // Also load jobs from shared repository (added by Planner)
        loadJobsFromRepository()
        // Observe repository for changes from Planner
        observeRepository()
    }
    
    /**
     * Load jobs from persistent storage
     * Ensures archived jobs are properly separated from active jobs
     */
    private fun loadFromStorage() {
        val storedJobs = JobStorage.loadJobs()
        val storedArchivedJobs = JobStorage.loadArchivedJobs()
        
        // #region agent log
        val jobDetails = storedJobs.take(5).map { """{"id":"${it.id}","title":"${it.title.take(30).replace("\"", "\\\"")}","tasksCount":0,"materialsCount":${it.materials.size},"workLogCount":${it.workLog.size}}""" }.joinToString(",")
        android.util.Log.w("DEBUG_JOBBOARD", """{"location":"JobBoardViewModel.kt:loadFromStorage","message":"loaded jobs from storage","data":{"storedJobsCount":${storedJobs.size},"archivedCount":${storedArchivedJobs.size},"sampleJobs":[$jobDetails]},"hypothesisId":"A,B,C,E","timestamp":${System.currentTimeMillis()}}""")
        // #endregion
        
        // Filter out any accidentally archived jobs from the active list
        val activeJobs = storedJobs.filter { !it.isArchived && it.status != JobStatus.ARCHIVED }
        val misplacedArchived = storedJobs.filter { it.isArchived || it.status == JobStatus.ARCHIVED }
        
        // Combine all archived jobs (stored + misplaced)
        val allArchived = (storedArchivedJobs + misplacedArchived).distinctBy { it.id }
        
        if (activeJobs.isNotEmpty()) {
            _jobs.value = activeJobs
            syncToRepository()
        }
        if (allArchived.isNotEmpty()) {
            _archivedJobs.value = allArchived
        }
        
        // If we found misplaced archived jobs, fix the storage
        if (misplacedArchived.isNotEmpty()) {
            android.util.Log.i("JobBoard", "Found ${misplacedArchived.size} misplaced archived jobs, fixing storage")
            persistToStorage()
        }
    }
    
    /**
     * Persist current state to storage (atomic saves)
     */
    private fun persistToStorage() {
        val jobsSaved = JobStorage.saveJobs(_jobs.value)
        val archivedSaved = JobStorage.saveArchivedJobs(_archivedJobs.value)
        
        if (jobsSaved && archivedSaved) {
            android.util.Log.d("JobBoard", "✓ Persisted ${_jobs.value.size} jobs, ${_archivedJobs.value.size} archived")
        } else {
            android.util.Log.e("JobBoard", "✗ Persist failed: jobs=$jobsSaved, archived=$archivedSaved")
        }
    }
    
    private fun observeRepository() {
        viewModelScope.launch {
            JobRepository.activeJobs.collect { repoJobs ->
                // Check for new jobs not in our list
                val existingIds = _jobs.value.map { it.id }.toSet()
                val newJobs = repoJobs.filter { it.id !in existingIds }.map { simpleJob ->
                    val now = System.currentTimeMillis()
                    Job(
                        id = simpleJob.id,
                        title = simpleJob.title,
                        description = "",
                        status = try {
                            JobStatus.valueOf(simpleJob.status.uppercase())
                        } catch (e: Exception) {
                            JobStatus.TODO
                        },
                        priority = Priority.MEDIUM,
                        createdBy = UserPreferences.getUserId(),
                        createdAt = now,
                        updatedAt = now
                    )
                }
                
                if (newJobs.isNotEmpty()) {
                    _jobs.value = _jobs.value + newJobs
                    // Persist newly added jobs
                    persistToStorage()
                }
            }
        }
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
    
    /**
     * Load jobs from shared JobRepository (includes jobs added by Planner transfer)
     */
    private fun loadJobsFromRepository() {
        val repoJobs = JobRepository.activeJobs.value
        val existingIds = _jobs.value.map { it.id }.toSet()
        
        val newJobs = repoJobs.filter { it.id !in existingIds }.map { simpleJob ->
            val now = System.currentTimeMillis()
            Job(
                id = simpleJob.id,
                title = simpleJob.title,
                description = "",
                status = try {
                    JobStatus.valueOf(simpleJob.status.uppercase())
                } catch (e: Exception) {
                    JobStatus.TODO
                },
                priority = Priority.MEDIUM,
                createdBy = UserPreferences.getUserId(),
                createdAt = now,
                updatedAt = now
            )
        }
        
        if (newJobs.isNotEmpty()) {
            _jobs.value = _jobs.value + newJobs
            // Persist newly added jobs
            persistToStorage()
        }
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
            }
        }
    }

    private fun loadJobsFromBackend() {
        val token = AuthService.getAccessToken()
        if (token == null) {
            _isLoading.value = false
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
                // Keep local jobs, don't show error
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
                        // Parse error - keep local jobs
                    }
                }
            }
        })
    }

    fun createJob(
        title: String, 
        description: String = "", 
        priority: Priority = Priority.MEDIUM,
        toolsNeeded: String = "",
        expenses: String = "",
        crewSize: Int = 1,
        crew: List<CrewMember> = emptyList(),
        materials: List<Material> = emptyList(),
        estimatedStartDate: Long? = null,
        estimatedEndDate: Long? = null
    ) {
        val userId = UserPreferences.getUserId()
        val now = System.currentTimeMillis()
        
        val newJob = Job(
            id = UUID.randomUUID().toString(),
            title = title,
            description = description,
            status = JobStatus.TODO,
            priority = priority,
            createdBy = userId,
            createdAt = now,
            updatedAt = now,
            toolsNeeded = toolsNeeded,
            expenses = expenses,
            crewSize = crewSize,
            crew = crew,
            materials = materials,
            estimatedStartDate = estimatedStartDate,
            estimatedEndDate = estimatedEndDate
        )

        // Add to local list immediately
        _jobs.value = _jobs.value + newJob
        
        // Sync to shared repository for Time Clock
        syncToRepository()
        
        // Persist to storage
        persistToStorage()
        
        // Try to sync to backend
        syncJobToBackend(newJob)
    }
    
    // ════════════════════════════════════════════════════════════════════
    // ARCHIVE OPERATIONS
    // ════════════════════════════════════════════════════════════════════
    
    fun toggleArchiveView() {
        _showArchive.value = !_showArchive.value
    }
    
    /**
     * Archive a job with optional execution items for document regeneration.
     * 
     * @param jobId The job ID to archive
     * @param reason The archive reason
     * @param executionItems Optional execution items from the plan (for document regeneration)
     */
    fun archiveJob(
        jobId: String, 
        reason: String = "Completed",
        executionItems: List<ExecutionItem> = emptyList()
    ) {
        val now = System.currentTimeMillis()

        // Find the job
        val jobToArchive = _jobs.value.find { it.id == jobId } ?: return

        // Serialize execution items for storage
        val executionItemsJson = if (executionItems.isNotEmpty()) {
            ExecutionItemSerializer.serialize(executionItems)
        } else {
            jobToArchive.executionItemsJson // Preserve existing if any
        }

        // Create archived version with archive metadata
        val archivedJob = jobToArchive.copy(
            isArchived = true,
            archivedAt = now,
            archiveReason = reason,
            updatedAt = now,
            executionItemsJson = executionItemsJson
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

        // Persist to storage
        persistToStorage()
    }
    
    /**
     * Get execution items for an archived job (for document regeneration).
     */
    fun getExecutionItems(jobId: String): List<ExecutionItem> {
        val job = _archivedJobs.value.find { it.id == jobId }
            ?: _jobs.value.find { it.id == jobId }
            ?: return emptyList()
        
        return ExecutionItemSerializer.deserialize(job.executionItemsJson)
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
        
        // Persist to storage
        persistToStorage()
    }
    
    fun deleteArchivedJob(jobId: String) {
        _archivedJobs.value = _archivedJobs.value.filter { it.id != jobId }
        localTasks.remove(jobId)
        
        // Persist to storage
        persistToStorage()
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
        
        // Persist to storage
        persistToStorage()

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

    fun selectJob(job: Job?) {
        _selectedJob.value = job
        if (job != null) {
            // Load tasks for this job from persistent storage first, then check local cache
            val persistedTasks = TaskStorage.loadTasks(job.id)
            val localTasksForJob = localTasks[job.id] ?: emptyList()
            
            // Merge: prefer persisted tasks, add any local tasks not in persisted
            val persistedIds = persistedTasks.map { it.id }.toSet()
            val mergedTasks = persistedTasks + localTasksForJob.filter { it.id !in persistedIds }
            
            // Update local cache with merged result
            if (mergedTasks.isNotEmpty()) {
                localTasks[job.id] = mergedTasks.toMutableList()
            }
            
            _tasks.value = mergedTasks
            // #region agent log
            android.util.Log.w("DEBUG_JOBBOARD", """{"location":"JobBoardViewModel.kt:selectJob","message":"job selected - loaded tasks from storage","data":{"jobId":"${job.id}","jobTitle":"${job.title.take(30).replace("\"", "\\\"")}","persistedTasksCount":${persistedTasks.size},"localTasksCount":${localTasksForJob.size},"mergedTasksCount":${mergedTasks.size},"jobMaterialsCount":${job.materials.size},"jobWorkLogCount":${job.workLog.size}},"hypothesisId":"E","timestamp":${System.currentTimeMillis()}}""")
            // #endregion
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
        
        // Persist to storage
        persistToStorage()
    }

    // Toggle task done state
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
        
        // Persist to TaskStorage
        TaskStorage.saveTasks(jobId, updatedTasks)
    }
    
    // ════════════════════════════════════════════════════════════════════
    // TASK ASSIGNMENT
    // ════════════════════════════════════════════════════════════════════
    
    // Task filter mode
    private val _taskFilterMode = MutableStateFlow(TaskFilterMode.ALL)
    val taskFilterMode: StateFlow<TaskFilterMode> = _taskFilterMode.asStateFlow()
    
    // Filtered tasks based on current filter mode
    private val _filteredTasks = MutableStateFlow<List<Task>>(emptyList())
    val filteredTasks: StateFlow<List<Task>> = _filteredTasks.asStateFlow()
    
    /**
     * Set task filter mode (ALL, MY_TASKS, UNASSIGNED)
     */
    fun setTaskFilter(mode: TaskFilterMode) {
        _taskFilterMode.value = mode
        applyTaskFilter()
    }
    
    /**
     * Apply current filter to tasks
     */
    private fun applyTaskFilter() {
        val currentUserId = UserPreferences.getUserId()
        val allTasks = _tasks.value
        
        _filteredTasks.value = when (_taskFilterMode.value) {
            TaskFilterMode.ALL -> allTasks
            TaskFilterMode.MY_TASKS -> allTasks.filter { it.assignedTo == currentUserId }
            TaskFilterMode.UNASSIGNED -> allTasks.filter { it.assignedTo == null }
        }
    }
    
    /**
     * Assign a task to a crew member or user
     */
    fun assignTask(taskId: String, assigneeId: String?) {
        val jobId = _selectedJob.value?.id ?: return
        val tasks = localTasks[jobId] ?: return
        
        val updatedTasks = tasks.map { task ->
            if (task.id == taskId) {
                task.copy(
                    assignedTo = assigneeId,
                    updatedAt = System.currentTimeMillis()
                )
            } else task
        }
        
        localTasks[jobId] = updatedTasks.toMutableList()
        _tasks.value = updatedTasks
        applyTaskFilter()
        
        // Persist to TaskStorage
        TaskStorage.saveTasks(jobId, updatedTasks)
        
        android.util.Log.i("JobBoardViewModel", "Task $taskId assigned to ${assigneeId ?: "unassigned"}")
    }
    
    /**
     * Auto-distribute tasks evenly among crew members
     * Each crew member gets approximately equal number of tasks
     */
    fun autoDistributeTasks() {
        val job = _selectedJob.value ?: return
        val jobId = job.id
        val tasks = localTasks[jobId] ?: return
        
        // Get assignable members (crew + job assignees)
        val assignees = getAssignableMembers(job)
        
        if (assignees.isEmpty()) {
            android.util.Log.w("JobBoardViewModel", "No crew members to distribute tasks to")
            return
        }
        
        // Distribute tasks round-robin style
        val updatedTasks = tasks.mapIndexed { index, task ->
            val assigneeIndex = index % assignees.size
            task.copy(
                assignedTo = assignees[assigneeIndex].id,
                updatedAt = System.currentTimeMillis()
            )
        }
        
        localTasks[jobId] = updatedTasks.toMutableList()
        _tasks.value = updatedTasks
        applyTaskFilter()
        
        // Persist to TaskStorage
        TaskStorage.saveTasks(jobId, updatedTasks)
        
        android.util.Log.i("JobBoardViewModel", "Auto-distributed ${tasks.size} tasks among ${assignees.size} members")
    }
    
    /**
     * Clear all task assignments
     */
    fun clearTaskAssignments() {
        val jobId = _selectedJob.value?.id ?: return
        val tasks = localTasks[jobId] ?: return
        
        val updatedTasks = tasks.map { task ->
            task.copy(
                assignedTo = null,
                updatedAt = System.currentTimeMillis()
            )
        }
        
        localTasks[jobId] = updatedTasks.toMutableList()
        _tasks.value = updatedTasks
        applyTaskFilter()
        
        // Persist to TaskStorage
        TaskStorage.saveTasks(jobId, updatedTasks)
    }
    
    /**
     * CLAIM a task for yourself (quick swipe-right action)
     * Also auto-assigns related materials to you
     */
    fun claimTask(taskId: String) {
        val currentUserId = UserPreferences.getUserId()
        val job = _selectedJob.value ?: return
        val jobId = job.id
        val tasks = localTasks[jobId] ?: return
        
        // Find the task being claimed
        val task = tasks.find { it.id == taskId } ?: return
        
        // Update the task
        val updatedTasks = tasks.map { t ->
            if (t.id == taskId) {
                t.copy(
                    assignedTo = currentUserId,
                    updatedAt = System.currentTimeMillis()
                )
            } else t
        }
        
        localTasks[jobId] = updatedTasks.toMutableList()
        _tasks.value = updatedTasks
        applyTaskFilter()
        
        // Persist task changes
        TaskStorage.saveTasks(jobId, updatedTasks)
        
        // Auto-assign related materials based on task keywords
        autoAssignRelatedMaterials(job, task.title, currentUserId)
        
        android.util.Log.i("JobBoardViewModel", "Task claimed: ${task.title.take(30)} by $currentUserId")
    }
    
    /**
     * UNCLAIM a task (swipe-left or release)
     */
    fun unclaimTask(taskId: String) {
        assignTask(taskId, null)
    }
    
    /**
     * Auto-assign materials related to a task based on keywords
     * Example: Task "Build firebox" → assigns fire brick, fireclay mortar
     */
    private fun autoAssignRelatedMaterials(job: Job, taskTitle: String, assigneeId: String) {
        val taskWords = taskTitle.lowercase().split(" ", "-", "_")
            .filter { it.length > 3 }
            .toSet()
        
        // Find materials that match task keywords
        val updatedMaterials = job.materials.map { material ->
            val materialWords = material.name.lowercase().split(" ", "-", "_")
            val hasMatch = materialWords.any { word -> 
                taskWords.any { taskWord -> 
                    word.contains(taskWord) || taskWord.contains(word) 
                }
            }
            
            // Only auto-assign if material is currently unassigned AND matches
            if (hasMatch && material.assignedTo == null) {
                material.copy(assignedTo = assigneeId)
            } else {
                material
            }
        }
        
        // Check if any materials were updated
        val changedCount = updatedMaterials.zip(job.materials).count { (new, old) -> 
            new.assignedTo != old.assignedTo 
        }
        
        if (changedCount > 0) {
            // Update the job with new material assignments
            _jobs.value = _jobs.value.map { j ->
                if (j.id == job.id) {
                    j.copy(materials = updatedMaterials, updatedAt = System.currentTimeMillis())
                } else j
            }
            _selectedJob.value = _jobs.value.find { it.id == job.id }
            persistToStorage()
            
            android.util.Log.i("JobBoardViewModel", "Auto-assigned $changedCount materials for task: ${taskTitle.take(30)}")
        }
    }
    
    /**
     * Assign a material to a worker
     */
    fun assignMaterial(jobId: String, materialIndex: Int, assigneeId: String?) {
        _jobs.value = _jobs.value.map { job ->
            if (job.id == jobId) {
                val updatedMaterials = job.materials.toMutableList()
                if (materialIndex < updatedMaterials.size) {
                    updatedMaterials[materialIndex] = updatedMaterials[materialIndex].copy(
                        assignedTo = assigneeId
                    )
                }
                job.copy(materials = updatedMaterials, updatedAt = System.currentTimeMillis())
            } else job
        }
        _selectedJob.value = _jobs.value.find { it.id == jobId }
        persistToStorage()
    }
    
    /**
     * Claim a material for yourself
     */
    fun claimMaterial(jobId: String, materialIndex: Int) {
        val currentUserId = UserPreferences.getUserId()
        assignMaterial(jobId, materialIndex, currentUserId)
    }
    
    /**
     * Get materials assigned to a specific user
     */
    fun getMyMaterials(job: Job): List<IndexedValue<Material>> {
        val currentUserId = UserPreferences.getUserId()
        return job.materials.withIndex()
            .filter { it.value.assignedTo == currentUserId }
            .toList()
    }
    
    /**
     * Get unassigned materials
     */
    fun getUnassignedMaterials(job: Job): List<IndexedValue<Material>> {
        return job.materials.withIndex()
            .filter { it.value.assignedTo == null }
            .toList()
    }

    /**
     * Get list of members who can be assigned tasks
     * Combines crew members and job assignees
     */
    fun getAssignableMembers(job: Job): List<AssignableMember> {
        val members = mutableListOf<AssignableMember>()
        
        // Add current user first
        val currentUserId = UserPreferences.getUserId()
        val currentUserName = UserPreferences.getUserName()
        members.add(AssignableMember(
            id = currentUserId,
            name = currentUserName.ifEmpty { "Me" },
            role = "You"
        ))
        
        // Add crew members
        job.crew.forEach { crewMember ->
            // Create a pseudo-ID for crew members based on their name
            val memberId = "crew_${crewMember.name.lowercase().replace(" ", "_")}"
            if (memberId != currentUserId) {
                members.add(AssignableMember(
                    id = memberId,
                    name = crewMember.name,
                    role = crewMember.occupation
                ))
            }
        }
        
        // Add job assignees that aren't already in the list
        job.assignedTo.forEach { assigneeId ->
            if (members.none { it.id == assigneeId }) {
                members.add(AssignableMember(
                    id = assigneeId,
                    name = assigneeId, // Would be resolved from user lookup
                    role = "Assigned"
                ))
            }
        }
        
        return members
    }
    
    /**
     * Get assignment summary for a job
     */
    fun getTaskAssignmentSummary(job: Job): Map<String, Int> {
        val tasks = localTasks[job.id] ?: return emptyMap()
        val summary = mutableMapOf<String, Int>()
        
        tasks.forEach { task ->
            val assignee = task.assignedTo ?: "Unassigned"
            summary[assignee] = (summary[assignee] ?: 0) + 1
        }
        
        return summary
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
        
        // Persist to storage
        persistToStorage()
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
        
        // Persist to storage
        persistToStorage()
    }

    // ════════════════════════════════════════════════════════════════════
    // INVOICE GENERATION
    // ════════════════════════════════════════════════════════════════════

    private val _generatedInvoice = MutableStateFlow<com.guildofsmiths.trademesh.ui.invoice.Invoice?>(null)
    val generatedInvoice: StateFlow<com.guildofsmiths.trademesh.ui.invoice.Invoice?> = _generatedInvoice.asStateFlow()

    fun generateInvoice(job: Job) {
        viewModelScope.launch {
            val userName = UserPreferences.getUserName()
            
            // Get time entries for this job from shared repository
            val timeEntries = TimeEntryRepository.getEntriesForJob(job.id, job.title)
            
            val invoice = com.guildofsmiths.trademesh.ui.invoice.InvoiceGenerator.generateFromJob(
                job = job,
                timeEntries = timeEntries,
                providerName = userName,
                providerTrade = "Tradesperson – Guild of Smiths"
            )
            
            _generatedInvoice.value = invoice
        }
    }

    fun clearInvoice() {
        _generatedInvoice.value = null
    }

    fun deleteJob(jobId: String) {
        // Remove locally
        _jobs.value = _jobs.value.filter { it.id != jobId }
        localTasks.remove(jobId)
        _selectedJob.value = null
        
        // Sync to repository
        syncToRepository()
        
        // Persist to storage
        persistToStorage()
        
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
        
        if (_selectedJob.value?.id == jobId) {
            _tasks.value = tasks.toList()
        }
        
        // Persist to TaskStorage
        TaskStorage.saveTasks(jobId, tasks.toList())

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

    /**
     * Share job with collaborators
     */
    fun shareJobWithCollaborators(
        jobId: String,
        collaborators: List<String>,
        collaborationMode: CollaborationMode,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            try {
                val currentUserId = UserPreferences.getUserId()

                // Create shared job
                val sharedJob = SharedJobRepository.createSharedJob(
                    jobId = jobId,
                    collaborators = collaborators,
                    leadCollaborator = currentUserId,
                    collaborationMode = collaborationMode
                )

                if (sharedJob != null) {
                    // Send invitations to collaborators
                    sendJobInvitations(sharedJob, collaborators)
                    onSuccess(sharedJob.id)
                } else {
                    onError("Failed to create shared job")
                }
            } catch (e: Exception) {
                onError("Error sharing job: ${e.message}")
            }
        }
    }

    /**
     * Send job invitations to collaborators
     */
    private suspend fun sendJobInvitations(sharedJob: com.guildofsmiths.trademesh.data.SharedJob, collaborators: List<String>) {
        // For now, we'll rely on the app's existing messaging to notify collaborators
        // In a full implementation, this would send push notifications, in-app notifications, etc.

        collaborators.forEach { collaboratorId ->
            // Create in-app notification or message
            // This will be expanded when we implement the full notification system
            Log.i("JobBoardViewModel", "Would send invitation to collaborator: $collaboratorId for job: ${sharedJob.id}")
        }
    }
}
