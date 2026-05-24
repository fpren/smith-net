package com.guildofsmiths.trademesh.ai

import android.util.Log
import com.guildofsmiths.trademesh.data.MessageRepository
import com.guildofsmiths.trademesh.data.TimeEntryRepository
import com.guildofsmiths.trademesh.ui.timetracking.EntryType
import com.guildofsmiths.trademesh.ui.timetracking.TimeEntry
import org.json.JSONObject
import java.util.UUID

object SmithAIToolExecutor {

    private const val TAG = "SmithAIToolExecutor"

    private val toolCallRegex = Regex("<tool_call>(.*?)</tool_call>", RegexOption.DOT_MATCHES_ALL)

    sealed class ParseResult {
        object NoToolCall : ParseResult()
        data class Parsed(val name: String, val arguments: JSONObject, val raw: String) : ParseResult()
        data class Malformed(val errorJson: String) : ParseResult()
    }

    sealed class ExecutionResult {
        data class ReadOk(val resultJson: String) : ExecutionResult()
        data class WritePending(val pending: PendingToolCall) : ExecutionResult()
        data class Failed(val errorJson: String) : ExecutionResult()
    }

    data class PendingToolCall(
        val id: String,
        val toolName: String,
        val arguments: JSONObject,
        val argsSummary: String
    )

    fun parse(response: String): ParseResult {
        val match = toolCallRegex.find(response) ?: return ParseResult.NoToolCall
        val payload = match.groupValues[1].trim()
        return try {
            val obj = JSONObject(payload)
            val name = obj.optString("name", "")
            val args = obj.optJSONObject("arguments") ?: JSONObject()
            if (name.isBlank()) {
                ParseResult.Malformed(errorJson("invalid_tool_call", "missing 'name' field"))
            } else {
                ParseResult.Parsed(name, args, payload)
            }
        } catch (e: Exception) {
            ParseResult.Malformed(errorJson("invalid_json", e.message ?: "parse failed"))
        }
    }

    fun validateAndExecuteRead(parsed: ParseResult.Parsed): ExecutionResult {
        val tool = SmithAIToolRegistry.byName(parsed.name)
            ?: return ExecutionResult.Failed(
                errorJson("unknown_tool", "tool ${parsed.name} not found", availableNames = SmithAIToolRegistry.availableNames())
            )
        val missing = missingRequired(tool, parsed.arguments)
        if (missing.isNotEmpty()) {
            return ExecutionResult.Failed(errorJson("invalid_args", "missing required: ${missing.joinToString(",")}"))
        }
        if (tool.requiresConfirmation) {
            return ExecutionResult.WritePending(
                PendingToolCall(
                    id = UUID.randomUUID().toString().take(12),
                    toolName = tool.name,
                    arguments = parsed.arguments,
                    argsSummary = summarize(tool.name, parsed.arguments)
                )
            )
        }
        return executeRead(tool.name, parsed.arguments)
    }

    fun executeApprovedWrite(
        pending: PendingToolCall,
        bridge: SmithAIToolBridge = SmithAIToolBridge,
        msgRepo: MessageRepository = MessageRepository,
        timeRepo: TimeEntryRepository = TimeEntryRepository,
        beaconId: String,
        senderId: String,
        senderName: String
    ): String {
        return when (pending.toolName) {
            "create_job" -> {
                val title = pending.arguments.optString("title", "")
                val client = pending.arguments.optString("clientName", "").takeIf { it.isNotBlank() }
                val address = pending.arguments.optString("address", "").takeIf { it.isNotBlank() }
                val stage = pending.arguments.optString("stage", "").takeIf { it.isNotBlank() }
                if (title.isBlank()) return "Could not create job: missing title."
                when (val result = bridge.createJob(title, client, address, stage)) {
                    is SmithAIToolBridge.CreateJobResult.Created ->
                        "Job created: \"${result.title}\" (${result.jobId.take(8)})"
                    is SmithAIToolBridge.CreateJobResult.Failed ->
                        "Could not create job: ${result.reason}"
                }
            }
            "send_message" -> {
                val channelId = pending.arguments.optString("channelId", "")
                val content = pending.arguments.optString("content", "")
                if (channelId.isBlank() || content.isBlank()) return "Could not send: missing channelId or content."
                val message = com.guildofsmiths.trademesh.data.Message(
                    beaconId = beaconId,
                    channelId = channelId,
                    senderId = senderId,
                    senderName = senderName,
                    content = content,
                    aiGenerated = true,
                    aiSource = "smithai-tool",
                    aiContext = "smithai-action"
                )
                msgRepo.addMessage(message)
                "Message sent to channel ${channelId.take(12)}."
            }
            "add_time_entry" -> {
                val jobId = pending.arguments.optString("jobId", "")
                val entryType = pending.arguments.optString("entryType", "WORK").uppercase()
                val durationMin = pending.arguments.optInt("durationMinutes", 0)
                if (jobId.isBlank() || durationMin <= 0) return "Could not add time entry: missing jobId or duration."
                val type = when (entryType) {
                    "BREAK" -> EntryType.BREAK
                    "OVERTIME" -> EntryType.OVERTIME
                    else -> EntryType.REGULAR
                }
                val now = System.currentTimeMillis()
                val entry = TimeEntry(
                    id = UUID.randomUUID().toString(),
                    userId = senderId,
                    userName = senderName,
                    clockInTime = now - durationMin * 60_000L,
                    clockOutTime = now,
                    durationMinutes = durationMin,
                    jobId = jobId,
                    entryType = type,
                    createdAt = now,
                    immutableHash = "smithai-${UUID.randomUUID().toString().take(8)}"
                )
                timeRepo.addEntry(entry)
                "Time entry added: ${durationMin} min (${type.displayName})."
            }
            "update_job_stage" -> {
                val jobId = pending.arguments.optString("jobId", "")
                val newStage = pending.arguments.optString("newStage", "").uppercase()
                if (jobId.isBlank() || newStage.isBlank()) return "Could not update stage: missing fields."
                when (val result = bridge.updateJobStage(jobId, newStage)) {
                    is SmithAIToolBridge.UpdateStageResult.Updated ->
                        "Job stage updated to ${result.newStage}."
                    is SmithAIToolBridge.UpdateStageResult.Failed ->
                        "Could not update stage: ${result.reason}"
                }
            }
            else -> "Unknown action: ${pending.toolName}"
        }
    }

    private fun executeRead(name: String, args: JSONObject): ExecutionResult {
        return try {
            val resultObj = JSONObject()
            when (name) {
                "query_jobs" -> {
                    val stage = args.optString("stage", "").takeIf { it.isNotBlank() }?.uppercase()
                    val clientNameFilter = args.optString("clientName", "").takeIf { it.isNotBlank() }?.lowercase()
                    val limit = args.optInt("limit", 8)
                    val jobs = SmithAIToolBridge.jobsSnapshot()
                        .filter { stage == null || it.stage.equals(stage, ignoreCase = true) }
                        .filter { clientNameFilter == null || (it.clientName ?: "").lowercase().contains(clientNameFilter) }
                        .take(limit)
                    val arr = org.json.JSONArray()
                    jobs.forEach {
                        arr.put(JSONObject().apply {
                            put("id", it.id)
                            put("title", it.title)
                            put("stage", it.stage)
                            put("clientName", it.clientName ?: "")
                        })
                    }
                    resultObj.put("jobs", arr)
                    resultObj.put("count", jobs.size)
                }
                "query_client" -> {
                    val needle = args.optString("name", "").lowercase()
                    if (needle.isBlank()) return ExecutionResult.Failed(errorJson("invalid_args", "name is required"))
                    val match = SmithAIToolBridge.clientsSnapshot().firstOrNull { it.name.lowercase().contains(needle) }
                    if (match == null) {
                        resultObj.put("found", false)
                    } else {
                        resultObj.put("found", true)
                        resultObj.put("name", match.name)
                        resultObj.put("phone", match.phone)
                        resultObj.put("address", match.address)
                        resultObj.put("activeJobCount", match.activeJobCount)
                        resultObj.put("totalJobCount", match.totalJobCount)
                    }
                }
                "query_time_entries" -> {
                    val jobId = args.optString("jobId", "").takeIf { it.isNotBlank() }
                    val sinceMs = args.optLong("sinceMs", System.currentTimeMillis() - 7L * 86_400_000)
                    val entries = TimeEntryRepository.entries.value
                        .filter { it.clockInTime >= sinceMs }
                        .filter { jobId == null || it.jobId == jobId }
                    val totalMin = entries.sumOf { it.durationMinutes ?: 0 }
                    resultObj.put("count", entries.size)
                    resultObj.put("totalMinutes", totalMin)
                    resultObj.put("totalHours", totalMin / 60.0)
                }
                "query_messages" -> {
                    val channelId = args.optString("channelId", "")
                    if (channelId.isBlank()) return ExecutionResult.Failed(errorJson("invalid_args", "channelId is required"))
                    val limit = args.optInt("limit", 10)
                    val msgs = MessageRepository.allMessages.value
                        .filter { it.channelId == channelId }
                        .sortedByDescending { it.timestamp }
                        .take(limit)
                        .reversed()
                    val arr = org.json.JSONArray()
                    msgs.forEach {
                        arr.put(JSONObject().apply {
                            put("from", it.senderName)
                            put("content", it.content.take(200))
                            put("ts", it.timestamp)
                        })
                    }
                    resultObj.put("messages", arr)
                }
                else -> return ExecutionResult.Failed(
                    errorJson("unknown_tool", "no read handler for $name", availableNames = SmithAIToolRegistry.availableNames())
                )
            }
            ExecutionResult.ReadOk(resultObj.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Read tool execution failed", e)
            ExecutionResult.Failed(errorJson("execution_error", e.message ?: "unknown error"))
        }
    }

    private fun missingRequired(tool: SmithAIToolRegistry.ToolDef, args: JSONObject): List<String> {
        return tool.parameters.filter { it.required && !args.has(it.name) }.map { it.name }
    }

    private fun summarize(toolName: String, args: JSONObject): String {
        return when (toolName) {
            "create_job" -> "title=${args.optString("title")}, client=${args.optString("clientName")}, address=${args.optString("address")}"
            "send_message" -> "channel=${args.optString("channelId")}, content=\"${args.optString("content").take(80)}\""
            "add_time_entry" -> "job=${args.optString("jobId")}, ${args.optInt("durationMinutes")}min ${args.optString("entryType")}"
            "update_job_stage" -> "job=${args.optString("jobId")} -> ${args.optString("newStage")}"
            else -> args.toString().take(140)
        }
    }

    private fun errorJson(code: String, detail: String, availableNames: List<String>? = null): String {
        val obj = JSONObject().apply {
            put("error", code)
            put("detail", detail)
            if (availableNames != null) put("available", org.json.JSONArray(availableNames))
        }
        return obj.toString()
    }
}
