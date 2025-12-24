package com.guildofsmiths.trademesh.ui.jobboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
 * Manages job board state and API interactions
 */
class JobBoardViewModel : ViewModel() {

    private val client = OkHttpClient()
    private val baseUrl = "http://10.0.2.2:3001" // localhost for emulator

    // ════════════════════════════════════════════════════════════════════
    // STATE
    // ════════════════════════════════════════════════════════════════════

    private val _jobs = MutableStateFlow<List<Job>>(emptyList())
    val jobs: StateFlow<List<Job>> = _jobs.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _selectedJob = MutableStateFlow<Job?>(null)
    val selectedJob: StateFlow<Job?> = _selectedJob.asStateFlow()

    private val _tasks = MutableStateFlow<List<Task>>(emptyList())
    val tasks: StateFlow<List<Task>> = _tasks.asStateFlow()

    // Board columns
    val columns: StateFlow<List<BoardColumn>>
        get() = MutableStateFlow(
            JobStatus.values().filter { it != JobStatus.ARCHIVED }.map { status ->
                BoardColumn(status, _jobs.value.filter { it.status == status })
            }
        )

    init {
        loadJobs()
    }

    // ════════════════════════════════════════════════════════════════════
    // API CALLS
    // ════════════════════════════════════════════════════════════════════

    fun loadJobs() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val token = AuthService.getAccessToken()
            if (token == null) {
                _error.value = "Not authenticated"
                _isLoading.value = false
                return@launch
            }

            val request = Request.Builder()
                .url("$baseUrl/api/jobs")
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    _error.value = "Network error: ${e.message}"
                    _isLoading.value = false
                }

                override fun onResponse(call: Call, response: Response) {
                    _isLoading.value = false
                    if (response.isSuccessful) {
                        try {
                            val json = JSONObject(response.body?.string() ?: "{}")
                            val jobsArray = json.optJSONArray("jobs") ?: JSONArray()
                            val jobsList = mutableListOf<Job>()

                            for (i in 0 until jobsArray.length()) {
                                val jobJson = jobsArray.getJSONObject(i)
                                jobsList.add(parseJob(jobJson))
                            }

                            _jobs.value = jobsList
                        } catch (e: Exception) {
                            _error.value = "Parse error: ${e.message}"
                        }
                    } else {
                        _error.value = "Error: ${response.code}"
                    }
                }
            })
        }
    }

    fun createJob(title: String, description: String = "", priority: Priority = Priority.MEDIUM) {
        viewModelScope.launch {
            val token = AuthService.getAccessToken() ?: return@launch

            val json = JSONObject().apply {
                put("title", title)
                put("description", description)
                put("priority", priority.name.lowercase())
            }

            val request = Request.Builder()
                .url("$baseUrl/api/jobs")
                .header("Authorization", "Bearer $token")
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    _error.value = "Failed to create job: ${e.message}"
                }

                override fun onResponse(call: Call, response: Response) {
                    if (response.isSuccessful) {
                        loadJobs() // Refresh list
                    } else {
                        _error.value = "Create failed: ${response.code}"
                    }
                }
            })
        }
    }

    fun moveJob(jobId: String, newStatus: JobStatus) {
        viewModelScope.launch {
            val token = AuthService.getAccessToken() ?: return@launch

            // Optimistic update
            _jobs.value = _jobs.value.map { job ->
                if (job.id == jobId) job.copy(status = newStatus) else job
            }

            val json = JSONObject().apply {
                put("newStatus", newStatus.name.lowercase())
            }

            val request = Request.Builder()
                .url("$baseUrl/api/jobs/$jobId/move")
                .header("Authorization", "Bearer $token")
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    loadJobs() // Revert on failure
                }

                override fun onResponse(call: Call, response: Response) {
                    if (!response.isSuccessful) {
                        loadJobs() // Revert on error
                    }
                }
            })
        }
    }

    fun selectJob(job: Job?) {
        _selectedJob.value = job
        if (job != null) {
            loadTasksForJob(job.id)
        }
    }

    fun loadTasksForJob(jobId: String) {
        viewModelScope.launch {
            val token = AuthService.getAccessToken() ?: return@launch

            val request = Request.Builder()
                .url("$baseUrl/api/jobs/$jobId/tasks")
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    _error.value = "Failed to load tasks"
                }

                override fun onResponse(call: Call, response: Response) {
                    if (response.isSuccessful) {
                        try {
                            val json = JSONObject(response.body?.string() ?: "{}")
                            val tasksArray = json.optJSONArray("tasks") ?: JSONArray()
                            val tasksList = mutableListOf<Task>()

                            for (i in 0 until tasksArray.length()) {
                                val taskJson = tasksArray.getJSONObject(i)
                                tasksList.add(parseTask(taskJson))
                            }

                            _tasks.value = tasksList
                        } catch (e: Exception) {
                            _error.value = "Parse error"
                        }
                    }
                }
            })
        }
    }

    fun createTask(jobId: String, title: String) {
        viewModelScope.launch {
            val token = AuthService.getAccessToken() ?: return@launch

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
                override fun onFailure(call: Call, e: IOException) {
                    _error.value = "Failed to create task"
                }

                override fun onResponse(call: Call, response: Response) {
                    if (response.isSuccessful) {
                        loadTasksForJob(jobId)
                    }
                }
            })
        }
    }

    fun deleteJob(jobId: String) {
        viewModelScope.launch {
            val token = AuthService.getAccessToken() ?: return@launch

            val request = Request.Builder()
                .url("$baseUrl/api/jobs/$jobId")
                .header("Authorization", "Bearer $token")
                .delete()
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    _error.value = "Failed to delete"
                }

                override fun onResponse(call: Call, response: Response) {
                    if (response.isSuccessful) {
                        _selectedJob.value = null
                        loadJobs()
                    }
                }
            })
        }
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
            dueDate = if (json.has("dueDate")) json.optLong("dueDate") else null,
            completedAt = if (json.has("completedAt")) json.optLong("completedAt") else null,
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
                    checkedAt = if (item.has("checkedAt")) item.optLong("checkedAt") else null,
                    checkedBy = item.optString("checkedBy", null)
                )
            )
        }

        return Task(
            id = json.getString("id"),
            jobId = json.getString("jobId"),
            title = json.getString("title"),
            description = json.optString("description", null),
            status = try {
                TaskStatus.valueOf(json.getString("status").uppercase())
            } catch (e: Exception) {
                TaskStatus.PENDING
            },
            assignedTo = json.optString("assignedTo", null),
            createdBy = json.getString("createdBy"),
            createdAt = json.getLong("createdAt"),
            updatedAt = json.getLong("updatedAt"),
            completedAt = if (json.has("completedAt")) json.optLong("completedAt") else null,
            order = json.optInt("order", 0),
            checklist = checklist
        )
    }
}
