package com.guildofsmiths.trademesh.data

import java.util.UUID

/**
 * Beacon represents a mesh network that devices can join.
 * Each beacon has a unique BLE service UUID for filtering.
 */
data class Beacon(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val description: String = "",
    val serviceUuid: UUID,
    val channels: List<Channel> = listOf(Channel.createGeneral()),
    val createdAt: Long = System.currentTimeMillis(),
    val isActive: Boolean = true
) {
    companion object {
        /** Base UUID for Guild of Smiths beacons - last 4 digits vary per beacon */
        private const val BASE_UUID_PREFIX = "0000"
        private const val BASE_UUID_SUFFIX = "-0000-1000-8000-00805F9B34FB"
        
        /** Default beacon for testing */
        val DEFAULT = Beacon(
            id = "default",
            name = "smith net",
            description = "local mesh network",
            serviceUuid = UUID.fromString("0000F00D-0000-1000-8000-00805F9B34FB")
        )
        
        /**
         * Generate a unique service UUID for a new beacon.
         * Uses format: 0000XXXX-0000-1000-8000-00805F9B34FB
         */
        fun generateServiceUuid(): UUID {
            val randomHex = (0xF000..0xFFFF).random().toString(16).uppercase()
            return UUID.fromString("$BASE_UUID_PREFIX$randomHex$BASE_UUID_SUFFIX")
        }
        
        /**
         * Create a new beacon with auto-generated UUID.
         */
        fun create(name: String, description: String = ""): Beacon {
            return Beacon(
                name = name,
                description = description,
                serviceUuid = generateServiceUuid()
            )
        }
    }
}

/**
 * Channel represents a conversation within a beacon.
 * Can be a broadcast channel, group chat, or direct message.
 */
data class Channel(
    val id: String = UUID.randomUUID().toString(),
    val beaconId: String = "",
    val name: String,
    val type: ChannelType = ChannelType.BROADCAST,
    val members: List<String> = emptyList(), // Empty = public/open
    val creatorId: String = "",  // User ID of creator/owner (can delete/archive)
    val deletePermissions: List<String> = emptyList(),  // User IDs who can delete for all
    val createdAt: Long = System.currentTimeMillis(),
    val isArchived: Boolean = false,  // Hidden from active list but history preserved
    val isDeleted: Boolean = false,   // Tombstone - shows "[deleted]" message
    val unreadCount: Int = 0,
    val lastMessagePreview: String? = null,
    val lastMessageTime: Long? = null
) {
    /**
     * Check if a user is the owner/creator of this channel.
     */
    fun isOwner(userId: String): Boolean {
        return creatorId.isNotEmpty() && creatorId == userId
    }
    
    /**
     * Check if a user can delete messages for everyone in this channel.
     * True if user is creator OR has been granted permission.
     */
    fun canDeleteForAll(userId: String): Boolean {
        return isOwner(userId) || deletePermissions.contains(userId)
    }
    
    /**
     * Check if channel should be visible (not archived or deleted).
     */
    fun isVisible(): Boolean = !isArchived && !isDeleted
    
    /**
     * Display name for the channel.
     */
    fun displayName(): String = when (type) {
        ChannelType.BROADCAST -> "#$name"
        ChannelType.GROUP -> "#$name"
        ChannelType.DM -> "@$name"
    }
    
    /**
     * Check if a user can access this channel.
     */
    fun canAccess(userId: String): Boolean = when (type) {
        ChannelType.BROADCAST -> true // Everyone can access broadcast
        ChannelType.GROUP -> members.isEmpty() || members.contains(userId)
        ChannelType.DM -> members.contains(userId)
    }
    
    companion object {
        /**
         * Create the default #general channel (no owner - system channel).
         */
        fun createGeneral(beaconId: String = ""): Channel {
            return Channel(
                id = "general",
                beaconId = beaconId,
                name = "general",
                type = ChannelType.BROADCAST,
                creatorId = ""  // System channel, no owner
            )
        }
        
        /**
         * Create a group channel with creator as owner.
         */
        fun createGroup(name: String, beaconId: String, creatorId: String, members: List<String> = emptyList()): Channel {
            return Channel(
                beaconId = beaconId,
                name = name,
                type = ChannelType.GROUP,
                creatorId = creatorId,
                members = if (members.isEmpty()) listOf(creatorId) else members
            )
        }
        
        /**
         * Create a DM channel between two users.
         * The initiator (myUserId) is considered the creator for management purposes.
         */
        fun createDM(otherUserId: String, otherUserName: String, beaconId: String, myUserId: String): Channel {
            return Channel(
                id = "dm_${listOf(myUserId, otherUserId).sorted().joinToString("_")}",
                beaconId = beaconId,
                name = otherUserName,
                type = ChannelType.DM,
                creatorId = myUserId,  // Initiator owns the DM
                members = listOf(myUserId, otherUserId)
            )
        }
    }
}

/**
 * Type of channel conversation.
 */
enum class ChannelType {
    BROADCAST,  // 1-to-many, public (like #general)
    GROUP,      // Many-to-many, can be invite-only
    DM          // 1-on-1 private direct message
}
