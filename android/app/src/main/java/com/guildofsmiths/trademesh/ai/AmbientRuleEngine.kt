package com.guildofsmiths.trademesh.ai

import android.util.Log
import com.guildofsmiths.trademesh.data.Message
import com.guildofsmiths.trademesh.data.MessageRepository
import com.guildofsmiths.trademesh.service.ChatManager
import com.guildofsmiths.trademesh.ai.OfflineQueueManager
import com.guildofsmiths.trademesh.ai.AIAction
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

/**
 * AmbientRuleEngine - Standard Mode: Local, lightweight, always-on ambient AI
 *
 * Processes ambient observations with simple pattern matching and rule-based decisions.
 * Provides zero-battery, near-zero latency responses for common scenarios:
 * - Clocking in/out confirmations
 * - Material request acknowledgments
 * - Non-English text translation (cached)
 * - Checklist surface for trade keywords
 * - Job status updates
 *
 * FITS IN: New service class in ai package, called by AmbientEventHub
 */
object AmbientRuleEngine {

    private const val TAG = "AmbientRuleEngine"

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // ════════════════════════════════════════════════════════════════════
    // PATTERN MATCHERS (lightweight, deterministic)
    // ════════════════════════════════════════════════════════════════════

    private val clockPatterns = listOf(
        Regex("clock in", RegexOption.IGNORE_CASE),
        Regex("clock out", RegexOption.IGNORE_CASE),
        Regex("start work", RegexOption.IGNORE_CASE),
        Regex("end work", RegexOption.IGNORE_CASE),
        Regex("lunch break", RegexOption.IGNORE_CASE),
        Regex("back from break", RegexOption.IGNORE_CASE)
    )

    private val materialPatterns = listOf(
        Regex("need (.+)", RegexOption.IGNORE_CASE),
        Regex("need more (.+)", RegexOption.IGNORE_CASE),
        Regex("out of (.+)", RegexOption.IGNORE_CASE),
        Regex("missing (.+)", RegexOption.IGNORE_CASE),
        Regex("send (.+)", RegexOption.IGNORE_CASE)
    )

    private val checklistPatterns = listOf(
        Regex("checklist", RegexOption.IGNORE_CASE),
        Regex("check list", RegexOption.IGNORE_CASE),
        Regex("what.*do.*next", RegexOption.IGNORE_CASE),
        Regex("next steps?", RegexOption.IGNORE_CASE),
        Regex("how.*do.*this", RegexOption.IGNORE_CASE)
    )

    private val tradeKeywords = mapOf(
        "electrical" to "electrical",
        "plumbing" to "plumbing",
        "hvac" to "hvac",
        "carpentry" to "carpentry",
        "painting" to "painting",
        "roofing" to "roofing",
        "concrete" to "concrete",
        "drywall" to "drywall"
    )

    // ════════════════════════════════════════════════════════════════════
    // EVENT PROCESSING
    // ════════════════════════════════════════════════════════════════════

    /**
     * Process message events from AmbientEventHub
     */
    fun processMessageEvent(event: AmbientEventHub.MessageEvent) {
        scope.launch {
            val response = analyzeMessage(event.message, event.context)
            if (response != null) {
                deliverAmbientResponse(response, event)
            }
        }
    }

    /**
     * Process time entry events
     */
    fun processTimeEvent(event: AmbientEventHub.TimeEntryEvent) {
        scope.launch {
            val response = generateTimeConfirmation(event)
            if (response != null) {
                deliverAmbientResponse(response, event)
            }
        }
    }

    /**
     * Process job activity events
     */
    fun processJobEvent(event: AmbientEventHub.JobEvent) {
        scope.launch {
            val response = generateJobUpdate(event)
            if (response != null) {
                deliverAmbientResponse(response, event)
            }
        }
    }

    /**
     * Process screen navigation events
     */
    fun processScreenEvent(event: AmbientEventHub.ScreenEvent) {
        // Context awareness - could trigger proactive suggestions
        Log.d(TAG, "Screen navigation: ${event.screen}")
    }

    /**
     * Process connectivity events (for hybrid mode decisions)
     */
    fun processConnectivityEvent(event: AmbientEventHub.ConnectivityEvent) {
        // Could trigger queued sync when connectivity returns
        if (event.connected) {
            Log.d(TAG, "Connectivity restored: ${event.type}")
            // Trigger sync of queued responses
            scope.launch {
                OfflineQueueManager.syncNow()
            }
        }
    }

    /**
     * Process battery events (for gating awareness)
     */
    fun processBatteryEvent(event: AmbientEventHub.BatteryEvent) {
        // Could provide proactive battery warnings
        if (event.level <= 15 && !event.charging) {
            Log.d(TAG, "Low battery warning: ${event.level}%")
            // Could queue a helpful message about AI being disabled soon
        }
    }

    /**
     * Process location/geofence events (for job site awareness)
     */
    fun processLocationEvent(event: AmbientEventHub.LocationEvent) {
        // Could provide location-based assistance
        val action = if (event.entered) "entered" else "exited"
        Log.d(TAG, "Geofence $action: ${event.geofenceId}")

        // Could trigger job-specific reminders or checklists
        if (event.entered && event.jobId != null) {
            // Generate ambient job site reminder
            val response = AmbientResponse(
                text = "Welcome to job site. Safety first! Check your job checklist.",
                type = AmbientResponseType.CHECKLIST,
                confidence = 0.9f,
                contextId = "location-${event.geofenceId}-${System.currentTimeMillis()}"
            )

            scope.launch {
                // Deliver to job-specific channel
                deliverAmbientResponse(response, AmbientEventHub.JobEvent(
                    jobId = event.jobId!!,
                    action = AmbientEventHub.JobAction.VIEWED
                ))
            }
        }
    }

    /**
     * Process app lifecycle events
     */
    fun processLifecycleEvent(event: AmbientEventHub.LifecycleEvent) {
        // Could pause/resume ambient processing based on app state
        Log.d(TAG, "Lifecycle: ${event.state}")
    }

    // ════════════════════════════════════════════════════════════════════
    // PATTERN ANALYSIS (fast, lightweight)
    // ════════════════════════════════════════════════════════════════════

    private fun analyzeMessage(message: Message, context: AmbientEventHub.MessageContext): AmbientResponse? {
        val content = message.content.lowercase().trim()

        // Skip if already processed or contains AI mentions
        if (content.contains("@ai") || content.contains("assistant")) {
            return null
        }

        // Clock in/out detection
        if (clockPatterns.any { it.containsMatchIn(content) }) {
            return generateClockResponse(content)
        }

        // Material requests
        materialPatterns.forEach { pattern ->
            pattern.find(content)?.let { match ->
                val material = match.groupValues.getOrNull(1)?.trim()
                if (!material.isNullOrBlank()) {
                    return generateMaterialResponse(material)
                }
            }
        }

        // Checklist requests
        if (checklistPatterns.any { it.containsMatchIn(content) }) {
            return generateChecklistResponse(content, context)
        }

        // Trade-specific patterns
        tradeKeywords.forEach { (keyword, trade) ->
            if (content.contains(keyword)) {
                return generateTradeChecklistResponse(trade)
            }
        }

        // Non-English text detection (simple heuristics)
        if (isNonEnglishText(content)) {
            return generateTranslationResponse(content)
        }

        return null // No ambient assistance needed
    }

    // ════════════════════════════════════════════════════════════════════
    // RESPONSE GENERATION (deterministic, fast)
    // ════════════════════════════════════════════════════════════════════

    private fun generateClockResponse(content: String): AmbientResponse {
        val isClockIn = content.contains("in") || content.contains("start")
        val isBreak = content.contains("break") || content.contains("lunch")

        val responseText = when {
            isBreak -> "✓ Break started. Remember to clock back in."
            isClockIn -> "✓ Clocked in. Time started."
            else -> "✓ Clocked out. Time recorded."
        }

        return AmbientResponse(
            text = responseText,
            type = AmbientResponseType.CONFIRMATION,
            confidence = 0.9f,
            contextId = "clock-${System.currentTimeMillis()}"
        )
    }

    private fun generateMaterialResponse(material: String): AmbientResponse {
        return AmbientResponse(
            text = "✓ Material request noted: $material. Check job materials list.",
            type = AmbientResponseType.MATERIAL_REQUEST,
            confidence = 0.8f,
            contextId = "material-${System.currentTimeMillis()}"
        )
    }

    private fun generateChecklistResponse(content: String, context: AmbientEventHub.MessageContext): AmbientResponse {
        val checklist = when (context) {
            AmbientEventHub.MessageContext.JOB_BOARD -> "Check your current task in Job Board workflow."
            AmbientEventHub.MessageContext.TIME_TRACKING -> "1. Clock in/out as needed\n2. Check your hours\n3. Submit timesheet"
            else -> "1. Review current tasks\n2. Check materials\n3. Complete in order\n4. Mark done"
        }

        return AmbientResponse(
            text = "Current checklist:\n$checklist",
            type = AmbientResponseType.CHECKLIST,
            confidence = 0.7f,
            contextId = "checklist-${System.currentTimeMillis()}"
        )
    }

    private fun generateTradeChecklistResponse(trade: String): AmbientResponse {
        val checklist = RuleBasedFallback.getTradeChecklist(trade)
        return AmbientResponse(
            text = "$trade checklist:\n$checklist",
            type = AmbientResponseType.CHECKLIST,
            confidence = 0.8f,
            contextId = "trade-checklist-$trade-${System.currentTimeMillis()}"
        )
    }

    private fun generateTranslationResponse(content: String): AmbientResponse {
        // Use cached translations from RuleBasedFallback
        val translation = RuleBasedFallback.getSimpleTranslation(content, detectLanguage(content))
        return if (translation != null) {
            AmbientResponse(
                text = "Translation: $translation",
                type = AmbientResponseType.TRANSLATION,
                confidence = 0.6f,
                contextId = "translation-${System.currentTimeMillis()}"
            )
        } else {
            AmbientResponse(
                text = "[Translation requires full AI - charge device for enhanced mode]",
                type = AmbientResponseType.DEGRADED,
                confidence = 0.3f,
                contextId = "translation-fallback-${System.currentTimeMillis()}"
            )
        }
    }

    private fun generateTimeConfirmation(event: AmbientEventHub.TimeEntryEvent): AmbientResponse? {
        val action = when {
            event.clockIn == true -> "Clocked in"
            event.clockOut == true -> "Clocked out"
            event.breakStart == true -> "Break started"
            else -> return null
        }

        return AmbientResponse(
            text = "✓ $action successfully.",
            type = AmbientResponseType.CONFIRMATION,
            confidence = 1.0f,
            contextId = "time-${System.currentTimeMillis()}"
        )
    }

    private fun generateJobUpdate(event: AmbientEventHub.JobEvent): AmbientResponse? {
        val action = when (event.action) {
            AmbientEventHub.JobAction.CHECKLIST_UPDATED -> "Checklist updated"
            AmbientEventHub.JobAction.MATERIAL_ADDED -> "Material added: ${event.materialUpdated}"
            AmbientEventHub.JobAction.STATUS_CHANGED -> "Job status updated"
            else -> return null
        }

        return AmbientResponse(
            text = "✓ $action for job ${event.jobId}.",
            type = AmbientResponseType.JOB_UPDATE,
            confidence = 0.9f,
            contextId = "job-${event.jobId}-${System.currentTimeMillis()}"
        )
    }

    // ════════════════════════════════════════════════════════════════════
    // DELIVERY (mesh-friendly payloads, invisible integration)
    // ════════════════════════════════════════════════════════════════════

    private suspend fun deliverAmbientResponse(response: AmbientResponse, event: Any) {
        try {
            // For mesh messages, keep payloads tiny
            val payload = createMeshPayload(response)

            // Decide delivery method based on connectivity and response type
            when (event) {
                is AmbientEventHub.MessageEvent -> {
                    deliverMessageResponse(payload, event.message.channelId, response)
                }
                is AmbientEventHub.TimeEntryEvent -> {
                    deliverTimeAction(response, event)
                }
                is AmbientEventHub.JobEvent -> {
                    deliverJobAction(response, event)
                }
            }

            Log.d(TAG, "Ambient response delivered: ${response.type} (${response.confidence})")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to deliver ambient response", e)
        }
    }

    private suspend fun deliverMessageResponse(payload: String, channelId: String, response: AmbientResponse) {
        // Try immediate delivery via existing message infrastructure
        try {
            // Create a message for the response
            val message = Message(
                id = "ai-${response.contextId}",
                channelId = channelId,
                senderId = "assistant",
                senderName = "Assistant",
                content = payload,
                timestamp = System.currentTimeMillis(),
                isMeshOrigin = false
            )
            MessageRepository.addMessage(message)
            ChatManager.sendMessage(message)
        } catch (e: Exception) {
            // Queue for later sync if immediate delivery fails
            val aiResponse = AIResponse.Success(
                text = payload,
                source = AISource.RULE_BASED,
                model = "ambient-rule-engine",
                durationMs = 0,
                tokensGenerated = 0,
                cueType = AICueType.NONE,
                intent = AIIntent.NONE
            )

            OfflineQueueManager.queueResponse(
                response = aiResponse,
                channelId = channelId,
                contextId = response.contextId
            )

            Log.d(TAG, "Queued ambient response for offline sync")
        }
    }

    private fun deliverTimeAction(response: AmbientResponse, event: AmbientEventHub.TimeEntryEvent) {
        // Queue time-related actions for sync
        val action = AIAction.TimeConfirmation(response.text)
        OfflineQueueManager.queueAction(
            action = action,
            channelId = "time-tracking", // Could be user-specific channel
            contextId = response.contextId
        )
    }

    private fun deliverJobAction(response: AmbientResponse, event: AmbientEventHub.JobEvent) {
        // Queue job-related actions for sync
        val action = when (response.type) {
            AmbientResponseType.JOB_UPDATE -> AIAction.JobUpdate(response.text)
            AmbientResponseType.MATERIAL_REQUEST -> AIAction.MaterialRequest(response.text)
            else -> AIAction.JobUpdate(response.text)
        }

        OfflineQueueManager.queueAction(
            action = action,
            channelId = "job-${event.jobId}",
            jobId = event.jobId,
            contextId = response.contextId
        )
    }

    private fun createMeshPayload(response: AmbientResponse): String {
        // Keep mesh payloads tiny - use abbreviations and codes
        return when (response.type) {
            AmbientResponseType.CONFIRMATION -> "✓ ${response.text}"
            AmbientResponseType.MATERIAL_REQUEST -> "📦 ${response.text}"
            AmbientResponseType.CHECKLIST -> "📋 ${response.text}"
            AmbientResponseType.TRANSLATION -> "🌐 ${response.text}"
            AmbientResponseType.JOB_UPDATE -> "🔧 ${response.text}"
            AmbientResponseType.DEGRADED -> "⚠️ ${response.text}"
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // UTILITIES
    // ════════════════════════════════════════════════════════════════════

    private fun isNonEnglishText(content: String): Boolean {
        // Simple heuristics for non-English detection
        val englishWords = setOf("the", "and", "or", "but", "in", "on", "at", "to", "for", "of", "with", "by")
        val words = content.split("\\s+".toRegex()).take(5) // Check first 5 words

        // If most words aren't common English words, might be foreign language
        val englishWordCount = words.count { word ->
            englishWords.any { common -> word.contains(common, ignoreCase = true) }
        }

        return englishWordCount < words.size / 2 && words.size >= 2
    }

    private fun detectLanguage(content: String): String {
        // Very basic language detection - could be enhanced
        return when {
            content.contains(Regex("[áéíóúüñ]")) -> "es" // Spanish
            content.contains(Regex("[àâäéèêëïîôöùûüÿç]")) -> "fr" // French
            else -> "unknown"
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// DATA CLASSES
// ════════════════════════════════════════════════════════════════════

/**
 * Ambient AI response (lightweight, mesh-friendly)
 */
data class AmbientResponse(
    val text: String,
    val type: AmbientResponseType,
    val confidence: Float, // 0.0 to 1.0
    val contextId: String // For tracking and deduplication
)

/**
 * Types of ambient responses
 */
enum class AmbientResponseType {
    CONFIRMATION,      // Clock in/out, acknowledgments
    MATERIAL_REQUEST,  // Material needs
    CHECKLIST,         // Task/checklists
    TRANSLATION,       // Language translation
    JOB_UPDATE,        // Job status changes
    DEGRADED           // Fallback when full AI unavailable
}
