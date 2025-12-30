package com.guildofsmiths.trademesh.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.guildofsmiths.trademesh.data.CordEntry
import com.guildofsmiths.trademesh.data.CordMessageClass

/**
 * Room entity for cord entries - append-only, immutable storage.
 * Uses WAL mode and content-addressed primary key for collision resistance.
 */
@Entity(
    tableName = "cord_entries",
    indices = [
        // Deterministic ordering index
        Index(value = ["lamportTimestamp", "authorId", "authorCounter"]),
        // Lookup indices
        Index(value = ["cordId"]),
        Index(value = ["hubId", "channelId"]),
        Index(value = ["authorId"]),
        Index(value = ["messageClass"]),
        Index(value = ["committedAt"])
    ]
)
data class CordEntity(
    @PrimaryKey
    val messageId: String,        // Content-addressed ID (collision-proof)

    // Cord structure
    val cordId: String,
    val hubId: String,
    val channelId: String,
    val threadId: String?,

    // Author
    val authorId: String,
    val authorName: String,

    // Content
    val messageClass: String,     // com.guildofsmiths.trademesh.data.CordEntry.MessageClass.name
    val payload: String,
    val payloadType: String,

    // Ordering (Lamport clock)
    val lamportTimestamp: Long,
    val authorCounter: Int,

    // Metadata
    val wallClockTime: Long,
    val signature: String,
    val deliveryMarker: String?,

    // Commit timestamp
    val committedAt: Long
) {
    /**
     * Convert to domain CordEntry.
     */
    fun toCordEntry(): CordEntry {
        return CordEntry(
            messageId = messageId,
            cordId = cordId,
            hubId = hubId,
            channelId = channelId,
            threadId = threadId,
            authorId = authorId,
            authorName = authorName,
            messageClass = CordMessageClass.valueOf(messageClass),
            payload = payload,
            payloadType = payloadType,
            lamportTimestamp = lamportTimestamp,
            authorCounter = authorCounter,
            wallClockTime = wallClockTime,
            signature = signature,
            deliveryMarker = deliveryMarker,
            committedAt = committedAt
        )
    }

    companion object {
        /**
         * Create entity from domain CordEntry.
         */
        fun fromCordEntry(entry: CordEntry): CordEntity {
            return CordEntity(
                messageId = entry.messageId,
                cordId = entry.cordId,
                hubId = entry.hubId,
                channelId = entry.channelId,
                threadId = entry.threadId,
                authorId = entry.authorId,
                authorName = entry.authorName,
                messageClass = entry.messageClass.name,
                payload = entry.payload,
                payloadType = entry.payloadType,
                lamportTimestamp = entry.lamportTimestamp,
                authorCounter = entry.authorCounter,
                wallClockTime = entry.wallClockTime,
                signature = entry.signature,
                deliveryMarker = entry.deliveryMarker,
                committedAt = entry.committedAt
            )
        }
    }
}
