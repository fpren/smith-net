package com.guildofsmiths.trademesh.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for messages.
 */
@Dao
interface MessageDao {
    
    /**
     * Insert a message, replacing if exists.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: MessageEntity)
    
    /**
     * Insert multiple messages.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(messages: List<MessageEntity>)
    
    /**
     * Update a message.
     */
    @Update
    suspend fun update(message: MessageEntity)
    
    /**
     * Get all messages for a channel, sorted by timestamp.
     */
    @Query("SELECT * FROM messages WHERE beaconId = :beaconId AND channelId = :channelId ORDER BY timestamp ASC")
    fun getMessagesForChannel(beaconId: String, channelId: String): Flow<List<MessageEntity>>
    
    /**
     * Get all messages for a channel (one-shot).
     */
    @Query("SELECT * FROM messages WHERE beaconId = :beaconId AND channelId = :channelId ORDER BY timestamp ASC")
    suspend fun getMessagesForChannelOnce(beaconId: String, channelId: String): List<MessageEntity>
    
    /**
     * Get a single message by ID.
     */
    @Query("SELECT * FROM messages WHERE id = :messageId")
    suspend fun getMessageById(messageId: String): MessageEntity?
    
    /**
     * Get unsynced messages (for sync to backend).
     */
    @Query("SELECT * FROM messages WHERE isSynced = 0 AND isMeshOrigin = 1 ORDER BY timestamp ASC")
    suspend fun getUnsyncedMessages(): List<MessageEntity>
    
    /**
     * Mark messages as synced.
     */
    @Query("UPDATE messages SET isSynced = 1 WHERE id IN (:messageIds)")
    suspend fun markAsSynced(messageIds: List<String>)
    
    /**
     * Update delivery status.
     */
    @Query("UPDATE messages SET deliveryStatus = :status WHERE id = :messageId")
    suspend fun updateDeliveryStatus(messageId: String, status: Int)
    
    /**
     * Get message count for a channel.
     */
    @Query("SELECT COUNT(*) FROM messages WHERE beaconId = :beaconId AND channelId = :channelId")
    suspend fun getMessageCount(beaconId: String, channelId: String): Int
    
    /**
     * Get total message count.
     */
    @Query("SELECT COUNT(*) FROM messages")
    suspend fun getTotalMessageCount(): Int
    
    /**
     * Delete messages older than timestamp.
     */
    @Query("DELETE FROM messages WHERE timestamp < :timestamp")
    suspend fun deleteOlderThan(timestamp: Long)
    
    /**
     * Delete messages older than timestamp for a specific channel.
     * Used when syncing after reconnect.
     */
    @Query("DELETE FROM messages WHERE channelId = :channelId AND timestamp < :timestamp")
    suspend fun deleteOlderThanForChannel(channelId: String, timestamp: Long)
    
    /**
     * Delete all messages for a channel.
     */
    @Query("DELETE FROM messages WHERE beaconId = :beaconId AND channelId = :channelId")
    suspend fun deleteChannelMessages(beaconId: String, channelId: String)
    
    /**
     * Delete all messages for a channel by channelId only (any beacon).
     * Used when dashboard clears a channel.
     */
    @Query("DELETE FROM messages WHERE channelId = :channelId")
    suspend fun deleteByChannelId(channelId: String)
    
    /**
     * Delete all messages.
     */
    @Query("DELETE FROM messages")
    suspend fun deleteAll()
    
    /**
     * Update archived status for a message.
     */
    @Query("UPDATE messages SET isArchived = :isArchived, archivedAt = :archivedAt, archiveReason = :archiveReason, relatedJobId = :relatedJobId WHERE id = :messageId")
    suspend fun updateArchivedStatus(messageId: String, isArchived: Boolean, archivedAt: Long?, archiveReason: String?, relatedJobId: String?)

    /**
     * Get all archived messages.
     */
    @Query("SELECT * FROM messages WHERE isArchived = 1 ORDER BY archivedAt DESC")
    suspend fun getArchivedMessages(): List<MessageEntity>

    /**
     * Get archived messages for a specific job.
     */
    @Query("SELECT * FROM messages WHERE isArchived = 1 AND relatedJobId = :jobId ORDER BY archivedAt DESC")
    suspend fun getArchivedMessagesForJob(jobId: String): List<MessageEntity>

    /**
     * Delete a single message by ID.
     */
    @Query("DELETE FROM messages WHERE id = :messageId")
    suspend fun deleteById(messageId: String)
    
    /**
     * Check if message exists.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM messages WHERE id = :messageId)")
    suspend fun exists(messageId: String): Boolean
    
    /**
     * Get latest message for each channel (for preview).
     */
    @Query("""
        SELECT * FROM messages m1 
        WHERE timestamp = (
            SELECT MAX(timestamp) FROM messages m2 
            WHERE m2.beaconId = m1.beaconId AND m2.channelId = m1.channelId
        )
        ORDER BY timestamp DESC
    """)
    suspend fun getLatestMessagePerChannel(): List<MessageEntity>
}
