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
    val visibility: ChannelVisibility = ChannelVisibility.PUBLIC,  // NEW: Access control
    val members: List<String> = emptyList(), // Empty = public/open
    val allowedUsers: List<String> = emptyList(),  // Specific users allowed (for RESTRICTED)
    val blockedUsers: List<String> = emptyList(),   // Users explicitly blocked
    val requiresApproval: Boolean = false,          // NEW: Manual approval needed
    val pendingRequests: List<String> = emptyList(), // Users waiting for approval
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
     * Check if a user can access this channel based on visibility settings.
     */
    fun canAccess(userId: String): Boolean {
        // Creator always has access
        if (isOwner(userId)) return true

        // Check blocked users first
        if (blockedUsers.contains(userId)) return false

        return when (visibility) {
            ChannelVisibility.PUBLIC -> {
                // Public channels respect type-based access
                when (type) {
                    ChannelType.BROADCAST -> true
                    ChannelType.GROUP -> members.isEmpty() || members.contains(userId)
                    ChannelType.DM -> members.contains(userId)
                }
            }
            ChannelVisibility.PRIVATE -> {
                // Private channels require invitation
                members.contains(userId)
            }
            ChannelVisibility.RESTRICTED -> {
                // Restricted channels only allow specific users
                allowedUsers.contains(userId)
            }
        }
    }

    /**
     * Check if a user can request access to this channel.
     */
    fun canRequestAccess(userId: String): Boolean {
        // Can't request if already have access
        if (canAccess(userId)) return false

        // Can't request if blocked
        if (blockedUsers.contains(userId)) return false

        // Can request for private channels with approval
        return visibility == ChannelVisibility.PRIVATE && requiresApproval
    }

    /**
     * Check if a user can see this channel in listings.
     * For private/restricted channels, only show if user has access or can request access.
     */
    fun canSeeInList(userId: String): Boolean {
        return when (visibility) {
            ChannelVisibility.PUBLIC -> true
            ChannelVisibility.PRIVATE, ChannelVisibility.RESTRICTED -> canAccess(userId) || canRequestAccess(userId)
        }
    }

    /**
     * Check if a user can manage this channel (add/remove members, change settings).
     */
    fun canManage(userId: String): Boolean {
        return isOwner(userId) || deletePermissions.contains(userId)
    }

    /**
     * Get the access status for a user.
     */
    fun getAccessStatus(userId: String): AccessStatus {
        return when {
            canAccess(userId) -> AccessStatus.GRANTED
            pendingRequests.contains(userId) -> AccessStatus.PENDING
            canRequestAccess(userId) -> AccessStatus.CAN_REQUEST
            else -> AccessStatus.DENIED
        }
    }

    /**
     * Create a copy of this channel with updated access control.
     * Only channel managers should call this.
     */
    fun withUpdatedAccess(
        visibility: ChannelVisibility = this.visibility,
        members: List<String> = this.members,
        allowedUsers: List<String> = this.allowedUsers,
        blockedUsers: List<String> = this.blockedUsers,
        requiresApproval: Boolean = this.requiresApproval,
        pendingRequests: List<String> = this.pendingRequests
    ): Channel {
        return copy(
            visibility = visibility,
            members = members,
            allowedUsers = allowedUsers,
            blockedUsers = blockedUsers,
            requiresApproval = requiresApproval,
            pendingRequests = pendingRequests
        )
    }

    /**
     * Get visibility description for UI.
     */
    fun getVisibilityDescription(): String {
        return when (visibility) {
            ChannelVisibility.PUBLIC -> "Anyone can join"
            ChannelVisibility.PRIVATE -> if (requiresApproval) "Invite-only with approval" else "Invite-only"
            ChannelVisibility.RESTRICTED -> "Restricted to specific users"
        }
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
        fun createGroup(
            name: String,
            beaconId: String,
            creatorId: String,
            visibility: ChannelVisibility = ChannelVisibility.PUBLIC,
            members: List<String> = emptyList(),
            requiresApproval: Boolean = false
        ): Channel {
            return Channel(
                beaconId = beaconId,
                name = name,
                type = ChannelType.GROUP,
                visibility = visibility,
                creatorId = creatorId,
                members = if (members.isEmpty()) listOf(creatorId) else members,
                requiresApproval = requiresApproval
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

/**
 * Channel visibility and access control.
 */
enum class ChannelVisibility {
    PUBLIC,      // Anyone can join and see
    PRIVATE,     // Invite-only, admin approval required
    RESTRICTED   // Only specific users can access
}

/**
 * User's access status to a channel.
 */
enum class AccessStatus {
    GRANTED,      // User has access
    PENDING,      // Request submitted, waiting approval
    CAN_REQUEST,  // User can submit request
    DENIED        // Access denied/blocked
}
