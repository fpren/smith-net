package com.guildofsmiths.trademesh.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Represents a discovered peer on the mesh network.
 */
data class Peer(
    val userId: String,
    val userName: String,
    val lastSeen: Long = System.currentTimeMillis(),
    val rssi: Int = 0, // Signal strength
    val messageCount: Int = 0
) {
    /**
     * Check if peer was seen recently (within timeout).
     */
    fun isActive(timeoutMs: Long = 60_000): Boolean {
        return System.currentTimeMillis() - lastSeen < timeoutMs
    }
    
    /**
     * Get signal strength description.
     */
    fun signalStrength(): String = when {
        rssi >= -50 -> "Excellent"
        rssi >= -60 -> "Good"
        rssi >= -70 -> "Fair"
        rssi >= -80 -> "Weak"
        else -> "Poor"
    }
    
    /**
     * Get time since last seen as readable string.
     */
    fun lastSeenAgo(): String {
        val diff = System.currentTimeMillis() - lastSeen
        return when {
            diff < 10_000 -> "now"
            diff < 60_000 -> "${diff / 1000}s ago"
            diff < 3600_000 -> "${diff / 60_000}m ago"
            else -> "${diff / 3600_000}h ago"
        }
    }
}

/**
 * Repository for tracking discovered peers on the mesh.
 */
object PeerRepository {
    
    private val _peers = MutableStateFlow<Map<String, Peer>>(emptyMap())
    val peers: StateFlow<Map<String, Peer>> = _peers.asStateFlow()
    
    /** Timeout for considering a peer "active" (75 seconds - ~2x heartbeat interval) */
    private const val PEER_ACTIVE_TIMEOUT_MS = 75_000L
    
    /** Timeout for removing stale peers (10 minutes - plenty of buffer) */
    private const val PEER_STALE_TIMEOUT_MS = 600_000L
    
    /**
     * Update or add a peer based on received message.
     */
    fun onPeerSeen(userId: String, userName: String, rssi: Int = 0) {
        _peers.update { current ->
            val existing = current[userId]
            val updated = Peer(
                userId = userId,
                userName = userName,
                lastSeen = System.currentTimeMillis(),
                rssi = rssi,
                messageCount = (existing?.messageCount ?: 0) + 1
            )
            current + (userId to updated)
        }
    }
    
    /**
     * Get list of active peers (seen recently).
     */
    fun getActivePeers(): List<Peer> {
        return _peers.value.values
            .filter { it.isActive(PEER_ACTIVE_TIMEOUT_MS) }
            .sortedByDescending { it.lastSeen }
    }
    
    /**
     * Get all known peers.
     */
    fun getAllPeers(): List<Peer> {
        return _peers.value.values.sortedByDescending { it.lastSeen }
    }
    
    /**
     * Get a specific peer by ID.
     */
    fun getPeer(userId: String): Peer? = _peers.value[userId]
    
    /**
     * Get count of active peers.
     */
    fun getActivePeerCount(): Int {
        return _peers.value.values.count { it.isActive(PEER_ACTIVE_TIMEOUT_MS) }
    }
    
    /**
     * Remove stale peers (not seen for a long time).
     */
    fun pruneStale() {
        _peers.update { current ->
            current.filter { (_, peer) ->
                System.currentTimeMillis() - peer.lastSeen < PEER_STALE_TIMEOUT_MS
            }
        }
    }
    
    /**
     * Clear all peers.
     */
    fun clear() {
        _peers.value = emptyMap()
    }
}
