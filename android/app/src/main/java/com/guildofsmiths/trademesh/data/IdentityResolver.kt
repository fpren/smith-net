package com.guildofsmiths.trademesh.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * IdentityResolver: Unifies logical author identity across BLE and internet transports.
 *
 * CORE PRINCIPLES:
 * - AuthorID = LOGICAL identity (human/user)
 * - DeviceID = PHYSICAL identity (BLE UUID/carrier)
 * - Transport MUST NOT create new authors
 * - Identity resolution is mandatory for message processing
 */
object IdentityResolver {

    private const val TAG = "IdentityResolver"

    /** DeviceID → AuthorID mapping (persistent) */
    private val deviceToAuthorMap = mutableMapOf<String, String>()

    /** Temporary offline AuthorIDs that need resolution */
    private val tempAuthorIds = mutableSetOf<String>()

    /** Observable identity resolution state */
    private val _identityMappings = MutableStateFlow<Map<String, String>>(emptyMap())
    val identityMappings: StateFlow<Map<String, String>> = _identityMappings.asStateFlow()

    /**
     * Resolve logical AuthorID from physical DeviceID.
     * Returns the canonical AuthorID for a device.
     */
    fun resolveAuthorId(deviceId: String?, knownAuthorId: String? = null): String {
        if (deviceId.isNullOrEmpty()) {
            return knownAuthorId ?: generateTempAuthorId()
        }

        // If we know this device maps to an author, return it
        deviceToAuthorMap[deviceId]?.let { return it }

        // If we have a known author for this device, map it
        if (knownAuthorId != null) {
            mapDeviceToAuthor(deviceId, knownAuthorId)
            return knownAuthorId
        }

        // Device unknown and no author provided - create temp ID
        val tempId = generateTempAuthorId()
        mapDeviceToAuthor(deviceId, tempId)
        tempAuthorIds.add(tempId)

        Log.d(TAG, "Created temp AuthorID $tempId for unknown device $deviceId")
        return tempId
    }

    /**
     * Merge temporary offline identity with real online identity.
     * Called when device connects online and reveals its true identity.
     */
    fun mergeIdentities(tempAuthorId: String, realAuthorId: String, realAuthorName: String) {
        if (tempAuthorId == realAuthorId) return // Already correct

        Log.i(TAG, "Merging temp identity $tempAuthorId → real identity $realAuthorId")

        // Update all device mappings that pointed to temp ID
        val devicesToUpdate = deviceToAuthorMap.filter { it.value == tempAuthorId }.keys
        devicesToUpdate.forEach { deviceId ->
            deviceToAuthorMap[deviceId] = realAuthorId
        }

        // Remove from temp set
        tempAuthorIds.remove(tempAuthorId)

        // Update observable state
        _identityMappings.value = deviceToAuthorMap.toMap()

        Log.i(TAG, "Identity merge complete: ${devicesToUpdate.size} devices updated")
    }

    /**
     * Check if an AuthorID is temporary (needs online resolution).
     */
    fun isTempAuthorId(authorId: String): Boolean = tempAuthorIds.contains(authorId)

    /**
     * Get all devices mapped to an author.
     */
    fun getDevicesForAuthor(authorId: String): List<String> {
        return deviceToAuthorMap.filter { it.value == authorId }.keys.toList()
    }

    /**
     * Get author for device (null if unmapped).
     */
    fun getAuthorForDevice(deviceId: String): String? = deviceToAuthorMap[deviceId]

    // PRIVATE METHODS

    private fun mapDeviceToAuthor(deviceId: String, authorId: String) {
        deviceToAuthorMap[deviceId] = authorId
        _identityMappings.value = deviceToAuthorMap.toMap()
        Log.d(TAG, "Mapped device $deviceId → author $authorId")
    }

    private fun generateTempAuthorId(): String {
        return "temp-${System.currentTimeMillis()}-${(0..9999).random()}"
    }

    /**
     * Initialize from persisted data.
     */
    fun init(context: Context) {
        // TODO: Load from SharedPreferences or database
        Log.d(TAG, "IdentityResolver initialized")
    }

    /**
     * Persist mappings to storage.
     */
    fun persist(context: Context) {
        // TODO: Save to SharedPreferences or database
        Log.d(TAG, "IdentityResolver persisted ${deviceToAuthorMap.size} mappings")
    }
}
