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
        return _activeJobs.value
            .filter { it.status in listOf("TODO", "IN_PROGRESS", "REVIEW") }
            .map { it.title }
    }
}
