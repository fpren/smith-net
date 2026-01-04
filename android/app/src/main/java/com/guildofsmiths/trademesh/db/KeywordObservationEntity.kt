package com.guildofsmiths.trademesh.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for keyword observations.
 * 
 * APPEND-ONLY table for logging unknown keywords detected during TEST ⧉ compile.
 * 
 * PURPOSE:
 * - Capture keywords not present in local keyword maps
 * - For later analysis and potential keyword map expansion
 * - OBSERVATION ONLY - does not feed back into runtime logic
 * 
 * CONSTRAINTS:
 * - No updates
 * - No deletes
 * - Insert only via KeywordObservationDao.insert()
 */
@Entity(
    tableName = "keyword_observations",
    indices = [
        Index(value = ["keyword"]),
        Index(value = ["tradeGuess"]),
        Index(value = ["timestamp"]),
        Index(value = ["sourceMode"])
    ]
)
data class KeywordObservationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    
    /**
     * The detected keyword that was not in local maps
     */
    val keyword: String,
    
    /**
     * The full text/context where the keyword was detected
     * (truncated to 500 chars for storage efficiency)
     */
    val detectedIn: String,
    
    /**
     * The trade classification that was guessed for this keyword
     * e.g., "ELECTRICAL", "PLUMBING", "UNKNOWN"
     */
    val tradeGuess: String,
    
    /**
     * Unix timestamp of when this observation was recorded
     */
    val timestamp: Long,
    
    /**
     * Source mode that triggered this observation
     * Always "planner_test_compile" for TEST ⧉ compile path
     */
    val sourceMode: String
) {
    companion object {
        const val SOURCE_PLANNER_TEST_COMPILE = "planner_test_compile"
        const val SOURCE_TEST_BUTTON_PLAYWRIGHT = "test_button_playwright"
        const val MAX_DETECTED_IN_LENGTH = 500
        
        /**
         * Create an observation entry
         */
        fun create(
            keyword: String,
            detectedIn: String,
            tradeGuess: String,
            sourceMode: String = SOURCE_PLANNER_TEST_COMPILE
        ): KeywordObservationEntity {
            return KeywordObservationEntity(
                keyword = keyword.lowercase().trim(),
                detectedIn = detectedIn.take(MAX_DETECTED_IN_LENGTH),
                tradeGuess = tradeGuess,
                timestamp = System.currentTimeMillis(),
                sourceMode = sourceMode
            )
        }
    }
}
