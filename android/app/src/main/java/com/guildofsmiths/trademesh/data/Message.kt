package com.guildofsmiths.trademesh.data

import java.util.UUID

/**
 * Media type for messages.
 * TEXT: Regular text (mesh + chat)
 * IMAGE: Photo/image (chat only, queued offline)
 * VOICE: Voice note (chat only, queued offline)
 * FILE: File attachment (chat only, queued offline)
 */
enum class MediaType {
    TEXT,
    IMAGE,
    VOICE,
    FILE
}

/**
 * Media attachment metadata.
 */
data class MediaAttachment(
    val type: MediaType,
    val localPath: String? = null,       // Local file path
    val remotePath: String? = null,      // Remote URL (after upload)
    val mimeType: String? = null,
    val fileName: String? = null,
    val fileSize: Long = 0,
    val duration: Long = 0,              // For voice notes (ms)
    val width: Int = 0,                  // For images
    val height: Int = 0,                 // For images
    val isQueued: Boolean = false,       // Waiting for IP to upload
    val uploadProgress: Float = 0f       // 0..1
)

/**
 * Core message data class for the dual-path communication system.
 * Messages can originate from either BLE mesh or IP chat paths.
 */
data class Message(
    val id: String = UUID.randomUUID().toString(),
    val beaconId: String = "default",
    val channelId: String = "general",
    val senderId: String,
    val senderName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val content: String,
    val isMeshOrigin: Boolean = false,
    
    // Media support (chat path only)
    val mediaType: MediaType = MediaType.TEXT,
    val media: MediaAttachment? = null,
    
    // Direct message support
    val recipientId: String? = null,     // null = group message, set = DM
    val recipientName: String? = null
) {
    
    /** Check if this is a direct message */
    fun isDirectMessage(): Boolean = recipientId != null
    
    /** Check if this message has media attachment */
    fun hasMedia(): Boolean = mediaType != MediaType.TEXT && media != null
    
    /** Check if media is queued for upload (offline) */
    fun isMediaQueued(): Boolean = media?.isQueued == true
    
    /**
     * Get placeholder text for media when sent over mesh.
     * Since mesh can't transmit media, we send a text placeholder.
     */
    fun getMeshPlaceholder(): String {
        return when (mediaType) {
            MediaType.TEXT -> content
            MediaType.IMAGE -> "[image queued]"
            MediaType.VOICE -> "[voice ${formatDuration(media?.duration ?: 0)} queued]"
            MediaType.FILE -> "[${media?.fileName ?: "file"} queued]"
        }
    }
    
    private fun formatDuration(ms: Long): String {
        val seconds = (ms / 1000) % 60
        val minutes = (ms / 1000) / 60
        return if (minutes > 0) "${minutes}m${seconds}s" else "${seconds}s"
    }
    
    /**
     * Serialize message to compact byte array for BLE transmission.
     * Format: [id_len(1)][id][sender_id_len(1)][sender_id][sender_name_len(1)][sender_name][timestamp(8)][content]
     */
    fun toMeshPayload(): ByteArray {
        val idBytes = id.toByteArray(Charsets.UTF_8)
        val senderIdBytes = senderId.toByteArray(Charsets.UTF_8)
        val senderNameBytes = senderName.toByteArray(Charsets.UTF_8)
        // For mesh, use placeholder if media message
        val contentToSend = if (mediaType != MediaType.TEXT) getMeshPlaceholder() else content
        val contentBytes = contentToSend.toByteArray(Charsets.UTF_8)
        
        val buffer = ByteArray(1 + idBytes.size + 1 + senderIdBytes.size + 1 + senderNameBytes.size + 8 + contentBytes.size)
        var offset = 0
        
        buffer[offset++] = idBytes.size.toByte()
        System.arraycopy(idBytes, 0, buffer, offset, idBytes.size)
        offset += idBytes.size
        
        buffer[offset++] = senderIdBytes.size.toByte()
        System.arraycopy(senderIdBytes, 0, buffer, offset, senderIdBytes.size)
        offset += senderIdBytes.size
        
        buffer[offset++] = senderNameBytes.size.toByte()
        System.arraycopy(senderNameBytes, 0, buffer, offset, senderNameBytes.size)
        offset += senderNameBytes.size
        
        // Timestamp as 8 bytes (big-endian)
        for (i in 7 downTo 0) {
            buffer[offset++] = ((timestamp shr (i * 8)) and 0xFF).toByte()
        }
        
        System.arraycopy(contentBytes, 0, buffer, offset, contentBytes.size)
        
        return buffer
    }
    
    companion object {
        /** Maximum payload size for mesh messages (BLE constraint) */
        const val MAX_MESH_PAYLOAD_BYTES = 512
        
        /** Check if content fits within mesh payload limits */
        fun fitsInMeshPayload(content: String): Boolean {
            return content.toByteArray(Charsets.UTF_8).size <= MAX_MESH_PAYLOAD_BYTES
        }
        
        /**
         * Deserialize message from BLE mesh payload.
         */
        fun fromMeshPayload(payload: ByteArray): Message? {
            return try {
                var offset = 0
                
                val idLen = payload[offset++].toInt() and 0xFF
                val id = String(payload, offset, idLen, Charsets.UTF_8)
                offset += idLen
                
                val senderIdLen = payload[offset++].toInt() and 0xFF
                val senderId = String(payload, offset, senderIdLen, Charsets.UTF_8)
                offset += senderIdLen
                
                val senderNameLen = payload[offset++].toInt() and 0xFF
                val senderName = String(payload, offset, senderNameLen, Charsets.UTF_8)
                offset += senderNameLen
                
                // Read timestamp (8 bytes big-endian)
                var timestamp = 0L
                for (i in 0 until 8) {
                    timestamp = (timestamp shl 8) or (payload[offset++].toLong() and 0xFF)
                }
                
                val content = String(payload, offset, payload.size - offset, Charsets.UTF_8)
                
                Message(
                    id = id,
                    senderId = senderId,
                    senderName = senderName,
                    timestamp = timestamp,
                    content = content,
                    isMeshOrigin = true
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
