package com.guildofsmiths.trademesh.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * DAO for keyword observations.
 * 
 * APPEND-ONLY operations:
 * - insert() - Add new observation
 * - Queries for analysis (read-only)
 * 
 * NO UPDATE OR DELETE OPERATIONS - This is intentional.
 * This table is for observation/analysis only.
 */
@Dao
interface KeywordObservationDao {
    
    // ════════════════════════════════════════════════════════════════════
    // INSERT ONLY (append-only semantics)
    // ════════════════════════════════════════════════════════════════════
    
    /**
     * Insert a single observation.
     * Uses IGNORE strategy to prevent duplicates within same millisecond.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(observation: KeywordObservationEntity): Long
    
    /**
     * Insert multiple observations in batch.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(observations: List<KeywordObservationEntity>): List<Long>
    
    // ════════════════════════════════════════════════════════════════════
    // READ OPERATIONS (for analysis)
    // ════════════════════════════════════════════════════════════════════
    
    /**
     * Get all observations ordered by timestamp (newest first).
     * For export/analysis.
     */
    @Query("SELECT * FROM keyword_observations ORDER BY timestamp DESC")
    suspend fun getAll(): List<KeywordObservationEntity>
    
    /**
     * Get observations as Flow for reactive UI.
     */
    @Query("SELECT * FROM keyword_observations ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<KeywordObservationEntity>>
    
    /**
     * Get observations for a specific trade guess.
     */
    @Query("SELECT * FROM keyword_observations WHERE tradeGuess = :trade ORDER BY timestamp DESC")
    suspend fun getByTrade(trade: String): List<KeywordObservationEntity>
    
    /**
     * Get observations within a time range.
     */
    @Query("SELECT * FROM keyword_observations WHERE timestamp BETWEEN :startTime AND :endTime ORDER BY timestamp DESC")
    suspend fun getByTimeRange(startTime: Long, endTime: Long): List<KeywordObservationEntity>
    
    /**
     * Get distinct keywords observed.
     */
    @Query("SELECT DISTINCT keyword FROM keyword_observations ORDER BY keyword")
    suspend fun getDistinctKeywords(): List<String>
    
    /**
     * Get keyword frequency (how many times each keyword was observed).
     */
    @Query("SELECT keyword, COUNT(*) as count FROM keyword_observations GROUP BY keyword ORDER BY count DESC")
    suspend fun getKeywordFrequency(): List<KeywordFrequency>
    
    /**
     * Get total observation count.
     */
    @Query("SELECT COUNT(*) FROM keyword_observations")
    suspend fun getCount(): Int
    
    /**
     * Get observations by source mode.
     */
    @Query("SELECT * FROM keyword_observations WHERE sourceMode = :sourceMode ORDER BY timestamp DESC")
    suspend fun getBySourceMode(sourceMode: String): List<KeywordObservationEntity>
    
    /**
     * Get observations from Playwright TEST ⧉ runs.
     */
    @Query("SELECT * FROM keyword_observations WHERE sourceMode = 'test_button_playwright' ORDER BY timestamp DESC")
    suspend fun getPlaywrightObservations(): List<KeywordObservationEntity>
    
    /**
     * Get count of Playwright observations.
     */
    @Query("SELECT COUNT(*) FROM keyword_observations WHERE sourceMode = 'test_button_playwright'")
    suspend fun getPlaywrightObservationCount(): Int
    
    /**
     * Check if a keyword has been observed before.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM keyword_observations WHERE keyword = :keyword LIMIT 1)")
    suspend fun hasKeyword(keyword: String): Boolean
    
    /**
     * Get recent observations (last N entries).
     */
    @Query("SELECT * FROM keyword_observations ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<KeywordObservationEntity>
}

/**
 * Data class for keyword frequency query results.
 */
data class KeywordFrequency(
    val keyword: String,
    val count: Int
)
