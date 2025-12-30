package com.guildofsmiths.trademesh.ai

import android.content.Context
import android.util.Log
import com.guildofsmiths.trademesh.data.Message
import com.guildofsmiths.trademesh.data.MessageRepository
import com.guildofsmiths.trademesh.service.ChatManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * OfflineQueueManager - Handles queuing and sync of AI-generated content
 *
 * Queues AI responses and actions locally when offline, then syncs to chat timeline
 * when connectivity returns. Preserves order and attribution as "Assistant".
 *
 * FITS IN: New service class in ai package, integrates with AIRouter and ChatManager
 */
object OfflineQueueManager {

    private const val TAG = "OfflineQueueManager"

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // ════════════════════════════════════════════════════════════════════
    // QUEUE MANAGEMENT
    // ════════════════════════════════════════════════════════════════════

    // Queue for pending AI responses (offline-generated)
    private val responseQueue = ConcurrentLinkedQueue<QueuedAIResponse>()

    // Queue for pending AI actions (confirmations, updates)
    private val actionQueue = ConcurrentLinkedQueue<QueuedAIAction>()

    // Sync state
    private val _syncState = MutableStateFlow(SyncState.IDLE)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private var context: Context? = null
    private var isInitialized = false

    // ════════════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ════════════════════════════════════════════════════════════════════

    /**
     * Initialize the queue manager. Call once at app startup.
     */
    fun initialize(appContext: Context) {
        if (isInitialized) return

        context = appContext.applicationContext
        isInitialized = true

        // Start monitoring connectivity for auto-sync
        monitorConnectivity()

        Log.i(TAG, "OfflineQueueManager initialized")
    }

    /**
     * Cleanup resources.
     */
    fun shutdown() {
        scope.cancel()
        context = null
        isInitialized = false
    }

    // ════════════════════════════════════════════════════════════════════
    // PUBLIC API - QUEUEING
    // ════════════════════════════════════════════════════════════════════

    /**
     * Queue an AI response for later sync to chat.
     * Called when AI generates response but we're offline or want to batch.
     */
    fun queueResponse(
        response: AIResponse.Success,
        channelId: String,
        jobId: String? = null,
        contextId: String? = null
    ) {
        val queued = QueuedAIResponse(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            response = response,
            channelId = channelId,
            jobId = jobId,
            contextId = contextId,
            retryCount = 0
        )

        responseQueue.add(queued)
        Log.d(TAG, "Queued AI response: ${queued.id}")

        // Try immediate sync if online
        scope.launch {
            trySyncQueuedItems()
        }
    }

    /**
     * Queue an AI action (confirmation, update) for later sync.
     */
    fun queueAction(
        action: AIAction,
        channelId: String,
        jobId: String? = null,
        contextId: String? = null
    ) {
        val queued = QueuedAIAction(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            action = action,
            channelId = channelId,
            jobId = jobId,
            contextId = contextId,
            retryCount = 0
        )

        actionQueue.add(queued)
        Log.d(TAG, "Queued AI action: ${queued.id}")

        // Try immediate sync if online
        scope.launch {
            trySyncQueuedItems()
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // PUBLIC API - SYNC
    // ════════════════════════════════════════════════════════════════════

    /**
     * Manually trigger sync of queued items.
     * Returns true if sync was attempted.
     */
    suspend fun syncNow(): Boolean {
        return trySyncQueuedItems()
    }

    /**
     * Get count of pending items.
     */
    fun getPendingCount(): Int = responseQueue.size + actionQueue.size

    /**
     * Clear all queued items (for testing or reset).
     */
    fun clearQueue() {
        responseQueue.clear()
        actionQueue.clear()
        Log.d(TAG, "Cleared all queued items")
    }

    // ════════════════════════════════════════════════════════════════════
    // PRIVATE - CONNECTIVITY MONITORING
    // ════════════════════════════════════════════════════════════════════

    private fun monitorConnectivity() {
        scope.launch {
            // Monitor connectivity changes
            // In real implementation, you'd use NetworkCallback or BroadcastReceiver
            // For now, we'll rely on manual sync triggers

            while (isActive) {
                delay(30000) // Check every 30 seconds

                // Try to sync any pending items
                trySyncQueuedItems()
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // PRIVATE - SYNC LOGIC
    // ════════════════════════════════════════════════════════════════════

    private suspend fun trySyncQueuedItems(): Boolean {
        if (_syncState.value == SyncState.SYNCING) {
            Log.d(TAG, "Sync already in progress")
            return false
        }

        if (responseQueue.isEmpty() && actionQueue.isEmpty()) {
            return true // Nothing to sync
        }

        // Check if we can sync (basic connectivity check)
        val canSync = context?.let { isConnectedToInternet(it) } ?: false
        if (!canSync) {
            Log.d(TAG, "Cannot sync: no internet connection")
            return false
        }

        _syncState.value = SyncState.SYNCING

        try {
            Log.d(TAG, "Starting sync of ${responseQueue.size} responses and ${actionQueue.size} actions")

            // Sync responses first (preserve chronological order)
            val responsesToSync = responseQueue.sortedBy { it.timestamp }
            for (queued in responsesToSync) {
                if (!syncResponse(queued)) {
                    Log.w(TAG, "Failed to sync response ${queued.id}")
                    break // Stop on first failure to preserve order
                }
            }

            // Then sync actions
            val actionsToSync = actionQueue.sortedBy { it.timestamp }
            for (queued in actionsToSync) {
                if (!syncAction(queued)) {
                    Log.w(TAG, "Failed to sync action ${queued.id}")
                    break
                }
            }

            _syncState.value = SyncState.IDLE
            Log.d(TAG, "Sync completed. Remaining: ${getPendingCount()}")

            return true

        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            _syncState.value = SyncState.ERROR
            return false
        }
    }

    private suspend fun syncResponse(queued: QueuedAIResponse): Boolean {
        return try {
            // Create message for chat timeline
            val message = Message(
                id = "ai-${queued.id}",
                channelId = queued.channelId,
                senderId = "assistant", // Attribution as Assistant
                senderName = "Assistant",
                content = formatResponseForChat(queued.response),
                timestamp = queued.timestamp,
                isMeshOrigin = false // IP chat origin for synced content
            )

            // Add to message repository (will sync via existing mechanisms)
            MessageRepository.addMessage(message)

            // Also send via ChatManager if online
            ChatManager.sendMessage(message)

            // Remove from queue
            responseQueue.remove(queued)
            Log.d(TAG, "Synced response: ${queued.id}")

            true

        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync response ${queued.id}", e)
            // Increment retry count
            queued.retryCount++
            if (queued.retryCount >= 3) {
                responseQueue.remove(queued) // Give up after 3 retries
                Log.w(TAG, "Gave up on response ${queued.id} after ${queued.retryCount} retries")
            }
            false
        }
    }

    private suspend fun syncAction(queued: QueuedAIAction): Boolean {
        return try {
            // Execute the queued action
            when (queued.action) {
                is AIAction.TimeConfirmation -> {
                    // Could trigger time tracking updates
                    Log.d(TAG, "Executing time confirmation: ${queued.action.message}")
                }
                is AIAction.JobUpdate -> {
                    // Could trigger job board updates
                    Log.d(TAG, "Executing job update: ${queued.action.message}")
                }
                is AIAction.MaterialRequest -> {
                    // Could trigger material tracking updates
                    Log.d(TAG, "Executing material request: ${queued.action.message}")
                }
            }

            // Create confirmation message
            val message = Message(
                id = "ai-action-${queued.id}",
                channelId = queued.channelId,
                senderId = "assistant",
                senderName = "Assistant",
                content = "✓ ${queued.action.message}",
                timestamp = queued.timestamp,
                isMeshOrigin = false
            )

            MessageRepository.addMessage(message)
            ChatManager.sendMessage(message)

            // Remove from queue
            actionQueue.remove(queued)
            Log.d(TAG, "Synced action: ${queued.id}")

            true

        } catch (e: Exception) {
            Log.w(TAG, "Failed to sync action ${queued.id}", e)
            queued.retryCount++
            if (queued.retryCount >= 3) {
                actionQueue.remove(queued)
                Log.w(TAG, "Gave up on action ${queued.id} after ${queued.retryCount} retries")
            }
            false
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // PRIVATE - UTILITIES
    // ════════════════════════════════════════════════════════════════════

    private fun formatResponseForChat(response: AIResponse.Success): String {
        val prefix = when (response.intent) {
            AIIntent.CONFIRM -> "✓ "
            AIIntent.CHECKLIST -> "📋 "
            AIIntent.TRANSLATE -> "🌐 "
            AIIntent.TASK_HELP -> "🔧 "
            else -> ""
        }
        return "$prefix${response.text}"
    }

    private fun isConnectedToInternet(context: Context): Boolean {
        // Reuse connectivity check from AIRouter
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
        val network = connectivityManager?.activeNetwork
        val capabilities = connectivityManager?.getNetworkCapabilities(network)
        return capabilities?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }
}

// ════════════════════════════════════════════════════════════════════
// DATA CLASSES
// ════════════════════════════════════════════════════════════════════

/**
 * Queued AI response pending sync
 */
data class QueuedAIResponse(
    val id: String,
    val timestamp: Long,
    val response: AIResponse.Success,
    val channelId: String,
    val jobId: String?,
    val contextId: String?,
    var retryCount: Int = 0
)

/**
 * Queued AI action pending sync
 */
data class QueuedAIAction(
    val id: String,
    val timestamp: Long,
    val action: AIAction,
    val channelId: String,
    val jobId: String?,
    val contextId: String?,
    var retryCount: Int = 0
)

/**
 * Types of AI actions that can be queued
 */
sealed class AIAction {
    abstract val message: String

    data class TimeConfirmation(override val message: String) : AIAction()
    data class JobUpdate(override val message: String) : AIAction()
    data class MaterialRequest(override val message: String) : AIAction()
}

/**
 * Sync state
 */
enum class SyncState {
    IDLE,    // Not syncing
    SYNCING, // Currently syncing
    ERROR    // Last sync failed
}
