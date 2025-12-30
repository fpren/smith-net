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
}
