package com.guildofsmiths.trademesh.ai

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.guildofsmiths.trademesh.data.AIMode
import com.guildofsmiths.trademesh.data.Message
import com.guildofsmiths.trademesh.data.UserPreferences
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * AIRouter - Main routing logic for AI requests
 * 
 * Routes AI requests through the appropriate pipeline:
 * 1. Cue Detection → Determine if AI is needed and intent
 * 2. Battery Gate → Check if inference is allowed
 * 3. Route to LLM or Rule-Based → Based on availability
 * 4. Cache Response → For offline sync
 * 5. Deliver Response → With attribution
 * 
 * Handles:
 * - Online/offline routing
 * - Battery-aware degradation
 * - Response caching for sync
 * - Context preservation
 */
object AIRouter {
    
    private const val TAG = "AIRouter"
    
    // Status state
    private val _status = MutableStateFlow(AIStatus.OFFLINE)
    val status: StateFlow<AIStatus> = _status.asStateFlow()
    
    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()
    
    // Configuration
    private var context: Context? = null
    private var isInitialized = false
    private var aiEnabled = false
    private var modelReady = false
    
    // Response cache for offline sync
    private val responseCache = ConcurrentLinkedQueue<CachedAIResponse>()
    
    // Coroutine scope for AI processing
    private val aiScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // ════════════════════════════════════════════════════════════════════
    // INITIALIZATION
    // ════════════════════════════════════════════════════════════════════
    
    /**
     * Initialize the AI Router. Call once at app startup.
     */
    fun initialize(appContext: Context) {
        if (isInitialized) {
            Log.d(TAG, "Already initialized")
            return
        }
        
        context = appContext.applicationContext
        
        // Initialize subsystems
        BatteryGate.initialize(appContext)
        LlamaInference.initialize()
        OfflineQueueManager.initialize(appContext)
        AgentInitializer.initialize(appContext)
        
        isInitialized = true
        updateStatus()
        
        Log.i(TAG, "AIRouter initialized. Status: ${_status.value}")
    }
    
    /**
     * Enable or disable AI features.
     */
    fun setEnabled(enabled: Boolean) {
        aiEnabled = enabled
        updateStatus()
        Log.i(TAG, "AI ${if (enabled) "enabled" else "disabled"}")
    }
    
    fun isEnabled(): Boolean = aiEnabled
    
    /**
     * Cleanup resources. Call at app shutdown.
     */
    fun shutdown() {
        Log.i(TAG, "Shutting down AIRouter")
        aiScope.cancel()
        LlamaInference.shutdown()
        BatteryGate.shutdown()
        OfflineQueueManager.shutdown()
        AgentInitializer.shutdown()
        isInitialized = false
        context = null
    }
    
    // ════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ════════════════════════════════════════════════════════════════════
    
    /**
     * Process a message automatically for ambient AI assistance.
     * No explicit cues needed - AI observes and assists contextually.
     *
     * @param message The message to observe
     * @param messageContext Where the message came from
     * @param metadata Additional context (job ID, channel ID, etc.)
     * @param onResponse Callback with the AI response (only if assistance is needed)
     */
    suspend fun processMessage(
        message: Message,
        messageContext: MessageContext = MessageContext.CHAT,
        metadata: AIMetadata = AIMetadata(),
        onResponse: (AIResponse) -> Unit
    ) = withContext(Dispatchers.Default) {

        Log.d(TAG, "Observing message: ${message.content.take(50)}...")

        if (!aiEnabled) {
            Log.d(TAG, "AI disabled, skipping observation")
            return@withContext
        }

        // Step 1: Ambient observation - always analyze when AI is enabled
        val observation = AmbientObserver.observe(message, messageContext)

        if (!observation.needsAssistance) {
            Log.d(TAG, "No assistance needed for this message")
            return@withContext
        }

        Log.d(TAG, "Assistance needed: ${observation.assistanceType} (${observation.confidence})")

        _isProcessing.value = true

        try {
            // Step 2: Check battery gate
            val availability = BatteryGate.getAIStatus()

            val response = when (availability) {
                AIAvailability.DISABLED -> {
                    Log.d(TAG, "AI disabled due to battery")
                    AIResponse.BatteryLow(
                        "Ambient AI paused - battery too low (${BatteryGate.gateState.value.batteryLevel}%)"
                    )
                }

                else -> {
                    // Process with sub-agents (works in all modes including offline)
                    processWithSubAgents(observation, metadata, availability)
                }
            }

            // Step 3: Handle offline queuing and sync
            if (response is AIResponse.Success) {
                handleResponseDelivery(response, message, metadata, onResponse)
            } else {
                onResponse(response)
            }

        } finally {
            _isProcessing.value = false
        }
    }
    
    /**
     * Check if AI is currently available for requests.
     */
    fun isAvailable(): Boolean {
        if (!aiEnabled) return false
        if (!isInitialized) return false
        
        val batteryOk = BatteryGate.getAIStatus() != AIAvailability.DISABLED
        return batteryOk
    }

    /**
     * Check if Hybrid Mode should be used (external LLM calls).
     * Hybrid Mode requirements:
     * - User selected Hybrid Mode
     * - Internet connectivity available
     * - Battery > 20% OR charging
     * - Thermal state not critical
     * - Not in power save mode
     */
    fun shouldUseHybridMode(context: Context): Boolean {
        // Must be in Hybrid Mode
        if (UserPreferences.getAIMode() != AIMode.HYBRID) {
            return false
        }

        // Check connectivity
        if (!isConnectedToInternet(context)) {
            Log.d(TAG, "Hybrid mode blocked: No internet connection")
            return false
        }

        // Check battery (stricter than Standard mode)
        val batteryState = BatteryGate.gateState.value
        if (batteryState.batteryLevel <= 20 && !batteryState.isCharging) {
            Log.d(TAG, "Hybrid mode blocked: Battery too low (${batteryState.batteryLevel}%)")
            return false
        }

        // Check thermal state
        if (batteryState.thermalStatus >= ThermalStatus.SEVERE) {
            Log.d(TAG, "Hybrid mode blocked: Thermal throttling (${batteryState.thermalStatus})")
            return false
        }

        // Check power save mode
        if (batteryState.isPowerSaveMode) {
            Log.d(TAG, "Hybrid mode blocked: Power save mode active")
            return false
        }

        Log.d(TAG, "Hybrid mode conditions met")
        return true
    }

    /**
     * Check internet connectivity.
     */
    private fun isConnectedToInternet(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = connectivityManager?.activeNetwork
        val capabilities = connectivityManager?.getNetworkCapabilities(network)
        return capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }
    
    /**
     * Get the current AI status.
     */
    fun getStatus(): AIStatus = _status.value
    
    /**
     * Get human-readable status for UI.
     */
    fun getStatusText(): String {
        return when (_status.value) {
            AIStatus.READY -> "AI Ready"
            AIStatus.LOADING -> "AI Loading..."
            AIStatus.DEGRADED -> "AI Degraded"
            AIStatus.RULE_BASED -> "Rule-Based"
            AIStatus.OFFLINE -> "AI Offline"
            AIStatus.DISABLED -> "AI Disabled"
        }
    }
    
    /**
     * Load the AI model from the specified path and wake the agent.
     * This is called when the user presses the "Load" button.
     */
    suspend fun loadModel(modelPath: String): Boolean {
        return try {
            val result = LlamaInference.loadModel(modelPath)
            modelReady = result

            if (result) {
                // Model loaded successfully - wake the agent
                Log.i(TAG, "Model loaded, waking agent...")
                val wakeResult = AgentInitializer.wakeAgent(modelPath)

                when (wakeResult) {
                    is AgentWakeResult.Success -> {
                        if (wakeResult.isFallbackMode) {
                            Log.i(TAG, "Agent awakened in fallback mode")
                        } else {
                            Log.i(TAG, "Agent fully awakened and alive")
                        }
                    }
                    is AgentWakeResult.Failure -> {
                        Log.w(TAG, "Agent wake failed: ${wakeResult.reason}, falling back to rule-based")
                    }
                }
            } else {
                Log.e(TAG, "Model loading failed")
            }

            updateStatus()
            result
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model", e)
            modelReady = false
            updateStatus()
            false
        }
    }
    
    /**
     * Check if model is loaded.
     */
    fun isModelLoaded(): Boolean = LlamaInference.isModelLoaded()
    
    /**
     * Get cached responses pending sync.
     */
    fun getPendingResponses(): List<CachedAIResponse> = responseCache.toList()
    
    /**
     * Mark responses as synced.
     */
    fun markResponsesSynced(ids: List<String>) {
        responseCache.removeIf { it.id in ids }
        Log.d(TAG, "Marked ${ids.size} responses as synced")
    }
    
    /**
     * Clear all cached responses.
     */
    fun clearCache() {
        responseCache.clear()
    }
    
    // ════════════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ════════════════════════════════════════════════════════════════════
    
    private suspend fun processWithLLM(
        cue: AICue,
        metadata: AIMetadata,
        maxTokens: Int
    ): AIResponse {
        // Check if model is ready
        if (!LlamaInference.isModelLoaded()) {
            Log.w(TAG, "Model not loaded, falling back to rule-based")
            return processWithRuleBased(cue, metadata)
        }
        
        // If agent is alive, use enhanced reasoning with context
        if (AgentInitializer.isAgentAlive()) {
            val agentContext = AgentInitializer.getAgentContext()
            if (agentContext != null) {
                val availableTools = listOf("web_search", "weather_api", "code_execution")
                val enhancedResponse = AgentInitializer.enhancedReasoning(
                    query = cue.extractedQuery ?: cue.originalMessage,
                    context = agentContext,
                    availableTools = availableTools
                )

                return AIResponse.Success(
                    text = enhancedResponse,
                    source = AISource.LLM,
                    model = "qwen3-1.7b-q4-agent",
                    durationMs = 0, // Would need to track this
                    tokensGenerated = 0, // Would need to estimate this
                    cueType = cue.type,
                    intent = cue.intent
                )
            }
        }

        // Standard LLM inference (fallback)
        val prompt = buildPrompt(cue, metadata)
        
        val result = LlamaInference.generate(
            prompt = prompt,
            maxTokens = maxTokens,
            temperature = 0.7f
        )
        
        return when (result) {
            is GenerationResult.Success -> {
                AIResponse.Success(
                    text = result.text.trim(),
                    source = AISource.LLM,
                    model = "qwen3-1.7b-q4",
                    durationMs = result.durationMs,
                    tokensGenerated = result.tokensGenerated,
                    cueType = cue.type,
                    intent = cue.intent
                )
            }
            is GenerationResult.Error -> {
                Log.e(TAG, "LLM error: ${result.message}")
                // Fall back to rule-based
                processWithRuleBased(cue, metadata)
            }
        }
    }
    
    private fun processWithRuleBased(
        cue: AICue,
        metadata: AIMetadata
    ): AIResponse {
        val response = RuleBasedFallback.getResponse(cue, metadata)

        return AIResponse.Success(
            text = response,
            source = AISource.RULE_BASED,
            model = "rule-based",
            durationMs = 0,
            tokensGenerated = 0,
            cueType = cue.type,
            intent = cue.intent
        )
    }

    private suspend fun processWithSubAgents(
        observation: AmbientObserver.Observation,
        metadata: AIMetadata,
        availability: AIAvailability
    ): AIResponse {
        Log.d(TAG, "Processing with sub-agent: ${observation.subAgent}")

        // Get sub-agent response
        val subResponse = SubAgents.process(observation)

        if (subResponse.responseType == SubAgents.SubAgentResponse.ResponseType.NONE) {
            return AIResponse.Success(
                text = "",
                source = AISource.RULE_BASED,
                model = "ambient-ai",
                durationMs = 0,
                tokensGenerated = 0,
                cueType = AICueType.GENERAL,
                intent = AIIntent.NONE
            )
        }

        // For Hybrid Mode, enhance with external LLM if conditions met
        val enhancedText = if (shouldUseHybridMode(context!!)) {
            Log.d(TAG, "Using Hybrid Mode - attempting external LLM enhancement")
            try {
                // Call external vendor-neutral LLM layer
                // This would integrate with your LLM interface (OpenAI, Claude, etc.)
                enhanceWithExternalLLM(subResponse.content, observation, metadata)
            } catch (e: Exception) {
                Log.w(TAG, "External LLM failed, falling back to local enhancement", e)
                // Fallback: try local LLM if available
                if (availability == AIAvailability.FULL && LlamaInference.isModelLoaded()) {
                    enhanceWithLLM(subResponse.content, observation, metadata)
                } else {
                    subResponse.content
                }
            }
        } else if (availability == AIAvailability.FULL && LlamaInference.isModelLoaded()) {
            // Standard Mode with local LLM available
            enhanceWithLLM(subResponse.content, observation, metadata)
        } else {
            // Standard Mode: rule-based only
            subResponse.content
        }

        return AIResponse.Success(
            text = enhancedText,
            source = AISource.RULE_BASED,
            model = "ambient-ai-${observation.subAgent?.name?.lowercase() ?: "unknown"}",
            durationMs = 0,
            tokensGenerated = 0,
            cueType = AICueType.GENERAL,
            intent = AIIntent.NONE
        )
    }

    private suspend fun enhanceWithLLM(
        baseResponse: String,
        observation: AmbientObserver.Observation,
        metadata: AIMetadata
    ): String {
        // Build a context-aware prompt for enhancement
        val contextPrompt = buildContextPrompt(observation, metadata, baseResponse)

        return try {
            val result = LlamaInference.generate(
                prompt = contextPrompt,
                maxTokens = minOf(BatteryGate.getRecommendedMaxTokens(), 100),
                temperature = 0.3f // Lower temperature for more focused responses
            )

            when (result) {
                is GenerationResult.Success -> {
                    // Combine base response with LLM enhancement
                    "$baseResponse\n\n💡 ${result.text.trim()}"
                }
                else -> baseResponse // Fall back to base response
            }
        } catch (e: Exception) {
            Log.w(TAG, "LLM enhancement failed, using base response", e)
            baseResponse
        }
    }

    private suspend fun enhanceWithExternalLLM(
        baseResponse: String,
        observation: AmbientObserver.Observation,
        metadata: AIMetadata
    ): String {
        // Vendor-neutral external LLM call
        // This integrates with your LLM interface for cloud AI

        val prompt = buildExternalPrompt(observation, metadata, baseResponse)

        return try {
            // Call external LLM via your LLM interface
            // This is a placeholder - integrate with your actual LLM service
            val externalResponse = callExternalLLM(prompt)

            // Combine with base response
            "$baseResponse\n\n💡 ${externalResponse.trim()}"

        } catch (e: Exception) {
            Log.w(TAG, "External LLM call failed", e)
            // Silent fallback to base response
            baseResponse
        }
    }

    private suspend fun callExternalLLM(prompt: String): String {
        // Placeholder for external LLM integration
        // Replace with actual LLM API call (OpenAI, Claude, etc.)
        // Should respect battery/signal priorities - keep payloads small

        // Example structure:
        // return llmInterface.generateResponse(
        //     prompt = prompt,
        //     maxTokens = 100, // Keep small for mesh-friendliness
        //     temperature = 0.3f
        // )

        // For now, simulate network call
        delay(500) // Simulate network latency
        return "Enhanced insight from cloud AI (integrate your LLM service here)"
    }

    private fun buildExternalPrompt(
        observation: AmbientObserver.Observation,
        metadata: AIMetadata,
        baseResponse: String
    ): String {
        return """
            Context: ${observation.context}
            Message: "${observation.message.content}"
            Base assistance: "$baseResponse"

            Provide a brief, contextual enhancement (max 50 words).
            Focus on practical, trade-specific insights.
            Keep response mesh-friendly (under 200 chars).
        """.trimIndent()
    }

    private fun buildContextPrompt(
        observation: AmbientObserver.Observation,
        metadata: AIMetadata,
        baseResponse: String
    ): String {
        return """
            Context: ${observation.context}
            Message: "${observation.message.content}"
            Base assistance: "$baseResponse"

            Provide a brief, contextual enhancement (max 50 words) that adds value without being verbose.
            Focus on being helpful and authoritative.
        """.trimIndent()
    }
    
    private fun buildPrompt(cue: AICue, metadata: AIMetadata): String {
        val systemPrompt = buildSystemPrompt(cue, metadata)
        val userQuery = cue.extractedQuery ?: cue.originalMessage
        
        // Qwen3 chat template
        return """<|im_start|>system
$systemPrompt<|im_end|>
<|im_start|>user
$userQuery<|im_end|>
<|im_start|>assistant
"""
    }
    
    private fun buildSystemPrompt(cue: AICue, metadata: AIMetadata): String {
        val basePrompt = "You are Smith, a helpful AI assistant for construction and trade workers. " +
                         "Keep responses brief, practical, and professional. " +
                         "Use simple language. Respond in 2-4 sentences max."
        
        val contextPrompt = when (cue.context) {
            MessageContext.JOB_BOARD -> 
                " You're helping with job management. Focus on tasks, materials, and timelines."
            MessageContext.TIME_TRACKING -> 
                " You're helping with time tracking. Focus on clock-in/out, breaks, and hours."
            MessageContext.MESH -> 
                " This is an offline message via BLE mesh. Keep response under 100 words."
            MessageContext.CHAT -> ""
        }
        
        val intentPrompt = when (cue.intent) {
            AIIntent.TRANSLATE -> 
                " Translate the following to English. Only provide the translation."
            AIIntent.CHECKLIST -> 
                " Provide a brief checklist. Use bullet points."
            AIIntent.CONFIRM -> 
                " Confirm or correct the information briefly."
            else -> ""
        }
        
        val jobContext = if (metadata.jobTitle != null) {
            " Current job: ${metadata.jobTitle}."
        } else ""
        
        return basePrompt + contextPrompt + intentPrompt + jobContext
    }
    
    private fun cacheResponse(
        response: AIResponse.Success,
        cue: AICue,
        metadata: AIMetadata
    ) {
        val cached = CachedAIResponse(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            query = cue.originalMessage,
            response = response.text,
            source = response.source,
            model = response.model,
            channelId = metadata.channelId,
            jobId = metadata.jobId,
            userId = metadata.userId,
            synced = false
        )
        
        responseCache.add(cached)
        
        // Keep cache bounded
        while (responseCache.size > 100) {
            responseCache.poll()
        }
        
        Log.d(TAG, "Cached response: ${cached.id}")
    }

    private fun handleResponseDelivery(
        response: AIResponse.Success,
        message: Message,
        metadata: AIMetadata,
        onResponse: (AIResponse) -> Unit
    ) {
        val channelId = message.channelId
        val canDeliverImmediately = context?.let { isConnectedToInternet(it) } ?: false

        if (canDeliverImmediately) {
            // Deliver immediately and also cache for potential sync
            cacheAmbientResponse(response, AmbientObserver.Observation(
                messageId = message.id,
                message = message,
                context = MessageContext.CHAT,
                subAgent = null,
                needsAssistance = true,
                assistanceType = AmbientObserver.AssistanceType.NONE,
                confidence = 0.8f
            ), metadata)
            onResponse(response)
        } else {
            // Queue for later sync when connectivity returns
            OfflineQueueManager.queueResponse(
                response = response,
                channelId = channelId,
                jobId = metadata.jobId,
                contextId = "ambient-${System.currentTimeMillis()}"
            )

            // Still call onResponse for immediate UI feedback
            onResponse(response)

            Log.d(TAG, "Queued response for offline sync: ${response.text.take(50)}...")
        }
    }

    private fun cacheAmbientResponse(
        response: AIResponse.Success,
        observation: AmbientObserver.Observation,
        metadata: AIMetadata
    ) {
        val cached = CachedAIResponse(
            id = UUID.randomUUID().toString(),
            timestamp = System.currentTimeMillis(),
            query = observation.message.content,
            response = response.text,
            source = response.source,
            model = response.model,
            channelId = observation.message.channelId,
            jobId = metadata.jobId,
            userId = metadata.userId,
            synced = false
        )

        responseCache.add(cached)
        Log.d(TAG, "Cached ambient response: ${cached.id}")
    }

    private fun updateStatus() {
        _status.value = when {
            !aiEnabled -> AIStatus.DISABLED
            !isInitialized -> AIStatus.OFFLINE
            AgentInitializer.agentState.value == AgentState.WAKING -> AIStatus.LOADING
            AgentInitializer.agentState.value == AgentState.ALIVE -> {
                when (BatteryGate.getAIStatus()) {
                    AIAvailability.DISABLED -> AIStatus.OFFLINE
                    AIAvailability.RULE_BASED_ONLY -> AIStatus.RULE_BASED
                    AIAvailability.DEGRADED -> AIStatus.DEGRADED
                    else -> AIStatus.READY
                }
            }
            AgentInitializer.agentState.value == AgentState.RULE_BASED_FALLBACK -> AIStatus.RULE_BASED
            !modelReady -> AIStatus.RULE_BASED
            BatteryGate.getAIStatus() == AIAvailability.DISABLED -> AIStatus.OFFLINE
            BatteryGate.getAIStatus() == AIAvailability.RULE_BASED_ONLY -> AIStatus.RULE_BASED
            BatteryGate.getAIStatus() == AIAvailability.DEGRADED -> AIStatus.DEGRADED
            else -> AIStatus.READY
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// DATA CLASSES
// ════════════════════════════════════════════════════════════════════

/**
 * AI system status
 */
enum class AIStatus {
    READY,      // Full LLM available
    LOADING,    // Model/agent loading
    DEGRADED,   // LLM with reduced capacity
    RULE_BASED, // Only rule-based responses
    OFFLINE,    // AI unavailable
    DISABLED    // AI disabled by user
}

/**
 * Source of AI response
 */
enum class AISource {
    LLM,        // On-device Qwen3 model
    RULE_BASED, // Pre-defined rule-based responses
    ONLINE      // Cloud API (future)
}

/**
 * Additional context for AI processing
 */
data class AIMetadata(
    val channelId: String? = null,
    val jobId: String? = null,
    val jobTitle: String? = null,
    val userId: String? = null,
    val userName: String? = null,
    val timeEntryId: String? = null
)

/**
 * Result of AI processing
 */
sealed class AIResponse {
    
    data class Success(
        val text: String,
        val source: AISource,
        val model: String,
        val durationMs: Long,
        val tokensGenerated: Int,
        val cueType: AICueType,
        val intent: AIIntent
    ) : AIResponse()
    
    data class BatteryLow(val message: String) : AIResponse()
    
    data class Disabled(val message: String) : AIResponse()
    
    data class Error(val message: String) : AIResponse()
}

/**
 * Cached AI response for offline sync
 */
data class CachedAIResponse(
    val id: String,
    val timestamp: Long,
    val query: String,
    val response: String,
    val source: AISource,
    val model: String,
    val channelId: String?,
    val jobId: String?,
    val userId: String?,
    val synced: Boolean
)
