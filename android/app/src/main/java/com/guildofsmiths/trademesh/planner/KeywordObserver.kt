package com.guildofsmiths.trademesh.planner

import android.content.Context
import android.util.Log
import com.guildofsmiths.trademesh.db.AppDatabase
import com.guildofsmiths.trademesh.db.KeywordObservationEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * KeywordObserver - Logs unknown keywords detected during TEST ⧉ compile
 * 
 * PURPOSE:
 * - Capture keywords not present in local keyword maps
 * - For later analysis and potential keyword map expansion
 * - OBSERVATION ONLY - does not feed back into runtime logic
 * 
 * BEHAVIOR:
 * - On detection of unknown keyword:
 *   - Log keyword
 *   - Log guessed trade
 *   - Log source = "planner_test_compile"
 *   - Log timestamp
 * 
 * CONSTRAINTS:
 * - Does NOT modify keyword maps automatically
 * - Does NOT feed data back into runtime logic
 * - Append-only database operations
 * - No effect on production behavior
 * 
 * DATABASE:
 * - Append-only table: keyword_observations
 * - No updates, no deletes
 */
object KeywordObserver {
    
    private const val TAG = "KeywordObserver"
    
    private var database: AppDatabase? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // Known keywords from OnlineResolver (for detecting unknowns)
    private val knownKeywords = setOf(
        // Electrical
        "wire", "wiring", "electrical", "outlet", "switch",
        "circuit", "breaker", "panel", "voltage", "amp", "conduit", "motherboard",
        // Plumbing
        "pipe", "plumb", "drain", "faucet", "toilet",
        "water heater", "sewage", "valve", "fitting",
        // HVAC
        "hvac", "heating", "cooling", "furnace", "ac",
        "air condition", "duct", "ventilation", "thermostat",
        // Masonry
        "brick", "block", "concrete", "mortar", "stone",
        "chimney", "foundation", "masonry", "fireplace",
        // Carpentry
        "wood", "frame", "framing", "cabinet", "trim",
        "door", "window", "deck", "stair", "carpenter",
        // Roofing
        "roof", "shingle", "gutter", "flashing", "soffit",
        // Drywall
        "drywall", "sheetrock", "plaster", "wall board",
        // Painting
        "paint", "primer", "stain", "finish", "coat",
        // Flooring
        "floor", "tile", "carpet", "hardwood", "laminate", "vinyl",
        // General
        "build", "install", "repair", "replace", "construct"
    )
    
    /**
     * Initialize with application context.
     * Call once at app startup.
     */
    fun initialize(context: Context) {
        if (database != null) {
            Log.d(TAG, "Already initialized")
            return
        }
        
        database = AppDatabase.getInstance(context.applicationContext)
        Log.i(TAG, "KeywordObserver initialized")
    }
    
    /**
     * Observe keywords in input text and log any unknowns.
     * 
     * Called by OnlineResolver during TEST ⧉ compile.
     * 
     * @param inputText The full input text being analyzed
     * @param tradeGuess The trade classification that was guessed
     * @param sourceMode The source mode (default: planner_test_compile)
     */
    fun observeKeywords(
        inputText: String,
        tradeGuess: String,
        sourceMode: String = KeywordObservationEntity.SOURCE_PLANNER_TEST_COMPILE
    ) {
        val db = database
        if (db == null) {
            Log.w(TAG, "Not initialized - cannot log observations")
            return
        }
        
        scope.launch {
            try {
                val unknownKeywords = extractUnknownKeywords(inputText)
                
                if (unknownKeywords.isEmpty()) {
                    Log.d(TAG, "No unknown keywords detected")
                    return@launch
                }
                
                Log.i(TAG, "Detected ${unknownKeywords.size} unknown keywords: $unknownKeywords")
                
                // Create observation entries
                val observations = unknownKeywords.map { keyword ->
                    KeywordObservationEntity.create(
                        keyword = keyword,
                        detectedIn = inputText,
                        tradeGuess = tradeGuess,
                        sourceMode = sourceMode
                    )
                }
                
                // Insert into append-only table
                val insertedIds = db.keywordObservationDao().insertAll(observations)
                Log.i(TAG, "Logged ${insertedIds.count { it > 0 }} keyword observations")
                
            } catch (e: Exception) {
                // Never fail silently - log but don't crash
                Log.e(TAG, "Failed to log keyword observations: ${e.message}")
            }
        }
    }
    
    /**
     * Log a single unknown keyword observation.
     * 
     * @param keyword The unknown keyword
     * @param detectedIn Context where it was found
     * @param tradeGuess The guessed trade
     * @param sourceMode Source of the observation (default: planner_test_compile)
     */
    fun logUnknownKeyword(
        keyword: String,
        detectedIn: String,
        tradeGuess: String,
        sourceMode: String = KeywordObservationEntity.SOURCE_PLANNER_TEST_COMPILE
    ) {
        val db = database
        if (db == null) {
            Log.w(TAG, "Not initialized - cannot log observation")
            return
        }
        
        scope.launch {
            try {
                val observation = KeywordObservationEntity.create(
                    keyword = keyword,
                    detectedIn = detectedIn,
                    tradeGuess = tradeGuess,
                    sourceMode = sourceMode
                )
                
                val id = db.keywordObservationDao().insert(observation)
                if (id > 0) {
                    Log.i(TAG, "Logged keyword observation: '$keyword' -> $tradeGuess (source: $sourceMode)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to log keyword observation: ${e.message}")
            }
        }
    }
    
    /**
     * Log keywords detected by Playwright during TEST ⧉.
     * 
     * Uses source = 'test_button_playwright' to distinguish from other sources.
     * 
     * @param keyword The detected keyword
     * @param detectedIn Context where it was found
     * @param tradeGuess The guessed trade
     */
    fun logPlaywrightKeyword(
        keyword: String,
        detectedIn: String,
        tradeGuess: String
    ) {
        logUnknownKeyword(
            keyword = keyword,
            detectedIn = detectedIn,
            tradeGuess = tradeGuess,
            sourceMode = KeywordObservationEntity.SOURCE_TEST_BUTTON_PLAYWRIGHT
        )
    }
    
    /**
     * Extract words from input that are not in known keyword maps.
     * 
     * Uses simple tokenization - splits on whitespace and punctuation,
     * filters out common stop words and short tokens.
     */
    private fun extractUnknownKeywords(inputText: String): List<String> {
        val lowerInput = inputText.lowercase()
        
        // Tokenize: split on non-alphanumeric, filter short words
        val tokens = lowerInput
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length >= 3 }
            .distinct()
        
        // Filter out known keywords and common stop words
        val stopWords = setOf(
            "the", "and", "for", "are", "but", "not", "you", "all",
            "can", "her", "was", "one", "our", "out", "has", "have",
            "been", "will", "with", "this", "that", "from", "they",
            "what", "when", "where", "which", "who", "how", "why",
            "need", "needs", "want", "wants", "like", "just", "get",
            "got", "make", "made", "some", "any", "new", "old"
        )
        
        return tokens.filter { token ->
            // Not a known keyword
            !knownKeywords.any { known -> 
                token == known || token.contains(known) || known.contains(token)
            } &&
            // Not a stop word
            !stopWords.contains(token) &&
            // Not purely numeric
            !token.all { it.isDigit() }
        }
    }
    
    /**
     * Check if a keyword is known (in local maps).
     */
    fun isKnownKeyword(keyword: String): Boolean {
        val lower = keyword.lowercase().trim()
        return knownKeywords.any { known ->
            lower == known || lower.contains(known) || known.contains(lower)
        }
    }
    
    // ════════════════════════════════════════════════════════════════════
    // ANALYSIS HELPERS (for debugging/export)
    // ════════════════════════════════════════════════════════════════════
    
    /**
     * Get observation count for debugging.
     */
    suspend fun getObservationCount(): Int {
        return database?.keywordObservationDao()?.getCount() ?: 0
    }
    
    /**
     * Get recent observations for debugging.
     */
    suspend fun getRecentObservations(limit: Int = 20): List<KeywordObservationEntity> {
        return database?.keywordObservationDao()?.getRecent(limit) ?: emptyList()
    }
    
    /**
     * Get keyword frequency for analysis.
     */
    suspend fun getKeywordFrequency(): Map<String, Int> {
        val frequencies = database?.keywordObservationDao()?.getKeywordFrequency() ?: emptyList()
        return frequencies.associate { it.keyword to it.count }
    }
    
    // ════════════════════════════════════════════════════════════════════
    // PLAYWRIGHT-SPECIFIC HELPERS
    // ════════════════════════════════════════════════════════════════════
    
    /**
     * Get observations from Playwright TEST ⧉ runs only.
     */
    suspend fun getPlaywrightObservations(): List<KeywordObservationEntity> {
        return database?.keywordObservationDao()?.getPlaywrightObservations() ?: emptyList()
    }
    
    /**
     * Get count of Playwright observations.
     */
    suspend fun getPlaywrightObservationCount(): Int {
        return database?.keywordObservationDao()?.getPlaywrightObservationCount() ?: 0
    }
}
