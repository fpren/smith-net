package com.guildofsmiths.trademesh.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

/**
 * Repository for managing Beacons and Channels.
 * In-memory storage for Phase 0 (will be replaced with Room later).
 */
object BeaconRepository {
    
    private val _beacons = MutableStateFlow<List<Beacon>>(listOf(createDefaultBeacon()))
    val beacons: StateFlow<List<Beacon>> = _beacons.asStateFlow()
    
    private val _activeBeacon = MutableStateFlow<Beacon?>(null)
    val activeBeacon: StateFlow<Beacon?> = _activeBeacon.asStateFlow()
    
    private val _activeChannel = MutableStateFlow<Channel?>(null)
    val activeChannel: StateFlow<Channel?> = _activeChannel.asStateFlow()
    
    init {
        // Set default beacon as active
        _activeBeacon.value = _beacons.value.firstOrNull()
        _activeChannel.value = _activeBeacon.value?.channels?.firstOrNull()
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
        _beacons.update { beacons ->
            beacons.map { beacon ->
                if (beacon.id == beaconId) {
                    val updatedChannel = channel.copy(beaconId = beaconId)
                    if (beacon.channels.any { it.id == channel.id }) beacon
                    else beacon.copy(channels = beacon.channels + updatedChannel)
                } else beacon
            }
        }
        // Update active beacon reference if needed
        if (_activeBeacon.value?.id == beaconId) {
            _activeBeacon.value = getBeacon(beaconId)
        }
    }
    
    /**
     * Create a group channel in a beacon.
     */
    fun createGroupChannel(beaconId: String, name: String, creatorId: String, members: List<String> = emptyList()): Channel {
        val channel = Channel.createGroup(name, beaconId, creatorId, members)
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
     * Only the creator can delete. For groups, shows "[channel deleted]" to all.
     */
    fun deleteChannel(beaconId: String, channelId: String, userId: String): Boolean {
        val channel = getChannel(beaconId, channelId) ?: return false
        
        // Only creator can delete (or anyone for DMs they're part of)
        if (!channel.isOwner(userId) && 
            !(channel.type == ChannelType.DM && channel.members.contains(userId))) {
            return false
        }
        
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
        return true
    }
    
    /**
     * Archive a channel (hide from active list but preserve history).
     * Only the creator can archive.
     */
    fun archiveChannel(beaconId: String, channelId: String, userId: String): Boolean {
        val channel = getChannel(beaconId, channelId) ?: return false
        
        // Only creator can archive
        if (!channel.isOwner(userId) && 
            !(channel.type == ChannelType.DM && channel.members.contains(userId))) {
            return false
        }
        
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
        return true
    }
    
    /**
     * Unarchive a channel (restore to active list).
     */
    fun unarchiveChannel(beaconId: String, channelId: String, userId: String): Boolean {
        val channel = getChannel(beaconId, channelId) ?: return false
        
        // Only creator can unarchive
        if (!channel.isOwner(userId) && 
            !(channel.type == ChannelType.DM && channel.members.contains(userId))) {
            return false
        }
        
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
        return true
    }
    
    /**
     * Get visible (non-archived, non-deleted) channels for a beacon.
     */
    fun getVisibleChannels(beaconId: String): List<Channel> {
        return getBeacon(beaconId)?.channels?.filter { it.isVisible() } ?: emptyList()
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
