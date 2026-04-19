package com.guildofsmiths.trademesh.ai

import android.util.Log
import com.guildofsmiths.trademesh.data.BeaconRepository
import com.guildofsmiths.trademesh.data.ClockStatus
import com.guildofsmiths.trademesh.data.CrewPresenceInfo
import com.guildofsmiths.trademesh.data.CrewPresenceRepository
import com.guildofsmiths.trademesh.data.Message
import com.guildofsmiths.trademesh.data.MessageRepository
import com.guildofsmiths.trademesh.data.UserPreferences
import com.guildofsmiths.trademesh.ui.jobboard.Job
import com.guildofsmiths.trademesh.ui.jobboard.JobStage
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AI Supervisor — a team member with supervisor privileges.
 * Observes jobs, crew, and operations. Generates insights automatically.
 *
 * Modes:
 * - auto: posts insights directly to activity log
 * - semi-auto: queues insights for user approval in AI Inbox
 * - off: disabled
 *
 * Active behaviors:
 * - Periodic observation loop (every 5 min while app is active)
 * - Stage change hooks (auto-summarize when job transitions)
 * - Crew status check-ins (flag off-clock, long breaks, unassigned)
 * - Works for ALL roles including Solo
 */
object AISupervisor {

    private const val TAG = "AISupervisor"
    private const val LOOP_INTERVAL_MS = 5 * 60 * 1000L // 5 minutes

    data class AIInsight(
        val id: String = java.util.UUID.randomUUID().toString(),
        val type: InsightType,
        val jobId: String? = null,
        val jobTitle: String? = null,
        val crewUserId: String? = null,    // for CREW insights — enables DM
        val crewName: String? = null,
        val title: String,
        val body: String,
        val timestamp: Long = System.currentTimeMillis(),
        val approved: Boolean = false
    )

    enum class InsightType {
        SUMMARY,    // Job status summary
        ALERT,      // Something needs attention
        DRAFT,      // Draft message for crew
        CHECKIN,    // Overall status check
        STAGE,      // Stage change summary
        CREW        // Crew status alert
    }

    // Pending insights (semi-auto mode)
    private val _insights = MutableStateFlow<List<AIInsight>>(emptyList())
    val insights: StateFlow<List<AIInsight>> = _insights.asStateFlow()

    // Auto-posted insights (for activity log)
    private val _autoPosted = MutableStateFlow<List<AIInsight>>(emptyList())
    val autoPosted: StateFlow<List<AIInsight>> = _autoPosted.asStateFlow()

    // Processing state
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var loopJob: kotlinx.coroutines.Job? = null
    private var lastObservation: Long = 0
    private var cachedJobs: List<Job> = emptyList()

    // Track which stage changes we've already processed
    private val processedStageChanges = mutableSetOf<String>() // "jobId-stage"

    // End-of-day sweep tracking
    private var lastSweepDate: Long = 0 // midnight of last swept day
    private const val END_OF_DAY_HOUR = 18 // 6 PM

    // Callback to store daily logs on jobs (set by ViewModel)
    var onDailyLogGenerated: ((String, com.guildofsmiths.trademesh.ui.jobboard.DailyJobLog) -> Unit)? = null

    fun getMode(): String = UserPreferences.getAISupervisorMode()

    fun isEnabled(): Boolean = getMode() != "off"

    // ════════════════════════════════════════════════════════════════════
    // PERIODIC OBSERVATION LOOP
    // ════════════════════════════════════════════════════════════════════

    /**
     * Start the periodic observation loop. Safe to call multiple times —
     * only one loop runs at a time. Call when app becomes active.
     */
    fun startLoop(jobs: List<Job>) {
        if (!isEnabled()) return
        cachedJobs = jobs

        if (loopJob?.isActive == true) return // already running

        Log.i(TAG, "Starting observation loop (${LOOP_INTERVAL_MS / 1000}s interval, mode=${getMode()})")
        loopJob = scope.launch {
            // Run immediately on start
            runFullObservation()

            // Then repeat every interval
            while (isActive) {
                delay(LOOP_INTERVAL_MS)
                if (isEnabled()) {
                    runFullObservation()
                }
            }
        }
    }

    /**
     * Stop the periodic loop. Call when app goes to background.
     */
    fun stopLoop() {
        loopJob?.cancel()
        loopJob = null
        Log.i(TAG, "Observation loop stopped")
    }

    /**
     * Update the cached jobs list. Called from dashboard when jobs change.
     * Triggers an immediate observation if enough time has passed.
     */
    fun updateJobs(jobs: List<Job>) {
        cachedJobs = jobs
        // If loop isn't running, start it
        if (isEnabled() && loopJob?.isActive != true) {
            startLoop(jobs)
        }
    }

    /**
     * Legacy observe() — still works, now also starts the loop.
     */
    fun observe(jobs: List<Job>) {
        updateJobs(jobs)
    }

    // ════════════════════════════════════════════════════════════════════
    // STAGE CHANGE HOOKS
    // ════════════════════════════════════════════════════════════════════

    /**
     * Called when a job transitions stages. Generates a stage summary.
     */
    fun onStageChange(job: Job, oldStage: JobStage, newStage: JobStage) {
        if (!isEnabled()) return

        val changeKey = "${job.id}-$newStage"
        if (changeKey in processedStageChanges) return
        processedStageChanges.add(changeKey)

        // Keep the set from growing forever
        if (processedStageChanges.size > 100) {
            val toRemove = processedStageChanges.take(50)
            processedStageChanges.removeAll(toRemove.toSet())
        }

        scope.launch {
            Log.i(TAG, "Stage change: ${job.clientName ?: job.title} ${oldStage.displayName} → ${newStage.displayName}")

            // Rule-based stage insight (always works, no API needed)
            val ruleInsight = generateStageInsight(job, oldStage, newStage)

            // Try AI-powered summary if API key is available
            val apiKey = UserPreferences.getOpenRouterApiKey()
            val aiSummary = if (apiKey.isNotBlank()) {
                OpenRouterClient.chat(
                    systemPrompt = AIPrompts.SYSTEM,
                    userMessage = AIPrompts.stageChange(job, oldStage, newStage),
                    maxTokens = 80
                )
            } else null

            val insight = AIInsight(
                type = InsightType.STAGE,
                jobId = job.id,
                jobTitle = job.clientName ?: job.title,
                title = "${job.clientName ?: job.title} → ${newStage.displayName}",
                body = aiSummary ?: ruleInsight
            )

            deliverInsight(insight)
        }
    }

    private fun generateStageInsight(job: Job, oldStage: JobStage, newStage: JobStage): String {
        val materialCount = job.materials.size
        val checkedCount = job.materials.count { it.checked }
        val totalCost = job.materials.sumOf { it.totalCost } + (job.hourlyRate * 8)

        return when (newStage) {
            JobStage.PROPOSAL -> "Proposal created. $materialCount materials listed, est. $${String.format("%.0f", totalCost)}."
            JobStage.APPROVED -> "Client approved. Ready to schedule work."
            JobStage.IN_PROGRESS -> "Work started. ${job.crewSize} crew, ${job.crew.size} assigned."
            JobStage.REVIEW -> "$checkedCount/$materialCount materials checked. Ready for review."
            JobStage.INVOICE -> "Invoice generated. Total: $${String.format("%.0f", totalCost)}. Follow up if no payment in 5 days."
            JobStage.CLOSED -> "Job closed. Final value: $${String.format("%.0f", totalCost)}."
            else -> "Stage updated: ${oldStage.displayName} → ${newStage.displayName}."
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // CLOCK-OUT HOOK — trigger daily log generation
    // ════════════════════════════════════════════════════════════════════

    /**
     * Called when a worker clocks out of a job. Generates a daily log.
     */
    fun onClockOut(job: Job, timeEntryId: String) {
        if (!isEnabled()) return

        scope.launch {
            try {
                Log.i(TAG, "Clock-out hook: generating daily log for ${job.clientName ?: job.title}")
                val log = DailyLogGenerator.generateLog(job)

                // Deliver insight about the log
                deliverInsight(AIInsight(
                    type = InsightType.SUMMARY,
                    jobId = job.id,
                    jobTitle = job.clientName ?: job.title,
                    title = "${job.clientName ?: job.title} — day logged",
                    body = log.summaryDetailed
                ))

                // Store the log on the job via callback
                onDailyLogGenerated?.invoke(job.id, log)
            } catch (e: Exception) {
                Log.e(TAG, "Daily log generation failed: ${e.message}")
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // END-OF-DAY SWEEP
    // ════════════════════════════════════════════════════════════════════

    private suspend fun runEndOfDaySweep() {
        val cal = java.util.Calendar.getInstance()
        val currentHour = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val today = DailyLogGenerator.todayMidnight()

        // Only sweep after END_OF_DAY_HOUR and once per day
        if (currentHour < END_OF_DAY_HOUR || today == lastSweepDate) return

        lastSweepDate = today
        Log.i(TAG, "Running end-of-day sweep")

        val jobs = cachedJobs.filter { it.stage == JobStage.IN_PROGRESS }
        for (job in jobs) {
            // Skip if log already exists for today
            val hasLog = job.dailyLogs.any { it.date == today }
            if (hasLog) continue

            // Check if there are time entries today for this job
            val todaysEntries = com.guildofsmiths.trademesh.data.TimeEntryRepository
                .getEntriesForJob(job.id, job.title)
                .filter { it.clockInTime >= today }

            if (todaysEntries.isNotEmpty()) {
                try {
                    val log = DailyLogGenerator.generateLog(job, today)
                    onDailyLogGenerated?.invoke(job.id, log)

                    deliverInsight(AIInsight(
                        type = InsightType.SUMMARY,
                        jobId = job.id,
                        jobTitle = job.clientName ?: job.title,
                        title = "${job.clientName ?: job.title} — end of day",
                        body = log.summaryDetailed
                    ))

                    Log.i(TAG, "End-of-day log generated for ${job.clientName ?: job.title}")
                } catch (e: Exception) {
                    Log.e(TAG, "End-of-day log failed for ${job.id}: ${e.message}")
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // FULL OBSERVATION (periodic + on-demand)
    // ════════════════════════════════════════════════════════════════════

    private suspend fun runFullObservation() {
        if (_isProcessing.value) return
        _isProcessing.value = true

        try {
            val jobs = cachedJobs
            if (jobs.isEmpty()) return

            val allInsights = mutableListOf<AIInsight>()

            // 1. Job issue detection (rule-based)
            allInsights.addAll(detectJobIssues(jobs))

            // 2. Crew status check-ins
            allInsights.addAll(detectCrewIssues())

            // 3. AI-powered check-in (if API key set and enough jobs)
            val apiKey = UserPreferences.getOpenRouterApiKey()
            val activeJobs = jobs.filter { it.stage != JobStage.CLOSED }
            if (apiKey.isNotBlank() && activeJobs.size > 1) {
                val response = OpenRouterClient.chat(
                    systemPrompt = AIPrompts.SYSTEM,
                    userMessage = AIPrompts.checkIn(activeJobs),
                    maxTokens = 150
                )
                if (response != null) {
                    allInsights.add(AIInsight(
                        type = InsightType.CHECKIN,
                        title = "Status Check",
                        body = response
                    ))
                }
            }

            // Deliver all insights
            allInsights.forEach { deliverInsight(it) }

            lastObservation = System.currentTimeMillis()
            Log.i(TAG, "Observation complete: ${allInsights.size} insights (mode=${getMode()})")

            // End-of-day sweep for daily logs
            runEndOfDaySweep()

        } catch (e: Exception) {
            Log.e(TAG, "Observation failed: ${e.message}")
        } finally {
            _isProcessing.value = false
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // JOB ISSUE DETECTION
    // ════════════════════════════════════════════════════════════════════

    private fun detectJobIssues(jobs: List<Job>): List<AIInsight> {
        val insights = mutableListOf<AIInsight>()
        jobs.forEach { job ->
            val issue = AIPrompts.detectIssue(job)
            if (issue != null) {
                insights.add(AIInsight(
                    type = InsightType.ALERT,
                    jobId = job.id,
                    jobTitle = job.clientName ?: job.title,
                    title = job.clientName ?: job.title,
                    body = issue
                ))
            }
        }
        return insights
    }

    // ════════════════════════════════════════════════════════════════════
    // CREW STATUS CHECK-INS
    // ════════════════════════════════════════════════════════════════════

    private fun detectCrewIssues(): List<AIInsight> {
        // Solo users don't have crew — skip crew checks
        if (com.guildofsmiths.trademesh.data.RoleContext.isSolo()) return emptyList()

        val insights = mutableListOf<AIInsight>()
        val crew = CrewPresenceRepository.getCrew()
        val now = System.currentTimeMillis()

        crew.forEach { member ->
            when {
                // Off clock for 8+ hours during work day (gone dark)
                member.status == ClockStatus.OFF_CLOCK -> {
                    val hoursSinceSeen = (now - member.lastSeen) / 3_600_000.0
                    if (hoursSinceSeen >= 8 && member.currentJobId != null) {
                        insights.add(AIInsight(
                            type = InsightType.CREW,
                            crewUserId = member.userId,
                            crewName = member.name,
                            title = "${member.name} — no check-in",
                            body = "${member.name} (${member.trade}) has been off clock for ${hoursSinceSeen.toInt()}h. Assigned to ${member.currentJobTitle ?: "a job"} but hasn't checked in."
                        ))
                    }
                }

                // On break for 45+ minutes
                member.status == ClockStatus.ON_BREAK && member.clockInTime != null -> {
                    val minsOnBreak = (now - member.clockInTime) / 60_000
                    if (minsOnBreak >= 45) {
                        insights.add(AIInsight(
                            type = InsightType.CREW,
                            crewUserId = member.userId,
                            crewName = member.name,
                            title = "${member.name} — extended break",
                            body = "${member.name} has been on break for ${minsOnBreak}m. Everything OK?"
                        ))
                    }
                }

                // On clock 10+ hours (overtime alert)
                member.status == ClockStatus.ON_CLOCK && member.clockInTime != null -> {
                    val hoursOnClock = (now - member.clockInTime) / 3_600_000.0
                    if (hoursOnClock >= 10) {
                        insights.add(AIInsight(
                            type = InsightType.CREW,
                            crewUserId = member.userId,
                            crewName = member.name,
                            title = "${member.name} — overtime",
                            body = "${member.name} has been on clock for ${String.format("%.1f", hoursOnClock)}h. Consider overtime approval."
                        ))
                    }
                }
            }
        }

        return insights
    }

    // ════════════════════════════════════════════════════════════════════
    // INSIGHT DELIVERY
    // ════════════════════════════════════════════════════════════════════

    private fun deliverInsight(insight: AIInsight) {
        val mode = getMode()
        if (mode == "auto") {
            // Don't duplicate — check by title + type
            val existing = _autoPosted.value.any { it.title == insight.title && it.type == insight.type }
            if (!existing) {
                _autoPosted.value = _autoPosted.value + insight
                // Cap auto-posted to last 20
                if (_autoPosted.value.size > 20) {
                    _autoPosted.value = _autoPosted.value.takeLast(20)
                }
                // Auto-DM crew members on CREW insights
                if (insight.type == InsightType.CREW && insight.crewUserId != null) {
                    sendCrewDM(insight.crewUserId, insight.crewName ?: "Crew", insight.body)
                }
            }
        } else if (mode == "semi-auto") {
            val existing = _insights.value.any { it.title == insight.title && it.type == insight.type }
            if (!existing) {
                _insights.value = _insights.value + insight
            }
        }
    }

    /**
     * Send a DM to a crew member from the AI supervisor.
     */
    private fun sendCrewDM(crewUserId: String, crewName: String, messageBody: String) {
        try {
            val myUserId = UserPreferences.getUserId()
            val dm = BeaconRepository.getOrCreateDM("default", myUserId, crewUserId, crewName)

            val aiMessage = Message.createAIResponse(
                channelId = dm.id,
                content = messageBody,
                aiModel = "smith-ai",
                aiSource = "supervisor",
                aiContext = "crew-checkin:$crewUserId"
            )
            MessageRepository.addMessage(aiMessage)
            Log.i(TAG, "AI DM sent to $crewName: ${messageBody.take(50)}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to DM crew $crewName: ${e.message}")
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // SINGLE JOB SUMMARY
    // ════════════════════════════════════════════════════════════════════

    /**
     * Generate an AI summary for a specific job.
     */
    suspend fun summarizeJob(job: Job): String? {
        val prompt = AIPrompts.jobSummary(job)
        return OpenRouterClient.chat(
            systemPrompt = AIPrompts.SYSTEM,
            userMessage = prompt,
            maxTokens = 100
        )
    }

    // ════════════════════════════════════════════════════════════════════
    // USER ACTIONS
    // ════════════════════════════════════════════════════════════════════

    fun approveInsight(id: String) {
        val insight = _insights.value.find { it.id == id } ?: return
        _insights.value = _insights.value.filter { it.id != id }
        _autoPosted.value = _autoPosted.value + insight.copy(approved = true)

        // If CREW insight, send the DM on approval
        if (insight.type == InsightType.CREW && insight.crewUserId != null) {
            sendCrewDM(insight.crewUserId, insight.crewName ?: "Crew", insight.body)
        }
    }

    fun dismissInsight(id: String) {
        _insights.value = _insights.value.filter { it.id != id }
    }

    fun clear() {
        _insights.value = emptyList()
        _autoPosted.value = emptyList()
        processedStageChanges.clear()
    }
}
