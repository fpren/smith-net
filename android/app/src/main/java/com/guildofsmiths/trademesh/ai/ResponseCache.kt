package com.guildofsmiths.trademesh.ai

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * ResponseCache - Persistent storage for AI responses awaiting sync
 * 
 * Stores AI responses to disk for:
 * - Offline-generated responses that need cloud sync
 * - Attribution data for AI messages
 * - Recovery after app restart
 * 
 * Uses simple JSON file storage for reliability.
 */
object ResponseCache {
    
    private const val TAG = "ResponseCache"
    private const val CACHE_FILE = "ai_response_cache.json"
    private const val MAX_CACHE_SIZE = 200
    private const val MAX_AGE_MS = 7 * 24 * 60 * 60 * 1000L // 7 days
    
    private var cacheFile: File? = null
    private val cache = ConcurrentLinkedQueue<CachedAIResponse>()
    
    private val _pendingCount = MutableStateFlow(0)
    val pendingCount: StateFlow<Int> = _pendingCount.asStateFlow()
    
    // ════════════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ════════════════════════════════════════════════════════════════════
    
    /**
     * Initialize the cache. Call once at app start.
     */
    fun initialize(context: Context) {
        cacheFile = File(context.filesDir, CACHE_FILE)
        loadFromDisk()
        cleanupOldEntries()
        _pendingCount.value = cache.size
        Log.i(TAG, "ResponseCache initialized with ${cache.size} entries")
    }
    
    // ════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ════════════════════════════════════════════════════════════════════
    
    /**
     * Add a response to the cache.
     */
    fun add(response: CachedAIResponse) {
        // Avoid duplicates
        if (cache.any { it.id == response.id }) {
            Log.d(TAG, "Response ${response.id} already in cache")
            return
        }
        
        cache.add(response)
        
        // Enforce size limit
        while (cache.size > MAX_CACHE_SIZE) {
            cache.poll()
        }
        
        _pendingCount.value = cache.size
        saveToDisk()
        
        Log.d(TAG, "Added response ${response.id} to cache (total: ${cache.size})")
    }
    
    /**
     * Get all pending responses.
     */
    fun getAll(): List<CachedAIResponse> = cache.toList()
    
    /**
     * Get pending responses for a specific channel.
     */
    fun getByChannel(channelId: String): List<CachedAIResponse> {
        return cache.filter { it.channelId == channelId }
    }
    
    /**
     * Get pending responses for a specific job.
     */
    fun getByJob(jobId: String): List<CachedAIResponse> {
        return cache.filter { it.jobId == jobId }
    }
    
    /**
     * Remove synced responses.
     */
    fun removeSynced(ids: List<String>) {
        val before = cache.size
        cache.removeAll { it.id in ids }
        _pendingCount.value = cache.size
        saveToDisk()
        Log.d(TAG, "Removed ${before - cache.size} synced responses")
    }
    
    /**
     * Mark a response as synced.
     */
    fun markSynced(id: String) {
        val response = cache.find { it.id == id }
        if (response != null) {
            cache.remove(response)
            // Add back with synced flag (for history, if needed)
            // Or just remove if we don't need synced ones in cache
        }
        _pendingCount.value = cache.size
        saveToDisk()
    }
    
    /**
     * Clear all cached responses.
     */
    fun clear() {
        cache.clear()
        _pendingCount.value = 0
        saveToDisk()
        Log.i(TAG, "Cache cleared")
    }
    
    /**
     * Get count of pending responses.
     */
    fun count(): Int = cache.size
    
    // ════════════════════════════════════════════════════════════════════
    // PERSISTENCE
    // ════════════════════════════════════════════════════════════════════
    
    private fun saveToDisk() {
        val file = cacheFile ?: return
        
        try {
            val json = JSONArray()
            cache.forEach { response ->
                json.put(responseToJson(response))
            }
            file.writeText(json.toString())
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save cache to disk", e)
        }
    }
    
    private fun loadFromDisk() {
        val file = cacheFile ?: return
        
        if (!file.exists()) return
        
        try {
            val text = file.readText()
            if (text.isBlank()) return
            
            val json = JSONArray(text)
            cache.clear()
            
            for (i in 0 until json.length()) {
                val responseJson = json.getJSONObject(i)
                jsonToResponse(responseJson)?.let { cache.add(it) }
            }
            
            Log.i(TAG, "Loaded ${cache.size} responses from disk")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load cache from disk", e)
        }
    }
    
    private fun responseToJson(response: CachedAIResponse): JSONObject {
        return JSONObject().apply {
            put("id", response.id)
            put("timestamp", response.timestamp)
            put("query", response.query)
            put("response", response.response)
            put("source", response.source.name)
            put("model", response.model)
            put("channelId", response.channelId)
            put("jobId", response.jobId)
            put("userId", response.userId)
            put("synced", response.synced)
        }
    }
    
    private fun jsonToResponse(json: JSONObject): CachedAIResponse? {
        return try {
            CachedAIResponse(
                id = json.getString("id"),
                timestamp = json.getLong("timestamp"),
                query = json.getString("query"),
                response = json.getString("response"),
                source = AISource.valueOf(json.optString("source", "RULE_BASED")),
                model = json.getString("model"),
                channelId = if (json.has("channelId") && !json.isNull("channelId")) json.getString("channelId") else null,
                jobId = if (json.has("jobId") && !json.isNull("jobId")) json.getString("jobId") else null,
                userId = if (json.has("userId") && !json.isNull("userId")) json.getString("userId") else null,
                synced = json.optBoolean("synced", false)
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse response JSON", e)
            null
        }
    }
    
    private fun cleanupOldEntries() {
        val now = System.currentTimeMillis()
        val before = cache.size
        
        cache.removeAll { (now - it.timestamp) > MAX_AGE_MS }
        
        if (cache.size < before) {
            Log.d(TAG, "Cleaned up ${before - cache.size} old entries")
            _pendingCount.value = cache.size
            saveToDisk()
        }
    }
}
