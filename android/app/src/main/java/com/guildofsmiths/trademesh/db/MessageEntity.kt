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
        Index(value = ["senderId"]),
        Index(value = ["isArchived"])
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

    // Media support
    val mediaType: String = "TEXT",
    val mediaLocalPath: String? = null,
    val mediaRemotePath: String? = null,
    val mediaMimeType: String? = null,
    val mediaFileName: String? = null,
    val mediaFileSize: Long = 0,
    val mediaDuration: Long = 0,
    val mediaWidth: Int = 0,
    val mediaHeight: Int = 0,
    val isMediaQueued: Boolean = false,
    val uploadProgress: Float = 0f,

    // DM support
    val recipientId: String? = null,
    val recipientName: String? = null,

    // AI support
    val aiGenerated: Boolean = false,
    val aiModel: String? = null,
    val aiSource: String? = null,
    val aiContext: String? = null,
    val aiPrompt: String? = null,
    val syncedToCloud: Boolean = false,

    // Archive support
    val isArchived: Boolean = false,
    val archivedAt: Long? = null,
    val archiveReason: String? = null,
    val relatedJobId: String? = null,

    // Sync status
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
            isMeshOrigin = isMeshOrigin,
            mediaType = try { com.guildofsmiths.trademesh.data.MediaType.valueOf(mediaType) } catch (e: Exception) { com.guildofsmiths.trademesh.data.MediaType.TEXT },
            media = if (mediaLocalPath != null || mediaRemotePath != null) {
                com.guildofsmiths.trademesh.data.MediaAttachment(
                    type = try { com.guildofsmiths.trademesh.data.MediaType.valueOf(mediaType) } catch (e: Exception) { com.guildofsmiths.trademesh.data.MediaType.TEXT },
                    localPath = mediaLocalPath,
                    remotePath = mediaRemotePath,
                    mimeType = mediaMimeType,
                    fileName = mediaFileName,
                    fileSize = mediaFileSize,
                    duration = mediaDuration,
                    width = mediaWidth,
                    height = mediaHeight,
                    isQueued = isMediaQueued,
                    uploadProgress = uploadProgress
                )
            } else null,
            recipientId = recipientId,
            recipientName = recipientName,
            aiGenerated = aiGenerated,
            aiModel = aiModel,
            aiSource = aiSource,
            aiContext = aiContext,
            aiPrompt = aiPrompt,
            syncedToCloud = syncedToCloud,
            isArchived = isArchived,
            archivedAt = archivedAt,
            archiveReason = archiveReason,
            relatedJobId = relatedJobId
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
                mediaType = message.mediaType.name,
                mediaLocalPath = message.media?.localPath,
                mediaRemotePath = message.media?.remotePath,
                mediaMimeType = message.media?.mimeType,
                mediaFileName = message.media?.fileName,
                mediaFileSize = message.media?.fileSize ?: 0,
                mediaDuration = message.media?.duration ?: 0,
                mediaWidth = message.media?.width ?: 0,
                mediaHeight = message.media?.height ?: 0,
                isMediaQueued = message.media?.isQueued ?: false,
                uploadProgress = message.media?.uploadProgress ?: 0f,
                recipientId = message.recipientId,
                recipientName = message.recipientName,
                aiGenerated = message.aiGenerated,
                aiModel = message.aiModel,
                aiSource = message.aiSource,
                aiContext = message.aiContext,
                aiPrompt = message.aiPrompt,
                syncedToCloud = message.syncedToCloud,
                isArchived = message.isArchived,
                archivedAt = message.archivedAt,
                archiveReason = message.archiveReason,
                relatedJobId = message.relatedJobId,
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
