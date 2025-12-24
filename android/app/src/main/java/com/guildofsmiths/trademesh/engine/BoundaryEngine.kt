package com.guildofsmiths.trademesh.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.guildofsmiths.trademesh.data.BeaconRepository
import com.guildofsmiths.trademesh.data.Channel
import com.guildofsmiths.trademesh.data.ChannelType
import com.guildofsmiths.trademesh.data.MediaAttachment
import com.guildofsmiths.trademesh.data.MediaType
import com.guildofsmiths.trademesh.data.Message
import com.guildofsmiths.trademesh.data.MessageRepository
import com.guildofsmiths.trademesh.data.Peer
import com.guildofsmiths.trademesh.data.PeerRepository
import com.guildofsmiths.trademesh.data.UserPreferences
import com.guildofsmiths.trademesh.service.ChatManager
import com.guildofsmiths.trademesh.service.GatewayClient
import com.guildofsmiths.trademesh.service.MeshService
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
    private val _isOnline = MutableStateFlow(false)
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
        
        // Initialize ChatManager with backend URL for online messaging
        ChatManager.setBackendUrl("http://192.168.8.163:3000")
        
        // Auto-connect to online chat (for receiving messages)
        ChatManager.connect()
        Log.i(TAG, "🌐 Auto-connecting to online chat for receiving messages")
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
        
        GatewayClient.setBackendUrl(backendUrl)
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
        if (GatewayClient.isConnected()) {
            GatewayClient.forwardMeshMessage(message)
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
        // Phase 0: Always use mesh for testing
        if (forceMeshMode) {
            Log.d(TAG, "Force mesh mode enabled - using mesh")
            return true
        }
        
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true // Default to mesh if we can't check
        
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
        
        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        
        val useMesh = !hasInternet
        Log.d(TAG, "Connectivity check: hasInternet=$hasInternet, useMesh=$useMesh")
        
        return useMesh
    }
    
    /**
     * Check if we have IP connectivity for media transmission.
     */
    fun hasIpConnectivity(context: Context): Boolean {
        // In Phase 0 with force mesh mode, we're "offline" for media
        if (forceMeshMode) {
            return false
        }
        return !shouldUseMesh(context)
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
     * - Text messages: Mesh (offline) or Chat (online)
     * - Media messages: Chat only (queued if offline)
     * 
     * Message is always added to local repository for immediate display.
     */
    fun routeMessage(context: Context, message: Message) {
        // Always add to local repository first for immediate UI feedback
        MessageRepository.addMessage(message)
        
        // Media messages require IP - queue if offline
        if (message.hasMedia()) {
            routeMediaMessage(context, message)
            return
        }
        
        // Text messages can go via mesh or chat
        if (shouldUseMesh(context)) {
            routeViaMesh(message)
        } else {
            routeViaChat(message)
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
     * Route message via IP chat path (stub for Phase 0).
     */
    private fun routeViaChat(message: Message) {
        Log.d(TAG, "Routing via chat: ${message.id.take(8)}...")
        ChatManager.sendMessage(message)
    }
    
    // Track recently injected message IDs to avoid re-forwarding our own broadcasts
    private val recentlyInjectedIds = mutableSetOf<String>()
    private const val MAX_INJECTED_IDS = 50
    
    /**
     * Handle incoming mesh message from BLE scan.
     * Called by MeshService when a peer message is received.
     */
    fun onMeshMessageReceived(message: Message, rssi: Int = 0) {
        Log.d(TAG, "Mesh message received: ${message.id.take(8)} from ${message.senderName}")
        
        // Track the peer (always, even for heartbeats)
        PeerRepository.onPeerSeen(message.senderId, message.senderName, rssi)
        
        // Filter out heartbeat/ping messages - they're only for presence tracking
        if (isPresenceMessage(message.content)) {
            Log.d(TAG, "   (presence message - not displaying)")
            return
        }
        
        // Don't forward messages that WE just injected (prevents echo loop)
        val myUserId = UserPreferences.getUserId()
        if (recentlyInjectedIds.contains(message.id) || message.senderId == myUserId) {
            Log.d(TAG, "   (own message or recently injected - not re-forwarding to backend)")
            // Still add to local repo for display
            MessageRepository.addMessage(message)
            return
        }
        
        // Add to repository - deduplication handled there
        MessageRepository.addMessage(message)
        
        // Forward to gateway if connected (bridge mesh → online)
        forwardToGateway(message)
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
            syncQueuedMedia(context)
        }
        
        lastConnectivityState = shouldUseMesh(context)
        _isOnline.value = isOnlineNow
    }
    
    /**
     * Sync all pending mesh messages to chat backend.
     * Preserves message ordering and attribution.
     */
    private fun syncMeshMessagesToChat() {
        val pendingMessages = MessageRepository.getPendingSyncMessages()
        if (pendingMessages.isEmpty()) {
            Log.d(TAG, "No pending mesh messages to sync")
            return
        }
        
        Log.i(TAG, "Syncing ${pendingMessages.size} mesh messages to chat")
        
        val syncedIds = mutableSetOf<String>()
        for (message in pendingMessages.sortedBy { it.timestamp }) {
            // Re-send via chat path, preserving original metadata
            val chatMessage = message.copy(isMeshOrigin = false)
            ChatManager.sendMessage(chatMessage)
            syncedIds.add(message.id)
        }
        
        // Mark as synced to prevent re-sync
        MessageRepository.markAsSynced(syncedIds)
        Log.i(TAG, "Mesh sync completed: ${syncedIds.size} messages")
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
        
        if (lastConnectivityState != null && lastConnectivityState != currentState) {
            if (!currentState) {
                // Transitioned from offline to online
                onConnectivityRestored(context)
            }
        }
        
        lastConnectivityState = currentState
        _isOnline.value = !currentState
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
            GatewayClient.setBackendUrl(url)
            GatewayClient.connect()
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
     * Request channel discovery from nearby peers.
     * This triggers a BLE scan refresh and broadcasts a discovery request.
     * Nearby channel owners will re-broadcast their channel invites.
     */
    fun requestChannelDiscovery() {
        Log.i(TAG, "════════════════════════════════════════")
        Log.i(TAG, "🔍 CHANNEL DISCOVERY REQUESTED")
        
        // Restart BLE scanning to pick up any new broadcasts
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
        
        // Also broadcast our own channels so others can discover them
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
     */
    fun onChannelInviteReceived(channelHash: Int, channelName: String, senderName: String) {
        // Don't show invite if already a member
        if (channelMembership.containsKey(channelHash)) {
            Log.d(TAG, "Already member of channel #$channelName - ignoring invite")
            return
        }
        
        Log.i(TAG, "Received invite to #$channelName from $senderName")
        _pendingInvites.value = _pendingInvites.value + (channelHash to Pair(channelName, senderName))
    }
    
    /**
     * Accept a channel invite.
     */
    fun acceptInvite(channelHash: Int): String? {
        val invite = _pendingInvites.value[channelHash] ?: return null
        val (channelName, _) = invite
        
        // Create and join the channel
        val channelId = channelName.lowercase().replace(" ", "-")
        val channel = Channel(
            id = channelId,
            beaconId = "default",
            name = channelName,
            type = ChannelType.GROUP
        )
        BeaconRepository.addChannel("default", channel)
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
}
