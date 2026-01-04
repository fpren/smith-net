package com.guildofsmiths.trademesh.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared repository for jobs - accessible by Job Board and Time Clock
 */
object JobRepository {
    
    data class SimpleJob(
        val id: String,
        val title: String,
        val status: String
    )
    
    private val _activeJobs = MutableStateFlow<List<SimpleJob>>(emptyList())
    val activeJobs: StateFlow<List<SimpleJob>> = _activeJobs.asStateFlow()
    
    fun updateJobs(jobs: List<SimpleJob>) {
        _activeJobs.value = jobs
    }
    
    fun addJob(job: SimpleJob) {
        val current = _activeJobs.value.toMutableList()
        // Remove if exists, then add
        current.removeAll { it.id == job.id }
        current.add(0, job)
        _activeJobs.value = current
    }
    
    fun getActiveJobTitles(): List<String> {
        // Include all non-archived/completed statuses
        val activeStatuses = listOf("TODO", "IN_PROGRESS", "REVIEW", "WORKING", "BACKLOG", "PENDING")
        return _activeJobs.value
            .filter { it.status in activeStatuses }
            .map { it.title }
    }

    /**
     * Create a job from PLAN execution item
     * Used by PLAN transfer to Job Board
     */
    fun createJobFromExecutionItem(item: com.guildofsmiths.trademesh.ui.ExecutionItem): String {
        val jobId = java.util.UUID.randomUUID().toString()

        val job = SimpleJob(
            id = jobId,
            title = item.title,
            status = "TODO" // Jobs arrive ACTIVE and PRE-FILLED
        )

        addJob(job)
        return jobId
    }
}
