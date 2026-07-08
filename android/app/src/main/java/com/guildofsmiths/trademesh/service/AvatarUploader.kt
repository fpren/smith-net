package com.guildofsmiths.trademesh.service

import android.content.Context
import android.net.Uri
import android.util.Log
import com.guildofsmiths.trademesh.BuildConfig
import com.guildofsmiths.trademesh.data.SupabaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Uploads a picked image to the auth-gated avatar endpoint
 * (POST /api/profile/avatar) and returns the absolute photo URL, or null.
 * Mirrors MediaUploadManager's multipart + bearer pattern; kept separate
 * because that route is message-keyed and unauthenticated.
 */
object AvatarUploader {
    private const val TAG = "AvatarUploader"
    private val client = OkHttpClient()

    suspend fun upload(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            val backend = BuildConfig.BACKEND_URL.trimEnd('/')
            val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext null

            val ext = when (mime) {
                "image/png" -> "png"; "image/webp" -> "webp"; "image/gif" -> "gif"; else -> "jpg"
            }
            val body = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("file", "avatar.$ext", bytes.toRequestBody(mime.toMediaTypeOrNull()))
                .build()

            val builder = Request.Builder().url("$backend/api/profile/avatar").post(body)
            SupabaseAuth.getAccessToken()?.let { builder.header("Authorization", "Bearer $it") }

            client.newCall(builder.build()).execute().use { resp ->
                if (!resp.isSuccessful) {
                    Log.w(TAG, "avatar upload failed: ${resp.code}")
                    return@withContext null
                }
                val url = JSONObject(resp.body?.string() ?: "{}").optString("avatarUrl", null)
                when {
                    url.isNullOrBlank() -> null
                    url.startsWith("http") -> url
                    else -> "$backend$url"
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "avatar upload error", e)
            null
        }
    }
}
