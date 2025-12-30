package com.guildofsmiths.trademesh.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

/**
 * DAO for cord entries - append-only operations only.
 * No updates or deletes allowed - cord is immutable.
 */
@Dao
interface CordDao {

    /**
     * Insert a new cord entry.
     * Fails if messageId already exists (collision resistance).
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCordEntry(entity: CordEntity): Long

    /**
     * Insert multiple cord entries atomically.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCordEntries(entities: List<CordEntity>): List<Long>

    /**
     * Get all cord entries for deterministic ordering.
     * Ordered by Lamport timestamp, then author ID, then author counter.
     */
    @Query("SELECT * FROM cord_entries ORDER BY lamportTimestamp ASC, authorId ASC, authorCounter ASC")
    fun getAllCordEntries(): Flow<List<CordEntity>>

    /**
     * Get cord entries for a specific hub/channel.
     */
    @Query("SELECT * FROM cord_entries WHERE hubId = :hubId AND channelId = :channelId ORDER BY lamportTimestamp ASC, authorId ASC, authorCounter ASC")
    fun getCordEntriesForChannel(hubId: String, channelId: String): Flow<List<CordEntity>>

    /**
     * Get cord entries for a specific cord.
     */
    @Query("SELECT * FROM cord_entries WHERE cordId = :cordId ORDER BY lamportTimestamp ASC, authorId ASC, authorCounter ASC")
    fun getCordEntriesForCord(cordId: String): Flow<List<CordEntity>>

    /**
     * Get cord entries by message class.
     */
    @Query("SELECT * FROM cord_entries WHERE messageClass = :messageClass ORDER BY lamportTimestamp ASC, authorId ASC, authorCounter ASC")
    fun getCordEntriesByClass(messageClass: String): Flow<List<CordEntity>>

    /**
     * Get cord entries by author.
     */
    @Query("SELECT * FROM cord_entries WHERE authorId = :authorId ORDER BY lamportTimestamp ASC, authorId ASC, authorCounter ASC")
    fun getCordEntriesByAuthor(authorId: String): Flow<List<CordEntity>>

    /**
     * Get a specific cord entry by MessageID.
     */
    @Query("SELECT * FROM cord_entries WHERE messageId = :messageId LIMIT 1")
    suspend fun getCordEntryById(messageId: String): CordEntity?

    /**
     * Check if a MessageID exists (for collision detection).
     */
    @Query("SELECT COUNT(*) FROM cord_entries WHERE messageId = :messageId")
    suspend fun cordEntryExists(messageId: String): Int

    /**
     * Update delivery marker for existing cord entry.
     * Delivery markers are telemetry-only and the ONLY mutable aspect of cord entries.
     */
    @Query("UPDATE cord_entries SET deliveryMarker = :deliveryMarker WHERE messageId = :messageId")
    suspend fun updateDeliveryMarker(messageId: String, deliveryMarker: String)

    /**
     * Get the maximum Lamport timestamp seen so far.
     */
    @Query("SELECT MAX(lamportTimestamp) FROM cord_entries")
    suspend fun getMaxLamportTimestamp(): Long?

    /**
     * Get the latest Lamport timestamp for a specific author.
     */
    @Query("SELECT MAX(lamportTimestamp) FROM cord_entries WHERE authorId = :authorId")
    suspend fun getMaxLamportTimestampForAuthor(authorId: String): Long?

    /**
     * Get the latest author counter for a specific author and timestamp.
     */
    @Query("SELECT MAX(authorCounter) FROM cord_entries WHERE authorId = :authorId AND lamportTimestamp = :lamportTimestamp")
    suspend fun getMaxAuthorCounter(authorId: String, lamportTimestamp: Long): Int?

    /**
     * Get cord entries newer than a specific Lamport timestamp (for sync).
     */
    @Query("SELECT * FROM cord_entries WHERE lamportTimestamp > :sinceTimestamp ORDER BY lamportTimestamp ASC, authorId ASC, authorCounter ASC")
    suspend fun getCordEntriesSince(sinceTimestamp: Long): List<CordEntity>

    /**
     * Get cord entries within a Lamport timestamp range (for pagination).
     */
    @Query("SELECT * FROM cord_entries WHERE lamportTimestamp BETWEEN :startTimestamp AND :endTimestamp ORDER BY lamportTimestamp ASC, authorId ASC, authorCounter ASC")
    suspend fun getCordEntriesInRange(startTimestamp: Long, endTimestamp: Long): List<CordEntity>

    /**
     * Get cord entries that need reconciliation (missing from our cord).
     * Used during merge operations.
     */
    @Query("SELECT * FROM cord_entries WHERE messageId NOT IN (:knownMessageIds) ORDER BY lamportTimestamp ASC, authorId ASC, authorCounter ASC")
    suspend fun getUnknownCordEntries(knownMessageIds: List<String>): List<CordEntity>

    /**
     * Get count of cord entries (for diagnostics).
     */
    @Query("SELECT COUNT(*) FROM cord_entries")
    suspend fun getCordEntryCount(): Int

    /**
     * Get cord entries by thread.
     */
    @Query("SELECT * FROM cord_entries WHERE threadId = :threadId ORDER BY lamportTimestamp ASC, authorId ASC, authorCounter ASC")
    fun getCordEntriesForThread(threadId: String): Flow<List<CordEntity>>

    // NOTE: No UPDATE or DELETE operations - cord is append-only and immutable
}
