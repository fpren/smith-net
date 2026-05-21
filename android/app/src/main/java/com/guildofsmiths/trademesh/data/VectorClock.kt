package com.guildofsmiths.trademesh.data

import com.guildofsmiths.trademesh.core.SmithCore
import org.json.JSONObject

data class VectorClock(
    val state: Map<String, Int> = emptyMap()
) {
    fun increment(deviceId: String): VectorClock {
        val newState = state.toMutableMap()
        newState[deviceId] = (newState[deviceId] ?: 0) + 1
        return VectorClock(newState)
    }

    fun merge(other: VectorClock): VectorClock {
        if (useSmithCore && SmithCore.isReady()) {
            SmithCore.vclockMerge(state, other.state)?.let { return VectorClock(it) }
        }
        return mergeLocal(other)
    }

    // Returns: -1 if this < other, 1 if this > other, 0 if concurrent
    fun compareTo(other: VectorClock): Int {
        if (useSmithCore && SmithCore.isReady()) {
            val r = SmithCore.vclockCompare(state, other.state)
            if (r != SC_CMP_ERR) return r
        }
        return compareLocalTo(other)
    }

    // Pure legacy implementations. Kept so the parity test can compare the ROM
    // against them directly, and as the readiness-gated fallback.
    fun mergeLocal(other: VectorClock): VectorClock {
        val merged = state.toMutableMap()
        for ((deviceId, count) in other.state) {
            merged[deviceId] = maxOf(merged[deviceId] ?: 0, count)
        }
        return VectorClock(merged)
    }

    fun compareLocalTo(other: VectorClock): Int {
        val allKeys = state.keys + other.state.keys
        var thisGreater = false
        var otherGreater = false

        for (key in allKeys) {
            val thisVal = state[key] ?: 0
            val otherVal = other.state[key] ?: 0
            if (thisVal > otherVal) thisGreater = true
            if (otherVal > thisVal) otherGreater = true
        }

        return when {
            thisGreater && !otherGreater -> 1
            otherGreater && !thisGreater -> -1
            else -> 0
        }
    }

    fun toJson(): String {
        val obj = JSONObject()
        for ((key, value) in state) {
            obj.put(key, value)
        }
        return obj.toString()
    }

    companion object {
        private const val SC_CMP_ERR = 2  // matches core/include/smithcore.h

        // Opt-in: route merge/compare through the shared wasm ROM. Set true once
        // SmithCore.initFromAssets() succeeds at app start. Readiness is also
        // checked at the call site, so this defaulting to false is safe.
        @Volatile
        var useSmithCore: Boolean = false

        fun fromJson(json: String): VectorClock {
            if (json.isBlank()) return VectorClock()
            val obj = JSONObject(json)
            val map = mutableMapOf<String, Int>()
            for (key in obj.keys()) {
                map[key] = obj.getInt(key)
            }
            return VectorClock(map)
        }
    }
}
