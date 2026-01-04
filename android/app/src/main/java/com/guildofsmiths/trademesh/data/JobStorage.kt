package com.guildofsmiths.trademesh.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.guildofsmiths.trademesh.ui.jobboard.*
import org.json.JSONArray
import org.json.JSONObject

/**
 * JobStorage - Persistent storage for Jobs
 * 
 * Stores jobs in SharedPreferences as JSON.
 * Survives app restart, works offline.
 */
object JobStorage {
    
    private const val TAG = "JobStorage"
    private const val PREFS_NAME = "trademesh_jobs"
    private const val KEY_JOBS = "jobs"
    private const val KEY_ARCHIVED_JOBS = "archived_jobs"
    
    private var prefs: SharedPreferences? = null
    
    /**
     * Initialize storage with context.
     * Call this in Application.onCreate()
     */
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        Log.d(TAG, "JobStorage initialized")
    }
    
    /**
     * Save all active jobs
     * Uses commit() for synchronous/atomic save to ensure data persists immediately
     */
    fun saveJobs(jobs: List<Job>): Boolean {
        val jsonArray = JSONArray()
        jobs.forEach { job ->
            jsonArray.put(jobToJson(job))
        }
        val success = prefs?.edit()?.putString(KEY_JOBS, jsonArray.toString())?.commit() ?: false
        Log.d(TAG, "Saved ${jobs.size} jobs (success: $success)")
        return success
    }
    
    /**
     * Load all active jobs
     */
    fun loadJobs(): List<Job> {
        val jsonStr = prefs?.getString(KEY_JOBS, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(jsonStr)
            val jobs = mutableListOf<Job>()
            for (i in 0 until jsonArray.length()) {
                jobs.add(jsonToJob(jsonArray.getJSONObject(i)))
            }
            Log.d(TAG, "Loaded ${jobs.size} jobs")
            jobs
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load jobs: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Save all archived jobs
     * Uses commit() for synchronous/atomic save to ensure data persists immediately
     */
    fun saveArchivedJobs(jobs: List<Job>): Boolean {
        val jsonArray = JSONArray()
        jobs.forEach { job ->
            jsonArray.put(jobToJson(job))
        }
        val success = prefs?.edit()?.putString(KEY_ARCHIVED_JOBS, jsonArray.toString())?.commit() ?: false
        Log.d(TAG, "Saved ${jobs.size} archived jobs (success: $success)")
        return success
    }
    
    /**
     * Load all archived jobs
     */
    fun loadArchivedJobs(): List<Job> {
        val jsonStr = prefs?.getString(KEY_ARCHIVED_JOBS, null) ?: return emptyList()
        return try {
            val jsonArray = JSONArray(jsonStr)
            val jobs = mutableListOf<Job>()
            for (i in 0 until jsonArray.length()) {
                jobs.add(jsonToJob(jsonArray.getJSONObject(i)))
            }
            Log.d(TAG, "Loaded ${jobs.size} archived jobs")
            jobs
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load archived jobs: ${e.message}")
            emptyList()
        }
    }
    
    /**
     * Convert Job to JSON
     */
    private fun jobToJson(job: Job): JSONObject {
        return JSONObject().apply {
            put("id", job.id)
            put("title", job.title)
            put("description", job.description)
            put("projectId", job.projectId)
            put("clientName", job.clientName)
            put("location", job.location)
            put("status", job.status.name)
            put("priority", job.priority.name)
            put("createdBy", job.createdBy)
            put("createdAt", job.createdAt)
            put("updatedAt", job.updatedAt)
            put("dueDate", job.dueDate)
            put("completedAt", job.completedAt)
            put("toolsNeeded", job.toolsNeeded)
            put("expenses", job.expenses)
            put("crewSize", job.crewSize)
            put("estimatedStartDate", job.estimatedStartDate)
            put("estimatedEndDate", job.estimatedEndDate)
            put("actualStartDate", job.actualStartDate)
            put("actualEndDate", job.actualEndDate)
            put("chatSummary", job.chatSummary)
            put("isArchived", job.isArchived)
            put("archivedAt", job.archivedAt)
            put("archiveReason", job.archiveReason)
            put("relatedChannelId", job.relatedChannelId)
            
            // Tags
            put("tags", JSONArray(job.tags))
            
            // Assigned to
            put("assignedTo", JSONArray(job.assignedTo))
            
            // Related message IDs
            put("relatedMessageIds", JSONArray(job.relatedMessageIds))
            
            // Crew members
            val crewArray = JSONArray()
            job.crew.forEach { member ->
                crewArray.put(JSONObject().apply {
                    put("name", member.name)
                    put("occupation", member.occupation)
                    put("task", member.task)
                })
            }
            put("crew", crewArray)
            
            // Materials
            val materialsArray = JSONArray()
            job.materials.forEach { material ->
                materialsArray.put(JSONObject().apply {
                    put("name", material.name)
                    put("notes", material.notes)
                    put("checked", material.checked)
                    put("checkedAt", material.checkedAt)
                    put("quantity", material.quantity)
                    put("unit", material.unit)
                    put("unitCost", material.unitCost)
                    put("totalCost", material.totalCost)
                    put("vendor", material.vendor)
                    put("assignedTo", material.assignedTo)
                    put("relatedTaskId", material.relatedTaskId)
                })
            }
            put("materials", materialsArray)
            
            // Work log
            val workLogArray = JSONArray()
            job.workLog.forEach { entry ->
                workLogArray.put(JSONObject().apply {
                    put("text", entry.text)
                    put("timestamp", entry.timestamp)
                    put("author", entry.author)
                })
            }
            put("workLog", workLogArray)
        }
    }
    
    /**
     * Convert JSON to Job
     */
    private fun jsonToJob(json: JSONObject): Job {
        // Parse tags
        val tagsArray = json.optJSONArray("tags") ?: JSONArray()
        val tags = mutableListOf<String>()
        for (i in 0 until tagsArray.length()) {
            tags.add(tagsArray.getString(i))
        }
        
        // Parse assigned to
        val assignedArray = json.optJSONArray("assignedTo") ?: JSONArray()
        val assignedTo = mutableListOf<String>()
        for (i in 0 until assignedArray.length()) {
            assignedTo.add(assignedArray.getString(i))
        }
        
        // Parse related message IDs
        val messageIdsArray = json.optJSONArray("relatedMessageIds") ?: JSONArray()
        val relatedMessageIds = mutableListOf<String>()
        for (i in 0 until messageIdsArray.length()) {
            relatedMessageIds.add(messageIdsArray.getString(i))
        }
        
        // Parse crew
        val crewArray = json.optJSONArray("crew") ?: JSONArray()
        val crew = mutableListOf<CrewMember>()
        for (i in 0 until crewArray.length()) {
            val memberJson = crewArray.getJSONObject(i)
            crew.add(CrewMember(
                name = memberJson.optString("name", ""),
                occupation = memberJson.optString("occupation", ""),
                task = memberJson.optString("task", "")
            ))
        }
        
        // Parse materials
        val materialsArray = json.optJSONArray("materials") ?: JSONArray()
        val materials = mutableListOf<Material>()
        for (i in 0 until materialsArray.length()) {
            val matJson = materialsArray.getJSONObject(i)
            materials.add(Material(
                name = matJson.optString("name", ""),
                notes = matJson.optString("notes", ""),
                checked = matJson.optBoolean("checked", false),
                checkedAt = if (matJson.has("checkedAt") && !matJson.isNull("checkedAt")) matJson.optLong("checkedAt") else null,
                quantity = matJson.optDouble("quantity", 0.0),
                unit = matJson.optString("unit", ""),
                unitCost = matJson.optDouble("unitCost", 0.0),
                totalCost = matJson.optDouble("totalCost", 0.0),
                vendor = matJson.optString("vendor", ""),
                assignedTo = matJson.optString("assignedTo", null)?.takeIf { it.isNotEmpty() },
                relatedTaskId = matJson.optString("relatedTaskId", null)?.takeIf { it.isNotEmpty() }
            ))
        }
        
        // Parse work log
        val workLogArray = json.optJSONArray("workLog") ?: JSONArray()
        val workLog = mutableListOf<WorkLogEntry>()
        for (i in 0 until workLogArray.length()) {
            val entryJson = workLogArray.getJSONObject(i)
            workLog.add(WorkLogEntry(
                text = entryJson.optString("text", ""),
                timestamp = entryJson.optLong("timestamp", System.currentTimeMillis()),
                author = entryJson.optString("author", "")
            ))
        }
        
        return Job(
            id = json.getString("id"),
            title = json.optString("title", ""),
            description = json.optString("description", ""),
            projectId = json.optString("projectId", null).takeIf { it?.isNotEmpty() == true },
            clientName = json.optString("clientName", null).takeIf { it?.isNotEmpty() == true },
            location = json.optString("location", null).takeIf { it?.isNotEmpty() == true },
            status = try {
                JobStatus.valueOf(json.optString("status", "TODO").uppercase())
            } catch (e: Exception) {
                JobStatus.TODO
            },
            priority = try {
                Priority.valueOf(json.optString("priority", "MEDIUM").uppercase())
            } catch (e: Exception) {
                Priority.MEDIUM
            },
            createdBy = json.optString("createdBy", ""),
            assignedTo = assignedTo,
            createdAt = json.optLong("createdAt", System.currentTimeMillis()),
            updatedAt = json.optLong("updatedAt", System.currentTimeMillis()),
            dueDate = if (json.has("dueDate") && !json.isNull("dueDate")) json.optLong("dueDate") else null,
            completedAt = if (json.has("completedAt") && !json.isNull("completedAt")) json.optLong("completedAt") else null,
            tags = tags,
            toolsNeeded = json.optString("toolsNeeded", ""),
            expenses = json.optString("expenses", ""),
            crewSize = json.optInt("crewSize", 1),
            crew = crew,
            materials = materials,
            workLog = workLog,
            estimatedStartDate = if (json.has("estimatedStartDate") && !json.isNull("estimatedStartDate")) json.optLong("estimatedStartDate") else null,
            estimatedEndDate = if (json.has("estimatedEndDate") && !json.isNull("estimatedEndDate")) json.optLong("estimatedEndDate") else null,
            actualStartDate = if (json.has("actualStartDate") && !json.isNull("actualStartDate")) json.optLong("actualStartDate") else null,
            actualEndDate = if (json.has("actualEndDate") && !json.isNull("actualEndDate")) json.optLong("actualEndDate") else null,
            chatSummary = json.optString("chatSummary", null).takeIf { it?.isNotEmpty() == true },
            isArchived = json.optBoolean("isArchived", false),
            archivedAt = if (json.has("archivedAt") && !json.isNull("archivedAt")) json.optLong("archivedAt") else null,
            archiveReason = json.optString("archiveReason", null).takeIf { it?.isNotEmpty() == true },
            relatedMessageIds = relatedMessageIds,
            relatedChannelId = json.optString("relatedChannelId", null).takeIf { it?.isNotEmpty() == true }
        )
    }
    
    /**
     * Clear all stored jobs (for testing)
     */
    fun clear() {
        prefs?.edit()?.clear()?.commit()
    }
    
    /**
     * Get job count without loading full data
     */
    fun getJobCount(): Int {
        val jsonStr = prefs?.getString(KEY_JOBS, null) ?: return 0
        return try {
            JSONArray(jsonStr).length()
        } catch (e: Exception) {
            0
        }
    }
    
    /**
     * Get archived job count without loading full data
     */
    fun getArchivedJobCount(): Int {
        val jsonStr = prefs?.getString(KEY_ARCHIVED_JOBS, null) ?: return 0
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
