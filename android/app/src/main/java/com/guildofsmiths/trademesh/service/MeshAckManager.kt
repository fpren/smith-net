package com.guildofsmiths.trademesh.service

import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * MeshAckManager: Handles ACK tracking and retry logic for BLE mesh messages.
 * 
 * - Tracks outbound messages awaiting ACK
 * - Retries up to 3 times with 1.5-2s intervals
 * - Marks messages as FAILED when retries exhausted
 * - No infinite loops, no permanent background timers
 */
object MeshAckManager {
    
    private const val TAG = "MeshAckManager"
    
    /** Maximum retry attempts (including initial send) */
    private const val MAX_ATTEMPTS = 3
    
    /** Retry interval in milliseconds */
    private const val RETRY_INTERVAL_MS = 1750L // ~1.75s
    
    /** ACK packet prefix marker */
    const val ACK_PREFIX = "[ACK:"
    const val ACK_SUFFIX = "]"
    
    /** Pending outbound messages awaiting ACK: messageId -> PendingMessage */
    private val pendingAcks = mutableMapOf<String, PendingMessage>()
    
    /** Handler for retry scheduling */
    private val handler = Handler(Looper.getMainLooper())
    
    /** Callback for retry broadcasts */
    private var retryCallback: ((String) -> Unit)? = null
    
    /**
     * Pending message tracking state.
     */
    data class PendingMessage(
        val messageId: String,
        val originalContent: String,
        var attemptCount: Int = 1,
        var isFailed: Boolean = false,
        val retryRunnable: Runnable
    )
    
    /**
     * Set the callback for retrying messages.
     * Called by MeshService to inject the broadcast function.
     */
    fun setRetryCallback(callback: (String) -> Unit) {
        retryCallback = callback
    }
    
    /**
     * Register an outbound message for ACK tracking.
     * Called when a non-presence message is broadcast.
     * 
     * @param messageId The unique message ID
     * @param content The message content (for logging)
     */
    fun registerOutbound(messageId: String, content: String) {
        if (pendingAcks.containsKey(messageId)) {
            Log.d(TAG, "Message already registered: ${messageId.take(8)}...")
            return
        }
        
        val retryRunnable = object : Runnable {
            override fun run() {
                retryMessage(messageId)
            }
        }
        
        val pending = PendingMessage(
            messageId = messageId,
            originalContent = content,
            attemptCount = 1,
            retryRunnable = retryRunnable
        )
        
        pendingAcks[messageId] = pending
        
        // Schedule first retry check
        handler.postDelayed(retryRunnable, RETRY_INTERVAL_MS)
        
        Log.d(TAG, "📤 Registered for ACK: ${messageId.take(8)}... (attempt 1/$MAX_ATTEMPTS)")
    }
    
    /**
     * Handle retry logic for a message.
     */
    private fun retryMessage(messageId: String) {
        val pending = pendingAcks[messageId] ?: return
        
        if (pending.isFailed) {
            // Already marked failed, clean up
            pendingAcks.remove(messageId)
            return
        }
        
        pending.attemptCount++
        
        if (pending.attemptCount > MAX_ATTEMPTS) {
            // Retries exhausted - mark as failed
            pending.isFailed = true
            Log.w(TAG, "❌ Message FAILED (no ACK after $MAX_ATTEMPTS attempts): ${messageId.take(8)}...")
            pendingAcks.remove(messageId)
            // Note: Message remains in UI - we don't remove it
            return
        }
        
        Log.i(TAG, "🔄 Retrying message: ${messageId.take(8)}... (attempt ${pending.attemptCount}/$MAX_ATTEMPTS)")
        
        // Trigger retry broadcast via callback
        retryCallback?.invoke(messageId)
        
        // Schedule next retry
        handler.postDelayed(pending.retryRunnable, RETRY_INTERVAL_MS)
    }
    
    /**
     * Handle received ACK for an outbound message.
     * Cancels retries and clears pending state.
     * 
     * @param originalMessageId The ID of the message being acknowledged
     * @return true if ACK was for a pending message, false otherwise
     */
    fun onAckReceived(originalMessageId: String): Boolean {
        val pending = pendingAcks.remove(originalMessageId)
        
        if (pending == null) {
            Log.d(TAG, "ACK for unknown/already-resolved message: ${originalMessageId.take(8)}...")
            return false
        }
        
        // Cancel retry timer
        handler.removeCallbacks(pending.retryRunnable)
        
        Log.i(TAG, "✅ ACK received for: ${originalMessageId.take(8)}... (attempt ${pending.attemptCount})")
        return true
    }
    
    /**
     * Check if a message content is an ACK packet.
     */
    fun isAckPacket(content: String): Boolean {
        return content.startsWith(ACK_PREFIX) && content.endsWith(ACK_SUFFIX)
    }
    
    /**
     * Extract the original message ID from an ACK packet.
     */
    fun extractAckMessageId(content: String): String? {
        if (!isAckPacket(content)) return null
        return content.removePrefix(ACK_PREFIX).removeSuffix(ACK_SUFFIX)
    }
    
    /**
     * Create an ACK packet content for a message ID.
     */
    fun createAckContent(messageId: String): String {
        return "$ACK_PREFIX$messageId$ACK_SUFFIX"
    }
    
    /**
     * Check if a message requires ACK (non-presence messages only).
     */
    fun requiresAck(channelId: String, content: String): Boolean {
        // No ACK for presence channel
        if (channelId == "_presence") return false
        
        // No ACK for heartbeat/ping
        if (content == "[heartbeat]" || content == "[ping]") return false
        
        // No ACK for ACK packets themselves
        if (isAckPacket(content)) return false
        
        return true
    }
    
    /**
     * Get the number of pending messages awaiting ACK.
     */
    fun getPendingCount(): Int = pendingAcks.size
    
    /**
     * Check if a specific message is still pending ACK.
     */
    fun isPending(messageId: String): Boolean = pendingAcks.containsKey(messageId)
    
    /**
     * Check if a specific message has failed (retries exhausted).
     */
    fun isFailed(messageId: String): Boolean = pendingAcks[messageId]?.isFailed == true
    
    /**
     * Clear all pending ACKs (for cleanup on service shutdown).
     */
    fun clearAll() {
        pendingAcks.values.forEach { pending ->
            handler.removeCallbacks(pending.retryRunnable)
        }
        pendingAcks.clear()
        Log.d(TAG, "Cleared all pending ACKs")
    }
}
