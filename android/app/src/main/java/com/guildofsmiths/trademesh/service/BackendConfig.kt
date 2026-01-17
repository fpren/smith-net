package com.guildofsmiths.trademesh.service

import com.guildofsmiths.trademesh.data.UserPreferences

/**
 * Backend Configuration Manager
 * Handles configurable backend URLs and settings
 */
object BackendConfig {

    // Default fallback URLs (for development)
    private const val DEFAULT_BACKEND_URL = "http://192.168.8.169:3000"
    private const val DEFAULT_WEBSOCKET_URL = "ws://192.168.8.169:3000"
    private const val DEFAULT_SUPABASE_URL = "https://your-project.supabase.co"

    // Get configured backend HTTP URL
    val backendUrl: String
        get() = UserPreferences.getBackendUrl() ?: DEFAULT_BACKEND_URL

    // Get configured WebSocket URL
    val websocketUrl: String
        get() = UserPreferences.getWebSocketUrl() ?: DEFAULT_WEBSOCKET_URL

    // Get configured Supabase URL
    val supabaseUrl: String
        get() = UserPreferences.getSupabaseUrl() ?: DEFAULT_SUPABASE_URL

    // Update backend URL
    fun setBackendUrl(url: String) {
        UserPreferences.setBackendUrl(url)
    }

    // Update WebSocket URL
    fun setWebSocketUrl(url: String) {
        UserPreferences.setWebSocketUrl(url)
    }

    // Update Supabase URL
    fun setSupabaseUrl(url: String) {
        UserPreferences.setSupabaseUrl(url)
    }

    // Reset to defaults
    fun resetToDefaults() {
        UserPreferences.setBackendUrl(DEFAULT_BACKEND_URL)
        UserPreferences.setWebSocketUrl(DEFAULT_WEBSOCKET_URL)
        UserPreferences.setSupabaseUrl(DEFAULT_SUPABASE_URL)
    }
}