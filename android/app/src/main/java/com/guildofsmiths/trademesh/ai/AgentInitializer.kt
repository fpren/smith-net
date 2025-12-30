package com.guildofsmiths.trademesh.ai

import android.content.Context
import android.util.Log
import com.guildofsmiths.trademesh.data.JobRepository
import com.guildofsmiths.trademesh.data.Message
import com.guildofsmiths.trademesh.data.MessageRepository
import com.guildofsmiths.trademesh.data.OccupationalForms
import com.guildofsmiths.trademesh.data.TimeEntryRepository
import com.guildofsmiths.trademesh.data.TradeRole
import com.guildofsmiths.trademesh.data.UserPreferences
import com.guildofsmiths.trademesh.ui.timetracking.TimeEntry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * AgentInitializer - Handles AI agent awakening and context initialization
 *
 * When user presses "Load" button:
 * 1. Loads the downloaded LLM model (Qwen3)
 * 2. Initializes agent memory from app data (past jobs, patterns, occupational forms)
 * 3. Awakens the agent for proactive operation
 * 4. Falls back to rule-based mode if loading fails
 *
 * The agent becomes "alive" - proactively observing, suggesting, and assisting
 * while respecting battery/signal constraints.
 */
object AgentInitializer {

    private const val TAG = "AgentInitializer"

    // ════════════════════════════════════════════════════════════════════
    // AGENT STATE
    // ════════════════════════════════════════════════════════════════════

    private val _agentState = MutableStateFlow(AgentState.SLEEPING)
    val agentState: StateFlow<AgentState> = _agentState.asStateFlow()

    private val _initializationProgress = MutableStateFlow(0f)
    val initializationProgress: StateFlow<Float> = _initializationProgress.asStateFlow()

    private val _contextSummary = MutableStateFlow("")
    val contextSummary: StateFlow<String> = _contextSummary.asStateFlow()

    private var agentContext: AgentContext? = null
    private var context: Context? = null
    private val agentScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // ════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ════════════════════════════════════════════════════════════════════

    /**
     * Initialize the agent initializer. Call once at app startup.
     */
    fun initialize(appContext: Context) {
        context = appContext.applicationContext
        Log.i(TAG, "AgentInitializer initialized")
    }

    /**
     * Wake the agent - called when user presses "Load" button.
     * This is the final activation gate for the AI agent.
     */
    suspend fun wakeAgent(modelPath: String): AgentWakeResult = withContext(Dispatchers.IO) {
        Log.i(TAG, "Waking agent with model: $modelPath")

        _agentState.value = AgentState.WAKING
        _initializationProgress.value = 0f

        try {
            // Step 1: Load the model
            _initializationProgress.value = 0.1f
            val loadSuccess = LlamaInference.loadModel(modelPath)
            if (!loadSuccess) {
                Log.e(TAG, "Failed to load model")
                _agentState.value = AgentState.SLEEPING
                return@withContext AgentWakeResult.Failure("Model loading failed")
            }

            // Step 2: Initialize context from app data
            _initializationProgress.value = 0.3f
            val contextResult = initializeAgentContext()
            if (contextResult is ContextInitResult.Failure) {
                Log.w(TAG, "Context initialization failed, falling back to rule-based")
                _agentState.value = AgentState.RULE_BASED_FALLBACK
                return@withContext AgentWakeResult.Success(true) // Success but rule-based fallback
            }

            // Step 3: Build agent memory and patterns
            _initializationProgress.value = 0.6f
            val memoryResult = buildAgentMemory(contextResult as ContextInitResult.Success)
            if (memoryResult is MemoryBuildResult.Failure) {
                Log.w(TAG, "Memory building failed, falling back to rule-based")
                _agentState.value = AgentState.RULE_BASED_FALLBACK
                return@withContext AgentWakeResult.Success(true)
            }

            // Step 4: Initialize proactive behavior
            _initializationProgress.value = 0.8f
            initializeProactiveBehavior(memoryResult as MemoryBuildResult.Success)

            // Step 5: Final activation
            _initializationProgress.value = 1.0f
            _agentState.value = AgentState.ALIVE
            agentContext = (memoryResult as MemoryBuildResult.Success).context

            Log.i(TAG, "Agent successfully awakened and alive")
            AgentWakeResult.Success(false) // Success with full AI capabilities

        } catch (e: Exception) {
            Log.e(TAG, "Exception during agent wake", e)
            _agentState.value = AgentState.SLEEPING
            AgentWakeResult.Failure("Exception: ${e.message}")
        }
    }

    /**
     * Check if agent is currently alive and proactive.
     */
    fun isAgentAlive(): Boolean = _agentState.value == AgentState.ALIVE

    /**
     * Get current agent context for reasoning.
     */
    fun getAgentContext(): AgentContext? = agentContext

    /**
     * Sleep the agent - called on model unload or app shutdown.
     */
    fun sleepAgent() {
        Log.i(TAG, "Putting agent to sleep")
        _agentState.value = AgentState.SLEEPING
        agentContext = null
        _contextSummary.value = ""
        _initializationProgress.value = 0f
    }

    /**
     * Get agent status text for UI.
     */
    fun getStatusText(): String {
        return when (_agentState.value) {
            AgentState.SLEEPING -> "Agent sleeping"
            AgentState.WAKING -> "Agent waking... (${(_initializationProgress.value * 100).toInt()}%)"
            AgentState.ALIVE -> "Agent alive and proactive"
            AgentState.RULE_BASED_FALLBACK -> "Agent in rule-based mode"
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // CONTEXT INITIALIZATION
    // ════════════════════════════════════════════════════════════════════

    private suspend fun initializeAgentContext(): ContextInitResult {
        return try {
            // Gather data from repositories (using existing state)
            val jobs = JobRepository.activeJobs.value
            val messages = MessageRepository.getAllMessages().take(100) // Recent messages
            val timeEntries = TimeEntryRepository.entries.value.take(50) // Recent entries
            val userPrefs = UserPreferences.getAllPreferences()
            val tradeRole = UserPreferences.getTradeRole()
            val roleKnowledge = OccupationalForms.getKnowledgeBase(tradeRole)

            // Build context object
            val agentCtx = AgentContext(
                userName = UserPreferences.getUserName(),
                userId = UserPreferences.getUserId(),
                jobHistory = jobs,
                messageHistory = messages,
                timeTrackingHistory = timeEntries,
                userPreferences = userPrefs,
                tradeRole = tradeRole,
                roleKnowledge = roleKnowledge,
                occupationalProfiles = loadOccupationalProfiles(),
                learnedPatterns = emptyMap() // Will be populated during memory building
            )

            ContextInitResult.Success(agentCtx)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize agent context", e)
            ContextInitResult.Failure("Context init error: ${e.message}")
        }
    }

    private fun loadOccupationalProfiles(): Map<String, OccupationalProfile> {
        // Load predefined occupational forms and procedures
        return mapOf(
            "electrician" to OccupationalProfile(
                name = "Electrician",
                commonTasks = listOf(
                    "Panel upgrade", "Circuit installation", "Outlet installation",
                    "Lighting installation", "Safety inspection", "Code compliance check"
                ),
                requiredTools = listOf(
                    "Multimeter", "Wire strippers", "Voltage tester", "Fish tape",
                    "Conduit bender", "Cable cutters", "NEC code book"
                ),
                safetyProtocols = listOf(
                    "Lock out/tag out procedures", "GFCI testing", "Arc flash protection",
                    "Proper grounding verification", "Voltage testing before work"
                ),
                commonPatterns = listOf(
                    "Check power off before wiring", "Test circuits after installation",
                    "Document all work performed", "Verify permit requirements"
                )
            ),
            "hvac" to OccupationalProfile(
                name = "HVAC Technician",
                commonTasks = listOf(
                    "System installation", "Maintenance service", "Refrigerant handling",
                    "Ductwork installation", "Thermostat programming", "Load calculations"
                ),
                requiredTools = listOf(
                    "Manifold gauges", "Vacuum pump", "Multimeter", "Pipe cutter",
                    "Flaring tool", "Recovery machine", "Temperature probes"
                ),
                safetyProtocols = listOf(
                    "Proper refrigerant handling", "Electrical safety", "Fall protection",
                    "Confined space procedures", "PPE requirements"
                ),
                commonPatterns = listOf(
                    "Verify system pressures", "Check electrical connections",
                    "Test system operation", "Document refrigerant usage"
                )
            ),
            "plumber" to OccupationalProfile(
                name = "Plumber",
                commonTasks = listOf(
                    "Pipe installation", "Fixture installation", "Leak repair",
                    "Water heater installation", "Drain cleaning", "Pressure testing"
                ),
                requiredTools = listOf(
                    "Pipe wrench", "Threader", "Pipe cutter", "Plunger",
                    "Drain snake", "Pressure tester", "Torch kit"
                ),
                safetyProtocols = listOf(
                    "Proper gas line handling", "Water contamination prevention",
                    "Electrical grounding", "Confined space awareness"
                ),
                commonPatterns = listOf(
                    "Pressure test all connections", "Check for leaks before covering",
                    "Verify proper venting", "Document all repairs"
                )
            )
        )
    }

    // ════════════════════════════════════════════════════════════════════
    // MEMORY BUILDING
    // ════════════════════════════════════════════════════════════════════

    private suspend fun buildAgentMemory(contextResult: ContextInitResult.Success): MemoryBuildResult {
        return try {
            val baseContext = contextResult.context

            // Analyze patterns from job history
            val jobPatterns = analyzeJobPatterns(baseContext.jobHistory)

            // Analyze communication patterns
            val communicationPatterns = analyzeCommunicationPatterns(baseContext.messageHistory)

            // Analyze time tracking patterns
            val timePatterns = analyzeTimePatterns(baseContext.timeTrackingHistory)

            // Build comprehensive context
            val enhancedContext = baseContext.copy(
                learnedPatterns = mapOf(
                    "job_patterns" to jobPatterns,
                    "communication_patterns" to communicationPatterns,
                    "time_patterns" to timePatterns
                )
            )

            // Generate context summary for LLM
            val summary = generateContextSummary(enhancedContext)
            _contextSummary.value = summary

            MemoryBuildResult.Success(enhancedContext)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to build agent memory", e)
            MemoryBuildResult.Failure("Memory build error: ${e.message}")
        }
    }

    private fun analyzeJobPatterns(jobs: List<JobRepository.SimpleJob>): Map<String, Any> {
        val patterns = mutableMapOf<String, Any>()

        // Common job types
        val jobTypes = jobs.groupBy { it.title }.mapValues { it.value.size }
        patterns["common_job_types"] = jobTypes

        // Job status distribution
        val jobStatuses = jobs.groupBy { it.status }.mapValues { it.value.size }
        patterns["job_status_distribution"] = jobStatuses

        // Active jobs count
        val activeJobs = jobs.filter { it.status in listOf("TODO", "IN_PROGRESS", "REVIEW") }
        patterns["active_jobs_count"] = activeJobs.size

        // Job titles for pattern recognition
        patterns["active_job_titles"] = activeJobs.map { it.title }

        return patterns
    }

    private fun analyzeCommunicationPatterns(messages: List<Message>): Map<String, Any> {
        val patterns = mutableMapOf<String, Any>()

        // Communication frequency
        val messageCount = messages.size
        patterns["total_messages"] = messageCount

        // Time-based patterns
        val messagesByHour = messages.groupBy { it.timestamp / (1000 * 60 * 60) % 24 }
        patterns["peak_hours"] = messagesByHour.maxByOrNull { it.value.size }?.key ?: 0

        // Common phrases and triggers
        val commonPhrases = mutableListOf<String>()
        messages.forEach { msg ->
            val content = msg.content.lowercase()
            if (content.contains("need help")) commonPhrases.add("help requests")
            if (content.contains("running late")) commonPhrases.add("schedule updates")
            if (content.contains("material")) commonPhrases.add("material discussions")
        }
        patterns["common_phrases"] = commonPhrases.distinct()

        return patterns
    }

    private fun analyzeTimePatterns(timeEntries: List<TimeEntry>): Map<String, Any> {
        val patterns = mutableMapOf<String, Any>()

        // Work patterns - handle nullable durationMinutes
        val totalMinutes = timeEntries.mapNotNull { it.durationMinutes }.sum()
        patterns["total_hours_worked"] = totalMinutes / 60.0

        // Average session length
        val durations = timeEntries.mapNotNull { it.durationMinutes }
        val avgSession = if (durations.isNotEmpty()) durations.average() else 0.0
        patterns["average_session_hours"] = avgSession / 60.0

        // Break patterns - use entryType instead of isBreak
        val breakEntries = timeEntries.filter { it.entryType == com.guildofsmiths.trademesh.ui.timetracking.EntryType.BREAK }
        patterns["break_frequency"] = breakEntries.size

        return patterns
    }

    private fun generateContextSummary(context: AgentContext): String {
        val builder = StringBuilder()

        builder.append("USER PROFILE:\n")
        builder.append("- Name: ${context.userName}\n")
        builder.append("- ID: ${context.userId}\n")
        builder.append("- Trade Role: ${context.tradeRole.displayName}\n")
        builder.append("- Total jobs: ${context.jobHistory.size}\n")
        builder.append("- Total messages: ${context.messageHistory.size}\n")
        builder.append("- Hours tracked: ${context.timeTrackingHistory.mapNotNull { it.durationMinutes }.sum() / 60.0}\n\n")

        builder.append("ROLE-SPECIFIC KNOWLEDGE:\n")
        builder.append("- Core Skills: ${context.roleKnowledge.coreSkills.joinToString(", ")}\n")
        builder.append("- Key Regulations: ${context.roleKnowledge.regulations.joinToString(", ")}\n")
        builder.append("- Safety Protocols: ${context.roleKnowledge.safetyProtocols.size} protocols\n")
        builder.append("- Common Tools: ${context.roleKnowledge.tools.size} with dexterity guidance\n\n")

        builder.append("LEARNED PATTERNS:\n")
        context.learnedPatterns.forEach { (key, value) ->
            builder.append("- $key: $value\n")
        }

        return builder.toString()
    }

    // ════════════════════════════════════════════════════════════════════
    // PROACTIVE BEHAVIOR INITIALIZATION
    // ════════════════════════════════════════════════════════════════════

    private fun initializeProactiveBehavior(memoryResult: MemoryBuildResult.Success) {
        val context = memoryResult.context

        // Start ambient observation in background
        agentScope.launch {
            startAmbientObservation(context)
        }

        // Start proactive suggestion engine
        agentScope.launch {
            startProactiveSuggestions(context)
        }

        Log.i(TAG, "Proactive behavior initialized")
    }

    private suspend fun startAmbientObservation(context: AgentContext) {
        Log.d(TAG, "Starting ambient observation")

        // This will be called by AmbientObserver for proactive analysis
        // The agent is now "alive" and will observe all messages for assistance opportunities

        // Set up periodic context refresh (every hour)
        while (isAgentAlive()) {
            delay(60 * 60 * 1000L) // 1 hour
            refreshAgentContext()
        }
    }

    private suspend fun startProactiveSuggestions(context: AgentContext) {
        Log.d(TAG, "Starting proactive suggestions")

        // This will generate contextual suggestions based on:
        // - Current time of day
        // - Recent job activity
        // - Weather conditions (via integrated tools)
        // - Team coordination needs

        // Set up periodic suggestion checks (every 30 minutes)
        while (isAgentAlive()) {
            delay(30 * 60 * 1000L) // 30 minutes
            generateProactiveSuggestions(context)
        }
    }

    private suspend fun refreshAgentContext() {
        Log.d(TAG, "Refreshing agent context")
        try {
            val newContext = initializeAgentContext()
            if (newContext is ContextInitResult.Success) {
                agentContext = newContext.context
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to refresh agent context", e)
        }
    }

    private suspend fun generateProactiveSuggestions(context: AgentContext) {
        // Generate contextual suggestions based on current state
        // This would integrate with the reasoning loop to provide helpful suggestions

        Log.d(TAG, "Generating proactive suggestions")
        // Implementation would check:
        // - Time for breaks
        // - Upcoming deadlines
        // - Material needs
        // - Safety reminders
        // - Coordination opportunities
    }

    // ════════════════════════════════════════════════════════════════════
    // REASONING LOOP AND TOOL CHAINING
    // ════════════════════════════════════════════════════════════════════

    /**
     * Enhanced reasoning with tool chaining for complex queries.
     */
    suspend fun enhancedReasoning(
        query: String,
        context: AgentContext,
        availableTools: List<String>
    ): String {
        if (!isAgentAlive()) {
            return "Agent not active - using rule-based response"
        }

        // Build enhanced prompt with context and tools
        val enhancedPrompt = buildReasoningPrompt(query, context, availableTools)

        // Call LLM with tool integration
        val result = LlamaInference.generate(
            prompt = enhancedPrompt,
            maxTokens = 200,
            temperature = 0.3f
        )

        return when (result) {
            is GenerationResult.Success -> {
                // Check if LLM wants to use tools
                val response = result.text
                if (response.contains("[TOOL_CALL:")) {
                    // Parse tool calls and execute them
                    executeToolChain(response, context)
                } else {
                    response
                }
            }
            is GenerationResult.Error -> {
                Log.w(TAG, "LLM reasoning failed: ${result.message}")
                "I apologize, but I encountered an issue processing your request. Please try again."
            }
        }
    }

    private fun buildReasoningPrompt(
        query: String,
        context: AgentContext,
        availableTools: List<String>
    ): String {
        val roleGuidance = """
            TRADE ROLE: ${context.tradeRole.displayName}
            - Focus on ${context.roleKnowledge.coreSkills.joinToString(", ")}
            - Reference ${context.roleKnowledge.regulations.joinToString(", ")} when relevant
            - Include dexterity considerations: ${context.roleKnowledge.breakReminders.take(2).joinToString("; ")}
            - Use role-appropriate procedures and safety protocols
        """.trimIndent()

        val systemPrompt = """
            You are Smith, an intelligent AI assistant specialized for ${context.tradeRole.displayName}s in construction and trade work.
            You have deep knowledge of ${context.tradeRole.displayName} procedures, tools, safety protocols, and dexterity requirements.

            $roleGuidance

            USER CONTEXT:
            ${_contextSummary.value}

            AVAILABLE TOOLS:
            ${availableTools.joinToString("\n")}

            When responding as a ${context.tradeRole.displayName}:
            - Reference role-specific procedures and safety protocols naturally
            - Include dexterity and fatigue prevention in tool/skill suggestions
            - Mention relevant regulations (NEC, OSHA, etc.) when applicable
            - Keep responses under 100 words for mobile efficiency
            - Use tools when they would provide better information
            - Format tool calls as [TOOL_CALL:tool_name:parameters]

            Current query: $query
        """.trimIndent()

        return "<|im_start|>system\n$systemPrompt<|im_end|>\n<|im_start|>user\n$query<|im_end|>\n<|im_start|>assistant\n"
    }

    private suspend fun executeToolChain(response: String, context: AgentContext): String {
        // Parse and execute tool calls from LLM response
        // This is a simplified implementation - would need proper tool parsing

        var finalResponse = response

        // Example tool integration
        if (response.contains("weather")) {
            // Would integrate with weather API
            finalResponse = finalResponse.replace("[TOOL_CALL:weather]", "[Weather: 72°F, Clear]")
        }

        if (response.contains("code_execution")) {
            // Would integrate with code execution for calculations
            finalResponse = finalResponse.replace("[TOOL_CALL:code_execution]", "[Calculated: 240V circuit]")
        }

        return finalResponse
    }

    // ════════════════════════════════════════════════════════════════════
    // CLEANUP
    // ════════════════════════════════════════════════════════════════════

    fun shutdown() {
        Log.i(TAG, "Shutting down agent initializer")
        agentScope.cancel()
        sleepAgent()
    }
}

// ════════════════════════════════════════════════════════════════════
// DATA CLASSES
// ════════════════════════════════════════════════════════════════════

enum class AgentState {
    SLEEPING,           // Agent not loaded
    WAKING,            // Loading and initializing
    ALIVE,             // Fully active and proactive
    RULE_BASED_FALLBACK // Using rule-based mode
}

sealed class AgentWakeResult {
    data class Success(val isFallbackMode: Boolean) : AgentWakeResult()
    data class Failure(val reason: String) : AgentWakeResult()
}

sealed class ContextInitResult {
    data class Success(val context: AgentContext) : ContextInitResult()
    data class Failure(val reason: String) : ContextInitResult()
}

sealed class MemoryBuildResult {
    data class Success(val context: AgentContext) : MemoryBuildResult()
    data class Failure(val reason: String) : MemoryBuildResult()
}

data class AgentContext(
    val userName: String,
    val userId: String,
    val jobHistory: List<JobRepository.SimpleJob>,
    val messageHistory: List<Message>,
    val timeTrackingHistory: List<TimeEntry>,
    val userPreferences: Map<String, Any>,
    val tradeRole: TradeRole,
    val roleKnowledge: OccupationalForms.RoleKnowledgeBase,
    val occupationalProfiles: Map<String, OccupationalProfile>,
    val learnedPatterns: Map<String, Any>
)

data class OccupationalProfile(
    val name: String,
    val commonTasks: List<String>,
    val requiredTools: List<String>,
    val safetyProtocols: List<String>,
    val commonPatterns: List<String>
)
