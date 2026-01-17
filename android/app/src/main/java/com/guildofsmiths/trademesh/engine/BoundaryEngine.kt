package com.guildofsmiths.trademesh.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.guildofsmiths.trademesh.data.BeaconRepository
import com.guildofsmiths.trademesh.data.Channel
import com.guildofsmiths.trademesh.data.ChannelType
import com.guildofsmiths.trademesh.data.CordEntry
import com.guildofsmiths.trademesh.data.CordMessageClass
import com.guildofsmiths.trademesh.data.CordRepository
import com.guildofsmiths.trademesh.data.IdentityResolver
import com.guildofsmiths.trademesh.data.MediaAttachment
import com.guildofsmiths.trademesh.data.MediaType
import com.guildofsmiths.trademesh.data.Message
import com.guildofsmiths.trademesh.data.MessageRepository
import com.guildofsmiths.trademesh.data.Peer
import com.guildofsmiths.trademesh.data.PeerRepository
import com.guildofsmiths.trademesh.data.UserPreferences
import com.guildofsmiths.trademesh.service.BackendConfig
import com.guildofsmiths.trademesh.service.ChatManager
import com.guildofsmiths.trademesh.service.GatewayClient
import com.guildofsmiths.trademesh.service.MeshService
import com.guildofsmiths.trademesh.service.SupabaseChat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * BoundaryEngine: Core routing singleton for dual-path message system.
 * 
 * Responsibilities:
 * - Determine which path (mesh vs chat) to use based on connectivity
 * - Route outbound messages to appropriate path
 * - Queue media messages when offline (mesh can't handle media)
 * - Trigger sync when connectivity restores
 * - Maintain strict separation between mesh and chat paths
 */
object BoundaryEngine {
    
    private const val TAG = "BoundaryEngine"
    
    /** Reference to active MeshService for mesh routing */
    private var meshService: MeshService? = null
    
    /** Track last known connectivity state for change detection */
    private var lastConnectivityState: Boolean? = null
    
    /** Observable mesh connection state */
    private val _isMeshConnected = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isMeshConnected: kotlinx.coroutines.flow.StateFlow<Boolean> = _isMeshConnected
    
    /** Observable scanning state */
    private val _isScanning = kotlinx.coroutines.flow.MutableStateFlow(false)
    val isScanning: kotlinx.coroutines.flow.StateFlow<Boolean> = _isScanning
    
    /** Observable IP/online state */
    private val _isOnline = MutableStateFlow(true) // Start online - will be updated by connectivity monitoring
    val isOnline: StateFlow<Boolean> = _isOnline
    
    /** Force mesh mode (ignore internet connectivity) - for testing */
    private var forceMeshMode = false // Use smart routing: online if available, mesh if offline
    
    /** Channel membership: maps channel hash -> channel ID for channels user has joined */
    private val channelMembership = mutableMapOf<Int, String>()
    
    /** Pending channel invites: hash -> (channelName, senderName) */
    private val _pendingInvites = MutableStateFlow<Map<Int, Pair<String, String>>>(emptyMap())
    val pendingInvites: StateFlow<Map<Int, Pair<String, String>>> = _pendingInvites
    
    /** Queued media messages waiting for IP connectivity */
    private val _queuedMedia = MutableStateFlow<List<Message>>(emptyList())
    val queuedMedia: StateFlow<List<Message>> = _queuedMedia

    /** Queued text messages waiting for online connectivity */
    private val _queuedMessages = MutableStateFlow<List<Message>>(emptyList())
    val queuedMessages: StateFlow<List<Message>> = _queuedMessages

    /** Gateway mode enabled */
    private val _isGatewayConnected = MutableStateFlow(false)
    val isGatewayConnected: StateFlow<Boolean> = _isGatewayConnected
    
    /**
     * Register the mesh service for message routing.
     * Called by MeshService on startup.
     */
    fun registerMeshService(service: MeshService) {
        meshService = service
        _isMeshConnected.value = true
        Log.d(TAG, "MeshService registered")
        
        // Setup gateway message listener
        GatewayClient.setMessageListener(object : GatewayClient.OnlineMessageListener {
            override fun onOnlineMessage(message: Message) {
                // Store online message locally
                MessageRepository.addMessage(message)
                Log.d(TAG, "📨 Online message stored: ${message.content.take(20)}")
            }
        })
        
        // Setup gateway connection state listener to update UI
        GatewayClient.setConnectionStateListener(object : GatewayClient.ConnectionStateListener {
            override fun onConnectionStateChanged(connected: Boolean) {
                Log.d(TAG, "🌐 Gateway connection state changed: $connected")
                _isGatewayConnected.value = connected

                // When gateway connects, sync channels from backend
                if (connected) {
                    syncChannelsFromBackend()
                }
            }
        })
        
        // Setup channel cleared listener to sync with dashboard (real-time clear)
        GatewayClient.setChannelClearedListener(object : GatewayClient.ChannelClearedListener {
            override fun onChannelCleared(channelId: String) {
                Log.d(TAG, "🗑️ Clearing messages for channel from dashboard: $channelId")
                // Clear local messages for this channel
                // The channelId from dashboard is a UUID, but we store by channel name
                // For now, clear all messages if it's the general channel
                MessageRepository.clearChannel(channelId)
                // Also try clearing by name "general" since we use that locally
                MessageRepository.clearChannel("general")
            }
        })
        
        // Setup sync cleared listener (for reconnecting after being offline)
        GatewayClient.setSyncClearedListener(object : GatewayClient.SyncClearedListener {
            override fun onSyncCleared(channelId: String, clearedAtTimestamp: Long) {
                Log.d(TAG, "🔄 Sync: clearing messages older than $clearedAtTimestamp for channel $channelId")
                // Clear messages older than the clear timestamp
                MessageRepository.clearMessagesOlderThan(channelId, clearedAtTimestamp)
                MessageRepository.clearMessagesOlderThan("general", clearedAtTimestamp)
            }
        })
        
        // Initialize ChatManager with backend URL for online messaging (configurable)
        // ChatManager now uses BackendConfig automatically
        
        // Auto-connect to online chat (for receiving messages)
        ChatManager.connect()
        Log.i(TAG, "🌐 Auto-connecting to local chat backend")
        
        // Connect to Supabase Realtime for GLOBAL chat (works anywhere in the world)
        com.guildofsmiths.trademesh.service.SupabaseChat.connect()
        Log.i(TAG, "🌍 Connecting to Supabase Realtime for global chat")
    }
    
    /**
     * Unregister mesh service on shutdown.
     */
    fun unregisterMeshService() {
        meshService = null
        _isMeshConnected.value = false
        _isScanning.value = false
        Log.d(TAG, "MeshService unregistered")
    }
    
    /**
     * Update scanning state (called by MeshService).
     */
    fun updateScanningState(scanning: Boolean) {
        _isScanning.value = scanning
    }
    
    /**
     * Connect to mesh - starts scanning/advertising.
     */
    fun connectMesh() {
        Log.i(TAG, "════════════════════════════════════════")
        Log.i(TAG, "📡 CONNECT MESH REQUESTED")
        Log.i(TAG, "   MeshService available: ${meshService != null}")
        meshService?.startScanning()
        if (meshService == null) {
            Log.w(TAG, "   ⚠️ MeshService is null - cannot start scanning")
        }
        Log.i(TAG, "════════════════════════════════════════")
    }
    
    /**
     * Disconnect from mesh - stops scanning/advertising.
     */
    fun disconnectMesh() {
        Log.i(TAG, "════════════════════════════════════════")
        Log.i(TAG, "🔌 DISCONNECT MESH REQUESTED")
        Log.i(TAG, "   MeshService available: ${meshService != null}")
        meshService?.stopScanning()
        meshService?.stopAdvertising()
        _isScanning.value = false
        Log.i(TAG, "════════════════════════════════════════")
    }
    
    /**
     * Check if mesh service is available.
     */
    fun isMeshServiceAvailable(): Boolean = meshService != null
    
    // ════════════════════════════════════════════════════════════════════
    // GATEWAY MODE
    // ════════════════════════════════════════════════════════════════════
    
    /**
     * Connect to online backend as a gateway relay.
     */
    fun connectGateway(backendUrl: String) {
        Log.i(TAG, "════════════════════════════════════════")
        Log.i(TAG, "🌐 CONNECT GATEWAY: $backendUrl")
        
        // Save settings for auto-reconnect
        UserPreferences.setGatewayUrl(backendUrl)
        UserPreferences.setGatewayEnabled(true)

        // Update backend configuration
        BackendConfig.setBackendUrl(backendUrl.replace("ws://", "http://").replace("wss://", "https://"))
        BackendConfig.setWebSocketUrl(backendUrl)
        GatewayClient.connect()
        _isGatewayConnected.value = true
        Log.i(TAG, "════════════════════════════════════════")
    }
    
    /**
     * Disconnect from gateway.
     */
    fun disconnectGateway() {
        Log.i(TAG, "🔌 DISCONNECT GATEWAY")
        
        // Disable auto-reconnect
        UserPreferences.setGatewayEnabled(false)
        
        GatewayClient.disconnect()
        _isGatewayConnected.value = false
    }
    
    /**
     * Forward a mesh message to the gateway (backend).
     * Called when a mesh message is received and gateway is connected.
     */
    fun forwardToGateway(message: Message) {
        // Forward via Gateway WebSocket if connected
        if (GatewayClient.isConnected()) {
            GatewayClient.forwardMeshMessage(message)
        }
        
        // Also forward via Supabase for global reach (auto-bridges mesh → online)
        // This ensures mesh messages get uploaded to the cloud even without gateway mode
        if (_isOnline.value) {
            Log.d(TAG, "🌉 Auto-bridging mesh message to Supabase: ${message.content.take(20)}")
            com.guildofsmiths.trademesh.service.SupabaseChat.sendMessage(message)
        }
    }
    
    /**
     * Inject a message from gateway (online) to BLE mesh.
     * Called by GatewayClient when backend requests injection.
     */
    fun injectFromGateway(message: Message) {
        Log.d(TAG, "🔄 Injecting from gateway to mesh: ${message.content.take(30)}")
        Log.d(TAG, "   Original channelId: ${message.channelId}")
        
        // Track this message ID to prevent re-forwarding when we hear our own broadcast
        synchronized(recentlyInjectedIds) {
            recentlyInjectedIds.add(message.id)
            if (recentlyInjectedIds.size > MAX_INJECTED_IDS) {
                recentlyInjectedIds.remove(recentlyInjectedIds.first())
            }
        }
        
        // Convert UUID channelId to channel name for mesh
        // The mesh uses channel names (like "general") not UUIDs
        val channelName = resolveChannelNameFromId(message.channelId)
        Log.d(TAG, "   Resolved to: $channelName")
        
        val meshMessage = if (channelName != null && channelName != message.channelId) {
            message.copy(channelId = channelName)
        } else {
            message
        }
        
        meshService?.broadcastMessage(meshMessage)
    }
    
    /**
     * Resolve a channel UUID to its name for mesh broadcast.
     * Returns the name if found, or the original ID if not.
     */
    private fun resolveChannelNameFromId(channelId: String): String? {
        // If it's already a simple name (not a UUID), return as-is
        if (!channelId.contains("-")) {
            return channelId
        }
        
        // Look up in our channel membership mappings (hash -> name)
        for ((_, name) in channelMembership) {
            // Try to match by looking at common channel names
            if (name.isNotEmpty()) {
                return name
            }
        }
        
        // Default to "general" for broadcast channels
        return "general"
    }
    
    /**
     * Determine if mesh path should be used based on network connectivity.
     * Returns true if no internet connection is available.
     * 
     * In Phase 0, we force mesh mode to always be true for testing.
     */
    fun shouldUseMesh(context: Context): Boolean {
        // Phase 0: Check connectivity properly but log details
        if (forceMeshMode) {
            Log.d(TAG, "Force mesh mode enabled - using mesh")
            return true
        }

        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (connectivityManager == null) {
            Log.w(TAG, "No ConnectivityManager - defaulting to mesh")
            return true
        }

        val network = connectivityManager.activeNetwork
        if (network == null) {
            Log.d(TAG, "No active network - using mesh")
            return true
        }

        val capabilities = connectivityManager.getNetworkCapabilities(network)
        if (capabilities == null) {
            Log.d(TAG, "No network capabilities - using mesh")
            return true
        }

        // For P0: Be more permissive - just check for internet capability
        // Don't require VALIDATED which can fail even with working internet
        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val hasValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        // Log network transport type for debugging
        val isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val isCellular = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)

        Log.i(TAG, "🌐 Connectivity check: hasInternet=$hasInternet, hasValidated=$hasValidated")
        Log.i(TAG, "   Transport: WIFI=$isWifi, CELLULAR=$isCellular")

        // Use mesh only if there's no internet at all
        // This prevents the app from staying offline when Supabase has issues
        if (!hasInternet) {
            Log.d(TAG, "No internet capability - using mesh")
            return true
        }

        // We have internet - prefer online mode
        Log.d(TAG, "Internet available - using online mode")
        return false
    }
    
    /**
     * Check if we have IP connectivity for media transmission.
     */
    fun hasIpConnectivity(context: Context): Boolean {
        // In Phase 0 with force mesh mode, we're "offline" for media
        if (forceMeshMode) {
            Log.d(TAG, "Force mesh mode - no IP connectivity")
            return false
        }

        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false

        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

        // Check for internet capability and validation
        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val hasValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        val result = hasInternet && hasValidated
        Log.d(TAG, "IP connectivity check: internet=$hasInternet, validated=$hasValidated, result=$result")
        return result
    }
    
    /**
     * Set force mesh mode (for testing).
     */
    fun setForceMeshMode(force: Boolean) {
        forceMeshMode = force
        Log.i(TAG, "Force mesh mode: $force")
    }
    
    /**
     * Route an outbound message through the appropriate path.
     * - When online: Send via BOTH Supabase (global) AND mesh (local)
     * - When offline: Send via mesh only
     * - Media messages: Chat only (queued if offline)
     * 
     * Message is always added to local repository for immediate display.
     */
    fun routeMessage(context: Context, message: Message) {
        // #region agent log
        Log.w("DEBUG_MSG", "routeMessage: msgId=${message.id.take(8)} content='${message.content.take(20)}' isMeshOrigin=${message.isMeshOrigin}")
        // #endregion
        // Always add to local repository first for immediate UI feedback
        MessageRepository.addMessage(message)

        // For legal/financial records, also append to cord for provable correctness
        createCordEntryIfNeeded(context, message)
        
        // Media messages require IP - queue if offline
        if (message.hasMedia()) {
            routeMediaMessage(context, message)
            return
        }
        
        // ════════════════════════════════════════════════════════════════
        // TRANSPORT PRIORITY RULE (Core Law):
        // - Online/Internet = PRIMARY (single path)
        // - Offline/No service = SECONDARY (mesh fallback)
        // - BLE Mesh is fallback, NOT a parallel chat surface
        // - No duplicate send paths allowed
        // ════════════════════════════════════════════════════════════════
        if (!shouldUseMesh(context)) {
            // ONLINE: Route via Supabase ONLY
            // BLE stays in presence/emergency mode only
            // #region agent log
            Log.w("DEBUG_MSG", "ONLINE PATH: Sending via Supabase ONLY msgId=${message.id.take(8)}")
            // #endregion
            Log.d(TAG, "📤 ONLINE: Sending via Supabase (internet primary)")
            routeViaChat(message)
            // NOTE: No mesh broadcast when online - prevents duplicate delivery
        } else {
            // OFFLINE: Route via Mesh ONLY (fallback)
            // #region agent log
            Log.w("DEBUG_MSG", "OFFLINE PATH: Sending via Mesh only (fallback) msgId=${message.id.take(8)}")
            // #endregion
            Log.d(TAG, "📤 OFFLINE: Sending via Mesh (fallback)")
            routeViaMesh(message)
        }
    }
    
    /**
     * Route media message - chat only, queue if offline.
     */
    private fun routeMediaMessage(context: Context, message: Message) {
        if (hasIpConnectivity(context)) {
            // Online - send via chat
            Log.i(TAG, "📤 Routing media via chat: ${message.mediaType}")
            routeViaChat(message)
        } else {
            // Offline - queue and send placeholder via mesh
            Log.i(TAG, "📥 Media queued (offline): ${message.mediaType}")
            queueMediaMessage(message)
            
            // Send text placeholder via mesh so peers know something is coming
            val placeholder = message.copy(
                id = "${message.id}_placeholder",
                content = message.getMeshPlaceholder(),
                mediaType = MediaType.TEXT,
                media = null
            )
            routeViaMesh(placeholder)
        }
    }
    
    /**
     * Queue a media message for later upload when IP restores.
     */
    private fun queueMediaMessage(message: Message) {
        val queued = message.copy(
            media = message.media?.copy(isQueued = true)
        )
        _queuedMedia.value = _queuedMedia.value + queued
        Log.d(TAG, "Media queued. Total queued: ${_queuedMedia.value.size}")
    }

    /**
     * Queue a text message for later delivery when online connectivity restores.
     */
    private fun queueMessageForOfflineDelivery(message: Message) {
        _queuedMessages.value = _queuedMessages.value + message
        Log.d(TAG, "Message queued for offline delivery. Total queued: ${_queuedMessages.value.size}")
    }
    
    /**
     * Route message via BLE mesh path.
     */
    private fun routeViaMesh(message: Message) {
        val service = meshService
        if (service != null) {
            Log.d(TAG, "Routing via mesh: ${message.id.take(8)}...")
            service.broadcastMessage(message)
        } else {
            Log.w(TAG, "MeshService not available - message queued locally")
            // Message is already in repository; will be picked up when mesh restarts
        }
    }
    
    /**
     * Route message via IP chat path.
     * Uses smart routing: Telegram Bridge primary, Supabase fallback, ChatManager tertiary.
     */
    private fun routeViaChat(message: Message) {
        Log.d(TAG, "Routing via chat: ${message.id.take(8)}...")

        // PRIMARY: Try Telegram Bridge first (most reliable for cross-device)
        if (com.guildofsmiths.trademesh.service.TelegramBridge.isConnected.value) {
            com.guildofsmiths.trademesh.service.TelegramBridge.sendMessage(message)
            Log.d(TAG, "📤 Sent via Telegram Bridge (primary)")
            return
        }

        // SECONDARY: Try Supabase (global, cloud-based)
        if (SupabaseChat.isConnected.value) {
            SupabaseChat.sendMessage(message)
            Log.d(TAG, "📤 Sent via Supabase (secondary)")
            return
        }

        // TERTIARY: Use local ChatManager if others unavailable
        if (ChatManager.isConnected()) {
            ChatManager.sendMessage(message)
            Log.d(TAG, "📤 Sent via ChatManager (tertiary)")
            return
        }

        // OFFLINE: Queue for later delivery
        Log.w(TAG, "⚠️ No online connection available - queuing message")
        queueMessageForOfflineDelivery(message)
    }
    
    // Track recently injected message IDs to avoid re-forwarding our own broadcasts
    private val recentlyInjectedIds = mutableSetOf<String>()
    private const val MAX_INJECTED_IDS = 50
    
    /**
     * Handle incoming mesh message from BLE scan.
     * Called by MeshService when a peer message is received.
     */
    fun onMeshMessageReceived(context: Context, message: Message, rssi: Int = 0) {
        Log.d(TAG, "Mesh message received: ${message.id.take(8)} from ${message.senderName}")

        // IDENTITY RESOLUTION: Ensure consistent author identity
        // BLE messages may have device-based senderId that needs resolution
        val resolvedSenderId = IdentityResolver.resolveAuthorId(
            deviceId = message.deviceId ?: message.senderId, // Use deviceId if available
            knownAuthorId = message.senderId
        )

        // If identity was resolved/changed, update the message
        val resolvedMessage = if (resolvedSenderId != message.senderId) {
            message.copy(senderId = resolvedSenderId)
        } else {
            message
        }

        // Track the peer (always, even for heartbeats)
        PeerRepository.onPeerSeen(resolvedMessage.senderId, resolvedMessage.senderName, rssi)
        
        // Filter out heartbeat/ping messages - they're only for presence tracking
        if (isPresenceMessage(resolvedMessage.content)) {
            Log.d(TAG, "   (presence message - not displaying)")
            return
        }

        // Don't forward messages that WE just injected (prevents echo loop)
        val myUserId = UserPreferences.getUserId()
        if (recentlyInjectedIds.contains(resolvedMessage.id) || resolvedMessage.senderId == myUserId) {
            Log.d(TAG, "   (own message or recently injected - not re-forwarding to backend)")
            // Still add to local repo for display
            MessageRepository.addMessage(resolvedMessage)
            return
        }

        // Add to repository - deduplication handled there
        MessageRepository.addMessage(resolvedMessage)

        // Forward to gateway if connected (bridge mesh → online)
        forwardToGateway(resolvedMessage)
    }
    
    /**
     * Check if a message is a presence/heartbeat message (not for display).
     */
    private fun isPresenceMessage(content: String): Boolean {
        return content == "[heartbeat]" || content == "[ping]"
    }
    
    /**
     * Called when network connectivity is restored.
     * Triggers sync of queued mesh messages and media to chat backend.
     */
    fun onConnectivityRestored(context: Context) {
        val wasOffline = lastConnectivityState == true
        val isOnlineNow = !shouldUseMesh(context)
        
        if (wasOffline && isOnlineNow) {
            Log.i(TAG, "Connectivity restored - initiating sync")
            syncMeshMessagesToChat()
            syncQueuedContent(context)
        }
        
        lastConnectivityState = shouldUseMesh(context)
        _isOnline.value = isOnlineNow
    }
    
    /**
     * Sync all pending mesh messages to chat backend.
     * Preserves message ordering and attribution.
     * Messages are uploaded to BOTH local backend AND Supabase for global reach.
     */
    private fun syncMeshMessagesToChat() {
        val pendingMessages = MessageRepository.getPendingSyncMessages()
        if (pendingMessages.isEmpty()) {
            Log.d(TAG, "No pending mesh messages to sync")
            return
        }
        
        Log.i(TAG, "🌊 Syncing ${pendingMessages.size} mesh messages to cloud (bobbling up!)")
        
        val syncedIds = mutableSetOf<String>()
        for (message in pendingMessages.sortedBy { it.timestamp }) {
            // Re-send via chat path, preserving original metadata
            val chatMessage = message.copy(isMeshOrigin = false)
            
            // Send to local backend
            ChatManager.sendMessage(chatMessage)
            
            // Also send to Supabase for global reach
            com.guildofsmiths.trademesh.service.SupabaseChat.sendMessage(chatMessage)
            
            syncedIds.add(message.id)
            Log.d(TAG, "   ↑ Uploaded: ${message.content.take(30)}")
        }
        
        // Mark as synced to prevent re-sync
        MessageRepository.markAsSynced(syncedIds)
        Log.i(TAG, "✅ Mesh sync completed: ${syncedIds.size} messages uploaded to cloud")
    }
    
    /**
     * Sync queued messages and media when IP connectivity restores.
     */
    private fun syncQueuedContent(context: Context) {
        // Sync queued text messages
        syncQueuedMessages()

        // Sync queued media messages
        syncQueuedMedia(context)
    }

    /**
     * Sync queued text messages when online connectivity restores.
     */
    private fun syncQueuedMessages() {
        val queued = _queuedMessages.value
        if (queued.isEmpty()) {
            Log.d(TAG, "No queued messages to sync")
            return
        }

        Log.i(TAG, "Syncing ${queued.size} queued text messages")

        for (message in queued.sortedBy { it.timestamp }) {
            routeViaChat(message)
        }

        // Clear queue
        _queuedMessages.value = emptyList()
        Log.i(TAG, "Message sync completed")
    }

    /**
     * Sync queued media messages when IP restores.
     */
    private fun syncQueuedMedia(context: Context) {
        val queued = _queuedMedia.value
        if (queued.isEmpty()) {
            Log.d(TAG, "No queued media to sync")
            return
        }

        Log.i(TAG, "Syncing ${queued.size} queued media messages")

        for (message in queued.sortedBy { it.timestamp }) {
            // Upload media and send via chat
            val uploaded = message.copy(
                media = message.media?.copy(isQueued = false)
            )
            routeViaChat(uploaded)
        }

        // Clear queue
        _queuedMedia.value = emptyList()
        Log.i(TAG, "Media sync completed")
    }
    
    /**
     * Update connectivity state tracking.
     * Call this periodically or on network change broadcasts.
     */
    fun updateConnectivityState(context: Context) {
        val currentState = shouldUseMesh(context)
        val isOnlineNow = !currentState

        Log.d(TAG, "Connectivity update: mesh=$currentState, online=$isOnlineNow")

        if (lastConnectivityState != null && lastConnectivityState != currentState) {
            if (!currentState) {
                // Transitioned from offline to online
                Log.i(TAG, "🌐 Online connectivity restored")
                onConnectivityRestored(context)
            } else {
                // Transitioned from online to offline
                Log.i(TAG, "📶 Offline - switching to mesh mode")
            }
        }

        lastConnectivityState = currentState
        _isOnline.value = isOnlineNow

        // Connect/disconnect Supabase based on online state
        try {
            if (isOnlineNow) {
                com.guildofsmiths.trademesh.service.SupabaseChat.connect()
            } else {
                com.guildofsmiths.trademesh.service.SupabaseChat.disconnect()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Supabase connection update failed", e)
        }
    }
    
    // ══════════════════════════════════════════════════════════════════
    // PEER SELECTION FOR DIRECT MESSAGES
    // ══════════════════════════════════════════════════════════════════
    
    /**
     * Get list of discovered peers for DM selection.
     * In mesh mode, returns peers from BLE scans.
     * In chat mode, would return contacts (stub for now).
     */
    fun getAvailablePeers(): List<Peer> {
        return PeerRepository.getActivePeers()
    }
    
    /**
     * Send a direct message to a specific peer.
     */
    fun sendDirectMessage(context: Context, content: String, recipientId: String, recipientName: String) {
        val message = Message(
            senderId = UserPreferences.getUserId(),
            senderName = UserPreferences.getDisplayName(),
            deviceId = UserPreferences.getDeviceId(), // Include physical device identity
            content = content,
            recipientId = recipientId,
            recipientName = recipientName
        )
        routeMessage(context, message)
    }
    
    /**
     * Send a media direct message.
     */
    fun sendMediaDirectMessage(
        context: Context,
        mediaType: MediaType,
        localPath: String,
        mimeType: String?,
        fileName: String?,
        fileSize: Long,
        duration: Long = 0,
        width: Int = 0,
        height: Int = 0,
        recipientId: String? = null,
        recipientName: String? = null,
        channelId: String = "general"
    ) {
        val media = MediaAttachment(
            type = mediaType,
            localPath = localPath,
            mimeType = mimeType,
            fileName = fileName,
            fileSize = fileSize,
            duration = duration,
            width = width,
            height = height
        )
        
        val message = Message(
            senderId = UserPreferences.getUserId(),
            senderName = UserPreferences.getDisplayName(),
            channelId = channelId,
            content = "", // Content is the media
            mediaType = mediaType,
            media = media,
            recipientId = recipientId,
            recipientName = recipientName
        )
        
        routeMessage(context, message)
    }
    
    // ══════════════════════════════════════════════════════════════════
    // CHANNEL MEMBERSHIP SYSTEM
    // ══════════════════════════════════════════════════════════════════
    
    /**
     * Get a 2-byte hash of a channel ID (matches MeshService format).
     */
    private fun channelHash(channelId: String): Int {
        return channelId.hashCode() and 0x7FFF // Keep positive, 15 bits
    }
    
    /**
     * Join a channel - registers the channel hash for message filtering.
     */
    fun joinChannel(channelId: String) {
        val hash = channelHash(channelId)
        channelMembership[hash] = channelId
        Log.i(TAG, "Joined channel: #$channelId (hash: $hash)")
    }
    
    /**
     * Leave a channel - unregisters the channel hash.
     */
    fun leaveChannel(channelId: String) {
        val hash = channelHash(channelId)
        channelMembership.remove(hash)
        Log.i(TAG, "Left channel: #$channelId")
    }
    
    /**
     * Check if user is a member of a channel.
     */
    fun isMemberOf(channelId: String): Boolean {
        return channelMembership.containsValue(channelId)
    }
    
    /**
     * Resolve a channel ID from its hash.
     * Returns the channel ID if the user is a member, null otherwise.
     */
    fun resolveChannelByHash(hash: Int): String? {
        // Convert short hash to our internal format
        val normalizedHash = hash and 0x7FFF
        Log.d(TAG, "resolveChannelByHash: input=$hash, normalized=$normalizedHash")
        Log.d(TAG, "   Known channels: ${channelMembership.entries.joinToString { "${it.key}->${it.value}" }}")
        val result = channelMembership[normalizedHash]
        Log.d(TAG, "   Result: $result")
        return result
    }
    
    /**
     * Initialize default channel memberships.
     * Called on app startup.
     */
    fun initializeChannelMembership() {
        // Auto-join general channel only
        joinChannel("general")
        Log.i(TAG, "Initialized default channel membership: general")

        // Auto-connect gateway if enabled
        if (UserPreferences.isGatewayEnabled()) {
            val url = UserPreferences.getGatewayUrl()
            Log.i(TAG, "🌐 Auto-connecting gateway: $url")
            BackendConfig.setWebSocketUrl(url)
            GatewayClient.connect()
        }
        
        // Always sync channels from backend on startup (even without gateway mode)
        // This ensures all devices see the same channels
        Log.i(TAG, "🔄 Auto-syncing channels from backend on startup...")
        syncChannelsFromBackend()
    }

    /**
     * Sync channels from backend and join them locally.
     * Called when gateway connects to ensure all devices have the same channel list.
     */
    fun syncChannelsFromBackend() {
        Log.i(TAG, "🔄 Syncing channels from backend...")

        GatewayClient.fetchChannels { channels, error ->
            if (error != null) {
                Log.e(TAG, "Failed to sync channels from backend", error)
                return@fetchChannels
            }

            if (channels != null) {
                Log.i(TAG, "📥 Processing ${channels.length()} channels from backend")

                // Run on main thread to ensure UI updates
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    var addedCount = 0
                    for (i in 0 until channels.length()) {
                        try {
                            val channelJson = channels.getJSONObject(i)
                            val channelId = channelJson.getString("id")
                            val channelName = channelJson.getString("name")
                            val channelType = channelJson.getString("type")

                            Log.d(TAG, "   Checking channel: #$channelName ($channelId)")

                            // Check if we already have this channel locally
                            val existing = BeaconRepository.getChannel("default", channelId)
                            if (existing == null) {
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

                                BeaconRepository.addChannel("default", localChannel)
                                addedCount++
                                Log.i(TAG, "✅ Added backend channel locally: #$channelName ($channelId)")
                            } else {
                                Log.d(TAG, "   Channel already exists locally: #$channelName")
                            }

                            // Join the channel for message routing
                            joinChannel(channelId)

                        } catch (e: Exception) {
                            Log.e(TAG, "Error processing channel from backend", e)
                        }
                    }

                    Log.i(TAG, "✅ Channel sync completed: added $addedCount new channels")
                }
            }
        }
    }
    
    /**
     * Broadcast a channel invite to nearby peers.
     */
    fun broadcastChannelInvite(channelId: String, channelName: String) {
        Log.i(TAG, "════════════════════════════════════════")
        Log.i(TAG, "📤 BROADCAST INVITE CALLED")
        Log.i(TAG, "   Channel: #$channelName (id: $channelId)")
        Log.i(TAG, "   MeshService available: ${meshService != null}")
        
        val senderId = UserPreferences.getUserId()
        Log.i(TAG, "   SenderId: $senderId")
        
        if (meshService == null) {
            Log.e(TAG, "   ❌ MeshService is NULL - cannot broadcast invite!")
        } else {
            meshService?.broadcastInvite(channelId, channelName, senderId)
            Log.i(TAG, "   ✅ Invite sent to MeshService")
        }
        Log.i(TAG, "════════════════════════════════════════")
    }
    
    /**
     * Request peer discovery by restarting BLE scanning.
     * This triggers a fresh BLE scan to pick up nearby peers.
     * Also broadcasts a ping message so other devices can see us.
     */
    fun requestPeerDiscovery() {
        Log.i(TAG, "════════════════════════════════════════")
        Log.i(TAG, "👥 PEER DISCOVERY REQUESTED")
        
        // Restart BLE scanning to pick up any nearby devices
        meshService?.let { service ->
            if (service.isScanningActive()) {
                Log.i(TAG, "   Restarting BLE scan...")
                service.stopScanning()
            }
            service.startScanning()
            Log.i(TAG, "   ✅ BLE scan started")
        } ?: run {
            Log.w(TAG, "   ⚠️ MeshService not available")
        }
        
        // Also send a ping message so other devices can discover us
        // NOTE: Sent directly via mesh (not routeMessage) to avoid MessageRepository
        val pingMessage = Message(
            senderId = UserPreferences.getUserId(),
            senderName = UserPreferences.getDisplayName(),
            channelId = "_presence",  // Special channel, not displayed
            content = "[ping]",
            isMeshOrigin = true
        )
        meshService?.broadcastMessage(pingMessage)
        Log.i(TAG, "   📡 Ping broadcast sent")
        
        Log.i(TAG, "════════════════════════════════════════")
    }
    
    /**
     * Request channel discovery from nearby peers AND backend.
     * This triggers:
     * 1. Backend sync - fetches all channels from dashboard/server
     * 2. BLE scan refresh - picks up nearby mesh broadcasts
     * 3. Broadcasts our own channels so others can discover them
     */
    fun requestChannelDiscovery() {
        Log.i(TAG, "════════════════════════════════════════")
        Log.i(TAG, "🔍 CHANNEL DISCOVERY REQUESTED")

        // 1. Sync channels from backend (dashboard channels)
        Log.i(TAG, "   📡 Syncing channels from backend...")
        syncChannelsFromBackend()

        // 2. Also fetch from Supabase if connected
        Log.i(TAG, "   🌍 Fetching channels from Supabase...")
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val supabaseChannels = com.guildofsmiths.trademesh.service.SupabaseChat.fetchAvailableChannels()
                Log.i(TAG, "   📋 Found ${supabaseChannels.size} Supabase channels")
            } catch (e: Exception) {
                Log.w(TAG, "   ⚠️ Supabase channel fetch failed: ${e.message}")
            }
        }

        // 3. Restart BLE scanning to pick up any new broadcasts
        meshService?.let { service ->
            if (service.isScanningActive()) {
                Log.i(TAG, "   Restarting BLE scan...")
                service.stopScanning()
            }
            service.startScanning()
            Log.i(TAG, "   ✅ BLE scan started")
        } ?: run {
            Log.w(TAG, "   ⚠️ MeshService not available")
        }

        // 4. Also broadcast our own channels so others can discover them
        val myUserId = UserPreferences.getUserId()
        val myChannels = BeaconRepository.getBeacon("default")?.channels
            ?.filter { it.isOwner(myUserId) && it.isVisible() && it.id != "general" }
            ?: emptyList()

        Log.i(TAG, "   Broadcasting ${myChannels.size} owned channels...")
        myChannels.forEach { channel ->
            meshService?.broadcastInvite(channel.id, channel.name, myUserId)
        }

        Log.i(TAG, "════════════════════════════════════════")
    }
    
    /**
     * Handle received channel invite.
     * Content may be "channelName" or "channelName|channelId" format.
     */
    fun onChannelInviteReceived(channelHash: Int, content: String, senderName: String) {
        // Parse content: could be "name" or "name|uuid"
        val parts = content.split("|", limit = 2)
        val channelName = parts[0]
        val channelId = if (parts.size > 1) parts[1] else channelName.lowercase().replace(" ", "-")

        // Don't show invite if already a member
        if (channelMembership.containsKey(channelHash)) {
            Log.d(TAG, "Already member of channel #$channelName - ignoring invite")
            return
        }

        Log.i(TAG, "Received invite to #$channelName ($channelId) from $senderName")
        _pendingInvites.value = _pendingInvites.value + (channelHash to Pair(channelName, senderName))
    }
    
    /**
     * Accept a channel invite.
     */
    fun acceptInvite(channelHash: Int): String? {
        val invite = _pendingInvites.value[channelHash] ?: return null
        val (channelName, _) = invite

        // For now, use derived ID. In future, we could look up by name from backend
        val channelId = channelName.lowercase().replace(" ", "-")

        // Check if channel already exists locally (maybe from backend sync)
        val existingChannel = BeaconRepository.getChannel("default", channelId)
        if (existingChannel == null) {
            // Create the channel locally
            val channel = Channel(
                id = channelId,
                beaconId = "default",
                name = channelName,
                type = ChannelType.GROUP
            )
            BeaconRepository.addChannel("default", channel)
        }

        joinChannel(channelId)

        // Remove from pending
        _pendingInvites.value = _pendingInvites.value - channelHash

        Log.i(TAG, "Accepted invite to #$channelName")
        return channelId
    }
    
    /**
     * Decline a channel invite.
     */
    fun declineInvite(channelHash: Int) {
        val invite = _pendingInvites.value[channelHash]
        if (invite != null) {
            Log.i(TAG, "Declined invite to #${invite.first}")
            _pendingInvites.value = _pendingInvites.value - channelHash
        }
    }
    
    /**
     * Broadcast a channel deletion to nearby peers.
     * Called when the channel owner deletes a channel.
     */
    fun broadcastChannelDeletion(channelId: String, channelName: String) {
        Log.i(TAG, "════════════════════════════════════════")
        Log.i(TAG, "🗑️ BROADCAST DELETION CALLED")
        Log.i(TAG, "   Channel: #$channelName (id: $channelId)")
        Log.i(TAG, "   MeshService available: ${meshService != null}")
        
        val senderId = UserPreferences.getUserId()
        Log.i(TAG, "   SenderId: $senderId")
        
        if (meshService == null) {
            Log.e(TAG, "   ❌ MeshService is NULL - cannot broadcast deletion!")
        } else {
            meshService?.broadcastDeletion(channelId, channelName, senderId)
            Log.i(TAG, "   ✅ Deletion sent to MeshService")
        }
        Log.i(TAG, "════════════════════════════════════════")
    }
    
    /**
     * Handle received channel deletion (tombstone) from a peer.
     * Marks the channel as deleted locally if we have it.
     */
    fun onChannelDeletionReceived(channelName: String, senderId: String) {
        Log.i(TAG, "════════════════════════════════════════")
        Log.i(TAG, "🗑️ CHANNEL DELETION RECEIVED")
        Log.i(TAG, "   Channel: #$channelName")
        Log.i(TAG, "   From: $senderId")
        
        // Find and delete the channel locally
        val channelId = channelName.lowercase().replace(" ", "-")
        val channel = BeaconRepository.getChannel("default", channelId)
        
        if (channel == null) {
            Log.i(TAG, "   ℹ️ Channel not found locally - ignoring")
            Log.i(TAG, "════════════════════════════════════════")
            return
        }
        
        // Only process deletion if it came from the channel owner
        // For now, we trust the sender (in production, would verify signature)
        Log.i(TAG, "   Found channel locally, marking as deleted...")
        
        // Remove from channel membership
        val hash = channelHash(channelId)
        channelMembership.remove(hash)
        
        // Remove the channel from repository (hard delete for simplicity)
        BeaconRepository.removeChannel("default", channelId)
        
        // Also remove from pending invites if present
        _pendingInvites.value = _pendingInvites.value.filterValues { it.first != channelName }
        
        Log.i(TAG, "   ✅ Channel #$channelName removed locally")
        Log.i(TAG, "════════════════════════════════════════")
    }
    
    /**
     * Get list of channels user is a member of.
     */
    fun getJoinedChannels(): List<String> {
        return channelMembership.values.toList()
    }
    
    // ══════════════════════════════════════════════════════════════════
    // PERIODIC HEARTBEAT SYSTEM
    // ══════════════════════════════════════════════════════════════════
    
    /** Heartbeat interval in milliseconds (36 seconds) */
    private const val HEARTBEAT_INTERVAL_MS = 36_000L
    
    /** Handler for periodic heartbeat */
    private val heartbeatHandler = android.os.Handler(android.os.Looper.getMainLooper())
    
    /** Is heartbeat currently running */
    private var isHeartbeatRunning = false
    
    /** Heartbeat runnable */
    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            if (isHeartbeatRunning && meshService != null) {
                sendHeartbeat()
                heartbeatHandler.postDelayed(this, HEARTBEAT_INTERVAL_MS)
            }
        }
    }
    
    /**
     * Start the periodic heartbeat system.
     * Should be called when mesh service is registered and scanning starts.
     */
    fun startHeartbeat() {
        if (isHeartbeatRunning) {
            Log.d(TAG, "Heartbeat already running")
            return
        }
        
        isHeartbeatRunning = true
        Log.i(TAG, "💓 HEARTBEAT STARTED (interval: ${HEARTBEAT_INTERVAL_MS/1000}s)")
        
        // Send first heartbeat immediately
        sendHeartbeat()
        
        // Schedule periodic heartbeats
        heartbeatHandler.postDelayed(heartbeatRunnable, HEARTBEAT_INTERVAL_MS)
    }
    
    /**
     * Stop the periodic heartbeat system.
     * Called when mesh is disconnected or app goes to background.
     */
    fun stopHeartbeat() {
        if (!isHeartbeatRunning) return
        
        isHeartbeatRunning = false
        heartbeatHandler.removeCallbacks(heartbeatRunnable)
        Log.i(TAG, "💔 HEARTBEAT STOPPED")
    }
    
    /**
     * Send a single heartbeat/presence broadcast.
     * This is a small message that lets nearby peers know we're active.
     * NOTE: This bypasses routeMessage() to avoid adding to MessageRepository.
     */
    private fun sendHeartbeat() {
        val service = meshService ?: return
        
        Log.d(TAG, "💓 Sending heartbeat...")
        
        // Create a minimal presence message
        val heartbeatMessage = Message(
            senderId = UserPreferences.getUserId(),
            senderName = UserPreferences.getDisplayName(),
            channelId = "_presence",  // Special channel, not displayed
            content = "[heartbeat]",
            isMeshOrigin = true
        )
        
        // Broadcast directly via mesh (NOT via routeMessage to avoid MessageRepository)
        service.broadcastMessage(heartbeatMessage)
    }
    
    /**
     * Check if heartbeat is currently active.
     */
    fun isHeartbeatActive(): Boolean = isHeartbeatRunning
    
    /**
     * Delete a message from the backend (for "Delete for everyone").
     * Called when user chooses to delete for everyone.
     */
    fun deleteMessageFromBackend(message: Message) {
        Log.i(TAG, "🗑️ Requesting backend deletion for message: ${message.id}")
        ChatManager.deleteMessage(message.id, message.channelId)
    }

    // ════════════════════════════════════════════════════════════════════
    // CORD-BASED STATE INTEGRATION
    // ════════════════════════════════════════════════════════════════════

    /**
     * Create a cord entry if the message contains legal/financial data that needs provable correctness.
     * This is transparent to the UI - cord entries provide auditability without changing user experience.
     */
    private fun createCordEntryIfNeeded(context: Context, message: Message) {
        val messageClass = detectMessageClass(message)
        if (messageClass == null) return // Regular chat, no cord needed

        val cordRepository = CordRepository(context)

        // Run cord append in background (non-blocking)
        kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val cordEntry = cordRepository.appendCordEntry(
                    cordId = message.beaconId, // Use beacon as cord boundary
                    hubId = message.beaconId,  // Beacon = permission domain
                    channelId = message.channelId,
                    authorId = message.senderId,
                    authorName = message.senderName,
                    messageClass = messageClass,
                    payload = serializeMessageForCord(message),
                    payloadType = "message",
                    threadId = null, // TODO: Add thread support if needed
                    deliveryMarker = getDeliveryMarker(message)
                )

                if (cordEntry != null) {
                    Log.d(TAG, "📝 Cord entry created: ${cordEntry.messageId.take(8)} (${messageClass})")
                } else {
                    Log.w(TAG, "⚠️ Cord entry collision or failure for message: ${message.id}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ Failed to create cord entry", e)
            }
        }
    }

    /**
     * Detect if a message should be cord-protected based on content and context.
     */
    private fun detectMessageClass(message: Message): CordMessageClass? {
        val content = message.content.lowercase()

        // WORK_LOG: Time entries, labor records
        if (content.contains("clock") || content.contains("time") ||
            content.contains("worked") || content.contains("hours") ||
            message.aiContext?.contains("time_entry") == true) {
            return CordMessageClass.WORK_LOG
        }

        // DECISION: Approvals, rejections, status changes
        if (content.contains("approve") || content.contains("reject") ||
            content.contains("decision") || content.contains("status") ||
            message.aiContext?.contains("decision") == true) {
            return CordMessageClass.DECISION
        }

        // COMMAND: Executable commands, job assignments
        if (content.startsWith("/") || content.contains("assign") ||
            content.contains("command") ||
            message.aiContext?.contains("command") == true) {
            return CordMessageClass.COMMAND
        }

        // ALERT: Warnings, notifications, safety alerts
        if (content.contains("alert") || content.contains("warning") ||
            content.contains("safety") || content.contains("urgent") ||
            message.aiContext?.contains("alert") == true) {
            return CordMessageClass.ALERT
        }

        // AI_SUMMARY: AI-generated summaries and reports
        if (message.aiGenerated && message.aiSource == "llm") {
            return CordMessageClass.AI_SUMMARY
        }

        // STATUS: System status updates
        if (content.contains("status") || content.contains("update") ||
            message.aiContext?.contains("status") == true) {
            return CordMessageClass.STATUS
        }

        // Regular chat doesn't need cord protection
        return null
    }

    /**
     * Serialize message content for cord storage.
     */
    private fun serializeMessageForCord(message: Message): String {
        // Create a JSON representation of the message for cord storage
        // For semantic deduplication, exclude transport-specific fields
        val cordPayload = mutableMapOf<String, Any?>(
            "content" to message.content,
            "aiGenerated" to message.aiGenerated,
            "aiModel" to message.aiModel,
            "aiSource" to message.aiSource,
            "aiContext" to message.aiContext,
            "hasMedia" to message.hasMedia(),
            "recipientId" to message.recipientId,
            "isArchived" to message.isArchived
        )

        // Include media-specific fields for semantic content (not transport-specific)
        if (message.hasMedia() && message.media != null) {
            cordPayload["mediaType"] = message.mediaType.name
            cordPayload["mediaFilename"] = message.media?.fileName
            cordPayload["mediaSize"] = message.media?.fileSize
            // Note: We don't include URLs or upload timestamps as they're transport-specific
        }

        return org.json.JSONObject(cordPayload).toString()
    }

    /**
     * Get delivery marker for cord telemetry.
     */
    private fun getDeliveryMarker(message: Message): String? {
        return when {
            message.isMeshOrigin -> "· sub"
            else -> "· online"
        }
    }
}
