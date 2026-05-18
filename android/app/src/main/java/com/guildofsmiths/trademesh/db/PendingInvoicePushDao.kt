package com.guildofsmiths.trademesh.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface PendingInvoicePushDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(row: PendingInvoicePushEntity)

    @Query("SELECT * FROM pending_invoice_pushes WHERE id = :id")
    suspend fun findById(id: String): PendingInvoicePushEntity?

    @Query("""
        SELECT * FROM pending_invoice_pushes
         WHERE status = 'pending'
         ORDER BY createdAt ASC
         LIMIT 1
    """)
    suspend fun nextPending(): PendingInvoicePushEntity?

    @Query("""
        SELECT * FROM pending_invoice_pushes
         WHERE op = 'CREATE' AND localInvoiceId = :localInvoiceId
         LIMIT 1
    """)
    suspend fun findCreateRowFor(localInvoiceId: String): PendingInvoicePushEntity?

    @Query("""
        UPDATE pending_invoice_pushes
           SET status = 'in_flight', updatedAt = :nowMs
         WHERE id = :id AND status = 'pending'
    """)
    suspend fun markInFlightInternal(id: String, nowMs: Long): Int

    suspend fun markInFlight(id: String, nowMs: Long): Boolean =
        markInFlightInternal(id, nowMs) > 0

    @Query("""
        UPDATE pending_invoice_pushes
           SET status = 'done', backendId = :backendId, updatedAt = :nowMs
         WHERE id = :id
    """)
    suspend fun markDone(id: String, backendId: String?, nowMs: Long)

    @Query("""
        UPDATE pending_invoice_pushes
           SET status = 'failed', lastError = :lastError, updatedAt = :nowMs
         WHERE id = :id
    """)
    suspend fun markFailed(id: String, lastError: String, nowMs: Long)

    @Query("""
        UPDATE pending_invoice_pushes
           SET status = 'pending',
               attempts = attempts + 1,
               lastError = :lastError,
               updatedAt = :nowMs
         WHERE id = :id
    """)
    suspend fun revertToPending(id: String, lastError: String, nowMs: Long)

    @Query("""
        UPDATE pending_invoice_pushes
           SET status = 'cancelled', updatedAt = :nowMs
         WHERE id = :id AND status = 'pending'
    """)
    suspend fun cancelIfPendingInternal(id: String, nowMs: Long): Int

    suspend fun cancelIfPending(id: String, nowMs: Long): Boolean =
        cancelIfPendingInternal(id, nowMs) > 0

    @Query("""
        SELECT * FROM pending_invoice_pushes
         WHERE op = :op AND localInvoiceId = :localInvoiceId
         LIMIT 1
    """)
    suspend fun findByOpAndLocalId(op: String, localInvoiceId: String): PendingInvoicePushEntity?
}
