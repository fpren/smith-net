package com.guildofsmiths.trademesh.data

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

object RoleContext {
    var role by mutableStateOf(UserRole.SOLO)
        private set

    // Organization membership (null = solo/no org)
    var orgId by mutableStateOf<String?>(null)
        private set

    val permissions: Set<Permission>
        get() = ROLE_PERMISSIONS[role] ?: emptySet()

    fun can(permission: Permission): Boolean =
        permissions.contains(permission)

    fun isAtLeast(minimumRole: UserRole): Boolean =
        role.ordinal >= minimumRole.ordinal

    // Role predicates
    fun isSolo(): Boolean = role == UserRole.SOLO
    fun isTeamMember(): Boolean = role == UserRole.TEAM_MEMBER
    fun isTeamLead(): Boolean = role == UserRole.TEAM_LEAD
    fun isForeman(): Boolean = isAtLeast(UserRole.FOREMAN)
    fun isGC(): Boolean = role == UserRole.GENERAL_CONTRACTOR
    fun isAdmin(): Boolean = isAtLeast(UserRole.ADMIN)

    // Team awareness
    fun hasTeam(): Boolean = role != UserRole.SOLO
    fun hasOrg(): Boolean = orgId != null

    fun updateRole(newRole: UserRole) {
        role = newRole
    }

    fun setRoleFromString(roleString: String?) {
        role = UserRole.fromString(roleString)
    }

    fun setOrg(id: String?) {
        orgId = id
    }

    fun reset() {
        role = UserRole.SOLO
        orgId = null
    }
}
