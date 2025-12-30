package com.guildofsmiths.trademesh.ui.jobboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
        loadJobs()
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
        _archivedJobs.value = _archivedJobs.value.filter { it.id != jobId }
        localTasks.remove(jobId)
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
