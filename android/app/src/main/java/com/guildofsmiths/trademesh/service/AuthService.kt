package com.guildofsmiths.trademesh.service

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * C-01: Authentication Service
 * Handles JWT-based authentication with the backend.
 */
object AuthService {
    
    private const val TAG = "AuthService"
    private const val PREFS_NAME = "smith_net_auth"
    
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_REFRESH_TOKEN = "refresh_token"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_USER_EMAIL = "user_email"
    private const val KEY_USER_NAME = "user_name"
    private const val KEY_USER_ROLE = "user_role"
    private const val KEY_TOKEN_EXPIRY = "token_expiry"
    
    private var prefs: SharedPreferences? = null
    private var baseUrl: String = "http://10.0.2.2:3000" // Android emulator localhost
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    
    // ════════════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ════════════════════════════════════════════════════════════════════
    
    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        Log.d(TAG, "AuthService initialized")
    }
    
    fun setBaseUrl(url: String) {
        baseUrl = url.trimEnd('/')
        Log.d(TAG, "Base URL set to: $baseUrl")
    }
    
    // ════════════════════════════════════════════════════════════════════
    // AUTH STATE
    // ════════════════════════════════════════════════════════════════════
    
    fun isLoggedIn(): Boolean {
        val token = prefs?.getString(KEY_ACCESS_TOKEN, null)
        return !token.isNullOrBlank()
    }
    
    fun getAccessToken(): String? {
        return prefs?.getString(KEY_ACCESS_TOKEN, null)
    }
    
    fun getUserId(): String? {
        return prefs?.getString(KEY_USER_ID, null)
    }
    
    fun getUserEmail(): String? {
        return prefs?.getString(KEY_USER_EMAIL, null)
    }
    
    fun getUserName(): String? {
        return prefs?.getString(KEY_USER_NAME, null)
    }
    
    fun getUserRole(): String? {
        return prefs?.getString(KEY_USER_ROLE, null)
    }
    
    // ════════════════════════════════════════════════════════════════════
    // REGISTER
    // ════════════════════════════════════════════════════════════════════
    
    data class AuthResult(
        val success: Boolean,
        val userId: String? = null,
        val email: String? = null,
        val displayName: String? = null,
        val role: String? = null,
        val error: String? = null
    )
    
    suspend fun register(
        email: String,
        password: String,
        displayName: String
    ): AuthResult = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("email", email)
                put("password", password)
                put("displayName", displayName)
            }
            
            val request = Request.Builder()
                .url("$baseUrl/api/auth/register")
                .post(json.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: "{}"
            
            if (response.isSuccessful) {
                val result = JSONObject(body)
                val user = result.getJSONObject("user")
                
                // Store tokens and user info
                saveAuthData(
                    accessToken = result.getString("accessToken"),
                    refreshToken = result.getString("refreshToken"),
                    userId = user.getString("id"),
                    email = user.getString("email"),
                    displayName = user.getString("displayName"),
                    role = user.getString("role")
                )
                
                Log.i(TAG, "✓ Registered: ${user.getString("email")}")
                
                AuthResult(
                    success = true,
                    userId = user.getString("id"),
                    email = user.getString("email"),
                    displayName = user.getString("displayName"),
                    role = user.getString("role")
                )
            } else {
                val error = try {
                    JSONObject(body).optString("error", "Registration failed")
                } catch (e: Exception) {
                    "Registration failed"
                }
                Log.w(TAG, "✗ Register failed: $error")
                AuthResult(success = false, error = error)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Register error", e)
            AuthResult(success = false, error = e.message ?: "Network error")
        }
    }
    
    // ════════════════════════════════════════════════════════════════════
    // LOGIN
    // ════════════════════════════════════════════════════════════════════
    
    suspend fun login(email: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        try {
            val json = JSONObject().apply {
                put("email", email)
                put("password", password)
            }
            
            val request = Request.Builder()
                .url("$baseUrl/api/auth/login")
                .post(json.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: "{}"
            
            if (response.isSuccessful) {
                val result = JSONObject(body)
                val user = result.getJSONObject("user")
                
                // Store tokens and user info
                saveAuthData(
                    accessToken = result.getString("accessToken"),
                    refreshToken = result.getString("refreshToken"),
                    userId = user.getString("id"),
                    email = user.getString("email"),
                    displayName = user.getString("displayName"),
                    role = user.getString("role")
                )
                
                Log.i(TAG, "✓ Logged in: ${user.getString("email")}")
                
                AuthResult(
                    success = true,
                    userId = user.getString("id"),
                    email = user.getString("email"),
                    displayName = user.getString("displayName"),
                    role = user.getString("role")
                )
            } else {
                val error = try {
                    JSONObject(body).optString("error", "Login failed")
                } catch (e: Exception) {
                    "Invalid credentials"
                }
                Log.w(TAG, "✗ Login failed: $error")
                AuthResult(success = false, error = error)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Login error", e)
            AuthResult(success = false, error = e.message ?: "Network error")
        }
    }
    
    // ════════════════════════════════════════════════════════════════════
    // TOKEN REFRESH
    // ════════════════════════════════════════════════════════════════════
    
    suspend fun refreshToken(): Boolean = withContext(Dispatchers.IO) {
        try {
            val refreshToken = prefs?.getString(KEY_REFRESH_TOKEN, null)
            if (refreshToken.isNullOrBlank()) {
                Log.w(TAG, "No refresh token available")
                return@withContext false
            }
            
            val json = JSONObject().apply {
                put("refreshToken", refreshToken)
            }
            
            val request = Request.Builder()
                .url("$baseUrl/api/auth/refresh")
                .post(json.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()
            
            val response = client.newCall(request).execute()
            val body = response.body?.string() ?: "{}"
            
            if (response.isSuccessful) {
                val result = JSONObject(body)
                
                prefs?.edit()?.apply {
                    putString(KEY_ACCESS_TOKEN, result.getString("accessToken"))
                    putString(KEY_REFRESH_TOKEN, result.getString("refreshToken"))
                    apply()
                }
                
                Log.i(TAG, "✓ Token refreshed")
                true
            } else {
                Log.w(TAG, "✗ Token refresh failed")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Token refresh error", e)
            false
        }
    }
    
    // ════════════════════════════════════════════════════════════════════
    // LOGOUT
    // ════════════════════════════════════════════════════════════════════
    
    suspend fun logout(): Boolean = withContext(Dispatchers.IO) {
        try {
            val accessToken = prefs?.getString(KEY_ACCESS_TOKEN, null)
            val refreshToken = prefs?.getString(KEY_REFRESH_TOKEN, null)
            
            if (!accessToken.isNullOrBlank()) {
                val json = JSONObject().apply {
                    put("refreshToken", refreshToken ?: "")
                }
                
                val request = Request.Builder()
                    .url("$baseUrl/api/auth/logout")
                    .addHeader("Authorization", "Bearer $accessToken")
                    .post(json.toString().toRequestBody(JSON_MEDIA_TYPE))
                    .build()
                
                try {
                    client.newCall(request).execute()
                } catch (e: Exception) {
                    // Ignore network errors on logout
                }
            }
            
            // Clear local storage
            clearAuthData()
            
            Log.i(TAG, "✓ Logged out")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Logout error", e)
            clearAuthData()
            false
        }
    }
    
    // ════════════════════════════════════════════════════════════════════
    // HELPERS
    // ════════════════════════════════════════════════════════════════════
    
    private fun saveAuthData(
        accessToken: String,
        refreshToken: String,
        userId: String,
        email: String,
        displayName: String,
        role: String
    ) {
        prefs?.edit()?.apply {
            putString(KEY_ACCESS_TOKEN, accessToken)
            putString(KEY_REFRESH_TOKEN, refreshToken)
            putString(KEY_USER_ID, userId)
            putString(KEY_USER_EMAIL, email)
            putString(KEY_USER_NAME, displayName)
            putString(KEY_USER_ROLE, role)
            apply()
        }
    }
    
    private fun clearAuthData() {
        prefs?.edit()?.apply {
            remove(KEY_ACCESS_TOKEN)
            remove(KEY_REFRESH_TOKEN)
            remove(KEY_USER_ID)
            remove(KEY_USER_EMAIL)
            remove(KEY_USER_NAME)
            remove(KEY_USER_ROLE)
            remove(KEY_TOKEN_EXPIRY)
            apply()
        }
    }
    
    /**
     * Add auth header to a request builder.
     */
    fun addAuthHeader(builder: Request.Builder): Request.Builder {
        val token = getAccessToken()
        if (!token.isNullOrBlank()) {
            builder.addHeader("Authorization", "Bearer $token")
        }
        return builder
    }
}
