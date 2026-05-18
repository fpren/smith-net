// android/app/src/androidTest/java/com/guildofsmiths/trademesh/data/invoice/InvoicesPushE2ETest.kt
package com.guildofsmiths.trademesh.data.invoice

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.guildofsmiths.trademesh.db.AppDatabase
import com.guildofsmiths.trademesh.service.HttpClientFactory
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
import java.util.UUID

/**
 * End-to-end smoke against a real backend reachable at BuildConfig.BACKEND_URL.
 * Drives outbox + worker through a generate -> share roundtrip. Skips network
 * verification beyond "no exception thrown + outbox row went to done".
 *
 * Requires: emulator/device + backend running + an authenticated session
 * (HttpClientFactory.client must have a valid auth cookie/token, same as
 * the apk's normal usage).
 */
@RunWith(AndroidJUnit4::class)
class InvoicesPushE2ETest {

    private lateinit var db: AppDatabase
    private lateinit var outbox: InvoicesOutbox
    private lateinit var worker: InvoicesPushWorker

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        // In-memory db so we don't pollute the real one.
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).build()
        val dao = db.pendingInvoicePushDao()
        outbox = InvoicesOutbox(dao, object : InvoicesOutbox.Scheduler {
            override fun scheduleDrain() { /* tests drive the worker manually */ }
        })
        worker = InvoicesPushWorker(dao, InvoicesApiClient(HttpClientFactory.client))
    }

    @After fun tearDown() { db.close() }

    @Test fun generate_share_e2e() = runBlocking {
        val invoice = Invoice(
            id = UUID.randomUUID().toString(),
            invoiceNumber = "INV-2026-05-E2E",
            issueDate = System.currentTimeMillis(),
            dueDate = System.currentTimeMillis() + 14L * 24 * 3600 * 1000,
            mode = InvoiceMode.SOLO,
            fromName = "Jane E2E",
            toName = "Acme E2E",
            toEmail = "ops@acme-e2e.com",
            jobId = "job-e2e",
            jobTitle = "E2E Reroof",
            lineItems = listOf(
                InvoiceLineItem(
                    code = "LAB-01", description = "Labor",
                    quantity = 4.0, unit = "hr", rate = 85.0, total = 340.0,
                    category = LineItemCategory.LABOR,
                ),
            ),
        )

        outbox.enqueueCreate(invoice)
        worker.drainOnce()

        val createRow = db.pendingInvoicePushDao().findById(invoice.id)
        assertEquals("done", createRow?.status)
        assertNotNull(createRow?.backendId)

        outbox.enqueueMarkSent(invoice.id)
        worker.drainOnce()

        val pending = db.pendingInvoicePushDao().nextPending()
        assertNull("no pending ops left after generate+share", pending)
    }
}
