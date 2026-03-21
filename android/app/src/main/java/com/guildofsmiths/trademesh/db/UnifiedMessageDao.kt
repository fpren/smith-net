package com.guildofsmiths.trademesh.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UnifiedMessageDao {
    @Query("SELECT * FROM unified_messages WHERE channel_id = :channelId ORDER BY timestamp ASC")
    fun getChannelMessages(channelId: String): Flow<List<UnifiedMessageEntity>>

    @Query("SELECT * FROM unified_messages WHERE channel_id = :channelId ORDER BY timestamp ASC")
    suspend fun getChannelMessagesList(channelId: String): List<UnifiedMessageEntity>

    @Query("SELECT id FROM unified_messages WHERE channel_id = :channelId")
    suspend fun getMessageIds(channelId: String): List<String>

    @Query("SELECT * FROM unified_messages WHERE synced_to_remote = 0")
    suspend fun getUnsyncedMessages(): List<UnifiedMessageEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: UnifiedMessageEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(messages: List<UnifiedMessageEntity>)

    @Query("UPDATE unified_messages SET synced_to_remote = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("DELETE FROM unified_messages WHERE channel_id = :channelId AND timestamp < :before")
    suspend fun deleteOlderThan(channelId: String, before: Long)

    @Query("DELETE FROM unified_messages WHERE channel_id = :channelId")
    suspend fun clearChannel(channelId: String)
}
