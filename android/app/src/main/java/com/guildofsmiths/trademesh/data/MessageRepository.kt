package com.guildofsmiths.trademesh.data

import android.content.Context
import com.guildofsmiths.trademesh.db.AppDatabase
import com.guildofsmiths.trademesh.db.MessageEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.ArrayDeque
import com.guildofsmiths.trademesh.service.ChatManager
import com.guildofsmiths.trademesh.service.NotificationHelper
import com.guildofsmiths.trademesh.db.DeliveryStatus as DbDeliveryStatus

/**
 * Repository for managing messages.
 * Uses Room for persistence and in-memory cache for fast access.
 */
object MessageRepository {
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    /** In-memory cache of all messages */
    private val _allMessages = MutableStateFlow<List<Message>>(emptyList())
    val allMessages: StateFlow<List<Message>> = _allMessages.asStateFlow()
    
    /** Queue of mesh messages pending sync to chat backend */
    private val pendingSyncQueue = ArrayDeque<Message>()
    
    /** Set of message IDs to prevent duplicates */
    private val seenMessageIds = mutableSetOf<String>()
    
    /** Database instance */
    private var database: AppDatabase? = null

    /** Application context for notifications */
    private var appContext: Context? = null

    /** messageId -> set of userIds who have read it (self excluded at write time). */
    private val _readByMessage = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val readByMessage: StateFlow<Map<String, Set<String>>> = _readByMessage.asStateFlow()

    /** Guards against wiring the ChatManager read-receipt listener more than once. */
    private var receiptsWired = false

    /**
     * Initialize with context (call from Application.onCreate).
     */
    fun init(context: Context) {
        appContext = context.applicationContext
        database = AppDatabase.getInstance(context)
        wireReadReceipts()

        // Load existing messages from database
        scope.launch {
            val dao = database?.messageDao() ?: return@launch
            val entities = dao.getLatestMessagePerChannel()
            // Load recent messages from each channel
            val allChannels = entities.map { it.beaconId to it.channelId }.distinct()
            val messages = mutableListOf<Message>()
            for ((beaconId, channelId) in allChannels) {
                val channelMessages = dao.getMessagesForChannelOnce(beaconId, channelId)
                messages.addAll(channelMessages.map { it.toMessage() })
            }
            _allMessages.value = messages.sortedBy { it.timestamp }
            seenMessageIds.addAll(messages.map { it.id })

            // Seed mock roofing conversation if DB is empty (demo builds only)
            if (messages.isEmpty() && BuildFlags.SEED_DEMO_DATA) {
                seedMockRoofingConversation()
            }
        }
    }

    /**
     * Register this repository as the single ChatManager.OnReadReceiptListener,
     * so incoming "message_read" WS frames accumulate into [readByMessage].
     * Called once from [init], which TradeMeshApplication.onCreate calls exactly
     * once at process start — that's the seam that guarantees single registration
     * in production without needing ChatManager to expose its own init lifecycle
     * (it's a lazily-touched object with no single "start" call site of its own).
     */
    private fun wireReadReceipts() {
        if (receiptsWired) return
        receiptsWired = true
        ChatManager.setReadReceiptListener(object : ChatManager.OnReadReceiptListener {
            override fun onMessageRead(messageId: String, readBy: String, readAt: Long) {
                markReadLocal(messageId, readBy)
            }
        })
    }

    /**
     * Record that [userId] has read [messageId]. Self-reads are excluded here
     * (at write time) so consumers of [readByMessage] never need to filter
     * their own id back out. Duplicate (messageId, userId) pairs are no-ops —
     * the underlying map is structurally unchanged, so the StateFlow does not
     * re-emit.
     */
    @Synchronized
    fun markReadLocal(messageId: String, userId: String) {
        if (userId.isBlank() || userId == UserPreferences.getUserId()) return
        _readByMessage.update { current ->
            val existing = current[messageId] ?: emptySet()
            if (userId in existing) current else current + (messageId to (existing + userId))
        }
    }

    /**
     * Seed a mock roofing conversation for demo purposes.
     * Two coworkers: Marcus (lead roofer/Samsung) and Jay (crew/Emulator).
     */
    private fun seedMockRoofingConversation() {
        val now = System.currentTimeMillis()
        val min = 60_000L

        // Conversation from earlier today — spacing messages ~1-3 min apart
        val base = now - (45 * min)

        val convo = listOf(
            Triple("marcus-0002", "Marcus",
                "Yo you good for the Henderson job tomorrow?"),
            Triple("jay-0001", "Jay",
                "Yeah I'm in. What time we meeting?"),
            Triple("marcus-0002", "Marcus",
                "Be at the shop by 6:30. Dumpster's already on site, I dropped it off today"),
            Triple("jay-0001", "Jay",
                "Cool. What are we looking at"),
            Triple("marcus-0002", "Marcus",
                "Full tear-off. 15 squares, 3-tab. South side is cooked. Got 2 sheets of decking to replace by the chimney too"),
            Triple("jay-0001", "Jay",
                "Architectural going back on?"),
            Triple("marcus-0002", "Marcus",
                "Yeah OC Duration, Driftwood. Material's already loaded on the trailer. Just need the plywood and ice & water shield"),
            Triple("jay-0001", "Jay",
                "I can grab the plywood from Home Depot on my way in. How many sheets"),
            Triple("marcus-0002", "Marcus",
                "Get 3 just in case. 1/2 inch CDX. And grab a roll of flashing tape we're almost out"),
            Triple("jay-0001", "Jay",
                "Got it. We doing the chimney flashing too or just the field?"),
            Triple("marcus-0002", "Marcus",
                "Whole thing. Step flashing is shot. I already bent the counter flashing pieces in the shop"),
            Triple("jay-0001", "Jay",
                "Nice. Is it just us two or is Ricky coming"),
            Triple("marcus-0002", "Marcus",
                "Ricky and the new kid Alex. 4 man crew we should knock tear-off out by lunch if we hustle"),
            Triple("jay-0001", "Jay",
                "Bet. I'll bring the nail gun too mine pulls cleaner than the shop ones"),
            Triple("marcus-0002", "Marcus",
                "Good call. Alright see you at 6:30. Don't be late bro we got a 2 day window before rain hits Thursday"),
            Triple("jay-0001", "Jay",
                "I'm never late lol. See you tomorrow")
        )

        convo.forEachIndexed { index, (senderId, senderName, content) ->
            val msg = Message(
                id = "mock-roof-${index.toString().padStart(3, '0')}",
                beaconId = "default",
                channelId = "general",
                senderId = senderId,
                senderName = senderName,
                content = content,
                timestamp = base + (index * 2 * min) + (index * 15_000L), // ~2min spacing
                isMeshOrigin = false
            )
            addMessage(msg)
        }

        android.util.Log.i("MessageRepository", "Seeded mock roofing conversation (${convo.size} messages)")
    }
    
    /**
     * Get messages flow filtered by beacon and channel.
     */
    fun messagesFlow(beaconId: String, channelId: String): Flow<List<Message>> {
        return _allMessages.map { messages ->
            messages.filter { it.beaconId == beaconId && it.channelId == channelId }
                .sortedBy { it.timestamp }
        }
    }
    
    /**
     * Get messages flow from database (persistent).
     */
    fun messagesFlowFromDb(beaconId: String, channelId: String): Flow<List<Message>>? {
        return database?.messageDao()?.getMessagesForChannel(beaconId, channelId)
            ?.map { entities -> entities.map { it.toMessage() } }
    }
    
    /**
     * Add a message to the repository.
     * Automatically deduplicates by message ID.
     * Messages are sorted by timestamp (chronological order).
     */
    @Synchronized
    fun addMessage(message: Message) {
        if (seenMessageIds.contains(message.id)) {
            return // Duplicate, ignore
        }
        seenMessageIds.add(message.id)
        
        // Add to in-memory cache
        _allMessages.update { current ->
            (current + message).sortedBy { it.timestamp }
        }
        
        // Persist to database
        scope.launch {
            database?.messageDao()?.insert(MessageEntity.fromMessage(message))
        }
        
        // Update channel with last message preview. Attachment-only messages
        // have content == "" -- fall back to the `[type]` media label so the
        // channel list never shows a blank preview.
        BeaconRepository.updateChannelLastMessage(
            beaconId = message.beaconId,
            channelId = message.channelId,
            preview = message.content.ifEmpty { message.mediaPreviewLabel() },
            time = message.timestamp,
            incrementUnread = message.isMeshOrigin,
            outgoing = message.senderId == UserPreferences.getUserId()
        )
        
        // If mesh origin, queue for later sync
        if (message.isMeshOrigin) {
            pendingSyncQueue.addLast(message)
        }
        
        // Show notification for incoming messages (not from self)
        val myUserId = UserPreferences.getUserId()
        if (message.senderId != myUserId) {
            appContext?.let { context ->
                NotificationHelper.showMessageNotification(context, message)
            }
        }
    }
    
    /**
     * Update the delivery status of a message (PENDING/SENT/FAILED/etc).
     * Updates the in-memory _allMessages entry immediately, and persists
     * to the DAO (currently a caller-less no-op) with the int mapping.
     * Unknown ids are a no-op.
     */
    @Synchronized
    fun updateDeliveryStatus(messageId: String, status: DeliveryStatus) {
        _allMessages.update { messages ->
            messages.map { message ->
                if (message.id == messageId) message.copy(deliveryStatus = status) else message
            }
        }

        scope.launch {
            database?.messageDao()?.updateDeliveryStatus(messageId, status.toDbInt())
        }
    }

    private fun DeliveryStatus.toDbInt(): Int = when (this) {
        DeliveryStatus.PENDING -> DbDeliveryStatus.PENDING
        DeliveryStatus.SENT -> DbDeliveryStatus.SENT
        DeliveryStatus.DELIVERED -> DbDeliveryStatus.DELIVERED
        DeliveryStatus.READ -> DbDeliveryStatus.READ
        DeliveryStatus.FAILED -> DbDeliveryStatus.FAILED
    }

    /**
     * Get all messages (for debugging).
     */
    fun getAllMessages(): List<Message> = _allMessages.value
    
    /**
     * Get messages for the currently active channel.
     */
    fun getActiveChannelMessages(): List<Message> {
        val beacon = BeaconRepository.getActiveBeacon() ?: return emptyList()
        val channel = BeaconRepository.getActiveChannel() ?: return emptyList()
        return _allMessages.value
            .filter { it.beaconId == beacon.id && it.channelId == channel.id }
            .sortedBy { it.timestamp }
    }
    
    /**
     * Get all messages pending sync to the chat backend.
     */
    @Synchronized
    fun getPendingSyncMessages(): List<Message> {
        return pendingSyncQueue.toList()
    }
    
    /**
     * Mark messages as synced (remove from pending queue).
     */
    @Synchronized
    fun markAsSynced(messageIds: Set<String>) {
        pendingSyncQueue.removeAll { it.id in messageIds }
        
        // Update in database
        scope.launch {
            database?.messageDao()?.markAsSynced(messageIds.toList())
        }
    }
    
    /**
     * Clear all messages (for testing/reset).
     */
    @Synchronized
    fun clear() {
        _allMessages.value = emptyList()
        seenMessageIds.clear()
        pendingSyncQueue.clear()
        _readByMessage.value = emptyMap()

        scope.launch {
            database?.messageDao()?.deleteAll()
        }
    }
    
    /**
     * Clear messages for a specific channel.
     */
    @Synchronized
    fun clearChannel(beaconId: String, channelId: String) {
        _allMessages.update { messages ->
            messages.filter { !(it.beaconId == beaconId && it.channelId == channelId) }
        }
        
        scope.launch {
            database?.messageDao()?.deleteChannelMessages(beaconId, channelId)
        }
    }
    
    /**
     * Clear messages for a channel by channelId only (matches any beacon).
     * Used when dashboard clears a channel.
     */
    @Synchronized
    fun clearChannel(channelId: String) {
        android.util.Log.d("MessageRepository", "🗑️ Clearing all messages for channel: $channelId")
        val beforeCount = _allMessages.value.size
        
        _allMessages.update { messages ->
            messages.filter { it.channelId != channelId }
        }
        
        val afterCount = _allMessages.value.size
        android.util.Log.d("MessageRepository", "🗑️ Cleared ${beforeCount - afterCount} messages")
        
        // Also clear seen IDs for this channel so messages can be re-received
        seenMessageIds.removeAll { id ->
            _allMessages.value.none { it.id == id }
        }
        
        scope.launch {
            database?.messageDao()?.deleteByChannelId(channelId)
        }
    }
    
    /**
     * Clear messages older than a timestamp for a channel.
     * Used when syncing after reconnect - clears messages that were cleared while offline.
     */
    @Synchronized
    fun clearMessagesOlderThan(channelId: String, timestamp: Long) {
        android.util.Log.d("MessageRepository", "🗑️ Clearing messages older than $timestamp for channel: $channelId")
        val beforeCount = _allMessages.value.size
        
        _allMessages.update { messages ->
            messages.filter { !(it.channelId == channelId && it.timestamp < timestamp) }
        }
        
        val afterCount = _allMessages.value.size
        android.util.Log.d("MessageRepository", "🗑️ Cleared ${beforeCount - afterCount} old messages")
        
        // Update seen IDs
        seenMessageIds.removeAll { id ->
            _allMessages.value.none { it.id == id }
        }
        
        scope.launch {
            database?.messageDao()?.deleteOlderThanForChannel(channelId, timestamp)
        }
    }
    
    /**
     * Remove a single message by ID (swipe to delete).
     */
    @Synchronized
    fun removeMessage(messageId: String) {
        _allMessages.update { messages ->
            messages.filter { it.id != messageId }
        }
        seenMessageIds.remove(messageId)
        pendingSyncQueue.removeAll { it.id == messageId }
        
        scope.launch {
            database?.messageDao()?.deleteById(messageId)
        }
    }
    
    /**
     * Archive a message (hide from view but keep in storage).
     * The message is removed from the active list but can be retrieved later.
     */
    @Synchronized
    fun archiveMessage(messageId: String) {
        // For now, archive just removes from active view
        // In a full implementation, you'd mark it as archived in the database
        _allMessages.update { messages ->
            messages.filter { it.id != messageId }
        }
        
        scope.launch {
            // Mark as archived in DB (would need schema update for full implementation)
            // For now, we just hide it from the active list
            android.util.Log.d("MessageRepository", "📦 Archived message: $messageId")
        }
    }
    
    /**
     * Get message count for debugging.
     */
    fun getMessageCount(): Int = _allMessages.value.size
    
    /**
     * Get message count for a specific channel.
     */
    fun getChannelMessageCount(beaconId: String, channelId: String): Int {
        return _allMessages.value.count { it.beaconId == beaconId && it.channelId == channelId }
    }
    
    /**
     * Get pending sync count for debugging.
     */
    fun getPendingSyncCount(): Int = pendingSyncQueue.size
    
    /**
     * Archive a message.
     */
    fun archiveMessage(messageId: String, reason: String? = null, relatedJobId: String? = null) {
        _allMessages.update { messages ->
            messages.map { message ->
                if (message.id == messageId) {
                    message.copy(
                        isArchived = true,
                        archivedAt = System.currentTimeMillis(),
                        archiveReason = reason,
                        relatedJobId = relatedJobId
                    )
                } else message
            }
        }

        scope.launch {
            database?.messageDao()?.updateArchivedStatus(messageId, true, System.currentTimeMillis(), reason, relatedJobId)
        }
    }

    /**
     * Unarchive a message (restore to active).
     */
    fun unarchiveMessage(messageId: String) {
        _allMessages.update { messages ->
            messages.map { message ->
                if (message.id == messageId) {
                    message.copy(
                        isArchived = false,
                        archivedAt = null,
                        archiveReason = null,
                        relatedJobId = null
                    )
                } else message
            }
        }

        scope.launch {
            database?.messageDao()?.updateArchivedStatus(messageId, false, null, null, null)
        }
    }

    /**
     * Get all archived messages.
     */
    fun getArchivedMessages(): List<Message> {
        return _allMessages.value.filter { it.isArchived }
    }

    /**
     * Get archived messages for a specific job.
     */
    fun getArchivedMessagesForJob(jobId: String): List<Message> {
        return _allMessages.value.filter { it.isArchived && it.relatedJobId == jobId }
    }

    /**
     * Permanently delete an archived message.
     */
    fun deleteArchivedMessage(messageId: String) {
        _allMessages.update { messages ->
            messages.filter { it.id != messageId }
        }

        scope.launch {
            database?.messageDao()?.deleteById(messageId)
        }
    }

    /**
     * Clean up old messages (keep last 7 days).
     */
    fun pruneOldMessages() {
        val cutoff = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
        
        _allMessages.update { messages ->
            messages.filter { it.timestamp >= cutoff }
        }
        
        scope.launch {
            database?.messageDao()?.deleteOlderThan(cutoff)
        }
    }
}
