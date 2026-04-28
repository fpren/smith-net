package com.guildofsmiths.trademesh.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Local-only location-sharing preferences. On-by-default while clocked in;
 * users can turn the feature off entirely from Settings.
 */
object LocationSharingPreferences {

    private const val PREFS = "location_sharing_prefs"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_CADENCE = "cadence_seconds"

    /** Update cadence in seconds while clocked in. */
    enum class Cadence(val seconds: Int, val label: String) {
        FAST(60, "Every 60 s (default)"),
        MEDIUM(300, "Every 5 min"),
        MANUAL(Int.MAX_VALUE, "Manual only — no background updates")
    }

    data class State(
        val enabled: Boolean = true,
        val cadence: Cadence = Cadence.FAST
    )

    private var prefs: SharedPreferences? = null
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val enabled = prefs?.getBoolean(KEY_ENABLED, true) ?: true
        val cadenceName = prefs?.getString(KEY_CADENCE, Cadence.FAST.name) ?: Cadence.FAST.name
        _state.value = State(
            enabled = enabled,
            cadence = runCatching { Cadence.valueOf(cadenceName) }.getOrDefault(Cadence.FAST)
        )
    }

    fun setEnabled(on: Boolean) {
        _state.value = _state.value.copy(enabled = on)
        prefs?.edit()?.putBoolean(KEY_ENABLED, on)?.apply()
    }

    fun setCadence(c: Cadence) {
        _state.value = _state.value.copy(cadence = c)
        prefs?.edit()?.putString(KEY_CADENCE, c.name)?.apply()
    }
}
