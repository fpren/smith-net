package com.guildofsmiths.trademesh.service

import android.content.Context
import android.util.Log
import com.guildofsmiths.trademesh.data.Message
import com.guildofsmiths.trademesh.data.MessageRepository
import com.guildofsmiths.trademesh.data.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * TelegramBridge - Reliable online messaging via Telegram CLI bridge
 *
 * Connects to a telegram-cli bridge server for cross-device communication.
 * Falls back gracefully when bridge is unavailable (messages queue locally).
 *
 * Bridge Protocol:
 * - POST /send    → Send message to Telegram chat/group
 * - GET  /poll    → Long-poll for new messages
 * - WS   /stream  → WebSocket for real-time message stream
 * - POST /status  → Update device presence
 *
 * Message Format (JSON):
 * {
 *   "id": "uuid",
 *   "channel": "smithnet-general",
 *   "sender_id": "device-id",
 *   "sender_name": "Display Name",
 *   "content": "message text",
 *   "timestamp": 1234567890,
 *   "media_type": null | "image" | "voice" | "file",
 *   "media_url": null | "telegram-file-id"
 * }
 */
object TelegramBridge {

    private const val TAG = "TelegramBridge"

    // Connection states
    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        RECONNECTING,
        ERROR
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Configuration
    private var bridgeUrl: String? = null
    private var bridgeToken: String? = null
    private var telegramChatId: String? = null  // Target Telegram group/chat

    // State
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    // Offline queue for messages when bridge unavailable
    private val offlineQueue = mutableListOf<QueuedMessage>()
    private val seenMessageIds = mutableSetOf<String>()

    // HTTP client
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    // WebSocket connection
    private var webSocket: WebSocket? = null
    private var pollJob: Job? = null
    private var reconnectJob: Job? = null

    // Reconnection parameters
    private var reconnectAttempts = 0
    private val maxReconnectAttempts = 10
    private val baseReconnectDelayMs = 1000L

    // ════════════════════════════════════════════════════════════════════
    // DATA CLASSES
    // ════════════════════════════════════════════════════════════════════

    data class QueuedMessage(
        val id: String,
        val channelId: String,
        val content: String,
        val timestamp: Long,
        val mediaType: String? = null,
        val mediaPath: String? = null,
        val retryCount: Int = 0
    )

    data class BridgeConfig(
        val url: String,
        val token: String,
        val chatId: String,
        val useWebSocket: Boolean = true
    )

    // ════════════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ════════════════════════════════════════════════════════════════════

    /**
     * Configure the Telegram bridge connection.
     *
     * @param url Bridge server URL (e.g., "http://192.168.1.100:8080" or "https://bridge.example.com")
     * @param token Authentication token for the bridge
     * @param chatId Telegram chat/group ID to sync with
     */
    fun configure(url: String, token: String, chatId: String) {
        bridgeUrl = url.trimEnd('/')
        bridgeToken = token
        telegramChatId = chatId
        Log.i(TAG, "Configured bridge: $url, chat: $chatId")
    }

    /**
     * Configure from saved preferences.
     */
    fun configureFromPreferences(context: Context) {
        val prefs = context.getSharedPreferences("telegram_bridge", Context.MODE_PRIVATE)
        val url = prefs.getString("bridge_url", null)
        val token = prefs.getString("bridge_token", null)
        val chatId = prefs.getString("chat_id", null)

        if (url != null && token != null && chatId != null) {
            configure(url, token, chatId)
        }
    }

    /**
     * Save configuration to preferences.
     */
    fun saveConfiguration(context: Context, config: BridgeConfig) {
        context.getSharedPreferences("telegram_bridge", Context.MODE_PRIVATE)
            .edit()
            .putString("bridge_url", config.url)
            .putString("bridge_token", config.token)
            .putString("chat_id", config.chatId)
            .putBoolean("use_websocket", config.useWebSocket)
            .apply()

        configure(config.url, config.token, config.chatId)
    }

    /**
     * Check if bridge is configured.
     */
    fun isConfigured(): Boolean {
        return bridgeUrl != null && bridgeToken != null && telegramChatId != null
    }

    // ════════════════════════════════════════════════════════════════════
    // CONNECTION MANAGEMENT
    // ════════════════════════════════════════════════════════════════════

    /**
     * Connect to the Telegram bridge.
     * Tries WebSocket first, falls back to long-polling.
     */
    fun connect() {
        if (!isConfigured()) {
            Log.w(TAG, "Bridge not configured - cannot connect")
            _lastError.value = "Bridge not configured"
            return
        }

        if (_connectionState.value == ConnectionState.CONNECTED ||
            _connectionState.value == ConnectionState.CONNECTING) {
            Log.d(TAG, "Already connected or connecting")
            return
        }

        _connectionState.value = ConnectionState.CONNECTING
        Log.i(TAG, "Connecting to Telegram bridge: $bridgeUrl")

        scope.launch {
            try {
                // Try WebSocket connection first
                if (connectWebSocket()) {
                    _connectionState.value = ConnectionState.CONNECTED
                    _isConnected.value = true
                    reconnectAttempts = 0
                    Log.i(TAG, "✓ Connected via WebSocket")

                    // Sync any queued messages
                    syncOfflineQueue()
                } else {
                    // Fall back to long-polling
                    Log.w(TAG, "WebSocket failed, falling back to polling")
                    startPolling()
                    _connectionState.value = ConnectionState.CONNECTED
                    _isConnected.value = true
                    reconnectAttempts = 0

                    syncOfflineQueue()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Connection failed", e)
                _lastError.value = e.message
                _connectionState.value = ConnectionState.ERROR
                _isConnected.value = false
                scheduleReconnect()
            }
        }
    }

    /**
     * Disconnect from the bridge.
     */
    fun disconnect() {
        Log.i(TAG, "Disconnecting from Telegram bridge")

        webSocket?.close(1000, "Client disconnect")
        webSocket = null
        pollJob?.cancel()
        pollJob = null
        reconnectJob?.cancel()
        reconnectJob = null

        _connectionState.value = ConnectionState.DISCONNECTED
        _isConnected.value = false
    }

    /**
     * Connect via WebSocket for real-time updates.
     */
    private suspend fun connectWebSocket(): Boolean = withContext(Dispatchers.IO) {
        val url = bridgeUrl ?: return@withContext false
        val wsUrl = url.replace("http://", "ws://").replace("https://", "wss://") + "/stream"

        try {
            val request = Request.Builder()
                .url(wsUrl)
                .header("Authorization", "Bearer $bridgeToken")
                .header("X-Device-Id", UserPreferences.getUserId())
                .header("X-Chat-Id", telegramChatId ?: "")
                .build()

            var connected = false
            val latch = java.util.concurrent.CountDownLatch(1)

            webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    Log.i(TAG, "WebSocket opened")
                    connected = true
                    latch.countDown()
                }

                override fun onMessage(webSocket: WebSocket, text: String) {
                    handleIncomingMessage(text)
                }

                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    Log.w(TAG, "WebSocket closing: $code $reason")
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    Log.w(TAG, "WebSocket closed: $code $reason")
                    _isConnected.value = false
                    _connectionState.value = ConnectionState.DISCONNECTED
                    if (code != 1000) {
                        scheduleReconnect()
                    }
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                    Log.e(TAG, "WebSocket failure", t)
                    _isConnected.value = false
                    _connectionState.value = ConnectionState.ERROR
                    _lastError.value = t.message
                    latch.countDown()
                    scheduleReconnect()
                }
            })

            // Wait for connection with timeout
            latch.await(5, TimeUnit.SECONDS)
            return@withContext connected

        } catch (e: Exception) {
            Log.e(TAG, "WebSocket connection failed", e)
            return@withContext false
        }
    }

    /**
     * Start long-polling for messages (fallback when WebSocket unavailable).
     */
    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            var lastTimestamp = System.currentTimeMillis()

            while (isActive) {
                try {
                    val messages = pollMessages(lastTimestamp)
                    messages.forEach { msg ->
                        handleIncomingMessage(msg)
                        val ts = JSONObject(msg).optLong("timestamp", 0)
                        if (ts > lastTimestamp) lastTimestamp = ts
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Poll failed: ${e.message}")
                    _lastError.value = e.message
                }

                delay(2000) // Poll every 2 seconds
            }
        }
    }

    /**
     * Poll for new messages since timestamp.
     */
    private suspend fun pollMessages(since: Long): List<String> = withContext(Dispatchers.IO) {
        val url = bridgeUrl ?: return@withContext emptyList()

        try {
            val request = Request.Builder()
                .url("$url/poll?since=$since&chat_id=$telegramChatId")
                .header("Authorization", "Bearer $bridgeToken")
                .header("X-Device-Id", UserPreferences.getUserId())
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "Poll failed: ${response.code}")
                return@withContext emptyList()
            }

            val body = response.body?.string() ?: return@withContext emptyList()
            val json = JSONObject(body)
            val messagesArray = json.optJSONArray("messages") ?: return@withContext emptyList()

            val messages = mutableListOf<String>()
            for (i in 0 until messagesArray.length()) {
                messages.add(messagesArray.getJSONObject(i).toString())
            }

            return@withContext messages

        } catch (e: Exception) {
            Log.w(TAG, "Poll request failed", e)
            return@withContext emptyList()
        }
    }

    /**
     * Schedule reconnection with exponential backoff.
     */
    private fun scheduleReconnect() {
        if (reconnectAttempts >= maxReconnectAttempts) {
            Log.w(TAG, "Max reconnect attempts reached")
            _connectionState.value = ConnectionState.ERROR
            _lastError.value = "Connection failed after $maxReconnectAttempts attempts"
            return
        }

        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            _connectionState.value = ConnectionState.RECONNECTING
            val delay = baseReconnectDelayMs * (1 shl reconnectAttempts.coerceAtMost(6))
            Log.i(TAG, "Reconnecting in ${delay}ms (attempt ${reconnectAttempts + 1})")

            delay(delay)
            reconnectAttempts++
            connect()
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // MESSAGING
    // ════════════════════════════════════════════════════════════════════

    /**
     * Send a message via the Telegram bridge.
     * Queues locally if bridge is unavailable.
     */
    fun sendMessage(message: Message) {
        Log.i(TAG, "📤 sendMessage: ${message.content.take(30)}")

        if (!_isConnected.value) {
            Log.w(TAG, "Not connected, queuing message")
            queueMessage(message)
            return
        }

        scope.launch {
            try {
                val success = sendToTelegram(message)
                if (!success) {
                    Log.w(TAG, "Send failed, queuing message")
                    queueMessage(message)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Send error", e)
                queueMessage(message)
            }
        }
    }

    /**
     * Send message to Telegram via bridge API.
     */
    private suspend fun sendToTelegram(message: Message): Boolean = withContext(Dispatchers.IO) {
        val url = bridgeUrl ?: return@withContext false

        try {
            val payload = JSONObject().apply {
                put("id", message.id)
                put("channel", message.channelId)
                put("sender_id", message.senderId)
                put("sender_name", message.senderName)
                put("content", message.content)
                put("timestamp", message.timestamp)
                put("chat_id", telegramChatId)
                put("is_mesh_origin", message.isMeshOrigin)

                // Media handling
                if (message.hasMedia() && message.media != null) {
                    put("media_type", message.mediaType.name.lowercase())
                    put("media_url", message.media?.remotePath)
                    put("media_filename", message.media?.fileName)
                }
            }

            val request = Request.Builder()
                .url("$url/send")
                .header("Authorization", "Bearer $bridgeToken")
                .header("Content-Type", "application/json")
                .post(payload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val response = httpClient.newCall(request).execute()
            val success = response.isSuccessful

            if (success) {
                Log.i(TAG, "✓ Message sent to Telegram")
            } else {
                Log.w(TAG, "Send failed: ${response.code} ${response.body?.string()}")
            }

            return@withContext success

        } catch (e: Exception) {
            Log.e(TAG, "Send request failed", e)
            return@withContext false
        }
    }

    /**
     * Handle incoming message from bridge.
     */
    private fun handleIncomingMessage(jsonStr: String) {
        try {
            val json = JSONObject(jsonStr)

            // Skip if we've already seen this message
            val msgId = json.optString("id", UUID.randomUUID().toString())
            if (seenMessageIds.contains(msgId)) {
                return
            }
            seenMessageIds.add(msgId)

            // Skip our own messages
            val senderId = json.optString("sender_id", "")
            val myUserId = UserPreferences.getUserId()
            if (senderId == myUserId) {
                // Check if we already have it locally
                if (MessageRepository.allMessages.value.any { it.id == msgId }) {
                    return
                }
            }

            // Get the current active channel from repository
            // Route bridge messages to active channel for cross-device sync
            val activeChannel = com.guildofsmiths.trademesh.data.BeaconRepository.getActiveChannel()
            val messageChannelId = activeChannel?.id ?: json.optString("channel", "general")

            // Parse message
            val message = Message(
                id = msgId,
                channelId = messageChannelId,
                beaconId = "default",
                senderId = senderId,
                senderName = json.optString("sender_name", "Unknown"),
                content = json.optString("content", ""),
                timestamp = json.optLong("timestamp", System.currentTimeMillis()),
                isMeshOrigin = false  // Mark as online origin (came from bridge)
            )

            Log.i(TAG, "📨 Received from Telegram: ${message.senderName}: ${message.content.take(30)} -> channel: $messageChannelId")

            // Add to repository (handles dedup and notifications)
            MessageRepository.addMessage(message)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse incoming message", e)
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // OFFLINE QUEUE
    // ════════════════════════════════════════════════════════════════════

    /**
     * Queue message for later sync.
     */
    private fun queueMessage(message: Message) {
        synchronized(offlineQueue) {
            offlineQueue.add(QueuedMessage(
                id = message.id,
                channelId = message.channelId,
                content = message.content,
                timestamp = message.timestamp,
                mediaType = message.mediaType.name.takeIf { message.hasMedia() },
                mediaPath = message.media?.remotePath
            ))
        }
        Log.d(TAG, "Message queued (${offlineQueue.size} pending)")
    }

    /**
     * Sync offline queue when connection restored.
     */
    private suspend fun syncOfflineQueue() {
        val toSync: List<QueuedMessage>
        synchronized(offlineQueue) {
            toSync = offlineQueue.toList()
            offlineQueue.clear()
        }

        if (toSync.isEmpty()) return

        Log.i(TAG, "Syncing ${toSync.size} queued messages...")

        for (queued in toSync) {
            try {
                val message = Message(
                    id = queued.id,
                    channelId = queued.channelId,
                    beaconId = "default",
                    senderId = UserPreferences.getUserId(),
                    senderName = UserPreferences.getUserName() ?: "Unknown",
                    content = queued.content,
                    timestamp = queued.timestamp,
                    isMeshOrigin = false
                )

                val success = sendToTelegram(message)
                if (!success && queued.retryCount < 3) {
                    // Re-queue with incremented retry count
                    synchronized(offlineQueue) {
                        offlineQueue.add(queued.copy(retryCount = queued.retryCount + 1))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to sync message ${queued.id}", e)
            }
        }
    }

    /**
     * Get count of pending messages.
     */
    fun getPendingCount(): Int = synchronized(offlineQueue) { offlineQueue.size }

    // ════════════════════════════════════════════════════════════════════
    // STATUS & UTILITIES
    // ════════════════════════════════════════════════════════════════════

    /**
     * Send presence/status update to bridge.
     */
    fun updatePresence(status: String = "online") {
        if (!_isConnected.value) return

        scope.launch {
            try {
                val url = bridgeUrl ?: return@launch
                val payload = JSONObject().apply {
                    put("device_id", UserPreferences.getUserId())
                    put("device_name", UserPreferences.getUserName())
                    put("status", status)
                    put("timestamp", System.currentTimeMillis())
                    put("chat_id", telegramChatId)
                }

                val request = Request.Builder()
                    .url("$url/status")
                    .header("Authorization", "Bearer $bridgeToken")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                httpClient.newCall(request).execute()
            } catch (e: Exception) {
                Log.w(TAG, "Presence update failed", e)
            }
        }
    }

    /**
     * Test bridge connection.
     */
    suspend fun testConnection(): Result<String> = withContext(Dispatchers.IO) {
        val url = bridgeUrl ?: return@withContext Result.failure(Exception("Not configured"))

        try {
            val request = Request.Builder()
                .url("$url/health")
                .header("Authorization", "Bearer $bridgeToken")
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            if (response.isSuccessful) {
                Result.success("Bridge is healthy")
            } else {
                Result.failure(Exception("Bridge returned ${response.code}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
