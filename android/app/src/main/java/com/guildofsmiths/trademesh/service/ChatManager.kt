package com.guildofsmiths.trademesh.service

import android.util.Log
import com.guildofsmiths.trademesh.data.Message

/**
 * ChatManager: Stub for IP-based chat backend (Phase 0).
 * 
 * In production, this will handle:
 * - WebSocket/HTTP connections to chat server
 * - Message persistence and ordering
 * - Large payload support (text + media)
 * - Group management
 * 
 * For Phase 0, simply logs operations for validation.
 */
object ChatManager {
    
    private const val TAG = "ChatManager"
    
    /** Connection state for future implementation */
    private var isConnected = false
    
    /**
     * Send message via IP chat path.
     * Phase 0: Logs the message for testing validation.
     */
    fun sendMessage(message: Message) {
        Log.i(TAG, buildString {
            append("CHAT SEND: ")
            append("[${message.id.take(8)}] ")
            append("from=${message.senderName} ")
            append("mesh_origin=${message.isMeshOrigin} ")
            append("content=\"${message.content.take(50)}\"")
            if (message.content.length > 50) append("...")
        })
        
        // Phase 0: No actual network call
        // In production: POST to chat API or send via WebSocket
    }
    
    /**
     * Simulate connection state (for future use).
     */
    fun connect() {
        Log.d(TAG, "Chat backend connection initiated (stub)")
        isConnected = true
    }
    
    /**
     * Simulate disconnection (for future use).
     */
    fun disconnect() {
        Log.d(TAG, "Chat backend disconnected (stub)")
        isConnected = false
    }
    
    /**
     * Check if connected to chat backend.
     */
    fun isConnected(): Boolean = isConnected
}
