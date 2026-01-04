package com.guildofsmiths.trademesh.data

import android.content.Context
import android.content.SharedPreferences
import com.guildofsmiths.trademesh.ui.Language
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
    private const val KEY_WEB_AUTHENTICATED = "web_authenticated"
    private const val KEY_GATEWAY_ENABLED = "gateway_enabled"
    private const val KEY_GATEWAY_URL = "gateway_url"
    private const val KEY_AI_MODE = "ai_mode" // "standard" or "hybrid"
    private const val KEY_TRADE_ROLE = "trade_role"
    private const val KEY_DEVICE_ID = "device_id"
    private const val KEY_BACKEND_URL = "backend_url"
    private const val KEY_WEBSOCKET_URL = "websocket_url"
    private const val KEY_SUPABASE_URL = "supabase_url"

    // Onboarding-related keys
    private const val KEY_LANGUAGE = "language"
    private const val KEY_AI_ENABLED = "ai_enabled"
    private const val KEY_ADDRESS_STREET = "address_street"
    private const val KEY_ADDRESS_CITY = "address_city"
    private const val KEY_ADDRESS_STATE = "address_state"
    private const val KEY_ADDRESS_ZIP = "address_zip"
    private const val KEY_ADDRESS_COUNTRY = "address_country"
    private const val KEY_OCCUPATION = "occupation"
    private const val KEY_EXPERIENCE_LEVEL = "experience_level"
    private const val KEY_BUSINESS_NAME = "business_name"
    
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
     * Check if gateway mode is enabled.
     */
    fun isGatewayEnabled(): Boolean {
        return prefs?.getBoolean(KEY_GATEWAY_ENABLED, false) ?: false
    }
    
    /**
     * Set gateway mode enabled/disabled.
     */
    fun setGatewayEnabled(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_GATEWAY_ENABLED, enabled)?.apply()
    }
    
    /**
     * Get gateway URL.
     */
    fun getGatewayUrl(): String {
        return prefs?.getString(KEY_GATEWAY_URL, "ws://192.168.8.163:3000") ?: "ws://192.168.8.163:3000"
    }
    
    /**
     * Set gateway URL.
     */
    fun setGatewayUrl(url: String) {
        prefs?.edit()?.putString(KEY_GATEWAY_URL, url)?.apply()
    }

    /**
     * Get AI mode (Standard vs Hybrid).
     * Default: Standard (always-on, local only)
     */
    fun getAIMode(): AIMode {
        val modeString = prefs?.getString(KEY_AI_MODE, "standard") ?: "standard"
        return try {
            AIMode.valueOf(modeString.uppercase())
        } catch (e: IllegalArgumentException) {
            AIMode.STANDARD
        }
    }

    /**
     * Set AI mode.
     */
    fun setAIMode(mode: AIMode) {
        prefs?.edit()?.putString(KEY_AI_MODE, mode.name.lowercase())?.apply()
    }

    /**
     * Get the user's trade role.
     * Default: General Laborer if not set.
     */
    fun getTradeRole(): TradeRole {
        return try {
            val roleString = prefs?.getString(KEY_TRADE_ROLE, null)
            TradeRole.fromString(roleString) ?: TradeRole.getDefault()
        } catch (e: Exception) {
            TradeRole.getDefault()
        }
    }

    /**
     * Set the user's trade role.
     */
    fun setTradeRole(role: TradeRole) {
        try {
            prefs?.edit()?.putString(KEY_TRADE_ROLE, role.name)?.apply()
        } catch (e: Exception) {
            // Ignore if prefs not initialized
        }
    }

    /**
     * Check if user has explicitly set a trade role.
     */
    fun hasTradeRoleSet(): Boolean {
        return try {
            prefs?.contains(KEY_TRADE_ROLE) == true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get all preferences as a map for agent context.
     */
    fun getAllPreferences(): Map<String, Any> {
        return mapOf(
            "userId" to getUserId(),
            "userName" to getUserName(),
            "onboardingComplete" to isOnboardingComplete(),
            "gatewayEnabled" to isGatewayEnabled(),
            "gatewayUrl" to getGatewayUrl(),
            "aiMode" to getAIMode().name,
            "tradeRole" to getTradeRole().name
        )
    }
    
    /**
     * Clear all preferences (for testing).
     */
    /**
     * Get the device ID (physical device identifier).
     * Used for identity resolution across transports.
     */
    fun getDeviceId(): String {
        var deviceId = prefs?.getString(KEY_DEVICE_ID, null)
        if (deviceId == null) {
            // Generate device ID based on Android ID or create unique
            deviceId = android.provider.Settings.Secure.ANDROID_ID.takeIf { it.isNotEmpty() }
                ?: UUID.randomUUID().toString().take(16)
            prefs?.edit()?.putString(KEY_DEVICE_ID, deviceId)?.apply()
        }
        return deviceId
    }

    // ════════════════════════════════════════════════════════════════════
    // ONBOARDING DATA METHODS
    // ════════════════════════════════════════════════════════════════════

    /**
     * Save AI enabled preference.
     */
    fun saveAiEnabled(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_AI_ENABLED, enabled)?.apply()
    }

    /**
     * Get AI enabled preference.
     */
    fun isAiEnabled(): Boolean {
        return prefs?.getBoolean(KEY_AI_ENABLED, false) ?: false
    }

    /**
     * Set language preference.
     */
    fun setLanguage(language: Language) {
        prefs?.edit()?.putString(KEY_LANGUAGE, language.name)?.apply()
    }

    /**
     * Get language preference.
     */
    fun getLanguage(): Language {
        val languageName = prefs?.getString(KEY_LANGUAGE, Language.ENGLISH.name) ?: Language.ENGLISH.name
        return try {
            Language.valueOf(languageName)
        } catch (e: IllegalArgumentException) {
            Language.ENGLISH
        }
    }

    /**
     * Save address information.
     */
    fun saveAddress(street: String, city: String, stateProvince: String, zipPostal: String, country: String) {
        prefs?.edit()?.apply {
            putString(KEY_ADDRESS_STREET, street.trim())
            putString(KEY_ADDRESS_CITY, city.trim())
            putString(KEY_ADDRESS_STATE, stateProvince.trim())
            putString(KEY_ADDRESS_ZIP, zipPostal.trim())
            putString(KEY_ADDRESS_COUNTRY, country.trim())
        }?.apply()
    }

    /**
     * Get address information.
     */
    fun getAddress(): Map<String, String> {
        return mapOf(
            "street" to (prefs?.getString(KEY_ADDRESS_STREET, "") ?: ""),
            "city" to (prefs?.getString(KEY_ADDRESS_CITY, "") ?: ""),
            "stateProvince" to (prefs?.getString(KEY_ADDRESS_STATE, "") ?: ""),
            "zipPostal" to (prefs?.getString(KEY_ADDRESS_ZIP, "") ?: ""),
            "country" to (prefs?.getString(KEY_ADDRESS_COUNTRY, "") ?: "")
        )
    }

    /**
     * Save occupation.
     */
    fun saveOccupation(occupation: String) {
        prefs?.edit()?.putString(KEY_OCCUPATION, occupation)?.apply()
    }

    /**
     * Get occupation.
     */
    fun getOccupation(): String {
        return prefs?.getString(KEY_OCCUPATION, "") ?: ""
    }

    /**
     * Save experience level.
     */
    fun saveExperienceLevel(experienceLevel: String) {
        prefs?.edit()?.putString(KEY_EXPERIENCE_LEVEL, experienceLevel)?.apply()
    }

    /**
     * Get experience level.
     */
    fun getExperienceLevel(): String {
        return prefs?.getString(KEY_EXPERIENCE_LEVEL, "") ?: ""
    }

    /**
     * Save business name.
     */
    fun saveBusinessName(businessName: String) {
        prefs?.edit()?.putString(KEY_BUSINESS_NAME, businessName.trim())?.apply()
    }

    /**
     * Get business name.
     */
    fun getBusinessName(): String {
        return prefs?.getString(KEY_BUSINESS_NAME, "") ?: ""
    }

    /**
     * Mark user as web-authenticated (for onboarding flow).
     */
    fun setWebAuthenticated(authenticated: Boolean) {
        prefs?.edit()?.putBoolean(KEY_WEB_AUTHENTICATED, authenticated)?.apply()
    }

    /**
     * Check if user was authenticated via web portal.
     */
    fun isWebAuthenticated(): Boolean {
        return prefs?.getBoolean(KEY_WEB_AUTHENTICATED, false) ?: false
    }

    /**
     * Check if all required onboarding data is complete.
     * Requires: Address + Work Context (occupation + experience)
     */
    fun isOnboardingDataComplete(): Boolean {
        // Check address data (required)
        val address = getAddress()
        val addressComplete = address["street"]?.isNotBlank() == true &&
                           address["city"]?.isNotBlank() == true &&
                           address["stateProvince"]?.isNotBlank() == true &&
                           address["zipPostal"]?.isNotBlank() == true &&
                           address["country"]?.isNotBlank() == true

        // Check work context (occupation and experience required)
        val occupationComplete = getOccupation()?.isNotBlank() == true
        val experienceComplete = getExperienceLevel()?.isNotBlank() == true

        // Work context must be complete for onboarding to be considered done
        val workContextComplete = occupationComplete && experienceComplete

        return addressComplete && workContextComplete
    }

    // ════════════════════════════════════════════════════════════════════
    // BACKEND CONFIGURATION
    // ════════════════════════════════════════════════════════════════════

    /**
     * Get configured backend API URL
     */
    fun getBackendUrl(): String? = prefs?.getString(KEY_BACKEND_URL, null)

    /**
     * Set backend API URL
     */
    fun setBackendUrl(url: String) {
        prefs?.edit()?.putString(KEY_BACKEND_URL, url)?.apply()
    }

    /**
     * Get configured WebSocket URL
     */
    fun getWebSocketUrl(): String? = prefs?.getString(KEY_WEBSOCKET_URL, null)

    /**
     * Set WebSocket URL
     */
    fun setWebSocketUrl(url: String) {
        prefs?.edit()?.putString(KEY_WEBSOCKET_URL, url)?.apply()
    }

    /**
     * Get configured Supabase URL
     */
    fun getSupabaseUrl(): String? = prefs?.getString(KEY_SUPABASE_URL, null)

    /**
     * Set Supabase URL
     */
    fun setSupabaseUrl(url: String) {
        prefs?.edit()?.putString(KEY_SUPABASE_URL, url)?.apply()
    }

    /**
     * Clear all stored data (for sign out/reset)
     */
    fun clearAllData() {
        prefs?.edit()?.clear()?.apply()
    }

    /**
     * Get all onboarding data as a map.
     */
    fun getOnboardingData(): Map<String, Any> {
        return mapOf(
            "aiEnabled" to isAiEnabled(),
            "address" to getAddress(),
            "occupation" to getOccupation(),
            "experienceLevel" to getExperienceLevel(),
            "businessName" to getBusinessName(),
            "isComplete" to isOnboardingDataComplete()
        )
    }

    fun clear() {
        prefs?.edit()?.clear()?.apply()
        cachedUserId = null
        cachedUserName = null
    }
}

// ════════════════════════════════════════════════════════════════════
// AI MODE ENUM
// ════════════════════════════════════════════════════════════════════

/**
 * AI Assistant modes for the embedded assistant.
 */
enum class AIMode {
    /**
     * Standard Mode: Always-on, local rule-based AI.
     * - Zero battery when idle
     * - No external calls
     * - Deterministic responses
     * - Always available
     */
    STANDARD,

    /**
     * Hybrid Mode: Standard + external LLM when conditions met.
     * - Local rules as fallback
     * - External AI for complex queries
     * - Gated by connectivity + battery + thermal
     * - Graceful degradation to Standard
     */
    HYBRID
}
