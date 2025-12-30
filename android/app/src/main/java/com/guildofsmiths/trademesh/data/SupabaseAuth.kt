package com.guildofsmiths.trademesh.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/**
 * Supabase Authentication Manager for Guild of Smiths
 * 
 * Provides:
 * - Email/password authentication via Supabase Auth
 * - Persistent sessions (survives app restart)
 * - User profile management
 * - Offline fallback mode
 */
object SupabaseAuth {
    
    private const val TAG = "SupabaseAuth"
    private const val PREFS_NAME = "supabase_auth"
    
    // ══════════════════════════════════════════════════════════════════════
    // CONFIGURATION - Guild of Smiths Supabase Project
    // ══════════════════════════════════════════════════════════════════════
    
    private const val SUPABASE_URL = "https://bhmeeuzjfniuocovwbyl.supabase.co"
    private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJobWVldXpqZm5pdW9jb3Z3YnlsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjY2MzEzNjksImV4cCI6MjA4MjIwNzM2OX0.SC_I94o68Q86rzaHi1Ojz_CeWa4rY7Le5y7b4-AyHgc"
    
    // ══════════════════════════════════════════════════════════════════════
    // STATE
    // ══════════════════════════════════════════════════════════════════════
    
    private var _client: SupabaseClient? = null
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
    
    // ══════════════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ══════════════════════════════════════════════════════════════════════
    
    fun init(context: Context) {
        if (_isInitialized.value) return
        
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        try {
            _client = createSupabaseClient(
                supabaseUrl = SUPABASE_URL,
                supabaseKey = SUPABASE_ANON_KEY
            ) {
                // Use OkHttp engine with WebSocket support (required for Realtime)
                httpEngine = OkHttp.create()
                
                install(Auth)
                install(Postgrest)
                install(Realtime)
                install(Storage)
            }
            
            Log.i(TAG, "════════════════════════════════════════")
            Log.i(TAG, "✓ Supabase initialized")
            Log.i(TAG, "  URL: $SUPABASE_URL")
            Log.i(TAG, "════════════════════════════════════════")
            
            _isInitialized.value = true
            
            // Load cached user from prefs
            loadUserFromPrefs()
            
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Supabase", e)
            _error.value = "Failed to connect to server"
            _isInitialized.value = true
            
            // Fall back to local storage
            loadUserFromPrefs()
        }
    }
    
    private fun loadUserFromPrefs() {
        val userId = prefs?.getString("user_id", null)
        val email = prefs?.getString("email", null)
        val displayName = prefs?.getString("display_name", null)
        val role = prefs?.getString("role", "solo")
        val isOffline = prefs?.getBoolean("is_offline", false) ?: false
        
        if (userId != null && displayName != null) {
            _currentUser.value = UserProfile(
                id = userId,
                email = email ?: "",
                displayName = displayName,
                role = role ?: "solo",
                isOffline = isOffline
            )
            Log.i(TAG, "✓ Loaded cached user: $displayName")
        }
    }
    
    // ══════════════════════════════════════════════════════════════════════
    // AUTHENTICATION
    // ══════════════════════════════════════════════════════════════════════
    
    data class AuthResult(
        val success: Boolean,
        val user: UserProfile? = null,
        val error: String? = null
    )
    
    /**
     * Sign up a new user with email and password
     */
    suspend fun signUp(
        email: String,
        password: String,
        displayName: String
    ): AuthResult = withContext(Dispatchers.IO) {
        if (_client == null) {
            return@withContext createOfflineAccount(email, displayName)
        }
        
        _isLoading.value = true
        _error.value = null
        
        try {
            Log.i(TAG, "Attempting sign up for: $email")
            
            _client!!.auth.signUpWith(Email) {
                this.email = email
                this.password = password
            }
            
            val user = _client!!.auth.currentUserOrNull()
            if (user != null) {
                Log.i(TAG, "✓ Sign up successful, user ID: ${user.id}")
                
                // Create profile in database
                createUserProfile(user.id, email, displayName)
                
                val profile = UserProfile(
                    id = user.id,
                    email = email,
                    displayName = displayName,
                    role = "solo",
                    isOffline = false
                )
                
                _currentUser.value = profile
                saveUserToPrefs(profile)
                
                _isLoading.value = false
                AuthResult(success = true, user = profile)
            } else {
                // User might need to confirm email
                Log.i(TAG, "Sign up submitted - check email for confirmation")
                
                // Still create local profile so they can use app
                val profile = UserProfile(
                    id = "pending_$email",
                    email = email,
                    displayName = displayName,
                    role = "solo",
                    isOffline = false
                )
                
                _currentUser.value = profile
                saveUserToPrefs(profile)
                
                _isLoading.value = false
                AuthResult(
                    success = true, 
                    user = profile,
                    error = "Check your email to confirm your account"
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sign up error", e)
            _isLoading.value = false
            
            val errorMsg = parseError(e)
            _error.value = errorMsg
            
            // If it's a network error, offer offline mode
            if (errorMsg.contains("network", ignoreCase = true) || 
                errorMsg.contains("timeout", ignoreCase = true) ||
                errorMsg.contains("connection", ignoreCase = true)) {
                return@withContext createOfflineAccount(email, displayName)
            }
            
            AuthResult(success = false, error = errorMsg)
        }
    }
    
    /**
     * Sign in an existing user
     */
    suspend fun signIn(email: String, password: String): AuthResult = withContext(Dispatchers.IO) {
        if (_client == null) {
            return@withContext AuthResult(
                success = false,
                error = "Not connected to server. Try offline mode."
            )
        }
        
        _isLoading.value = true
        _error.value = null
        
        try {
            Log.i(TAG, "Attempting sign in for: $email")
            
            _client!!.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
            
            val user = _client!!.auth.currentUserOrNull()
            if (user != null) {
                Log.i(TAG, "✓ Sign in successful, user ID: ${user.id}")
                
                // Try to load profile from database
                var profile = loadUserProfileFromDb(user.id)
                
                if (profile == null) {
                    // Create profile if doesn't exist
                    createUserProfile(user.id, email, email.substringBefore("@"))
                    profile = UserProfile(
                        id = user.id,
                        email = email,
                        displayName = email.substringBefore("@"),
                        role = "solo",
                        isOffline = false
                    )
                }
                
                _currentUser.value = profile
                saveUserToPrefs(profile)
                
                _isLoading.value = false
                AuthResult(success = true, user = profile)
            } else {
                _isLoading.value = false
                AuthResult(success = false, error = "Sign in failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sign in error", e)
            _isLoading.value = false
            
            val errorMsg = parseError(e)
            _error.value = errorMsg
            
            AuthResult(success = false, error = errorMsg)
        }
    }
    
    /**
     * Sign out the current user
     */
    suspend fun signOut() = withContext(Dispatchers.IO) {
        try {
            _client?.auth?.signOut()
            Log.i(TAG, "✓ Signed out from Supabase")
        } catch (e: Exception) {
            Log.w(TAG, "Sign out error (ignoring)", e)
        }
        
        _currentUser.value = null
        clearPrefs()
        Log.i(TAG, "✓ Local session cleared")
    }
    
    /**
     * Resend confirmation email for unconfirmed accounts
     * Uses OTP (magic link) which effectively resends the confirmation
     */
    suspend fun resendConfirmationEmail(email: String): AuthResult = withContext(Dispatchers.IO) {
        if (_client == null) {
            return@withContext AuthResult(
                success = false,
                error = "Not connected to server"
            )
        }
        
        try {
            Log.i(TAG, "Resending confirmation email to: $email")
            // Use OTP signup to resend confirmation - this sends a magic link
            _client!!.auth.signInWith(io.github.jan.supabase.gotrue.providers.builtin.OTP) {
                this.email = email
                createUser = false // Don't create new user, just send email
            }
            Log.i(TAG, "✓ Confirmation email resent")
            AuthResult(success = true, error = "Confirmation email sent! Check your inbox.")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resend confirmation", e)
            AuthResult(success = false, error = parseError(e))
        }
    }
    
    /**
     * Create an offline-only account (no server required)
     */
    private fun createOfflineAccount(email: String, displayName: String): AuthResult {
        val id = "offline_${System.currentTimeMillis()}"
        val profile = UserProfile(
            id = id,
            email = email,
            displayName = displayName,
            role = "solo",
            isOffline = true
        )
        
        _currentUser.value = profile
        saveUserToPrefs(profile)
        
        Log.i(TAG, "✓ Created offline account: $displayName")
        Log.w(TAG, "  ⚠️ Data will only be saved locally")
        
        return AuthResult(success = true, user = profile)
    }
    
    // ══════════════════════════════════════════════════════════════════════
    // PROFILE MANAGEMENT
    // ══════════════════════════════════════════════════════════════════════
    
    private suspend fun createUserProfile(userId: String, email: String, displayName: String) {
        try {
            _client?.from("profiles")?.insert(
                ProfileInsert(
                    id = userId,
                    email = email,
                    display_name = displayName,
                    role = "solo"
                )
            )
            Log.i(TAG, "✓ Created user profile in database")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create profile (may already exist): ${e.message}")
        }
    }
    
    private suspend fun loadUserProfileFromDb(userId: String): UserProfile? {
        return try {
            val result = _client?.from("profiles")
                ?.select {
                    filter { eq("id", userId) }
                }
                ?.decodeSingleOrNull<ProfileRow>()
            
            result?.let {
                UserProfile(
                    id = it.id,
                    email = it.email,
                    displayName = it.display_name,
                    role = it.role,
                    trade = it.trade,
                    hourlyRate = it.hourly_rate ?: 85.0,
                    isOffline = false
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load profile from db: ${e.message}")
            null
        }
    }
    
    // ══════════════════════════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════════════════════════
    
    fun isLoggedIn(): Boolean = _currentUser.value != null
    
    fun getUserId(): String? = _currentUser.value?.id
    
    fun getUserName(): String? = _currentUser.value?.displayName
    
    fun getUserEmail(): String? = _currentUser.value?.email
    
    fun isOfflineMode(): Boolean = _currentUser.value?.isOffline == true || _client == null
    
    /**
     * Refresh the current session (e.g., after email confirmation deep link)
     */
    fun refreshSession() {
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                val user = _client?.auth?.currentUserOrNull()
                if (user != null) {
                    Log.i(TAG, "Session refreshed, user: ${user.id}")
                    
                    // Load or create profile
                    val profile = loadUserProfileFromDb(user.id) ?: UserProfile(
                        id = user.id,
                        email = user.email ?: "",
                        displayName = user.email?.substringBefore("@") ?: "User",
                        role = "solo",
                        isOffline = false
                    )
                    
                    _currentUser.value = profile
                    saveUserToPrefs(profile)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to refresh session", e)
            }
        }
    }
    
    private fun saveUserToPrefs(profile: UserProfile) {
        prefs?.edit()?.apply {
            putString("user_id", profile.id)
            putString("email", profile.email)
            putString("display_name", profile.displayName)
            putString("role", profile.role)
            putBoolean("is_offline", profile.isOffline)
            apply()
        }
    }
    
    private fun clearPrefs() {
        prefs?.edit()?.clear()?.apply()
    }
    
    private fun parseError(e: Exception): String {
        val message = e.message ?: "Unknown error"
        return when {
            message.contains("invalid_credentials", ignoreCase = true) ||
            message.contains("Invalid login", ignoreCase = true) -> 
                "Invalid email or password"
            // Catch all variations of email confirmation messages
            message.contains("email_not_confirmed", ignoreCase = true) ||
            message.contains("email not confirm", ignoreCase = true) ||
            message.contains("not confirmed", ignoreCase = true) ||
            message.contains("confirm your email", ignoreCase = true) -> 
                "Email not confirmed. Please check your inbox and click the confirmation link."
            message.contains("user_already_exists", ignoreCase = true) ||
            message.contains("already registered", ignoreCase = true) -> 
                "An account with this email already exists. Try signing in."
            message.contains("weak_password", ignoreCase = true) -> 
                "Password must be at least 6 characters"
            message.contains("network", ignoreCase = true) ||
            message.contains("Unable to resolve host", ignoreCase = true) -> 
                "Network error - check your internet connection"
            message.contains("timeout", ignoreCase = true) -> 
                "Connection timed out - try again"
            message.contains("rate limit", ignoreCase = true) ||
            message.contains("too many", ignoreCase = true) ->
                "Too many attempts. Please wait a moment."
            message.contains("no user", ignoreCase = true) ||
            message.contains("user not found", ignoreCase = true) ->
                "No account found with this email"
            else -> message.take(100) // Truncate long messages
        }
    }
    
    /**
     * Request a password reset email
     */
    suspend fun resetPassword(email: String): AuthResult = withContext(Dispatchers.IO) {
        if (_client == null) {
            return@withContext AuthResult(
                success = false,
                error = "Not connected to server"
            )
        }
        
        try {
            Log.i(TAG, "Requesting password reset for: $email")
            _client!!.auth.resetPasswordForEmail(email)
            Log.i(TAG, "✓ Password reset email sent")
            AuthResult(
                success = true, 
                error = "Password reset email sent! Check your inbox."
            )
        } catch (e: Exception) {
            Log.e(TAG, "Password reset failed", e)
            AuthResult(success = false, error = parseError(e))
        }
    }
}

// ══════════════════════════════════════════════════════════════════════
// DATA CLASSES
// ══════════════════════════════════════════════════════════════════════

data class UserProfile(
    val id: String,
    val email: String,
    val displayName: String,
    val role: String = "solo",
    val trade: String? = null,
    val hourlyRate: Double = 85.0,
    val isOffline: Boolean = false
)

@Serializable
data class ProfileRow(
    val id: String,
    val email: String,
    val display_name: String,
    val role: String = "solo",
    val trade: String? = null,
    val hourly_rate: Double? = null
)

@Serializable
data class ProfileInsert(
    val id: String,
    val email: String,
    val display_name: String,
    val role: String = "solo"
)
