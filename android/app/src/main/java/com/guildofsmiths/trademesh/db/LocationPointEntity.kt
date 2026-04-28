package com.guildofsmiths.trademesh.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single GPS fix recorded during a clocked-in shift. Rolling buffer per
 * user — the DAO prunes to the last 2000 points per user on insert so the
 * table stays bounded regardless of how long the crew runs.
 */
@Entity(
    tableName = "location_points",
    indices = [
        Index(value = ["userId", "timestamp"]),
        Index(value = ["timestamp"])
    ]
)
data class LocationPointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val userId: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val timestamp: Long,
    val batteryPct: Int? = null,
    val source: String = "gps" // "gps" | "network" | "beacon"
)
