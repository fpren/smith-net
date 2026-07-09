package com.guildofsmiths.trademesh.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.auth
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/**
 * App-facing authentication facade.
 *
 * W3 unification (2026-06-09): identity now lives on the Hetzner backend. Login,
 * signup, session, token refresh and the current-user identity all go through
 * HetznerAuthClient (the /api/auth routes). The Supabase SDK is kept wired for
 * only -- password reset email (resetPasswordForEmail) -- which the Hetzner
 * backend does not yet provide. (Caveat: Supabase reset resolves against the
 * Supabase user directory, so it only helps users that also exist there, not
 * new Hetzner-only beta signups.) Everything else -- session, profiles, media,
 * presence -- is on Hetzner; the Supabase client is installed with Auth only.
 *
 * The object name and public surface are unchanged so existing call sites keep
 * working; a later cleanup may rename it to HetznerAuth.
 */
object SupabaseAuth {

    private const val TAG = "SupabaseAuth"
    private const val PREFS_NAME = "supabase_auth"

    // Supabase project -- retained only for the password-reset email flow.
    private const val SUPABASE_URL = "https://bhmeeuzjfniuocovwbyl.supabase.co"
    private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJobWVldXpqZm5pdW9jb3Z3YnlsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjY2MzEzNjksImV4cCI6MjA4MjIwNzM2OX0.SC_I94o68Q86rzaHi1Ojz_CeWa4rY7Le5y7b4-AyHgc"

    // ── state ────────────────────────────────────────────────────────────

    private var _client: SupabaseClient? = null
    /** Supabase client -- kept for password reset + the not-yet-migrated
     *  media/profiles/presence features. Auth/session no longer flow through it. */
    val client: SupabaseClient?
        get() = _client

    private var prefs: SharedPreferences? = null

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    private val _currentUser = MutableStateFlow<UserProfile?>(null)
    val currentUser: StateFlow<UserProfile?> = _currentUser.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // ── init ──────────────────────────────────────────────────────────────

    fun init(context: Context) {
        if (_isInitialized.value) return

        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        try {
            // Auth-only install: the sole remaining Supabase use is the password
            // reset email (resetPasswordForEmail). Everything else (auth/session,
            // profiles, media, presence) is on Hetzner now.
            _client = createSupabaseClient(
                supabaseUrl = SUPABASE_URL,
                supabaseKey = SUPABASE_ANON_KEY
            ) {
                httpEngine = OkHttp.create()
                install(Auth)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Supabase client init failed (reset email may be unavailable)", e)
            _client = null
        }

        _isInitialized.value = true
        loadUserFromPrefs()
        Log.i(TAG, "Auth facade initialized (identity=Hetzner, reset=Supabase)")
    }

    private fun loadUserFromPrefs() {
        val userId = prefs?.getString("user_id", null)
        val email = prefs?.getString("email", null)
        val displayName = prefs?.getString("display_name", null)
        val role = prefs?.getString("role", "solo")
        val orgId = prefs?.getString("org_id", null)
        val isOffline = prefs?.getBoolean("is_offline", false) ?: false

        if (userId != null && displayName != null) {
            _currentUser.value = UserProfile(
                id = userId,
                email = email ?: "",
                displayName = displayName,
                role = role ?: "solo",
                isOffline = isOffline,
                orgId = orgId,
            )
            Log.i(TAG, "Loaded cached user: $displayName")
        }
    }

    // ── auth (Hetzner) ─────────────────────────────────────────────────────

    data class AuthResult(
        val success: Boolean,
        val user: UserProfile? = null,
        val error: String? = null
    )

    suspend fun signUp(
        email: String,
        password: String,
        displayName: String
    ): AuthResult = withContext(Dispatchers.IO) {
        _isLoading.value = true
        _error.value = null
        val resp = HetznerAuthClient.register(email, password, displayName)
        _isLoading.value = false

        if (resp.ok && resp.user != null) {
            persistSession(resp.user, resp.accessToken, resp.refreshToken)
            val note = if (resp.requiresEmailVerification) "Check your email to confirm your account" else null
            AuthResult(success = true, user = resp.user, error = note)
        } else {
            val msg = resp.error ?: "Sign up failed"
            // Network failures fall back to a local-only account so field testers
            // are never hard-blocked offline.
            if (isNetworkError(msg)) return@withContext createOfflineAccount(email, displayName)
            _error.value = msg
            AuthResult(success = false, error = friendlyError(msg, resp.code))
        }
    }

    suspend fun signIn(email: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        _isLoading.value = true
        _error.value = null
        val resp = HetznerAuthClient.login(email, password)
        _isLoading.value = false

        if (resp.ok && resp.user != null) {
            persistSession(resp.user, resp.accessToken, resp.refreshToken)
            AuthResult(success = true, user = resp.user)
        } else {
            val msg = when (resp.code) {
                "account_locked" -> "Account temporarily locked." +
                    (resp.retryAfterMinutes?.let { " Try again in $it min." } ?: " Try again shortly.")
                else -> friendlyError(resp.error ?: "Sign in failed", resp.code)
            }
            _error.value = msg
            AuthResult(success = false, error = msg)
        }
    }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        HetznerAuthClient.logout(prefs?.getString("refresh_token", null))
        try { _client?.auth?.signOut() } catch (_: Exception) { /* best-effort */ }
        _currentUser.value = null
        clearPrefs()
        Log.i(TAG, "Signed out; local session cleared")
    }

    /**
     * Resend the email-verification message for the signed-in user. Hetzner's
     * resend requires a Bearer token, so this only works while logged in.
     */
    suspend fun resendConfirmationEmail(email: String): AuthResult = withContext(Dispatchers.IO) {
        val token = getAccessToken()
            ?: return@withContext AuthResult(success = false, error = "Sign in first to resend the confirmation email.")
        val resp = HetznerAuthClient.resendVerification(token)
        if (resp.ok) AuthResult(success = true, error = "Confirmation email sent! Check your inbox.")
        else AuthResult(success = false, error = resp.error ?: "Could not resend confirmation email.")
    }

    /**
     * Request a password-reset email. KEPT on Supabase intentionally (no Hetzner
     * endpoint yet). Best-effort: only resolves for emails present in Supabase.
     */
    suspend fun resetPassword(email: String): AuthResult = withContext(Dispatchers.IO) {
        val client = _client
            ?: return@withContext AuthResult(success = false, error = "Password reset is unavailable right now.")
        try {
            client.auth.resetPasswordForEmail(email)
            AuthResult(success = true, error = "If an account exists for that email, a reset link has been sent.")
        } catch (e: Exception) {
            Log.w(TAG, "resetPassword failed: ${e.message}")
            AuthResult(success = false, error = "Could not send reset email. Contact your administrator.")
        }
    }

    /**
     * Update the current user's discoverability level. Server-side directory
     * privacy is a post-beta follow-up; for now this updates locally so the UI
     * reflects the choice. Valid: "nobody", "team", "anyone".
     */
    /** Reflect a just-uploaded avatar in the in-memory current user. */
    fun updateLocalAvatar(url: String?) {
        _currentUser.value = _currentUser.value?.copy(avatarUrl = url)
    }

    suspend fun updateDiscoverability(level: String): AuthResult = withContext(Dispatchers.IO) {
        if (_currentUser.value == null) return@withContext AuthResult(success = false, error = "Not signed in")
        if (level !in setOf("nobody", "team", "anyone")) {
            return@withContext AuthResult(success = false, error = "Invalid privacy level")
        }
        _currentUser.value = _currentUser.value?.copy(discoverability = level)
        // TODO(W3 follow-up): persist to /api/profiles once the backend exposes
        // a discoverability column.
        AuthResult(success = true)
    }

    // ── offline fallback ────────────────────────────────────────────────────

    private fun createOfflineAccount(email: String, displayName: String): AuthResult {
        val profile = UserProfile(
            id = "offline_${System.currentTimeMillis()}",
            email = email,
            displayName = displayName,
            role = "solo",
            isOffline = true
        )
        _currentUser.value = profile
        saveUserToPrefs(profile)
        Log.w(TAG, "Created offline account: $displayName (data stays local)")
        return AuthResult(success = true, user = profile)
    }

    // ── session refresh ─────────────────────────────────────────────────────

    /**
     * Refresh the access token from the stored refresh token, then reload the
     * user from /api/auth/me. Suspend (not fire-and-forget) so a caller that
     * hit a 401 on a Bearer token sourced from [getAccessToken] -- e.g.
     * [com.guildofsmiths.trademesh.service.AuthedRequest] -- can await
     * completion before retrying. Returns true iff the backend issued a fresh
     * token pair.
     */
    suspend fun refreshSession(): Boolean = withContext(Dispatchers.IO) {
        val refresh = prefs?.getString("refresh_token", null) ?: return@withContext false
        val tokens = HetznerAuthClient.refresh(refresh)
        if (tokens.ok) {
            saveTokens(tokens.accessToken, tokens.refreshToken)
            val token = tokens.accessToken
            if (token != null) {
                val user = HetznerAuthClient.me(token)
                if (user != null) {
                    _currentUser.value = user
                    saveUserToPrefs(user)
                }
            }
            true
        } else {
            Log.w(TAG, "Session refresh failed: ${tokens.error}")
            false
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    fun isLoggedIn(): Boolean = _currentUser.value != null
    fun getUserId(): String? = _currentUser.value?.id
    fun getUserName(): String? = _currentUser.value?.displayName
    fun getUserEmail(): String? = _currentUser.value?.email
    fun isOfflineMode(): Boolean = _currentUser.value?.isOffline == true

    /** The Hetzner access token for authenticated backend calls, or null. May be
     *  expired -- callers that get a 401 should trigger refreshSession(). */
    fun getAccessToken(): String? = prefs?.getString("access_token", null)
    fun getRefreshToken(): String? = prefs?.getString("refresh_token", null)

    private fun persistSession(user: UserProfile, accessToken: String?, refreshToken: String?) {
        _currentUser.value = user
        saveUserToPrefs(user)
        saveTokens(accessToken, refreshToken)
    }

    private fun saveTokens(accessToken: String?, refreshToken: String?) {
        prefs?.edit()?.apply {
            if (accessToken != null) putString("access_token", accessToken)
            if (refreshToken != null) putString("refresh_token", refreshToken)
            apply()
        }
    }

    private fun saveUserToPrefs(profile: UserProfile) {
        prefs?.edit()?.apply {
            putString("user_id", profile.id)
            putString("email", profile.email)
            putString("display_name", profile.displayName)
            putString("role", profile.role)
            putString("org_id", profile.orgId)
            putBoolean("is_offline", profile.isOffline)
            apply()
        }
    }

    private fun clearPrefs() {
        prefs?.edit()?.clear()?.apply()
    }

    private fun isNetworkError(msg: String): Boolean =
        msg.contains("network", true) || msg.contains("timeout", true) ||
        msg.contains("connection", true) || msg.contains("Unable to resolve host", true)

    private fun friendlyError(msg: String, code: String?): String = when {
        code == "weak_password" -> "Password must be at least 8 characters and include a letter and a number."
        msg.contains("Invalid credentials", true) -> "Invalid email or password"
        msg.contains("already", true) -> "An account with this email already exists. Try signing in."
        isNetworkError(msg) -> "Network error - check your internet connection"
        else -> msg.take(120)
    }
}

// ── data classes ───────────────────────────────────────────────────────────

data class UserProfile(
    val id: String,
    val email: String,
    val displayName: String,
    val role: String = "solo",
    val trade: String? = null,
    val hourlyRate: Double = 85.0,
    val isOffline: Boolean = false,
    val publicId: String? = null,
    val avatarUrl: String? = null,
    val discoverability: String = "team",
    val orgId: String? = null,
)

@Serializable
data class ProfileRow(
    val id: String,
    val email: String,
    val display_name: String,
    val role: String = "solo",
    val trade: String? = null,
    val hourly_rate: Double? = null,
    val public_id: String? = null,
    val avatar_url: String? = null,
    val discoverability: String = "team",
    val org_id: String? = null,
)
