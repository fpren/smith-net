package com.guildofsmiths.trademesh.ai

import android.content.Context
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.guildofsmiths.trademesh.data.Message
import com.guildofsmiths.trademesh.data.MessageRepository
import com.guildofsmiths.trademesh.engine.BoundaryEngine
import com.guildofsmiths.trademesh.ui.jobboard.JobBoardViewModel
import com.guildofsmiths.trademesh.ui.timetracking.TimeTrackingViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

/**
 * AmbientEventHub - Central event observer for ambient AI assistance
 *
 * Observes all app events via Flows (no polling):
 * - Incoming mesh/IP messages
 * - Time entry creations/edits
 * - Job/checklist views/edits
 * - Screen navigation changes
 *
 * Publishes events to AmbientRuleEngine for processing.
 *
 * FITS IN: New service class in ai package
 */
object AmbientEventHub {

    private const val TAG = "AmbientEventHub"

    // ════════════════════════════════════════════════════════════════════
    // EVENT FLOWS (event-driven observation)
    // ════════════════════════════════════════════════════════════════════

    // Message events (mesh and IP chat)
    val messageReceivedFlow: Flow<MessageEvent> = MessageRepository.allMessages
        .map { messages -> messages.lastOrNull() } // Get latest message
        .filterNotNull()
        .distinctUntilChanged { old, new -> old.id == new.id } // Only new messages
        .map { message ->
            MessageEvent(
                message = message,
                source = when {
                    message.isMeshOrigin -> MessageSource.MESH
                    else -> MessageSource.IP_CHAT
                },
                context = determineMessageContext(message)
            )
        }

    // Time entry events
    val timeEntryCreatedFlow: Flow<TimeEntryEvent> = flow {
        // This would connect to TimeTrackingViewModel's state
        // For now, emit mock events - replace with real integration
        // emit(TimeEntryEvent(clockIn = true, jobId = "job123"))
    }

    // Job/checklist events
    val jobActivityFlow: Flow<JobEvent> = flow {
        // This would connect to JobBoardViewModel's state changes
        // For now, emit mock events - replace with real integration
        // emit(JobEvent(jobId = "job123", action = JobAction.CHECKLIST_UPDATED))
    }

    // Screen navigation events (for context awareness)
    val screenNavigationFlow: Flow<ScreenEvent> = flow {
        // This would be fed by MainActivity navigation changes
        // emit(ScreenEvent(screen = Screen.JOB_BOARD))
    }

    // Connectivity events (for hybrid mode decisions)
    val connectivityFlow: Flow<ConnectivityEvent> = flow {
        // Monitor network state changes
        // emit(ConnectivityEvent(connected = true, type = "wifi"))
    }

    // Battery events (for gating decisions)
    val batteryFlow: Flow<BatteryEvent> = flow {
        // Monitor battery level changes
        // emit(BatteryEvent(level = 75, charging = true))
    }

    // Location/geofence events (for job site awareness)
    val locationFlow: Flow<LocationEvent> = flow {
        // Monitor geofence entries/exits
        // emit(LocationEvent(geofenceId = "job123", entered = true))
    }

    // App lifecycle events (for ambient AI state management)
    val lifecycleFlow: Flow<LifecycleEvent> = flow {
        // Monitor app foreground/background
        // emit(LifecycleEvent(state = LifecycleState.FOREGROUND))
    }

    // ════════════════════════════════════════════════════════════════════
    // EVENT DATA CLASSES
    // ════════════════════════════════════════════════════════════════════

    data class MessageEvent(
        val message: Message,
        val source: MessageSource,
        val context: MessageContext
    )

    data class TimeEntryEvent(
        val clockIn: Boolean,
        val clockOut: Boolean = false,
        val breakStart: Boolean = false,
        val jobId: String? = null,
        val entryType: String? = null
    )

    data class JobEvent(
        val jobId: String,
        val action: JobAction,
        val checklistItem: String? = null,
        val materialUpdated: String? = null
    )

    data class ScreenEvent(
        val screen: Screen,
        val parameters: Map<String, String> = emptyMap()
    )

    data class ConnectivityEvent(
        val connected: Boolean,
        val type: String, // "wifi", "cellular", "none"
        val metered: Boolean = false
    )

    data class BatteryEvent(
        val level: Int, // 0-100
        val charging: Boolean,
        val temperature: Int // Celsius
    )

    data class LocationEvent(
        val geofenceId: String,
        val entered: Boolean,
        val latitude: Double? = null,
        val longitude: Double? = null,
        val jobId: String? = null
    )

    data class LifecycleEvent(
        val state: LifecycleState
    )

    // ════════════════════════════════════════════════════════════════════
    // ENUMS
    // ════════════════════════════════════════════════════════════════════

    enum class MessageSource { MESH, IP_CHAT }
    enum class MessageContext { CHAT, JOB_BOARD, TIME_TRACKING, UNKNOWN }
    enum class JobAction { CREATED, CHECKLIST_UPDATED, MATERIAL_ADDED, STATUS_CHANGED, VIEWED }
    enum class Screen { CHAT, JOB_BOARD, TIME_TRACKING, ARCHIVE, SETTINGS }
    enum class LifecycleState { FOREGROUND, BACKGROUND, DESTROYED }

    // ════════════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ════════════════════════════════════════════════════════════════════

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun initialize(context: Context) {
        // Start observing events when AI is enabled
        if (AIRouter.isEnabled()) {
            startEventObservation()
        }
    }

    /**
     * Start observing all events and routing to AmbientRuleEngine
     */
    private fun startEventObservation() {
        // Observe message events separately (primary flow)
        scope.launch {
            messageReceivedFlow.collect { messageEvent ->
                AmbientRuleEngine.processMessageEvent(messageEvent)
            }
        }

        // Observe time entry events
        scope.launch {
            timeEntryCreatedFlow.collect { timeEvent ->
                AmbientRuleEngine.processTimeEvent(timeEvent)
            }
        }

        // Observe job activity events
        scope.launch {
            jobActivityFlow.collect { jobEvent ->
                AmbientRuleEngine.processJobEvent(jobEvent)
            }
        }

        // Observe screen navigation events
        scope.launch {
            screenNavigationFlow.collect { screenEvent ->
                AmbientRuleEngine.processScreenEvent(screenEvent)
            }
        }

        // Observe connectivity events
        scope.launch {
            connectivityFlow.collect { connectivityEvent ->
                AmbientRuleEngine.processConnectivityEvent(connectivityEvent)
            }
        }

        // Observe battery events
        scope.launch {
            batteryFlow.collect { batteryEvent ->
                AmbientRuleEngine.processBatteryEvent(batteryEvent)
            }
        }

        // Observe location events
        scope.launch {
            locationFlow.collect { locationEvent ->
                AmbientRuleEngine.processLocationEvent(locationEvent)
            }
        }

        // Observe lifecycle events
        scope.launch {
            lifecycleFlow.collect { lifecycleEvent ->
                AmbientRuleEngine.processLifecycleEvent(lifecycleEvent)
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // HELPER FUNCTIONS
    // ════════════════════════════════════════════════════════════════════

    private fun determineMessageContext(message: Message): MessageContext {
        return when {
            message.channelId.startsWith("dm_") -> MessageContext.CHAT
            message.content.contains("#job") || message.content.contains("checklist") -> MessageContext.JOB_BOARD
            message.content.contains("clock") || message.content.contains("time") -> MessageContext.TIME_TRACKING
            else -> MessageContext.CHAT
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // LIFECYCLE INTEGRATION
    // ════════════════════════════════════════════════════════════════════

    /**
     * Lifecycle-aware collection for Activities/Fragments
     * FITS IN: Call from MainActivity.onCreate()
     */
    fun observeInLifecycle(owner: LifecycleOwner) {
        owner.lifecycleScope.launch {
            owner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Additional lifecycle-aware observations can go here
                messageReceivedFlow.collect { event ->
                    // Handle message events in lifecycle-aware context
                    AmbientRuleEngine.processMessageEvent(event)
                }
            }
        }
    }
}