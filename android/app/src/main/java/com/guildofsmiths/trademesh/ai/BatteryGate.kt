package com.guildofsmiths.trademesh.ai

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * BatteryGate - Battery and thermal state monitoring for AI inference gating
 * 
 * Controls when AI inference is allowed based on:
 * - Battery level (disable below threshold)
 * - Charging state (allow more when charging)
 * - Thermal state (reduce load when hot)
 * - Power save mode (respect system power saving)
 * 
 * Ensures battery preservation while providing intelligent degradation.
 */
object BatteryGate {
    
    private const val TAG = "BatteryGate"
    
    // Thresholds
    private const val BATTERY_CRITICAL_THRESHOLD = 5    // Completely disable AI
    private const val BATTERY_LOW_THRESHOLD = 15        // Use rule-based only
    private const val BATTERY_MEDIUM_THRESHOLD = 30     // Reduce max tokens
    private const val BATTERY_OK_THRESHOLD = 50         // Normal operation
    
    // Token limits based on battery
    private const val TOKENS_FULL = 512
    private const val TOKENS_MEDIUM = 256
    private const val TOKENS_LOW = 128
    private const val TOKENS_RULE_BASED = 0
    
    // State
    private val _gateState = MutableStateFlow(GateState())
    val gateState: StateFlow<GateState> = _gateState.asStateFlow()
    
    private val _inferenceAllowed = MutableStateFlow(true)
    val inferenceAllowed: StateFlow<Boolean> = _inferenceAllowed.asStateFlow()
    
    private var context: Context? = null
    private var batteryReceiver: BroadcastReceiver? = null
    private var powerManager: PowerManager? = null
    private var autoDegradeEnabled = true
    
    // ════════════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ════════════════════════════════════════════════════════════════════
    
    /**
     * Initialize battery monitoring. Call once at app start.
     */
    fun initialize(appContext: Context) {
        if (context != null) {
            Log.d(TAG, "Already initialized")
            return
        }
        
        context = appContext.applicationContext
        powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
        
        // Register battery change receiver
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                updateBatteryState(intent)
            }
        }
        
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            }
        }
        
        // Get initial state
        val batteryIntent = appContext.registerReceiver(batteryReceiver, filter)
        updateBatteryState(batteryIntent)
        
        Log.i(TAG, "BatteryGate initialized. State: ${_gateState.value}")
    }
    
    /**
     * Cleanup resources. Call at app shutdown.
     */
    fun shutdown() {
        batteryReceiver?.let {
            try {
                context?.unregisterReceiver(it)
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering receiver", e)
            }
        }
        batteryReceiver = null
        context = null
        powerManager = null
    }
    
    // ════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ════════════════════════════════════════════════════════════════════
    
    /**
     * Check if LLM inference is currently allowed.
     */
    fun isInferenceAllowed(): Boolean {
        val state = _gateState.value
        
        // Always block if critical battery
        if (state.batteryLevel <= BATTERY_CRITICAL_THRESHOLD && !state.isCharging) {
            return false
        }
        
        // Block if power save mode and not charging
        if (state.isPowerSaveMode && !state.isCharging) {
            return false
        }
        
        // Block if thermal throttling severe
        if (state.thermalStatus >= ThermalStatus.SEVERE) {
            return false
        }
        
        // If auto-degrade disabled, always allow (user override)
        if (!autoDegradeEnabled) {
            return true
        }
        
        // Allow if charging or battery OK
        return state.isCharging || state.batteryLevel > BATTERY_LOW_THRESHOLD
    }
    
    /**
     * Get recommended max tokens based on current state.
     * Returns 0 if only rule-based should be used.
     */
    fun getRecommendedMaxTokens(): Int {
        val state = _gateState.value
        
        // Charging = full power
        if (state.isCharging) {
            return when (state.thermalStatus) {
                ThermalStatus.NONE, ThermalStatus.LIGHT -> TOKENS_FULL
                ThermalStatus.MODERATE -> TOKENS_MEDIUM
                ThermalStatus.SEVERE, ThermalStatus.CRITICAL -> TOKENS_LOW
                ThermalStatus.SHUTDOWN -> TOKENS_RULE_BASED
            }
        }
        
        // Not charging - consider battery
        return when {
            state.batteryLevel <= BATTERY_CRITICAL_THRESHOLD -> TOKENS_RULE_BASED
            state.batteryLevel <= BATTERY_LOW_THRESHOLD -> TOKENS_RULE_BASED
            state.batteryLevel <= BATTERY_MEDIUM_THRESHOLD -> TOKENS_LOW
            state.batteryLevel <= BATTERY_OK_THRESHOLD -> TOKENS_MEDIUM
            else -> TOKENS_FULL
        }
    }
    
    /**
     * Get the current AI availability status.
     */
    fun getAIStatus(): AIAvailability {
        val state = _gateState.value
        
        return when {
            state.batteryLevel <= BATTERY_CRITICAL_THRESHOLD && !state.isCharging -> 
                AIAvailability.DISABLED
            state.thermalStatus >= ThermalStatus.SHUTDOWN -> 
                AIAvailability.DISABLED
            state.isPowerSaveMode && !state.isCharging -> 
                AIAvailability.RULE_BASED_ONLY
            state.batteryLevel <= BATTERY_LOW_THRESHOLD && !state.isCharging -> 
                AIAvailability.RULE_BASED_ONLY
            state.thermalStatus >= ThermalStatus.SEVERE -> 
                AIAvailability.DEGRADED
            state.batteryLevel <= BATTERY_MEDIUM_THRESHOLD && !state.isCharging -> 
                AIAvailability.DEGRADED
            else -> 
                AIAvailability.FULL
        }
    }
    
    /**
     * Get human-readable status string for UI display.
     */
    fun getStatusText(): String {
        val state = _gateState.value
        val status = getAIStatus()
        
        val batteryText = "${state.batteryLevel}%"
        val chargingText = if (state.isCharging) " ⚡" else ""
        val thermalText = when (state.thermalStatus) {
            ThermalStatus.NONE, ThermalStatus.LIGHT -> ""
            ThermalStatus.MODERATE -> " 🌡"
            ThermalStatus.SEVERE -> " 🔥"
            ThermalStatus.CRITICAL, ThermalStatus.SHUTDOWN -> " ⚠️"
        }
        
        val statusText = when (status) {
            AIAvailability.FULL -> "Ready"
            AIAvailability.DEGRADED -> "Degraded"
            AIAvailability.RULE_BASED_ONLY -> "Rule-based"
            AIAvailability.DISABLED -> "Disabled"
        }
        
        return "$statusText | $batteryText$chargingText$thermalText"
    }
    
    /**
     * Enable or disable auto-degradation.
     * When disabled, user overrides battery/thermal limits.
     */
    fun setAutoDegradeEnabled(enabled: Boolean) {
        autoDegradeEnabled = enabled
        Log.i(TAG, "Auto-degrade ${if (enabled) "enabled" else "disabled"}")
        updateInferenceAllowed()
    }
    
    fun isAutoDegradeEnabled(): Boolean = autoDegradeEnabled
    
    // ════════════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ════════════════════════════════════════════════════════════════════
    
    private fun updateBatteryState(intent: Intent?) {
        intent ?: return
        
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val batteryPct = if (level >= 0 && scale > 0) {
            (level * 100 / scale)
        } else {
            _gateState.value.batteryLevel
        }
        
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                         status == BatteryManager.BATTERY_STATUS_FULL
        
        val temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10 // Convert to Celsius
        
        val isPowerSaveMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            powerManager?.isPowerSaveMode ?: false
        } else {
            false
        }
        
        val thermalStatus = getThermalStatus(temperature)
        
        _gateState.value = GateState(
            batteryLevel = batteryPct,
            isCharging = isCharging,
            batteryTemperature = temperature,
            thermalStatus = thermalStatus,
            isPowerSaveMode = isPowerSaveMode
        )
        
        updateInferenceAllowed()
        
        Log.d(TAG, "Battery state updated: $batteryPct%, charging=$isCharging, " +
                   "temp=${temperature}°C, thermal=$thermalStatus, powerSave=$isPowerSaveMode")
    }
    
    private fun getThermalStatus(temperature: Int): ThermalStatus {
        return when {
            temperature >= 50 -> ThermalStatus.SHUTDOWN
            temperature >= 45 -> ThermalStatus.CRITICAL
            temperature >= 42 -> ThermalStatus.SEVERE
            temperature >= 38 -> ThermalStatus.MODERATE
            temperature >= 35 -> ThermalStatus.LIGHT
            else -> ThermalStatus.NONE
        }
    }
    
    private fun updateInferenceAllowed() {
        _inferenceAllowed.value = isInferenceAllowed()
    }
}

// ════════════════════════════════════════════════════════════════════
// DATA CLASSES
// ════════════════════════════════════════════════════════════════════

/**
 * Current battery/thermal gate state
 */
data class GateState(
    val batteryLevel: Int = 100,
    val isCharging: Boolean = false,
    val batteryTemperature: Int = 25,
    val thermalStatus: ThermalStatus = ThermalStatus.NONE,
    val isPowerSaveMode: Boolean = false
)

/**
 * Thermal throttling status
 */
enum class ThermalStatus {
    NONE,       // Normal operation
    LIGHT,      // Slightly warm
    MODERATE,   // Getting warm, reduce load
    SEVERE,     // Hot, minimal inference
    CRITICAL,   // Very hot, rule-based only
    SHUTDOWN    // Too hot, disable AI entirely
}

/**
 * AI feature availability based on battery/thermal state
 */
enum class AIAvailability {
    FULL,            // Full LLM inference available
    DEGRADED,        // LLM available with reduced tokens
    RULE_BASED_ONLY, // Only rule-based responses
    DISABLED         // No AI features available
}
