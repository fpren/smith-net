package com.guildofsmiths.trademesh.ai

object SmithAIToolRegistry {

    data class ToolDef(
        val name: String,
        val description: String,
        val parameters: List<Param>,
        val requiresConfirmation: Boolean
    )

    data class Param(
        val name: String,
        val type: String,
        val required: Boolean,
        val description: String
    )

    val readOnly: List<ToolDef> = listOf(
        ToolDef(
            name = "query_jobs",
            description = "List the user's active jobs. Optional filter by stage or client name.",
            parameters = listOf(
                Param("stage", "string", false, "LEAD, PROPOSAL, APPROVED, IN_PROGRESS, REVIEW, INVOICE, CLOSED"),
                Param("clientName", "string", false, "filter by client (case-insensitive contains)"),
                Param("limit", "number", false, "max results, default 8")
            ),
            requiresConfirmation = false
        ),
        ToolDef(
            name = "query_client",
            description = "Look up a client by name and return phone, address, and job count.",
            parameters = listOf(
                Param("name", "string", true, "client name (case-insensitive contains)")
            ),
            requiresConfirmation = false
        ),
        ToolDef(
            name = "query_time_entries",
            description = "Recent time entries summarized as totals.",
            parameters = listOf(
                Param("jobId", "string", false, "filter by job id"),
                Param("sinceMs", "number", false, "epoch ms lower bound; default last 7 days")
            ),
            requiresConfirmation = false
        ),
        ToolDef(
            name = "query_messages",
            description = "Recent messages in a channel.",
            parameters = listOf(
                Param("channelId", "string", true, "channel id"),
                Param("limit", "number", false, "default 10")
            ),
            requiresConfirmation = false
        )
    )

    val write: List<ToolDef> = listOf(
        ToolDef(
            name = "create_job",
            description = "Create a new job. User must approve before it is saved.",
            parameters = listOf(
                Param("title", "string", true, "short job title"),
                Param("clientName", "string", false, "client name"),
                Param("address", "string", false, "site address"),
                Param("stage", "string", false, "LEAD, PROPOSAL, APPROVED, IN_PROGRESS — default LEAD")
            ),
            requiresConfirmation = true
        ),
        ToolDef(
            name = "send_message",
            description = "Send a message to a channel. User must approve before it is sent.",
            parameters = listOf(
                Param("channelId", "string", true, "channel id"),
                Param("content", "string", true, "message body")
            ),
            requiresConfirmation = true
        ),
        ToolDef(
            name = "add_time_entry",
            description = "Add a manual time entry to a job. User must approve.",
            parameters = listOf(
                Param("jobId", "string", true, "job id"),
                Param("entryType", "string", true, "WORK or BREAK"),
                Param("durationMinutes", "number", true, "duration in minutes")
            ),
            requiresConfirmation = true
        ),
        ToolDef(
            name = "update_job_stage",
            description = "Change a job's stage. User must approve.",
            parameters = listOf(
                Param("jobId", "string", true, "job id"),
                Param("newStage", "string", true, "LEAD, PROPOSAL, APPROVED, IN_PROGRESS, REVIEW, INVOICE, CLOSED")
            ),
            requiresConfirmation = true
        )
    )

    fun all(): List<ToolDef> = readOnly + write

    fun byName(name: String): ToolDef? = all().firstOrNull { it.name == name }

    fun availableNames(): List<String> = all().map { it.name }

    fun toolListPrompt(): String {
        val sb = StringBuilder()
        sb.append("Tools you can call. Emit a tool call as a single fenced block: <tool_call>{\"name\":\"<name>\",\"arguments\":{...}}</tool_call>. After a read-only tool returns, you will receive the result and may answer or call another tool. Write tools require user approval before they execute. If no tool fits, just answer in plain text.\n")
        all().forEach { tool ->
            sb.append("- ${tool.name}: ${tool.description}\n")
            tool.parameters.forEach { p ->
                val req = if (p.required) "required" else "optional"
                sb.append("    - ${p.name} (${p.type}, $req): ${p.description}\n")
            }
        }
        return sb.toString()
    }
}
