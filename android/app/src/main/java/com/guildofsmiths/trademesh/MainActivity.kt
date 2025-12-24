package com.guildofsmiths.trademesh

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.guildofsmiths.trademesh.data.Peer
import com.guildofsmiths.trademesh.data.UserPreferences
import com.guildofsmiths.trademesh.engine.BoundaryEngine
import com.guildofsmiths.trademesh.service.MeshService
import com.guildofsmiths.trademesh.ui.BeaconListScreen
import com.guildofsmiths.trademesh.ui.ChannelListScreen
import com.guildofsmiths.trademesh.ui.ConversationScreen
import com.guildofsmiths.trademesh.ui.ConversationViewModel
import com.guildofsmiths.trademesh.ui.CreateBeaconScreen
import com.guildofsmiths.trademesh.ui.CreateChannelScreen
import com.guildofsmiths.trademesh.ui.NavRoutes
import com.guildofsmiths.trademesh.ui.PeersScreen
import com.guildofsmiths.trademesh.ui.SettingsScreen
import com.guildofsmiths.trademesh.ui.WelcomeScreen
import com.guildofsmiths.trademesh.ui.theme.TradeMeshTheme

/**
 * Main activity for TradeMesh Phase 0.
 * Hosts navigation and manages BLE/network permissions.
 */
class MainActivity : ComponentActivity() {
    
    companion object {
        private const val TAG = "MainActivity"
    }
    
    private val viewModel: ConversationViewModel by viewModels()
    
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    
    // Permission launcher for Android 12+ BLE permissions
    private val blePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Log.i(TAG, "All BLE permissions granted")
            startMeshService()
        } else {
            Log.w(TAG, "Some BLE permissions denied: $permissions")
            Toast.makeText(
                this,
                "BLE permissions required for mesh communication",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    // Permission launcher for location (Android < 12)
    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Log.i(TAG, "Location permission granted")
            startMeshService()
        } else {
            Log.w(TAG, "Location permission denied")
            Toast.makeText(
                this,
                "Location permission required for BLE scanning",
                Toast.LENGTH_LONG
            ).show()
        }
    }
    
    // ══════════════════════════════════════════════════════════════════
    // MEDIA LAUNCHERS
    // ══════════════════════════════════════════════════════════════════
    
    /** Camera capture launcher */
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            Log.i(TAG, "📷 Camera capture successful")
            viewModel.onCameraCaptured(pendingDmPeerId, pendingDmPeerName)
        } else {
            Log.w(TAG, "📷 Camera capture cancelled or failed")
        }
        pendingDmPeerId = null
        pendingDmPeerName = null
    }
    
    /** File picker launcher */
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            Log.i(TAG, "📁 File selected: $uri")
            viewModel.onFileSelected(uri, pendingDmPeerId, pendingDmPeerName)
        } else {
            Log.w(TAG, "📁 File picker cancelled")
        }
        pendingDmPeerId = null
        pendingDmPeerName = null
    }
    
    /** Camera permission launcher */
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Log.i(TAG, "Camera permission granted")
            launchCamera()
        } else {
            Log.w(TAG, "Camera permission denied")
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
        }
    }
    
    /** Microphone permission launcher */
    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Log.i(TAG, "Microphone permission granted")
            startVoiceRecording()
        } else {
            Log.w(TAG, "Microphone permission denied")
            Toast.makeText(this, "Microphone permission required", Toast.LENGTH_SHORT).show()
        }
    }
    
    // Track DM context for media callbacks
    private var pendingDmPeerId: String? = null
    private var pendingDmPeerName: String? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        Log.i(TAG, "MainActivity onCreate")
        
        // Setup connectivity monitoring
        setupConnectivityMonitoring()
        
        // Request permissions and start mesh service
        checkAndRequestPermissions()
        
        // Setup UI with navigation
        setContent {
            TradeMeshTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    
                    // Determine start destination based on onboarding status
                    val startDestination = if (UserPreferences.hasUserName()) {
                        NavRoutes.BEACON_LIST
                    } else {
                        NavRoutes.WELCOME
                    }
                    
                    NavHost(
                        navController = navController,
                        startDestination = startDestination
                    ) {
                        // Welcome/onboarding screen
                        composable(NavRoutes.WELCOME) {
                            WelcomeScreen(
                                onComplete = { userName ->
                                    UserPreferences.setUserName(userName)
                                    UserPreferences.setOnboardingComplete()
                                    viewModel.setUserName(userName)
                                    navController.navigate(NavRoutes.BEACON_LIST) {
                                        popUpTo(NavRoutes.WELCOME) { inclusive = true }
                                    }
                                }
                            )
                        }
                        
                        // Beacon list screen
                        composable(NavRoutes.BEACON_LIST) {
                            BeaconListScreen(
                                onBeaconClick = { beacon ->
                                    navController.navigate(NavRoutes.channelList(beacon.id))
                                },
                                onSettingsClick = {
                                    navController.navigate(NavRoutes.SETTINGS)
                                },
                                onPeersClick = {
                                    navController.navigate(NavRoutes.PEERS)
                                },
                                onCreateBeaconClick = {
                                    navController.navigate(NavRoutes.CREATE_BEACON)
                                }
                            )
                        }
                        
                        // Settings screen
                        composable(NavRoutes.SETTINGS) {
                            SettingsScreen(
                                onBackClick = {
                                    navController.popBackStack()
                                },
                                onNameChanged = { newName ->
                                    viewModel.setUserName(newName)
                                }
                            )
                        }
                        
                        // Peers screen
                        composable(NavRoutes.PEERS) {
                            PeersScreen(
                                onBackClick = {
                                    navController.popBackStack()
                                },
                                onPeerClick = { peer ->
                                    // Just view peer info (optional)
                                },
                                onStartChat = { dmChannelId ->
                                    // Join the DM channel before navigating
                                    BoundaryEngine.joinChannel(dmChannelId)
                                    Log.d("MainActivity", "Starting chat in DM channel: $dmChannelId")
                                    
                                    // Navigate to channel list first, then to conversation
                                    navController.navigate(NavRoutes.conversation("default", dmChannelId))
                                }
                            )
                        }
                        
                        // Create beacon screen
                        composable(NavRoutes.CREATE_BEACON) {
                            CreateBeaconScreen(
                                onBackClick = {
                                    navController.popBackStack()
                                },
                                onBeaconCreated = { beacon ->
                                    navController.popBackStack()
                                    navController.navigate(NavRoutes.channelList(beacon.id))
                                }
                            )
                        }
                        
                        // Channel list screen
                        composable(
                            route = NavRoutes.CHANNEL_LIST,
                            arguments = listOf(
                                navArgument("beaconId") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val beaconId = backStackEntry.arguments?.getString("beaconId") ?: "default"
                            
                            ChannelListScreen(
                                beaconId = beaconId,
                                onChannelClick = { channel ->
                                    navController.navigate(NavRoutes.conversation(beaconId, channel.id))
                                },
                                onBackClick = {
                                    navController.popBackStack()
                                },
                                onCreateChannel = {
                                    navController.navigate(NavRoutes.createChannel(beaconId))
                                }
                            )
                        }
                        
                        // Create channel screen
                        composable(
                            route = NavRoutes.CREATE_CHANNEL,
                            arguments = listOf(
                                navArgument("beaconId") { type = NavType.StringType }
                            )
                        ) { backStackEntry ->
                            val beaconId = backStackEntry.arguments?.getString("beaconId") ?: "default"
                            
                            CreateChannelScreen(
                                beaconId = beaconId,
                                onBackClick = {
                                    navController.popBackStack()
                                },
                                onChannelCreated = { channel ->
                                    navController.popBackStack()
                                    navController.navigate(NavRoutes.conversation(beaconId, channel.id))
                                }
                            )
                        }
                        
                        // Conversation screen (with optional DM peer parameters)
                        composable(
                            route = "conversation/{beaconId}/{channelId}?dmPeerId={dmPeerId}&dmPeerName={dmPeerName}",
                            arguments = listOf(
                                navArgument("beaconId") { type = NavType.StringType },
                                navArgument("channelId") { type = NavType.StringType },
                                navArgument("dmPeerId") { 
                                    type = NavType.StringType
                                    nullable = true
                                    defaultValue = null
                                },
                                navArgument("dmPeerName") { 
                                    type = NavType.StringType
                                    nullable = true
                                    defaultValue = null
                                }
                            )
                        ) { backStackEntry ->
                            val beaconId = backStackEntry.arguments?.getString("beaconId") ?: "default"
                            val channelId = backStackEntry.arguments?.getString("channelId") ?: "general"
                            val dmPeerId = backStackEntry.arguments?.getString("dmPeerId")
                            val dmPeerName = backStackEntry.arguments?.getString("dmPeerName")
                            
                            // Set channel in viewModel
                            viewModel.setChannel(beaconId, channelId)
                            
                            val messages by viewModel.messages.collectAsState()
                            val channel by viewModel.currentChannel.collectAsState()
                            val beacon by viewModel.currentBeacon.collectAsState()
                            
                            // Create initial DM peer if passed via navigation
                            val initialDmPeer = if (dmPeerId != null && dmPeerName != null) {
                                Peer(userId = dmPeerId, userName = dmPeerName)
                            } else null
                            
                            // Check if user can delete for all in this channel
                            val canDeleteForAll = channel?.canDeleteForAll(viewModel.getLocalUserId()) ?: false
                            
                            // Track DM peer for media callbacks
                            var currentDmPeer by remember { mutableStateOf(initialDmPeer) }
                            
                            ConversationScreen(
                                messages = messages,
                                onSendMessage = { content, peer ->
                                    currentDmPeer = peer ?: initialDmPeer
                                    viewModel.sendMessage(
                                        content = content,
                                        recipientId = peer?.userId,
                                        recipientName = peer?.userName
                                    )
                                },
                                onMessageAction = { message, action ->
                                    viewModel.handleMessageAction(message, action)
                                },
                                localUserId = viewModel.getLocalUserId(),
                                channel = channel,
                                beaconName = beacon?.name,
                                canDeleteForAll = canDeleteForAll,
                                onBackClick = {
                                    navController.popBackStack()
                                },
                                onVoiceClick = {
                                    pendingDmPeerId = currentDmPeer?.userId ?: initialDmPeer?.userId
                                    pendingDmPeerName = currentDmPeer?.userName ?: initialDmPeer?.userName
                                    requestMicrophoneAndRecord()
                                },
                                onCameraClick = {
                                    pendingDmPeerId = currentDmPeer?.userId ?: initialDmPeer?.userId
                                    pendingDmPeerName = currentDmPeer?.userName ?: initialDmPeer?.userName
                                    requestCameraAndCapture()
                                },
                                onFileClick = {
                                    pendingDmPeerId = currentDmPeer?.userId ?: initialDmPeer?.userId
                                    pendingDmPeerName = currentDmPeer?.userName ?: initialDmPeer?.userName
                                    launchFilePicker()
                                },
                                initialDmPeer = initialDmPeer
                            )
                        }
                    }
                }
            }
        }
    }
    
    override fun onDestroy() {
        // Unregister network callback
        networkCallback?.let {
            connectivityManager?.unregisterNetworkCallback(it)
        }
        super.onDestroy()
    }
    
    /**
     * Check and request required BLE permissions.
     */
    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ requires BLUETOOTH_SCAN, BLUETOOTH_ADVERTISE, BLUETOOTH_CONNECT
            val requiredPermissions = arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.POST_NOTIFICATIONS
            )
            
            val missingPermissions = requiredPermissions.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }
            
            if (missingPermissions.isEmpty()) {
                Log.i(TAG, "All permissions already granted")
                startMeshService()
            } else {
                Log.i(TAG, "Requesting permissions: $missingPermissions")
                blePermissionLauncher.launch(missingPermissions.toTypedArray())
            }
        } else {
            // Android < 12 requires ACCESS_FINE_LOCATION for BLE scanning
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                Log.i(TAG, "Location permission already granted")
                startMeshService()
            } else {
                Log.i(TAG, "Requesting location permission")
                locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
    }
    
    /**
     * Start the mesh service as a foreground service.
     */
    private fun startMeshService() {
        Log.i(TAG, "Starting MeshService")
        val serviceIntent = Intent(this, MeshService::class.java)
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }
    }
    
    /**
     * Setup connectivity monitoring to detect network changes.
     * Triggers mesh sync when connectivity is restored.
     */
    private fun setupConnectivityMonitoring() {
        connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as? ConnectivityManager
        
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.d(TAG, "Network available")
                BoundaryEngine.updateConnectivityState(this@MainActivity)
            }
            
            override fun onLost(network: Network) {
                Log.d(TAG, "Network lost")
                BoundaryEngine.updateConnectivityState(this@MainActivity)
            }
            
            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                BoundaryEngine.updateConnectivityState(this@MainActivity)
            }
        }
        
        connectivityManager?.registerNetworkCallback(networkRequest, networkCallback!!)
        
        // Set initial state
        BoundaryEngine.updateConnectivityState(this)
    }
    
    // ══════════════════════════════════════════════════════════════════
    // MEDIA HELPERS
    // ══════════════════════════════════════════════════════════════════
    
    /**
     * Request camera permission and launch camera if granted.
     */
    private fun requestCameraAndCapture() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
                == PackageManager.PERMISSION_GRANTED -> {
                launchCamera()
            }
            else -> {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
    
    /**
     * Launch the camera to capture a photo.
     */
    private fun launchCamera() {
        val uri = viewModel.createCameraUri()
        if (uri != null) {
            cameraLauncher.launch(uri)
        } else {
            Toast.makeText(this, "Failed to create camera file", Toast.LENGTH_SHORT).show()
        }
    }
    
    /**
     * Request microphone permission and start recording if granted.
     */
    private fun requestMicrophoneAndRecord() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) 
                == PackageManager.PERMISSION_GRANTED -> {
                startVoiceRecording()
            }
            else -> {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }
    
    /**
     * Toggle voice recording (start/stop).
     * Called when the mic button is pressed.
     */
    private fun startVoiceRecording() {
        viewModel.toggleVoiceRecording(pendingDmPeerId, pendingDmPeerName)
    }
    
    /**
     * Launch file picker for any file type.
     */
    private fun launchFilePicker() {
        filePickerLauncher.launch("*/*")
    }
}
