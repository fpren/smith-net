package com.guildofsmiths.trademesh.data

import android.util.Log
import com.guildofsmiths.trademesh.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

/**
 * Phase 3.5 Slice 2: thin OkHttp wrapper for the crew-tracking backend.
 *
 * Endpoints:
 *   POST /api/shifts/start   { source: "android" }
 *   POST /api/shifts/end
 *   POST /api/presence/location { lat, lng, accuracy_m?, battery_pct? }
 *
 * Caller is responsible for owning a CookieJar so the auth cookie persists
 * across requests. Pass the configured OkHttpClient via the constructor.
 */
class PresenceApiClient(private val client: OkHttpClient) {

    companion object {
        private const val TAG = "PresenceApiClient"
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }

    private val baseUrl: String get() = BuildConfig.BACKEND_URL

    /** Returns the shift id (UUID string) on success; throws on 4xx/5xx. */
    suspend fun startShift(source: String = "android"): String = withContext(Dispatchers.IO) {
        val body = JSONObject().put("source", source).toString().toRequestBody(JSON)
        val req = Request.Builder().url("$baseUrl/api/shifts/start").post(body).build()
        client.newCall(req).execute().use { res ->
            if (!res.isSuccessful) throw IOException("startShift HTTP ${res.code}")
            val json = JSONObject(res.body?.string() ?: "{}")
            json.getString("id")
        }
    }

    /** No-op on 404 (no open shift). Throws on other 4xx/5xx. */
    suspend fun endShift(): Unit = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$baseUrl/api/shifts/end")
            .post("".toRequestBody(JSON))
            .build()
        client.newCall(req).execute().use { res ->
            if (res.code == 404) return@withContext  // already off-clock
            if (!res.isSuccessful) throw IOException("endShift HTTP ${res.code}")
        }
    }

    /** POST a location report. Returns false on 403 (no open shift); throws on other errors. */
    suspend fun postLocation(
        lat: Double,
        lng: Double,
        accuracyM: Float? = null,
        batteryPct: Int? = null,
    ): Boolean = withContext(Dispatchers.IO) {
        val payload = JSONObject().apply {
            put("lat", lat)
            put("lng", lng)
            if (accuracyM != null) put("accuracy_m", accuracyM.toDouble())
            if (batteryPct != null) put("battery_pct", batteryPct)
        }
        val req = Request.Builder()
            .url("$baseUrl/api/presence/location")
            .post(payload.toString().toRequestBody(JSON))
            .build()
        client.newCall(req).execute().use { res ->
            if (res.code == 403) {
                Log.w(TAG, "postLocation 403 - no open shift; caller should stop tracking")
                return@withContext false
            }
            if (!res.isSuccessful) throw IOException("postLocation HTTP ${res.code}")
            true
        }
    }
}
