package com.guildofsmiths.trademesh.data

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

object RoleContext {
    var role by mutableStateOf(UserRole.SOLO)
        private set

    val permissions: Set<Permission>
        get() = ROLE_PERMISSIONS[role] ?: emptySet()

    fun can(permission: Permission): Boolean =
        permissions.contains(permission)

    fun isAtLeast(minimumRole: UserRole): Boolean =
        role.ordinal >= minimumRole.ordinal

    fun isSolo(): Boolean = role == UserRole.SOLO
    fun isForeman(): Boolean = isAtLeast(UserRole.FOREMAN)
    fun isAdmin(): Boolean = isAtLeast(UserRole.ADMIN)

    fun updateRole(newRole: UserRole) {
        role = newRole
    }

    fun setRoleFromString(roleString: String?) {
        role = UserRole.fromString(roleString)
    }

    fun reset() {
        role = UserRole.SOLO
    }
}
