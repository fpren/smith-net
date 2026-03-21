package com.guildofsmiths.trademesh.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.guildofsmiths.trademesh.data.TransportType
import com.guildofsmiths.trademesh.data.UnifiedMessage
import com.guildofsmiths.trademesh.data.VectorClock

@Entity(tableName = "unified_messages")
data class UnifiedMessageEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "channel_id") val channelId: String,
    @ColumnInfo(name = "sender_id") val senderId: String,
    @ColumnInfo(name = "sender_name") val senderName: String,
    val content: String,
    val timestamp: Long,
    @ColumnInfo(name = "vector_clock") val vectorClockJson: String,
    @ColumnInfo(name = "transport_type") val transportType: String,
    @ColumnInfo(name = "media_type") val mediaType: String = "TEXT",
    @ColumnInfo(name = "media_url") val mediaUrl: String? = null,
    @ColumnInfo(name = "ai_generated") val aiGenerated: Boolean = false,
    @ColumnInfo(name = "ai_model") val aiModel: String? = null,
    @ColumnInfo(name = "synced_to_remote") val syncedToRemote: Boolean = false
) {
    fun toUnifiedMessage(): UnifiedMessage = UnifiedMessage(
        id = id,
        channelId = channelId,
        senderId = senderId,
        senderName = senderName,
        content = content,
        timestamp = timestamp,
        vectorClock = VectorClock.fromJson(vectorClockJson),
        transportType = TransportType.valueOf(transportType),
        mediaType = mediaType,
        mediaUrl = mediaUrl,
        aiGenerated = aiGenerated,
        aiModel = aiModel,
        syncedToRemote = syncedToRemote
    )

    companion object {
        fun from(msg: UnifiedMessage): UnifiedMessageEntity = UnifiedMessageEntity(
            id = msg.id,
            channelId = msg.channelId,
            senderId = msg.senderId,
            senderName = msg.senderName,
            content = msg.content,
            timestamp = msg.timestamp,
            vectorClockJson = msg.vectorClock.toJson(),
            transportType = msg.transportType.name,
            mediaType = msg.mediaType,
            mediaUrl = msg.mediaUrl,
            aiGenerated = msg.aiGenerated,
            aiModel = msg.aiModel,
            syncedToRemote = msg.syncedToRemote
        )
    }
}
