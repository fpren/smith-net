package com.guildofsmiths.trademesh.data

import android.content.Context
import com.guildofsmiths.trademesh.db.AppDatabase
import com.guildofsmiths.trademesh.db.UnifiedMessageEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

class MessageBusRepository(context: Context) {
    private val dao = AppDatabase.getInstance(context).unifiedMessageDao()
    private val scope = CoroutineScope(Dispatchers.IO)
    private val seenIds = LinkedHashSet<String>(1000)
    private val prefs = context.getSharedPreferences("message_bus", Context.MODE_PRIVATE)
    private val deviceId: String = prefs.getString("device_id", null) ?: run {
        val id = UUID.randomUUID().toString().take(12)
        prefs.edit().putString("device_id", id).apply()
        id
    }
    private var localClock: VectorClock = run {
        val json = prefs.getString("local_clock", null)
        if (json != null) VectorClock.fromJson(json) else VectorClock()
    }

    private val listeners = mutableListOf<(UnifiedMessage) -> Unit>()

    fun addListener(listener: (UnifiedMessage) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (UnifiedMessage) -> Unit) {
        listeners.remove(listener)
    }

    fun getChannelMessages(channelId: String): Flow<List<UnifiedMessage>> {
        return dao.getChannelMessages(channelId).map { entities ->
            entities.map { it.toUnifiedMessage() }
        }
    }

    fun createAndPublish(
        channelId: String,
        senderId: String,
        senderName: String,
        content: String,
        transportType: TransportType,
        mediaType: String = "TEXT",
        mediaUrl: String? = null
    ): UnifiedMessage {
        localClock = localClock.increment(deviceId)
        persistClock()

        val message = UnifiedMessage(
            id = UUID.randomUUID().toString(),
            channelId = channelId,
            senderId = senderId,
            senderName = senderName,
            content = content,
            transportType = transportType,
            vectorClock = localClock,
            mediaType = mediaType,
            mediaUrl = mediaUrl
        )

        publish(message)
        return message
    }

    fun publish(message: UnifiedMessage) {
        if (seenIds.contains(message.id)) return
        seenIds.add(message.id)
        if (seenIds.size > 5000) {
            val iter = seenIds.iterator()
            iter.next()
            iter.remove()
        }

        // Merge incoming clock
        localClock = localClock.merge(message.vectorClock)
        persistClock()

        // Persist locally
        scope.launch {
            dao.insert(UnifiedMessageEntity.from(message))
        }

        // Notify listeners
        for (listener in listeners) {
            listener(message)
        }
    }

    suspend fun getUnsyncedMessages(): List<UnifiedMessage> {
        return dao.getUnsyncedMessages().map { it.toUnifiedMessage() }
    }

    suspend fun markSynced(ids: List<String>) {
        dao.markSynced(ids)
    }

    suspend fun getMessageIds(channelId: String): List<String> {
        return dao.getMessageIds(channelId)
    }

    suspend fun insertRemoteMessages(messages: List<UnifiedMessage>) {
        val newMessages = messages.filter { !seenIds.contains(it.id) }
        val entities = newMessages.map { msg ->
            seenIds.add(msg.id)
            localClock = localClock.merge(msg.vectorClock)
            UnifiedMessageEntity.from(msg.copy(syncedToRemote = true))
        }
        dao.insertAll(entities)
        if (entities.isNotEmpty()) persistClock()

        // Surface in the UI-facing MessageRepository so reconciled messages appear in chats.
        for (msg in newMessages) {
            MessageRepository.addMessage(
                Message(
                    id = msg.id,
                    channelId = msg.channelId,
                    senderId = msg.senderId,
                    senderName = msg.senderName,
                    content = msg.content,
                    timestamp = msg.timestamp,
                    isMeshOrigin = msg.transportType == TransportType.BLE
                )
            )
        }
    }

    fun getLocalClock(): VectorClock = localClock

    private fun persistClock() {
        prefs.edit().putString("local_clock", localClock.toJson()).apply()
    }

    suspend fun clearChannel(channelId: String) {
        dao.clearChannel(channelId)
    }
}
