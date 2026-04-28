package com.guildofsmiths.trademesh.data

import com.guildofsmiths.trademesh.ui.timetracking.TimeEntry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Covers audit Gaps 1 + 5: OnClock employer must resolve through the canonical
 * Job at read time, with fallback to the legacy denormalized field.
 */
class TimeEntryRepositoryResolveTest {

    @After
    fun tearDown() {
        // JobRepository is a singleton — reset to avoid test bleed.
        JobRepository.updateJobs(emptyList())
    }

    @Test
    fun `resolves through Job when jobId matches`() {
        JobRepository.updateJobs(
            listOf(JobRepository.SimpleJob(id = "job-1", title = "Maria Rodriguez", status = "TODO"))
        )
        val entry = sampleEntry(jobId = "job-1", jobTitle = "STALE")

        val resolved = TimeEntryRepository.resolveJobTitle(entry)

        assertEquals("Maria Rodriguez", resolved)
    }

    @Test
    fun `falls back to entry jobTitle when jobId is unknown`() {
        JobRepository.updateJobs(emptyList())
        val entry = sampleEntry(jobId = "missing", jobTitle = "Legacy Client")

        val resolved = TimeEntryRepository.resolveJobTitle(entry)

        assertEquals("Legacy Client", resolved)
    }

    @Test
    fun `falls back to entry jobTitle when jobId is null`() {
        JobRepository.updateJobs(
            listOf(JobRepository.SimpleJob(id = "job-1", title = "Should Not Be Used", status = "TODO"))
        )
        val entry = sampleEntry(jobId = null, jobTitle = "Manual Entry")

        val resolved = TimeEntryRepository.resolveJobTitle(entry)

        assertEquals("Manual Entry", resolved)
    }

    @Test
    fun `returns null when both jobId is null and jobTitle is null`() {
        val entry = sampleEntry(jobId = null, jobTitle = null)

        assertNull(TimeEntryRepository.resolveJobTitle(entry))
    }

    @Test
    fun `live rename of Job propagates without touching the entry`() {
        JobRepository.updateJobs(
            listOf(JobRepository.SimpleJob(id = "job-1", title = "Old Name", status = "TODO"))
        )
        val entry = sampleEntry(jobId = "job-1", jobTitle = "Old Name")
        assertEquals("Old Name", TimeEntryRepository.resolveJobTitle(entry))

        JobRepository.updateJobs(
            listOf(JobRepository.SimpleJob(id = "job-1", title = "New Name", status = "TODO"))
        )

        // Same entry instance, no clock-out/in cycle, but the resolved name follows the Job.
        assertEquals("New Name", TimeEntryRepository.resolveJobTitle(entry))
    }

    private fun sampleEntry(jobId: String?, jobTitle: String?) = TimeEntry(
        id = "entry-1",
        userId = "user-1",
        userName = "Tester",
        clockInTime = 0L,
        jobId = jobId,
        jobTitle = jobTitle,
        createdAt = 0L,
        immutableHash = "hash"
    )
}
