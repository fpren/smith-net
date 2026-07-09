package com.guildofsmiths.trademesh.service

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.guildofsmiths.trademesh.BuildConfig
import com.guildofsmiths.trademesh.data.MediaAttachment
import com.guildofsmiths.trademesh.data.MediaType
import com.guildofsmiths.trademesh.data.Message
import com.guildofsmiths.trademesh.data.MessageRepository
import com.guildofsmiths.trademesh.data.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

enum class ConnectionMode { ONLINE, MESH, OFFLINE }

/**
 * ChatManager: Online chat via WebSocket + HTTP API.
 *
 * - WebSocket for receiving real-time messages
 * - HTTP API for sending messages
 * - Works without gateway/mesh - pure online mode
 */
object ChatManager {
    
    private const val TAG = "ChatManager"
    
    // Backend URLs — primary tried first, fallback used after a failed connect.
    // Values originate from BuildConfig (see app/build.gradle.kts defaultConfig / buildTypes).
    private val candidateUrls: List<String> = listOf(
        BuildConfig.BACKEND_URL_PRIMARY,
        BuildConfig.BACKEND_URL_FALLBACK,
    ).distinct()
    private var activeUrlIndex = 0
    private var httpUrl = toHttp(candidateUrls[0])
    private var wsUrl = toWs(candidateUrls[0])

    private fun toHttp(u: String) = u.replace("ws://", "http://").replace("wss://", "https://")
    private fun toWs(u: String) = u.replace("http://", "ws://").replace("https://", "wss://")

    /** Rotate to the next configured URL on the next connect. */
    private fun rotateUrl() {
        if (candidateUrls.size <= 1) return
        activeUrlIndex = (activeUrlIndex + 1) % candidateUrls.size
        val next = candidateUrls[activeUrlIndex]
        httpUrl = toHttp(next)
        wsUrl = toWs(next)
        Log.i(TAG, "Rotating backend URL → $httpUrl")
    }
    
    // Shared authenticated client (adds Authorization: Bearer <token>). The bare
    // OkHttpClient sent no token, so JWT-guarded routes like /api/messages/inject
    // returned 401 on a real device.
    private val httpClient = HttpClientFactory.client
    private val wsClient = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    
    private var webSocket: WebSocket? = null
    private val handler = Handler(Looper.getMainLooper())
    private var reconnectRunnable: Runnable? = null
    private const val RECONNECT_DELAY_MS = 5000L

    // Background scope for one-off suspend work (session refresh) kicked off
    // from the (non-suspend) WebSocketListener callbacks.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Task 3 (401 -> refreshSession wiring): a rejected-JWT upgrade would
    // otherwise reconnect-loop forever with the same stale token. Guard the
    // refresh attempt so a dead session doesn't hot-loop refresh calls --
    // at most one attempt per this window, regardless of how many reconnect
    // cycles land inside it.
    @Volatile
    private var lastAuthRefreshAtMs = 0L
    private const val AUTH_REFRESH_MIN_INTERVAL_MS = 60_000L

    /** Connection state */
    private var isConnected = false
    private var isAuthenticated = false

    private val _connectionMode = MutableStateFlow(ConnectionMode.OFFLINE)
    val connectionMode: StateFlow<ConnectionMode> = _connectionMode.asStateFlow()

    // Message listener
    interface OnMessageListener {
        fun onMessageReceived(message: Message)
        fun onChannelCleared(channelId: String)
    }
    private var messageListener: OnMessageListener? = null
    
    fun setMessageListener(listener: OnMessageListener) {
        messageListener = listener
    }

    interface OnTypingListener {
        fun onTypingStarted(channelId: String, userId: String, userName: String)
        fun onTypingStopped(channelId: String, userId: String)
    }

    interface OnReadReceiptListener {
        fun onMessageRead(messageId: String, readBy: String, readAt: Long)
    }

    private var typingListener: OnTypingListener? = null
    private var readReceiptListener: OnReadReceiptListener? = null

    fun setTypingListener(listener: OnTypingListener?) { typingListener = listener }
    fun setReadReceiptListener(listener: OnReadReceiptListener?) { readReceiptListener = listener }

    /**
     * Set the backend URL
     */
    fun setBackendUrl(url: String) {
        // Handle both http and ws URLs
        httpUrl = toHttp(url)
        wsUrl = toWs(url)
        Log.d(TAG, "Backend URL set to: HTTP=$httpUrl, WS=$wsUrl")
    }

    /** Get the current HTTP base URL (e.g. for reconciliation or REST calls). */
    fun currentBackendHttpUrl(): String = httpUrl
    
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
        // The backend (wsAuth) authenticates the JWT on the upgrade request, the
        // same way the browser rides its cookie. Native clients send the access
        // token as a Bearer header — without it the upgrade is denied (401).
        val token = AuthService.getAccessToken()
        if (token.isNullOrBlank()) {
            Log.e(TAG, "❌ No access token - cannot connect")
            return
        }

        Log.i(TAG, "🌐 Connecting to online chat: $wsUrl")

        val request = Request.Builder()
            .url(wsUrl)
            .addHeader("Authorization", "Bearer $token")
            .build()

        webSocket = wsClient.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                // Server validated the JWT on the upgrade and sends `auth_ok`
                // proactively, so no in-band auth message is needed here.
                Log.d(TAG, "✅ WebSocket connected")
                isConnected = true
            }
            
            override fun onMessage(webSocket: WebSocket, text: String) {
                handleMessage(text)
            }
            
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closing: $reason")
                isConnected = false
                isAuthenticated = false
                _connectionMode.value = ConnectionMode.OFFLINE
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket closed: $reason")
                isConnected = false
                isAuthenticated = false
                _connectionMode.value = ConnectionMode.OFFLINE
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket error: ${t.message}")
                isConnected = false
                isAuthenticated = false
                _connectionMode.value = ConnectionMode.OFFLINE

                // The upgrade request was rejected on auth grounds (expired/invalid
                // JWT) rather than a network/transient failure -- reconnecting with
                // the same token would just loop forever. Kick off one guarded
                // session refresh before the normal reconnect cadence resumes; the
                // next connect() call will read whatever token AuthService.getAccessToken()
                // returns, refreshed or not. Every other failure keeps today's
                // behavior exactly.
                val code = response?.code
                if (code == 401 || code == 403) {
                    maybeRefreshAuthSession(code)
                }

                // Try the fallback URL next time — keeps Hetzner primary,
                // flips to Mac Mini LAN (or back) after a failed attempt.
                rotateUrl()
                scheduleReconnect()
            }
        })
    }

    /**
     * Fire a guarded, one-shot session refresh after a WS upgrade rejection
     * (401/403). Guarded to at most one attempt per [AUTH_REFRESH_MIN_INTERVAL_MS]
     * so a dead/revoked session doesn't hammer the refresh endpoint once per
     * reconnect cycle. Refreshes via [AuthService] -- not [SupabaseAuth] --
     * because [connect] reads its Bearer token from `AuthService.getAccessToken()`;
     * the two auth stacks keep independent token stores.
     */
    private fun maybeRefreshAuthSession(httpCode: Int) {
        val now = System.currentTimeMillis()
        if (now - lastAuthRefreshAtMs < AUTH_REFRESH_MIN_INTERVAL_MS) {
            Log.d(TAG, "Skipping session refresh (guarded, last attempt too recent) for HTTP $httpCode")
            return
        }
        lastAuthRefreshAtMs = now
        Log.w(TAG, "WS upgrade rejected (HTTP $httpCode) — refreshing session before reconnect")
        scope.launch {
            val refreshed = try {
                AuthService.refreshToken()
            } catch (e: Exception) {
                Log.w(TAG, "Session refresh threw: ${e.message}")
                false
            }
            if (!refreshed) {
                Log.w(TAG, "[x] session refresh failed")
            }
        }
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
                    _connectionMode.value = ConnectionMode.ONLINE
                    // auth_ok carries the caller's full channel list (server-side
                    // listForUser, which includes cross-org DMs). Upsert them so
                    // the Comm screen is correct the moment the WS authenticates,
                    // independent of the REST sync.
                    payload?.optJSONArray("channels")?.let { arr ->
                        for (i in 0 until arr.length()) {
                            arr.optJSONObject(i)?.let { handleChannelUpsert(it) }
                        }
                    }
                    // Pull anything missed while we were disconnected (relay cold start,
                    // or app was force-stopped while peers kept messaging online).
                    com.guildofsmiths.trademesh.engine.BoundaryEngine.reconcileOnAuth()
                    // Auto-register as mesh relay if we have BLE — the server will
                    // forward online messages to us so we can re-advertise for
                    // nearby mesh-only peers.
                    registerAsMeshRelay()
                }

                "inject_message" -> {
                    // Server is asking us (a registered relay) to rebroadcast this
                    // message to nearby BLE peers.
                    payload?.let { handleInjectMessage(it) }
                }
                
                "message" -> {
                    // Incoming message from another user
                    payload?.let { handleIncomingMessage(it) }
                }

                "channel_created", "channel_updated" -> {
                    // A channel we're a member of was created/updated server-side
                    // (e.g. a cross-org DM someone opened with us). Add it locally
                    // so it shows in the Comm list without waiting for a resync.
                    payload?.let { handleChannelUpsert(it) }
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
                
                "typing_start" -> {
                    val chId = json.optString("channelId")
                    val uId = json.optString("userId")
                    val uName = json.optString("userName")
                    handler.post { typingListener?.onTypingStarted(chId, uId, uName) }
                }

                "typing_stop" -> {
                    val chId = json.optString("channelId")
                    val uId = json.optString("userId")
                    handler.post { typingListener?.onTypingStopped(chId, uId) }
                }

                "message_read" -> {
                    val msgId = json.optString("messageId")
                    val readBy = json.optString("readBy")
                    val readAt = json.optLong("readAt", System.currentTimeMillis())
                    handler.post { readReceiptListener?.onMessageRead(msgId, readBy, readAt) }
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
     * Add (or refresh) a channel pushed from the backend via a
     * channel_created / channel_updated event, then join it for routing.
     * Mirrors GatewayClient.handleChannelCreated for the online (non-relay) path.
     */
    private fun handleChannelUpsert(payload: JSONObject) {
        try {
            val channelId = payload.getString("id")
            val channelName = payload.getString("name")
            val channelType = payload.getString("type")
            Log.i(TAG, "📢 Channel upserted: #$channelName ($channelId)")
            handler.post {
                if (com.guildofsmiths.trademesh.data.BeaconRepository.getChannel("default", channelId) == null) {
                    com.guildofsmiths.trademesh.data.BeaconRepository.addChannel(
                        "default",
                        com.guildofsmiths.trademesh.data.Channel(
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
                    )
                    Log.i(TAG, "✅ Added backend channel locally: #$channelName ($channelId)")
                }
                com.guildofsmiths.trademesh.engine.BoundaryEngine.joinChannel(channelId)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling channel upsert", e)
        }
    }

    /**
     * Opportunistically upgrade this WS client to a mesh relay once the
     * MeshService is up and BLE advertising is available. Server then
     * forwards 'inject_message' frames for us to re-advertise.
     */
    private fun registerAsMeshRelay() {
        val meshRunning = com.guildofsmiths.trademesh.engine.BoundaryEngine.isMeshAvailable()
        if (!meshRunning) {
            Log.d(TAG, "Skipping relay registration - MeshService not running")
            return
        }
        val relayId = UserPreferences.getUserId().ifBlank { return }
        val payload = JSONObject().apply {
            put("type", "gateway_connect")
            put("payload", JSONObject().apply {
                put("relayId", relayId)
                put("name", UserPreferences.getUserName() ?: "phone-relay")
                put("capabilities", org.json.JSONArray(listOf("ble")))
            })
            put("timestamp", System.currentTimeMillis())
        }
        webSocket?.send(payload.toString())
        Log.i(TAG, "🔌 Registered as mesh relay: $relayId")
    }

    /**
     * Server is telling us to re-advertise a message over BLE so mesh-only
     * peers can pick it up. Build a Message and hand it to BoundaryEngine.
     */
    private fun handleInjectMessage(payload: JSONObject) {
        try {
            val senderId = payload.optString("senderId")
            val myUserId = UserPreferences.getUserId()
            // Don't re-advertise our own outgoing message — we already broadcast it.
            if (senderId == myUserId) return
            val channelId = payload.optString("channelId").ifBlank { "general" }
            val normalizedChannelId = if (channelId.contains("-")) "general" else channelId
            val msg = com.guildofsmiths.trademesh.data.Message(
                id = payload.getString("id"),
                channelId = normalizedChannelId,
                senderId = senderId,
                senderName = payload.optString("senderName"),
                content = payload.optString("content"),
                timestamp = payload.optLong("timestamp", System.currentTimeMillis()),
                isMeshOrigin = false
            )
            Log.i(TAG, "📡 Relay: re-advertising ${msg.id.take(8)} via BLE")
            com.guildofsmiths.trademesh.engine.BoundaryEngine.relayToMesh(msg)
        } catch (e: Exception) {
            Log.e(TAG, "handleInjectMessage failed", e)
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
        
        // Keep the real channelId. We now sync real backend channels (DMs,
        // groups) by their UUID, so the old "normalize any UUID to general"
        // hack mis-routed every incoming DM/group message into #general.
        val channelId = payload.getString("channelId")

        // Backend stamps an optional `media` object on messages that carry an
        // attachment (see backend/src/types.ts Message.media):
        // { type, url, filename?, mimeType?, size?, duration?, thumbnailUrl? }.
        // Map it into Android's MediaAttachment -- the server URL only ever
        // lands in `remotePath` here since this message was never on this
        // device (nothing to put in `localPath`).
        val media = payload.optJSONObject("media")?.let { parseIncomingMedia(it) }
        val mediaType = media?.type ?: MediaType.TEXT

        val message = Message(
            id = payload.getString("id"),
            channelId = channelId,
            senderId = senderId,
            senderName = payload.getString("senderName"),
            content = payload.getString("content"),
            timestamp = payload.getLong("timestamp"),
            recipientId = payload.optString("recipientId").takeIf { it.isNotEmpty() },
            recipientName = payload.optString("recipientName").takeIf { it.isNotEmpty() },
            isMeshOrigin = false,
            mediaType = mediaType,
            media = media
        )
        
        Log.d(TAG, "📨 Received online message: ${message.content.take(30)} from ${message.senderName}")

        // Add to repository
        handler.post {
            MessageRepository.addMessage(message)
            messageListener?.onMessageReceived(message)
        }
    }

    /**
     * Parse a WS `media` JSON object (backend/src/types.ts Message.media)
     * into Android's MediaAttachment. `url` -> `remotePath`; there's no
     * `localPath` because this attachment arrived from another device.
     */
    private fun parseIncomingMedia(json: JSONObject): MediaAttachment {
        return MediaAttachment(
            type = mediaTypeFromWireString(json.optString("type")),
            remotePath = json.optString("url").ifBlank { null },
            mimeType = json.optString("mimeType").ifBlank { null },
            fileName = json.optString("filename").ifBlank { null },
            fileSize = json.optLong("size", 0L),
            duration = json.optLong("duration", 0L)
        )
    }

    /** Map the backend's lowercase media `type` string to our MediaType enum. */
    private fun mediaTypeFromWireString(type: String): MediaType {
        return when (type) {
            "image" -> MediaType.IMAGE
            "voice" -> MediaType.VOICE
            "video" -> MediaType.VIDEO
            "file" -> MediaType.FILE
            else -> MediaType.FILE
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
        
        val userId = UserPreferences.getUserId().ifBlank { "unknown" }
        val userName = UserPreferences.getUserName()?.ifBlank { null } ?: "Unknown"

        if (userId == "unknown" || userId == "system") {
            Log.e(TAG, "❌ Refusing to send - invalid userId '$userId'. Re-onboard.")
            callback?.invoke(false)
            return
        }

        val json = JSONObject().apply {
            put("id", message.id)
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
    
    fun sendTypingStart(channelId: String) {
        val userId = UserPreferences.getUserId() ?: return
        val userName = UserPreferences.getUserName() ?: return
        webSocket?.send(JSONObject().apply {
            put("type", "typing_start")
            put("channelId", channelId)
            put("userId", userId)
            put("userName", userName)
        }.toString())
    }

    fun sendTypingStop(channelId: String) {
        val userId = UserPreferences.getUserId() ?: return
        webSocket?.send(JSONObject().apply {
            put("type", "typing_stop")
            put("channelId", channelId)
            put("userId", userId)
        }.toString())
    }

    fun sendReadReceipt(messageId: String, channelId: String) {
        val userId = UserPreferences.getUserId() ?: return
        webSocket?.send(JSONObject().apply {
            put("type", "message_read")
            put("messageId", messageId)
            put("channelId", channelId)
            put("readBy", userId)
            put("readAt", System.currentTimeMillis())
        }.toString())
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
