package com.guildofsmiths.trademesh.data

import com.guildofsmiths.trademesh.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

object WageService {
    private val client = OkHttpClient()
    // Wages are served by the Hetzner backend at /api/wages (see backend
    // invoiceLinksWagesRoutes.ts). Use the production-reachable primary URL so
    // off-LAN beta devices resolve it; the old :3001 dev microservice is gone.
    private val baseUrl = BuildConfig.BACKEND_URL_PRIMARY

    suspend fun getWageSuggestion(zipCode: String, socCode: String): WageSuggestion? {
        return withContext(Dispatchers.IO) {
            try {
                val builder = Request.Builder()
                    .url("$baseUrl/api/wages?zip=$zipCode&soc=$socCode")
                    .get()
                // /api/wages is behind authenticateToken on the Hetzner backend.
                SupabaseAuth.getAccessToken()?.let { builder.header("Authorization", "Bearer $it") }
                val request = builder.build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) return@withContext null
                val json = JSONObject(response.body?.string() ?: return@withContext null)
                WageSuggestion(
                    metroName = json.getString("metroName"),
                    lowRate = json.getDouble("p25Hourly"),
                    highRate = json.getDouble("p75Hourly"),
                    medianRate = json.getDouble("medianHourly")
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

data class WageSuggestion(
    val metroName: String,
    val lowRate: Double,
    val highRate: Double,
    val medianRate: Double
)
