package com.guildofsmiths.trademesh

import android.app.Application
import android.util.Log
import com.guildofsmiths.trademesh.ai.AIRouter
import com.guildofsmiths.trademesh.ai.BatteryGate
import com.guildofsmiths.trademesh.ai.LlamaInference
import com.guildofsmiths.trademesh.ai.ResponseCache
import com.guildofsmiths.trademesh.planner.KeywordObserver
import com.guildofsmiths.trademesh.data.BeaconRepository
import com.guildofsmiths.trademesh.data.IdentityResolver
import com.guildofsmiths.trademesh.data.JobStorage
import com.guildofsmiths.trademesh.data.TaskStorage
import com.guildofsmiths.trademesh.data.MessageRepository
import com.guildofsmiths.trademesh.data.SupabaseAuth
import com.guildofsmiths.trademesh.data.TimeStorage
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
        
        // Initialize user preferences
        UserPreferences.init(this)
        
        // Initialize Job, Task, and Time persistent storage
        JobStorage.init(this)
        TaskStorage.init(this)
        TimeStorage.init(this)
        
        // Initialize beacon repository (loads saved channels)
        BeaconRepository.init(this)

        // Initialize message repository (with Room database)
        MessageRepository.init(this)

        // Initialize identity resolver (must be after UserPreferences)
        IdentityResolver.init(this)
        
        // Initialize notification channels
        NotificationHelper.initialize(this)
        
        // Initialize channel membership (auto-join default channels)
        BoundaryEngine.initializeChannelMembership()
        
        // Initialize AI components
        BatteryGate.initialize(this)
        ResponseCache.initialize(this)
        AIRouter.initialize(this)
        LlamaInference.initialize()
        
        // Initialize Planner components (keyword observation for TEST ⧉)
        KeywordObserver.initialize(this)
        
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
