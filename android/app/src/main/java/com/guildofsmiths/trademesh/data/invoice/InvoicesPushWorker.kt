package com.guildofsmiths.trademesh.data.invoice

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.guildofsmiths.trademesh.db.AppDatabase
import com.guildofsmiths.trademesh.db.PendingInvoicePushDao
import com.guildofsmiths.trademesh.db.PendingInvoicePushEntity
import com.guildofsmiths.trademesh.service.HttpClientFactory
import com.guildofsmiths.trademesh.ui.invoice.Invoice
import com.guildofsmiths.trademesh.ui.invoice.InvoiceLineItem
import com.guildofsmiths.trademesh.ui.invoice.InvoiceMode
import com.guildofsmiths.trademesh.ui.invoice.LineItemCategory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Drains the InvoicesOutbox via the DAO. Pop next pending op, atomically
 * flip to in_flight, call the ApiClient, classify the result.
 *
 * The class itself is constructor-injected (DAO + Api + Clock) so unit
 * tests can drive it directly. WorkManager invokes [InvoicesPushWorkerWrapper]
 * which builds the production wiring.
 */
class InvoicesPushWorker(
    private val dao: PendingInvoicePushDao,
    private val api: InvoicesApi,
    private val clock: Clock = SystemClock,
    private val maxAttempts: Int = 20,
) {
    interface Clock { fun now(): Long }
    object SystemClock : Clock { override fun now(): Long = System.currentTimeMillis() }

    /** Drain the queue in one pass. Returns when there is nothing pending.
     *
     * Each row is attempted at most once per call. A transient revertToPending
     * does NOT retry inside the same drain — the next scheduled drain (or test
     * `drainOnce()` call) picks the row back up. This keeps the idempotency
     * replay test deterministic (one CREATE per drain).
     */
    suspend fun drainOnce() {
        val seen = mutableSetOf<String>()
        while (true) {
            val row = dao.nextPending() ?: return
            if (row.id in seen) return  // we've already attempted this row this pass
            seen.add(row.id)
            if (!dao.markInFlight(row.id, clock.now())) continue
            val refreshed = dao.findById(row.id) ?: continue
            executeOne(refreshed)
        }
    }

    private suspend fun executeOne(row: PendingInvoicePushEntity) {
        try {
            when (row.op) {
                "CREATE"    -> executeCreate(row)
                "MARK_SENT" -> executeMarkSent(row)
                "DISCARD"   -> executeDiscard(row)
                else        -> dao.markFailed(row.id, "unknown op ${row.op}", clock.now())
            }
        } catch (e: ApiClientError) {
            dao.markFailed(row.id, "HTTP ${e.httpStatus}: ${e.message}", clock.now())
        } catch (e: Throwable) {
            if (row.attempts + 1 >= maxAttempts) {
                dao.markFailed(row.id, "exhausted $maxAttempts attempts: ${e.message}", clock.now())
            } else {
                dao.revertToPending(row.id, e.message ?: e.javaClass.simpleName, clock.now())
            }
        }
    }

    private suspend fun executeCreate(row: PendingInvoicePushEntity) {
        val invoice = deserializeInvoice(row.payloadJson ?: error("CREATE row missing payload"))
        val backendId = api.createInvoice(invoice)
        invoice.lineItems.forEach { api.addLineItem(backendId, it) }
        dao.markDone(row.id, backendId, clock.now())
    }

    private suspend fun executeMarkSent(row: PendingInvoicePushEntity) {
        val create = dao.findCreateRowFor(row.localInvoiceId)
        when {
            create == null -> dao.markFailed(row.id, "no CREATE row for ${row.localInvoiceId}", clock.now())
            create.status == "failed" || create.status == "cancelled" ->
                dao.markFailed(row.id, "CREATE ${create.status}", clock.now())
            create.backendId == null ->
                // CREATE not yet done — revert this MARK_SENT to pending; next drain retries.
                dao.revertToPending(row.id, "waiting for CREATE", clock.now())
            else -> {
                api.setStatus(create.backendId, "sent")
                dao.markDone(row.id, backendId = null, nowMs = clock.now())
            }
        }
    }

    private suspend fun executeDiscard(row: PendingInvoicePushEntity) {
        val create = dao.findCreateRowFor(row.localInvoiceId)
        when {
            create == null || create.backendId == null ->
                // Nothing exists on the server; done.
                dao.markDone(row.id, backendId = null, nowMs = clock.now())
            else -> {
                api.deleteInvoice(create.backendId)
                dao.markDone(row.id, backendId = null, nowMs = clock.now())
            }
        }
    }

    /**
     * Reconstitute an Invoice from the JSON the mapper wrote to the outbox.
     * For CREATE we need:
     *   - id (= idempotencyKey, so the retry path sends the same key)
     *   - the line items (from summary.fullLineItems)
     * Everything else the mapper re-derives from this minimal carrier when
     * called by createBody — but we do NOT call createBody again; we go
     * straight to the API. The fields below are the ones api.createInvoice
     * and api.addLineItem actually need.
     */
    private fun deserializeInvoice(payloadJson: String): Invoice {
        val root: JsonObject = Json.parseToJsonElement(payloadJson).jsonObject
        val idemKey = root["idempotencyKey"]!!.jsonPrimitive.content
        val summary = root["summary"]!!.jsonObject
        val full    = summary["fullLineItems"]?.jsonArray ?: error("missing summary.fullLineItems")

        val lineItems = full.map { el ->
            val o = el.jsonObject
            InvoiceLineItem(
                code        = o["code"]?.jsonPrimitive?.content ?: "",
                description = o["description"]?.jsonPrimitive?.content ?: "",
                quantity    = o["quantity"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
                unit        = o["unit"]?.jsonPrimitive?.content ?: "ea",
                rate        = o["rate"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
                total       = o["total"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0,
                category    = runCatching {
                    LineItemCategory.valueOf(
                        (o["category"]?.jsonPrimitive?.content ?: "OTHER").uppercase()
                    )
                }.getOrDefault(LineItemCategory.OTHER),
            )
        }

        // Build a minimal Invoice carrier. The id (== idempotencyKey) is the
        // only field the ApiClient.createInvoice path actually re-emits on the wire
        // (because the wire body was already serialized as payloadJson and we
        // just POST that). But ApiClient calls InvoiceJsonMapper.createBody
        // itself, so we need a "real-looking" Invoice with at least id and lineItems.
        return Invoice(
            id = idemKey,
            invoiceNumber = "",
            issueDate = 0L,
            dueDate = 0L,
            mode = InvoiceMode.SOLO,
            fromName = "",
            jobId = "",
            jobTitle = "",
            lineItems = lineItems,
        )
    }
}

/**
 * WorkManager-facing wrapper. Builds the production wiring and delegates
 * to [InvoicesPushWorker.drainOnce]. Unit tests construct InvoicesPushWorker
 * directly and never touch this class.
 */
class InvoicesPushWorkerWrapper(
    ctx: Context,
    params: WorkerParameters,
) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val dao = AppDatabase.getInstance(applicationContext).pendingInvoicePushDao()
        val api = InvoicesApiClient(HttpClientFactory.client)
        val worker = InvoicesPushWorker(dao, api)
        return try {
            worker.drainOnce()
            if (dao.nextPending() != null) Result.retry() else Result.success()
        } catch (e: Throwable) {
            Result.retry()
        }
    }
}
