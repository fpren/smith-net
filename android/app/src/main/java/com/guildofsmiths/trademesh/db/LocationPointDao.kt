package com.guildofsmiths.trademesh.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface LocationPointDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRaw(point: LocationPointEntity): Long

    @Query("SELECT * FROM location_points WHERE userId = :userId ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLastKnown(userId: String): LocationPointEntity?

    @Query("SELECT * FROM location_points WHERE userId = :userId AND timestamp BETWEEN :since AND :until ORDER BY timestamp ASC")
    suspend fun getTrail(userId: String, since: Long, until: Long): List<LocationPointEntity>

    @Query("SELECT * FROM location_points WHERE userId = :userId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(userId: String, limit: Int): List<LocationPointEntity>

    @Query("DELETE FROM location_points WHERE userId = :userId")
    suspend fun deleteForUser(userId: String)

    @Query("DELETE FROM location_points WHERE timestamp < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)

    @Query(
        "DELETE FROM location_points WHERE userId = :userId AND id NOT IN " +
            "(SELECT id FROM location_points WHERE userId = :userId ORDER BY timestamp DESC LIMIT :keep)"
    )
    suspend fun trimToCap(userId: String, keep: Int)

    /** Insert + keep only the N most-recent points per user. */
    @Transaction
    suspend fun insertAndTrim(point: LocationPointEntity, keep: Int = 2_000) {
        insertRaw(point)
        trimToCap(point.userId, keep)
    }
}
