package com.guildofsmiths.trademesh.data

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
        val merged = state.toMutableMap()
        for ((deviceId, count) in other.state) {
            merged[deviceId] = maxOf(merged[deviceId] ?: 0, count)
        }
        return VectorClock(merged)
    }

    // Returns: -1 if this < other, 1 if this > other, 0 if concurrent
    fun compareTo(other: VectorClock): Int {
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
