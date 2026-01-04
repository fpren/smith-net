package com.guildofsmiths.trademesh.planner.persistence

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.guildofsmiths.trademesh.planner.types.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * PlannerStorage - Local persistence for Planner state
 * 
 * - Offline-capable
 * - Debounced writes
 * - No network dependency
 */
class PlannerStorage(context: Context) {

    companion object {
        private const val TAG = "PlannerStorage"
        private const val PREFS_NAME = "planner_storage"
        private const val KEY_PLANNER_STATE = "planner_state"
        private const val KEY_JOBS = "planner_jobs"
        private const val KEY_JOB_INDEX = "job_index"
    }

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ============================================================
    // PLANNER STATE
    // ============================================================

    suspend fun savePlannerData(data: PlannerData) = withContext(Dispatchers.IO) {
        try {
            val json = serializePlannerData(data)
            prefs.edit().putString(KEY_PLANNER_STATE, json.toString()).apply()
            Log.d(TAG, "Saved planner data: ${data.state}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save planner data", e)
        }
    }

    suspend fun loadPlannerData(): PlannerData? = withContext(Dispatchers.IO) {
        try {
            val jsonString = prefs.getString(KEY_PLANNER_STATE, null) ?: return@withContext null
            val json = JSONObject(jsonString)
            deserializePlannerData(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load planner data", e)
            null
        }
    }

    suspend fun clearPlannerData() = withContext(Dispatchers.IO) {
        prefs.edit().remove(KEY_PLANNER_STATE).apply()
    }

    // ============================================================
    // JOBS
    // ============================================================

    suspend fun saveJob(job: PlannerJob) = withContext(Dispatchers.IO) {
        try {
            val json = serializeJob(job)
            prefs.edit().putString("job_${job.id}", json.toString()).apply()
            
            // Update index
            val index = getJobIndex().toMutableList()
            if (!index.contains(job.id)) {
                index.add(job.id)
                saveJobIndex(index)
            }
            
            Log.d(TAG, "Saved job: ${job.id}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save job", e)
        }
    }

    suspend fun saveJobs(jobs: List<PlannerJob>) = withContext(Dispatchers.IO) {
        jobs.forEach { saveJob(it) }
    }

    suspend fun getJob(id: String): PlannerJob? = withContext(Dispatchers.IO) {
        try {
            val jsonString = prefs.getString("job_$id", null) ?: return@withContext null
            val json = JSONObject(jsonString)
            deserializeJob(json)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load job: $id", e)
            null
        }
    }

    suspend fun getAllJobs(): List<PlannerJob> = withContext(Dispatchers.IO) {
        val index = getJobIndex()
        index.mapNotNull { getJob(it) }
    }

    suspend fun deleteJob(id: String) = withContext(Dispatchers.IO) {
        prefs.edit().remove("job_$id").apply()
        
        val index = getJobIndex().toMutableList()
        index.remove(id)
        saveJobIndex(index)
    }

    private fun getJobIndex(): List<String> {
        val jsonString = prefs.getString(KEY_JOB_INDEX, "[]") ?: "[]"
        return try {
            val jsonArray = JSONArray(jsonString)
            (0 until jsonArray.length()).map { jsonArray.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveJobIndex(index: List<String>) {
        val jsonArray = JSONArray(index)
        prefs.edit().putString(KEY_JOB_INDEX, jsonArray.toString()).apply()
    }

    // ============================================================
    // SERIALIZATION
    // ============================================================

    private fun serializePlannerData(data: PlannerData): JSONObject {
        return JSONObject().apply {
            put("id", data.id)
            put("content", data.content)
            put("contentHash", data.contentHash)
            put("state", data.state.name)
            put("selectedItemIds", JSONArray(data.selectedItemIds.toList()))
            put("createdAt", data.createdAt)
            put("updatedAt", data.updatedAt)
            
            // Compilation result
            data.compilationResult?.let { result ->
                put("compilationResult", JSONObject().apply {
                    put("contentHash", result.contentHash)
                    put("compiledAt", result.compiledAt)
                    put("sections", serializeSections(result.sections))
                })
            }
            
            // Execution items
            put("executionItems", JSONArray().apply {
                data.executionItems.forEach { item ->
                    put(serializeExecutionItem(item))
                }
            })
            
            // Last error
            data.lastError?.let { error ->
                put("lastError", JSONObject().apply {
                    put("code", error.code.value)
                    put("message", error.message)
                    error.line?.let { put("line", it) }
                    error.section?.let { put("section", it) }
                })
            }
        }
    }

    private fun deserializePlannerData(json: JSONObject): PlannerData {
        val selectedItemIds = mutableSetOf<String>()
        val selectedArray = json.optJSONArray("selectedItemIds")
        if (selectedArray != null) {
            for (i in 0 until selectedArray.length()) {
                selectedItemIds.add(selectedArray.getString(i))
            }
        }

        val executionItems = mutableListOf<ExecutionItem>()
        val itemsArray = json.optJSONArray("executionItems")
        if (itemsArray != null) {
            for (i in 0 until itemsArray.length()) {
                executionItems.add(deserializeExecutionItem(itemsArray.getJSONObject(i)))
            }
        }

        val compilationResult = json.optJSONObject("compilationResult")?.let { resultJson ->
            CompilationResult(
                contentHash = resultJson.getString("contentHash"),
                compiledAt = resultJson.getLong("compiledAt"),
                sections = deserializeSections(resultJson.getJSONObject("sections"))
            )
        }

        val lastError = json.optJSONObject("lastError")?.let { errorJson ->
            CompileError(
                code = ErrorCode.values().find { it.value == errorJson.getString("code") } ?: ErrorCode.E001,
                message = errorJson.getString("message"),
                line = if (errorJson.has("line")) errorJson.getInt("line") else null,
                section = if (errorJson.has("section")) errorJson.getString("section") else null
            )
        }

        return PlannerData(
            id = json.getString("id"),
            content = json.getString("content"),
            contentHash = json.getString("contentHash"),
            state = PlannerStateEnum.valueOf(json.getString("state")),
            compilationResult = compilationResult,
            executionItems = executionItems,
            selectedItemIds = selectedItemIds,
            lastError = lastError,
            createdAt = json.getLong("createdAt"),
            updatedAt = json.getLong("updatedAt")
        )
    }

    private fun serializeSections(sections: ParsedSections): JSONObject {
        return JSONObject().apply {
            sections.scope?.let { put("scope", it) }
            sections.assumptions?.let { put("assumptions", it) }
            put("tasks", JSONArray(sections.tasks))
            put("materials", JSONArray(sections.materials))
            put("labor", JSONArray(sections.labor))
            put("exclusions", JSONArray(sections.exclusions))
            sections.summary?.let { put("summary", it) }
            // Extended GOSPLAN fields
            put("phases", JSONArray(sections.phases))
            put("safety", JSONArray(sections.safety))
            put("code", JSONArray(sections.code))
            put("notes", JSONArray(sections.notes))
        }
    }

    private fun deserializeSections(json: JSONObject): ParsedSections {
        fun getStringList(key: String): List<String> {
            val array = json.optJSONArray(key) ?: return emptyList()
            return (0 until array.length()).map { array.getString(it) }
        }

        return ParsedSections(
            scope = json.optString("scope", null),
            assumptions = json.optString("assumptions", null),
            tasks = getStringList("tasks"),
            materials = getStringList("materials"),
            labor = getStringList("labor"),
            exclusions = getStringList("exclusions"),
            summary = json.optString("summary", null),
            // Extended GOSPLAN fields (may not exist in old data)
            jobHeader = null, // Parsed from content, not persisted
            financial = null, // Parsed from content, not persisted
            phases = getStringList("phases"),
            safety = getStringList("safety"),
            code = getStringList("code"),
            notes = getStringList("notes")
        )
    }

    private fun serializeExecutionItem(item: ExecutionItem): JSONObject {
        return JSONObject().apply {
            put("id", item.id)
            put("type", item.type.name)
            put("index", item.index)
            put("lineNumber", item.lineNumber)
            put("section", item.section)
            put("source", item.source)
            put("createdAt", item.createdAt)
            
            // Serialize parsed data
            put("parsed", when (val parsed = item.parsed) {
                is ParsedItemData.Task -> JSONObject().apply {
                    put("kind", "task")
                    put("description", parsed.description)
                }
                is ParsedItemData.Material -> JSONObject().apply {
                    put("kind", "material")
                    put("description", parsed.description)
                    parsed.quantity?.let { put("quantity", it) }
                    parsed.unit?.let { put("unit", it) }
                }
                is ParsedItemData.Labor -> JSONObject().apply {
                    put("kind", "labor")
                    put("description", parsed.description)
                    parsed.hours?.let { put("hours", it) }
                    parsed.role?.let { put("role", it) }
                }
            })
        }
    }

    private fun deserializeExecutionItem(json: JSONObject): ExecutionItem {
        val parsedJson = json.getJSONObject("parsed")
        val parsed = when (parsedJson.getString("kind")) {
            "task" -> ParsedItemData.Task(
                description = parsedJson.getString("description")
            )
            "material" -> ParsedItemData.Material(
                description = parsedJson.getString("description"),
                quantity = parsedJson.optString("quantity", null),
                unit = parsedJson.optString("unit", null)
            )
            "labor" -> ParsedItemData.Labor(
                description = parsedJson.getString("description"),
                hours = parsedJson.optString("hours", null),
                role = parsedJson.optString("role", null)
            )
            else -> ParsedItemData.Task(description = "")
        }

        return ExecutionItem(
            id = json.getString("id"),
            type = ExecutionItemType.valueOf(json.getString("type")),
            index = json.getInt("index"),
            lineNumber = json.getInt("lineNumber"),
            section = json.getString("section"),
            source = json.getString("source"),
            parsed = parsed,
            createdAt = json.getLong("createdAt")
        )
    }

    private fun serializeJob(job: PlannerJob): JSONObject {
        return JSONObject().apply {
            put("id", job.id)
            put("sourceItemId", job.sourceItemId)
            put("sourcePlanHash", job.sourcePlanHash)
            put("type", job.type.name)
            put("status", job.status.name)
            put("title", job.title)
            put("createdAt", job.createdAt)
            put("transferredAt", job.transferredAt)
            
            put("details", when (val details = job.details) {
                is JobDetails.Task -> JSONObject().apply {
                    put("kind", "task")
                    put("description", details.description)
                }
                is JobDetails.Material -> JSONObject().apply {
                    put("kind", "material")
                    put("description", details.description)
                    details.quantity?.let { put("quantity", it) }
                    details.unit?.let { put("unit", it) }
                }
                is JobDetails.Labor -> JSONObject().apply {
                    put("kind", "labor")
                    put("description", details.description)
                    details.hours?.let { put("hours", it) }
                    details.role?.let { put("role", it) }
                }
            })
        }
    }

    private fun deserializeJob(json: JSONObject): PlannerJob {
        val detailsJson = json.getJSONObject("details")
        val details = when (detailsJson.getString("kind")) {
            "task" -> JobDetails.Task(
                description = detailsJson.getString("description")
            )
            "material" -> JobDetails.Material(
                description = detailsJson.getString("description"),
                quantity = detailsJson.optString("quantity", null),
                unit = detailsJson.optString("unit", null)
            )
            "labor" -> JobDetails.Labor(
                description = detailsJson.getString("description"),
                hours = detailsJson.optString("hours", null),
                role = detailsJson.optString("role", null)
            )
            else -> JobDetails.Task(description = "")
        }

        return PlannerJob(
            id = json.getString("id"),
            sourceItemId = json.getString("sourceItemId"),
            sourcePlanHash = json.getString("sourcePlanHash"),
            type = ExecutionItemType.valueOf(json.getString("type")),
            status = JobStatus.valueOf(json.getString("status")),
            title = json.getString("title"),
            details = details,
            createdAt = json.getLong("createdAt"),
            transferredAt = json.getLong("transferredAt")
        )
    }
}
