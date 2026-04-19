package com.guildofsmiths.trademesh.ai

import android.util.Log
import com.guildofsmiths.trademesh.data.UserPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import kotlin.coroutines.resume

/**
 * OpenRouter API client for cloud AI inference.
 * Uses the OpenAI-compatible chat completions endpoint.
 */
object OpenRouterClient {

    private const val TAG = "OpenRouterClient"
    private const val BASE_URL = "https://openrouter.ai/api/v1/chat/completions"
    private const val DEFAULT_MODEL = "liquid/lfm-2.5-1.2b-instruct:free"
    private const val REFERER = "com.guildofsmiths.trademesh"
    private const val APP_NAME = "SmithNet"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    /**
     * Send a chat completion request to OpenRouter.
     * Returns the response text or null on failure.
     */
    suspend fun chat(
        systemPrompt: String,
        userMessage: String,
        model: String = DEFAULT_MODEL,
        maxTokens: Int = 200
    ): String? = withContext(Dispatchers.IO) {
        val apiKey = UserPreferences.getOpenRouterApiKey()
        if (apiKey.isBlank()) {
            Log.w(TAG, "No OpenRouter API key set")
            return@withContext null
        }

        val messages = JSONArray().apply {
            put(JSONObject().apply {
                put("role", "system")
                put("content", systemPrompt)
            })
            put(JSONObject().apply {
                put("role", "user")
                put("content", userMessage)
            })
        }

        val body = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            put("max_tokens", maxTokens)
            put("temperature", 0.3)
        }

        val request = Request.Builder()
            .url(BASE_URL)
            .header("Authorization", "Bearer $apiKey")
            .header("HTTP-Referer", REFERER)
            .header("X-Title", APP_NAME)
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()

        try {
            val response = suspendCancellableCoroutine<Response> { cont ->
                val call = client.newCall(request)
                cont.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e(TAG, "OpenRouter request failed: ${e.message}")
                        if (cont.isActive) cont.resume(Response.Builder()
                            .request(request)
                            .protocol(Protocol.HTTP_1_1)
                            .code(0)
                            .message(e.message ?: "Network error")
                            .body(null)
                            .build())
                    }
                    override fun onResponse(call: Call, response: Response) {
                        if (cont.isActive) cont.resume(response)
                    }
                })
            }

            if (!response.isSuccessful) {
                val errorBody = response.body?.string()
                Log.e(TAG, "OpenRouter error ${response.code}: $errorBody")
                return@withContext null
            }

            val responseBody = response.body?.string() ?: return@withContext null
            val json = JSONObject(responseBody)
            val choices = json.optJSONArray("choices") ?: return@withContext null
            if (choices.length() == 0) return@withContext null

            val message = choices.getJSONObject(0).getJSONObject("message")
            val content = message.optString("content", null)
                ?: message.optString("reasoning", null)
                ?: return@withContext null

            Log.i(TAG, "OpenRouter response: ${content.take(100)}...")
            content
        } catch (e: Exception) {
            Log.e(TAG, "OpenRouter parse error: ${e.message}")
            null
        }
    }

    /**
     * Quick test to verify the API key works.
     */
    suspend fun testConnection(): Boolean {
        val result = chat(
            systemPrompt = "Reply with exactly: OK",
            userMessage = "Test connection",
            maxTokens = 5
        )
        return result != null
    }
}
