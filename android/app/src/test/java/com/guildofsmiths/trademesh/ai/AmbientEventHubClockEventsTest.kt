package com.guildofsmiths.trademesh.ai

import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers audit Gap 3: AmbientEventHub.timeEntryCreatedFlow must deliver real
 * clock events (not the old mock flow) so AISupervisor can pick up active job
 * context without polling SharedPreferences.
 *
 * AmbientEventHub is a singleton with a SharedFlow(replay=1). We assert the
 * payload shape of emitted events directly — that's the "context payload is
 * correct 100% of the time" pass criterion from the test plan.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AmbientEventHubClockEventsTest {

    @Test
    fun `clock-in emit produces TimeEntryEvent with jobId and entryType`() = runTest {
        AmbientEventHub.timeEntryCreatedFlow.test {
            // Drain whatever is currently replayed, then inject a fresh event.
            cancelAndIgnoreRemainingEvents()
        }

        AmbientEventHub.emitClockEvent(
            clockIn = true,
            jobId = "job-42",
            entryType = "REGULAR"
        )

        AmbientEventHub.timeEntryCreatedFlow.test {
            val event = awaitItem()
            assertTrue("clockIn flag should be true", event.clockIn)
            assertFalse("clockOut should be false", event.clockOut)
            assertEquals("job-42", event.jobId)
            assertEquals("REGULAR", event.entryType)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `clock-out emit produces TimeEntryEvent with clockOut flag`() = runTest {
        AmbientEventHub.emitClockEvent(
            clockIn = false,
            clockOut = true,
            jobId = "job-99",
            entryType = "REGULAR"
        )

        AmbientEventHub.timeEntryCreatedFlow.test {
            val event = awaitItem()
            assertFalse(event.clockIn)
            assertTrue(event.clockOut)
            assertEquals("job-99", event.jobId)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `null jobId clock-in does not crash subscriber (general time entry)`() = runTest {
        // Negative-check from the audit plan: clock-in with no Job selected.
        AmbientEventHub.emitClockEvent(
            clockIn = true,
            jobId = null,
            entryType = "REGULAR"
        )

        AmbientEventHub.timeEntryCreatedFlow.test {
            val event = awaitItem()
            assertTrue(event.clockIn)
            assertNull(event.jobId)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
