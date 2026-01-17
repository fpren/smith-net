package com.guildofsmiths.trademesh.service

import android.util.Log
import com.guildofsmiths.trademesh.data.Peer
import com.guildofsmiths.trademesh.data.PeerRepository
import com.guildofsmiths.trademesh.data.SupabaseAuth
import com.guildofsmiths.trademesh.data.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Online Presence - Discover peers via Supabase when BLE is unavailable
 * 
 * This provides IP-based peer discovery as a fallback when Bluetooth is off.
 * Uses a simple polling mechanism to check for online users.
 */
object OnlinePresence {
    
    private const val TAG = "OnlinePresence"
    private const val PRESENCE_CHANNEL = "smithnet-presence"
    private const val HEARTBEAT_INTERVAL_MS = 30_000L // 30 seconds
    private const val PRESENCE_TIMEOUT_MS = 90_000L // 90 seconds = 3 missed heartbeats
    
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()
    
    private val _onlineUsers = MutableStateFlow<Map<String, OnlineUser>>(emptyMap())
    val onlineUsers: StateFlow<Map<String, OnlineUser>> = _onlineUsers.asStateFlow()
    
    private var heartbeatJob: Job? = null
    private var pollJob: Job? = null
    
    // Backend URL for presence API
    private var backendUrl: String? = null
    
    data class OnlineUser(
        val id: String,
        val name: String,
        val lastSeen: Long,
        val isOnline: Boolean = true
    )
    
    /**
     * Initialize online presence with backend URL
     */
    fun init(backendUrl: String) {
        this.backendUrl = backendUrl
        Log.i(TAG, "Initialized with backend: $backendUrl")
    }
    
    /**
     * Start broadcasting presence and listening for other users
     */
    fun connect() {
        if (_isConnected.value) {
            Log.d(TAG, "Already connected")
            return
        }
        
        val userId = UserPreferences.getUserId()
        val userName = UserPreferences.getUserName()
        
        if (userId.isBlank() || userName.isNullOrBlank()) {
            Log.w(TAG, "Cannot connect - no user info")
            return
        }
        
        Log.i(TAG, "Connecting online presence for $userName ($userId)")
        _isConnected.value = true
        
        // Start heartbeat
        startHeartbeat(userId, userName)
        
        // Start polling for other users
        startPolling()
    }
    
    /**
     * Stop presence broadcasting
     */
    fun disconnect() {
        Log.i(TAG, "Disconnecting online presence")
        heartbeatJob?.cancel()
        pollJob?.cancel()
        _isConnected.value = false
        _onlineUsers.value = emptyMap()
    }
    
    /**
     * Force refresh - poll for users immediately
     */
    fun refresh() {
        scope.launch {
            pollOnlineUsers()
        }
    }
    
    /**
     * Start heartbeat to announce our presence
     */
    private fun startHeartbeat(userId: String, userName: String) {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (isActive) {
                try {
                    sendPresenceHeartbeat(userId, userName)
                } catch (e: Exception) {
                    Log.w(TAG, "Heartbeat failed: ${e.message}")
                }
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }
    }
    
    /**
     * Start polling for online users
     */
    private fun startPolling() {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (isActive) {
                try {
                    pollOnlineUsers()
                } catch (e: Exception) {
                    Log.w(TAG, "Poll failed: ${e.message}")
                }
                delay(HEARTBEAT_INTERVAL_MS)
            }
        }
    }
    
    /**
     * Send presence heartbeat to backend
     */
    private suspend fun sendPresenceHeartbeat(userId: String, userName: String) {
        val url = backendUrl ?: return
        
        try {
            val connection = URL("$url/api/presence").openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            val payload = JSONObject().apply {
                put("userId", userId)
                put("userName", userName)
                put("timestamp", System.currentTimeMillis())
            }
            
            connection.outputStream.use { os ->
                os.write(payload.toString().toByteArray())
            }
            
            val responseCode = connection.responseCode
            if (responseCode == 200 || responseCode == 201) {
                Log.d(TAG, "Heartbeat sent successfully")
            } else {
                Log.w(TAG, "Heartbeat response: $responseCode")
            }
            
            connection.disconnect()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to send heartbeat: ${e.message}")
        }
    }
    
    /**
     * Poll backend for online users
     */
    private suspend fun pollOnlineUsers() {
        val url = backendUrl ?: return
        val myUserId = UserPreferences.getUserId()
        
        try {
            val connection = URL("$url/api/presence").openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            
            val responseCode = connection.responseCode
            if (responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                parseOnlineUsers(response, myUserId)
            } else {
                Log.w(TAG, "Poll response: $responseCode")
            }
            
            connection.disconnect()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to poll users: ${e.message}")
        }
    }
    
    /**
     * Parse online users from API response
     */
    private fun parseOnlineUsers(response: String, myUserId: String) {
        try {
            val json = JSONObject(response)
            val usersArray = json.optJSONArray("users") ?: return
            
            val now = System.currentTimeMillis()
            val users = mutableMapOf<String, OnlineUser>()
            
            for (i in 0 until usersArray.length()) {
                val userObj = usersArray.getJSONObject(i)
                val userId = userObj.getString("userId")
                val userName = userObj.getString("userName")
                val timestamp = userObj.optLong("timestamp", now)
                
                // Skip ourselves
                if (userId == myUserId) continue
                
                // Check if user is still "online" (seen within timeout)
                val isOnline = (now - timestamp) < PRESENCE_TIMEOUT_MS
                
                if (isOnline) {
                    users[userId] = OnlineUser(
                        id = userId,
                        name = userName,
                        lastSeen = timestamp,
                        isOnline = true
                    )
                    
                    // Also add to PeerRepository so they show up in Peers screen
                    PeerRepository.onPeerSeen(
                        userId = userId,
                        userName = userName,
                        rssi = 0 // No signal strength for IP peers
                    )
                }
            }
            
            _onlineUsers.value = users
            Log.d(TAG, "Found ${users.size} online users")
            
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse users: ${e.message}")
        }
    }
}

