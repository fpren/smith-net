package com.guildofsmiths.trademesh.service

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.guildofsmiths.trademesh.BuildConfig
import com.guildofsmiths.trademesh.data.RoleContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private var baseUrl: String = BuildConfig.BACKEND_URL
    
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
        RoleContext.setRoleFromString(getUserRole())
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

    fun updateUserRole(role: String) {
        prefs?.edit()?.putString(KEY_USER_ROLE, role)?.apply()
        RoleContext.setRoleFromString(role)
    }

    /**
     * Push the work-mode pick to the backend so server-side scoping (e.g. the
     * /api/crew/positions filter) sees the current role. The local SharedPrefs
     * write is still authoritative on the phone; this is best-effort and any
     * failure is swallowed so onboarding doesn't break offline.
     *
     * Caller passes "solo" or "foreman" (the work-mode whitelist on the
     * backend rejects anything else).
     */
    suspend fun syncWorkMode(mode: String): Boolean = withContext(Dispatchers.IO) {
        val token = getAccessToken()
        if (token.isNullOrBlank()) {
            Log.i(TAG, "syncWorkMode skipped — no auth token yet")
            return@withContext false
        }
        try {
            val body = JSONObject().put("mode", mode).toString().toRequestBody(JSON_MEDIA_TYPE)
            val req = Request.Builder()
                .url("$baseUrl/api/auth/users/me/work-mode")
                .addHeader("Authorization", "Bearer $token")
                .patch(body)
                .build()
            client.newCall(req).execute().use { res ->
                if (res.isSuccessful) {
                    Log.i(TAG, "syncWorkMode ok mode=$mode")
                    true
                } else {
                    Log.w(TAG, "syncWorkMode HTTP ${res.code}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "syncWorkMode failed (will reconcile later): ${e.message}")
            false
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // ORG INVITE & JOIN
    // ════════════════════════════════════════════════════════════════════

    data class InviteCode(val code: String, val expiresAt: String)

    data class OrgMember(
        val id: String,
        val email: String,
        val displayName: String,
        val role: String,
    )

    sealed class JoinResult {
        object Ok : JoinResult()
        data class Error(val status: Int, val message: String) : JoinResult()
    }

    /** Foreman-only. Returns null on auth/permission failure or network error. */
    suspend fun createOrgInvite(): InviteCode? = withContext(Dispatchers.IO) {
        val token = getAccessToken() ?: return@withContext null
        try {
            val req = Request.Builder()
                .url("$baseUrl/api/auth/org/invites")
                .addHeader("Authorization", "Bearer $token")
                .post("".toRequestBody(JSON_MEDIA_TYPE))
                .build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) {
                    Log.w(TAG, "createOrgInvite HTTP ${res.code}")
                    return@use null
                }
                val body = JSONObject(res.body?.string() ?: "{}")
                InviteCode(body.getString("code"), body.getString("expiresAt"))
            }
        } catch (e: Exception) {
            Log.w(TAG, "createOrgInvite failed: ${e.message}")
            null
        }
    }

    /**
     * Accept an invite code. On success, updates the local cached role from the
     * server response so RoleContext reflects the post-join team_member role.
     * Surfaces HTTP status distinctly so the Settings UI can render the right
     * error (404 not found / 409 already used / 410 expired).
     */
    suspend fun acceptOrgJoin(code: String): JoinResult = withContext(Dispatchers.IO) {
        val token = getAccessToken() ?: return@withContext JoinResult.Error(401, "not logged in")
        try {
            val body = JSONObject().put("code", code.trim()).toString().toRequestBody(JSON_MEDIA_TYPE)
            val req = Request.Builder()
                .url("$baseUrl/api/auth/org/join")
                .addHeader("Authorization", "Bearer $token")
                .post(body)
                .build()
            client.newCall(req).execute().use { res ->
                val bodyText = res.body?.string() ?: "{}"
                if (res.isSuccessful) {
                    // Pull the fresh role + org from the response so the local
                    // SharedPrefs cache + RoleContext follow the server.
                    val user = JSONObject(bodyText).optJSONObject("user")
                    user?.optString("role")?.takeIf { it.isNotBlank() }?.let { updateUserRole(it) }
                    JoinResult.Ok
                } else {
                    val msg = try { JSONObject(bodyText).optString("error", "join failed") }
                              catch (_: Exception) { "join failed" }
                    Log.w(TAG, "acceptOrgJoin HTTP ${res.code}: $msg")
                    JoinResult.Error(res.code, msg)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "acceptOrgJoin failed: ${e.message}")
            JoinResult.Error(0, e.message ?: "network error")
        }
    }

    sealed class LeaveResult {
        object Ok : LeaveResult()
        data class Error(val status: Int, val message: String) : LeaveResult()
    }

    /**
     * Any auth'd user. On success updates the local cached role from the
     * response so RoleContext + dashboard tabs reflect the post-leave state.
     */
    suspend fun leaveOrg(): LeaveResult = withContext(Dispatchers.IO) {
        val token = getAccessToken() ?: return@withContext LeaveResult.Error(401, "not logged in")
        try {
            val req = Request.Builder()
                .url("$baseUrl/api/auth/org/leave")
                .addHeader("Authorization", "Bearer $token")
                .post("".toRequestBody(JSON_MEDIA_TYPE))
                .build()
            client.newCall(req).execute().use { res ->
                val bodyText = res.body?.string() ?: "{}"
                if (res.isSuccessful) {
                    val user = JSONObject(bodyText).optJSONObject("user")
                    user?.optString("role")?.takeIf { it.isNotBlank() }?.let { updateUserRole(it) }
                    LeaveResult.Ok
                } else {
                    val msg = try { JSONObject(bodyText).optString("error", "leave failed") }
                              catch (_: Exception) { "leave failed" }
                    Log.w(TAG, "leaveOrg HTTP ${res.code}: $msg")
                    LeaveResult.Error(res.code, msg)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "leaveOrg failed: ${e.message}")
            LeaveResult.Error(0, e.message ?: "network error")
        }
    }

    /** Foreman-only. Returns true on success, false on any failure. */
    suspend fun removeOrgMember(memberId: String): Boolean = withContext(Dispatchers.IO) {
        val token = getAccessToken() ?: return@withContext false
        try {
            val req = Request.Builder()
                .url("$baseUrl/api/auth/org/members/$memberId")
                .addHeader("Authorization", "Bearer $token")
                .delete()
                .build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) {
                    Log.w(TAG, "removeOrgMember HTTP ${res.code}")
                    return@use false
                }
                true
            }
        } catch (e: Exception) {
            Log.w(TAG, "removeOrgMember failed: ${e.message}")
            false
        }
    }

    /** Foreman-only. Returns null on auth/permission failure or network error. */
    suspend fun listOrgMembers(): List<OrgMember>? = withContext(Dispatchers.IO) {
        val token = getAccessToken() ?: return@withContext null
        try {
            val req = Request.Builder()
                .url("$baseUrl/api/auth/org/members")
                .addHeader("Authorization", "Bearer $token")
                .get()
                .build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) {
                    Log.w(TAG, "listOrgMembers HTTP ${res.code}")
                    return@use null
                }
                val arr = JSONObject(res.body?.string() ?: "{}").optJSONArray("members") ?: return@use emptyList()
                buildList {
                    for (i in 0 until arr.length()) {
                        val m = arr.getJSONObject(i)
                        add(
                            OrgMember(
                                id = m.optString("id"),
                                email = m.optString("email"),
                                displayName = m.optString("displayName"),
                                role = m.optString("role"),
                            )
                        )
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "listOrgMembers failed: ${e.message}")
            null
        }
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
    
    // Single-flight guard for refreshToken(). The backend rotates refresh
    // tokens on use -- refreshAccessToken() (backend/src/auth.ts) revokes the
    // presented refresh token and issues a new pair, and the old one no
    // longer validates (usersService.validateRefreshToken /
    // revokeRefreshToken). If N callers hit a 401 concurrently and each ran
    // its own network refresh, only the first would succeed and the rest
    // would present an already-revoked refresh token and fail. A dedicated
    // scope (not the caller's own coroutineScope) keeps the in-flight
    // [Deferred] alive across whichever caller happens to cancel first.
    private val refreshScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshMutex = Mutex()
    private var refreshInFlight: Deferred<Boolean>? = null

    suspend fun refreshToken(): Boolean {
        val deferred = refreshMutex.withLock {
            refreshInFlight ?: refreshScope.async { doRefreshToken() }.also { refreshInFlight = it }
        }
        return try {
            deferred.await()
        } finally {
            refreshMutex.withLock {
                if (refreshInFlight === deferred) refreshInFlight = null
            }
        }
    }

    private suspend fun doRefreshToken(): Boolean = withContext(Dispatchers.IO) {
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
        RoleContext.setRoleFromString(role)
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
        RoleContext.reset()
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
