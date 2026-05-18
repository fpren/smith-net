package com.guildofsmiths.trademesh.ui.jobboard

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Covers audit Gap 4 (job published to flow on creation) and the bonus
 * proposalId plumbing identified during exploration.
 *
 * JobBoardViewModel uses viewModelScope which needs Dispatchers.Main; we swap
 * in an UnconfinedTestDispatcher so init's loadJobs() launch resolves
 * synchronously and never blocks the assertion.
 *
 * Now extends AndroidViewModel so tests run under Robolectric to supply the
 * Application context required by the constructor.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class JobBoardViewModelCreateJobTest {

    private lateinit var vm: JobBoardViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val app = ApplicationProvider.getApplicationContext<android.app.Application>()
        vm = JobBoardViewModel(app)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `createJob publishes new job to jobs flow`() {
        val before = vm.jobs.value.size

        vm.createJob(title = "Panel upgrade")

        val after = vm.jobs.value
        assertEquals(before + 1, after.size)
        assertEquals("Panel upgrade", after.last().title)
    }

    @Test
    fun `createJob writes proposalId when provided (proposal-to-job link)`() {
        vm.createJob(title = "From proposal", proposalId = "intent_123")

        val newest = vm.jobs.value.last()
        assertEquals("intent_123", newest.proposalId)
    }

    @Test
    fun `createJob proposalId is null by default (wizard path)`() {
        vm.createJob(title = "Wizard job")

        val newest = vm.jobs.value.last()
        assertNull(newest.proposalId)
    }

    @Test
    fun `createJob seeds tasks from taskDescriptions and they appear after selectJob`() {
        vm.createJob(
            title = "Multi-task job",
            taskDescriptions = listOf("Pull cables", "Set panel", "Final inspection")
        )
        val created = vm.jobs.value.last()
        vm.selectJob(created)

        val tasks = vm.tasks.value
        assertEquals(3, tasks.size)
        assertEquals(listOf("Pull cables", "Set panel", "Final inspection"), tasks.map { it.title })
        assertEquals(created.id, tasks.first().jobId)
    }

    @Test
    fun `createJob with blank task descriptions does not seed empty rows`() {
        vm.createJob(
            title = "Sparse",
            taskDescriptions = listOf("real task", "  ", "")
        )
        val created = vm.jobs.value.last()
        vm.selectJob(created)

        val tasks = vm.tasks.value
        assertEquals(1, tasks.size)
        assertEquals("real task", tasks.first().title)
    }

    @Test
    fun `createJob with no taskDescriptions leaves tasks empty (regression)`() {
        vm.createJob(title = "No tasks job")
        val created = vm.jobs.value.last()
        vm.selectJob(created)

        // Defensive: if seeding leaked, tasks would be non-empty.
        assertEquals(0, vm.tasks.value.size)
        assertNotNull(created)
    }
}
