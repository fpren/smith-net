package com.guildofsmiths.trademesh.data.invoice

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.guildofsmiths.trademesh.db.PendingInvoicePushDao
import com.guildofsmiths.trademesh.db.PendingInvoicePushEntity
import com.guildofsmiths.trademesh.ui.invoice.Invoice
import java.util.UUID

/**
 * The only entry point for code that wants something pushed to /api/invoices.
 * Translates UX events (Generate / Share / Cancel) into outbox rows; defers
 * the actual network work to InvoicesPushWorker via the Scheduler hook.
 *
 * Race-safety: enqueueDiscard checks the CREATE row's status under the
 * DAO's atomic UPDATE-WHERE-pending. If the CREATE is already in_flight or
 * done, a separate DISCARD row is inserted so the worker can fire DELETE
 * after the in-flight POST completes.
 */
class InvoicesOutbox(
    private val dao: PendingInvoicePushDao,
    private val scheduler: Scheduler,
    private val clock: Clock = SystemClock,
) {
    interface Scheduler {
        fun scheduleDrain()
    }
    interface Clock {
        fun now(): Long
    }
    object SystemClock : Clock {
        override fun now(): Long = System.currentTimeMillis()
    }

    suspend fun enqueueCreate(invoice: Invoice) {
        val now = clock.now()
        dao.insert(PendingInvoicePushEntity(
            id = invoice.id,
            localInvoiceId = invoice.id,
            op = "CREATE",
            payloadJson = InvoiceJsonMapper.createBody(invoice),
            backendId = null,
            status = "pending",
            attempts = 0,
            lastError = null,
            createdAt = now,
            updatedAt = now,
        ))
        scheduler.scheduleDrain()
    }

    suspend fun enqueueMarkSent(localInvoiceId: String) {
        val now = clock.now()
        dao.insert(PendingInvoicePushEntity(
            id = UUID.randomUUID().toString(),
            localInvoiceId = localInvoiceId,
            op = "MARK_SENT",
            payloadJson = null,
            backendId = null,
            status = "pending",
            attempts = 0,
            lastError = null,
            createdAt = now,
            updatedAt = now,
        ))
        scheduler.scheduleDrain()
    }

    suspend fun enqueueDiscard(localInvoiceId: String) {
        val createRow = dao.findCreateRowFor(localInvoiceId) ?: return
        val now = clock.now()
        when (createRow.status) {
            "pending" -> {
                // Atomic pending -> cancelled. If the worker grabbed it just now,
                // cancelIfPending returns false and we fall through to insert a DISCARD row.
                if (dao.cancelIfPending(createRow.id, now)) return
                insertDiscardRow(localInvoiceId, now)
            }
            "in_flight", "done" -> insertDiscardRow(localInvoiceId, now)
            "failed", "cancelled" -> { /* nothing to do server-side */ }
        }
    }

    private suspend fun insertDiscardRow(localInvoiceId: String, now: Long) {
        dao.insert(PendingInvoicePushEntity(
            id = UUID.randomUUID().toString(),
            localInvoiceId = localInvoiceId,
            op = "DISCARD",
            payloadJson = null,
            backendId = null,
            status = "pending",
            attempts = 0,
            lastError = null,
            createdAt = now,
            updatedAt = now,
        ))
        scheduler.scheduleDrain()
    }

    /** Real production scheduler: enqueues a WorkManager run with network constraint. */
    class WorkManagerScheduler(private val ctx: Context) : Scheduler {
        override fun scheduleDrain() {
            val req = OneTimeWorkRequestBuilder<InvoicesPushWorkerWrapper>()
                .setConstraints(Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build())
                .build()
            WorkManager.getInstance(ctx)
                .enqueueUniqueWork("invoices-push", ExistingWorkPolicy.KEEP, req)
        }
    }
}
