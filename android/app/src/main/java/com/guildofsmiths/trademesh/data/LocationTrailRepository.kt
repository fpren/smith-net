package com.guildofsmiths.trademesh.data

import android.content.Context
import com.guildofsmiths.trademesh.db.AppDatabase
import com.guildofsmiths.trademesh.db.LocationPointEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Lost & Found backing store. Holds a rolling 7-day trail per user, capped
 * at 2000 points — enough for a full week of 1-minute fixes plus slack.
 *
 * Consumers:
 *  - LocationService writes one point per GPS fix (capped automatically).
 *  - Lost & Found screen reads last-known + breadcrumb trail.
 *  - Mesh beacon receiver writes received peers' fixes the same way.
 */
object LocationTrailRepository {

    private const val CAP_PER_USER = 2_000
    private const val RETENTION_MS = 7L * 24 * 60 * 60 * 1000

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var db: AppDatabase? = null

    fun init(context: Context) {
        db = AppDatabase.getInstance(context)
        // Prune anything older than the retention window on each cold start.
        scope.launch {
            val cutoff = System.currentTimeMillis() - RETENTION_MS
            db?.locationPointDao()?.deleteOlderThan(cutoff)
        }
    }

    fun record(
        userId: String,
        latitude: Double,
        longitude: Double,
        accuracyMeters: Float?,
        timestamp: Long = System.currentTimeMillis(),
        batteryPct: Int? = null,
        source: String = "gps"
    ) {
        val dao = db?.locationPointDao() ?: return
        scope.launch {
            dao.insertAndTrim(
                LocationPointEntity(
                    userId = userId,
                    latitude = latitude,
                    longitude = longitude,
                    accuracyMeters = accuracyMeters,
                    timestamp = timestamp,
                    batteryPct = batteryPct,
                    source = source
                ),
                keep = CAP_PER_USER
            )
        }
    }

    suspend fun getLastKnown(userId: String): LocationPointEntity? =
        db?.locationPointDao()?.getLastKnown(userId)

    suspend fun getTrail(userId: String, since: Long, until: Long = System.currentTimeMillis()): List<LocationPointEntity> =
        db?.locationPointDao()?.getTrail(userId, since, until) ?: emptyList()

    suspend fun getRecent(userId: String, limit: Int = 200): List<LocationPointEntity> =
        db?.locationPointDao()?.getRecent(userId, limit) ?: emptyList()

    fun forgetTrail(userId: String) {
        val dao = db?.locationPointDao() ?: return
        scope.launch { dao.deleteForUser(userId) }
    }
}
