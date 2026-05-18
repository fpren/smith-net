package com.guildofsmiths.trademesh.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [33])
class PendingInvoicePushDaoTest {

    private lateinit var db: AppDatabase
    private lateinit var dao: PendingInvoicePushDao

    @Before fun setUp() {
        val ctx: Context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.pendingInvoicePushDao()
    }

    @After fun tearDown() { db.close() }

    @Test fun insert_and_findById() = runBlocking {
        val row = PendingInvoicePushEntity(
            id = "id-1", localInvoiceId = "id-1", op = "CREATE",
            payloadJson = """{"x":1}""", backendId = null,
            status = "pending", attempts = 0, lastError = null,
            createdAt = 1000L, updatedAt = 1000L,
        )
        dao.insert(row)
        val got = dao.findById("id-1")
        assertNotNull(got)
        assertEquals("CREATE", got!!.op)
        assertEquals("pending", got.status)
    }

    @Test fun nextPending_returns_oldest_pending() = runBlocking {
        dao.insert(makeRow("a", "CREATE", "pending", createdAt = 200L))
        dao.insert(makeRow("b", "CREATE", "pending", createdAt = 100L))
        dao.insert(makeRow("c", "CREATE", "done",    createdAt = 50L))
        val next = dao.nextPending()
        assertNotNull(next)
        assertEquals("b", next!!.id)
    }

    @Test fun markInFlight_atomic_returns_true_on_pending_false_on_other() = runBlocking {
        dao.insert(makeRow("a", "CREATE", "pending"))
        dao.insert(makeRow("b", "CREATE", "done"))
        assertTrue(dao.markInFlight("a", nowMs = 2000L))
        assertFalse(dao.markInFlight("b", nowMs = 2000L))
        assertFalse(dao.markInFlight("a", nowMs = 2000L))
        val a = dao.findById("a")!!
        assertEquals("in_flight", a.status)
        assertEquals(2000L, a.updatedAt)
    }

    @Test fun markDone_sets_backendId_and_status() = runBlocking {
        dao.insert(makeRow("a", "CREATE", "in_flight"))
        dao.markDone("a", backendId = "srv-123", nowMs = 3000L)
        val a = dao.findById("a")!!
        assertEquals("done",   a.status)
        assertEquals("srv-123", a.backendId)
    }

    @Test fun markFailed_records_lastError_and_status() = runBlocking {
        dao.insert(makeRow("a", "CREATE", "in_flight"))
        dao.markFailed("a", lastError = "422 boom", nowMs = 3000L)
        val a = dao.findById("a")!!
        assertEquals("failed", a.status)
        assertEquals("422 boom", a.lastError)
    }

    @Test fun revertToPending_bumps_attempts() = runBlocking {
        dao.insert(makeRow("a", "CREATE", "in_flight", attempts = 0))
        dao.revertToPending("a", lastError = "transient 500", nowMs = 4000L)
        val a = dao.findById("a")!!
        assertEquals("pending", a.status)
        assertEquals(1, a.attempts)
        assertEquals("transient 500", a.lastError)
    }

    @Test fun cancelIfPending_succeeds_on_pending_noops_on_in_flight() = runBlocking {
        dao.insert(makeRow("pending-row",   "CREATE", "pending"))
        dao.insert(makeRow("in-flight-row", "CREATE", "in_flight"))
        assertTrue(dao.cancelIfPending("pending-row", nowMs = 5000L))
        assertFalse(dao.cancelIfPending("in-flight-row", nowMs = 5000L))
        assertEquals("cancelled", dao.findById("pending-row")!!.status)
        assertEquals("in_flight", dao.findById("in-flight-row")!!.status)
    }

    @Test fun findCreateRowFor_returns_create_for_local_invoice_id() = runBlocking {
        dao.insert(makeRow("c1", "CREATE", "done", localInvoiceId = "inv-x", backendId = "srv-x"))
        dao.insert(makeRow("m1", "MARK_SENT", "pending", localInvoiceId = "inv-x"))
        val create = dao.findCreateRowFor("inv-x")
        assertNotNull(create)
        assertEquals("c1", create!!.id)
    }

    private fun makeRow(
        id: String, op: String, status: String,
        localInvoiceId: String = id, backendId: String? = null,
        attempts: Int = 0, createdAt: Long = 1000L,
    ) = PendingInvoicePushEntity(
        id = id, localInvoiceId = localInvoiceId, op = op,
        payloadJson = null, backendId = backendId, status = status,
        attempts = attempts, lastError = null,
        createdAt = createdAt, updatedAt = createdAt,
    )
}
