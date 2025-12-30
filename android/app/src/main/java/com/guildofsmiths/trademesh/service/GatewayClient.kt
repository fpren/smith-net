package com.guildofsmiths.trademesh.service

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.guildofsmiths.trademesh.data.Message
import com.guildofsmiths.trademesh.data.UserPreferences
import com.guildofsmiths.trademesh.engine.BoundaryEngine
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * GatewayClient: Connects Android relay to Smith Net backend
 * 
 * Acts as a bridge between BLE mesh and online messaging:
 * - Forwards mesh messages to backend (observer)
 * - Receives online messages and injects to mesh (injector)
 */
object GatewayClient {
    
    private const val TAG = "GatewayClient"
    
    // Backend URL - configurable
    private var backendUrl = "ws://192.168.8.163:3000" // Default to local network
    
    private var webSocket: WebSocket? = null
    private var isConnected = false
    private var isAuthenticated = false
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    
    private val handler = Handler(Looper.getMainLooper())
    private var reconnectRunnable: Runnable? = null
    private const val RECONNECT_DELAY_MS = 5000L
    
    // Listener for incoming online messages
    interface OnlineMessageListener {
        fun onOnlineMessage(message: Message)
    }
    private var messageListener: OnlineMessageListener? = null
    
    // Connection state listener
    interface ConnectionStateListener {
        fun onConnectionStateChanged(connected: Boolean)
    }
    private var connectionStateListener: ConnectionStateListener? = null
    
    // Channel cleared listener
    interface ChannelClearedListener {
        fun onChannelCleared(channelId: String)
    }
    private var channelClearedListener: ChannelClearedListener? = null
    
    /**
     * Set listener for connection state changes
     */
    fun setConnectionStateListener(listener: ConnectionStateListener) {
        connectionStateListener = listener
    }
    
    /**
     * Set listener for channel cleared events
     */
    fun setChannelClearedListener(listener: ChannelClearedListener) {
        channelClearedListener = listener
    }
    
    /**
     * Notify connection state changed
     */
    private fun notifyConnectionState(connected: Boolean) {
        handler.post {
            connectionStateListener?.onConnectionStateChanged(connected)
        }
    }
    
    /**
     * Set the backend URL
     */
    fun setBackendUrl(url: String) {
        backendUrl = url
        Log.d(TAG, "Backend URL set to: $url")
    }
    
    /**
     * Set listener for online messages
     */
    fun setMessageListener(listener: OnlineMessageListener) {
        messageListener = listener
    }
    
    /**
     * Connect to the backend as a gateway relay
     */
    fun connect() {
        Log.i(TAG, "════════════════════════════════════════")
        Log.i(TAG, "🌐 GATEWAY CONNECT REQUESTED")
        Log.i(TAG, "   URL: $backendUrl")
        
        if (isConnected) {
            Log.d(TAG, "Already connected")
            return
        }
        
        val userId = UserPreferences.getUserId()
        if (userId.isNullOrEmpty()) {
            Log.e(TAG, "❌ No user ID set - cannot connect")
            return
        }
        val userName = UserPreferences.getUserName() ?: "Android Relay"
        
        Log.i(TAG, "   User: $userName ($userId)")
        Log.i(TAG, "════════════════════════════════════════")
        
        val request = Request.Builder()
            .url(backendUrl)
            .build()
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "✅ WebSocket connected")
                isConnected = true
                
                // Authenticate
                val authPayload = JSONObject().apply {
                    put("type", "auth")
                    put("payload", JSONObject().apply {
                        put("userId", userId)
                        put("userName", userName)
                        put("isRelay", true)
                        put("relayId", userId)
                    })
                    put("timestamp", System.currentTimeMillis())
                }
                webSocket.send(authPayload.toString())
                
                // Note: We notify connected after auth_ok, not here
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }
            
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $reason")
                isConnected = false
                isAuthenticated = false
                notifyConnectionState(false)
            }
            
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $reason")
                isConnected = false
                isAuthenticated = false
                notifyConnectionState(false)
                scheduleReconnect()
            }
            
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket error: ${t.message}")
                isConnected = false
                isAuthenticated = false
                notifyConnectionState(false)
                scheduleReconnect()
            }
        })
    }
    
    /**
     * Disconnect from backend
     */
    fun disconnect() {
        reconnectRunnable?.let { handler.removeCallbacks(it) }
        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        isConnected = false
        isAuthenticated = false
        notifyConnectionState(false)
        Log.d(TAG, "Disconnected from gateway")
    }
    
    /**
     * Check if connected
     */
    fun isConnected(): Boolean = isConnected && isAuthenticated
    
    /**
     * Forward a mesh message to the backend
     */
    fun forwardMeshMessage(message: Message) {
        if (!isConnected || !isAuthenticated) {
            Log.d(TAG, "Not connected, skipping forward")
            return
        }
        
        val payload = JSONObject().apply {
            put("type", "gateway_message")
            put("payload", JSONObject().apply {
                put("id", message.id)
                put("channelId", message.channelId)
                put("senderId", message.senderId)
                put("senderName", message.senderName)
                put("content", message.content)
                put("timestamp", message.timestamp)
                put("origin", "mesh")
                message.recipientId?.let { put("recipientId", it) }
                message.recipientName?.let { put("recipientName", it) }
            })
            put("timestamp", System.currentTimeMillis())
        }
        
        webSocket?.send(payload.toString())
        Log.d(TAG, "📤 Forwarded mesh message to backend: ${message.id.take(8)}")
    }
    
    /**
     * Register as gateway relay (after auth)
     */
    private fun registerAsGateway() {
        val userId = UserPreferences.getUserId() ?: return
        val userName = UserPreferences.getUserName() ?: "Android Relay"
        
        val payload = JSONObject().apply {
            put("type", "gateway_connect")
            put("payload", JSONObject().apply {
                put("relayId", userId)
                put("name", "$userName's Phone")
                put("capabilities", JSONArray().apply {
                    put("ble_mesh")
                    put("broadcast")
                    put("receive")
                })
            })
            put("timestamp", System.currentTimeMillis())
        }
        
        webSocket?.send(payload.toString())
        Log.d(TAG, "📡 Registered as gateway relay")
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
                    registerAsGateway()
                }
                
                "auth_error" -> {
                    val error = payload?.optString("error") ?: "Unknown error"
                    Log.e(TAG, "❌ Auth error: $error")
                }
                
                "gateway_connect" -> {
                    Log.d(TAG, "✅ Gateway registered successfully")
                    notifyConnectionState(true)
                    // Fetch sync info to clear any channels that were cleared while offline
                    fetchSyncInfo()
                }
                
                "inject_message" -> {
                    // Backend wants us to inject a message into mesh
                    payload?.let { injectToMesh(it) }
                }
                
                "message" -> {
                    // Regular online message - optionally inject to mesh
                    payload?.let { handleOnlineMessage(it) }
                }
                
                "channel_created" -> {
                    payload?.let { handleChannelCreated(it) }
                }
                
                "channel_cleared" -> {
                    // Dashboard cleared this channel - clear local messages too
                    val channelId = payload?.optString("channelId")
                    if (channelId != null) {
                        Log.d(TAG, "🗑️ Channel cleared from dashboard: $channelId")
                        channelClearedListener?.onChannelCleared(channelId)
                    }
                }
                
                "presence_update" -> {
                    // Could update local presence display
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
     * Handle online message - inject to mesh if appropriate
     */
    private fun handleOnlineMessage(payload: JSONObject) {
        val origin = payload.optString("origin", "online")
        
        // Don't re-inject mesh messages
        if (origin == "mesh") return
        
        val message = Message(
            id = payload.getString("id"),
            channelId = payload.getString("channelId"),
            senderId = payload.getString("senderId"),
            senderName = payload.getString("senderName"),
            content = payload.getString("content"),
            timestamp = payload.getLong("timestamp"),
            recipientId = payload.optString("recipientId").takeIf { it.isNotEmpty() },
            recipientName = payload.optString("recipientName").takeIf { it.isNotEmpty() },
            isMeshOrigin = false
        )
        
        Log.d(TAG, "📨 Online message: ${message.content.take(30)}")
        messageListener?.onOnlineMessage(message)
    }
    
    /**
     * Inject a message from online to BLE mesh
     */
    private fun injectToMesh(payload: JSONObject) {
        val message = Message(
            id = payload.getString("id"),
            channelId = payload.getString("channelId"),
            senderId = payload.getString("senderId"),
            senderName = payload.getString("senderName"),
            content = payload.getString("content"),
            timestamp = payload.getLong("timestamp"),
            recipientId = payload.optString("recipientId").takeIf { it.isNotEmpty() },
            recipientName = payload.optString("recipientName").takeIf { it.isNotEmpty() },
            isMeshOrigin = false
        )
        
        Log.d(TAG, "🔄 Injecting to mesh: ${message.content.take(30)}")
        
        // Use BoundaryEngine to broadcast to mesh
        BoundaryEngine.injectFromGateway(message)
    }
    
    /**
     * Handle channel creation event from backend
     */
    private fun handleChannelCreated(payload: JSONObject) {
        try {
            val channelId = payload.getString("id")
            val channelName = payload.getString("name")
            val channelType = payload.getString("type")

            Log.i(TAG, "📢 Channel created: #$channelName ($channelId)")

            // Check if we already have this channel locally
            if (com.guildofsmiths.trademesh.data.BeaconRepository.getChannel("default", channelId) == null) {
                // Add to local repository
                val localChannel = com.guildofsmiths.trademesh.data.Channel(
                    id = channelId,
                    beaconId = "default",
                    name = channelName,
                    type = when (channelType) {
                        "broadcast" -> com.guildofsmiths.trademesh.data.ChannelType.BROADCAST
                        "group" -> com.guildofsmiths.trademesh.data.ChannelType.GROUP
                        "dm" -> com.guildofsmiths.trademesh.data.ChannelType.DM
                        else -> com.guildofsmiths.trademesh.data.ChannelType.GROUP
                    }
                )

                com.guildofsmiths.trademesh.data.BeaconRepository.addChannel("default", localChannel)
                Log.d(TAG, "✅ Added backend channel locally: #$channelName ($channelId)")
            }

            // Join the channel for message routing
            BoundaryEngine.joinChannel(channelId)

        } catch (e: Exception) {
            Log.e(TAG, "Error handling channel creation", e)
        }
    }

    /**
     * Fetch sync info from backend to clear channels that were cleared while offline.
     */
    private fun fetchSyncInfo() {
        // Make HTTP request to get sync info
        val httpUrl = backendUrl.replace("ws://", "http://").replace("wss://", "https://")
        val request = Request.Builder()
            .url("$httpUrl/api/sync")
            .get()
            .build()
        
        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                Log.e(TAG, "Failed to fetch sync info: ${e.message}")
            }
            
            override fun onResponse(call: okhttp3.Call, response: Response) {
                try {
                    val body = response.body?.string() ?: return
                    val json = JSONObject(body)
                    val channelClearedAt = json.optJSONObject("channelClearedAt")
                    
                    if (channelClearedAt != null) {
                        Log.d(TAG, "📥 Received sync info: ${channelClearedAt.length()} channels with clear timestamps")
                        
                        // Notify listener for each cleared channel
                        val keys = channelClearedAt.keys()
                        while (keys.hasNext()) {
                            val channelId = keys.next()
                            val clearedAt = channelClearedAt.getLong(channelId)
                            Log.d(TAG, "🗑️ Channel $channelId was cleared at $clearedAt")
                            
                            // Notify to clear messages older than clearedAt
                            handler.post {
                                syncClearedListener?.onSyncCleared(channelId, clearedAt)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing sync info: ${e.message}")
                }
            }
        })
    }
    
    // Sync cleared listener (clears messages older than timestamp)
    interface SyncClearedListener {
        fun onSyncCleared(channelId: String, clearedAtTimestamp: Long)
    }
    private var syncClearedListener: SyncClearedListener? = null
    
    /**
     * Set listener for sync clear events
     */
    fun setSyncClearedListener(listener: SyncClearedListener) {
        syncClearedListener = listener
    }

    /**
     * Create a channel via backend API
     * Note: This works via HTTP even without WebSocket connection
     */
    fun createChannel(name: String, type: String, memberIds: List<String>? = null, callback: (JSONObject?, Exception?) -> Unit) {
        // HTTP API works without WebSocket connection
        val httpUrl = backendUrl.replace("ws://", "http://").replace("wss://", "https://")
        val jsonBody = JSONObject().apply {
            put("name", name)
            put("type", type)
            if (memberIds != null) {
                put("memberIds", JSONArray(memberIds))
            }
        }

        val requestBody = jsonBody.toString().toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$httpUrl/api/channels")
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .addHeader("x-user-id", UserPreferences.getUserId())
            .addHeader("x-user-name", UserPreferences.getDisplayName() ?: "Unknown")
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                Log.e(TAG, "Failed to create channel: ${e.message}")
                callback(null, e)
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {
                try {
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (body != null) {
                            val json = JSONObject(body)
                            Log.i(TAG, "✅ Channel created: ${json.getString("name")}")
                            callback(json, null)
                        } else {
                            callback(null, Exception("Empty response"))
                        }
                    } else {
                        val errorBody = response.body?.string() ?: "Unknown error"
                        Log.e(TAG, "Channel creation failed: ${response.code} - $errorBody")
                        callback(null, Exception("HTTP ${response.code}: $errorBody"))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing channel creation response: ${e.message}")
                    callback(null, e)
                }
            }
        })
    }

    /**
     * Fetch all channels from backend
     * Note: This works via HTTP even without WebSocket connection
     */
    fun fetchChannels(callback: (JSONArray?, Exception?) -> Unit) {
        // HTTP API works without WebSocket connection
        val httpUrl = backendUrl.replace("ws://", "http://").replace("wss://", "https://")
        val request = Request.Builder()
            .url("$httpUrl/api/channels")
            .get()
            .build()

        client.newCall(request).enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                Log.e(TAG, "Failed to fetch channels: ${e.message}")
                callback(null, e)
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {
                try {
                    if (response.isSuccessful) {
                        val body = response.body?.string()
                        if (body != null) {
                            val json = JSONArray(body)
                            Log.i(TAG, "📥 Fetched ${json.length()} channels from backend")
                            callback(json, null)
                        } else {
                            callback(null, Exception("Empty response"))
                        }
                    } else {
                        val errorBody = response.body?.string() ?: "Unknown error"
                        Log.e(TAG, "Channel fetch failed: ${response.code} - $errorBody")
                        callback(null, Exception("HTTP ${response.code}: $errorBody"))
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing channel fetch response: ${e.message}")
                    callback(null, e)
                }
            }
        })
    }
    
    /**
     * Schedule reconnection attempt
     */
    private fun scheduleReconnect() {
        reconnectRunnable?.let { handler.removeCallbacks(it) }
        reconnectRunnable = Runnable {
            Log.d(TAG, "🔄 Attempting reconnect...")
            connect()
        }
        handler.postDelayed(reconnectRunnable!!, RECONNECT_DELAY_MS)
    }
}
