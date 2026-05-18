package com.guildofsmiths.trademesh.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Outbox row for the apk -> /api/invoices push pipeline. One row per
 * pending operation (CREATE / MARK_SENT / DISCARD). The InvoicesPushWorker
 * drains rows in `createdAt` order via the DAO.
 *
 * The status field doubles as a lock: a row marked `in_flight` is being
 * processed by the worker; the outbox's enqueueDiscard uses this state to
 * decide whether to mutate the CREATE row (pending) or insert a new
 * DISCARD row (in_flight or done). See the Android invoice wiring spec
 * for the full race-safety argument.
 */
@Entity(
    tableName = "pending_invoice_pushes",
    indices = [Index(value = ["status", "createdAt"])]
)
data class PendingInvoicePushEntity(
    @PrimaryKey
    val id: String,                 // For CREATE: client UUID (also the idempotency key sent to backend).
                                    // For MARK_SENT / DISCARD: a fresh UUID.
    val localInvoiceId: String,     // The apk Invoice.id this op refers to. Same as id for CREATE.
    val op: String,                 // "CREATE" | "MARK_SENT" | "DISCARD"
    val payloadJson: String?,       // Full 50-field Invoice JSON for CREATE; null otherwise.
    val backendId: String?,         // Server invoice UUID; populated after CREATE succeeds.
    val status: String,             // "pending" | "in_flight" | "done" | "failed" | "cancelled"
    val attempts: Int,
    val lastError: String?,
    val createdAt: Long,            // epoch millis
    val updatedAt: Long,
)
