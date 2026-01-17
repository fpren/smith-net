package com.guildofsmiths.trademesh.service

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.guildofsmiths.trademesh.data.Message
import com.guildofsmiths.trademesh.data.MessageRepository
import com.guildofsmiths.trademesh.data.UserPreferences
import com.guildofsmiths.trademesh.engine.BoundaryEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * CommsRouter - Unified message routing with automatic path selection
 *
 * Routes messages through the best available transport:
 * 1. Telegram Bridge (primary online path - most reliable)
 * 2. Supabase Realtime (backup online path)
 * 3. BLE Mesh (offline local path)
 *
 * Features:
 * - Automatic failover between paths
 * - Dual-path redundancy (send via multiple paths when available)
 * - Connectivity monitoring
 * - Predictive path selection based on reliability history
 */
object CommsRouter {

    private const val TAG = "CommsRouter"

    // Transport paths in priority order
    enum class TransportPath {
        SUPABASE,    // Supabase Realtime (primary - direct Smith Net)
        TELEGRAM,    // Telegram CLI bridge (backup - for Telegram integration)
        BLE_MESH,    // Bluetooth mesh (offline)
        OFFLINE      // No path available (queue locally)
    }

    // Routing strategies
    enum class RoutingStrategy {
        BEST_AVAILABLE,  // Use best single path
        DUAL_PATH,       // Send via online + mesh simultaneously
        ONLINE_ONLY,     // Only use online paths
        MESH_ONLY        // Only use BLE mesh
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Current state
    private val _activePath = MutableStateFlow(TransportPath.OFFLINE)
    val activePath: StateFlow<TransportPath> = _activePath.asStateFlow()

    private val _hasInternet = MutableStateFlow(false)
    val hasInternet: StateFlow<Boolean> = _hasInternet.asStateFlow()

    private val _hasBluetooth = MutableStateFlow(false)
    val hasBluetooth: StateFlow<Boolean> = _hasBluetooth.asStateFlow()

    private var routingStrategy = RoutingStrategy.BEST_AVAILABLE

    // Path reliability tracking (0.0 to 1.0)
    private val pathReliability = mutableMapOf(
        TransportPath.SUPABASE to 0.9f,   // Primary path for direct Smith Net
        TransportPath.TELEGRAM to 0.7f,   // Backup for Telegram integration
        TransportPath.BLE_MESH to 0.95f   // BLE is very reliable when available
    )

    // Recent send results for adaptive routing
    private val recentResults = mutableListOf<Pair<TransportPath, Boolean>>()
    private val maxResultHistory = 20

    // Context reference
    private var appContext: Context? = null
    private var connectivityCallback: ConnectivityManager.NetworkCallback? = null

    // ════════════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ════════════════════════════════════════════════════════════════════

    /**
     * Initialize the router with application context.
     */
    fun init(context: Context) {
        appContext = context.applicationContext
        startConnectivityMonitor(context)

        // Start observing transport states
        observeTransportStates()

        Log.i(TAG, "CommsRouter initialized")
    }

    /**
     * Set the routing strategy.
     */
    fun setStrategy(strategy: RoutingStrategy) {
        routingStrategy = strategy
        Log.i(TAG, "Routing strategy set to: $strategy")
    }

    /**
     * Start connectivity monitoring.
     */
    private fun startConnectivityMonitor(context: Context) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        connectivityCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network available")
                _hasInternet.value = true
                updateActivePath()
            }

            override fun onLost(network: Network) {
                Log.d(TAG, "Network lost")
                _hasInternet.value = false
                updateActivePath()
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                _hasInternet.value = hasInternet && isValidated
                updateActivePath()
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, connectivityCallback!!)

        // Check initial state
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
        _hasInternet.value = capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }

    /**
     * Observe transport connection states.
     */
    private fun observeTransportStates() {
        scope.launch {
            // Combine all transport states
            combine(
                TelegramBridge.isConnected,
                SupabaseChat.isConnected,
                _hasInternet,
                _hasBluetooth
            ) { telegram, supabase, internet, bluetooth ->
                Triple(telegram to supabase, internet, bluetooth)
            }.collect { (onlineStates, internet, bluetooth) ->
                val (telegramConnected, supabaseConnected) = onlineStates

                Log.d(TAG, "Transport states - Telegram: $telegramConnected, Supabase: $supabaseConnected, Internet: $internet, BLE: $bluetooth")

                updateActivePath()
            }
        }
    }

    /**
     * Update the active path based on current transport availability.
     */
    private fun updateActivePath() {
        val newPath = when {
            // Supabase is preferred for direct Smith Net messaging
            SupabaseChat.isConnected.value -> TransportPath.SUPABASE

            // Fall back to Telegram bridge if configured
            TelegramBridge.isConnected.value -> TransportPath.TELEGRAM

            // BLE mesh for offline (hasBluetooth is updated by MeshService)
            _hasBluetooth.value -> TransportPath.BLE_MESH

            // No path available
            else -> TransportPath.OFFLINE
        }

        if (_activePath.value != newPath) {
            Log.i(TAG, "Active path changed: ${_activePath.value} → $newPath")
            _activePath.value = newPath
        }
    }

    /**
     * Update Bluetooth availability from MeshService.
     */
    fun updateBluetoothState(available: Boolean) {
        _hasBluetooth.value = available
        updateActivePath()
    }

    // ════════════════════════════════════════════════════════════════════
    // MESSAGE ROUTING
    // ════════════════════════════════════════════════════════════════════

    /**
     * Send a message using the best available transport.
     * Returns the path(s) used for delivery.
     */
    fun sendMessage(message: Message, callback: ((List<TransportPath>) -> Unit)? = null) {
        Log.i(TAG, "📤 Routing message: ${message.content.take(30)}")

        val usedPaths = mutableListOf<TransportPath>()

        when (routingStrategy) {
            RoutingStrategy.BEST_AVAILABLE -> {
                sendViaBestPath(message) { path, success ->
                    if (success) usedPaths.add(path)
                    callback?.invoke(usedPaths)
                }
            }

            RoutingStrategy.DUAL_PATH -> {
                // Send via both online and mesh for redundancy
                sendDualPath(message) { paths ->
                    callback?.invoke(paths)
                }
            }

            RoutingStrategy.ONLINE_ONLY -> {
                sendOnlineOnly(message) { path, success ->
                    if (success) usedPaths.add(path)
                    callback?.invoke(usedPaths)
                }
            }

            RoutingStrategy.MESH_ONLY -> {
                sendMeshOnly(message) { success ->
                    if (success) usedPaths.add(TransportPath.BLE_MESH)
                    callback?.invoke(usedPaths)
                }
            }
        }
    }

    /**
     * Send via the single best available path.
     */
    private fun sendViaBestPath(message: Message, callback: (TransportPath, Boolean) -> Unit) {
        when (_activePath.value) {
            TransportPath.SUPABASE -> {
                SupabaseChat.sendMessage(message)
                recordResult(TransportPath.SUPABASE, true)
                callback(TransportPath.SUPABASE, true)
            }

            TransportPath.TELEGRAM -> {
                TelegramBridge.sendMessage(message)
                recordResult(TransportPath.TELEGRAM, true)
                callback(TransportPath.TELEGRAM, true)
            }

            TransportPath.BLE_MESH -> {
                sendViaMesh(message) { success ->
                    recordResult(TransportPath.BLE_MESH, success)
                    callback(TransportPath.BLE_MESH, success)
                }
            }

            TransportPath.OFFLINE -> {
                // Queue locally
                Log.w(TAG, "No path available, message queued locally")
                queueForLaterDelivery(message)
                callback(TransportPath.OFFLINE, false)
            }
        }
    }

    /**
     * Send via both online and mesh paths simultaneously.
     */
    private fun sendDualPath(message: Message, callback: (List<TransportPath>) -> Unit) {
        val usedPaths = mutableListOf<TransportPath>()

        scope.launch {
            // Online path - Supabase is primary for direct Smith Net
            if (_hasInternet.value) {
                when {
                    SupabaseChat.isConnected.value -> {
                        SupabaseChat.sendMessage(message)
                        usedPaths.add(TransportPath.SUPABASE)
                    }
                    TelegramBridge.isConnected.value -> {
                        TelegramBridge.sendMessage(message)
                        usedPaths.add(TransportPath.TELEGRAM)
                    }
                }
            }

            // Mesh path (in parallel)
            if (_hasBluetooth.value) {
                sendViaMesh(message) { success ->
                    if (success) usedPaths.add(TransportPath.BLE_MESH)
                }
            }

            // Allow time for mesh send
            delay(100)
            callback(usedPaths)
        }
    }

    /**
     * Send via online paths only (Supabase primary, Telegram backup).
     */
    private fun sendOnlineOnly(message: Message, callback: (TransportPath, Boolean) -> Unit) {
        when {
            SupabaseChat.isConnected.value -> {
                SupabaseChat.sendMessage(message)
                callback(TransportPath.SUPABASE, true)
            }
            TelegramBridge.isConnected.value -> {
                TelegramBridge.sendMessage(message)
                callback(TransportPath.TELEGRAM, true)
            }
            else -> {
                queueForLaterDelivery(message)
                callback(TransportPath.OFFLINE, false)
            }
        }
    }

    /**
     * Send via BLE mesh only.
     */
    private fun sendMeshOnly(message: Message, callback: (Boolean) -> Unit) {
        if (_hasBluetooth.value) {
            sendViaMesh(message, callback)
        } else {
            queueForLaterDelivery(message)
            callback(false)
        }
    }

    /**
     * Send message via BLE mesh.
     */
    private fun sendViaMesh(message: Message, callback: (Boolean) -> Unit) {
        val context = appContext
        if (context == null) {
            Log.e(TAG, "No context for mesh send")
            callback(false)
            return
        }

        try {
            // Route through BoundaryEngine which handles mesh serialization
            BoundaryEngine.routeMessage(context, message)
            Log.i(TAG, "✓ Sent via BLE mesh")
            callback(true)
        } catch (e: Exception) {
            Log.e(TAG, "Mesh send failed", e)
            callback(false)
        }
    }

    /**
     * Queue message for delivery when a path becomes available.
     */
    private fun queueForLaterDelivery(message: Message) {
        // TelegramBridge and SupabaseChat both have their own offline queues
        // This is for messages that couldn't be sent anywhere
        Log.d(TAG, "Message queued for later: ${message.id.take(8)}")

        // Add to pending sync queue in MessageRepository
        MessageRepository.addMessage(message.copy(isMeshOrigin = true))
    }

    // ════════════════════════════════════════════════════════════════════
    // RELIABILITY TRACKING
    // ════════════════════════════════════════════════════════════════════

    /**
     * Record send result for adaptive routing.
     */
    private fun recordResult(path: TransportPath, success: Boolean) {
        synchronized(recentResults) {
            recentResults.add(path to success)
            if (recentResults.size > maxResultHistory) {
                recentResults.removeAt(0)
            }

            // Update reliability score
            val pathResults = recentResults.filter { it.first == path }
            if (pathResults.isNotEmpty()) {
                val successRate = pathResults.count { it.second }.toFloat() / pathResults.size
                pathReliability[path] = successRate
            }
        }
    }

    /**
     * Get reliability score for a path.
     */
    fun getPathReliability(path: TransportPath): Float {
        return pathReliability[path] ?: 0.5f
    }

    // ════════════════════════════════════════════════════════════════════
    // CONNECTION MANAGEMENT
    // ════════════════════════════════════════════════════════════════════

    /**
     * Connect all available transports.
     */
    fun connectAll() {
        Log.i(TAG, "Connecting all transports...")

        // Connect Telegram bridge if configured
        if (TelegramBridge.isConfigured()) {
            TelegramBridge.connect()
        }

        // Connect Supabase
        SupabaseChat.connect()

        // BLE mesh is handled by MeshService
    }

    /**
     * Disconnect all transports.
     */
    fun disconnectAll() {
        Log.i(TAG, "Disconnecting all transports...")
        TelegramBridge.disconnect()
        SupabaseChat.disconnect()
    }

    /**
     * Get current status summary.
     */
    fun getStatusSummary(): String {
        val telegram = if (TelegramBridge.isConnected.value) "✓" else "✗"
        val supabase = if (SupabaseChat.isConnected.value) "✓" else "✗"
        val mesh = if (_hasBluetooth.value) "✓" else "✗"
        val internet = if (_hasInternet.value) "✓" else "✗"

        return "TG:$telegram SB:$supabase BLE:$mesh NET:$internet | Active: ${_activePath.value}"
    }

    /**
     * Cleanup resources.
     */
    fun cleanup() {
        connectivityCallback?.let { callback ->
            appContext?.let { ctx ->
                val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
                cm.unregisterNetworkCallback(callback)
            }
        }
    }
}
