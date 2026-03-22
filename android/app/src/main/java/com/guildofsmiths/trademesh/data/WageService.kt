package com.guildofsmiths.trademesh.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

object WageService {
    private val client = OkHttpClient()
    private val baseUrl = "http://10.0.2.2:3001"

    suspend fun getWageSuggestion(zipCode: String, socCode: String): WageSuggestion? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$baseUrl/api/wages?zip=$zipCode&soc=$socCode")
                    .get()
                    .build()
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
