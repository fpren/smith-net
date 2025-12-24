package com.guildofsmiths.trademesh

import android.app.Application
import android.util.Log
import com.guildofsmiths.trademesh.data.MessageRepository
import com.guildofsmiths.trademesh.data.UserPreferences
import com.guildofsmiths.trademesh.engine.BoundaryEngine
import com.guildofsmiths.trademesh.service.NotificationHelper

/**
 * Application class for TradeMesh.
 * Handles global initialization for Phase 0.
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
        
        // Initialize user preferences
        UserPreferences.init(this)
        
        // Initialize message repository (with Room database)
        MessageRepository.init(this)
        
        // Initialize notification channels
        NotificationHelper.initialize(this)
        
        // Initialize channel membership (auto-join default channels)
        BoundaryEngine.initializeChannelMembership()
        
        Log.i(TAG, "TradeMesh Phase 0 initialized")
        Log.i(TAG, "User ID: ${UserPreferences.getUserId()}")
        Log.i(TAG, "User Name: ${UserPreferences.getDisplayName()}")
    }
}
