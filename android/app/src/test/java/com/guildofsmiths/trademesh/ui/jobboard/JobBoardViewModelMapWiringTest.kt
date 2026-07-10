package com.guildofsmiths.trademesh.ui.jobboard

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Job -> map wiring regressions. Three independent breaks used to sever the
 * "post a job -> pin on the map" chain:
 *  1. the create POST carried `priority`, which the backend's zod .strict()
 *     schema rejects with a 400 -- and never carried the address at all;
 *  2. the create response (backend id + coords) was discarded, so local and
 *     backend copies of a job could never reconcile;
 *  3. parseJob demanded fields the backend never sends (createdBy, epoch-long
 *     dates), so every downloaded job threw and the merge silently died.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class JobBoardViewModelMapWiringTest {

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

    // ── create body matches the backend's strict schema ────────────────────

    @Test
    fun `create body carries location and never priority`() {
        val job = Job(
            id = "local-1",
            title = "Panel upgrade",
            clientAddress = "847 Flatbush Ave, Brooklyn NY",
            createdBy = "u1", createdAt = 0L, updatedAt = 0L
        )
        val body = vm.buildCreateJobBody(job)

        assertEquals("Panel upgrade", body.getString("title"))
        assertEquals("847 Flatbush Ave, Brooklyn NY", body.getString("location"))
        assertFalse("priority 400s the strict backend schema", body.has("priority"))
    }

    @Test
    fun `create body omits location when address is blank`() {
        val job = Job(id = "local-1", title = "T", createdBy = "u1", createdAt = 0L, updatedAt = 0L)
        assertFalse(vm.buildCreateJobBody(job).has("location"))
    }

    // ── parseJob survives the real backend response shape ──────────────────

    private fun backendJobJson(
        id: String = "b7e6a1c2-0000-0000-0000-000000000001",
        lat: Double? = 40.6505,
        lng: Double? = -73.9612
    ): JSONObject = JSONObject().apply {
        put("id", id)
        put("foremanId", "f-1")
        put("title", "Backend job")
        put("description", "desc")
        put("status", "planned")
        put("stage", "lead")
        put("location", "847 Flatbush Ave, Brooklyn NY")
        if (lat != null) put("latitude", lat) else put("latitude", JSONObject.NULL)
        if (lng != null) put("longitude", lng) else put("longitude", JSONObject.NULL)
        put("createdAt", "2026-07-10T14:00:00.000Z")
        put("updatedAt", "2026-07-10T14:05:00.000Z")
        put("client", JSONObject().apply { put("id", "c-1"); put("name", "Maria") })
    }

    @Test
    fun `parseJob reads coords, address, foremanId and ISO dates`() {
        val job = vm.parseJob(backendJobJson())

        assertEquals(40.6505, job.latitude!!, 1e-9)
        assertEquals(-73.9612, job.longitude!!, 1e-9)
        assertEquals("847 Flatbush Ave, Brooklyn NY", job.clientAddress)
        assertEquals("Maria", job.clientName)
        assertEquals("f-1", job.createdBy)
        assertEquals(
            java.time.Instant.parse("2026-07-10T14:00:00.000Z").toEpochMilli(),
            job.createdAt
        )
    }

    @Test
    fun `parseJob leaves coords null before geocoding lands`() {
        val job = vm.parseJob(backendJobJson(lat = null, lng = null))
        assertNull(job.latitude)
        assertNull(job.longitude)
    }

    // ── merge pulls geocoded coords onto the local copy ─────────────────────

    @Test
    fun `merge copies backend coords onto local job and appends unknown jobs`() {
        val local = Job(
            id = "shared-id", title = "Local title",
            clientAddress = "847 Flatbush Ave, Brooklyn NY", createdBy = "u1", createdAt = 0L, updatedAt = 0L
        )
        val backendTwin = Job(
            id = "shared-id", title = "Backend title",
            latitude = 40.6505, longitude = -73.9612, createdBy = "f-1", createdAt = 0L, updatedAt = 0L
        )
        val backendOnly = Job(id = "other-id", title = "Remote only", createdBy = "f-1", createdAt = 0L, updatedAt = 0L)

        val merged = vm.mergeBackendJobs(listOf(local), listOf(backendTwin, backendOnly))

        assertEquals(2, merged.size)
        val mine = merged.first { it.id == "shared-id" }
        assertEquals(40.6505, mine.latitude!!, 1e-9)
        assertEquals(-73.9612, mine.longitude!!, 1e-9)
        assertEquals("Local title", mine.title) // local fields win
        assertTrue(merged.any { it.id == "other-id" })
    }

    @Test
    fun `merge never downgrades coords the local job already has`() {
        val local = Job(
            id = "shared-id", title = "T",
            latitude = 1.0, longitude = 2.0, createdBy = "u1", createdAt = 0L, updatedAt = 0L
        )
        val backendTwin = Job(id = "shared-id", title = "T", createdBy = "f-1", createdAt = 0L, updatedAt = 0L)

        val merged = vm.mergeBackendJobs(listOf(local), listOf(backendTwin))

        assertEquals(1.0, merged.single().latitude!!, 1e-9)
        assertEquals(2.0, merged.single().longitude!!, 1e-9)
    }

    // ── the create response's backend identity is adopted ──────────────────

    @Test
    fun `adoptBackendJob swaps the local id so later syncs can match`() {
        vm.createJob(title = "Panel upgrade", clientAddress = "847 Flatbush Ave, Brooklyn NY")
        val localId = vm.jobs.value.last().id

        vm.adoptBackendJob(localId, backendJobJson(id = "backend-uuid-1"))

        val adopted = vm.jobs.value.last()
        assertEquals("backend-uuid-1", adopted.id)
        assertEquals(40.6505, adopted.latitude!!, 1e-9)
        assertFalse(vm.jobs.value.any { it.id == localId })
    }
}
