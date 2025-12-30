package com.guildofsmiths.trademesh.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Repository for managing Beacons and Channels.
 * Now with SharedPreferences persistence so channels survive app restarts.
 */
object BeaconRepository {
    
    private const val TAG = "BeaconRepository"
    private const val PREFS_NAME = "beacon_repository"
    private const val KEY_CHANNELS = "saved_channels"
    
    private var prefs: SharedPreferences? = null
    
    private val _beacons = MutableStateFlow<List<Beacon>>(listOf(createDefaultBeacon()))
    val beacons: StateFlow<List<Beacon>> = _beacons.asStateFlow()
    
    private val _activeBeacon = MutableStateFlow<Beacon?>(null)
    val activeBeacon: StateFlow<Beacon?> = _activeBeacon.asStateFlow()
    
    private val _activeChannel = MutableStateFlow<Channel?>(null)
    val activeChannel: StateFlow<Channel?> = _activeChannel.asStateFlow()
    
    /**
     * Initialize repository with context for persistence.
     * Call this in Application.onCreate() AFTER UserPreferences.init()
     */
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadSavedChannels()
        
        // Set default beacon as active
        _activeBeacon.value = _beacons.value.firstOrNull()
        _activeChannel.value = _activeBeacon.value?.channels?.firstOrNull()
        
        Log.i(TAG, "✅ BeaconRepository initialized with ${_beacons.value.firstOrNull()?.channels?.size ?: 0} channels")
    }
    
    /**
     * Load saved channels from SharedPreferences
     */
    private fun loadSavedChannels() {
        val savedJson = prefs?.getString(KEY_CHANNELS, null) ?: return
        
        try {
            val channelsArray = JSONArray(savedJson)
            val loadedChannels = mutableListOf<Channel>()
            
            // Always include general channel
            loadedChannels.add(Channel(
                id = "general",
                beaconId = "default",
                name = "general",
                type = ChannelType.BROADCAST
            ))
            
            for (i in 0 until channelsArray.length()) {
                val channelJson = channelsArray.getJSONObject(i)
                val channelId = channelJson.getString("id")
                
                // Skip general (already added) and deleted channels
                if (channelId == "general") continue
                if (channelJson.optBoolean("isDeleted", false)) continue
                
                val channel = Channel(
                    id = channelId,
                    beaconId = channelJson.optString("beaconId", "default"),
                    name = channelJson.getString("name"),
                    type = ChannelType.valueOf(channelJson.optString("type", "GROUP").uppercase()),
                    creatorId = channelJson.optString("creatorId", ""),
                    createdAt = channelJson.optLong("createdAt", System.currentTimeMillis()),
                    isArchived = channelJson.optBoolean("isArchived", false),
                    isDeleted = false
                )
                loadedChannels.add(channel)
                Log.d(TAG, "   Loaded channel: #${channel.name} (${channel.id})")
            }
            
            // Update beacons with loaded channels
            _beacons.value = listOf(
                Beacon(
                    id = "default",
                    name = "smith net",
                    description = "local mesh network",
                    serviceUuid = UUID.fromString("0000F00D-0000-1000-8000-00805F9B34FB"),
                    channels = loadedChannels
                )
            )
            
            Log.i(TAG, "📂 Loaded ${loadedChannels.size} channels from storage")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load saved channels", e)
        }
    }
    
    /**
     * Save current channels to SharedPreferences
     */
    private fun saveChannels() {
        val channels = _beacons.value.firstOrNull()?.channels ?: return
        
        try {
            val channelsArray = JSONArray()
            
            for (channel in channels) {
                // Skip general channel (always recreated)
                if (channel.id == "general") continue
                
                val channelJson = JSONObject().apply {
                    put("id", channel.id)
                    put("beaconId", channel.beaconId)
                    put("name", channel.name)
                    put("type", channel.type.name)
                    put("creatorId", channel.creatorId)
                    put("createdAt", channel.createdAt)
                    put("isArchived", channel.isArchived)
                    put("isDeleted", channel.isDeleted)
                }
                channelsArray.put(channelJson)
            }
            
            prefs?.edit()?.putString(KEY_CHANNELS, channelsArray.toString())?.apply()
            Log.d(TAG, "💾 Saved ${channelsArray.length()} channels to storage")
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save channels", e)
        }
    }
    
    /**
     * Create the default beacon with general channel only.
     */
    private fun createDefaultBeacon(): Beacon {
        val beaconId = "default"
        return Beacon(
            id = beaconId,
            name = "smith net",
            description = "local mesh network",
            serviceUuid = UUID.fromString("0000F00D-0000-1000-8000-00805F9B34FB"),
            channels = listOf(
                Channel(
                    id = "general",
                    beaconId = beaconId,
                    name = "general",
                    type = ChannelType.BROADCAST
                )
            )
        )
    }
    
    // ════════════════════════════════════════
    // BEACON OPERATIONS
    // ════════════════════════════════════════
    
    /**
     * Get all beacons.
     */
    fun getAllBeacons(): List<Beacon> = _beacons.value
    
    /**
     * Get a beacon by ID.
     */
    fun getBeacon(beaconId: String): Beacon? {
        return _beacons.value.find { it.id == beaconId }
    }
    
    /**
     * Add a new beacon.
     */
    fun addBeacon(beacon: Beacon) {
        _beacons.update { current ->
            if (current.any { it.id == beacon.id }) current
            else current + beacon
        }
    }
    
    /**
     * Create and add a new beacon.
     */
    fun createBeacon(name: String, description: String = ""): Beacon {
        val beacon = Beacon.create(name, description)
        addBeacon(beacon)
        return beacon
    }
    
    /**
     * Remove a beacon.
     */
    fun removeBeacon(beaconId: String) {
        _beacons.update { current ->
            current.filter { it.id != beaconId }
        }
        // Clear active if removed
        if (_activeBeacon.value?.id == beaconId) {
            _activeBeacon.value = _beacons.value.firstOrNull()
            _activeChannel.value = _activeBeacon.value?.channels?.firstOrNull()
        }
    }
    
    /**
     * Set the active beacon.
     */
    fun setActiveBeacon(beaconId: String) {
        val beacon = getBeacon(beaconId)
        if (beacon != null) {
            _activeBeacon.value = beacon
            _activeChannel.value = beacon.channels.firstOrNull()
        }
    }
    
    /**
     * Get the currently active beacon.
     */
    fun getActiveBeacon(): Beacon? = _activeBeacon.value
    
    // ════════════════════════════════════════
    // CHANNEL OPERATIONS
    // ════════════════════════════════════════
    
    /**
     * Get all channels for a beacon.
     */
    fun getChannels(beaconId: String): List<Channel> {
        return getBeacon(beaconId)?.channels ?: emptyList()
    }
    
    /**
     * Get a channel by ID within a beacon.
     */
    fun getChannel(beaconId: String, channelId: String): Channel? {
        return getBeacon(beaconId)?.channels?.find { it.id == channelId }
    }
    
    /**
     * Add a channel to a beacon.
     */
    fun addChannel(beaconId: String, channel: Channel) {
        var wasAdded = false
        _beacons.update { beacons ->
            beacons.map { beacon ->
                if (beacon.id == beaconId) {
                    val updatedChannel = channel.copy(beaconId = beaconId)
                    if (beacon.channels.any { it.id == channel.id }) {
                        beacon // Already exists
                    } else {
                        wasAdded = true
                        beacon.copy(channels = beacon.channels + updatedChannel)
                    }
                } else beacon
            }
        }
        // Update active beacon reference if needed
        if (_activeBeacon.value?.id == beaconId) {
            _activeBeacon.value = getBeacon(beaconId)
        }
        // Persist to storage
        if (wasAdded) {
            saveChannels()
            Log.i(TAG, "✅ Channel added and saved: #${channel.name} (${channel.id})")
        }
    }
    
    /**
     * Create a group channel in a beacon with access control.
     */
    fun createGroupChannel(
        beaconId: String,
        name: String,
        creatorId: String,
        visibility: ChannelVisibility = ChannelVisibility.PUBLIC,
        members: List<String> = emptyList(),
        requiresApproval: Boolean = false
    ): Channel {
        val channel = Channel.createGroup(name, beaconId, creatorId, visibility, members, requiresApproval)
        addChannel(beaconId, channel)
        return channel
    }
    
    /**
     * Create or get a DM channel between two users.
     */
    fun getOrCreateDM(beaconId: String, myUserId: String, otherUserId: String, otherUserName: String): Channel {
        val dmId = "dm_${listOf(myUserId, otherUserId).sorted().joinToString("_")}"
        
        // Check if DM already exists
        val existing = getChannel(beaconId, dmId)
        if (existing != null) return existing
        
        // Create new DM
        val dm = Channel.createDM(otherUserId, otherUserName, beaconId, myUserId)
        addChannel(beaconId, dm)
        return dm
    }
    
    /**
     * Remove a channel from a beacon (hard delete - removes completely).
     */
    fun removeChannel(beaconId: String, channelId: String) {
        _beacons.update { beacons ->
            beacons.map { beacon ->
                if (beacon.id == beaconId) {
                    beacon.copy(channels = beacon.channels.filter { it.id != channelId })
                } else beacon
            }
        }
        // Persist to storage
        saveChannels()
        Log.i(TAG, "🗑️ Channel removed: $channelId")
        
        // Update active references
        if (_activeBeacon.value?.id == beaconId) {
            _activeBeacon.value = getBeacon(beaconId)
            if (_activeChannel.value?.id == channelId) {
                _activeChannel.value = _activeBeacon.value?.channels?.firstOrNull()
            }
        }
    }
    
    /**
     * Delete a channel (soft delete - marks as deleted with tombstone).
     * Any user can delete channels from their local view.
     * This is a LOCAL operation - it hides the channel for this user only.
     */
    fun deleteChannel(beaconId: String, channelId: String, userId: String): Boolean {
        val channel = getChannel(beaconId, channelId) ?: return false
        
        // Don't allow deleting system channels like #general
        if (channel.id == "general") return false
        
        _beacons.update { beacons ->
            beacons.map { beacon ->
                if (beacon.id == beaconId) {
                    beacon.copy(channels = beacon.channels.map { ch ->
                        if (ch.id == channelId) {
                            ch.copy(
                                isDeleted = true,
                                lastMessagePreview = "[deleted]",
                                lastMessageTime = System.currentTimeMillis()
                            )
                        } else ch
                    })
                } else beacon
            }
        }
        refreshActiveReferences(beaconId, channelId)
        saveChannels() // Persist deletion
        Log.i(TAG, "🗑️ Channel deleted: #${channel.name}")
        return true
    }
    
    /**
     * Archive a channel (hide from active list but preserve history).
     * Any user can archive channels from their local view.
     */
    fun archiveChannel(beaconId: String, channelId: String, userId: String): Boolean {
        val channel = getChannel(beaconId, channelId) ?: return false
        
        // Don't allow archiving system channels
        if (channel.id == "general") return false
        
        _beacons.update { beacons ->
            beacons.map { beacon ->
                if (beacon.id == beaconId) {
                    beacon.copy(channels = beacon.channels.map { ch ->
                        if (ch.id == channelId) ch.copy(isArchived = true) else ch
                    })
                } else beacon
            }
        }
        refreshActiveReferences(beaconId, channelId)
        saveChannels() // Persist archive state
        Log.i(TAG, "📦 Channel archived: #${channel.name}")
        return true
    }
    
    /**
     * Unarchive a channel (restore to active list).
     * Any user can unarchive channels in their local view.
     */
    fun unarchiveChannel(beaconId: String, channelId: String, userId: String): Boolean {
        val channel = getChannel(beaconId, channelId) ?: return false
        
        _beacons.update { beacons ->
            beacons.map { beacon ->
                if (beacon.id == beaconId) {
                    beacon.copy(channels = beacon.channels.map { ch ->
                        if (ch.id == channelId) ch.copy(isArchived = false) else ch
                    })
                } else beacon
            }
        }
        refreshActiveReferences(beaconId, channelId)
        saveChannels() // Persist unarchive state
        Log.i(TAG, "📂 Channel unarchived: #${channel.name}")
        return true
    }
    
    /**
     * Get visible (non-archived, non-deleted) channels for a beacon.
     */
    fun getVisibleChannels(beaconId: String): List<Channel> {
        return getBeacon(beaconId)?.channels?.filter { it.isVisible() } ?: emptyList()
    }

    /**
     * Get channels a user can see (respects visibility permissions).
     */
    fun getAccessibleChannels(beaconId: String, userId: String): List<Channel> {
        return getChannels(beaconId).filter { it.canSeeInList(userId) }
    }

    /**
     * Update a channel's access control settings.
     */
    private fun updateChannelAccess(beaconId: String, channelId: String, updater: (Channel) -> Channel) {
        _beacons.update { beacons ->
            beacons.map { beacon ->
                if (beacon.id == beaconId) {
                    beacon.copy(channels = beacon.channels.map { channel ->
                        if (channel.id == channelId) {
                            updater(channel)
                        } else channel
                    })
                } else beacon
            }
        }
        saveChannels()
    }

    /**
     * Request access to a private channel.
     */
    fun requestChannelAccess(beaconId: String, channelId: String, userId: String): Boolean {
        val channel = getChannel(beaconId, channelId) ?: return false

        if (!channel.canRequestAccess(userId)) return false

        // Add user to pending requests
        updateChannelAccess(beaconId, channelId) { ch ->
            ch.withUpdatedAccess(pendingRequests = ch.pendingRequests + userId)
        }
        Log.i(TAG, "📝 Access request submitted: $userId for #$channelId")
        return true
    }

    /**
     * Approve or deny a channel access request.
     */
    fun respondToAccessRequest(
        beaconId: String,
        channelId: String,
        requesterId: String,
        managerId: String,
        approve: Boolean
    ): Boolean {
        val channel = getChannel(beaconId, channelId) ?: return false

        // Only channel managers can approve/deny
        if (!channel.canManage(managerId)) return false

        // Remove from pending
        val newPending = channel.pendingRequests - requesterId

        if (approve) {
            // Add to members
            updateChannelAccess(beaconId, channelId) { ch ->
                ch.withUpdatedAccess(
                    members = ch.members + requesterId,
                    pendingRequests = newPending
                )
            }
            Log.i(TAG, "✅ Access approved: $requesterId for #$channelId by $managerId")
        } else {
            // Just remove from pending (denied)
            updateChannelAccess(beaconId, channelId) { ch ->
                ch.withUpdatedAccess(pendingRequests = newPending)
            }
            Log.i(TAG, "❌ Access denied: $requesterId for #$channelId by $managerId")
        }
        return true
    }

    /**
     * Add a user to a channel's allowed/blocked list.
     */
    fun updateChannelUserAccess(
        beaconId: String,
        channelId: String,
        userId: String,
        managerId: String,
        allow: Boolean
    ): Boolean {
        val channel = getChannel(beaconId, channelId) ?: return false

        // Only managers can update access
        if (!channel.canManage(managerId)) return false

        if (allow) {
            // Add to allowed, remove from blocked
            updateChannelAccess(beaconId, channelId) { ch ->
                ch.withUpdatedAccess(
                    allowedUsers = if (ch.allowedUsers.contains(userId)) ch.allowedUsers else ch.allowedUsers + userId,
                    blockedUsers = ch.blockedUsers - userId
                )
            }
            Log.i(TAG, "✅ User allowed: $userId in #$channelId by $managerId")
        } else {
            // Add to blocked, remove from allowed/members
            updateChannelAccess(beaconId, channelId) { ch ->
                ch.withUpdatedAccess(
                    allowedUsers = ch.allowedUsers - userId,
                    blockedUsers = if (ch.blockedUsers.contains(userId)) ch.blockedUsers else ch.blockedUsers + userId,
                    members = ch.members - userId
                )
            }
            Log.i(TAG, "🚫 User blocked: $userId from #$channelId by $managerId")
        }
        return true
    }

    /**
     * Update channel visibility settings.
     */
    fun updateChannelVisibility(
        beaconId: String,
        channelId: String,
        managerId: String,
        visibility: ChannelVisibility,
        requiresApproval: Boolean = false
    ): Boolean {
        val channel = getChannel(beaconId, channelId) ?: return false

        // Only managers can update visibility
        if (!channel.canManage(managerId)) return false

        updateChannelAccess(beaconId, channelId) { ch ->
            ch.withUpdatedAccess(
                visibility = visibility,
                requiresApproval = requiresApproval
            )
        }
        Log.i(TAG, "🔒 Channel visibility updated: #$channelId to $visibility by $managerId")
        return true
    }
    
    /**
     * Get archived channels for a beacon.
     */
    fun getArchivedChannels(beaconId: String): List<Channel> {
        return getBeacon(beaconId)?.channels?.filter { it.isArchived && !it.isDeleted } ?: emptyList()
    }
    
    /**
     * Helper to refresh active references after channel updates.
     */
    private fun refreshActiveReferences(beaconId: String, channelId: String) {
        if (_activeBeacon.value?.id == beaconId) {
            _activeBeacon.value = getBeacon(beaconId)
            if (_activeChannel.value?.id == channelId) {
                _activeChannel.value = _activeBeacon.value?.channels?.find { it.id == channelId }
            }
        }
    }
    
    /**
     * Set the active channel.
     */
    fun setActiveChannel(channelId: String) {
        val beacon = _activeBeacon.value ?: return
        val channel = beacon.channels.find { it.id == channelId }
        if (channel != null) {
            _activeChannel.value = channel
        }
    }
    
    /**
     * Get the currently active channel.
     */
    fun getActiveChannel(): Channel? = _activeChannel.value
    
    /**
     * Update channel with new message info (for preview/unread).
     */
    fun updateChannelLastMessage(beaconId: String, channelId: String, preview: String, time: Long, incrementUnread: Boolean = false) {
        _beacons.update { beacons ->
            beacons.map { beacon ->
                if (beacon.id == beaconId) {
                    beacon.copy(channels = beacon.channels.map { channel ->
                        if (channel.id == channelId) {
                            channel.copy(
                                lastMessagePreview = preview.take(50),
                                lastMessageTime = time,
                                unreadCount = if (incrementUnread) channel.unreadCount + 1 else channel.unreadCount
                            )
                        } else channel
                    })
                } else beacon
            }
        }
        // Update active references
        if (_activeBeacon.value?.id == beaconId) {
            _activeBeacon.value = getBeacon(beaconId)
            if (_activeChannel.value?.id == channelId) {
                _activeChannel.value = _activeBeacon.value?.channels?.find { it.id == channelId }
            }
        }
    }
    
    /**
     * Clear unread count for a channel.
     */
    fun clearUnread(beaconId: String, channelId: String) {
        _beacons.update { beacons ->
            beacons.map { beacon ->
                if (beacon.id == beaconId) {
                    beacon.copy(channels = beacon.channels.map { channel ->
                        if (channel.id == channelId) {
                            channel.copy(unreadCount = 0)
                        } else channel
                    })
                } else beacon
            }
        }
    }
    
    /**
     * Clear all data (for testing).
     */
    fun clear() {
        _beacons.value = listOf(createDefaultBeacon())
        _activeBeacon.value = _beacons.value.firstOrNull()
        _activeChannel.value = _activeBeacon.value?.channels?.firstOrNull()
    }
}
