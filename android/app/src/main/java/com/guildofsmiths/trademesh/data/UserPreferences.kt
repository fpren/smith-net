package com.guildofsmiths.trademesh.data

import android.content.Context
import android.content.SharedPreferences
import com.guildofsmiths.trademesh.ui.Language
import com.guildofsmiths.trademesh.ui.theme2.ThemePreference
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
    private const val KEY_CLOCK_IN_TIME = "clock_in_time"
    private const val KEY_IS_CLOCKED_IN = "is_clocked_in"
    private const val KEY_TRADE_ROLE = "trade_role"
    private const val KEY_DEVICE_ID = "device_id"

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
    private const val KEY_HOURLY_RATE = "hourly_rate"
    private const val KEY_LICENSE_NUMBER = "license_number"
    private const val KEY_PAYMENT_INFO = "payment_info"
    private const val KEY_ZELLE_HANDLE = "zelle_handle"
    private const val KEY_VENMO_HANDLE = "venmo_handle"
    private const val KEY_PRIMARY_TRADE = "primary_trade"
    private const val KEY_SECONDARY_TRADES = "secondary_trades"
    private const val KEY_OPENROUTER_API_KEY = "openrouter_api_key"
    private const val KEY_AI_SUPERVISOR_MODE = "ai_supervisor_mode"
    private const val KEY_MESH_SOLO_OVERRIDE = "mesh_solo_override"
    private const val KEY_THEME_PREFERENCE = "theme_preference"

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

        val current = getUserId()
        if (current.isBlank() || current == "system" || current == "unknown") {
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
    
    /** Production relay as a WebSocket URL, derived from the primary backend
     *  host. Used as the gateway default so off-LAN beta devices connect out of
     *  the box (the old LAN literal only worked on the dev network). */
    private val defaultGatewayUrl: String =
        com.guildofsmiths.trademesh.BuildConfig.BACKEND_URL_PRIMARY
            .replaceFirst("https://", "wss://")
            .replaceFirst("http://", "ws://")

    /**
     * Get gateway URL.
     */
    fun getGatewayUrl(): String {
        return prefs?.getString(KEY_GATEWAY_URL, defaultGatewayUrl) ?: defaultGatewayUrl
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
     * Get the user's theme preference (Light/Dark/System).
     * Default: SYSTEM. Unknown/corrupt stored values fall back to SYSTEM.
     */
    fun getThemePreference(): ThemePreference {
        val prefString = prefs?.getString(KEY_THEME_PREFERENCE, ThemePreference.SYSTEM.name)
            ?: ThemePreference.SYSTEM.name
        return try {
            ThemePreference.valueOf(prefString)
        } catch (e: IllegalArgumentException) {
            ThemePreference.SYSTEM
        }
    }

    /**
     * Set the user's theme preference.
     */
    fun setThemePreference(preference: ThemePreference) {
        prefs?.edit()?.putString(KEY_THEME_PREFERENCE, preference.name)?.apply()
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
    
    fun getHourlyRate(): Double {
        return prefs?.getString(KEY_HOURLY_RATE, "0.0")?.toDoubleOrNull() ?: 0.0
    }

    fun setHourlyRate(rate: Double) {
        prefs?.edit()?.putString(KEY_HOURLY_RATE, rate.toString())?.apply()
    }

    fun getLicenseNumber(): String {
        return prefs?.getString(KEY_LICENSE_NUMBER, "") ?: ""
    }

    fun setLicenseNumber(license: String) {
        prefs?.edit()?.putString(KEY_LICENSE_NUMBER, license.trim())?.apply()
    }

    fun getPaymentInfo(): String {
        return prefs?.getString(KEY_PAYMENT_INFO, "") ?: ""
    }

    fun setPaymentInfo(info: String) {
        prefs?.edit()?.putString(KEY_PAYMENT_INFO, info.trim())?.apply()
    }

    /** Zelle recipient — email or phone number registered with the user's bank. */
    fun getZelleHandle(): String = prefs?.getString(KEY_ZELLE_HANDLE, "") ?: ""
    fun setZelleHandle(handle: String) {
        prefs?.edit()?.putString(KEY_ZELLE_HANDLE, handle.trim())?.apply()
    }

    /** Venmo username (the @handle, without the leading @). */
    fun getVenmoHandle(): String = prefs?.getString(KEY_VENMO_HANDLE, "") ?: ""
    fun setVenmoHandle(handle: String) {
        prefs?.edit()?.putString(KEY_VENMO_HANDLE, handle.trimStart('@').trim())?.apply()
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
        // Auto-sync TradeRole from Occupation so the app's knowledge base stays aligned
        val role = when (occupation.uppercase()) {
            "ELECTRICIAN" -> TradeRole.ELECTRICIAN
            "HVAC" -> TradeRole.HVAC_TECHNICIAN
            "PLUMBER" -> TradeRole.PLUMBER
            "CARPENTER" -> TradeRole.CARPENTER
            "GENERAL_LABOR", "GENERAL_CONTRACTOR" -> TradeRole.GENERAL_LABORER
            else -> null
        }
        if (role != null) setTradeRole(role)
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
    // AI SUPERVISOR
    // ════════════════════════════════════════════════════════════════════

    fun getOpenRouterApiKey(): String {
        return prefs?.getString(KEY_OPENROUTER_API_KEY, "") ?: ""
    }

    private const val KEY_CLOUD_MODEL = "cloud_model"

    fun getCloudModel(): String = prefs?.getString(KEY_CLOUD_MODEL, "") ?: ""

    fun setCloudModel(model: String) {
        prefs?.edit()?.putString(KEY_CLOUD_MODEL, model.trim())?.apply()
    }

    fun setOpenRouterApiKey(key: String) {
        prefs?.edit()?.putString(KEY_OPENROUTER_API_KEY, key.trim())?.apply()
    }

    fun getAISupervisorMode(): String {
        return prefs?.getString(KEY_AI_SUPERVISOR_MODE, "semi-auto") ?: "semi-auto"
    }

    fun setAISupervisorMode(mode: String) {
        prefs?.edit()?.putString(KEY_AI_SUPERVISOR_MODE, mode)?.apply()
    }

    // ════════════════════════════════════════════════════════════════════
    // TRADE SELECTION (primary + secondaries)
    // ════════════════════════════════════════════════════════════════════

    fun getPrimaryTrade(): String {
        return prefs?.getString(KEY_PRIMARY_TRADE, null) ?: getOccupation()
    }

    fun setPrimaryTrade(trade: String) {
        prefs?.edit()?.putString(KEY_PRIMARY_TRADE, trade)?.apply()
    }

    fun getSecondaryTrades(): List<String> {
        val raw = prefs?.getString(KEY_SECONDARY_TRADES, null) ?: return emptyList()
        return raw.split("|").filter { it.isNotBlank() }
    }

    fun setSecondaryTrades(trades: List<String>) {
        prefs?.edit()?.putString(KEY_SECONDARY_TRADES, trades.joinToString("|"))?.apply()
    }

    fun addSecondaryTrade(trade: String) {
        val current = getSecondaryTrades().toMutableList()
        if (trade !in current) {
            current.add(trade)
            setSecondaryTrades(current)
        }
    }

    fun removeSecondaryTrade(trade: String) {
        val current = getSecondaryTrades().toMutableList()
        current.remove(trade)
        setSecondaryTrades(current)
    }

    // ════════════════════════════════════════════════════════════════════
    // CLOCK-IN PERSISTENCE
    // ════════════════════════════════════════════════════════════════════

    private const val KEY_ACTIVE_TIME_ENTRY = "active_time_entry"
    private const val KEY_COMPLETED_TIME_ENTRIES = "completed_time_entries"

    fun setActiveTimeEntry(json: String) {
        prefs?.edit()?.putString(KEY_ACTIVE_TIME_ENTRY, json)?.apply()
    }

    fun getActiveTimeEntry(): String? {
        return prefs?.getString(KEY_ACTIVE_TIME_ENTRY, null)
    }

    fun clearActiveTimeEntry() {
        prefs?.edit()?.remove(KEY_ACTIVE_TIME_ENTRY)?.apply()
    }

    fun saveCompletedEntries(json: String) {
        prefs?.edit()?.putString(KEY_COMPLETED_TIME_ENTRIES, json)?.apply()
    }

    fun getCompletedEntries(): String? {
        return prefs?.getString(KEY_COMPLETED_TIME_ENTRIES, null)
    }

    // ════════════════════════════════════════════════════════════════════
    // JOB BOARD STATE (persists across app restarts)
    // ════════════════════════════════════════════════════════════════════

    private const val KEY_JOBS_JSON = "jobs_json"
    private const val KEY_ARCHIVED_JOBS_JSON = "archived_jobs_json"
    private const val KEY_LOCAL_TASKS_JSON = "local_tasks_json"

    fun saveJobs(json: String) {
        prefs?.edit()?.putString(KEY_JOBS_JSON, json)?.apply()
    }

    fun getJobs(): String? = prefs?.getString(KEY_JOBS_JSON, null)

    fun saveArchivedJobs(json: String) {
        prefs?.edit()?.putString(KEY_ARCHIVED_JOBS_JSON, json)?.apply()
    }

    fun getArchivedJobs(): String? = prefs?.getString(KEY_ARCHIVED_JOBS_JSON, null)

    fun saveLocalTasks(json: String) {
        prefs?.edit()?.putString(KEY_LOCAL_TASKS_JSON, json)?.apply()
    }

    fun getLocalTasks(): String? = prefs?.getString(KEY_LOCAL_TASKS_JSON, null)

    // ════════════════════════════════════════════════════════════════════
    // PROPOSAL / INTENT STATE (persists across app restarts)
    // ════════════════════════════════════════════════════════════════════

    private const val KEY_INTENTS_JSON = "intents_json"
    private const val KEY_INTENT_VERSIONS_JSON = "intent_versions_json"

    fun saveIntents(json: String) {
        prefs?.edit()?.putString(KEY_INTENTS_JSON, json)?.apply()
    }

    fun getIntents(): String? = prefs?.getString(KEY_INTENTS_JSON, null)

    fun saveIntentVersions(json: String) {
        prefs?.edit()?.putString(KEY_INTENT_VERSIONS_JSON, json)?.apply()
    }

    fun getIntentVersions(): String? = prefs?.getString(KEY_INTENT_VERSIONS_JSON, null)

    // ════════════════════════════════════════════════════════════════════
    // ONE-TIME MIGRATIONS
    // ════════════════════════════════════════════════════════════════════

    private const val KEY_FREE_TEXT_BACKFILL_DONE = "free_text_backfill_done_v1"

    fun isFreeTextBackfillDone(): Boolean =
        prefs?.getBoolean(KEY_FREE_TEXT_BACKFILL_DONE, false) ?: false

    fun setFreeTextBackfillDone() {
        prefs?.edit()?.putBoolean(KEY_FREE_TEXT_BACKFILL_DONE, true)?.apply()
    }

    // v3 — also aligns Job.createdAt with first TimeEntry's clockInTime so
    // the row date reflects when work actually started, not when the Job
    // entity was synthesized.
    private const val KEY_LIFECYCLE_BACKFILL_DONE = "lifecycle_backfill_done_v3"

    fun isLifecycleBackfillDone(): Boolean =
        prefs?.getBoolean(KEY_LIFECYCLE_BACKFILL_DONE, false) ?: false

    fun setLifecycleBackfillDone() {
        prefs?.edit()?.putBoolean(KEY_LIFECYCLE_BACKFILL_DONE, true)?.apply()
    }

    private const val KEY_SMITHAI_ENTERPRISE_JOB_SEEDED = "smithai_enterprise_job_seeded_v1"
    private const val KEY_SMITHAI_ENTERPRISE_JOB_CANCELLED = "smithai_enterprise_job_cancelled_v1"

    fun isSmithAIEnterpriseJobSeeded(): Boolean =
        prefs?.getBoolean(KEY_SMITHAI_ENTERPRISE_JOB_SEEDED, false) ?: false

    fun setSmithAIEnterpriseJobSeeded() {
        prefs?.edit()?.putBoolean(KEY_SMITHAI_ENTERPRISE_JOB_SEEDED, true)?.apply()
    }

    fun isSmithAIEnterpriseJobCancelled(): Boolean =
        prefs?.getBoolean(KEY_SMITHAI_ENTERPRISE_JOB_CANCELLED, false) ?: false

    fun setSmithAIEnterpriseJobCancelled() {
        prefs?.edit()?.putBoolean(KEY_SMITHAI_ENTERPRISE_JOB_CANCELLED, true)?.apply()
    }

    // ════════════════════════════════════════════════════════════════════
    // CLOCK STATE (shared with AISupervisor for self-monitoring)
    // ════════════════════════════════════════════════════════════════════

    fun setClockState(isClockedIn: Boolean, clockInTime: Long = 0L) {
        prefs?.edit()
            ?.putBoolean(KEY_IS_CLOCKED_IN, isClockedIn)
            ?.putLong(KEY_CLOCK_IN_TIME, if (isClockedIn) clockInTime else 0L)
            ?.apply()
    }

    fun isClockedIn(): Boolean = prefs?.getBoolean(KEY_IS_CLOCKED_IN, false) ?: false

    fun getClockInTime(): Long = prefs?.getLong(KEY_CLOCK_IN_TIME, 0L) ?: 0L

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

    /** True if a solo user has explicitly flipped the mesh toggle on. */
    fun getMeshSoloOverride(): Boolean =
        prefs?.getBoolean(KEY_MESH_SOLO_OVERRIDE, false) ?: false

    fun setMeshSoloOverride(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_MESH_SOLO_OVERRIDE, enabled)?.apply()
    }

    /**
     * Policy: run mesh when the user has a team (foreman/GC/admin etc.), or
     * when a solo user has explicitly enabled it for nearby-peer use.
     */
    fun shouldRunMesh(): Boolean {
        return RoleContext.hasTeam() || getMeshSoloOverride()
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
