package com.guildofsmiths.trademesh.planner

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * OnlineResolver - Enhanced online-assisted plan research
 * 
 * USED ONLY BY: TEST ⧉ compile path (not production COMPILE)
 * 
 * PURPOSE:
 * - Research jobs online and return comprehensive structured data
 * - Extract materials, labor, timeline, financial estimates
 * - Generate full GOSPLAN template for review
 * 
 * OUTPUT:
 * - Full EnhancedJobData with all fields populated
 * - Ready-to-use canvas text in GOSPLAN format
 * - Structured data for direct transfer to Job Board
 * 
 * FAILURE RULE:
 * - Any resolver error → ignore output → continue local compile
 * - Resolver output is ALWAYS visible in canvas (never silent)
 * 
 * CONSTRAINTS:
 * - Does NOT modify PlanCompiler
 * - Does NOT persist any state
 * - Timeout: 10 seconds max (increased for comprehensive research)
 */
object OnlineResolver {
    
    private const val TAG = "OnlineResolver"
    
    // Timeout for online resolution (10 seconds for comprehensive research)
    private const val TIMEOUT_MS = 10000L
    
    // Store last resolved job data for transfer
    private var lastResolvedJobData: EnhancedJobData? = null
    
    /**
     * Get the last resolved job data (for transfer to Job Board)
     */
    fun getLastJobData(): EnhancedJobData? = lastResolvedJobData
    
    /**
     * Clear the last resolved job data
     */
    fun clearLastJobData() {
        lastResolvedJobData = null
    }
    
    /**
     * Set job data directly (for loading from Job Board)
     */
    fun setLastJobData(data: EnhancedJobData) {
        lastResolvedJobData = data
    }
    
    // Backend endpoint for plan resolution
    // In production, this would be a secure backend URL
    // For development, uses the local backend server
    private const val RESOLVE_ENDPOINT_PROD = "https://api.aegisassure.org/v1/plan/resolve"
    private const val RESOLVE_ENDPOINT_DEV = "http://10.0.2.2:3000/api/plan/resolve" // Android emulator localhost
    private const val RESOLVE_ENDPOINT_LOCAL = "http://192.168.8.163:3000/api/plan/resolve" // Real device on local network
    
    // Use LOCAL endpoint for real device testing
    // Switch to RESOLVE_ENDPOINT_DEV for emulator, RESOLVE_ENDPOINT_PROD for release
    private const val RESOLVE_ENDPOINT = RESOLVE_ENDPOINT_LOCAL
    
    /**
     * Check if backend is likely reachable.
     * This is a quick pre-check before attempting resolution.
     */
    private fun isBackendReachable(): Boolean {
        return try {
            val url = java.net.URL(RESOLVE_ENDPOINT)
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.connectTimeout = 1000 // 1 second quick check
            connection.requestMethod = "HEAD"
            connection.responseCode < 500
        } catch (e: Exception) {
            Log.d(TAG, "Backend pre-check failed: ${e.message}")
            false
        }
    }
    
    // ════════════════════════════════════════════════════════════════════
    // ENHANCED JOB DATA (Full structured job from online research)
    // ════════════════════════════════════════════════════════════════════
    
    /**
     * Material item with quantity and cost estimate
     */
    data class MaterialItem(
        val name: String,
        val quantity: String?,
        val unit: String?,
        val estimatedCost: Double?
    )
    
    /**
     * Labor entry with role, hours, and rate
     */
    data class LaborEntry(
        val role: String,
        val hours: Double,
        val rate: Double
    )
    
    /**
     * Work phase with order
     */
    data class WorkPhaseItem(
        val name: String,
        val description: String,
        val order: Int
    )
    
    /**
     * ENHANCED JOB DATA - Complete job structure from online research
     * 
     * Contains all data needed for:
     * - Full GOSPLAN template display
     * - Direct transfer to Job Board
     * - Report and Invoice generation
     */
    data class EnhancedJobData(
        // Job Header
        val jobTitle: String,
        val clientName: String?,
        val location: String?,
        val jobType: String,
        val primaryTrade: String,
        val urgency: String,
        
        // Service Provider Info (for documents)
        val providerName: String? = null,
        val providerBusinessName: String? = null,
        val providerPhone: String? = null,
        val providerEmail: String? = null,
        val providerAddress: String? = null,
        val providerGuildRole: String? = null,
        
        // Client Additional Info
        val clientCompany: String? = null,
        val clientEmail: String? = null,
        val clientPhone: String? = null,
        
        // Scope
        val scope: String,
        val scopeDetails: List<String>,
        
        // Tasks
        val tasks: List<String>,
        
        // Materials
        val materials: List<MaterialItem>,
        
        // Labor
        val labor: List<LaborEntry>,
        val crewSize: Int,
        
        // Timeline
        val estimatedDays: Int,
        val phases: List<WorkPhaseItem>,
        
        // Financial
        val estimatedLaborCost: Double,
        val estimatedMaterialCost: Double,
        val estimatedTotal: Double,
        val depositRequired: String,
        val warranty: String,
        
        // Safety & Code
        val safetyRequirements: List<String>,
        val codeRequirements: List<String>,
        val permitRequired: Boolean,
        val inspectionRequired: Boolean,
        
        // Notes
        val assumptions: List<String>,
        val exclusions: List<String>,
        val notes: List<String>,
        
        // Metadata
        val detectedKeywords: List<String>,
        val tradeClassification: String,
        val researchSources: List<String>
    )

    // ════════════════════════════════════════════════════════════════════
    // LEGACY STRUCTURED SUGGESTION TYPES (for backward compatibility)
    // ════════════════════════════════════════════════════════════════════

    /**
     * Trade classification for the work described
     */
    enum class TradeClassification {
        ELECTRICAL,
        PLUMBING,
        HVAC,
        CARPENTRY,
        MASONRY,
        ROOFING,
        DRYWALL,
        PAINTING,
        FLOORING,
        GENERAL,
        UNKNOWN
    }
    
    /**
     * Standard work phase
     */
    data class WorkPhase(
        val name: String,           // e.g., "Rough-in", "Finish", "Testing"
        val description: String,    // Brief description
        val order: Int              // Sequence order (1, 2, 3...)
    )
    
    /**
     * Safety prerequisite
     */
    data class SafetyPrerequisite(
        val requirement: String,    // e.g., "Permit required", "GFCI protection"
        val category: String,       // e.g., "Permit", "PPE", "Code", "Inspection"
        val mandatory: Boolean      // true = must-have, false = recommended
    )
    
    /**
     * Inspection checkpoint
     */
    data class InspectionCheckpoint(
        val name: String,           // e.g., "Rough-in inspection"
        val phase: String,          // Which phase this follows
        val description: String     // What is inspected
    )
    
    /**
     * STRUCTURED SUGGESTION - The main output of the resolver
     * 
     * This object contains ONLY allowed suggestions that map to plan sections.
     * It never contains pricing, authorization, or invoice data.
     */
    data class StructuredSuggestion(
        // Trade classification
        val trade: TradeClassification,
        val tradeNotes: String?,
        
        // Standard phases for this type of work
        val phases: List<WorkPhase>,
        
        // Safety prerequisites
        val safetyPrerequisites: List<SafetyPrerequisite>,
        
        // Inspection checkpoints (inspection-first scaffolding)
        val inspectionCheckpoints: List<InspectionCheckpoint>,
        
        // Suggested scope clarification
        val scopeSuggestion: String?,
        
        // Suggested assumptions based on input
        val assumptions: List<String>,
        
        // General notes/warnings
        val notes: List<String>,
        
        // Original user input (preserved)
        val originalInput: String
    ) {
        /**
         * Convert suggestion to visible plan text for canvas injection.
         * 
         * OUTPUT FORMAT:
         * # PLAN
         * ## Scope
         * [trade classification + scope suggestion]
         * 
         * ## Assumptions
         * [generated assumptions]
         * 
         * ## Tasks
         * [phases converted to tasks with inspection points]
         * [original user input preserved]
         * 
         * ## Notes
         * [safety prerequisites + general notes]
         */
        fun toCanvasText(): String = buildString {
            appendLine("# PLAN")
            appendLine()
            
            // ══════════════════════════════════════════════════════════════
            // ## Scope
            // ══════════════════════════════════════════════════════════════
            appendLine("## Scope")
            if (trade != TradeClassification.UNKNOWN) {
                appendLine("[${trade.name}] ${tradeNotes ?: "Trade work"}")
            }
            if (!scopeSuggestion.isNullOrBlank()) {
                appendLine(scopeSuggestion)
            }
            appendLine()
            
            // ══════════════════════════════════════════════════════════════
            // ## Assumptions
            // ══════════════════════════════════════════════════════════════
            if (assumptions.isNotEmpty() || safetyPrerequisites.any { it.mandatory }) {
                appendLine("## Assumptions")
                
                // Mandatory safety items as assumptions
                safetyPrerequisites.filter { it.mandatory }.forEach { prereq ->
                    appendLine("- ${prereq.requirement} (${prereq.category})")
                }
                
                // Generated assumptions
                assumptions.forEach { assumption ->
                    appendLine("- $assumption")
                }
                appendLine()
            }
            
            // ══════════════════════════════════════════════════════════════
            // ## Tasks
            // ══════════════════════════════════════════════════════════════
            appendLine("## Tasks")
            
            // Convert phases to tasks with inspection checkpoints
            if (phases.isNotEmpty()) {
                phases.sortedBy { it.order }.forEach { phase ->
                    appendLine("${phase.order}. [${phase.name}] ${phase.description}")
                    
                    // Add inspection checkpoint after phase if applicable
                    inspectionCheckpoints
                        .filter { it.phase.equals(phase.name, ignoreCase = true) }
                        .forEach { checkpoint ->
                            appendLine("   → INSPECT: ${checkpoint.name}")
                        }
                }
                appendLine()
            }
            
            // Preserve original user input as additional tasks
            val userLines = originalInput.trim().lines().filter { it.isNotBlank() }
            if (userLines.isNotEmpty()) {
                appendLine("// User input:")
                userLines.forEach { line ->
                    // Skip if line is already a section header
                    if (!line.trim().startsWith("#")) {
                        appendLine(line)
                    }
                }
                appendLine()
            }
            
            // ══════════════════════════════════════════════════════════════
            // ## Notes
            // ══════════════════════════════════════════════════════════════
            val hasNotes = notes.isNotEmpty() || 
                          safetyPrerequisites.any { !it.mandatory } ||
                          inspectionCheckpoints.isNotEmpty()
            
            if (hasNotes) {
                appendLine("## Notes")
                
                // Recommended safety items
                safetyPrerequisites.filter { !it.mandatory }.forEach { prereq ->
                    appendLine("- [${prereq.category}] ${prereq.requirement}")
                }
                
                // General notes
                notes.forEach { note ->
                    appendLine("- $note")
                }
                
                // Final inspection reminder
                if (inspectionCheckpoints.isNotEmpty()) {
                    appendLine("- Inspection checkpoints marked with → INSPECT")
                }
            }
        }.trimEnd()
    }
    
    // ════════════════════════════════════════════════════════════════════
    // RESOLVE RESULT
    // ════════════════════════════════════════════════════════════════════
    
    /**
     * Resolution result sealed class
     */
    sealed class ResolveResult {
        data class Success(val enhancedText: String) : ResolveResult()
        data class Failure(val reason: String) : ResolveResult()
    }
    
    // ════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ════════════════════════════════════════════════════════════════════
    
    /**
     * Attempt to resolve/enhance plan text using Playwright backend.
     * 
     * FLOW:
     * 1. Send user input to Playwright backend
     * 2. Backend performs comprehensive online research
     * 3. Receive full EnhancedJobData + canvasText
     * 4. Store job data for transfer, return canvas text for display
     * 
     * FAILURE RULE:
     * - Any error → ResolveResult.Failure → caller continues with local compile
     * - Never blocks, never throws to caller
     * 
     * @param inputText Raw user input from the canvas
     * @return ResolveResult.Success with GOSPLAN canvas text, or ResolveResult.Failure
     */
    suspend fun resolve(inputText: String): ResolveResult = withContext(Dispatchers.IO) {
        Log.i(TAG, "Attempting enhanced Playwright resolution for ${inputText.length} chars")
        // #region agent log
        Log.e("DEBUG_H1", """{"hypothesisId":"H1","location":"OnlineResolver.kt:resolve","message":"resolve() called","data":{"inputLength":${inputText.length},"endpoint":"$RESOLVE_ENDPOINT"},"timestamp":${System.currentTimeMillis()}}""")
        // #endregion
        
        // Guard: Empty input
        if (inputText.isBlank()) {
            return@withContext ResolveResult.Failure("Empty input")
        }
        
        try {
            // Wrap in timeout to never block compilation
            val result = withTimeoutOrNull(TIMEOUT_MS) {
                fetchEnhancedJobData(inputText)
            }
            
            when {
                result == null -> {
                    Log.w(TAG, "Playwright resolution timed out after ${TIMEOUT_MS}ms - using local fallback")
                    // #region agent log
                    Log.e("DEBUG_H1", """{"hypothesisId":"H1","location":"OnlineResolver.kt:resolve-timeout","message":"TIMEOUT - using local fallback","data":{"timeoutMs":$TIMEOUT_MS,"endpoint":"$RESOLVE_ENDPOINT"}}""")
                    // #endregion
                    // Use local fallback instead of failing
                    val localJobData = createLocalEnhancedJobData(inputText)
                    lastResolvedJobData = localJobData
                    val canvasText = formatEnhancedJobDataAsCanvasText(localJobData)
                    ResolveResult.Success(canvasText)
                }
                result.first.isNotBlank() -> {
                    Log.i(TAG, "Playwright resolution successful: ${result.first.length} chars canvas, job data stored")
                    // #region agent log
                    Log.e("DEBUG_H3", """{"hypothesisId":"H3","location":"OnlineResolver.kt:resolve-success","message":"Setting lastResolvedJobData","data":{"canvasLength":${result.first.length},"hasJobData":${result.second != null},"tasksCount":${result.second?.tasks?.size ?: -1},"materialsCount":${result.second?.materials?.size ?: -1},"laborCount":${result.second?.labor?.size ?: -1}}}""")
                    // #endregion
                    // Store the job data for transfer
                    lastResolvedJobData = result.second

                    ResolveResult.Success(result.first)
                }
                else -> {
                    Log.w(TAG, "Playwright resolution returned empty result - using local fallback")
                    // #region agent log
                    Log.e("DEBUG_H1", """{"hypothesisId":"H1","location":"OnlineResolver.kt:resolve-empty","message":"Using local fallback","data":{"endpoint":"$RESOLVE_ENDPOINT"}}""")
                    // #endregion
                    // Use local fallback instead of failing
                    val localJobData = createLocalEnhancedJobData(inputText)
                    lastResolvedJobData = localJobData
                    val canvasText = formatEnhancedJobDataAsCanvasText(localJobData)
                    ResolveResult.Success(canvasText)
                }
            }
        } catch (e: Exception) {
            // FAILURE RULE: Any error → use local fallback → continue compile
            Log.w(TAG, "Playwright resolution failed: ${e.message} - using local fallback")
            // Use local fallback instead of failing
            val localJobData = createLocalEnhancedJobData(inputText)
            lastResolvedJobData = localJobData
            val canvasText = formatEnhancedJobDataAsCanvasText(localJobData)
            ResolveResult.Success(canvasText)
        }
    }
    
    /**
     * Fetch enhanced job data from Playwright backend
     * Returns pair of (canvasText, EnhancedJobData)
     */
    private suspend fun fetchEnhancedJobData(inputText: String): Pair<String, EnhancedJobData?> =
        withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "Calling enhanced Playwright backend: $RESOLVE_ENDPOINT")
                
                val url = URL(RESOLVE_ENDPOINT)
                val connection = url.openConnection() as HttpURLConnection
                
                connection.apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                    connectTimeout = TIMEOUT_MS.toInt()
                    readTimeout = TIMEOUT_MS.toInt()
                    doOutput = true
                }
                
                // Build request body
                val requestBody = JSONObject().apply {
                    put("input", inputText)
                }
                
                // Send request
                connection.outputStream.bufferedWriter().use { writer ->
                    writer.write(requestBody.toString())
                }
                
                // Check response
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    Log.w(TAG, "HTTP ${connection.responseCode}: ${connection.responseMessage}")
                    return@withContext Pair("", null)
                }
                
                // Parse response
                val responseText = connection.inputStream.bufferedReader().readText()
                val json = JSONObject(responseText)
                
                // Check if backend succeeded
                val success = json.optBoolean("success", false)
                if (!success) {
                    Log.w(TAG, "Playwright backend returned failure")
                    return@withContext Pair("", null)
                }
                
                // Get canvasText
                val canvasText = json.optString("canvasText", "")
                
                // Parse jobData if available
                val jobData = parseEnhancedJobData(json.optJSONObject("jobData") ?: json.optJSONObject("result"))
                
                // Log detected keywords from Playwright
                val resultObj = json.optJSONObject("result")
                if (resultObj != null) {
                    logPlaywrightKeywords(resultObj, inputText)
                }
                
                Log.i(TAG, "Got enhanced job data: ${jobData?.tasks?.size ?: 0} tasks, ${jobData?.materials?.size ?: 0} materials")

                Pair(canvasText, jobData)
                
            } catch (e: Exception) {
                Log.w(TAG, "Enhanced Playwright request failed: ${e.message}")
                Pair("", null)
            }
        }
    
    /**
     * Parse EnhancedJobData from JSON response
     */
    private fun parseEnhancedJobData(json: JSONObject?): EnhancedJobData? {
        // #region agent log
        Log.e("DEBUG_H2", """{"hypothesisId":"H2","location":"OnlineResolver.kt:parseEnhancedJobData","message":"Parsing JSON","data":{"jsonNull":${json == null},"jsonKeys":"${json?.keys()?.asSequence()?.toList()?.joinToString(",") ?: "null"}"}}""")
        // #endregion
        if (json == null) return null
        
        try {
            // Parse materials
            val materialsJson = json.optJSONArray("materials") ?: JSONArray()
            val materials = (0 until materialsJson.length()).mapNotNull { i ->
                val m = materialsJson.optJSONObject(i) ?: return@mapNotNull null
                MaterialItem(
                    name = m.optString("name", ""),
                    quantity = m.optString("quantity", null),
                    unit = m.optString("unit", null),
                    estimatedCost = if (m.has("estimatedCost")) m.optDouble("estimatedCost") else null
                )
            }
            
            // Parse labor
            val laborJson = json.optJSONArray("labor") ?: JSONArray()
            val labor = (0 until laborJson.length()).mapNotNull { i ->
                val l = laborJson.optJSONObject(i) ?: return@mapNotNull null
                LaborEntry(
                    role = l.optString("role", ""),
                    hours = l.optDouble("hours", 0.0),
                    rate = l.optDouble("rate", 0.0)
                )
            }
            
            // Parse phases
            val phasesJson = json.optJSONArray("phases") ?: JSONArray()
            val phases = (0 until phasesJson.length()).mapNotNull { i ->
                val p = phasesJson.optJSONObject(i) ?: return@mapNotNull null
                WorkPhaseItem(
                    name = p.optString("name", ""),
                    description = p.optString("description", ""),
                    order = p.optInt("order", i + 1)
                )
            }
            
            // Parse arrays
            fun parseStringArray(key: String): List<String> {
                val arr = json.optJSONArray(key) ?: return emptyList()
                return (0 until arr.length()).map { arr.optString(it, "") }.filter { it.isNotBlank() }
            }
            
            return EnhancedJobData(
                jobTitle = json.optString("jobTitle", ""),
                clientName = json.optString("clientName", null),
                location = json.optString("location", null),
                jobType = json.optString("jobType", ""),
                primaryTrade = json.optString("primaryTrade", "GENERAL"),
                urgency = json.optString("urgency", "moderate"),
                scope = json.optString("scope", ""),
                scopeDetails = parseStringArray("scopeDetails"),
                tasks = parseStringArray("tasks"),
                materials = materials,
                labor = labor,
                crewSize = json.optInt("crewSize", 1),
                estimatedDays = json.optInt("estimatedDays", 1),
                phases = phases,
                estimatedLaborCost = json.optDouble("estimatedLaborCost", 0.0),
                estimatedMaterialCost = json.optDouble("estimatedMaterialCost", 0.0),
                estimatedTotal = json.optDouble("estimatedTotal", 0.0),
                depositRequired = json.optString("depositRequired", "50% on approval"),
                warranty = json.optString("warranty", "1 year workmanship"),
                safetyRequirements = parseStringArray("safetyRequirements"),
                codeRequirements = parseStringArray("codeRequirements"),
                permitRequired = json.optBoolean("permitRequired", false),
                inspectionRequired = json.optBoolean("inspectionRequired", false),
                assumptions = parseStringArray("assumptions"),
                exclusions = parseStringArray("exclusions"),
                notes = parseStringArray("notes"),
                detectedKeywords = parseStringArray("detectedKeywords"),
                tradeClassification = json.optString("tradeClassification", "GENERAL"),
                researchSources = parseStringArray("researchSources")
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse EnhancedJobData: ${e.message}")
            return null
        }
    }
    
    /**
     * Fetch canvas text from Playwright backend.
     * 
     * Backend response format:
     * {
     *   "success": true/false,
     *   "result": { scope, tasks, assumptions, notes, detectedKeywords },
     *   "canvasText": "# PLAN\n..."
     * }
     * 
     * Returns canvasText on success, empty string on failure.
     * Also logs detected keywords for analysis (TEST ⧉ only).
     */
    private suspend fun fetchPlaywrightCanvasText(inputText: String): String = 
        withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "Calling Playwright backend: $RESOLVE_ENDPOINT")
                
                val url = URL(RESOLVE_ENDPOINT)
                val connection = url.openConnection() as HttpURLConnection
                
                connection.apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                    connectTimeout = TIMEOUT_MS.toInt()
                    readTimeout = TIMEOUT_MS.toInt()
                    doOutput = true
                }
                
                // Build request body - just the input text
                val requestBody = JSONObject().apply {
                    put("input", inputText)
                }
                
                // Send request
                connection.outputStream.bufferedWriter().use { writer ->
                    writer.write(requestBody.toString())
                }
                
                // Check response
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    Log.w(TAG, "HTTP ${connection.responseCode}: ${connection.responseMessage}")
                    return@withContext ""
                }
                
                // Parse response
                val responseText = connection.inputStream.bufferedReader().readText()
                val json = JSONObject(responseText)
                
                // Check if backend succeeded
                val success = json.optBoolean("success", false)
                if (!success) {
                    Log.w(TAG, "Playwright backend returned failure")
                    return@withContext ""
                }
                
                // Log detected keywords from Playwright (TEST ⧉ only)
                val resultObj = json.optJSONObject("result")
                if (resultObj != null) {
                    logPlaywrightKeywords(resultObj, inputText)
                }
                
                // Get canvasText
                val canvasText = json.optString("canvasText", "")
                if (canvasText.isNotBlank()) {
                    Log.i(TAG, "Got canvasText from Playwright (${canvasText.length} chars)")
                    return@withContext canvasText
                }
                
                Log.w(TAG, "Playwright backend returned no canvasText")
                ""
                
            } catch (e: Exception) {
                Log.w(TAG, "Playwright request failed: ${e.message}")
                ""
            }
        }
    
    /**
     * Log keywords detected by Playwright for analysis.
     * 
     * RULES:
     * - Only logs during TEST ⧉ runs (this function is only called from Playwright path)
     * - Does not auto-learn or update maps
     * - Append-only to database
     * - No effect on runtime behavior
     * 
     * FIELDS:
     * - keyword
     * - guessed_trade (from scope)
     * - source = 'test_button_playwright'
     * - timestamp
     */
    private fun logPlaywrightKeywords(resultObj: JSONObject, inputText: String) {
        try {
            // Extract detected keywords from Playwright result
            val keywordsArray = resultObj.optJSONArray("detectedKeywords")
            if (keywordsArray == null || keywordsArray.length() == 0) {
                Log.d(TAG, "No detected keywords from Playwright to log")
                return
            }
            
            // Extract trade guess from scope (e.g., "[ELECTRICAL] ..." -> "ELECTRICAL")
            val scope = resultObj.optString("scope", "")
            val tradeGuess = extractTradeFromScope(scope)
            
            // Log each detected keyword
            val keywords = mutableListOf<String>()
            for (i in 0 until keywordsArray.length()) {
                val keyword = keywordsArray.optString(i, "")
                if (keyword.isNotBlank()) {
                    keywords.add(keyword)
                }
            }
            
            if (keywords.isNotEmpty()) {
                Log.i(TAG, "Logging ${keywords.size} keywords from Playwright: $keywords (trade: $tradeGuess)")
                
                // Log via KeywordObserver with source = test_button_playwright
                keywords.forEach { keyword ->
                    KeywordObserver.logPlaywrightKeyword(
                        keyword = keyword,
                        detectedIn = inputText,
                        tradeGuess = tradeGuess
                    )
                }
            }
        } catch (e: Exception) {
            // Never fail - logging is observational only
            Log.w(TAG, "Failed to log Playwright keywords: ${e.message}")
        }
    }
    
    /**
     * Extract trade classification from scope string.
     * e.g., "[ELECTRICAL] install outlet" -> "ELECTRICAL"
     */
    private fun extractTradeFromScope(scope: String): String {
        val match = Regex("\\[([A-Z]+)\\]").find(scope)
        return match?.groupValues?.getOrNull(1) ?: "UNKNOWN"
    }
    
    // ════════════════════════════════════════════════════════════════════
    // ONLINE FETCH (with stubbed fallback)
    // ════════════════════════════════════════════════════════════════════
    
    /**
     * Fetch structured suggestion from online service (Playwright backend).
     * 
     * REQUEST:
     * POST /api/plan/resolve
     * Content-Type: application/json
     * { "input": "<user text>", "mode": "structured" }
     * 
     * RESPONSE:
     * {
     *   "success": true,
     *   "trade": "ELECTRICAL",
     *   "tradeNotes": "...",
     *   "phases": [...],
     *   "safetyPrerequisites": [...],
     *   "inspectionCheckpoints": [...],
     *   "scopeSuggestion": "...",
     *   "assumptions": [...],
     *   "notes": [...],
     *   "canvasText": "# PLAN\n..."  // Ready-to-use canvas text
     * }
     * 
     * On HTTP failure → falls back to stubbed local suggestion
     */
    private suspend fun fetchStructuredSuggestion(inputText: String): StructuredSuggestion = 
        withContext(Dispatchers.IO) {
            try {
                Log.i(TAG, "Attempting Playwright-based resolution via: $RESOLVE_ENDPOINT")
                
                val url = URL(RESOLVE_ENDPOINT)
                val connection = url.openConnection() as HttpURLConnection
                
                connection.apply {
                    requestMethod = "POST"
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                    connectTimeout = TIMEOUT_MS.toInt()
                    readTimeout = TIMEOUT_MS.toInt()
                    doOutput = true
                }
                
                // Build request body
                val requestBody = JSONObject().apply {
                    put("input", inputText)
                    put("mode", "structured")
                }
                
                // Send request
                connection.outputStream.bufferedWriter().use { writer ->
                    writer.write(requestBody.toString())
                }
                
                // Check response
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    Log.w(TAG, "HTTP ${connection.responseCode}: ${connection.responseMessage}")
                    return@withContext createStubbedSuggestion(inputText)
                }
                
                // Parse response
                val responseText = connection.inputStream.bufferedReader().readText()
                val json = JSONObject(responseText)
                
                // Check if backend succeeded
                val success = json.optBoolean("success", false)
                if (!success) {
                    val error = json.optString("error", "Unknown error")
                    Log.w(TAG, "Backend returned failure: $error")
                    return@withContext createStubbedSuggestion(inputText)
                }
                
                Log.i(TAG, "Playwright resolution succeeded, parsing response")
                parseStructuredSuggestion(json, inputText)
                
            } catch (e: Exception) {
                Log.d(TAG, "HTTP request failed, using stubbed suggestion: ${e.message}")
                createStubbedSuggestion(inputText)
            }
        }
    
    /**
     * Parse JSON response into StructuredSuggestion
     */
    private fun parseStructuredSuggestion(json: JSONObject, originalInput: String): StructuredSuggestion {
        // Parse trade classification
        val tradeStr = json.optString("trade", "UNKNOWN")
        val trade = try {
            TradeClassification.valueOf(tradeStr.uppercase())
        } catch (e: Exception) {
            TradeClassification.UNKNOWN
        }
        
        // Parse phases
        val phasesJson = json.optJSONArray("phases") ?: JSONArray()
        val phases = (0 until phasesJson.length()).mapNotNull { i ->
            val p = phasesJson.optJSONObject(i) ?: return@mapNotNull null
            WorkPhase(
                name = p.optString("name", ""),
                description = p.optString("description", ""),
                order = p.optInt("order", i + 1)
            )
        }
        
        // Parse safety prerequisites
        val safetyJson = json.optJSONArray("safetyPrerequisites") ?: JSONArray()
        val safety = (0 until safetyJson.length()).mapNotNull { i ->
            val s = safetyJson.optJSONObject(i) ?: return@mapNotNull null
            SafetyPrerequisite(
                requirement = s.optString("requirement", ""),
                category = s.optString("category", "General"),
                mandatory = s.optBoolean("mandatory", false)
            )
        }
        
        // Parse inspection checkpoints
        val inspectJson = json.optJSONArray("inspectionCheckpoints") ?: JSONArray()
        val inspections = (0 until inspectJson.length()).mapNotNull { i ->
            val c = inspectJson.optJSONObject(i) ?: return@mapNotNull null
            InspectionCheckpoint(
                name = c.optString("name", ""),
                phase = c.optString("phase", ""),
                description = c.optString("description", "")
            )
        }
        
        // Parse assumptions
        val assumptionsJson = json.optJSONArray("assumptions") ?: JSONArray()
        val assumptions = (0 until assumptionsJson.length()).map { i ->
            assumptionsJson.optString(i, "")
        }.filter { it.isNotBlank() }
        
        // Parse notes
        val notesJson = json.optJSONArray("notes") ?: JSONArray()
        val notes = (0 until notesJson.length()).map { i ->
            notesJson.optString(i, "")
        }.filter { it.isNotBlank() }
        
        return StructuredSuggestion(
            trade = trade,
            tradeNotes = json.optString("tradeNotes", null),
            phases = phases,
            safetyPrerequisites = safety,
            inspectionCheckpoints = inspections,
            scopeSuggestion = json.optString("scopeSuggestion", null),
            assumptions = assumptions,
            notes = notes,
            originalInput = originalInput
        )
    }
    
    // ════════════════════════════════════════════════════════════════════
    // STUBBED IMPLEMENTATION (for testing/offline)
    // ════════════════════════════════════════════════════════════════════
    
    /**
     * Create a stubbed suggestion based on pattern analysis.
     * 
     * This is a deterministic local fallback that provides basic structure
     * when the online service is unavailable. It uses pattern matching to
     * infer trade type and generate appropriate scaffolding.
     * 
     * STUBBED - Can be mocked for testing.
     * 
     * KEYWORD OBSERVATION:
     * During TEST ⧉ compile, this function logs unknown keywords
     * to the keyword_observations table for later analysis.
     */
    private fun createStubbedSuggestion(inputText: String): StructuredSuggestion {
        val lowerInput = inputText.lowercase()
        
        // Detect trade from keywords
        val (trade, tradeNotes) = detectTrade(lowerInput)
        
        // ════════════════════════════════════════════════════════════════
        // KEYWORD OBSERVATION: Log unknown keywords for analysis
        // This is TEST ⧉ only - does not affect production behavior
        // ════════════════════════════════════════════════════════════════
        KeywordObserver.observeKeywords(
            inputText = inputText,
            tradeGuess = trade.name,
            sourceMode = "planner_test_compile"
        )
        
        // Generate standard phases for the detected trade
        val phases = generateStandardPhases(trade)
        
        // Generate safety prerequisites
        val safetyPrerequisites = generateSafetyPrerequisites(trade)
        
        // Generate inspection checkpoints
        val inspectionCheckpoints = generateInspectionCheckpoints(trade, phases)
        
        // Generate assumptions
        val assumptions = generateAssumptions(trade, lowerInput)
        
        // Generate notes
        val notes = generateNotes(trade)
        
        return StructuredSuggestion(
            trade = trade,
            tradeNotes = tradeNotes,
            phases = phases,
            safetyPrerequisites = safetyPrerequisites,
            inspectionCheckpoints = inspectionCheckpoints,
            scopeSuggestion = generateScopeSuggestion(trade, inputText),
            assumptions = assumptions,
            notes = notes,
            originalInput = inputText
        )
    }
    
    /**
     * Detect trade classification from input keywords
     */
    private fun detectTrade(lowerInput: String): Pair<TradeClassification, String?> {
        return when {
            // Electrical keywords
            lowerInput.containsAny("wire", "wiring", "electrical", "outlet", "switch", 
                "circuit", "breaker", "panel", "voltage", "amp", "conduit", "motherboard") ->
                TradeClassification.ELECTRICAL to "Electrical work - requires licensed electrician"
            
            // Plumbing keywords
            lowerInput.containsAny("pipe", "plumb", "drain", "faucet", "toilet", 
                "water heater", "sewage", "valve", "fitting") ->
                TradeClassification.PLUMBING to "Plumbing work - requires licensed plumber"
            
            // HVAC keywords
            lowerInput.containsAny("hvac", "heating", "cooling", "furnace", "ac", 
                "air condition", "duct", "ventilation", "thermostat") ->
                TradeClassification.HVAC to "HVAC work - requires HVAC technician"
            
            // Masonry keywords
            lowerInput.containsAny("brick", "block", "concrete", "mortar", "stone", 
                "chimney", "foundation", "masonry", "fireplace") ->
                TradeClassification.MASONRY to "Masonry work"
            
            // Carpentry keywords
            lowerInput.containsAny("wood", "frame", "framing", "cabinet", "trim", 
                "door", "window", "deck", "stair", "carpenter") ->
                TradeClassification.CARPENTRY to "Carpentry/woodworking"
            
            // Roofing keywords
            lowerInput.containsAny("roof", "shingle", "gutter", "flashing", "soffit") ->
                TradeClassification.ROOFING to "Roofing work"
            
            // Drywall keywords
            lowerInput.containsAny("drywall", "sheetrock", "plaster", "wall board") ->
                TradeClassification.DRYWALL to "Drywall/finishing"
            
            // Painting keywords
            lowerInput.containsAny("paint", "primer", "stain", "finish", "coat") ->
                TradeClassification.PAINTING to "Painting/finishing"
            
            // Flooring keywords
            lowerInput.containsAny("floor", "tile", "carpet", "hardwood", "laminate", "vinyl") ->
                TradeClassification.FLOORING to "Flooring installation"
            
            // General construction
            lowerInput.containsAny("build", "install", "repair", "replace", "construct") ->
                TradeClassification.GENERAL to "General construction"
            
            else -> TradeClassification.UNKNOWN to null
        }
    }
    
    /**
     * Generate standard phases for a trade
     */
    private fun generateStandardPhases(trade: TradeClassification): List<WorkPhase> {
        return when (trade) {
            TradeClassification.ELECTRICAL -> listOf(
                WorkPhase("Planning", "Review scope, pull permits if required", 1),
                WorkPhase("Rough-in", "Run cables, install boxes", 2),
                WorkPhase("Inspection", "Rough-in inspection before close-up", 3),
                WorkPhase("Finish", "Install devices, covers, fixtures", 4),
                WorkPhase("Testing", "Test circuits, verify operation", 5)
            )
            TradeClassification.PLUMBING -> listOf(
                WorkPhase("Planning", "Review scope, pull permits if required", 1),
                WorkPhase("Rough-in", "Run supply and drain lines", 2),
                WorkPhase("Pressure Test", "Test for leaks under pressure", 3),
                WorkPhase("Inspection", "Rough-in inspection", 4),
                WorkPhase("Finish", "Install fixtures and trim", 5)
            )
            TradeClassification.HVAC -> listOf(
                WorkPhase("Planning", "Load calculation, equipment sizing", 1),
                WorkPhase("Rough-in", "Install ductwork, run lines", 2),
                WorkPhase("Equipment", "Install equipment units", 3),
                WorkPhase("Commissioning", "Start-up and balance", 4),
                WorkPhase("Testing", "Verify operation and efficiency", 5)
            )
            TradeClassification.MASONRY -> listOf(
                WorkPhase("Prep", "Layout, material staging", 1),
                WorkPhase("Foundation", "Base/footing work if needed", 2),
                WorkPhase("Build", "Lay masonry units", 3),
                WorkPhase("Cure", "Allow proper curing time", 4),
                WorkPhase("Finish", "Pointing, cleaning, sealing", 5)
            )
            TradeClassification.CARPENTRY -> listOf(
                WorkPhase("Layout", "Measure and mark", 1),
                WorkPhase("Cut", "Cut materials to size", 2),
                WorkPhase("Assemble", "Build/install structure", 3),
                WorkPhase("Finish", "Sand, fill, prep for finish", 4)
            )
            else -> listOf(
                WorkPhase("Planning", "Review scope and requirements", 1),
                WorkPhase("Preparation", "Gather materials, prep area", 2),
                WorkPhase("Execution", "Perform main work", 3),
                WorkPhase("Cleanup", "Clean area, remove debris", 4),
                WorkPhase("Verification", "Verify work completion", 5)
            )
        }
    }
    
    /**
     * Generate safety prerequisites for a trade
     */
    private fun generateSafetyPrerequisites(trade: TradeClassification): List<SafetyPrerequisite> {
        val common = listOf(
            SafetyPrerequisite("PPE as required for task", "PPE", false)
        )
        
        val tradeSpecific = when (trade) {
            TradeClassification.ELECTRICAL -> listOf(
                SafetyPrerequisite("Electrical permit required for new circuits", "Permit", true),
                SafetyPrerequisite("De-energize and lock-out before work", "Safety", true),
                SafetyPrerequisite("GFCI protection in wet locations", "Code", true),
                SafetyPrerequisite("Arc-flash PPE if working on live equipment", "PPE", false)
            )
            TradeClassification.PLUMBING -> listOf(
                SafetyPrerequisite("Plumbing permit may be required", "Permit", true),
                SafetyPrerequisite("Shut off water supply before work", "Safety", true),
                SafetyPrerequisite("Pressure test before close-up", "Inspection", true)
            )
            TradeClassification.HVAC -> listOf(
                SafetyPrerequisite("HVAC permit may be required", "Permit", true),
                SafetyPrerequisite("EPA certification for refrigerant handling", "Code", true),
                SafetyPrerequisite("Disconnect power before equipment work", "Safety", true)
            )
            TradeClassification.MASONRY -> listOf(
                SafetyPrerequisite("Structural review for load-bearing work", "Code", true),
                SafetyPrerequisite("Fall protection above 6 feet", "Safety", true)
            )
            TradeClassification.ROOFING -> listOf(
                SafetyPrerequisite("Fall protection required", "Safety", true),
                SafetyPrerequisite("Building permit may be required", "Permit", false)
            )
            else -> emptyList()
        }
        
        return common + tradeSpecific
    }
    
    /**
     * Generate inspection checkpoints
     */
    private fun generateInspectionCheckpoints(
        trade: TradeClassification, 
        phases: List<WorkPhase>
    ): List<InspectionCheckpoint> {
        return when (trade) {
            TradeClassification.ELECTRICAL -> listOf(
                InspectionCheckpoint(
                    "Rough-in inspection",
                    "Rough-in",
                    "Verify wire sizes, box fill, grounding before close-up"
                ),
                InspectionCheckpoint(
                    "Final inspection",
                    "Testing",
                    "Verify all devices, GFCI/AFCI operation, labeling"
                )
            )
            TradeClassification.PLUMBING -> listOf(
                InspectionCheckpoint(
                    "Rough-in inspection",
                    "Pressure Test",
                    "Verify pipe sizes, venting, pressure test results"
                ),
                InspectionCheckpoint(
                    "Final inspection",
                    "Finish",
                    "Verify fixture installation, no leaks"
                )
            )
            TradeClassification.HVAC -> listOf(
                InspectionCheckpoint(
                    "Rough-in inspection",
                    "Rough-in",
                    "Verify ductwork, line sets, condensate"
                ),
                InspectionCheckpoint(
                    "Final inspection",
                    "Testing",
                    "Verify equipment operation, airflow balance"
                )
            )
            else -> if (phases.any { it.name.contains("Inspection", ignoreCase = true) }) {
                listOf(
                    InspectionCheckpoint(
                        "Progress inspection",
                        "Execution",
                        "Verify work meets requirements before proceeding"
                    )
                )
            } else {
                emptyList()
            }
        }
    }
    
    /**
     * Generate assumptions based on trade and input
     */
    private fun generateAssumptions(trade: TradeClassification, lowerInput: String): List<String> {
        val assumptions = mutableListOf<String>()
        
        // Trade-specific assumptions
        when (trade) {
            TradeClassification.ELECTRICAL -> {
                assumptions.add("Existing panel has capacity for new circuits")
                assumptions.add("Access to all work areas is available")
            }
            TradeClassification.PLUMBING -> {
                assumptions.add("Existing supply pressure is adequate")
                assumptions.add("Drain access is available")
            }
            TradeClassification.HVAC -> {
                assumptions.add("Existing ductwork is in acceptable condition")
                assumptions.add("Equipment location has adequate clearance")
            }
            TradeClassification.MASONRY -> {
                assumptions.add("Foundation/base is structurally sound")
                assumptions.add("Weather conditions permit masonry work")
            }
            else -> {
                assumptions.add("Work area is accessible")
                assumptions.add("Required utilities are available")
            }
        }
        
        // Input-based assumptions
        if (lowerInput.contains("new") || lowerInput.contains("install")) {
            assumptions.add("This is new installation, not repair/replacement")
        }
        if (lowerInput.contains("repair") || lowerInput.contains("fix")) {
            assumptions.add("Replacement parts are available")
        }
        
        return assumptions
    }
    
    /**
     * Generate general notes
     */
    private fun generateNotes(trade: TradeClassification): List<String> {
        return when (trade) {
            TradeClassification.ELECTRICAL -> listOf(
                "All electrical work must comply with NEC and local codes",
                "Homeowner should know main breaker location"
            )
            TradeClassification.PLUMBING -> listOf(
                "All plumbing work must comply with local plumbing codes",
                "Know location of main water shut-off"
            )
            TradeClassification.HVAC -> listOf(
                "System should be tested in both heating and cooling modes",
                "Filter replacement schedule should be established"
            )
            else -> listOf(
                "Verify all work meets applicable codes and standards"
            )
        }
    }
    
    /**
     * Generate scope suggestion
     */
    private fun generateScopeSuggestion(trade: TradeClassification, inputText: String): String? {
        val trimmed = inputText.trim()
        return when {
            trimmed.length < 20 -> "Consider expanding scope description with specific details"
            trade == TradeClassification.UNKNOWN -> "Specify trade type for better task organization"
            else -> null
        }
    }
    
    // ════════════════════════════════════════════════════════════════════
    // LOCAL FALLBACK - Creates EnhancedJobData when backend is unavailable
    // ════════════════════════════════════════════════════════════════════
    
    /**
     * Creates EnhancedJobData locally when the backend is unavailable.
     * Uses trade detection and knowledge base to generate comprehensive job data.
     */
    private fun createLocalEnhancedJobData(inputText: String): EnhancedJobData {
        val lowerInput = inputText.lowercase()
        val (trade, tradeNotes) = detectTrade(lowerInput)
        
        Log.i(TAG, "Creating local EnhancedJobData: trade=$trade")
        
        // Extract client name from input
        val clientName = extractClientName(inputText)
        
        // Extract location from input
        val location = extractLocation(inputText)
        
        // Generate job title
        val jobTitle = generateJobTitle(inputText, trade, clientName, location)
        
        // Detect urgency
        val urgency = when {
            lowerInput.containsAny("urgent", "emergency", "asap", "immediately") -> "urgent"
            lowerInput.containsAny("soon", "quickly", "before") -> "high"
            lowerInput.containsAny("when available", "no rush", "flexible") -> "low"
            else -> "moderate"
        }
        
        // Generate tasks from phases
        val phases = generateStandardPhases(trade)
        val tasks = phases.map { "${it.order}. [${it.name}] ${it.description}" }.toMutableList()
        tasks.add("Final walkthrough with client")
        tasks.add("Client sign-off on completed work")
        tasks.add("Generate invoice and close job")
        
        // Generate materials based on trade
        val materials = generateMaterialsForTrade(trade)
        
        // Generate labor based on trade
        val labor = generateLaborForTrade(trade)
        val crewSize = when (trade) {
            TradeClassification.MASONRY -> 2
            TradeClassification.ELECTRICAL -> 1
            TradeClassification.PLUMBING -> 1
            TradeClassification.CARPENTRY -> 2
            else -> 2
        }
        
        // Estimate days
        val estimatedDays = when (trade) {
            TradeClassification.MASONRY -> 5
            TradeClassification.ELECTRICAL -> 2
            TradeClassification.PLUMBING -> 2
            TradeClassification.CARPENTRY -> 3
            else -> 2
        }
        
        // Calculate costs
        val laborCost = labor.sumOf { it.hours * it.rate }
        val materialCost = materials.sumOf { (it.estimatedCost ?: 0.0) * (it.quantity?.toDoubleOrNull() ?: 1.0) }
        
        // Get safety requirements
        val safety = generateSafetyPrerequisites(trade)
        
        return EnhancedJobData(
            jobTitle = jobTitle,
            clientName = clientName,
            location = location,
            jobType = "${trade.name} / General",
            primaryTrade = trade.name,
            urgency = urgency,
            scope = "${trade.name} work: $inputText",
            scopeDetails = listOf(
                "Perform ${trade.name.lowercase()} work as specified",
                "Ensure all work meets applicable codes",
                "Clean up work area upon completion"
            ),
            tasks = tasks,
            materials = materials,
            labor = labor,
            crewSize = crewSize,
            estimatedDays = estimatedDays,
            phases = phases.map { WorkPhaseItem(it.name, it.description, it.order) },
            estimatedLaborCost = laborCost,
            estimatedMaterialCost = materialCost,
            estimatedTotal = laborCost + materialCost,
            depositRequired = "50% on approval",
            warranty = "1 year workmanship",
            safetyRequirements = safety.filter { it.mandatory }.map { it.requirement },
            codeRequirements = generateCodeRequirements(trade),
            permitRequired = trade in listOf(TradeClassification.ELECTRICAL, TradeClassification.PLUMBING, TradeClassification.MASONRY),
            inspectionRequired = trade in listOf(TradeClassification.ELECTRICAL, TradeClassification.PLUMBING, TradeClassification.MASONRY),
            assumptions = generateAssumptions(trade, lowerInput),
            exclusions = listOf("Work not specifically described in scope", "Permit fees (billed separately)"),
            notes = listOf("Generated locally - no backend connection", "Trade: ${trade.name}"),
            detectedKeywords = inputText.split(" ").filter { it.length > 4 }.take(10),
            tradeClassification = trade.name,
            researchSources = listOf("Local Knowledge Base")
        )
    }
    
    /**
     * Extract client name from input text
     */
    private fun extractClientName(input: String): String? {
        // Pattern: "for [Name]" or "[Name] is requesting"
        val forMatch = Regex("""(?:for|client[:\s]+)\s*([A-Z][a-z]+(?:\s+[A-Z][a-z]+)?)""", RegexOption.IGNORE_CASE).find(input)
        if (forMatch != null) return forMatch.groupValues[1]
        
        val requestingMatch = Regex("""([A-Z][a-z]+)\s+(?:is\s+)?requesting""").find(input)
        if (requestingMatch != null) return requestingMatch.groupValues[1]
        
        return null
    }
    
    /**
     * Extract location from input text
     */
    private fun extractLocation(input: String): String? {
        // Pattern: "in [Location]" or "at [Address]"
        val inMatch = Regex("""(?:in|at)\s+(?:the\s+)?([A-Z][a-zA-Z\s]+(?:,\s*[A-Z]{2})?)""").find(input)
        if (inMatch != null) return inMatch.groupValues[1].trim()
        
        return null
    }
    
    /**
     * Generate job title from input
     */
    private fun generateJobTitle(input: String, trade: TradeClassification, client: String?, location: String?): String {
        var title = "${trade.name.lowercase().replaceFirstChar { it.uppercase() }} Work"
        if (client != null) title = "$title for $client"
        if (location != null) title = "$title - $location"
        return title.take(100)
    }
    
    /**
     * Generate materials list based on trade
     */
    private fun generateMaterialsForTrade(trade: TradeClassification): List<MaterialItem> {
        return when (trade) {
            TradeClassification.PLUMBING -> listOf(
                MaterialItem("PEX tubing", "50", "ft", 1.50),
                MaterialItem("Fittings assortment", "1", "lot", 75.00),
                MaterialItem("Shut-off valves", "4", "ea", 15.00),
                MaterialItem("P-traps", "2", "ea", 12.00),
                MaterialItem("Teflon tape", "2", "rolls", 3.00),
                MaterialItem("Pipe hangers", "1", "box", 25.00)
            )
            TradeClassification.ELECTRICAL -> listOf(
                MaterialItem("Romex 12/2", "100", "ft", 0.75),
                MaterialItem("Wire nuts", "1", "box", 8.00),
                MaterialItem("Outlet boxes", "6", "ea", 2.50),
                MaterialItem("GFCI outlets", "2", "ea", 18.00),
                MaterialItem("Standard outlets", "4", "ea", 3.00),
                MaterialItem("Switches", "2", "ea", 4.00)
            )
            TradeClassification.MASONRY -> listOf(
                MaterialItem("Fire brick", "200", "ea", 1.50),
                MaterialItem("Fireclay mortar", "5", "bags", 15.00),
                MaterialItem("Common brick", "100", "ea", 0.75),
                MaterialItem("Type S mortar", "5", "bags", 12.00),
                MaterialItem("Sand", "1", "cu yd", 50.00)
            )
            TradeClassification.CARPENTRY -> listOf(
                MaterialItem("Lumber 2x4", "20", "ea", 5.00),
                MaterialItem("Plywood", "4", "sheets", 45.00),
                MaterialItem("Screws", "2", "box", 12.00),
                MaterialItem("Wood glue", "1", "bottle", 8.00)
            )
            else -> listOf(
                MaterialItem("Various materials", "1", "lot", 200.00)
            )
        }
    }
    
    /**
     * Generate labor entries based on trade
     */
    private fun generateLaborForTrade(trade: TradeClassification): List<LaborEntry> {
        return when (trade) {
            TradeClassification.PLUMBING -> listOf(
                LaborEntry("Licensed Plumber", 16.0, 80.00),
                LaborEntry("Apprentice", 16.0, 40.00)
            )
            TradeClassification.ELECTRICAL -> listOf(
                LaborEntry("Licensed Electrician", 16.0, 85.00),
                LaborEntry("Apprentice", 8.0, 40.00)
            )
            TradeClassification.MASONRY -> listOf(
                LaborEntry("Lead Mason", 40.0, 75.00),
                LaborEntry("Mason Helper", 40.0, 45.00)
            )
            TradeClassification.CARPENTRY -> listOf(
                LaborEntry("Carpenter", 24.0, 65.00),
                LaborEntry("Helper", 24.0, 35.00)
            )
            else -> listOf(
                LaborEntry("Tradesperson", 16.0, 60.00),
                LaborEntry("Helper", 16.0, 35.00)
            )
        }
    }
    
    /**
     * Generate code requirements based on trade
     */
    private fun generateCodeRequirements(trade: TradeClassification): List<String> {
        return when (trade) {
            TradeClassification.PLUMBING -> listOf(
                "Proper venting per DWV code",
                "Backflow prevention requirements",
                "Minimum fixture unit calculations"
            )
            TradeClassification.ELECTRICAL -> listOf(
                "GFCI protection in wet locations",
                "AFCI protection in bedrooms",
                "Proper wire gauge for circuit amperage"
            )
            TradeClassification.MASONRY -> listOf(
                "Clearance to combustibles",
                "Chimney height requirements",
                "Flue sizing per appliance BTU"
            )
            else -> listOf(
                "Verify applicable codes for work type"
            )
        }
    }
    
    /**
     * Format EnhancedJobData as GOSPLAN canvas text
     */
    private fun formatEnhancedJobDataAsCanvasText(job: EnhancedJobData): String {
        return buildString {
            appendLine("# PLAN")
            appendLine()
            appendLine("## JobHeader")
            appendLine("Job Title:      ${job.jobTitle}")
            appendLine("Client:         ${job.clientName ?: "[Client Name]"}")
            appendLine("Location:       ${job.location ?: "[Location]"}")
            appendLine("Job Type:       ${job.jobType}")
            appendLine("Primary Trade:  ${job.primaryTrade}")
            appendLine("Urgency:        ${job.urgency.replaceFirstChar { it.uppercase() }}")
            appendLine("Crew Size:      ${job.crewSize} workers")
            appendLine("Est. Duration:  ${job.estimatedDays} days")
            appendLine()
            appendLine("## Scope")
            appendLine(job.scope)
            job.scopeDetails.forEach { appendLine("- $it") }
            appendLine()
            appendLine("## Tasks")
            job.tasks.forEach { appendLine(it) }
            appendLine()
            appendLine("## Materials")
            job.materials.forEach { mat ->
                val qty = mat.quantity ?: "1"
                val unit = mat.unit ?: "ea"
                val cost = mat.estimatedCost?.let { " @ \$${String.format("%.2f", it)}" } ?: ""
                appendLine("- $qty $unit ${mat.name}$cost")
            }
            appendLine()
            appendLine("## Labor")
            job.labor.forEach { lab ->
                appendLine("- ${lab.hours.toInt()}h ${lab.role} @ \$${String.format("%.2f", lab.rate)}/hr")
            }
            appendLine()
            appendLine("## Financial")
            appendLine("Est. Labor:     \$${String.format("%.2f", job.estimatedLaborCost)}")
            appendLine("Est. Materials: \$${String.format("%.2f", job.estimatedMaterialCost)}")
            appendLine("Est. Total:     \$${String.format("%.2f", job.estimatedTotal)}")
            appendLine("Deposit:        ${job.depositRequired}")
            appendLine("Warranty:       ${job.warranty}")
            appendLine()
            appendLine("## Safety")
            job.safetyRequirements.forEach { appendLine("- $it") }
            appendLine()
            appendLine("## Assumptions")
            job.assumptions.forEach { appendLine("- $it") }
            appendLine()
            appendLine("## Exclusions")
            job.exclusions.forEach { appendLine("- $it") }
            appendLine()
            appendLine("## Notes")
            job.notes.forEach { appendLine("- $it") }
        }.trimEnd()
    }
    
    // ════════════════════════════════════════════════════════════════════
    // UTILITY EXTENSIONS
    // ════════════════════════════════════════════════════════════════════
    
    private fun String.containsAny(vararg keywords: String): Boolean {
        return keywords.any { this.contains(it, ignoreCase = true) }
    }
}
