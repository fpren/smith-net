package com.guildofsmiths.trademesh.ai

import com.guildofsmiths.trademesh.data.JobRepository
import com.guildofsmiths.trademesh.data.Message
import com.guildofsmiths.trademesh.data.MessageRepository
import com.guildofsmiths.trademesh.data.OccupationalForms
import com.guildofsmiths.trademesh.data.TimeEntryRepository
import com.guildofsmiths.trademesh.data.UserPreferences

object SmithAIContextBuilder {

    enum class Intent { LOOKUP, DRAFTING, ACTION, ADVICE }

    private const val TARGET_BUDGET_TOKENS = 1400
    private const val CHARS_PER_TOKEN = 4

    data class BuildResult(
        val systemPrompt: String,
        val intent: Intent,
        val approxTokens: Int
    )

    fun classify(userMessage: String): Intent {
        val m = userMessage.lowercase()
        return when {
            m.contains(Regex("\\b(create|add|new job|schedule|send|update|change|set)\\b")) -> Intent.ACTION
            m.contains(Regex("\\b(draft|write|compose|follow up|message|text)\\b")) -> Intent.DRAFTING
            m.contains(Regex("\\b(code|nec|osha|regulation|safety|how do i|what is the)\\b")) -> Intent.ADVICE
            else -> Intent.LOOKUP
        }
    }

    fun build(
        userMessage: String,
        recentTurns: List<Message>,
        currentChannelId: String
    ): BuildResult {
        val intent = classify(userMessage)
        val sb = StringBuilder()
        sb.append(AIPrompts.SYSTEM)
        sb.append("\n\n")
        sb.append(SmithAIToolRegistry.toolListPrompt())
        sb.append("\n")
        sb.append(buildUserBlock())
        sb.append(buildJobsBlock(limit = 8))
        if (intent == Intent.ADVICE || intent == Intent.LOOKUP) {
            sb.append(buildTimeBlock())
        }
        if (intent == Intent.ADVICE) {
            sb.append(buildTradeKnowledgeBlock())
        }
        sb.append(buildRecentTurnsBlock(recentTurns, currentChannelId, max = 10))

        var prompt = sb.toString()
        var approxTokens = prompt.length / CHARS_PER_TOKEN

        if (approxTokens > TARGET_BUDGET_TOKENS) {
            prompt = trimToBudget(userMessage, recentTurns, currentChannelId, intent)
            approxTokens = prompt.length / CHARS_PER_TOKEN
        }

        return BuildResult(prompt, intent, approxTokens)
    }

    private fun buildUserBlock(): String {
        val name = UserPreferences.getUserName().ifBlank { "User" }
        val role = UserPreferences.getTradeRole().displayName
        val clockedIn = UserPreferences.isClockedIn()
        return "USER:\n- name: $name\n- trade: $role\n- clocked_in: $clockedIn\n\n"
    }

    private fun buildJobsBlock(limit: Int): String {
        val snaps = SmithAIToolBridge.jobsSnapshot().take(limit)
        if (snaps.isEmpty()) {
            // Fall back to lighter SimpleJob view when bridge not yet registered
            val simple = JobRepository.activeJobs.value.take(limit)
            if (simple.isEmpty()) return "ACTIVE JOBS: (none)\n\n"
            val lines = simple.joinToString("\n") { "- ${it.title} (${it.status}) [${it.id.take(8)}]" }
            return "ACTIVE JOBS (light):\n$lines\n\n"
        }
        val lines = snaps.joinToString("\n") { j ->
            val client = j.clientName?.takeIf { it.isNotBlank() } ?: "no client"
            "- ${j.title} | ${j.stage} | $client | id=${j.id.take(8)}"
        }
        return "ACTIVE JOBS:\n$lines\n\n"
    }

    private fun buildTimeBlock(): String {
        val entries = TimeEntryRepository.entries.value
        val today = entries.filter { it.clockInTime >= startOfTodayMs() }
        val totalMinToday = today.sumOf { it.durationMinutes ?: 0 }
        return "TIME TODAY:\n- entries: ${today.size}\n- minutes: $totalMinToday\n\n"
    }

    private fun buildTradeKnowledgeBlock(): String {
        val role = UserPreferences.getTradeRole()
        val kb = OccupationalForms.getKnowledgeBase(role)
        val skills = kb.coreSkills.take(8).joinToString(", ")
        val regs = kb.regulations.take(6).joinToString(", ")
        return "TRADE KNOWLEDGE (${role.displayName}):\n- skills: $skills\n- regulations: $regs\n\n"
    }

    private fun buildRecentTurnsBlock(turns: List<Message>, channelId: String, max: Int): String {
        val filtered = turns.filter { it.channelId == channelId }.takeLast(max)
        if (filtered.isEmpty()) return "RECENT CONVERSATION:\n(no prior messages)\n\n"
        val lines = filtered.joinToString("\n") { m ->
            val role = if (m.aiGenerated) "SmithAI" else "User"
            "$role: ${m.content.take(280)}"
        }
        return "RECENT CONVERSATION:\n$lines\n\n"
    }

    private fun trimToBudget(
        @Suppress("UNUSED_PARAMETER") userMessage: String,
        recentTurns: List<Message>,
        currentChannelId: String,
        intent: Intent
    ): String {
        val sb = StringBuilder()
        sb.append(AIPrompts.SYSTEM)
        sb.append("\n\n")
        sb.append(SmithAIToolRegistry.toolListPrompt())
        sb.append("\n")
        sb.append(buildUserBlock())
        sb.append(buildJobsBlock(limit = 5))
        if (intent == Intent.ADVICE) {
            val role = UserPreferences.getTradeRole()
            val kb = OccupationalForms.getKnowledgeBase(role)
            sb.append("TRADE: ${role.displayName} (skills: ${kb.coreSkills.take(4).joinToString(", ")})\n\n")
        }
        sb.append(buildRecentTurnsBlock(recentTurns, currentChannelId, max = 6))
        return sb.toString()
    }

    private fun startOfTodayMs(): Long {
        val cal = java.util.Calendar.getInstance()
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }
}
