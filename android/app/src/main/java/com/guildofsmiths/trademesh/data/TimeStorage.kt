package com.guildofsmiths.trademesh.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.guildofsmiths.trademesh.ui.timetracking.*
import org.json.JSONArray
import org.json.JSONObject

/**
 * TimeStorage - Persistent storage for Time Entries
 * 
 * Stores time entries and timer state in SharedPreferences as JSON.
 * Survives app restart, works offline.
 */
object TimeStorage {
    
    private const val TAG = "TimeStorage"
    private const val PREFS_NAME = "trademesh_time"
    private const val KEY_ENTRIES = "time_entries"
    private const val KEY_ACTIVE_ENTRY_ID = "active_entry_id"
    private const val KEY_CUSTOM_JOBS = "custom_jobs"
    
    private var prefs: SharedPreferences? = null
    
    /**
     * Initialize storage with context.
     * Call this in Application.onCreate()
     */
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        Log.d(TAG, "TimeStorage initialized")
    }
    
    /**
     * Save all time entries
     * Uses commit() for synchronous/atomic save to ensure data persists immediately
     */
    fun saveEntries(entries: List<TimeEntry>): Boolean {
        val jsonArray = JSONArray()
        entries.forEach { entry ->
            jsonArray.put(entryToJson(entry))
        }
        val success = prefs?.edit()?.putString(KEY_ENTRIES, jsonArray.toString())?.commit() ?: false
        Log.d(TAG, "Saved ${entries.size} time entries (success: $success)")
        return success
    }
    
    /**
     * Load all time entries
     */
    fun loadEntries(): List<TimeEntry> {
        val jsonStr = prefs?.getString(KEY_ENTRIES, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(jsonStr)
            val entries = mutableListOf<TimeEntry>()
            for (i in 0 until jsonArray.length()) {
                entries.add(jsonToEntry(jsonArray.getJSONObject(i)))
            }
            Log.d(TAG, "Loaded ${entries.size} time entries")
            entries
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load entries: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Save ID of currently active (clocked-in) entry
     * Uses commit() for synchronous/atomic save
     */
    fun saveActiveEntryId(entryId: String?): Boolean {
        val success = if (entryId == null) {
            prefs?.edit()?.remove(KEY_ACTIVE_ENTRY_ID)?.commit() ?: false
        } else {
            prefs?.edit()?.putString(KEY_ACTIVE_ENTRY_ID, entryId)?.commit() ?: false
        }
        Log.d(TAG, "Saved active entry ID: $entryId (success: $success)")
        return success
    }
    
    /**
     * Get ID of currently active entry
     */
    fun getActiveEntryId(): String? {
        return prefs?.getString(KEY_ACTIVE_ENTRY_ID, null)
    }
    
    /**
     * Save custom job names (for clock-in dropdown)
     * Uses commit() for synchronous/atomic save
     */
    fun saveCustomJobs(jobs: Set<String>): Boolean {
        val jsonArray = JSONArray(jobs.toList())
        return prefs?.edit()?.putString(KEY_CUSTOM_JOBS, jsonArray.toString())?.commit() ?: false
    }
    
    /**
     * Load custom job names
     */
    fun loadCustomJobs(): Set<String> {
        val jsonStr = prefs?.getString(KEY_CUSTOM_JOBS, null) ?: return emptySet()
        return try {
            val jsonArray = JSONArray(jsonStr)
            val jobs = mutableSetOf<String>()
            for (i in 0 until jsonArray.length()) {
                jobs.add(jsonArray.getString(i))
            }
            jobs
        } catch (e: Exception) {
            emptySet()
        }
    }
    
    /**
     * Convert TimeEntry to JSON
     */
    private fun entryToJson(entry: TimeEntry): JSONObject {
        return JSONObject().apply {
            put("id", entry.id)
            put("userId", entry.userId)
            put("userName", entry.userName)
            put("clockInTime", entry.clockInTime)
            put("clockOutTime", entry.clockOutTime)
            put("durationMinutes", entry.durationMinutes)
            put("jobId", entry.jobId)
            put("jobTitle", entry.jobTitle)
            put("projectId", entry.projectId)
            put("location", entry.location)
            put("entryType", entry.entryType.name)
            put("source", entry.source.name)
            put("createdAt", entry.createdAt)
            put("immutableHash", entry.immutableHash)
            put("status", entry.status.name)
            
            // Notes
            val notesArray = JSONArray()
            entry.notes.forEach { note ->
                notesArray.put(JSONObject().apply {
                    put("id", note.id)
                    put("text", note.text)
                    put("addedBy", note.addedBy)
                    put("addedAt", note.addedAt)
                    put("type", note.type)
                })
            }
            put("notes", notesArray)
        }
    }
    
    /**
     * Convert JSON to TimeEntry
     */
    private fun jsonToEntry(json: JSONObject): TimeEntry {
        // Parse notes
        val notesArray = json.optJSONArray("notes") ?: JSONArray()
        val notes = mutableListOf<TimeNote>()
        for (i in 0 until notesArray.length()) {
            val noteJson = notesArray.getJSONObject(i)
            notes.add(TimeNote(
                id = noteJson.optString("id", java.util.UUID.randomUUID().toString()),
                text = noteJson.optString("text", ""),
                addedBy = noteJson.optString("addedBy", ""),
                addedAt = noteJson.optLong("addedAt", System.currentTimeMillis()),
                type = noteJson.optString("type", "note")
            ))
        }
        
        return TimeEntry(
            id = json.getString("id"),
            userId = json.optString("userId", ""),
            userName = json.optString("userName", ""),
            clockInTime = json.optLong("clockInTime", System.currentTimeMillis()),
            clockOutTime = if (json.has("clockOutTime") && !json.isNull("clockOutTime")) json.optLong("clockOutTime") else null,
            durationMinutes = if (json.has("durationMinutes") && !json.isNull("durationMinutes")) json.optInt("durationMinutes") else null,
            jobId = json.optString("jobId", null).takeIf { it?.isNotEmpty() == true },
            jobTitle = json.optString("jobTitle", null).takeIf { it?.isNotEmpty() == true },
            projectId = json.optString("projectId", null).takeIf { it?.isNotEmpty() == true },
            location = json.optString("location", null).takeIf { it?.isNotEmpty() == true },
            entryType = try {
                EntryType.valueOf(json.optString("entryType", "REGULAR").uppercase())
            } catch (e: Exception) {
                EntryType.REGULAR
            },
            source = try {
                EntrySource.valueOf(json.optString("source", "MANUAL").uppercase())
            } catch (e: Exception) {
                EntrySource.MANUAL
            },
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            immutableHash = json.optString("immutableHash", ""),
            notes = notes,
            status = try {
                EntryStatus.valueOf(json.optString("status", "COMPLETED").uppercase())
            } catch (e: Exception) {
                EntryStatus.COMPLETED
            }
        )
    }
    
    /**
     * Clear all stored data (for testing)
     */
    fun clear() {
        prefs?.edit()?.clear()?.commit()
    }
    
    /**
     * Get entry count without loading full data
     */
    fun getEntryCount(): Int {
        val jsonStr = prefs?.getString(KEY_ENTRIES, null) ?: return 0
        return try {
            JSONArray(jsonStr).length()
        } catch (e: Exception) {
            0
        }
    }
    
    /**
     * Check if storage is initialized
     */
    fun isInitialized(): Boolean = prefs != null
}
