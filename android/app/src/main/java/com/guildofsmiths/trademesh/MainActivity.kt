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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
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
import androidx.navigation.compose.currentBackStackEntryAsState

import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.guildofsmiths.trademesh.data.Peer
import com.guildofsmiths.trademesh.data.SupabaseAuth
import com.guildofsmiths.trademesh.data.BeaconRepository
import com.guildofsmiths.trademesh.data.UserPreferences
import com.guildofsmiths.trademesh.engine.BoundaryEngine
import com.guildofsmiths.trademesh.service.MeshService
import com.guildofsmiths.trademesh.service.NotificationHelper
import com.guildofsmiths.trademesh.service.AuthService
import com.guildofsmiths.trademesh.ui.ArchiveScreen
import com.guildofsmiths.trademesh.ui.AuthScreen
import com.guildofsmiths.trademesh.ui.BeaconListScreen
import com.guildofsmiths.trademesh.ui.ChatListScreen
import com.guildofsmiths.trademesh.ui.NewConversationScreen
import com.guildofsmiths.trademesh.ui.ChannelListScreen
import com.guildofsmiths.trademesh.ui.ChannelsScreen
import com.guildofsmiths.trademesh.ui.ConsoleTheme
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
import com.guildofsmiths.trademesh.ui.clients.ClientsScreen
import com.guildofsmiths.trademesh.ui.clients.ClientDetailScreen
import com.guildofsmiths.trademesh.ui.jobboard.JobBoardScreen
import com.guildofsmiths.trademesh.ui.timetracking.TimeTrackingScreen
import com.guildofsmiths.trademesh.ui.theme.TradeMeshTheme
import androidx.lifecycle.viewmodel.compose.viewModel

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
        val jobId = pendingJobPhotoId
        val uri = pendingJobPhotoUri
        if (success) {
            if (jobId != null && uri != null) {
                Log.i(TAG, "📷 Job photo captured for job $jobId")
                jobPhotoCallback?.invoke(jobId, uri.toString())
            } else {
                Log.i(TAG, "📷 Camera capture successful (messaging)")
                viewModel.onCameraCaptured(pendingDmPeerId, pendingDmPeerName)
            }
        } else {
            Log.w(TAG, "📷 Camera capture cancelled or failed")
        }
        pendingDmPeerId = null
        pendingDmPeerName = null
        pendingJobPhotoId = null
        pendingJobPhotoUri = null
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

    // Track job-photo context for media callbacks
    private var pendingJobPhotoId: String? = null
    private var pendingJobPhotoUri: android.net.Uri? = null
    private var jobPhotoCallback: ((String, String) -> Unit)? = null

    fun captureJobPhoto(jobId: String, onCaptured: (String, String) -> Unit) {
        jobPhotoCallback = onCaptured
        pendingJobPhotoId = jobId
        val uri = viewModel.createCameraUri()
        if (uri == null) {
            Toast.makeText(this, "Failed to create camera file", Toast.LENGTH_SHORT).show()
            pendingJobPhotoId = null
            return
        }
        pendingJobPhotoUri = uri
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED) {
            cameraLauncher.launch(uri)
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
    
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

                    // Start / stop the GPS foreground service when clock state changes.
                    // Uses a shared TimeTrackingViewModel so every screen sees the same
                    // clock-in flag. No-op if the user disabled location sharing in Settings
                    // or hasn't granted the runtime permission yet.
                    val ttvm: com.guildofsmiths.trademesh.ui.timetracking.TimeTrackingViewModel = viewModel(viewModelStoreOwner = this@MainActivity)
                    val isClockedInFlag by ttvm.isClockedIn.collectAsState()
                    val appContext = this@MainActivity.applicationContext
                    LaunchedEffect(isClockedInFlag) {
                        if (isClockedInFlag) {
                            com.guildofsmiths.trademesh.service.LocationService.start(appContext)
                        } else {
                            com.guildofsmiths.trademesh.service.LocationService.stop(appContext)
                        }
                    }

                    // Activity-scoped Job Board VM — shared across composables so
                    // the migration here writes to the same instance the rest of
                    // the app reads from.
                    val sharedJobVm: com.guildofsmiths.trademesh.ui.jobboard.JobBoardViewModel =
                        viewModel(viewModelStoreOwner = this@MainActivity)

                    // Wire ttvm to auto-create a Job whenever the user clocks
                    // in with a free-text title. Returns the new jobId so the
                    // TimeEntry is tagged correctly from the start (no more
                    // jobId="" orphans that miss financial rollups).
                    LaunchedEffect(Unit) {
                        ttvm.onResolveJobIdForFreeText = { title ->
                            val existing = sharedJobVm.jobs.value.firstOrNull { j ->
                                ((j.clientName ?: j.title).trim().equals(title, ignoreCase = true))
                            }
                            if (existing != null) existing.id
                            else {
                                val before = sharedJobVm.jobs.value
                                sharedJobVm.createJob(
                                    title = title,
                                    clientName = title,
                                    hourlyRate = UserPreferences.getHourlyRate(),
                                    stage = com.guildofsmiths.trademesh.ui.jobboard.JobStage.LEAD
                                )
                                sharedJobVm.jobs.value
                                    .firstOrNull { it.id !in before.map { b -> b.id } }
                                    ?.id
                            }
                        }
                    }

                    // One-time backfill: promote free-text time entries to real
                    // Jobs so financials, clients, and the Job Board all line up
                    // with the hours actually worked. Gated by a SharedPreferences
                    // flag so it runs at most once per install.
                    LaunchedEffect(Unit) {
                        if (UserPreferences.isFreeTextBackfillDone()) return@LaunchedEffect
                        // Wait a beat so both VMs finish their restorePersistedState.
                        kotlinx.coroutines.delay(500)
                        val titleToJobId = mutableMapOf<String, String>()
                        val existingJobs = sharedJobVm.jobs.value
                        val existingTitles = existingJobs.associateBy { (it.clientName ?: it.title).trim().lowercase() }
                        val freeTextEntries = com.guildofsmiths.trademesh.data.TimeEntryRepository.entries.value
                            .filter { it.jobId.isNullOrBlank() && !it.jobTitle.isNullOrBlank() }
                        val titles = freeTextEntries.map { it.jobTitle!!.trim() }.distinct()
                        if (titles.isEmpty()) {
                            UserPreferences.setFreeTextBackfillDone()
                            return@LaunchedEffect
                        }
                        val defaultRate = UserPreferences.getHourlyRate()
                        for (title in titles) {
                            val key = title.lowercase()
                            val existing = existingTitles[key]
                            if (existing != null) {
                                titleToJobId[title] = existing.id
                                continue
                            }
                            val before = sharedJobVm.jobs.value
                            sharedJobVm.createJob(
                                title = title,
                                clientName = title,
                                hourlyRate = defaultRate,
                                stage = com.guildofsmiths.trademesh.ui.jobboard.JobStage.LEAD
                            )
                            val newJob = sharedJobVm.jobs.value.lastOrNull()
                            if (newJob != null && newJob.id !in before.map { it.id }) {
                                titleToJobId[title] = newJob.id
                            }
                        }
                        ttvm.relinkEntriesByTitle(titleToJobId)
                        android.util.Log.i("Backfill", "promoted ${titleToJobId.size} free-text titles to Jobs")
                        UserPreferences.setFreeTextBackfillDone()
                    }

                    // Lifecycle backfill (v2): align each Job's status/stage/
                    // updatedAt with its actual TimeEntry history. A Job with
                    // completed entries but no active session is DONE/CLOSED
                    // at the latest clock-out; a Job with an active session is
                    // IN_PROGRESS. Skips Jobs the user manually advanced past
                    // CLOSED to avoid clobbering deliberate state.
                    LaunchedEffect(Unit) {
                        if (UserPreferences.isLifecycleBackfillDone()) return@LaunchedEffect
                        kotlinx.coroutines.delay(800)
                        var fixes = 0
                        sharedJobVm.jobs.value.forEach { job ->
                            val entries = com.guildofsmiths.trademesh.data.TimeEntryRepository
                                .getEntriesForJob(job.id, job.title)
                            if (entries.isEmpty()) return@forEach
                            val firstIn = entries.minOfOrNull { it.clockInTime }
                            val activeEntry = entries.firstOrNull { it.clockOutTime == null }
                            val lastOut = entries.mapNotNull { it.clockOutTime }.maxOrNull()
                            // v3 also fires when status is DONE/IN_PROGRESS but
                            // the createdAt is later than the earliest clockIn —
                            // happens when v2 corrected status without aligning
                            // createdAt.
                            val needsCreatedAtFix = firstIn != null && job.createdAt > firstIn
                            when {
                                activeEntry != null &&
                                    (job.status == com.guildofsmiths.trademesh.ui.jobboard.JobStatus.TODO ||
                                        needsCreatedAtFix) -> {
                                    sharedJobVm.updateJobLifecycle(
                                        jobId = job.id,
                                        status = com.guildofsmiths.trademesh.ui.jobboard.JobStatus.IN_PROGRESS,
                                        stage = com.guildofsmiths.trademesh.ui.jobboard.JobStage.IN_PROGRESS,
                                        createdAt = firstIn,
                                        updatedAt = activeEntry.clockInTime,
                                        completedAt = null
                                    )
                                    fixes++
                                }
                                activeEntry == null && lastOut != null &&
                                    (job.status == com.guildofsmiths.trademesh.ui.jobboard.JobStatus.TODO ||
                                        job.status == com.guildofsmiths.trademesh.ui.jobboard.JobStatus.DONE && needsCreatedAtFix) -> {
                                    sharedJobVm.updateJobLifecycle(
                                        jobId = job.id,
                                        status = com.guildofsmiths.trademesh.ui.jobboard.JobStatus.DONE,
                                        stage = com.guildofsmiths.trademesh.ui.jobboard.JobStage.CLOSED,
                                        createdAt = firstIn,
                                        updatedAt = lastOut,
                                        completedAt = lastOut
                                    )
                                    fixes++
                                }
                            }
                        }
                        android.util.Log.i("Backfill", "lifecycle corrected $fixes Job(s)")
                        UserPreferences.setLifecycleBackfillDone()
                    }

                    // Determine start destination - auth first, then onboarding
                    // Priority: Not logged in → Auth, Logged in but no onboarding → Onboarding, Complete → Dashboard
                    val startDestination = when {
                        !SupabaseAuth.isLoggedIn() -> {
                            // User not authenticated - show auth screen
                            NavRoutes.AUTH
                        }
                        !UserPreferences.isOnboardingComplete() -> {
                            // User authenticated but hasn't completed onboarding
                            NavRoutes.ONBOARDING
                        }
                        else -> {
                            // User authenticated and completed onboarding - go to Dashboard
                            NavRoutes.DASHBOARD
                        }
                    }

                    // Routes that show the bottom nav bar
                    val navBarRoutes = setOf(
                        NavRoutes.DASHBOARD, NavRoutes.JOB_BOARD,
                        NavRoutes.TIME_TRACKING, NavRoutes.ARCHIVE,
                        NavRoutes.REPORT, NavRoutes.SUPPLY,
                        NavRoutes.CLIENTS, NavRoutes.CHAT_LIST,
                        NavRoutes.SETTINGS, NavRoutes.PROFILE,
                        NavRoutes.MAP, NavRoutes.DISPATCH,
                        NavRoutes.PLAN, NavRoutes.EXPENSES
                    )

                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route

                    androidx.compose.foundation.layout.Column(
                        modifier = Modifier.fillMaxSize()
                    ) {
                    NavHost(
                        navController = navController,
                        startDestination = startDestination,
                        modifier = Modifier.weight(1f)
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

                                    // Navigate to Dashboard (main hub)
                                    navController.navigate(NavRoutes.DASHBOARD) {
                                        popUpTo(NavRoutes.ONBOARDING) { inclusive = true }
                                    }
                                }
                            )
                        }

                        // Dashboard screen (main hub)
                        composable(NavRoutes.DASHBOARD) {
                            if (UserPreferences.isOnboardingDataComplete()) {
                                initializeCommunication()
                            }

                            val jobViewModel: com.guildofsmiths.trademesh.ui.jobboard.JobBoardViewModel = viewModel(viewModelStoreOwner = this@MainActivity)
                            val jobs by jobViewModel.jobs.collectAsState()

                            com.guildofsmiths.trademesh.ui.dashboard.DashboardScreen(
                                jobs = jobs,
                                onJobClick = { jobId ->
                                    navController.navigate(NavRoutes.jobPipeline(jobId))
                                },
                                onNewJob = {
                                    navController.navigate(NavRoutes.NEW_JOB)
                                },
                                onClockIn = {
                                    navController.navigate(NavRoutes.TIME_TRACKING)
                                },
                                onComm = {
                                    navController.navigate(NavRoutes.CHAT_LIST)
                                },
                                onSettings = {
                                    navController.navigate(NavRoutes.SETTINGS)
                                },
                                onProfile = {
                                    navController.navigate(NavRoutes.PROFILE)
                                },
                                onArchive = {
                                    navController.navigate(NavRoutes.ARCHIVE)
                                },
                                onJobBoard = {
                                    navController.navigate(NavRoutes.JOB_BOARD)
                                },
                                onDispatch = {
                                    navController.navigate(NavRoutes.DISPATCH)
                                },
                                onMessageCrew = { crewMember ->
                                    val myUserId = UserPreferences.getUserId()
                                    val dm = com.guildofsmiths.trademesh.data.BeaconRepository.getOrCreateDM(
                                        "default", myUserId, crewMember.userId, crewMember.name
                                    )
                                    navController.navigate(NavRoutes.conversation("default", dm.id))
                                },
                                onTimeTracking = {
                                    navController.navigate(NavRoutes.TIME_TRACKING)
                                },
                                onPlan = {
                                    navController.navigate(NavRoutes.JOB_BOARD)
                                },
                                onReport = {
                                    navController.navigate(NavRoutes.REPORT)
                                },
                                onSupply = {
                                    navController.navigate(NavRoutes.SUPPLY)
                                },
                                onClients = {
                                    navController.navigate(NavRoutes.CLIENTS)
                                },
                                onMap = {
                                    navController.navigate(NavRoutes.MAP)
                                },
                                onExpenses = {
                                    navController.navigate(NavRoutes.EXPENSES)
                                }
                            )
                        }

                        // Job Pipeline detail screen
                        composable(
                            route = NavRoutes.JOB_PIPELINE,
                            arguments = listOf(navArgument("jobId") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val jobId = backStackEntry.arguments?.getString("jobId") ?: return@composable
                            val jobViewModel: com.guildofsmiths.trademesh.ui.jobboard.JobBoardViewModel = viewModel(viewModelStoreOwner = this@MainActivity)
                            val jobs by jobViewModel.jobs.collectAsState()
                            val job = jobs.find { it.id == jobId }

                            if (job != null) {
                                com.guildofsmiths.trademesh.ui.jobpipeline.JobPipelineScreen(
                                    job = job,
                                    onBack = { navController.popBackStack() },
                                    onStageAction = { j, newStage ->
                                        jobViewModel.moveJobStage(j.id, newStage)
                                    },
                                    onToggleMaterial = { index ->
                                        jobViewModel.toggleMaterial(jobId, index)
                                    },
                                    onClockIn = {
                                        navController.navigate(NavRoutes.TIME_TRACKING)
                                    },
                                    onShareProposal = { /* TODO: Wire in Task 11 */ },
                                    onShareInvoice = { /* TODO: Wire in Task 12 */ },
                                    onAddNote = { noteText ->
                                        jobViewModel.addWorkLog(jobId, noteText)
                                    },
                                    onAddPhoto = {
                                        this@MainActivity.captureJobPhoto(jobId) { id, uri ->
                                            jobViewModel.addPhoto(id, uri)
                                        }
                                    },
                                    onAddMaterial = { material, orderIt, vendor ->
                                        jobViewModel.addMaterial(jobId, material)
                                        if (orderIt) {
                                            val vendorPart = if (vendor != null) " via $vendor" else ""
                                            jobViewModel.addWorkLog(jobId, "ORDER$vendorPart: ${material.name} (${material.quantity} ${material.unit})")
                                        }
                                    },
                                    onSummarizeToday = {
                                        jobViewModel.triggerManualSummary(jobId)
                                    }
                                )
                            }
                        }

                        // New Job guided flow
                        composable(NavRoutes.NEW_JOB) {
                            val jobViewModel: com.guildofsmiths.trademesh.ui.jobboard.JobBoardViewModel = viewModel(viewModelStoreOwner = this@MainActivity)
                            val allJobsForPicker by jobViewModel.jobs.collectAsState()

                            com.guildofsmiths.trademesh.ui.newjob.NewJobFlow(
                                onBack = { navController.popBackStack() },
                                allJobs = allJobsForPicker,
                                onJobCreated = { newJob ->
                                    jobViewModel.createJob(
                                        title = newJob.clientName.ifBlank { "New Job" },
                                        description = newJob.description,
                                        materials = newJob.materials,
                                        crewSize = newJob.crewSize,
                                        clientName = newJob.clientName,
                                        clientPhone = newJob.clientPhone,
                                        clientAddress = newJob.clientAddress,
                                        hourlyRate = com.guildofsmiths.trademesh.data.UserPreferences.getHourlyRate(),
                                        equipmentList = newJob.equipmentList,
                                        taskDescriptions = newJob.taskDescriptions
                                    )
                                    navController.popBackStack()
                                }
                            )
                        }

                        // Beacon list screen (Messages/Chat Hub)
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
                                onCreateBeaconClick = {
                                    navController.navigate(NavRoutes.CREATE_BEACON)
                                }
                            )
                        }
                        
                        // Chat list screen (new primary messaging entry point)
                        composable(NavRoutes.CHAT_LIST) {
                            ChatListScreen(
                                onChannelClick = { beaconId, channelId ->
                                    navController.navigate(NavRoutes.conversation(beaconId, channelId))
                                },
                                onNewClick = {
                                    navController.navigate(NavRoutes.NEW_CONVERSATION)
                                },
                                onBackClick = {
                                    navController.popBackStack()
                                },
                                onPeersClick = {
                                    navController.navigate(NavRoutes.PEERS)
                                },
                                onSmithAIClick = {
                                    val myUserId = UserPreferences.getUserId()
                                    val aiPeerId = "smith-ai"
                                    val aiPeerName = "SmithAI"
                                    val dm = BeaconRepository.getOrCreateDM("default", myUserId, aiPeerId, aiPeerName)
                                    BoundaryEngine.joinChannel(dm.id)
                                    navController.navigate(NavRoutes.conversationDM("default", dm.id, aiPeerId, aiPeerName))
                                }
                            )
                        }

                        // New conversation screen (contact picker)
                        composable(NavRoutes.NEW_CONVERSATION) {
                            val jobViewModel: com.guildofsmiths.trademesh.ui.jobboard.JobBoardViewModel =
                                androidx.lifecycle.viewmodel.compose.viewModel(viewModelStoreOwner = this@MainActivity)
                            val allJobs by jobViewModel.jobs.collectAsState()
                            NewConversationScreen(
                                allJobs = allJobs,
                                onConversationStart = { beaconId, channelId, peerId, peerName ->
                                    navController.popBackStack()
                                    navController.navigate(NavRoutes.conversationDM(beaconId, channelId, peerId, peerName))
                                },
                                onBackClick = { navController.popBackStack() }
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
                                onProfileClick = {
                                    navController.navigate(NavRoutes.PROFILE)
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
                            val jobViewModel: com.guildofsmiths.trademesh.ui.jobboard.JobBoardViewModel = viewModel(viewModelStoreOwner = this@MainActivity)
                            val activeEntry by ttvm.activeEntry.collectAsState()
                            val isClockedIn by ttvm.isClockedIn.collectAsState()
                            JobBoardScreen(
                                viewModel = jobViewModel,
                                onNavigateBack = {
                                    navController.popBackStack()
                                },
                                currentlyClockedIn = isClockedIn,
                                currentClockedInJobId = activeEntry?.jobId?.takeIf { it.isNotBlank() },
                                currentClockedInJobTitle = activeEntry?.jobTitle,
                                currentClockedInTaskId = activeEntry?.taskId?.takeIf { it.isNotBlank() },
                                onClockIn = { jobId, jobTitle, taskId ->
                                    // Direct clock-in: only fires when JobBoardScreen
                                    // determined the user is OFF the clock.
                                    ttvm.clockIn(
                                        jobId = jobId,
                                        jobTitle = jobTitle,
                                        taskId = taskId,
                                        entryType = com.guildofsmiths.trademesh.ui.timetracking.EntryType.REGULAR
                                    )
                                },
                                onSwitchClock = { jobId, jobTitle, taskId ->
                                    // Atomic clock-out + clock-in. Old job's stage
                                    // is sticky — we never regress it here.
                                    if (ttvm.isClockedIn.value) {
                                        ttvm.clockOut()
                                    }
                                    ttvm.clockIn(
                                        jobId = jobId,
                                        jobTitle = jobTitle,
                                        taskId = taskId,
                                        entryType = com.guildofsmiths.trademesh.ui.timetracking.EntryType.REGULAR
                                    )
                                }
                            )
                        }

                        // C-12: Time Tracking
                        composable(NavRoutes.TIME_TRACKING) {
                            TimeTrackingScreen(
                                onNavigateBack = {
                                    navController.popBackStack()
                                }
                            )
                        }

                        // C-13: Archive
                        composable(NavRoutes.ARCHIVE) {
                            val jobViewModel: com.guildofsmiths.trademesh.ui.jobboard.JobBoardViewModel = viewModel(viewModelStoreOwner = this@MainActivity)
                            ArchiveScreen(
                                viewModel = jobViewModel,
                                onNavigateBack = {
                                    navController.popBackStack()
                                },
                                onJobClick = { jobId ->
                                    navController.navigate(NavRoutes.jobPipeline(jobId))
                                }
                            )
                        }

                        // Supply screen
                        composable(NavRoutes.SUPPLY) {
                            val supplyJobViewModel: com.guildofsmiths.trademesh.ui.jobboard.JobBoardViewModel = viewModel(viewModelStoreOwner = this@MainActivity)
                            val allJobs by supplyJobViewModel.jobs.collectAsState()
                            com.guildofsmiths.trademesh.ui.supply.SupplyScreen(
                                allJobs = allJobs,
                                onToggleMaterial = { jobId, materialIndex ->
                                    supplyJobViewModel.toggleMaterial(jobId, materialIndex)
                                },
                                onAddMaterial = { jobId, material ->
                                    supplyJobViewModel.addMaterial(jobId, material)
                                },
                                onUpdateMaterial = { jobId, materialIndex, material ->
                                    supplyJobViewModel.updateMaterial(jobId, materialIndex, material)
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        // Report screen
                        composable(NavRoutes.REPORT) {
                            val reportJobViewModel: com.guildofsmiths.trademesh.ui.jobboard.JobBoardViewModel = viewModel(viewModelStoreOwner = this@MainActivity)
                            val allJobs by reportJobViewModel.jobs.collectAsState()
                            com.guildofsmiths.trademesh.ui.report.ReportScreen(
                                allJobs = allJobs,
                                onJobClick = { jobId ->
                                    navController.navigate(NavRoutes.jobPipeline(jobId))
                                },
                                onOpenJobExpenses = { jobId ->
                                    navController.navigate(NavRoutes.jobExpenses(jobId))
                                },
                                onOpenExpenses = {
                                    navController.navigate(NavRoutes.EXPENSES)
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        // Top-level Expenses page (By Job / Ledger / Timeline / BOL Table)
                        composable(NavRoutes.EXPENSES) {
                            val vm: com.guildofsmiths.trademesh.ui.jobboard.JobBoardViewModel = viewModel(viewModelStoreOwner = this@MainActivity)
                            com.guildofsmiths.trademesh.ui.expenses.ExpensesScreen(
                                viewModel = vm,
                                onBack = { navController.popBackStack() },
                                onOpenJobExpenses = { jobId ->
                                    navController.navigate(NavRoutes.jobExpenses(jobId))
                                },
                                onOpenCategoryManager = {
                                    navController.navigate(NavRoutes.EXPENSE_CATEGORIES)
                                },
                                onOpenCsvImport = {
                                    navController.navigate(NavRoutes.EXPENSE_CSV_IMPORT)
                                },
                                onOpenLegalSettings = {
                                    navController.navigate(NavRoutes.BOL_LEGAL_SETTINGS)
                                }
                            )
                        }

                        // Per-job BOL (Bill of Work & Expenses) detail
                        composable(NavRoutes.JOB_EXPENSES) { backStackEntry ->
                            val jobId = backStackEntry.arguments?.getString("jobId") ?: ""
                            val vm: com.guildofsmiths.trademesh.ui.jobboard.JobBoardViewModel = viewModel(viewModelStoreOwner = this@MainActivity)
                            com.guildofsmiths.trademesh.ui.expenses.JobExpenseDetailScreen(
                                jobId = jobId,
                                viewModel = vm,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        // Category manager
                        composable(NavRoutes.EXPENSE_CATEGORIES) {
                            com.guildofsmiths.trademesh.ui.expenses.CategoryManagerScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }

                        // CSV importer
                        composable(NavRoutes.EXPENSE_CSV_IMPORT) {
                            val vm: com.guildofsmiths.trademesh.ui.jobboard.JobBoardViewModel = viewModel(viewModelStoreOwner = this@MainActivity)
                            com.guildofsmiths.trademesh.ui.expenses.ExpenseCsvImportScreen(
                                viewModel = vm,
                                onBack = { navController.popBackStack() }
                            )
                        }

                        // BOL legal-terms settings
                        composable(NavRoutes.BOL_LEGAL_SETTINGS) {
                            com.guildofsmiths.trademesh.ui.expenses.BolLegalSettingsScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }

                        // Lost & Found — GPS breadcrumb for a crew member
                        composable(NavRoutes.LOST_AND_FOUND) { backStackEntry ->
                            val userId = backStackEntry.arguments?.getString("userId") ?: return@composable
                            com.guildofsmiths.trademesh.ui.map.LostAndFoundScreen(
                                targetUserId = android.net.Uri.decode(userId),
                                onBack = { navController.popBackStack() }
                            )
                        }

                        // Plan / Proposals screen
                        composable(NavRoutes.PLAN) {
                            com.guildofsmiths.trademesh.ui.plan.PlanScreen(
                                onNavigateToJob = {
                                    navController.navigate(NavRoutes.JOB_BOARD)
                                }
                            )
                        }

                        // Crew Map
                        composable(NavRoutes.MAP) {
                            val jobViewModel: com.guildofsmiths.trademesh.ui.jobboard.JobBoardViewModel = viewModel(viewModelStoreOwner = this@MainActivity)
                            com.guildofsmiths.trademesh.ui.map.MapScreen(
                                jobViewModel = jobViewModel,
                                onBack = { navController.popBackStack() },
                                onJobClick = { jobId ->
                                    navController.navigate(NavRoutes.jobPipeline(jobId))
                                },
                                onCallPhone = { phone ->
                                    val intent = android.content.Intent(
                                        android.content.Intent.ACTION_DIAL,
                                        android.net.Uri.parse("tel:$phone")
                                    )
                                    startActivity(intent)
                                },
                                onMessageCrew = { crewMember ->
                                    val myUserId = UserPreferences.getUserId()
                                    val dm = com.guildofsmiths.trademesh.data.BeaconRepository.getOrCreateDM(
                                        "default", myUserId, crewMember.userId, crewMember.name
                                    )
                                    navController.navigate(NavRoutes.conversation("default", dm.id))
                                }
                            )
                        }

                        // Dispatch screen — crew-to-task assignments
                        composable(NavRoutes.DISPATCH) {
                            val jobViewModel: com.guildofsmiths.trademesh.ui.jobboard.JobBoardViewModel = viewModel(viewModelStoreOwner = this@MainActivity)
                            com.guildofsmiths.trademesh.ui.dispatch.DispatchScreen(
                                viewModel = jobViewModel,
                                onBack = { navController.popBackStack() },
                                onJobClick = { jobId ->
                                    navController.navigate(NavRoutes.jobPipeline(jobId))
                                },
                                onCallPhone = { phone ->
                                    val intent = android.content.Intent(
                                        android.content.Intent.ACTION_DIAL,
                                        android.net.Uri.parse("tel:$phone")
                                    )
                                    startActivity(intent)
                                },
                                onMessageCrew = { crewMember ->
                                    val myUserId = UserPreferences.getUserId()
                                    val dm = com.guildofsmiths.trademesh.data.BeaconRepository.getOrCreateDM(
                                        "default", myUserId, crewMember.userId, crewMember.name
                                    )
                                    navController.navigate(NavRoutes.conversation("default", dm.id))
                                }
                            )
                        }

                        // Clients list
                        composable(NavRoutes.CLIENTS) {
                            val clientJobViewModel: com.guildofsmiths.trademesh.ui.jobboard.JobBoardViewModel = viewModel(viewModelStoreOwner = this@MainActivity)
                            val allJobs by clientJobViewModel.jobs.collectAsState()
                            ClientsScreen(
                                allJobs = allJobs,
                                onClientClick = { clientName ->
                                    navController.navigate(NavRoutes.clientDetail(clientName))
                                },
                                onBack = { navController.popBackStack() }
                            )
                        }

                        // Client detail
                        composable(
                            route = NavRoutes.CLIENT_DETAIL,
                            arguments = listOf(navArgument("clientName") { type = NavType.StringType })
                        ) { backStackEntry ->
                            val clientName = android.net.Uri.decode(
                                backStackEntry.arguments?.getString("clientName") ?: ""
                            )
                            val detailJobViewModel: com.guildofsmiths.trademesh.ui.jobboard.JobBoardViewModel = viewModel(viewModelStoreOwner = this@MainActivity)
                            val allJobs by detailJobViewModel.jobs.collectAsState()
                            ClientDetailScreen(
                                clientName = clientName,
                                allJobs = allJobs,
                                onJobClick = { jobId ->
                                    navController.navigate(NavRoutes.jobPipeline(jobId))
                                },
                                onBack = { navController.popBackStack() }
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
                    }

                    // Bottom nav bar — visible on main screens
                    if (currentRoute in navBarRoutes) {
                        com.guildofsmiths.trademesh.ui.BottomNavBar(
                            currentRoute = currentRoute ?: "",
                            onHome = {
                                if (currentRoute != NavRoutes.DASHBOARD) {
                                    navController.navigate(NavRoutes.DASHBOARD) {
                                        popUpTo(NavRoutes.DASHBOARD) { inclusive = true }
                                    }
                                }
                            },
                            onJobs = {
                                if (currentRoute != NavRoutes.JOB_BOARD) {
                                    navController.navigate(NavRoutes.JOB_BOARD) {
                                        popUpTo(NavRoutes.DASHBOARD)
                                    }
                                }
                            },
                            onComm = {
                                if (currentRoute != NavRoutes.CHAT_LIST) {
                                    navController.navigate(NavRoutes.CHAT_LIST) {
                                        popUpTo(NavRoutes.DASHBOARD)
                                    }
                                }
                            },
                            onPlan = {
                                // [Plan] for solo → Proposals, [Map] for foreman/GC
                                val target = if (com.guildofsmiths.trademesh.data.RoleContext.isForeman() || com.guildofsmiths.trademesh.data.RoleContext.isGC()) {
                                    NavRoutes.MAP
                                } else {
                                    NavRoutes.PLAN
                                }
                                if (currentRoute != target) {
                                    navController.navigate(target) {
                                        popUpTo(NavRoutes.DASHBOARD)
                                    }
                                }
                            },
                            onClockIn = {
                                navController.navigate(NavRoutes.TIME_TRACKING) {
                                    popUpTo(NavRoutes.DASHBOARD)
                                }
                            },
                            onDispatch = {
                                if (currentRoute != NavRoutes.DISPATCH) {
                                    navController.navigate(NavRoutes.DISPATCH) {
                                        popUpTo(NavRoutes.DASHBOARD)
                                    }
                                }
                            }
                        )
                    }
                    } // Column
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
    @Suppress("InlinedApi")
    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ requires BLUETOOTH_SCAN, BLUETOOTH_ADVERTISE, BLUETOOTH_CONNECT
            val requiredPermissions = arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.POST_NOTIFICATIONS,
                // GPS for clock-in validation + lost & found. Coarse is enough to run
                // the foreground service; user can elevate to fine from system Settings.
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
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
        if (!com.guildofsmiths.trademesh.data.UserPreferences.shouldRunMesh()) {
            Log.i(TAG, "Skipping MeshService start — work mode is solo without override")
            return
        }
        Log.i(TAG, "Starting MeshService")
        val serviceIntent = Intent(this, MeshService::class.java)
        startForegroundService(serviceIntent)
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
        val pendingJobUri = pendingJobPhotoUri
        if (pendingJobUri != null) {
            cameraLauncher.launch(pendingJobUri)
            return
        }
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
