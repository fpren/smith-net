package com.guildofsmiths.trademesh.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.guildofsmiths.trademesh.data.Message

/**
 * Room entity for persisted messages.
 */
@Entity(
    tableName = "messages",
    indices = [
        Index(value = ["beaconId", "channelId", "timestamp"]),
        Index(value = ["senderId"])
    ]
)
data class MessageEntity(
    @PrimaryKey
    val id: String,
    val beaconId: String,
    val channelId: String,
    val senderId: String,
    val senderName: String,
    val timestamp: Long,
    val content: String,
    val isMeshOrigin: Boolean,
    val isSynced: Boolean = false,
    val deliveryStatus: Int = DeliveryStatus.SENT
) {
    /**
     * Convert to domain Message.
     */
    fun toMessage(): Message {
        return Message(
            id = id,
            beaconId = beaconId,
            channelId = channelId,
            senderId = senderId,
            senderName = senderName,
            timestamp = timestamp,
            content = content,
            isMeshOrigin = isMeshOrigin
        )
    }
    
    companion object {
        /**
         * Create entity from domain Message.
         */
        fun fromMessage(message: Message, isSynced: Boolean = false): MessageEntity {
            return MessageEntity(
                id = message.id,
                beaconId = message.beaconId,
                channelId = message.channelId,
                senderId = message.senderId,
                senderName = message.senderName,
                timestamp = message.timestamp,
                content = message.content,
                isMeshOrigin = message.isMeshOrigin,
                isSynced = isSynced
            )
        }
    }
}

/**
 * Message delivery status constants.
 */
object DeliveryStatus {
    const val PENDING = 0
    const val SENT = 1
    const val DELIVERED = 2
    const val READ = 3
    const val FAILED = -1
}
