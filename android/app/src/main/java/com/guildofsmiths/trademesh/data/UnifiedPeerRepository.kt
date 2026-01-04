package com.guildofsmiths.trademesh.data

import android.util.Log
import com.guildofsmiths.trademesh.service.SupabaseChat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

/**
 * Unified Peer Repository
 * Combines online and mesh peer detection into single interface
 */
object UnifiedPeerRepository {

    private const val TAG = "UnifiedPeerRepo"

    // Individual peer sources
    private val meshPeers = PeerRepository.peers
    private val onlinePeers = MutableStateFlow<Map<String, OnlinePeer>>(emptyMap())

    // Unified peer state
    private val _unifiedPeers = MutableStateFlow<Map<String, UnifiedPeer>>(emptyMap())
    val unifiedPeers: StateFlow<Map<String, UnifiedPeer>> = _unifiedPeers.asStateFlow()

    // Available peers for collaboration (online + mesh)
    val availablePeers: StateFlow<List<UnifiedPeer>> = combine(
        _unifiedPeers,
        PeerRepository.peers
    ) { unified, mesh ->
        unified.values.filter { peer ->
            // Available if online, mesh, or hybrid connected
            peer.connectionType != ConnectionType.OFFLINE &&
            peer.isAvailableForJobs
        }.sortedByDescending { it.lastSeen }
    }.asStateFlow()

    init {
        // Listen for online peer changes
        SupabaseChat.onPresenceUpdate { userId, userName, status ->
            updateOnlinePeer(userId, userName, status)
        }

        // Listen for mesh peer changes and combine
        PeerRepository.peers.collect { meshPeers ->
            mergePeerSources()
        }
    }

    /**
     * Update online peer presence
     */
    fun updateOnlinePeer(userId: String, userName: String, status: PeerStatus) {
        Log.d(TAG, "Online peer update: $userName ($userId) -> $status")

        _unifiedPeers.update { current ->
            val existing = current[userId]
            val onlinePeer = OnlinePeer(
                userId = userId,
                userName = userName,
                status = status,
                lastSeen = System.currentTimeMillis()
            )

            current + (userId to createUnifiedPeer(
                userId = userId,
                onlinePeer = onlinePeer,
                meshPeer = PeerRepository.getPeer(userId)
            ))
        }
    }

    /**
     * Update mesh peer presence
     */
    fun updateMeshPeer(userId: String, userName: String, rssi: Int) {
        Log.d(TAG, "Mesh peer update: $userName ($userId) RSSI: $rssi")

        _unifiedPeers.update { current ->
            val meshPeer = Peer(
                userId = userId,
                userName = userName,
                rssi = rssi,
                lastSeen = System.currentTimeMillis()
            )

            current + (userId to createUnifiedPeer(
                userId = userId,
                onlinePeer = null, // Will be merged if exists
                meshPeer = meshPeer
            ))
        }
    }

    /**
     * Link mesh and online identities for same user
     */
    fun linkNetworks(userId: String, userName: String) {
        Log.i(TAG, "🔗 Linking networks for peer: $userName ($userId)")

        _unifiedPeers.update { current ->
            val existing = current[userId]
            if (existing != null) {
                // Update with linked information
                current + (userId to existing.copy(
                    userName = userName, // Use most recent name
                    connectionType = ConnectionType.HYBRID
                ))
            } else {
                current
            }
        }
    }

    /**
     * Get peers available for job collaboration
     */
    fun getAvailableCollaborators(currentUserId: String): List<UnifiedPeer> {
        return _unifiedPeers.value.values
            .filter { peer ->
                // Exclude current user
                peer.userId != currentUserId &&
                // Must be reachable
                peer.connectionType != ConnectionType.OFFLINE &&
                // Must be available for jobs
                peer.isAvailableForJobs
            }
            .sortedByDescending { it.lastSeen }
    }

    /**
     * Mark peer as busy/unavailable for jobs
     */
    fun updatePeerAvailability(userId: String, available: Boolean, currentJobCount: Int = 0) {
        _unifiedPeers.update { current ->
            val existing = current[userId]
            if (existing != null) {
                current + (userId to existing.copy(
                    isAvailableForJobs = available,
                    currentJobCount = currentJobCount
                ))
            } else {
                current
            }
        }
    }

    /**
     * Create unified peer from online and mesh sources
     */
    private fun createUnifiedPeer(
        userId: String,
        onlinePeer: OnlinePeer?,
        meshPeer: Peer?
    ): UnifiedPeer {
        // Determine connection type
        val hasOnline = onlinePeer?.status == PeerStatus.ONLINE
        val hasMesh = meshPeer != null && meshPeer.isActive()

        val connectionType = when {
            hasOnline && hasMesh -> ConnectionType.HYBRID
            hasOnline -> ConnectionType.ONLINE_ONLY
            hasMesh -> ConnectionType.MESH_ONLY
            else -> ConnectionType.OFFLINE
        }

        // Use most recent information
        val userName = onlinePeer?.userName ?: meshPeer?.userName ?: userId
        val lastSeen = maxOf(
            onlinePeer?.lastSeen ?: 0L,
            meshPeer?.lastSeen ?: 0L,
            System.currentTimeMillis()
        )

        return UnifiedPeer(
            userId = userId,
            userName = userName,
            meshStatus = if (hasMesh) PeerStatus.AVAILABLE else PeerStatus.OFFLINE,
            meshRssi = meshPeer?.rssi,
            meshLastSeen = meshPeer?.lastSeen,
            onlineStatus = onlinePeer?.status ?: PeerStatus.OFFLINE,
            onlineLastSeen = onlinePeer?.lastSeen,
            connectionType = connectionType,
            lastSeen = lastSeen,
            isAvailableForJobs = true, // Default to available
            currentJobCount = 0
        )
    }

    /**
     * Merge peer sources and update unified state
     */
    private fun mergePeerSources() {
        val currentUnified = _unifiedPeers.value.toMutableMap()
        val meshPeers = PeerRepository.getActivePeers()
        val onlinePeers = SupabaseChat.getAllUsers()

        // Update from mesh peers
        meshPeers.forEach { meshPeer ->
            currentUnified[meshPeer.userId] = createUnifiedPeer(
                userId = meshPeer.userId,
                onlinePeer = onlinePeers.find { it.userId == meshPeer.userId },
                meshPeer = meshPeer
            )
        }

        // Update from online peers (that might not be in mesh)
        onlinePeers.forEach { onlinePeer ->
            if (onlinePeer.userId !in currentUnified) {
                currentUnified[onlinePeer.userId] = createUnifiedPeer(
                    userId = onlinePeer.userId,
                    onlinePeer = onlinePeer,
                    meshPeer = null
                )
            }
        }

        _unifiedPeers.value = currentUnified
    }
}

/**
 * Online peer data structure
 */
data class OnlinePeer(
    val userId: String,
    val userName: String,
    val status: PeerStatus,
    val lastSeen: Long = System.currentTimeMillis()
)

/**
 * Unified peer combining online and mesh information
 */
data class UnifiedPeer(
    val userId: String,
    val userName: String,

    // Mesh connectivity
    val meshStatus: PeerStatus = PeerStatus.OFFLINE,
    val meshRssi: Int? = null,
    val meshLastSeen: Long? = null,

    // Online connectivity
    val onlineStatus: PeerStatus = PeerStatus.OFFLINE,
    val onlineLastSeen: Long? = null,

    // Combined status
    val connectionType: ConnectionType,
    val lastSeen: Long,

    // Collaboration availability
    val isAvailableForJobs: Boolean = false,
    val skills: List<String> = emptyList(),
    val currentJobCount: Int = 0
)

/**
 * Connection type combining online and mesh
 */
enum class ConnectionType {
    OFFLINE,      // Not reachable on any network
    MESH_ONLY,    // BLE mesh only
    ONLINE_ONLY,  // Internet/WebSocket only
    HYBRID        // Both networks available
}

/**
 * Peer status (online/offline/away)
 */
enum class PeerStatus {
    OFFLINE,    // Not detected
    AWAY,       // Detected but not actively responding
    AVAILABLE   // Actively available
}