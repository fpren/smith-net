package com.guildofsmiths.trademesh.data

import java.util.UUID

enum class TransportType {
    BLE, IP, SUPABASE, GATEWAY
}

data class UnifiedMessage(
    val id: String = UUID.randomUUID().toString(),
    val channelId: String,
    val senderId: String,
    val senderName: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val vectorClock: VectorClock = VectorClock(),
    val transportType: TransportType,
    val mediaType: String = "TEXT",
    val mediaUrl: String? = null,
    val aiGenerated: Boolean = false,
    val aiModel: String? = null,
    val syncedToRemote: Boolean = false
) {
    fun toBlePayload(senderHash: ByteArray, channelHash: ByteArray): ByteArray? {
        if (mediaType != "TEXT") return null
        val contentBytes = content.toByteArray(Charsets.UTF_8)
        if (contentBytes.size > 10) return null

        val payload = ByteArray(20)
        System.arraycopy(senderHash, 0, payload, 0, 4)
        System.arraycopy(channelHash, 0, payload, 4, 2)
        val ts = (timestamp / 1000).toInt()
        payload[6] = (ts shr 24).toByte()
        payload[7] = (ts shr 16).toByte()
        payload[8] = (ts shr 8).toByte()
        payload[9] = ts.toByte()
        System.arraycopy(contentBytes, 0, payload, 10, contentBytes.size)
        return payload
    }
}
