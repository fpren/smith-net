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
import java.util.concurrent.TimeUnit

/**
 * Thin HTTP client for the Hetzner backend's /api/auth surface. This is the
 * network layer only -- it holds no session state. SupabaseAuth (the app-facing
 * auth facade) owns token storage and the currentUser StateFlow and delegates
 * its network calls here as part of the W3 unification (Android -> Hetzner JWT).
 *
 * Backend contract (backend/src/authRoutes.ts):
 *   POST /api/auth/register {email,password,displayName}
 *        -> 201 {user, accessToken, refreshToken, expiresIn, requiresEmailVerification}
 *        -> 400 {error, code:"weak_password"}
 *   POST /api/auth/login {email,password}
 *        -> 200 {user, accessToken, refreshToken, expiresIn}
 *        -> 401 {error:"Invalid credentials"}
 *        -> 429 {error, code:"account_locked", retry_after_minutes}
 *   POST /api/auth/refresh {refreshToken} -> {accessToken, refreshToken, expiresIn}
 *   POST /api/auth/logout  {refreshToken} -> {success:true}
 *   GET  /api/auth/me  (Bearer)           -> {user}
 *   POST /api/auth/resend-verification (Bearer) -> {ok:true} | 429 {code:"resend_throttled"}
 *
 * The `user` object shape (auth.ts toPublicUser):
 *   {id, email, displayName, role, tier, organizationId, permissions[], emailVerified}
 */
object HetznerAuthClient {

    private const val TAG = "HetznerAuthClient"
    private val JSON = "application/json; charset=utf-8".toMediaType()

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private fun baseUrl(): String = BuildConfig.BACKEND_URL_PRIMARY.trimEnd('/')

    /** A parsed auth response (register/login). On failure, [error]/[code] are set. */
    data class TokenResponse(
        val ok: Boolean,
        val user: UserProfile? = null,
        val accessToken: String? = null,
        val refreshToken: String? = null,
        val expiresInSeconds: Long = 0,
        val requiresEmailVerification: Boolean = false,
        val error: String? = null,
        val code: String? = null,
        val retryAfterMinutes: Int? = null,
    )

    suspend fun register(email: String, password: String, displayName: String): TokenResponse =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("email", email)
                .put("password", password)
                .put("displayName", displayName)
            postForTokens("/api/auth/register", body)
        }

    suspend fun login(email: String, password: String): TokenResponse =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("email", email).put("password", password)
            postForTokens("/api/auth/login", body)
        }

    suspend fun refresh(refreshToken: String): TokenResponse =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("refreshToken", refreshToken)
            postForTokens("/api/auth/refresh", body)
        }

    /** Best-effort server-side logout (revokes the refresh token). Never throws. */
    suspend fun logout(refreshToken: String?) = withContext(Dispatchers.IO) {
        if (refreshToken.isNullOrEmpty()) return@withContext
        try {
            val body = JSONObject().put("refreshToken", refreshToken)
            val req = Request.Builder()
                .url("${baseUrl()}/api/auth/logout")
                .post(body.toString().toRequestBody(JSON))
                .build()
            http.newCall(req).execute().use { /* ignore body */ }
        } catch (e: Exception) {
            Log.w(TAG, "logout (ignored): ${e.message}")
        }
    }

    /** Fetch the current user with a Bearer access token, or null on any failure. */
    suspend fun me(accessToken: String): UserProfile? = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("${baseUrl()}/api/auth/me")
                .header("Authorization", "Bearer $accessToken")
                .get()
                .build()
            http.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val json = JSONObject(resp.body?.string() ?: return@withContext null)
                parseUser(json.optJSONObject("user") ?: return@withContext null)
            }
        } catch (e: Exception) {
            Log.w(TAG, "me() failed: ${e.message}")
            null
        }
    }

    /** Resend the verification email for the logged-in user. Requires a Bearer token. */
    suspend fun resendVerification(accessToken: String): TokenResponse = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder()
                .url("${baseUrl()}/api/auth/resend-verification")
                .header("Authorization", "Bearer $accessToken")
                .post("{}".toRequestBody(JSON))
                .build()
            http.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                val json = if (raw.isNotEmpty()) JSONObject(raw) else JSONObject()
                if (resp.isSuccessful) TokenResponse(ok = true)
                else TokenResponse(ok = false, error = json.optString("error", "Could not resend"), code = json.optString("code", null))
            }
        } catch (e: Exception) {
            TokenResponse(ok = false, error = e.message ?: "Network error")
        }
    }

    // ── internals ────────────────────────────────────────────────────────

    private fun postForTokens(path: String, body: JSONObject): TokenResponse {
        return try {
            val req = Request.Builder()
                .url("${baseUrl()}$path")
                .post(body.toString().toRequestBody(JSON))
                .build()
            http.newCall(req).execute().use { resp ->
                val raw = resp.body?.string().orEmpty()
                val json = if (raw.isNotEmpty()) JSONObject(raw) else JSONObject()
                if (resp.isSuccessful) {
                    TokenResponse(
                        ok = true,
                        user = json.optJSONObject("user")?.let { parseUser(it) },
                        accessToken = json.optString("accessToken", null),
                        refreshToken = json.optString("refreshToken", null),
                        expiresInSeconds = json.optLong("expiresIn", 0),
                        requiresEmailVerification = json.optBoolean("requiresEmailVerification", false),
                    )
                } else {
                    TokenResponse(
                        ok = false,
                        error = json.optString("error", "Request failed (${resp.code})"),
                        code = json.optString("code", null),
                        retryAfterMinutes = if (json.has("retry_after_minutes")) json.optInt("retry_after_minutes") else null,
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "$path failed", e)
            TokenResponse(ok = false, error = e.message ?: "Network error")
        }
    }

    /** Map the backend public-user JSON onto the app's UserProfile model. */
    private fun parseUser(u: JSONObject): UserProfile = UserProfile(
        id = u.optString("id"),
        email = u.optString("email"),
        displayName = u.optString("displayName", u.optString("email").substringBefore("@")),
        role = u.optString("role", "solo"),
        isOffline = false,
        orgId = u.optString("organizationId", null),
    )
}
