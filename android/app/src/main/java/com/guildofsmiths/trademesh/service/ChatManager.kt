package com.guildofsmiths.trademesh.service

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.guildofsmiths.trademesh.data.Message
import com.guildofsmiths.trademesh.data.MessageRepository
import com.guildofsmiths.trademesh.data.UserPreferences
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * ChatManager: Online chat via WebSocket + HTTP API.
 * 
 * - WebSocket for receiving real-time messages
 * - HTTP API for sending messages
 * - Works without gateway/mesh - pure online mode
 */
object ChatManager {
    
    private const val TAG = "ChatManager"
    
    // Backend URLs
    private var httpUrl = "http://192.168.8.163:3000"
    private var wsUrl = "ws://192.168.8.163:3000"
    
    private val httpClient = OkHttpClient()
    private val wsClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    
    private var webSocket: WebSocket? = null
    private val handler = Handler(Looper.getMainLooper())
    private var reconnectRunnable: Runnable? = null
    private const val RECONNECT_DELAY_MS = 5000L
    
    /** Connection state */
    private var isConnected = false
    private var isAuthenticated = false
    
    // Message listener
    interface OnMessageListener {
        fun onMessageReceived(message: Message)
        fun onChannelCleared(channelId: String)
    }
    private var messageListener: OnMessageListener? = null
    
    fun setMessageListener(listener: OnMessageListener) {
        messageListener = listener
    }
    
    /**
     * Set the backend URL
     */
    fun setBackendUrl(url: String) {
        // Handle both http and ws URLs
        httpUrl = url.replace("ws://", "http://").replace("wss://", "https://")
        wsUrl = url.replace("http://", "ws://").replace("https://", "wss://")
        Log.d(TAG, "Backend URL set to: HTTP=$httpUrl, WS=$wsUrl")
    }
    
    /**
     * Connect to backend via WebSocket for receiving messages.
     */
    fun connect() {
        if (isConnected) {
            Log.d(TAG, "Already connected")
            return
        }
        
        val userId = UserPreferences.getUserId()
        if (userId.isNullOrEmpty()) {
            Log.e(TAG, "❌ No user ID - cannot connect")
            return
        }
        val userName = UserPreferences.getUserName() ?: "Unknown"
        
        Log.i(TAG, "🌐 Connecting to online chat: $wsUrl")
        
        val request = Request.Builder()
            .url(wsUrl)
            .build()
        
        webSocket = wsClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "✅ WebSocket connected")
                isConnected = true
                
                // Authenticate as regular client (NOT a relay)
                val authPayload = JSONObject().apply {
                    put("type", "auth")
                    put("payload", JSONObject().apply {
                        put("userId", userId)
                        put("userName", userName)
                        put("isRelay", false) // Regular online client, not gateway
                    })
                    put("timestamp", System.currentTimeMillis())
                }
                webSocket.send(authPayload.toString())
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }
            
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $reason")
                isConnected = false
                isAuthenticated = false
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $reason")
                isConnected = false
                isAuthenticated = false
                scheduleReconnect()
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket error: ${t.message}")
                isConnected = false
                isAuthenticated = false
                scheduleReconnect()
            }
        })
    }
    
    /**
     * Handle incoming WebSocket message
     */
    private fun handleMessage(text: String) {
        try {
            val json = JSONObject(text)
            val type = json.getString("type")
            val payload = json.optJSONObject("payload")
            
            when (type) {
                "auth_ok" -> {
                    Log.d(TAG, "✅ Authenticated with backend")
                    isAuthenticated = true
                }
                
                "message" -> {
                    // Incoming message from another user
                    payload?.let { handleIncomingMessage(it) }
                }
                
                "channel_cleared" -> {
                    val channelId = payload?.optString("channelId")
                    if (channelId != null) {
                        Log.d(TAG, "🗑️ Channel cleared: $channelId")
                        handler.post {
                            messageListener?.onChannelCleared(channelId)
                            MessageRepository.clearChannel(channelId)
                            MessageRepository.clearChannel("general")
                        }
                    }
                }
                
                "message_deleted" -> {
                    val messageId = payload?.optString("messageId")
                    if (messageId != null) {
                        Log.d(TAG, "🗑️ Message deleted: $messageId")
                        handler.post {
                            MessageRepository.removeMessage(messageId)
                        }
                    }
                }
                
                "error" -> {
                    val error = payload?.optString("error") ?: "Unknown error"
                    Log.e(TAG, "Server error: $error")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing message: ${e.message}")
        }
    }
    
    /**
     * Handle incoming message from WebSocket
     */
    private fun handleIncomingMessage(payload: JSONObject) {
        val senderId = payload.getString("senderId")
        val myUserId = UserPreferences.getUserId()
        
        // Don't process our own messages (we already have them locally)
        if (senderId == myUserId) {
            return
        }
        
        // Normalize channelId - backend uses UUID, we use "general" locally
        // For now, treat any UUID channel as "general" since that's our main channel
        var channelId = payload.getString("channelId")
        if (channelId.contains("-")) {
            // It's a UUID, normalize to "general"
            channelId = "general"
            Log.d(TAG, "Normalized channel UUID to 'general'")
        }
        
        val message = Message(
            id = payload.getString("id"),
            channelId = channelId,
            senderId = senderId,
            senderName = payload.getString("senderName"),
            content = payload.getString("content"),
            timestamp = payload.getLong("timestamp"),
            recipientId = payload.optString("recipientId").takeIf { it.isNotEmpty() },
            recipientName = payload.optString("recipientName").takeIf { it.isNotEmpty() },
            isMeshOrigin = false
        )
        
        Log.d(TAG, "📨 Received online message: ${message.content.take(30)} from ${message.senderName}")
        
        // Add to repository
        handler.post {
            MessageRepository.addMessage(message)
            messageListener?.onMessageReceived(message)
        }
    }
    
    /**
     * Schedule reconnection
     */
    private fun scheduleReconnect() {
        reconnectRunnable?.let { handler.removeCallbacks(it) }
        reconnectRunnable = Runnable {
            Log.d(TAG, "🔄 Attempting reconnect...")
            connect()
        }
        handler.postDelayed(reconnectRunnable!!, RECONNECT_DELAY_MS)
    }
    
    /**
     * Send message via online backend API.
     */
    fun sendMessage(message: Message, callback: ((Boolean) -> Unit)? = null) {
        Log.i(TAG, "📤 ONLINE SEND: [${message.id.take(8)}] content=\"${message.content.take(50)}\"")
        
        val userId = UserPreferences.getUserId() ?: "unknown"
        val userName = UserPreferences.getUserName() ?: "Unknown"
        
        val json = JSONObject().apply {
            put("channelId", message.channelId)
            put("content", message.content)
        }
        
        val request = Request.Builder()
            .url("$httpUrl/api/messages/inject")
            .addHeader("X-User-Id", userId)
            .addHeader("X-User-Name", userName)
            .addHeader("Content-Type", "application/json")
            .post(json.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()
        
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "❌ Online send failed: ${e.message}")
                callback?.invoke(false)
            }
            
            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    Log.i(TAG, "✅ Online send success!")
                    callback?.invoke(true)
                } else {
                    Log.e(TAG, "❌ Online send error: ${response.code}")
                    callback?.invoke(false)
                }
                response.close()
            }
        })
    }
    
    /**
     * Disconnect from backend.
     */
    fun disconnect() {
        reconnectRunnable?.let { handler.removeCallbacks(it) }
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        isConnected = false
        isAuthenticated = false
        Log.d(TAG, "Disconnected from chat backend")
    }
    
    /**
     * Check if connected to chat backend.
     */
    fun isConnected(): Boolean = isConnected && isAuthenticated
    
    /**
     * Delete a message from backend (for "Delete for everyone").
     */
    fun deleteMessage(messageId: String, channelId: String, callback: ((Boolean) -> Unit)? = null) {
        Log.i(TAG, "🗑️ DELETE: messageId=$messageId channelId=$channelId")
        
        val userId = UserPreferences.getUserId() ?: "unknown"
        
        val request = Request.Builder()
            .url("$httpUrl/api/messages/$messageId")
            .addHeader("X-User-Id", userId)
            .delete()
            .build()
        
        httpClient.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "❌ Delete failed: ${e.message}")
                callback?.invoke(false)
            }
            
            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    Log.i(TAG, "✅ Delete success - message removed from backend")
                    callback?.invoke(true)
                } else {
                    Log.e(TAG, "❌ Delete error: ${response.code}")
                    callback?.invoke(false)
                }
                response.close()
            }
        })
    }
}
