package com.guildofsmiths.trademesh.data.invoice

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.guildofsmiths.trademesh.db.AppDatabase
import com.guildofsmiths.trademesh.db.PendingInvoicePushDao
import com.guildofsmiths.trademesh.ui.invoice.Invoice
import com.guildofsmiths.trademesh.ui.invoice.InvoiceLineItem
import com.guildofsmiths.trademesh.ui.invoice.InvoiceMode
import com.guildofsmiths.trademesh.ui.invoice.LineItemCategory
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Fake ApiClient: records calls + lets the test configure per-op behavior.
 */
class FakeInvoicesApi : InvoicesApi {
    data class Call(val op: String, val arg: String)
    val calls = mutableListOf<Call>()

    /** Captures the raw payload JSON the worker submits, so tests can verify it. */
    val createPayloads = mutableListOf<String>()

    /** Per-op behavior: throw, return, etc. createBehavior receives the parsed idempotencyKey. */
    var createBehavior: (idempotencyKey: String) -> String = { _ -> "srv-${calls.size}" }
    var lineItemBehavior: (String, InvoiceLineItem) -> Unit = { _, _ -> }
    var statusBehavior: (String, String) -> Unit = { _, _ -> }
    var deleteBehavior: (String) -> Unit = { _ -> }

    override suspend fun createInvoiceWithPayload(payloadJson: String): String {
        createPayloads.add(payloadJson)
        // Extract idempotencyKey from the payload for the test's Call.arg
        val key = org.json.JSONObject(payloadJson).getString("idempotencyKey")
        calls.add(Call("CREATE", key))
        return createBehavior(key)
    }
    override suspend fun addLineItem(backendInvoiceId: String, item: InvoiceLineItem, clientItemId: String) {
        calls.add(Call("LINE:$clientItemId", backendInvoiceId))
        lineItemBehavior(backendInvoiceId, item)
    }
    override suspend fun setStatus(backendInvoiceId: String, status: String) {
        calls.add(Call("STATUS:$status", backendInvoiceId))
        statusBehavior(backendInvoiceId, status)
    }
    override suspend fun deleteInvoice(backendInvoiceId: String) {
        calls.add(Call("DELETE", backendInvoiceId))
        deleteBehavior(backendInvoiceId)
    }
}

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33], manifest = Config.NONE)
class InvoicesOutboxTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: PendingInvoicePushDao
    private lateinit var api: FakeInvoicesApi
    private lateinit var outbox: InvoicesOutbox
    private lateinit var worker: InvoicesPushWorker
    private val clock = AtomicClock(start = 1_000L)

    @Before fun setUp() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.pendingInvoicePushDao()
        api = FakeInvoicesApi()
        outbox = InvoicesOutbox(dao, scheduler = NoopScheduler, clock = clock)
        worker = InvoicesPushWorker(dao, api, clock = clock)
    }

    @After fun tearDown() { db.close() }

    @Test fun enqueueCreate_inserts_pending_row() = runBlocking {
        outbox.enqueueCreate(sampleInvoice("inv-a"))
        val row = dao.findById("inv-a")
        assertNotNull(row)
        assertEquals("CREATE",  row!!.op)
        assertEquals("pending", row.status)
        assertNotNull(row.payloadJson)
    }

    @Test fun worker_drain_201_marks_done_writes_backendId() = runBlocking {
        outbox.enqueueCreate(sampleInvoice("inv-b"))
        api.createBehavior = { _ -> "srv-b" }
        worker.drainOnce()
        val row = dao.findById("inv-b")!!
        assertEquals("done",  row.status)
        assertEquals("srv-b", row.backendId)

        // Wire body sent to backend matches what was enqueued (full fields,
        // not a re-serialized stub with empty clientName/etc).
        assertEquals(1, api.createPayloads.size)
        val sent = org.json.JSONObject(api.createPayloads[0])
        assertEquals("Acme", sent.getString("clientName"))
    }

    @Test fun worker_drain_500_reverts_to_pending_increments_attempts() = runBlocking {
        outbox.enqueueCreate(sampleInvoice("inv-c"))
        api.createBehavior = { _ -> throw java.io.IOException("HTTP 500") }
        worker.drainOnce()
        val row = dao.findById("inv-c")!!
        assertEquals("pending", row.status)
        assertEquals(1, row.attempts)
        assertTrue(row.lastError?.contains("500") == true)
    }

    @Test fun worker_drain_422_marks_failed_no_retry() = runBlocking {
        outbox.enqueueCreate(sampleInvoice("inv-d"))
        api.createBehavior = { _ -> throw ApiClientError(422, "validation boom") }
        worker.drainOnce()
        worker.drainOnce()  // should be a no-op the second time
        val row = dao.findById("inv-d")!!
        assertEquals("failed", row.status)
        assertTrue(row.lastError?.contains("422") == true)
        assertEquals(1, api.calls.count { it.op == "CREATE" })
    }

    @Test fun enqueueDiscard_before_drain_cancels_create_row() = runBlocking {
        outbox.enqueueCreate(sampleInvoice("inv-e"))
        outbox.enqueueDiscard("inv-e")
        worker.drainOnce()
        val row = dao.findById("inv-e")!!
        assertEquals("cancelled", row.status)
        assertEquals(0, api.calls.size)
    }

    @Test fun enqueueDiscard_after_create_done_fires_DELETE() = runBlocking {
        outbox.enqueueCreate(sampleInvoice("inv-f"))
        api.createBehavior = { _ -> "srv-f" }
        worker.drainOnce()  // CREATE -> done

        outbox.enqueueDiscard("inv-f")
        worker.drainOnce()  // DISCARD row drains

        assertTrue(api.calls.any { it.op == "DELETE" && it.arg == "srv-f" })
    }

    @Test fun enqueueMarkSent_waits_for_create_then_PATCHes() = runBlocking {
        outbox.enqueueCreate(sampleInvoice("inv-g"))
        outbox.enqueueMarkSent("inv-g")
        api.createBehavior = { _ -> "srv-g" }

        worker.drainOnce()

        assertTrue(api.calls.any { it.op == "CREATE" })
        assertTrue(api.calls.any { it.op == "STATUS:sent" && it.arg == "srv-g" })
    }

    @Test fun idempotency_replay_after_response_lost_results_in_one_done() = runBlocking {
        outbox.enqueueCreate(sampleInvoice("inv-h"))
        var failedOnce = false
        api.createBehavior = { _ ->
            if (!failedOnce) { failedOnce = true; throw java.io.IOException("response lost") }
            "srv-h"
        }

        worker.drainOnce()  // first throws
        worker.drainOnce()  // second succeeds

        val row = dao.findById("inv-h")!!
        assertEquals("done", row.status)
        assertEquals("srv-h", row.backendId)
        assertEquals(2, api.calls.count { it.op == "CREATE" })
        assertTrue(api.calls.all { it.op != "CREATE" || it.arg == "inv-h" })
    }

    @Test fun race_discard_during_in_flight_inserts_separate_DISCARD_row() = runBlocking {
        outbox.enqueueCreate(sampleInvoice("inv-i"))
        // Simulate worker mid-POST by directly flipping the row to in_flight.
        dao.markInFlight("inv-i", nowMs = clock.now())

        outbox.enqueueDiscard("inv-i")

        // CREATE row stays in_flight (NOT mutated to cancelled).
        assertEquals("in_flight", dao.findById("inv-i")!!.status)

        // A separate DISCARD row was inserted.
        val discard = dao.findByOpAndLocalId("DISCARD", "inv-i")
        assertNotNull(discard)
        assertEquals("pending", discard!!.status)

        // Complete the "in-flight" CREATE manually (simulating worker finishing).
        dao.markDone("inv-i", backendId = "srv-i", nowMs = clock.now())

        // Drain — the DISCARD now fires DELETE.
        worker.drainOnce()

        assertTrue(api.calls.any { it.op == "DELETE" && it.arg == "srv-i" })
    }

    @Test fun partial_create_failure_does_not_duplicate_line_items_on_retry() = runBlocking {
        // Invoice with 2 line items.
        val inv = sampleInvoice("inv-partial").copy(
            lineItems = listOf(
                InvoiceLineItem("LAB-01", "Labor",     4.0, "hr", 85.0,  340.0, LineItemCategory.LABOR),
                InvoiceLineItem("MAT-01", "Materials", 1.0, "lot", 230.0, 230.0, LineItemCategory.MATERIALS),
            ),
        )
        outbox.enqueueCreate(inv)

        // First drain: CREATE succeeds, addLineItem #1 succeeds, addLineItem #2 throws.
        api.createBehavior = { _ -> "srv-partial" }
        var lineCount = 0
        api.lineItemBehavior = { _, _ ->
            lineCount += 1
            if (lineCount == 2) throw java.io.IOException("HTTP 503")
        }
        worker.drainOnce()

        val rowAfter1 = dao.findById("inv-partial")!!
        assertEquals("pending", rowAfter1.status)  // reverted by transient failure

        // Second drain: CREATE replays (idempotent server-side), then BOTH line items
        // retry — but the first one carries the same clientItemId, so the backend
        // (if it were real) would dedup. The fake records every call we make; we
        // verify the worker invoked addLineItem with stable clientItemIds.
        api.lineItemBehavior = { _, _ -> /* no throw this time */ }
        worker.drainOnce()

        // Each line item was called twice (once on first drain, once on retry).
        // Both calls used the same clientItemId. Verify by inspecting Call.op strings.
        val lineCalls = api.calls.filter { it.op.startsWith("LINE:") }
        assertEquals(4, lineCalls.size)  // 2 items × 2 attempts
        val uniqueIds = lineCalls.map { it.op.removePrefix("LINE:") }.toSet()
        assertEquals(2, uniqueIds.size)  // exactly 2 distinct clientItemIds
        assertTrue(uniqueIds.all { it.startsWith("inv-partial-li-") })

        assertEquals("done", dao.findById("inv-partial")!!.status)
    }

    private fun sampleInvoice(id: String): Invoice = Invoice(
        id = id,
        invoiceNumber = "INV-2026-05-0001",
        issueDate = 1_700_000_000_000L,
        dueDate   = 1_700_000_000_000L,
        mode = InvoiceMode.SOLO,
        fromName = "Jane",
        toName = "Acme",
        jobId = "job-1",
        jobTitle = "Reroof",
        lineItems = listOf(
            InvoiceLineItem(
                code = "LAB-01", description = "Labor",
                quantity = 4.0, unit = "hr", rate = 85.0, total = 340.0,
                category = LineItemCategory.LABOR,
            ),
        ),
    )

    object NoopScheduler : InvoicesOutbox.Scheduler {
        override fun scheduleDrain() { /* tests drive the worker manually */ }
    }

    class AtomicClock(start: Long) : InvoicesOutbox.Clock, InvoicesPushWorker.Clock {
        private var t: Long = start
        override fun now(): Long { t += 1; return t }
    }
}
