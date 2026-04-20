package com.guildofsmiths.trademesh

import android.app.Application
import android.util.Log
import com.guildofsmiths.trademesh.ai.AIRouter
import com.guildofsmiths.trademesh.ai.BatteryGate
import com.guildofsmiths.trademesh.ai.LlamaInference
import com.guildofsmiths.trademesh.ai.ResponseCache
import com.guildofsmiths.trademesh.data.BeaconRepository
import com.guildofsmiths.trademesh.data.ClientRepository
import com.guildofsmiths.trademesh.data.ColleagueRepository
import com.guildofsmiths.trademesh.data.IntentRepository
import com.guildofsmiths.trademesh.data.IdentityResolver
import com.guildofsmiths.trademesh.data.MessageRepository
import com.guildofsmiths.trademesh.data.RoleContext
import com.guildofsmiths.trademesh.data.SupabaseAuth
import com.guildofsmiths.trademesh.data.UserPreferences
import com.guildofsmiths.trademesh.engine.BoundaryEngine
import com.guildofsmiths.trademesh.service.AuthService
import com.guildofsmiths.trademesh.service.NotificationHelper

/**
 * Application class for Guild of Smiths / TradeMesh.
 * Handles global initialization.
 * 
 * Components:
 * - Supabase Auth (primary)
 * - Legacy AuthService (fallback)
 * - Local preferences
 */
class TradeMeshApplication : Application() {
    
    companion object {
        private const val TAG = "GuildOfSmiths"
        
        /** Global application instance */
        lateinit var instance: TradeMeshApplication
            private set
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Initialize Supabase Auth (primary)
        SupabaseAuth.init(this)
        
        // Initialize legacy auth service (fallback)
        AuthService.init(this)

        // Initialize role context from persisted auth data
        RoleContext.setRoleFromString(AuthService.getUserRole())

        // Initialize user preferences
        UserPreferences.init(this)
        
        // Initialize beacon repository (loads saved channels)
        BeaconRepository.init(this)

        // Initialize client repository
        ClientRepository.init(this)

        // Initialize colleague/crew contacts repository
        ColleagueRepository.init(this)

        // Initialize message repository (with Room database)
        MessageRepository.init(this)

        // Initialize identity resolver (must be after UserPreferences)
        IdentityResolver.init(this)
        
        // Initialize notification channels
        NotificationHelper.initialize(this)
        
        // Initialize channel membership (auto-join default channels)
        BoundaryEngine.initializeChannelMembership()

        // Initialize Intent repository (Phase 2 — scope declaration + versioning)
        IntentRepository.init(getSharedPreferences("intent_prefs", MODE_PRIVATE))

        // Initialize unified Message Bus (Phase 1 — dedup, vector clocks, reconciliation)
        BoundaryEngine.initMessageBus(this, BuildConfig.BACKEND_URL_PRIMARY)

        // Initialize AI components
        BatteryGate.initialize(this)
        ResponseCache.initialize(this)
        AIRouter.initialize(this)
        LlamaInference.initialize()
        
        Log.i(TAG, "════════════════════════════════════════")
        Log.i(TAG, "🔨 GUILD OF SMITHS")
        Log.i(TAG, "   Built for the trades")
        Log.i(TAG, "────────────────────────────────────────")
        
        // Check auth status
        val supabaseUser = SupabaseAuth.getUserName()
        val legacyUser = AuthService.getUserEmail()
        val localUser = UserPreferences.getUserName()
        
        when {
            supabaseUser != null -> {
                Log.i(TAG, "✓ Logged in: $supabaseUser (Supabase)")
                if (SupabaseAuth.isOfflineMode()) {
                    Log.i(TAG, "  [OFFLINE MODE - data local only]")
                }
            }
            legacyUser != null -> {
                Log.i(TAG, "✓ Logged in: $legacyUser (Legacy)")
            }
            localUser != null -> {
                Log.i(TAG, "⚠ Local user only: $localUser")
                Log.i(TAG, "  Consider creating an account to sync data")
            }
            else -> {
                Log.i(TAG, "○ Not logged in")
            }
        }
        
        // Log AI status
        val aiStatus = AIRouter.getStatusText()
        val batteryStatus = BatteryGate.getStatusText()
        Log.i(TAG, "────────────────────────────────────────")
        Log.i(TAG, "AI: $aiStatus")
        Log.i(TAG, "Battery: $batteryStatus")
        Log.i(TAG, "════════════════════════════════════════")
    }
}
