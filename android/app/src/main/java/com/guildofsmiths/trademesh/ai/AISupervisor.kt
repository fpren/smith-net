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
        CREW,       // Crew status alert
        FINANCIAL   // Needs user permission (invoices, payments, expenses)
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
    private var lastWeatherCheck: Long = 0
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
            val isSolo = com.guildofsmiths.trademesh.data.RoleContext.isSolo()

            // 1. Job issue detection (stale, budget, materials, invoices, proposals)
            val jobIssues = detectJobIssues(jobs)
            Log.i(TAG, "Monitor 1 — Job issues: ${jobIssues.size} found${jobIssues.take(2).joinToString("") { " [${it.title}: ${it.body.take(40)}]" }}")
            allInsights.addAll(jobIssues)

            // 2. Self-monitoring (Solo) or crew check-ins (Team)
            if (isSolo) {
                val selfIssues = detectSelfIssues()
                Log.i(TAG, "Monitor 2 — Self: ${selfIssues.size} found${selfIssues.take(2).joinToString("") { " [${it.title}]" }}")
                allInsights.addAll(selfIssues)
            } else {
                val crewIssues = detectCrewIssues()
                Log.i(TAG, "Monitor 2 — Crew: ${crewIssues.size} found")
                allInsights.addAll(crewIssues)
            }

            // 3. Client follow-ups (unresponsive clients, auto follow-up)
            val followUps = detectClientFollowUps()
            Log.i(TAG, "Monitor 3 — Client follow-ups: ${followUps.size} found")
            allInsights.addAll(followUps)

            // 4. Auto-respond to client messages when busy
            monitorClientMessages()
            Log.i(TAG, "Monitor 4 — Auto-respond: checked (clocked in: ${UserPreferences.isClockedIn()})")

            // 5. Schedule conflicts (overlapping jobs)
            val conflicts = detectScheduleConflicts(jobs)
            Log.i(TAG, "Monitor 5 — Schedule conflicts: ${conflicts.size} found")
            allInsights.addAll(conflicts)

            // 6. Weather alerts (bad weather at job sites)
            val weather = checkWeather(jobs)
            Log.i(TAG, "Monitor 6 — Weather: ${weather.size} alerts")
            allInsights.addAll(weather)

            // 7. Travel time between same-day jobs
            val travel = estimateTravelTime(jobs)
            Log.i(TAG, "Monitor 7 — Travel time: ${travel.size} warnings")
            allInsights.addAll(travel)

            // 8. AI-powered check-in (if API key set and enough jobs)
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

            // 9. End-of-day sweep (daily/weekly summaries)
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
            val issue = AIPrompts.detectIssue(job) ?: return@forEach
            // Invoice/payment issues need user permission → FINANCIAL type
            val isFinancial = issue.contains("Invoice", ignoreCase = true) ||
                              issue.contains("overdue", ignoreCase = true) ||
                              issue.contains("payment", ignoreCase = true)
            insights.add(AIInsight(
                type = if (isFinancial) InsightType.FINANCIAL else InsightType.ALERT,
                jobId = job.id,
                jobTitle = job.clientName ?: job.title,
                title = job.clientName ?: job.title,
                body = issue
            ))
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
    // SOLO SELF-MONITORING — SmithAI watches the user's own work patterns
    // ════════════════════════════════════════════════════════════════════

    private fun detectSelfIssues(): List<AIInsight> {
        val insights = mutableListOf<AIInsight>()
        val isClockedIn = UserPreferences.isClockedIn()
        val clockInTime = UserPreferences.getClockInTime()
        val now = System.currentTimeMillis()
        val userName = UserPreferences.getUserName().ifBlank { "Boss" }

        if (isClockedIn && clockInTime > 0) {
            val hoursWorked = (now - clockInTime) / 3_600_000.0
            val minutesWorked = (now - clockInTime) / 60_000

            // Working 4+ hours without break — lunch reminder
            if (hoursWorked >= 4.0 && hoursWorked < 4.5) {
                insights.add(AIInsight(
                    type = InsightType.CREW,
                    title = "Lunch break",
                    body = "You've been on the clock for ${String.format("%.0f", hoursWorked)}h. Time to take a lunch break — I'll keep an eye on things."
                ))
            }

            // Working 8+ hours — end of day reminder
            if (hoursWorked >= 8.0 && hoursWorked < 8.5) {
                insights.add(AIInsight(
                    type = InsightType.CREW,
                    title = "End of day",
                    body = "You've been working ${String.format("%.1f", hoursWorked)}h today. Consider wrapping up — I'll handle any client messages that come in."
                ))
            }

            // Working 10+ hours — overtime alert
            if (hoursWorked >= 10.0) {
                insights.add(AIInsight(
                    type = InsightType.ALERT,
                    title = "Overtime",
                    body = "You're at ${String.format("%.1f", hoursWorked)}h today — that's overtime. Make sure to bill accordingly."
                ))
            }
        }

        return insights
    }

    // ════════════════════════════════════════════════════════════════════
    // CLIENT FOLLOW-UPS — auto-follow up on unresponsive clients
    // ════════════════════════════════════════════════════════════════════

    private fun detectClientFollowUps(): List<AIInsight> {
        val insights = mutableListOf<AIInsight>()
        val now = System.currentTimeMillis()
        val threeDaysMs = 3 * 24 * 3_600_000L

        val beacons = com.guildofsmiths.trademesh.data.BeaconRepository.beacons.value
        val allChannels = beacons.flatMap { it.channels }

        allChannels.forEach { channel ->
            if (channel.type != com.guildofsmiths.trademesh.data.ChannelType.DM) return@forEach
            if (channel.id.contains("smith-ai")) return@forEach

            val lastTime = channel.lastMessageTime ?: return@forEach
            val daysSinceMessage = (now - lastTime) / 86_400_000

            // Client hasn't responded in 3+ days
            if (daysSinceMessage >= 3) {
                val clientName = channel.name
                insights.add(AIInsight(
                    type = InsightType.ALERT,
                    title = "Follow up — $clientName",
                    body = "No response from $clientName in ${daysSinceMessage}d. Sending a follow-up."
                ))

                // Auto-send follow-up message
                val myUserId = UserPreferences.getUserId()
                val userName = UserPreferences.getUserName().ifBlank { "your tradesperson" }
                sendCrewDM(
                    crewUserId = channel.members.firstOrNull { it != myUserId } ?: return@forEach,
                    crewName = clientName,
                    messageBody = "Hi $clientName, just checking in. Let me know if you have any questions or need anything. — $userName via SmithAI"
                )
            }
        }
        return insights
    }

    // ════════════════════════════════════════════════════════════════════
    // AUTO-RESPOND TO CLIENT MESSAGES — reply when user is busy
    // ════════════════════════════════════════════════════════════════════

    private fun monitorClientMessages() {
        if (!UserPreferences.isClockedIn()) return // only auto-respond when working

        val now = System.currentTimeMillis()
        val fiveMinMs = 5 * 60_000L
        val userName = UserPreferences.getUserName().ifBlank { "the tradesperson" }
        val myUserId = UserPreferences.getUserId()

        val beacons = com.guildofsmiths.trademesh.data.BeaconRepository.beacons.value
        val allChannels = beacons.flatMap { it.channels }

        allChannels.forEach { channel ->
            if (channel.type != com.guildofsmiths.trademesh.data.ChannelType.DM) return@forEach
            if (channel.id.contains("smith-ai")) return@forEach

            val lastTime = channel.lastMessageTime ?: return@forEach
            // Message received in last 5 minutes (fresh)
            if (now - lastTime < fiveMinMs && channel.unreadCount > 0) {
                val clientName = channel.name
                val peerId = channel.members.firstOrNull { it != myUserId } ?: return@forEach

                // Check we haven't already auto-replied recently
                val lastAutoReplyKey = "auto_reply_${channel.id}"
                val lastAutoReply = autoReplyTimestamps[lastAutoReplyKey] ?: 0L
                if (now - lastAutoReply < 30 * 60_000L) return@forEach // throttle: max 1 per 30 min

                autoReplyTimestamps[lastAutoReplyKey] = now

                sendCrewDM(
                    crewUserId = peerId,
                    crewName = clientName,
                    messageBody = "Hi! $userName is currently on a job site and will get back to you shortly. — SmithAI"
                )
                Log.i(TAG, "Auto-replied to $clientName (user is clocked in)")
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // SCHEDULE CONFLICTS — overlapping jobs on same day
    // ════════════════════════════════════════════════════════════════════

    private fun detectScheduleConflicts(jobs: List<Job>): List<AIInsight> {
        val insights = mutableListOf<AIInsight>()
        val activeJobs = jobs.filter { it.stage == JobStage.IN_PROGRESS || it.stage == JobStage.APPROVED }

        if (activeJobs.size >= 2) {
            // Check for same-day overlaps based on start dates
            val today = System.currentTimeMillis()
            val todayStart = today - (today % 86_400_000L)
            val todayEnd = todayStart + 86_400_000L

            val todayJobs = activeJobs.filter { job ->
                val startDate = job.estimatedStartDate ?: job.createdAt
                startDate in todayStart..todayEnd || job.stage == JobStage.IN_PROGRESS
            }

            if (todayJobs.size >= 2) {
                val names = todayJobs.take(3).joinToString(" and ") { it.clientName ?: it.title }
                insights.add(AIInsight(
                    type = InsightType.ALERT,
                    title = "Schedule conflict",
                    body = "You have ${todayJobs.size} active jobs today: $names. Make sure you have time for all of them."
                ))
            }
        }
        return insights
    }

    // ════════════════════════════════════════════════════════════════════
    // WEATHER ALERTS — bad weather at job sites
    // ════════════════════════════════════════════════════════════════════

    private suspend fun checkWeather(jobs: List<Job>): List<AIInsight> {
        val insights = mutableListOf<AIInsight>()
        val now = System.currentTimeMillis()

        // Throttle: check weather max once per 6 hours
        val lastCheck = lastWeatherCheck
        if (now - lastCheck < 6 * 3_600_000L) return insights

        val activeJobs = jobs.filter { it.stage == JobStage.IN_PROGRESS || it.stage == JobStage.APPROVED }
        if (activeJobs.isEmpty()) return insights

        // Known coordinates for job sites
        val siteCoords = mapOf(
            "847 Flatbush Ave, Brooklyn NY" to Pair(40.6505, -73.9612),
            "55 W 125th St, Apt 4B, Manhattan NY" to Pair(40.8088, -73.9442),
            "1220 Ocean Pkwy, Brooklyn NY" to Pair(40.6275, -73.9685),
        )

        for (job in activeJobs.take(3)) {
            val coords = siteCoords[job.clientAddress] ?: continue
            try {
                val url = "https://api.open-meteo.com/v1/forecast?latitude=${coords.first}&longitude=${coords.second}&daily=precipitation_sum,temperature_2m_max&timezone=America/New_York&forecast_days=1"
                val request = okhttp3.Request.Builder().url(url).build()
                val response = okhttp3.OkHttpClient().newCall(request).execute()
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: continue
                    val json = org.json.JSONObject(body)
                    val daily = json.optJSONObject("daily") ?: continue
                    val precip = daily.optJSONArray("precipitation_sum")?.optDouble(0, 0.0) ?: 0.0
                    val maxTemp = daily.optJSONArray("temperature_2m_max")?.optDouble(0, 70.0) ?: 70.0

                    if (precip > 5.0) {
                        insights.add(AIInsight(
                            type = InsightType.ALERT,
                            title = "Rain alert — ${job.clientName ?: "job site"}",
                            body = "Heavy rain expected (${String.format("%.0f", precip)}mm) at ${job.clientAddress.take(30)}. Plan indoor work or reschedule."
                        ))
                    } else if (maxTemp > 35.0) { // 95°F
                        insights.add(AIInsight(
                            type = InsightType.ALERT,
                            title = "Heat alert — ${job.clientName ?: "job site"}",
                            body = "Extreme heat (${String.format("%.0f", maxTemp * 9/5 + 32)}°F) at ${job.clientAddress.take(30)}. Stay hydrated, take extra breaks."
                        ))
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Weather check failed for ${job.clientAddress}: ${e.message}")
            }
        }

        lastWeatherCheck = now
        return insights
    }

    // ════════════════════════════════════════════════════════════════════
    // TRAVEL TIME — drive time between same-day jobs
    // ════════════════════════════════════════════════════════════════════

    private suspend fun estimateTravelTime(jobs: List<Job>): List<AIInsight> {
        val insights = mutableListOf<AIInsight>()
        val activeJobs = jobs.filter {
            (it.stage == JobStage.IN_PROGRESS || it.stage == JobStage.APPROVED) && it.clientAddress.isNotBlank()
        }
        if (activeJobs.size < 2) return insights

        val siteCoords = mapOf(
            "847 Flatbush Ave, Brooklyn NY" to Pair(-73.9612, 40.6505),
            "55 W 125th St, Apt 4B, Manhattan NY" to Pair(-73.9442, 40.8088),
            "1220 Ocean Pkwy, Brooklyn NY" to Pair(-73.9685, 40.6275),
        )

        // Check pairs of jobs for travel time
        for (i in activeJobs.indices) {
            for (j in i + 1 until activeJobs.size) {
                val coordsA = siteCoords[activeJobs[i].clientAddress] ?: continue
                val coordsB = siteCoords[activeJobs[j].clientAddress] ?: continue

                try {
                    val url = "https://router.project-osrm.org/route/v1/driving/${coordsA.first},${coordsA.second};${coordsB.first},${coordsB.second}?overview=false"
                    val request = okhttp3.Request.Builder().url(url).build()
                    val response = okhttp3.OkHttpClient().newCall(request).execute()
                    if (response.isSuccessful) {
                        val body = response.body?.string() ?: continue
                        val json = org.json.JSONObject(body)
                        val routes = json.optJSONArray("routes") ?: continue
                        if (routes.length() > 0) {
                            val durationSec = routes.getJSONObject(0).optDouble("duration", 0.0)
                            val durationMin = (durationSec / 60).toInt()

                            if (durationMin >= 15) {
                                val nameA = activeJobs[i].clientName ?: activeJobs[i].title
                                val nameB = activeJobs[j].clientName ?: activeJobs[j].title
                                insights.add(AIInsight(
                                    type = InsightType.CHECKIN,
                                    title = "Travel time",
                                    body = "${durationMin} min drive between $nameA and $nameB. Plan accordingly."
                                ))
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Travel time check failed: ${e.message}")
                }
            }
        }
        return insights
    }

    // Track auto-reply throttling
    private val autoReplyTimestamps = mutableMapOf<String, Long>()

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
