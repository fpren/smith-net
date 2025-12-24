package com.guildofsmiths.trademesh.service

import android.util.Log
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
import java.util.concurrent.ConcurrentHashMap

/**
 * C-10: Foreman Local Hub Mode
 * 
 * Enables a Foreman-role device to act as a local relay and cache hub for
 * mesh messages when the main gateway is unavailable.
 * 
 * Features:
 * - Local message caching for offline peers
 * - Mesh relay amplification (re-broadcast important messages)
 * - Presence aggregation (track nearby workers)
 * - Priority message queuing for gateway sync when reconnected
 */
object ForemanHub {

    private const val TAG = "ForemanHub"
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    // ════════════════════════════════════════════════════════════════════
    // STATE
    // ════════════════════════════════════════════════════════════════════
    
    private val _isHubModeEnabled = MutableStateFlow(false)
    val isHubModeEnabled: StateFlow<Boolean> = _isHubModeEnabled.asStateFlow()
    
    private val _isHubActive = MutableStateFlow(false)
    val isHubActive: StateFlow<Boolean> = _isHubActive.asStateFlow()
    
    private val _connectedPeers = MutableStateFlow<Set<String>>(emptySet())
    val connectedPeers: StateFlow<Set<String>> = _connectedPeers.asStateFlow()
    
    private val _cachedMessageCount = MutableStateFlow(0)
    val cachedMessageCount: StateFlow<Int> = _cachedMessageCount.asStateFlow()
    
    // Message cache for offline peers: peerId -> List<Message>
    private val peerMessageCache = ConcurrentHashMap<String, MutableList<Message>>()
    
    // Pending messages for gateway sync
    private val pendingGatewayMessages = ConcurrentHashMap<String, Message>()
    
    // Peer presence tracking: peerId -> lastSeenTimestamp
    private val peerPresence = ConcurrentHashMap<String, Long>()
    
    // Hub configuration
    private var maxCachePerPeer = 50
    private var cacheRetentionMs = 24 * 60 * 60 * 1000L // 24 hours
    private var relayAmplificationEnabled = true
    
    // ════════════════════════════════════════════════════════════════════
    // HUB CONTROL
    // ════════════════════════════════════════════════════════════════════
    
    /**
     * Enable hub mode (requires Foreman role or higher).
     */
    fun enableHubMode(): Boolean {
        val role = AuthService.getUserRole() ?: "solo"
        
        // Only Foreman, Enterprise, Admin can enable hub mode
        if (role !in listOf("foreman", "enterprise", "admin")) {
            Log.w(TAG, "Hub mode requires Foreman role or higher. Current role: $role")
            return false
        }
        
        _isHubModeEnabled.value = true
        Log.i(TAG, "╔═══════════════════════════════════════╗")
        Log.i(TAG, "║  FOREMAN HUB MODE ENABLED             ║")
        Log.i(TAG, "╚═══════════════════════════════════════╝")
        
        return true
    }
    
    /**
     * Disable hub mode.
     */
    fun disableHubMode() {
        _isHubModeEnabled.value = false
        _isHubActive.value = false
        Log.i(TAG, "Hub mode disabled")
    }
    
    /**
     * Activate the hub (start relaying/caching).
     */
    fun activateHub() {
        if (!_isHubModeEnabled.value) {
            Log.w(TAG, "Cannot activate hub - hub mode not enabled")
            return
        }
        
        _isHubActive.value = true
        Log.i(TAG, "▓▓▓ HUB ACTIVATED - Relaying messages ▓▓▓")
        
        // Clean up old cache entries
        cleanupCache()
    }
    
    /**
     * Deactivate the hub (stop relaying).
     */
    fun deactivateHub() {
        _isHubActive.value = false
        Log.i(TAG, "░░░ HUB DEACTIVATED ░░░")
    }
    
    // ════════════════════════════════════════════════════════════════════
    // MESSAGE HANDLING
    // ════════════════════════════════════════════════════════════════════
    
    /**
     * Called when a mesh message is received.
     * Hub may cache it for offline peers or relay it.
     */
    fun onMeshMessageReceived(message: Message, fromPeerId: String) {
        if (!_isHubActive.value) return
        
        Log.d(TAG, "Hub received message from $fromPeerId: ${message.id}")
        
        // Update peer presence
        peerPresence[fromPeerId] = System.currentTimeMillis()
        updateConnectedPeers()
        
        // Check if message should be cached for other peers
        if (shouldCacheMessage(message)) {
            cacheMessageForOfflinePeers(message, fromPeerId)
        }
        
        // Check if message should be relayed (amplified)
        if (relayAmplificationEnabled && shouldAmplifyMessage(message)) {
            relayMessage(message, excludePeer = fromPeerId)
        }
        
        // Queue for gateway sync if important
        if (shouldQueueForGateway(message)) {
            pendingGatewayMessages[message.id] = message
        }
    }
    
    /**
     * Called when a peer connects to the hub.
     * Deliver any cached messages for them.
     */
    fun onPeerConnected(peerId: String) {
        Log.i(TAG, "Peer connected to hub: $peerId")
        
        peerPresence[peerId] = System.currentTimeMillis()
        updateConnectedPeers()
        
        // Deliver cached messages
        val cached = peerMessageCache.remove(peerId)
        if (!cached.isNullOrEmpty()) {
            Log.i(TAG, "Delivering ${cached.size} cached messages to $peerId")
            scope.launch {
                cached.forEach { message ->
                    deliverCachedMessage(message, peerId)
                }
            }
            updateCacheCount()
        }
    }
    
    /**
     * Called when a peer disconnects.
     */
    fun onPeerDisconnected(peerId: String) {
        Log.d(TAG, "Peer disconnected from hub: $peerId")
        // Don't remove from presence immediately - they might reconnect
    }
    
    /**
     * Called when gateway connection is restored.
     * Sync pending messages.
     */
    fun onGatewayConnected() {
        if (!_isHubActive.value) return
        
        Log.i(TAG, "Gateway connected - syncing ${pendingGatewayMessages.size} pending messages")
        
        scope.launch {
            val messages = pendingGatewayMessages.values.toList()
            pendingGatewayMessages.clear()
            
            messages.forEach { message ->
                try {
                    // Send via gateway
                    GatewayClient.sendMessage(message)
                    Log.d(TAG, "Synced message ${message.id} to gateway")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to sync message to gateway: ${e.message}")
                    // Re-queue on failure
                    pendingGatewayMessages[message.id] = message
                }
            }
        }
    }
    
    // ════════════════════════════════════════════════════════════════════
    // CACHING LOGIC
    // ════════════════════════════════════════════════════════════════════
    
    private fun shouldCacheMessage(message: Message): Boolean {
        // Cache channel messages and important DMs
        return message.channelId.isNotEmpty() || message.recipientId != null
    }
    
    private fun cacheMessageForOfflinePeers(message: Message, fromPeerId: String) {
        // Get all known peers except sender
        val offlinePeers = getOfflinePeers().filter { it != fromPeerId }
        
        offlinePeers.forEach { peerId ->
            val cache = peerMessageCache.getOrPut(peerId) { mutableListOf() }
            
            // Avoid duplicates
            if (cache.none { it.id == message.id }) {
                cache.add(message)
                
                // Enforce max cache size
                while (cache.size > maxCachePerPeer) {
                    cache.removeAt(0)
                }
            }
        }
        
        updateCacheCount()
    }
    
    private fun getOfflinePeers(): List<String> {
        val now = System.currentTimeMillis()
        val offlineThreshold = 5 * 60 * 1000L // 5 minutes
        
        return peerPresence.filter { (_, lastSeen) ->
            now - lastSeen > offlineThreshold
        }.keys.toList()
    }
    
    private fun deliverCachedMessage(message: Message, peerId: String) {
        // Use mesh service to send
        try {
            ChatManager.sendViaMesh(message, targetPeerId = peerId)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to deliver cached message: ${e.message}")
        }
    }
    
    // ════════════════════════════════════════════════════════════════════
    // RELAY AMPLIFICATION
    // ════════════════════════════════════════════════════════════════════
    
    private fun shouldAmplifyMessage(message: Message): Boolean {
        // Amplify important messages (presence, urgent, etc.)
        return message.content.contains("[URGENT]", ignoreCase = true) ||
               message.content.contains("[PRESENCE]", ignoreCase = true) ||
               message.senderId == UserPreferences.getUserId() // Own messages
    }
    
    private fun relayMessage(message: Message, excludePeer: String) {
        Log.d(TAG, "Amplifying message ${message.id}")
        
        scope.launch {
            // Re-broadcast via mesh with TTL decrease
            try {
                ChatManager.relayMessage(message, excludePeerId = excludePeer)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to relay message: ${e.message}")
            }
        }
    }
    
    // ════════════════════════════════════════════════════════════════════
    // GATEWAY SYNC
    // ════════════════════════════════════════════════════════════════════
    
    private fun shouldQueueForGateway(message: Message): Boolean {
        // Queue messages that should be persisted on the backend
        return !GatewayClient.isConnected() && 
               message.channelId.isNotEmpty()
    }
    
    fun getPendingGatewayCount(): Int = pendingGatewayMessages.size
    
    // ════════════════════════════════════════════════════════════════════
    // UTILITIES
    // ════════════════════════════════════════════════════════════════════
    
    private fun updateConnectedPeers() {
        val now = System.currentTimeMillis()
        val activeThreshold = 60 * 1000L // 1 minute
        
        _connectedPeers.value = peerPresence.filter { (_, lastSeen) ->
            now - lastSeen < activeThreshold
        }.keys.toSet()
    }
    
    private fun updateCacheCount() {
        _cachedMessageCount.value = peerMessageCache.values.sumOf { it.size }
    }
    
    private fun cleanupCache() {
        val now = System.currentTimeMillis()
        
        peerMessageCache.forEach { (peerId, messages) ->
            messages.removeAll { message ->
                now - message.timestamp > cacheRetentionMs
            }
        }
        
        // Remove empty peer entries
        peerMessageCache.entries.removeAll { it.value.isEmpty() }
        
        updateCacheCount()
    }
    
    /**
     * Get hub status summary.
     */
    fun getStatus(): HubStatus {
        return HubStatus(
            isEnabled = _isHubModeEnabled.value,
            isActive = _isHubActive.value,
            connectedPeerCount = _connectedPeers.value.size,
            cachedMessageCount = _cachedMessageCount.value,
            pendingGatewayCount = pendingGatewayMessages.size
        )
    }
    
    data class HubStatus(
        val isEnabled: Boolean,
        val isActive: Boolean,
        val connectedPeerCount: Int,
        val cachedMessageCount: Int,
        val pendingGatewayCount: Int
    )
}
