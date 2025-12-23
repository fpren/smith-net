package com.guildofsmiths.trademesh.data

import android.content.Context
import android.content.SharedPreferences
import java.util.UUID

/**
 * User preferences stored locally on device.
 * Handles user identity and app settings.
 */
object UserPreferences {
    
    private const val PREFS_NAME = "trademesh_prefs"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_ONBOARDING_COMPLETE = "onboarding_complete"
    
    private var prefs: SharedPreferences? = null
    
    // Cached values
    private var cachedUserId: String? = null
    private var cachedUserName: String? = null
    
    /**
     * Initialize preferences with context.
     * Call this in Application.onCreate()
     */
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        // Generate user ID if not exists
        if (getUserId().isEmpty()) {
            val newId = UUID.randomUUID().toString().take(8)
            prefs?.edit()?.putString(KEY_USER_ID, newId)?.apply()
            cachedUserId = newId
        }
    }
    
    /**
     * Get the unique user ID (generated on first launch).
     */
    fun getUserId(): String {
        if (cachedUserId != null) return cachedUserId!!
        cachedUserId = prefs?.getString(KEY_USER_ID, "") ?: ""
        return cachedUserId!!
    }
    
    /**
     * Get the user's display name.
     */
    fun getUserName(): String {
        if (cachedUserName != null) return cachedUserName!!
        cachedUserName = prefs?.getString(KEY_USER_NAME, "") ?: ""
        return cachedUserName!!
    }
    
    /**
     * Set the user's display name.
     */
    fun setUserName(name: String) {
        cachedUserName = name.trim()
        prefs?.edit()?.putString(KEY_USER_NAME, cachedUserName)?.apply()
    }
    
    /**
     * Check if user has completed onboarding.
     */
    fun isOnboardingComplete(): Boolean {
        return prefs?.getBoolean(KEY_ONBOARDING_COMPLETE, false) ?: false
    }
    
    /**
     * Mark onboarding as complete.
     */
    fun setOnboardingComplete() {
        prefs?.edit()?.putBoolean(KEY_ONBOARDING_COMPLETE, true)?.apply()
    }
    
    /**
     * Check if user has set a name.
     */
    fun hasUserName(): Boolean {
        return getUserName().isNotBlank()
    }
    
    /**
     * Get display name or fallback to User_XXXX format.
     */
    fun getDisplayName(): String {
        val name = getUserName()
        return if (name.isNotBlank()) name else "User_${getUserId().take(4)}"
    }
    
    /**
     * Clear all preferences (for testing).
     */
    fun clear() {
        prefs?.edit()?.clear()?.apply()
        cachedUserId = null
        cachedUserName = null
    }
}
