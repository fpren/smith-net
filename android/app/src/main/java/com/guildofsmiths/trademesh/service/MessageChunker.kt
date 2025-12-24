package com.guildofsmiths.trademesh.service

import android.util.Log
import com.guildofsmiths.trademesh.data.Message
import java.util.concurrent.ConcurrentHashMap

/**
 * Handles splitting long messages into chunks and reassembling them.
 * 
 * Chunk format (text-safe, fits in 10 bytes):
 * - Prefix: "~" (chunk marker - won't appear in normal messages)
 * - Char 1: chunk index as hex digit (0-7)
 * - Char 2: total chunks as hex digit (1-8)
 * - Char 3: message ID hash as hex digit
 * - Chars 4-9: Content (6 chars per chunk)
 * 
 * Example: "~03Ahello" = chunk 0 of 3, msgId A, content "hello"
 * Maximum message size: 8 chunks × 6 chars = 48 characters
 */
object MessageChunker {
    private const val TAG = "MessageChunker"
    
    // Chunk constants - text-safe format
    private const val CHUNK_PREFIX = "~"  // Unique prefix for chunks
    private const val CHUNK_HEADER_SIZE = 4  // ~ + index + total + msgIdHash
    private const val CHUNK_CONTENT_SIZE = 6  // 6 chars of content per chunk
    private const val MAX_CHUNKS = 8
    const val MAX_UNCHUNKED_SIZE = 10  // Messages <= 10 chars don't need chunking
    const val MAX_CHUNKED_MESSAGE_SIZE = MAX_CHUNKS * CHUNK_CONTENT_SIZE  // 48 chars
    
    // Pending chunks awaiting reassembly: messageIdHash -> (chunkIndex -> chunkContent)
    private val pendingChunks = ConcurrentHashMap<Int, ChunkAssembly>()
    
    // Timeout for incomplete messages (30 seconds)
    private const val CHUNK_TIMEOUT_MS = 30_000L
    
    data class ChunkAssembly(
        val totalChunks: Int,
        val senderId: String,
        val channelHash: Short,
        val timestamp: Long,
        val chunks: MutableMap<Int, ByteArray> = mutableMapOf(),
        val createdAt: Long = System.currentTimeMillis()
    )
    
    /**
     * Check if content needs chunking.
     */
    fun needsChunking(content: String): Boolean {
        return content.toByteArray(Charsets.UTF_8).size > MAX_UNCHUNKED_SIZE
    }
    
    /**
     * Split a message into chunks.
     * Returns list of chunk payloads (each is the content field for a Message).
     */
    fun chunkMessage(messageId: String, content: String): List<String> {
        if (content.length <= MAX_UNCHUNKED_SIZE) {
            return listOf(content)  // No chunking needed
        }
        
        val truncated = if (content.length > MAX_CHUNKED_MESSAGE_SIZE) {
            Log.w(TAG, "Message too long (${content.length} chars), truncating to $MAX_CHUNKED_MESSAGE_SIZE")
            content.take(MAX_CHUNKED_MESSAGE_SIZE)
        } else {
            content
        }
        
        val totalChunks = (truncated.length + CHUNK_CONTENT_SIZE - 1) / CHUNK_CONTENT_SIZE
        val messageIdHash = Integer.toHexString(messageId.hashCode() and 0x0F).uppercase()
        
        val chunks = mutableListOf<String>()
        
        for (i in 0 until totalChunks) {
            val startIdx = i * CHUNK_CONTENT_SIZE
            val endIdx = minOf(startIdx + CHUNK_CONTENT_SIZE, truncated.length)
            val chunkContent = truncated.substring(startIdx, endIdx)
            
            // Build chunk: ~{index}{total}{msgHash}{content}
            // Example: "~03Ahello" = chunk 0 of 3, hash A, content "hello"
            val chunk = "$CHUNK_PREFIX$i$totalChunks$messageIdHash$chunkContent"
            chunks.add(chunk)
        }
        
        Log.i(TAG, "📦 Split message into $totalChunks chunks (${truncated.length} chars)")
        return chunks
    }
    
    /**
     * Check if content is a chunk (starts with ~ prefix and has valid format).
     */
    fun isChunk(content: String): Boolean {
        if (content.length < CHUNK_HEADER_SIZE) return false
        if (!content.startsWith(CHUNK_PREFIX)) return false
        
        // Validate format: ~{index}{total}{hash}{content}
        val indexChar = content.getOrNull(1) ?: return false
        val totalChar = content.getOrNull(2) ?: return false
        
        val chunkIndex = indexChar.digitToIntOrNull() ?: return false
        val totalChunks = totalChar.digitToIntOrNull() ?: return false
        
        // Validate ranges
        if (totalChunks < 1 || totalChunks > MAX_CHUNKS) return false
        if (chunkIndex >= totalChunks) return false
        
        return true
    }
    
    /**
     * Process a received chunk. Returns the complete message if all chunks received, null otherwise.
     */
    fun processChunk(
        content: String,
        senderId: String,
        channelHash: Short,
        timestamp: Long
    ): String? {
        if (!isChunk(content)) return null
        
        val chunkIndex = content[1].digitToIntOrNull() ?: return null
        val totalChunks = content[2].digitToIntOrNull() ?: return null
        val messageIdHash = content[3].toString()
        val chunkContent = content.substring(CHUNK_HEADER_SIZE)
        
        // Create unique key from sender + messageIdHash + channelHash
        val assemblyKey = (senderId.hashCode() xor messageIdHash.hashCode() xor channelHash.toInt())
        
        Log.d(TAG, "📥 Chunk ${chunkIndex + 1}/$totalChunks received (msgHash=$messageIdHash, content='$chunkContent')")
        
        // Get or create assembly
        val assembly = pendingChunks.getOrPut(assemblyKey) {
            ChunkAssembly(totalChunks, senderId, channelHash, timestamp)
        }
        
        // Store chunk
        assembly.chunks[chunkIndex] = chunkContent.toByteArray()
        
        // Check if complete
        if (assembly.chunks.size == totalChunks) {
            pendingChunks.remove(assemblyKey)
            
            // Reassemble in order
            val reassembled = StringBuilder()
            for (i in 0 until totalChunks) {
                val chunk = assembly.chunks[i]
                if (chunk != null) {
                    reassembled.append(String(chunk, Charsets.UTF_8))
                }
            }
            
            Log.i(TAG, "✅ Message reassembled: '$reassembled' (${assembly.chunks.size} chunks)")
            return reassembled.toString()
        }
        
        Log.d(TAG, "⏳ Waiting for ${totalChunks - assembly.chunks.size} more chunks")
        return null
    }
    
    /**
     * Clean up stale pending chunks.
     */
    fun cleanupStaleChunks() {
        val now = System.currentTimeMillis()
        val staleKeys = pendingChunks.entries
            .filter { now - it.value.createdAt > CHUNK_TIMEOUT_MS }
            .map { it.key }
        
        staleKeys.forEach { key ->
            pendingChunks.remove(key)
            Log.d(TAG, "🗑️ Removed stale chunk assembly: $key")
        }
    }
}
