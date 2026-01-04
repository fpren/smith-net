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
import com.guildofsmiths.trademesh.data.SupabaseAuth
import com.guildofsmiths.trademesh.data.UserPreferences
import com.guildofsmiths.trademesh.engine.BoundaryEngine
import com.guildofsmiths.trademesh.service.MeshService
import com.guildofsmiths.trademesh.service.NotificationHelper
import com.guildofsmiths.trademesh.service.AuthService
import com.guildofsmiths.trademesh.ui.ArchiveScreen
import com.guildofsmiths.trademesh.ui.AuthScreen
import com.guildofsmiths.trademesh.ui.BeaconListScreen
import com.guildofsmiths.trademesh.ui.ChannelListScreen
import com.guildofsmiths.trademesh.ui.ChannelsScreen
import com.guildofsmiths.trademesh.ui.ConversationScreen
import com.guildofsmiths.trademesh.ui.ConversationViewModel
import com.guildofsmiths.trademesh.ui.CreateBeaconScreen
import com.guildofsmiths.trademesh.ui.CreateChannelScreen
import com.guildofsmiths.trademesh.ui.NavRoutes
import com.guildofsmiths.trademesh.ui.OnboardingScreen
import com.guildofsmiths.trademesh.ui.ProfileScreen
import com.guildofsmiths.trademesh.ui.PeersScreen
import com.guildofsmiths.trademesh.ui.SettingsScreen
import com.guildofsmiths.trademesh.ui.WelcomeScreen
import com.guildofsmiths.trademesh.ui.jobboard.JobBoardScreen
import com.guildofsmiths.trademesh.ui.timetracking.TimeTrackingScreen
import com.guildofsmiths.trademesh.ui.PlanScreen
import com.guildofsmiths.trademesh.ui.DashboardScreen
import com.guildofsmiths.trademesh.ui.SmithNetDashboard
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
            // Don't start mesh service directly - let initializeCommunication handle it
            // when Planner Container is ready
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
            // Don't start mesh service directly - let initializeCommunication handle it
            // when Planner Container is ready
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
    
    /** Video capture launcher */
    private val videoLauncher = registerForActivityResult(
        ActivityResultContracts.CaptureVideo()
    ) { success ->
        if (success) {
            Log.i(TAG, "🎬 Video capture successful")
            viewModel.onVideoCaptured(pendingDmPeerId, pendingDmPeerName)
        } else {
            Log.w(TAG, "🎬 Video capture cancelled or failed")
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

        // Initialize Supabase Auth
        SupabaseAuth.init(this)
        
        // Handle deep link if app was launched from auth callback
        handleAuthDeepLink(intent)
        
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
                    
                    // CANONICAL FLOW:
                    // APP LAUNCH → LOGIN → AUTH SUCCESS → ONBOARDING → DASHBOARD (hub with Plan tab)
                    // Priority: Not logged in → Auth, Logged in but no onboarding → Onboarding, Complete → Dashboard
                    val startDestination = when {
                        !SupabaseAuth.isLoggedIn() -> {
                            // User not authenticated - show auth screen
                            NavRoutes.AUTH
                        }
                        !UserPreferences.isOnboardingDataComplete() -> {
                            // User authenticated but hasn't completed onboarding
                            NavRoutes.ONBOARDING
                        }
                        else -> {
                            // User authenticated and completed onboarding - go to Dashboard
                            NavRoutes.DASHBOARD
                        }
                    }
                    
                    NavHost(
                        navController = navController,
                        startDestination = startDestination
                    ) {
                        // Auth screen (C-01) - Supabase Auth
                        composable(NavRoutes.AUTH) {
                            AuthScreen(
                                onAuthSuccess = {
                                    // Sync auth name with local preferences (IDENTITY ONLY)
                                    val name = SupabaseAuth.getUserName()
                                        ?: AuthService.getUserName()
                                        ?: "User"
                                    UserPreferences.setUserName(name)
                                    viewModel.setUserName(name)

                                    // DO NOT set onboarding complete here - that's system configuration
                                    // Check if system is already configured, then navigate appropriately
                                    if (UserPreferences.isOnboardingDataComplete()) {
                                        // System already configured - go to Dashboard
                                        navController.navigate(NavRoutes.DASHBOARD) {
                                            popUpTo(NavRoutes.AUTH) { inclusive = true }
                                        }
                                    } else {
                                        // System not configured - go to onboarding
                                        navController.navigate(NavRoutes.ONBOARDING) {
                                            popUpTo(NavRoutes.AUTH) { inclusive = true }
                                        }
                                    }
                                },
                                onSkip = {
                                    // Skip to welcome for offline/mesh mode (limited)
                                    navController.navigate(NavRoutes.WELCOME) {
                                        popUpTo(NavRoutes.AUTH) { inclusive = true }
                                    }
                                }
                            )
                        }
                        
                        // Welcome/onboarding screen (offline mode)
                        composable(NavRoutes.WELCOME) {
                            WelcomeScreen(
                                onComplete = { userName ->
                                    UserPreferences.setUserName(userName)
                                    UserPreferences.setOnboardingComplete()
                                    viewModel.setUserName(userName)
                                    // Go to Dashboard
                                    navController.navigate(NavRoutes.DASHBOARD) {
                                        popUpTo(NavRoutes.WELCOME) { inclusive = true }
                                    }
                                }
                            )
                        }

                        // Post-launch guided setup (5-screen onboarding: Language → Address → Work → Business → Auth)
                        composable(NavRoutes.ONBOARDING) {
                            OnboardingScreen(
                                onComplete = {
                                    // SYSTEM CONFIGURATION COMPLETE - Mark onboarding as complete
                                    UserPreferences.setOnboardingComplete()

                                    // Initialize Planner Container (main operational state)
                                    initializePlannerContainer()

                                    // Navigate to Dashboard
                                    navController.navigate(NavRoutes.DASHBOARD) {
                                        popUpTo(NavRoutes.ONBOARDING) { inclusive = true }
                                    }
                                }
                            )
                        }

                        // ════════════════════════════════════════════════════════════════
                        // DASHBOARD - Central Operational Hub (Tabbed Interface)
                        // ════════════════════════════════════════════════════════════════
                        composable(NavRoutes.DASHBOARD) {
                            // Initialize communication when dashboard loads
                            if (UserPreferences.isOnboardingDataComplete()) {
                                initializeCommunication()
                            }

                            SmithNetDashboard(
                                onProfileClick = {
                                    navController.navigate(NavRoutes.PROFILE)
                                },
                                onSettingsClick = {
                                    navController.navigate(NavRoutes.SETTINGS)
                                },
                                onJobBoardClick = {
                                    navController.navigate(NavRoutes.JOB_BOARD)
                                },
                                onTimeTrackingClick = {
                                    navController.navigate(NavRoutes.TIME_TRACKING)
                                },
                                onMessagesClick = {
                                    navController.navigate(NavRoutes.BEACON_LIST)
                                },
                                onArchiveClick = {
                                    navController.navigate(NavRoutes.ARCHIVE)
                                },
                                onPlanClick = {
                                    navController.navigate(NavRoutes.PLAN)
                                }
                            )
                        }
                        
                        // Beacon list screen (Planner Container - Operational Core)
                        composable(NavRoutes.BEACON_LIST) {
                            // Planner Container loads - ensure communication is initialized
                            if (UserPreferences.isOnboardingDataComplete()) {
                                initializeCommunication()
                            }

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
                                onProfileClick = {
                                    navController.navigate(NavRoutes.PROFILE)
                                },
                                onBackGesture = {
                                    // Swipe right to go back to Dashboard
                                    navController.popBackStack()
                                },
                                onCreateBeaconClick = {
                                    navController.navigate(NavRoutes.CREATE_BEACON)
                                }
                            )
                        }
                        
                        // Profile screen
                        composable(NavRoutes.PROFILE) {
                            ProfileScreen(
                                onNavigateBack = { navController.popBackStack() },
                                onSignOut = {
                                    // Navigate back to auth screen
                                    navController.navigate(NavRoutes.AUTH) {
                                        popUpTo(0) { inclusive = true }
                                    }
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
                                },
                                onSignOut = {
                                    // Navigate to auth screen and clear backstack
                                    navController.navigate(NavRoutes.AUTH) {
                                        popUpTo(0) { inclusive = true }
                                    }
                                }
                            )
                        }
                        
                        // C-11: Job Board
                        composable(NavRoutes.JOB_BOARD) {
                            JobBoardScreen(
                                onNavigateBack = {
                                    navController.popBackStack()
                                },
                                onSettingsClick = {
                                    navController.navigate(NavRoutes.SETTINGS)
                                },
                                onNavigateToPlan = { jobId ->
                                    // Navigate to Plan with the job context
                                    navController.navigate(NavRoutes.planWithJob(jobId))
                                }
                            )
                        }

                        // C-12: Time Tracking
                        composable(NavRoutes.TIME_TRACKING) {
                            TimeTrackingScreen(
                                onNavigateBack = {
                                    navController.popBackStack()
                                },
                                onSettingsClick = {
                                    navController.navigate(NavRoutes.SETTINGS)
                                }
                            )
                        }

                        // C-13: Archive
                        composable(NavRoutes.ARCHIVE) {
                            ArchiveScreen(
                                onNavigateBack = {
                                    navController.popBackStack()
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
                        
                        // Dashboard channels screen - discover and join channels from dashboard
                        composable(NavRoutes.DASHBOARD_CHANNELS) {
                            ChannelsScreen(
                                onBackClick = {
                                    navController.popBackStack()
                                },
                                onChannelJoined = { channelId ->
                                    // Navigate to conversation with the joined channel
                                    Log.e("MainActivity", "████ onChannelJoined: $channelId ████")
                                    // URL encode the channel ID to handle special characters
                                    val encodedChannelId = java.net.URLEncoder.encode(channelId, "UTF-8")
                                    val route = NavRoutes.conversation("default", encodedChannelId)
                                    Log.e("MainActivity", "████ Route: $route ████")
                                    navController.navigate(route)
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
                                },
                                onJoinDashboardChannels = {
                                    navController.navigate(NavRoutes.DASHBOARD_CHANNELS)
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
                            val rawChannelId = backStackEntry.arguments?.getString("channelId") ?: "general"
                            // Decode URL-encoded channel ID
                            val channelId = try {
                                java.net.URLDecoder.decode(rawChannelId, "UTF-8")
                            } catch (e: Exception) {
                                rawChannelId
                            }
                            val dmPeerId = backStackEntry.arguments?.getString("dmPeerId")
                            val dmPeerName = backStackEntry.arguments?.getString("dmPeerName")
                            
                            Log.i("MainActivity", "ConversationScreen - beaconId: $beaconId, rawChannelId: $rawChannelId, channelId: $channelId")
                            
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
                                onVideoClick = {
                                    pendingDmPeerId = currentDmPeer?.userId ?: initialDmPeer?.userId
                                    pendingDmPeerName = currentDmPeer?.userName ?: initialDmPeer?.userName
                                    launchVideoCapture()
                                },
                                onFileClick = {
                                    pendingDmPeerId = currentDmPeer?.userId ?: initialDmPeer?.userId
                                    pendingDmPeerName = currentDmPeer?.userName ?: initialDmPeer?.userName
                                    launchFilePicker()
                                },
                                initialDmPeer = initialDmPeer
                            )
                        }

                        // P-01: PLAN - SmithNet Canonical Implementation
                        // PLAN opens as first screen after auth, X exits to Dashboard
                        // Also handles plan?jobId={jobId} for loading from Job Board
                        composable(
                            route = "${NavRoutes.PLAN}?jobId={jobId}",
                            arguments = listOf(
                                navArgument("jobId") { 
                                    type = NavType.StringType
                                    nullable = true
                                    defaultValue = null
                                }
                            )
                        ) { backStackEntry ->
                            val jobId = backStackEntry.arguments?.getString("jobId")
                            PlanScreen(
                                onNavigateBack = {
                                    // X pressed - go to Dashboard (not back)
                                    // If backstack is empty (first time), navigate to Dashboard
                                    if (!navController.popBackStack()) {
                                        navController.navigate(NavRoutes.DASHBOARD) {
                                            popUpTo(NavRoutes.PLAN) { inclusive = true }
                                        }
                                    }
                                },
                                initialJobId = jobId
                            )
                        }
                    }
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        NotificationHelper.setAppForeground(true)
        NotificationHelper.cancelAll(this)  // Clear notifications when app opens
    }
    
    override fun onPause() {
        super.onPause()
        NotificationHelper.setAppForeground(false)
    }
    
    override fun onDestroy() {
        // Unregister network callback
        networkCallback?.let {
            connectivityManager?.unregisterNetworkCallback(it)
        }
        super.onDestroy()
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle deep link when app is already running
        handleAuthDeepLink(intent)
    }
    
    /**
     * Handle Supabase auth deep links (email confirmation, magic links, etc.)
     */
    private fun handleAuthDeepLink(intent: Intent?) {
        val uri = intent?.data ?: return
        val scheme = uri.scheme ?: return
        
        Log.i(TAG, "Deep link received: $uri")
        
        when {
            // Custom scheme: guildofsmiths://auth?...
            scheme == "guildofsmiths" && uri.host == "auth" -> {
                handleSupabaseCallback(uri.toString())
            }
            // HTTPS callback from web portal
            scheme == "https" && uri.host?.contains("aegisassure.org") == true -> {
                handleSupabaseCallback(uri.toString())
            }
            // Legacy scheme: trademesh://auth?...
            scheme == "trademesh" && uri.host == "auth" -> {
                handleSupabaseCallback(uri.toString())
            }
            // Legacy HTTPS callback from Supabase
            scheme == "https" && uri.host?.contains("supabase") == true -> {
                handleSupabaseCallback(uri.toString())
            }
        }
    }
    
    /**
     * Process Supabase auth callback URL
     */
    private fun handleSupabaseCallback(url: String) {
        Log.i(TAG, "Processing Supabase callback: $url")
        
        // Extract tokens from URL fragment or query params
        // Supabase sends: access_token, refresh_token, type, etc.
        val uri = android.net.Uri.parse(url)
        
        // Check for access_token in fragment (after #)
        val fragment = uri.fragment
        if (fragment != null) {
            val params = fragment.split("&").associate {
                val parts = it.split("=")
                if (parts.size == 2) parts[0] to parts[1] else "" to ""
            }
            
            val accessToken = params["access_token"]
            val refreshToken = params["refresh_token"]
            val type = params["type"]
            
            Log.i(TAG, "Auth callback type: $type, hasAccessToken: ${accessToken != null}")
            
            if (accessToken != null) {
                // Email confirmed! User is now authenticated via web portal
                Toast.makeText(this, "Email confirmed! Welcome to Guild of Smiths", Toast.LENGTH_LONG).show()

                // Mark user as web-authenticated for onboarding flow
                UserPreferences.setWebAuthenticated(true)
                
                // The Supabase client should automatically pick up the session
                // Trigger a refresh to update the UI
                SupabaseAuth.refreshSession()
            }
        }
        
        // Check for error
        val error = uri.getQueryParameter("error")
        val errorDescription = uri.getQueryParameter("error_description")
        if (error != null) {
            Log.e(TAG, "Auth error: $error - $errorDescription")
            Toast.makeText(this, "Auth error: $errorDescription", Toast.LENGTH_LONG).show()
        }
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
     * Initialize Planner Container after onboarding completes.
     * This creates the operational core with jobs, timers, permissions.
     * Gates mesh/chat initialization behind system configuration.
     */
    private fun initializePlannerContainer() {
        Log.i(TAG, "Initializing Planner Container - System Configuration Complete")

        // Initialize operational state
        // - Job definitions
        // - Task assignments
        // - Time tracking state
        // - Report aggregation
        // - Permissions from onboarding

        // Now that system is configured, initialize communication (chat + mesh)
        initializeCommunication()
    }

    /**
     * Initialize communication systems (Chat + Mesh).
     * Only called after Planner Container is ready (onboarding complete).
     */
    private fun initializeCommunication() {
        Log.i(TAG, "Initializing Communication - Planner Container Ready")

        // Request permissions and start mesh service (communication layer)
        checkAndRequestPermissions()

        // Chat can now initialize as a tool of the Planner Container
        // Mesh identity is created here (temporary/runtime-only)
    }
    
    /**
     * Setup connectivity monitoring to detect network changes.
     * Triggers mesh sync when connectivity is restored.
     */
    private fun setupConnectivityMonitoring() {
        connectivityManager = getSystemService(CONNECTIVITY_SERVICE) as? ConnectivityManager
        
        val networkRequest = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
            .build()
        
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                Log.i(TAG, "🔗 NETWORK AVAILABLE: $network")
                val capabilities = connectivityManager?.getNetworkCapabilities(network)
                Log.i(TAG, "   Capabilities: WIFI=${capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)}, CELLULAR=${capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)}")
                BoundaryEngine.updateConnectivityState(this@MainActivity)
            }

            override fun onLost(network: Network) {
                Log.i(TAG, "🔌 NETWORK LOST: $network")
                BoundaryEngine.updateConnectivityState(this@MainActivity)
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                Log.i(TAG, "📡 NETWORK CAPABILITIES CHANGED: $network")
                Log.i(TAG, "   WIFI: ${networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)}")
                Log.i(TAG, "   CELLULAR: ${networkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)}")
                Log.i(TAG, "   INTERNET: ${networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)}")
                Log.i(TAG, "   VALIDATED: ${networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)}")
                BoundaryEngine.updateConnectivityState(this@MainActivity)
            }

            override fun onUnavailable() {
                Log.w(TAG, "🚫 NETWORK UNAVAILABLE")
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
    
    /**
     * Launch video capture.
     */
    private fun launchVideoCapture() {
        // Check camera permission first
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) 
            != PackageManager.PERMISSION_GRANTED) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }
        
        val uri = viewModel.createVideoUri()
        if (uri != null) {
            videoLauncher.launch(uri)
        } else {
            Toast.makeText(this, "Failed to create video file", Toast.LENGTH_SHORT).show()
        }
    }
}
