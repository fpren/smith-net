package com.guildofsmiths.trademesh.data

import com.guildofsmiths.trademesh.ui.timetracking.TimeEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared repository for time entries
 * Accessible by Time Clock and Invoice Generator
 */
object TimeEntryRepository {
    
    private val _entries = MutableStateFlow<List<TimeEntry>>(emptyList())
    val entries: StateFlow<List<TimeEntry>> = _entries.asStateFlow()
    
    fun updateEntries(entries: List<TimeEntry>) {
        _entries.value = entries
    }
    
    fun addEntry(entry: TimeEntry) {
        val current = _entries.value.toMutableList()
        // Remove if exists (update), then add
        current.removeAll { it.id == entry.id }
        current.add(0, entry)
        _entries.value = current
    }
    
    fun updateEntry(entryId: String, updater: (TimeEntry) -> TimeEntry) {
        _entries.value = _entries.value.map { entry ->
            if (entry.id == entryId) updater(entry) else entry
        }
    }
    
    fun removeEntry(entryId: String) {
        _entries.value = _entries.value.filter { it.id != entryId }
    }
    
    /**
     * Get all time entries for a specific job
     */
    fun getEntriesForJob(jobId: String?, jobTitle: String?): List<TimeEntry> {
        return _entries.value.filter { entry ->
            (jobId != null && entry.jobId == jobId) ||
            (jobTitle != null && entry.jobTitle == jobTitle)
        }
    }
    
    /**
     * Get total minutes worked on a job
     */
    fun getTotalMinutesForJob(jobId: String?, jobTitle: String?): Int {
        return getEntriesForJob(jobId, jobTitle)
            .filter { it.clockOutTime != null }
            .sumOf { it.durationMinutes ?: 0 }
    }
    
    /**
     * Get all completed entries
     */
    fun getCompletedEntries(): List<TimeEntry> {
        return _entries.value.filter { it.clockOutTime != null }
    }

    /**
     * Resolve the display title for a time entry through Job (canonical) with
     * fallback to the denormalized field on the entry. Lets the OnClock UI
     * stay current when a Job's clientName changes without forcing a clock
     * out/in cycle. Legacy entries created before jobId was always set still
     * render via their stored jobTitle.
     */
    fun resolveJobTitle(entry: TimeEntry): String? {
        val jobId = entry.jobId
        if (jobId != null) {
            val match = JobRepository.activeJobs.value.firstOrNull { it.id == jobId }
            if (match != null) return match.title
        }
        return entry.jobTitle
    }

    /**
     * Sum of minutes worked on a job within a time window [since, until).
     * Used by Report / Expenses to compute labor cost from real time entries
     * (replaces the old hardcoded 8h × rate assumption).
     */
    fun getMinutesForJob(jobId: String?, jobTitle: String?, since: Long, until: Long): Int {
        if (jobId == null && jobTitle.isNullOrBlank()) return 0
        return _entries.value
            .asSequence()
            .filter { it.clockOutTime != null }
            .filter {
                (jobId != null && it.jobId == jobId) ||
                (jobId == null && !jobTitle.isNullOrBlank() && it.jobTitle == jobTitle)
            }
            .filter { it.clockInTime >= since && it.clockInTime < until }
            .sumOf { it.durationMinutes ?: 0 }
    }
}
