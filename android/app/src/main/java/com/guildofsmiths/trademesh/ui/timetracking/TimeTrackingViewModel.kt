package com.guildofsmiths.trademesh.ui.timetracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.guildofsmiths.trademesh.data.JobRepository
import com.guildofsmiths.trademesh.data.TimeEntryRepository
import com.guildofsmiths.trademesh.data.UserPreferences
import com.guildofsmiths.trademesh.service.AuthService
import kotlinx.coroutines.delay
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
import java.text.SimpleDateFormat
import java.util.*

/**
 * C-12: Time Tracking ViewModel
 * Manages time tracking state - works locally with optional backend sync
 */
class TimeTrackingViewModel : ViewModel() {

    private val client = OkHttpClient()
    private val baseUrl = "http://10.0.2.2:3002"

    // ════════════════════════════════════════════════════════════════════
    // STATE
    // ════════════════════════════════════════════════════════════════════

    private val _isClockedIn = MutableStateFlow(false)
    val isClockedIn: StateFlow<Boolean> = _isClockedIn.asStateFlow()

    private val _activeEntry = MutableStateFlow<TimeEntry?>(null)
    val activeEntry: StateFlow<TimeEntry?> = _activeEntry.asStateFlow()

    private val _entries = MutableStateFlow<List<TimeEntry>>(emptyList())
    val entries: StateFlow<List<TimeEntry>> = _entries.asStateFlow()

    private val _dailySummary = MutableStateFlow<DailySummary?>(null)
    val dailySummary: StateFlow<DailySummary?> = _dailySummary.asStateFlow()

    private val _weeklySummary = MutableStateFlow<WeeklySummary?>(null)
    val weeklySummary: StateFlow<WeeklySummary?> = _weeklySummary.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Available jobs (from job board or custom)
    private val _availableJobs = MutableStateFlow<List<String>>(emptyList())
    val availableJobs: StateFlow<List<String>> = _availableJobs.asStateFlow()

    // Local storage for entries
    private val localEntries = mutableListOf<TimeEntry>()
    
    // Custom job names (saved locally)
    private val customJobs = mutableSetOf<String>()

    init {
        loadStatus()
        loadEntries()
        loadDailySummary()
        loadAvailableJobs()
    }
    
    private fun loadAvailableJobs() {
        // Get active jobs from Job Board + custom jobs
        val jobBoardJobs = JobRepository.getActiveJobTitles()
        val allJobs = (jobBoardJobs + customJobs).distinct()
        _availableJobs.value = allJobs
    }
    
    fun refreshAvailableJobs() {
        loadAvailableJobs()
    }

    // ════════════════════════════════════════════════════════════════════
    // LOCAL OPERATIONS (work without backend)
    // ════════════════════════════════════════════════════════════════════

    fun loadStatus() {
        // Check if we have an active local entry
        val active = localEntries.find { it.clockOutTime == null }
        if (active != null) {
            _activeEntry.value = active
            _isClockedIn.value = true
        }
        
        // Also try backend
        tryLoadStatusFromBackend()
    }

    fun clockIn(jobId: String? = null, jobTitle: String? = null, entryType: EntryType = EntryType.REGULAR, note: String? = null) {
        if (_isClockedIn.value) {
            _error.value = "Already clocked in"
            return
        }

        _isLoading.value = true
        
        val userId = UserPreferences.getUserId()
        val userName = UserPreferences.getUserName()
        val now = System.currentTimeMillis()
        
        val entry = TimeEntry(
            id = UUID.randomUUID().toString(),
            userId = userId,
            userName = userName,
            clockInTime = now,
            clockOutTime = null,
            durationMinutes = null,
            jobId = jobId,
            jobTitle = jobTitle,
            entryType = entryType,
            source = EntrySource.MANUAL,
            createdAt = now,
            immutableHash = generateHash(userId, now),
            notes = if (note != null) listOf(
                TimeNote(
                    id = UUID.randomUUID().toString(),
                    text = note,
                    addedBy = userId,
                    addedAt = now,
                    type = "clock_in"
                )
            ) else emptyList(),
            status = EntryStatus.ACTIVE
        )

        // Add to local storage
        localEntries.add(0, entry)
        _activeEntry.value = entry
        _isClockedIn.value = true
        
        // Sync to shared repository for invoice generator
        TimeEntryRepository.addEntry(entry)
        _isLoading.value = false
        
        // Save custom job name for future use
        jobTitle?.let { 
            customJobs.add(it)
            _availableJobs.value = customJobs.toList()
        }
        
        // Update entries list
        _entries.value = localEntries.toList()
        
        // Try to sync to backend
        syncClockInToBackend(entry)
    }

    fun clockOut(note: String? = null) {
        val active = _activeEntry.value
        if (active == null) {
            _error.value = "Not clocked in"
            return
        }

        _isLoading.value = true
        
        val now = System.currentTimeMillis()
        val durationMinutes = ((now - active.clockInTime) / 60000).toInt()
        
        // Update the entry
        val updatedNotes = active.notes.toMutableList()
        if (note != null) {
            updatedNotes.add(
                TimeNote(
                    id = UUID.randomUUID().toString(),
                    text = note,
                    addedBy = active.userId,
                    addedAt = now,
                    type = "clock_out"
                )
            )
        }
        
        val completedEntry = active.copy(
            clockOutTime = now,
            durationMinutes = durationMinutes,
            notes = updatedNotes,
            status = EntryStatus.COMPLETED
        )

        // Update local storage
        val index = localEntries.indexOfFirst { it.id == active.id }
        if (index >= 0) {
            localEntries[index] = completedEntry
        }

        _activeEntry.value = null
        _isClockedIn.value = false
        _isLoading.value = false
        
        // Update entries list and summary
        _entries.value = localEntries.toList()
        updateDailySummary()
        
        // Sync completed entry to shared repository for invoice generator
        TimeEntryRepository.addEntry(completedEntry)
        
        // Try to sync to backend
        syncClockOutToBackend(completedEntry)
    }

    fun loadEntries(limit: Int = 10) {
        // Return local entries
        _entries.value = localEntries.take(limit)
        
        // Also try backend
        tryLoadEntriesFromBackend(limit)
    }

    fun loadDailySummary(date: String? = null) {
        updateDailySummary()
        
        // Also try backend
        tryLoadDailySummaryFromBackend(date)
    }

    private fun updateDailySummary() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val userId = UserPreferences.getUserId()
        
        val todayEntries = localEntries.filter { entry ->
            val entryDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(entry.clockInTime))
            entryDate == today
        }

        var totalMinutes = 0
        var regularMinutes = 0
        var overtimeMinutes = 0
        var breakMinutes = 0

        todayEntries.forEach { entry ->
            val mins = entry.durationMinutes ?: if (entry.clockOutTime == null) {
                ((System.currentTimeMillis() - entry.clockInTime) / 60000).toInt()
            } else 0
            
            totalMinutes += mins
            when (entry.entryType) {
                EntryType.REGULAR -> regularMinutes += mins
                EntryType.OVERTIME -> overtimeMinutes += mins
                EntryType.BREAK -> breakMinutes += mins
                else -> regularMinutes += mins
            }
        }

        _dailySummary.value = DailySummary(
            date = today,
            userId = userId,
            entries = todayEntries,
            totalMinutes = totalMinutes,
            regularMinutes = regularMinutes,
            overtimeMinutes = overtimeMinutes,
            breakMinutes = breakMinutes
        )
    }

    fun clearError() {
        _error.value = null
    }
    
    fun deleteEntry(entryId: String) {
        // Don't allow deleting active entry
        if (_activeEntry.value?.id == entryId) {
            _error.value = "Cannot delete active entry. Clock out first."
            return
        }
        
        // Remove from local storage
        localEntries.removeAll { it.id == entryId }
        
        // Update entries list and summary
        _entries.value = localEntries.toList()
        updateDailySummary()
        
        // Try to sync deletion to backend
        syncDeleteToBackend(entryId)
    }
    
    private fun syncDeleteToBackend(entryId: String) {
        val token = AuthService.getAccessToken() ?: return
        
        val request = Request.Builder()
            .url("$baseUrl/api/entries/$entryId")
            .header("Authorization", "Bearer $token")
            .delete()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {}
        })
    }

    private fun generateHash(userId: String, timestamp: Long): String {
        return "${userId}_${timestamp}".hashCode().toString(16)
    }

    // ════════════════════════════════════════════════════════════════════
    // BACKEND SYNC (optional - works if server is running)
    // ════════════════════════════════════════════════════════════════════

    private fun tryLoadStatusFromBackend() {
        val token = AuthService.getAccessToken() ?: return

        val request = Request.Builder()
            .url("$baseUrl/api/status")
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Backend not available - use local data
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    try {
                        val json = JSONObject(response.body?.string() ?: "{}")
                        val backendClockedIn = json.optBoolean("isClockedIn", false)
                        val activeJson = json.optJSONObject("activeEntry")
                        
                        // Only override if we're not locally clocked in
                        if (!_isClockedIn.value && backendClockedIn && activeJson != null) {
                            _activeEntry.value = parseEntry(activeJson)
                            _isClockedIn.value = true
                        }
                    } catch (e: Exception) {
                        // Ignore parse errors
                    }
                }
            }
        })
    }

    private fun syncClockInToBackend(entry: TimeEntry) {
        val token = AuthService.getAccessToken() ?: return

        val json = JSONObject().apply {
            entry.jobId?.let { put("jobId", it) }
            entry.jobTitle?.let { put("jobTitle", it) }
            put("entryType", entry.entryType.name.lowercase())
        }

        val request = Request.Builder()
            .url("$baseUrl/api/clock-in")
            .header("Authorization", "Bearer $token")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {}
        })
    }

    private fun syncClockOutToBackend(entry: TimeEntry) {
        val token = AuthService.getAccessToken() ?: return

        val json = JSONObject().apply {
            entry.notes.lastOrNull()?.let { put("note", it.text) }
        }

        val request = Request.Builder()
            .url("$baseUrl/api/clock-out")
            .header("Authorization", "Bearer $token")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}
            override fun onResponse(call: Call, response: Response) {}
        })
    }

    private fun tryLoadEntriesFromBackend(limit: Int) {
        val token = AuthService.getAccessToken() ?: return

        val request = Request.Builder()
            .url("$baseUrl/api/entries?limit=$limit")
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    try {
                        val json = JSONObject(response.body?.string() ?: "{}")
                        val entriesArray = json.optJSONArray("entries") ?: JSONArray()
                        
                        // Merge backend entries with local
                        val localIds = localEntries.map { it.id }.toSet()
                        for (i in 0 until entriesArray.length()) {
                            val entry = parseEntry(entriesArray.getJSONObject(i))
                            if (entry.id !in localIds) {
                                localEntries.add(entry)
                            }
                        }
                        
                        // Sort by clock in time descending
                        localEntries.sortByDescending { it.clockInTime }
                        _entries.value = localEntries.take(limit)
                    } catch (e: Exception) {}
                }
            }
        })
    }

    private fun tryLoadDailySummaryFromBackend(date: String?) {
        val token = AuthService.getAccessToken() ?: return

        val url = if (date != null) "$baseUrl/api/summary/daily?date=$date" else "$baseUrl/api/summary/daily"

        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .get()
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {}

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    try {
                        val json = JSONObject(response.body?.string() ?: "{}")
                        val summaryJson = json.optJSONObject("summary")
                        if (summaryJson != null) {
                            // Merge with local data
                            val backendSummary = parseDailySummary(summaryJson)
                            val localSummary = _dailySummary.value
                            
                            if (localSummary != null) {
                                // Combine totals
                                _dailySummary.value = localSummary.copy(
                                    totalMinutes = maxOf(localSummary.totalMinutes, backendSummary.totalMinutes)
                                )
                            }
                        }
                    } catch (e: Exception) {}
                }
            }
        })
    }

    // ════════════════════════════════════════════════════════════════════
    // PARSING (for backend responses)
    // ════════════════════════════════════════════════════════════════════

    private fun parseEntry(json: JSONObject): TimeEntry {
        val notesArray = json.optJSONArray("notes") ?: JSONArray()
        val notes = mutableListOf<TimeNote>()
        for (i in 0 until notesArray.length()) {
            val noteJson = notesArray.getJSONObject(i)
            notes.add(
                TimeNote(
                    id = noteJson.getString("id"),
                    text = noteJson.getString("text"),
                    addedBy = noteJson.getString("addedBy"),
                    addedAt = noteJson.getLong("addedAt"),
                    type = noteJson.optString("type", "note")
                )
            )
        }

        return TimeEntry(
            id = json.getString("id"),
            userId = json.getString("userId"),
            userName = json.getString("userName"),
            clockInTime = json.getLong("clockInTime"),
            clockOutTime = if (json.has("clockOutTime") && !json.isNull("clockOutTime")) json.optLong("clockOutTime") else null,
            durationMinutes = if (json.has("durationMinutes") && !json.isNull("durationMinutes")) json.optInt("durationMinutes") else null,
            jobId = if (json.has("jobId") && !json.isNull("jobId")) json.optString("jobId") else null,
            jobTitle = if (json.has("jobTitle") && !json.isNull("jobTitle")) json.optString("jobTitle") else null,
            projectId = if (json.has("projectId") && !json.isNull("projectId")) json.optString("projectId") else null,
            location = if (json.has("location") && !json.isNull("location")) json.optString("location") else null,
            entryType = try {
                EntryType.valueOf(json.getString("entryType").uppercase())
            } catch (e: Exception) {
                EntryType.REGULAR
            },
            source = try {
                EntrySource.valueOf(json.getString("source").uppercase())
            } catch (e: Exception) {
                EntrySource.MANUAL
            },
            createdAt = json.getLong("createdAt"),
            immutableHash = json.getString("immutableHash"),
            notes = notes,
            status = try {
                EntryStatus.valueOf(json.getString("status").uppercase())
            } catch (e: Exception) {
                EntryStatus.COMPLETED
            }
        )
    }

    private fun parseDailySummary(json: JSONObject): DailySummary {
        val entriesArray = json.optJSONArray("entries") ?: JSONArray()
        val entries = mutableListOf<TimeEntry>()
        for (i in 0 until entriesArray.length()) {
            entries.add(parseEntry(entriesArray.getJSONObject(i)))
        }

        return DailySummary(
            date = json.getString("date"),
            userId = json.getString("userId"),
            entries = entries,
            totalMinutes = json.optInt("totalMinutes", 0),
            regularMinutes = json.optInt("regularMinutes", 0),
            overtimeMinutes = json.optInt("overtimeMinutes", 0),
            breakMinutes = json.optInt("breakMinutes", 0)
        )
    }

    private fun parseWeeklySummary(json: JSONObject): WeeklySummary {
        val dailyArray = json.optJSONArray("dailySummaries") ?: JSONArray()
        val dailySummaries = mutableListOf<DailySummary>()
        for (i in 0 until dailyArray.length()) {
            dailySummaries.add(parseDailySummary(dailyArray.getJSONObject(i)))
        }

        return WeeklySummary(
            weekStart = json.getString("weekStart"),
            userId = json.getString("userId"),
            dailySummaries = dailySummaries,
            totalMinutes = json.optInt("totalMinutes", 0),
            regularMinutes = json.optInt("regularMinutes", 0),
            overtimeMinutes = json.optInt("overtimeMinutes", 0)
        )
    }
}
