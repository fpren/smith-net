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
import com.guildofsmiths.trademesh.service.NotificationHelper

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
    
    /**
     * Initialize with context (call from Application.onCreate).
     */
    fun init(context: Context) {
        appContext = context.applicationContext
        database = AppDatabase.getInstance(context)
        
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
        }
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
        
        // Update channel with last message preview
        BeaconRepository.updateChannelLastMessage(
            beaconId = message.beaconId,
            channelId = message.channelId,
            preview = message.content,
            time = message.timestamp,
            incrementUnread = message.isMeshOrigin
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
