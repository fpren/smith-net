package com.guildofsmiths.trademesh.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.guildofsmiths.trademesh.ui.jobboard.Task
import com.guildofsmiths.trademesh.ui.jobboard.TaskStatus
import com.guildofsmiths.trademesh.ui.jobboard.ChecklistItem
import org.json.JSONArray
import org.json.JSONObject

/**
 * TaskStorage - Persistent storage for Tasks (linked to Jobs by jobId)
 * 
 * Stores tasks in SharedPreferences as JSON, keyed by jobId.
 * Survives app restart, works offline.
 */
object TaskStorage {
    
    private const val TAG = "TaskStorage"
    private const val PREFS_NAME = "trademesh_tasks"
    private const val KEY_TASKS_PREFIX = "tasks_"
    
    private var prefs: SharedPreferences? = null
    
    /**
     * Initialize storage with context.
     * Call this in Application.onCreate()
     */
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        Log.d(TAG, "TaskStorage initialized")
    }
    
    /**
     * Save tasks for a specific job
     */
    fun saveTasks(jobId: String, tasks: List<Task>): Boolean {
        val jsonArray = JSONArray()
        tasks.forEach { task ->
            jsonArray.put(taskToJson(task))
        }
        val key = KEY_TASKS_PREFIX + jobId
        val success = prefs?.edit()?.putString(key, jsonArray.toString())?.commit() ?: false
        Log.d(TAG, "Saved ${tasks.size} tasks for job $jobId (success: $success)")
        return success
    }
    
    /**
     * Load tasks for a specific job
     */
    fun loadTasks(jobId: String): List<Task> {
        val key = KEY_TASKS_PREFIX + jobId
        val jsonStr = prefs?.getString(key, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(jsonStr)
            val tasks = mutableListOf<Task>()
            for (i in 0 until jsonArray.length()) {
                tasks.add(jsonToTask(jsonArray.getJSONObject(i)))
            }
            Log.d(TAG, "Loaded ${tasks.size} tasks for job $jobId")
            tasks
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load tasks for job $jobId: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Delete tasks for a specific job
     */
    fun deleteTasks(jobId: String): Boolean {
        val key = KEY_TASKS_PREFIX + jobId
        val success = prefs?.edit()?.remove(key)?.commit() ?: false
        Log.d(TAG, "Deleted tasks for job $jobId (success: $success)")
        return success
    }
    
    /**
     * Get task count for a job without loading full data
     */
    fun getTaskCount(jobId: String): Int {
        val key = KEY_TASKS_PREFIX + jobId
        val jsonStr = prefs?.getString(key, null) ?: return 0
        return try {
            JSONArray(jsonStr).length()
        } catch (e: Exception) {
            0
        }
    }
    
    /**
     * Convert Task to JSON
     */
    private fun taskToJson(task: Task): JSONObject {
        return JSONObject().apply {
            put("id", task.id)
            put("jobId", task.jobId)
            put("title", task.title)
            put("description", task.description)
            put("status", task.status.name)
            put("assignedTo", task.assignedTo)
            put("createdBy", task.createdBy)
            put("createdAt", task.createdAt)
            put("updatedAt", task.updatedAt)
            put("completedAt", task.completedAt)
            put("order", task.order)
            
            // Checklist items
            val checklistArray = JSONArray()
            task.checklist.forEach { item ->
                checklistArray.put(JSONObject().apply {
                    put("id", item.id)
                    put("text", item.text)
                    put("checked", item.checked)
                    put("checkedAt", item.checkedAt)
                    put("checkedBy", item.checkedBy)
                })
            }
            put("checklist", checklistArray)
        }
    }
    
    /**
     * Convert JSON to Task
     */
    private fun jsonToTask(json: JSONObject): Task {
        // Parse checklist
        val checklistArray = json.optJSONArray("checklist") ?: JSONArray()
        val checklist = mutableListOf<ChecklistItem>()
        for (i in 0 until checklistArray.length()) {
            val itemJson = checklistArray.getJSONObject(i)
            checklist.add(ChecklistItem(
                id = itemJson.optString("id", ""),
                text = itemJson.optString("text", ""),
                checked = itemJson.optBoolean("checked", false),
                checkedAt = if (itemJson.has("checkedAt") && !itemJson.isNull("checkedAt")) itemJson.optLong("checkedAt") else null,
                checkedBy = itemJson.optString("checkedBy", null).takeIf { it?.isNotEmpty() == true }
            ))
        }
        
        return Task(
            id = json.getString("id"),
            jobId = json.getString("jobId"),
            title = json.optString("title", ""),
            description = json.optString("description", null).takeIf { it?.isNotEmpty() == true },
            status = try {
                TaskStatus.valueOf(json.optString("status", "PENDING").uppercase())
            } catch (e: Exception) {
                TaskStatus.PENDING
            },
            assignedTo = json.optString("assignedTo", null).takeIf { it?.isNotEmpty() == true },
            createdBy = json.optString("createdBy", ""),
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
            completedAt = if (json.has("completedAt") && !json.isNull("completedAt")) json.optLong("completedAt") else null,
            order = json.optInt("order", 0),
            checklist = checklist
        )
    }
    
    /**
     * Check if storage is initialized
     */
    fun isInitialized(): Boolean = prefs != null
}
