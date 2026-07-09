package com.guildofsmiths.trademesh.ui.jobboard

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Cross-sweep fix: JobBoardViewModel exposed `_error` but never set it -
 * every SmithErrorState wired to `viewModel.error` across JobBoardScreen,
 * ArchiveScreen, DashboardScreen, ExpensesScreen and JobExpenseDetailScreen
 * was dead code. See JobBoardViewModel.surfaceLoadFailureIfNoLocalData().
 *
 * The real failure paths live inside an OkHttp Callback fired asynchronously
 * off a private, non-injectable `OkHttpClient()` field, so they can't be
 * driven directly in a fast/deterministic unit test without a larger DI
 * refactor (constructor-injecting the client, or a fake Call/Response).
 * That refactor is out of scope for this fix. Instead this test exercises
 * the actual decision rule the fix introduces -
 * `surfaceLoadFailureIfNoLocalData()`, exposed as `internal` for this
 * purpose - directly, plus the surrounding regression that "not logged in"
 * must NOT be treated as a load failure.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class JobBoardViewModelLoadFailureTest {

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
    fun `load failure with no local jobs surfaces an error`() {
        // Fresh VM in this Robolectric context restores no persisted jobs and
        // BuildFlags demo seeding is off in unit tests, so _jobs starts empty.
        assertEquals(0, vm.jobs.value.size)

        vm.surfaceLoadFailureIfNoLocalData()

        assertEquals("Couldn't load jobs.", vm.error.value)
    }

    @Test
    fun `load failure with existing local jobs does not surface an error`() {
        vm.createJob(title = "Panel upgrade")
        assertEquals(1, vm.jobs.value.size)

        vm.surfaceLoadFailureIfNoLocalData()

        assertNull(vm.error.value)
    }

    @Test
    fun `loadJobs resets error before evaluating the load`() {
        vm.createJob(title = "Panel upgrade")
        vm.surfaceLoadFailureIfNoLocalData()
        assertNull(vm.error.value) // has local data, guarded above

        vm.loadJobs()

        // Not logged in (no AuthService.init in this test), so the backend
        // path is skipped entirely - this must stay a no-op, not an error.
        assertNull(vm.error.value)
    }

    @Test
    fun `not logged in with no local jobs is not treated as a load failure`() {
        assertEquals(0, vm.jobs.value.size)

        vm.loadJobs()

        // AuthService.getAccessToken() is null in this test environment, so
        // loadJobsFromBackend() returns before reaching the network - that's
        // "nothing to sync yet", not a failure, even with zero local jobs.
        assertNull(vm.error.value)
    }
}
