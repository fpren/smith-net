package com.guildofsmiths.trademesh.ui.dashboard

import androidx.lifecycle.ViewModel
import com.guildofsmiths.trademesh.data.TimeEntryRepository
import com.guildofsmiths.trademesh.data.UserPreferences
import com.guildofsmiths.trademesh.ui.jobboard.Job
import com.guildofsmiths.trademesh.ui.jobboard.JobStage
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import com.guildofsmiths.trademesh.data.BeaconRepository
import java.util.Calendar

class DashboardViewModel : ViewModel() {
    private val _jobs = MutableStateFlow<List<Job>>(emptyList())
    val jobs: StateFlow<List<Job>> = _jobs.asStateFlow()

    // All jobs (including closed) for progress tracking
    private var _allJobs: List<Job> = emptyList()

    val isClockedIn = mutableStateOf(UserPreferences.isClockedIn())

    /** Refresh clock state — call when returning to dashboard */
    fun refreshClockState() {
        isClockedIn.value = UserPreferences.isClockedIn()
    }

    /** Get active time entry info (job name, clock-in time) */
    fun getActiveEntryInfo(): Pair<String?, Long> {
        val json = UserPreferences.getActiveTimeEntry() ?: return Pair(null, 0L)
        return try {
            val obj = org.json.JSONObject(json)
            val jobTitle = obj.optString("jobTitle", null)
            val clockInTime = obj.optLong("clockInTime", 0L)
            Pair(jobTitle, clockInTime)
        } catch (e: Exception) {
            Pair(null, 0L)
        }
    }

    fun loadJobs(allJobs: List<Job>) {
        _allJobs = allJobs
        _jobs.value = allJobs.filter { it.stage != JobStage.CLOSED }
    }

    fun getActiveJobCount(): Int = _jobs.value.count { it.stage != JobStage.CLOSED }

    /**
     * Outstanding $ across jobs in INVOICE stage = materials cost + real
     * labor cost (actual minutes worked × hourlyRate). Replaces the old
     * hardcoded 8h × rate assumption that ignored the time tracker.
     */
    fun getOutstandingTotal(): Double = _jobs.value
        .filter { it.stage == JobStage.INVOICE }
        .sumOf { job ->
            val laborMin = TimeEntryRepository.getEntriesForJob(job.id, job.title)
                .filter { it.clockOutTime != null }
                .sumOf { it.durationMinutes ?: 0 }
            job.materials.sumOf { m -> m.totalCost } + (laborMin / 60.0 * job.hourlyRate)
        }

    fun getBusinessName(): String {
        val biz = UserPreferences.getBusinessName()
        if (biz.isNotBlank()) return biz
        val name = UserPreferences.getUserName()
        return name.ifBlank { "Smith Net" }
    }

    fun hasAnyJobs(): Boolean = _allJobs.isNotEmpty()

    // ── Progress helpers ───────────────────────────────────────────

    private fun getMonthStart(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun getCompletedThisMonth(): Int {
        val monthStart = getMonthStart()
        return _allJobs.count {
            it.stage == JobStage.CLOSED && it.updatedAt >= monthStart
        }
    }

    fun getTotalThisMonth(): Int {
        val monthStart = getMonthStart()
        return _allJobs.count { it.createdAt >= monthStart } + getActiveJobCount()
    }

    fun getCommonJobTypes(): List<String> {
        return _allJobs
            .mapNotNull { it.title.takeIf { t -> t.isNotBlank() } }
            .groupBy { it }
            .entries
            .sortedByDescending { it.value.size }
            .take(2)
            .map { it.key }
    }

    fun getEarnedThisMonth(): Double {
        val monthStart = getMonthStart()
        val monthEnd = monthStart + 31L * 86_400_000L
        return _allJobs
            .filter { it.stage == JobStage.CLOSED && it.updatedAt >= monthStart }
            .sumOf { job ->
                val laborMin = TimeEntryRepository
                    .getMinutesForJob(job.id, job.title, monthStart, monthEnd)
                job.materials.sumOf { m -> m.totalCost } + (laborMin / 60.0 * job.hourlyRate)
            }
    }

    /**
     * Cumulative billable across every job (any stage) — real hours worked
     * × hourlyRate + materials. Reflects "what's been spent on labor +
     * supplies" from the contractor's POV, not gated by invoice/closed
     * status. Lets the dashboard show progress even before the user
     * formally advances jobs to INVOICE/CLOSED.
     */
    fun getSpentToDate(): Double = _allJobs.sumOf { job ->
        val laborMin = TimeEntryRepository.getEntriesForJob(job.id, job.title)
            .filter { it.clockOutTime != null }
            .sumOf { it.durationMinutes ?: 0 }
        job.materials.sumOf { m -> m.totalCost } + (laborMin / 60.0 * job.hourlyRate)
    }

    /** Total clocked-in minutes across all jobs (completed entries). */
    fun getMinutesWorkedToDate(): Int = _allJobs.sumOf { job ->
        TimeEntryRepository.getEntriesForJob(job.id, job.title)
            .filter { it.clockOutTime != null }
            .sumOf { it.durationMinutes ?: 0 }
    }

    /**
     * Minutes clocked-in *today*, including the active session's portion that
     * falls inside today's calendar window. Cross-midnight sessions only
     * contribute their post-midnight minutes — matches the OnClock screen.
     */
    fun getMinutesWorkedToday(): Int {
        val todayStart = getTodayStart()
        val now = System.currentTimeMillis()
        return _allJobs.sumOf { job ->
            TimeEntryRepository.getEntriesForJob(job.id, job.title).sumOf { e ->
                val start = maxOf(e.clockInTime, todayStart)
                val end = e.clockOutTime ?: now
                if (end <= todayStart) 0
                else ((end - start) / 60_000L).toInt().coerceAtLeast(0)
            }
        }
    }

    // ── Schedule helpers ──────────────────────────────────────────

    fun getScheduledDays(): Set<Int> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val monthStart = cal.timeInMillis
        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)
        val monthEnd = monthStart + daysInMonth.toLong() * 86_400_000L

        val days = mutableSetOf<Int>()

        // Jobs with estimatedStartDate in this month
        _jobs.value.forEach { job ->
            val start = job.estimatedStartDate
            if (start != null && start in monthStart until monthEnd) {
                val jobCal = Calendar.getInstance()
                jobCal.timeInMillis = start
                days.add(jobCal.get(Calendar.DAY_OF_MONTH))
            }
        }

        // Also mark today if there's an in-progress job
        val today = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        if (_jobs.value.any { it.stage == JobStage.IN_PROGRESS }) {
            days.add(today)
        }

        return days
    }

    fun getJobsForDay(dayOfMonth: Int): List<Job> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, dayOfMonth)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val dayStart = cal.timeInMillis
        val dayEnd = dayStart + 86_400_000L

        val result = _jobs.value.filter { job ->
            val start = job.estimatedStartDate
            start != null && start >= dayStart && start < dayEnd
        }

        // Also include in-progress jobs on today
        val today = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
        if (dayOfMonth == today) {
            val inProgress = _jobs.value.filter { it.stage == JobStage.IN_PROGRESS && it !in result }
            return result + inProgress
        }
        return result
    }

    fun getPrioritizedJobs(): List<Job> {
        val stagePriority = mapOf(
            JobStage.IN_PROGRESS to 0,
            JobStage.INVOICE to 1,
            JobStage.REVIEW to 2
        )
        return _jobs.value.sortedWith(
            compareBy<Job> { stagePriority[it.stage] ?: 3 }
                .thenByDescending { it.updatedAt }
        )
    }

    // ── Activity helpers ──────────────────────────────────────────

    enum class ActivityType { JOB, MESSAGE, SUPPLY }

    data class ActivityEvent(
        val type: ActivityType,
        val description: String,
        val timestamp: Long,
        val jobId: String? = null
    )

    fun getTodayActivity(): List<ActivityEvent> {
        val todayStart = getTodayStart()
        val events = mutableListOf<ActivityEvent>()
        val timeFormat = java.text.SimpleDateFormat("h:mma", java.util.Locale.US)

        // Job stage changes (jobs updated today). IN_PROGRESS is intentionally
        // skipped: clock-in already surfaces in the dashboard clock pill and
        // JOBS panel, so a duplicate "Started · <client>" line in TODAY would
        // just rename the active job a third time.
        _allJobs.filter { it.updatedAt >= todayStart && it.stage != JobStage.IN_PROGRESS }.forEach { job ->
            val time = timeFormat.format(java.util.Date(job.updatedAt)).lowercase()
            val name = job.clientName ?: job.title
            val desc = when (job.stage) {
                JobStage.REVIEW -> "${job.stage.icon} Marked complete · $name · $time"
                JobStage.INVOICE -> "${job.stage.icon} Invoice generated · $name · $time"
                JobStage.CLOSED -> "${job.stage.icon} Closed · $name · $time"
                else -> "${job.stage.icon} ${job.stage.displayName} · $name · $time"
            }
            events.add(ActivityEvent(ActivityType.JOB, desc, job.updatedAt, job.id))
        }

        // Messages from beacons (channels with recent messages)
        BeaconRepository.beacons.value.flatMap { it.channels }.forEach { channel ->
            val msgTime = channel.lastMessageTime
            if (msgTime != null && msgTime >= todayStart && channel.lastMessagePreview != null) {
                val time = timeFormat.format(java.util.Date(msgTime)).lowercase()
                val preview = channel.lastMessagePreview.take(30)
                events.add(ActivityEvent(ActivityType.MESSAGE, "(✉) ${channel.name}: \"$preview\" · $time", msgTime))
            }
        }

        // Materials checked off today
        _allJobs.forEach { job ->
            job.materials.filter { it.checked && it.checkedAt != null && it.checkedAt >= todayStart }.forEach { mat ->
                val time = timeFormat.format(java.util.Date(mat.checkedAt!!)).lowercase()
                val cost = if (mat.totalCost > 0) " · $${String.format("%.0f", mat.totalCost)}" else ""
                events.add(ActivityEvent(ActivityType.SUPPLY, "(◻) ${mat.name} checked$cost · $time", mat.checkedAt, job.id))
            }
        }

        return events.sortedByDescending { it.timestamp }.take(5)
    }

    private fun getTodayStart(): Long {
        val cal = Calendar.getInstance()
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun getScheduledJobs(): List<Pair<String, Job?>> {
        val now = Calendar.getInstance()
        val today = now.clone() as Calendar
        today.set(Calendar.HOUR_OF_DAY, 0)
        today.set(Calendar.MINUTE, 0)
        today.set(Calendar.SECOND, 0)
        today.set(Calendar.MILLISECOND, 0)

        val result = mutableListOf<Pair<String, Job?>>()
        val dateFormat = java.text.SimpleDateFormat("MMM d", java.util.Locale.US)

        for (dayOffset in 0..2) {
            val dayCal = today.clone() as Calendar
            dayCal.add(Calendar.DAY_OF_YEAR, dayOffset)
            val dayStart = dayCal.timeInMillis
            val dayEnd = dayStart + 86_400_000L

            val label = when (dayOffset) {
                0 -> "Today"
                1 -> "Tomorrow"
                else -> dateFormat.format(dayCal.time)
            }

            // Find job scheduled for this day
            val job = _jobs.value.firstOrNull { j ->
                val start = j.estimatedStartDate
                start != null && start >= dayStart && start < dayEnd
            }

            // Also match active in-progress jobs to today
            val matchedJob = job ?: if (dayOffset == 0) {
                _jobs.value.firstOrNull { it.stage == JobStage.IN_PROGRESS }
            } else null

            result.add(label to matchedJob)
        }

        // Only return if at least one day has a job
        return if (result.any { it.second != null }) result else emptyList()
    }
}
