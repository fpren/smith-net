package com.guildofsmiths.trademesh.ui.timetracking

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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

/**
 * C-12: Time Tracking ViewModel
 * Manages time tracking state and API interactions
 */
class TimeTrackingViewModel : ViewModel() {

    private val client = OkHttpClient()
    private val baseUrl = "http://10.0.2.2:3002" // localhost for emulator

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

    // Timer for active clock
    private val _elapsedSeconds = MutableStateFlow(0L)
    val elapsedSeconds: StateFlow<Long> = _elapsedSeconds.asStateFlow()

    init {
        loadStatus()
        loadEntries()
        loadDailySummary()
        startTimer()
    }

    private fun startTimer() {
        viewModelScope.launch {
            while (true) {
                delay(1000)
                val active = _activeEntry.value
                if (active != null) {
                    _elapsedSeconds.value = (System.currentTimeMillis() - active.clockInTime) / 1000
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // API CALLS
    // ════════════════════════════════════════════════════════════════════

    fun loadStatus() {
        viewModelScope.launch {
            val token = AuthService.getAccessToken() ?: return@launch

            val request = Request.Builder()
                .url("$baseUrl/api/status")
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    _error.value = "Network error: ${e.message}"
                }

                override fun onResponse(call: Call, response: Response) {
                    if (response.isSuccessful) {
                        try {
                            val json = JSONObject(response.body?.string() ?: "{}")
                            _isClockedIn.value = json.optBoolean("isClockedIn", false)

                            val activeJson = json.optJSONObject("activeEntry")
                            _activeEntry.value = if (activeJson != null) parseEntry(activeJson) else null

                            if (_activeEntry.value != null) {
                                _elapsedSeconds.value = (System.currentTimeMillis() - _activeEntry.value!!.clockInTime) / 1000
                            }
                        } catch (e: Exception) {
                            _error.value = "Parse error"
                        }
                    }
                }
            })
        }
    }

    fun clockIn(jobId: String? = null, jobTitle: String? = null, entryType: EntryType = EntryType.REGULAR, note: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            val token = AuthService.getAccessToken() ?: return@launch

            val json = JSONObject().apply {
                jobId?.let { put("jobId", it) }
                jobTitle?.let { put("jobTitle", it) }
                put("entryType", entryType.name.lowercase())
                note?.let { put("note", it) }
            }

            val request = Request.Builder()
                .url("$baseUrl/api/clock-in")
                .header("Authorization", "Bearer $token")
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    _error.value = "Clock in failed: ${e.message}"
                    _isLoading.value = false
                }

                override fun onResponse(call: Call, response: Response) {
                    _isLoading.value = false
                    if (response.isSuccessful) {
                        try {
                            val json = JSONObject(response.body?.string() ?: "{}")
                            val entryJson = json.optJSONObject("entry")
                            if (entryJson != null) {
                                _activeEntry.value = parseEntry(entryJson)
                                _isClockedIn.value = true
                                _elapsedSeconds.value = 0
                            }
                        } catch (e: Exception) {
                            _error.value = "Parse error"
                        }
                    } else {
                        try {
                            val json = JSONObject(response.body?.string() ?: "{}")
                            _error.value = json.optString("error", "Clock in failed")
                        } catch (e: Exception) {
                            _error.value = "Clock in failed"
                        }
                    }
                }
            })
        }
    }

    fun clockOut(note: String? = null) {
        viewModelScope.launch {
            _isLoading.value = true
            val token = AuthService.getAccessToken() ?: return@launch

            val json = JSONObject().apply {
                note?.let { put("note", it) }
            }

            val request = Request.Builder()
                .url("$baseUrl/api/clock-out")
                .header("Authorization", "Bearer $token")
                .post(json.toString().toRequestBody("application/json".toMediaType()))
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    _error.value = "Clock out failed: ${e.message}"
                    _isLoading.value = false
                }

                override fun onResponse(call: Call, response: Response) {
                    _isLoading.value = false
                    if (response.isSuccessful) {
                        _activeEntry.value = null
                        _isClockedIn.value = false
                        _elapsedSeconds.value = 0
                        loadEntries()
                        loadDailySummary()
                    } else {
                        _error.value = "Clock out failed"
                    }
                }
            })
        }
    }

    fun loadEntries(limit: Int = 10) {
        viewModelScope.launch {
            val token = AuthService.getAccessToken() ?: return@launch

            val request = Request.Builder()
                .url("$baseUrl/api/entries?limit=$limit")
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    _error.value = "Failed to load entries"
                }

                override fun onResponse(call: Call, response: Response) {
                    if (response.isSuccessful) {
                        try {
                            val json = JSONObject(response.body?.string() ?: "{}")
                            val entriesArray = json.optJSONArray("entries") ?: JSONArray()
                            val entriesList = mutableListOf<TimeEntry>()

                            for (i in 0 until entriesArray.length()) {
                                entriesList.add(parseEntry(entriesArray.getJSONObject(i)))
                            }

                            _entries.value = entriesList
                        } catch (e: Exception) {
                            _error.value = "Parse error"
                        }
                    }
                }
            })
        }
    }

    fun loadDailySummary(date: String? = null) {
        viewModelScope.launch {
            val token = AuthService.getAccessToken() ?: return@launch

            val url = if (date != null) "$baseUrl/api/summary/daily?date=$date" else "$baseUrl/api/summary/daily"

            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    // Ignore
                }

                override fun onResponse(call: Call, response: Response) {
                    if (response.isSuccessful) {
                        try {
                            val json = JSONObject(response.body?.string() ?: "{}")
                            val summaryJson = json.optJSONObject("summary")
                            if (summaryJson != null) {
                                _dailySummary.value = parseDailySummary(summaryJson)
                            }
                        } catch (e: Exception) {
                            // Ignore
                        }
                    }
                }
            })
        }
    }

    fun loadWeeklySummary() {
        viewModelScope.launch {
            val token = AuthService.getAccessToken() ?: return@launch

            val request = Request.Builder()
                .url("$baseUrl/api/summary/weekly")
                .header("Authorization", "Bearer $token")
                .get()
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    // Ignore
                }

                override fun onResponse(call: Call, response: Response) {
                    if (response.isSuccessful) {
                        try {
                            val json = JSONObject(response.body?.string() ?: "{}")
                            val summaryJson = json.optJSONObject("summary")
                            if (summaryJson != null) {
                                _weeklySummary.value = parseWeeklySummary(summaryJson)
                            }
                        } catch (e: Exception) {
                            // Ignore
                        }
                    }
                }
            })
        }
    }

    fun clearError() {
        _error.value = null
    }

    // ════════════════════════════════════════════════════════════════════
    // PARSING
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
            jobId = json.optString("jobId", null),
            jobTitle = json.optString("jobTitle", null),
            projectId = json.optString("projectId", null),
            location = json.optString("location", null),
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
