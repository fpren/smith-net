package com.guildofsmiths.trademesh

import android.app.Application
import android.util.Log
import com.guildofsmiths.trademesh.data.MessageRepository
import com.guildofsmiths.trademesh.data.UserPreferences
import com.guildofsmiths.trademesh.engine.BoundaryEngine
import com.guildofsmiths.trademesh.service.AuthService
import com.guildofsmiths.trademesh.service.NotificationHelper

/**
 * Application class for TradeMesh.
 * Handles global initialization for Phase 0.
 * 
 * P0 Components:
 * - C-01: Authentication & Identity
 * - C-02: Role Engine
 * - C-03: Schema & Boundary Engine
 */
class TradeMeshApplication : Application() {
    
    companion object {
        private const val TAG = "TradeMeshApp"
        
        /** Global application instance */
        lateinit var instance: TradeMeshApplication
            private set
    }
    
    override fun onCreate() {
        super.onCreate()
        instance = this
        
        // Initialize C-01: Authentication
        AuthService.init(this)
        
        // Initialize user preferences
        UserPreferences.init(this)
        
        // Initialize message repository (with Room database)
        MessageRepository.init(this)
        
        // Initialize notification channels
        NotificationHelper.initialize(this)
        
        // Initialize channel membership (auto-join default channels)
        BoundaryEngine.initializeChannelMembership()
        
        Log.i(TAG, "════════════════════════════════════════")
        Log.i(TAG, "🔨 SMITH NET - Phase 0 Forge")
        Log.i(TAG, "────────────────────────────────────────")
        Log.i(TAG, "✓ C-01 Authentication initialized")
        Log.i(TAG, "✓ C-03 Boundary Engine initialized")
        Log.i(TAG, "────────────────────────────────────────")
        Log.i(TAG, "Auth: ${if (AuthService.isLoggedIn()) "Logged in as ${AuthService.getUserEmail()}" else "Not logged in"}")
        Log.i(TAG, "Local ID: ${UserPreferences.getUserId()}")
        Log.i(TAG, "════════════════════════════════════════")
    }
}
