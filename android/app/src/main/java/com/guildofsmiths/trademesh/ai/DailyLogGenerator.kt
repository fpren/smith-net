package com.guildofsmiths.trademesh.ai

import android.util.Log
import com.guildofsmiths.trademesh.data.Message
import com.guildofsmiths.trademesh.data.MessageRepository
import com.guildofsmiths.trademesh.data.TimeEntryRepository
import com.guildofsmiths.trademesh.data.UserPreferences
import com.guildofsmiths.trademesh.ui.jobboard.DailyJobLog
import com.guildofsmiths.trademesh.ui.jobboard.DailyLogStatus
import com.guildofsmiths.trademesh.ui.jobboard.Job
import com.guildofsmiths.trademesh.ui.jobboard.WorkLogEntry
import com.guildofsmiths.trademesh.ui.timetracking.TimeEntry
import java.text.SimpleDateFormat
import java.util.*

/**
 * Generates daily job logs by gathering the day's data (time entries, notes, materials),
 * building rule-based summaries (always works), and calling AI for a narrative (optional).
 */
object DailyLogGenerator {

    private const val TAG = "DailyLogGenerator"

    /**
     * Generate a daily log for a specific job on a specific date.
     * Collects time entries, work log notes, and materials checked for the day.
     */
    suspend fun generateLog(
        job: Job,
        targetDate: Long = todayMidnight()
    ): DailyJobLog {
        val dateRange = dayRange(targetDate)
        val dateFormat = SimpleDateFormat("MMM d", Locale.US)
        val dateStr = dateFormat.format(Date(targetDate))

        // 1. Collect today's time entries for this job
        val allEntries = TimeEntryRepository.getEntriesForJob(job.id, job.title)
        val todaysEntries = allEntries.filter { entry ->
            entry.clockInTime in dateRange.first..dateRange.second
        }

        // 2. Calculate hours worked
        val totalMinutes = todaysEntries.sumOf { entry ->
            if (entry.clockOutTime != null) {
                ((entry.clockOutTime - entry.clockInTime) / 60_000).toInt()
            } else 0
        }
        val hoursWorked = totalMinutes / 60.0

        // 3. Collect time entry IDs
        val timeEntryIds = todaysEntries.map { it.id }

        // 4. Collect worker notes from job.workLog (today only)
        val todaysWorkNotes = job.workLog.filter { it.timestamp in dateRange.first..dateRange.second }

        // 5. Collect time notes from today's entries
        val timeNoteTexts = todaysEntries
            .flatMap { it.notes }
            .filter { it.type == "note" }
            .map { it.text }

        // 6. Count materials checked today
        val materialsCheckedToday = job.materials.filter { mat ->
            mat.checked && mat.checkedAt != null && mat.checkedAt in dateRange.first..dateRange.second
        }
        val materialsCheckedCount = materialsCheckedToday.size
        val materialsCostToday = materialsCheckedToday.sumOf { it.totalCost }

        // 7. Crew present
        val crewPresent = job.crew.map { it.name }

        // 8. Build rule-based standard summary
        val summaryStandard = buildStandardSummary(hoursWorked, materialsCheckedCount, materialsCostToday, crewPresent)

        // 9. Build rule-based detailed summary
        val summaryDetailed = buildDetailedSummary(dateStr, hoursWorked, materialsCheckedCount, materialsCostToday, todaysWorkNotes, timeNoteTexts, crewPresent)

        // 10. AI clarification — check if any notes need clarification
        val apiKey = UserPreferences.getOpenRouterApiKey()
        val clarifiedNotes = mutableListOf<String>()
        if (apiKey.isNotBlank() && todaysWorkNotes.isNotEmpty()) {
            for (note in todaysWorkNotes) {
                try {
                    val clarifyResponse = OpenRouterClient.chat(
                        systemPrompt = AIPrompts.SYSTEM,
                        userMessage = AIPrompts.clarifyNote(note.text, job.title),
                        maxTokens = 40
                    )
                    if (clarifyResponse != null && !clarifyResponse.trim().equals("CLEAR", ignoreCase = true)) {
                        // AI has a question — post it to the job's channel
                        val channelId = job.relatedChannelId ?: "general"
                        val questionMsg = Message.createAIResponse(
                            channelId = channelId,
                            content = "Re: \"${note.text}\" — $clarifyResponse",
                            aiModel = "lfm-2.5",
                            aiSource = "llm",
                            aiContext = "daily-log-clarify:${job.id}",
                            originalPrompt = note.text
                        )
                        MessageRepository.addMessage(questionMsg)
                        Log.i(TAG, "AI clarification question posted for note: ${note.text.take(30)}")
                        clarifiedNotes.add("${note.text} [AI asked: $clarifyResponse]")
                    } else {
                        clarifiedNotes.add(note.text)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Clarification check failed: ${e.message}")
                    clarifiedNotes.add(note.text)
                }
            }
        }

        // Use clarified notes for the AI narrative (includes any AI annotations)
        val finalWorkerNotes = if (clarifiedNotes.isNotEmpty()) clarifiedNotes else todaysWorkNotes.map { it.text }

        // 11. Attempt AI narrative
        var summaryNarrative: String? = null
        var status = DailyLogStatus.COMPLETE

        if (apiKey.isNotBlank()) {
            try {
                val prompt = AIPrompts.dailyLogSummary(
                    jobTitle = job.title,
                    clientName = job.clientName,
                    hoursWorked = hoursWorked,
                    crewPresent = crewPresent,
                    materialsChecked = materialsCheckedCount,
                    materialsCost = materialsCostToday,
                    workerNotes = finalWorkerNotes,
                    timeNotes = timeNoteTexts
                )
                summaryNarrative = OpenRouterClient.chat(
                    systemPrompt = AIPrompts.SYSTEM,
                    userMessage = prompt,
                    maxTokens = 150
                )
                status = if (summaryNarrative != null) DailyLogStatus.COMPLETE else DailyLogStatus.FAILED_AI
            } catch (e: Exception) {
                Log.e(TAG, "AI narrative failed: ${e.message}")
                status = DailyLogStatus.FAILED_AI
            }
        } else {
            status = DailyLogStatus.COMPLETE // rule-based is always complete
        }

        val log = DailyJobLog(
            jobId = job.id,
            date = targetDate,
            hoursWorked = hoursWorked,
            crewPresent = crewPresent,
            materialsCheckedCount = materialsCheckedCount,
            materialsCostToday = materialsCostToday,
            workerNotes = todaysWorkNotes,
            timeEntryIds = timeEntryIds,
            summaryStandard = summaryStandard,
            summaryDetailed = summaryDetailed,
            summaryNarrative = summaryNarrative,
            autoGenerated = true,
            status = status
        )

        Log.i(TAG, "Generated daily log for ${job.clientName ?: job.title} on $dateStr: ${hoursWorked}h, ${materialsCheckedCount} materials, AI=${summaryNarrative != null}")
        return log
    }

    private fun buildStandardSummary(
        hours: Double,
        materialsCount: Int,
        materialsCost: Double,
        crew: List<String>
    ): String {
        val parts = mutableListOf<String>()
        parts.add("${String.format("%.1f", hours)}h")
        if (materialsCount > 0) {
            parts.add("$materialsCount materials ($${String.format("%.0f", materialsCost)})")
        }
        if (crew.isNotEmpty()) {
            parts.add("crew: ${crew.joinToString(", ")}")
        }
        return parts.joinToString(", ")
    }

    private fun buildDetailedSummary(
        dateStr: String,
        hours: Double,
        materialsCount: Int,
        materialsCost: Double,
        workNotes: List<WorkLogEntry>,
        timeNotes: List<String>,
        crew: List<String>
    ): String {
        val sb = StringBuilder("$dateStr — ${String.format("%.1f", hours)}h")

        if (crew.isNotEmpty()) {
            sb.append(", ${crew.size} crew")
        }

        if (materialsCount > 0) {
            sb.append(", $materialsCount materials checked ($${String.format("%.0f", materialsCost)})")
        }

        // Append condensed notes
        val allNotes = workNotes.map { it.text } + timeNotes
        if (allNotes.isNotEmpty()) {
            sb.append(". ")
            sb.append(allNotes.take(3).joinToString("; ") { it.take(50) })
        }

        return sb.toString()
    }

    // ── Helpers ─────────────────────────────────────────

    fun todayMidnight(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun dayRange(midnight: Long): Pair<Long, Long> {
        return midnight to (midnight + 24 * 60 * 60 * 1000 - 1)
    }
}
