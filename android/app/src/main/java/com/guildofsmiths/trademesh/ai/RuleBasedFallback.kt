package com.guildofsmiths.trademesh.ai

import android.util.Log

/**
 * RuleBasedFallback - Pre-defined responses when LLM is unavailable
 * 
 * Provides zero-cost responses for common queries when:
 * - Battery is too low for LLM inference
 * - Model is not loaded
 * - Thermal throttling is active
 * 
 * Categories:
 * - Clock-in/out confirmations
 * - Material request acknowledgments
 * - Simple translations (cached phrases)
 * - Task/checklist templates
 * - Common greetings and acknowledgments
 */
object RuleBasedFallback {
    
    private const val TAG = "RuleBasedFallback"
    
    // ════════════════════════════════════════════════════════════════════
    // RESPONSE MAPS
    // ════════════════════════════════════════════════════════════════════
    
    // Time tracking responses
    private val timeResponses = mapOf(
        "clock in" to "✓ Clocked in. Time started.",
        "clock out" to "✓ Clocked out. Time recorded.",
        "break" to "✓ Break started. Remember to clock back in.",
        "lunch" to "✓ Lunch break started. Enjoy your meal.",
        "overtime" to "✓ Overtime noted. Extra hours will be tracked.",
        "hours" to "Check your Time Tracking tab for current hours.",
        "shift" to "Your shift details are in Time Tracking."
    )
    
    // Job board responses
    private val jobResponses = mapOf(
        "materials" to "Check your job's Materials tab for the full list.",
        "tools" to "Required tools are listed in the job details.",
        "checklist" to "Your task checklist is in the job workflow.",
        "next step" to "Complete current tasks, then proceed to next phase.",
        "status" to "Check job status in Job Board: TODO → WORKING → CHECK → DONE"
    )
    
    // Confirmation responses
    private val confirmResponses = mapOf(
        "yes" to "✓ Confirmed.",
        "correct" to "✓ That's correct.",
        "right" to "✓ Looks right to me.",
        "ok" to "✓ Acknowledged.",
        "good" to "✓ Good to go."
    )
    
    // Common Spanish phrases
    private val spanishTranslations = mapOf(
        "hola" to "Hello",
        "gracias" to "Thank you",
        "por favor" to "Please",
        "ayuda" to "Help",
        "necesito" to "I need",
        "trabajo" to "Work/Job",
        "materiales" to "Materials",
        "herramientas" to "Tools",
        "tiempo" to "Time",
        "listo" to "Ready/Done",
        "sí" to "Yes",
        "no" to "No",
        "buenos días" to "Good morning",
        "buenas tardes" to "Good afternoon",
        "hasta luego" to "See you later"
    )
    
    // Common French phrases
    private val frenchTranslations = mapOf(
        "bonjour" to "Hello",
        "merci" to "Thank you",
        "s'il vous plaît" to "Please",
        "aide" to "Help",
        "travail" to "Work",
        "outils" to "Tools",
        "matériaux" to "Materials",
        "temps" to "Time",
        "prêt" to "Ready",
        "oui" to "Yes",
        "non" to "No"
    )
    
    // Trade-specific checklists
    private val tradeChecklists = mapOf(
        "electrical" to """
            □ Turn off power at breaker
            □ Verify power is off with tester
            □ Gather wiring & connectors
            □ Install/repair as needed
            □ Test connections
            □ Restore power & verify
        """.trimIndent(),
        
        "plumbing" to """
            □ Turn off water supply
            □ Drain existing lines
            □ Gather pipes & fittings
            □ Cut & prep pipes
            □ Install connections
            □ Test for leaks
            □ Restore water
        """.trimIndent(),
        
        "hvac" to """
            □ Turn off system
            □ Check refrigerant levels
            □ Inspect filters & ducts
            □ Clean or replace parts
            □ Test operation
            □ Verify temperatures
        """.trimIndent(),
        
        "carpentry" to """
            □ Measure twice
            □ Mark cut lines
            □ Gather materials
            □ Make cuts
            □ Dry fit pieces
            □ Secure & fasten
            □ Final inspection
        """.trimIndent(),
        
        "painting" to """
            □ Cover & protect surfaces
            □ Prep surface (clean/sand)
            □ Apply primer if needed
            □ Cut in edges
            □ Roll main areas
            □ Second coat
            □ Clean up
        """.trimIndent(),
        
        "general" to """
            □ Review job scope
            □ Gather tools & materials
            □ Safety check
            □ Execute work
            □ Quality check
            □ Clean up site
            □ Document completion
        """.trimIndent()
    )
    
    // Acknowledgment phrases
    private val acknowledgments = listOf(
        "Got it.",
        "Understood.",
        "Roger that.",
        "Copy.",
        "On it."
    )
    
    // ════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ════════════════════════════════════════════════════════════════════
    
    /**
     * Get a rule-based response for the given cue.
     * 
     * @param cue The detected AI cue
     * @param metadata Additional context
     * @return Pre-defined response string
     */
    fun getResponse(cue: AICue, metadata: AIMetadata = AIMetadata()): String {
        val query = (cue.extractedQuery ?: cue.originalMessage).lowercase()
        
        Log.d(TAG, "Getting rule-based response for: ${query.take(30)}...")
        
        return when (cue.intent) {
            AIIntent.TRANSLATE -> handleTranslation(query, cue.detectedLanguage)
            AIIntent.CONFIRM -> handleConfirmation(query)
            AIIntent.CHECKLIST -> handleChecklist(query, metadata)
            AIIntent.TASK_HELP -> handleTaskHelp(query, metadata)
            AIIntent.TIME_TRACKING -> handleTimeTracking(query)
            AIIntent.JOB_HELP -> handleJobHelp(query, metadata)
            AIIntent.QUESTION -> handleQuestion(query, metadata)
            else -> handleGeneral(query, metadata)
        }
    }
    
    /**
     * Get a checklist for a specific trade.
     */
    fun getTradeChecklist(trade: String): String {
        val key = trade.lowercase()
        return tradeChecklists[key] ?: tradeChecklists["general"]!!
    }
    
    /**
     * Get a simple translation if available.
     */
    fun getSimpleTranslation(phrase: String, fromLang: String): String? {
        val lower = phrase.lowercase().trim()
        
        return when (fromLang) {
            "es" -> spanishTranslations[lower]
            "fr" -> frenchTranslations[lower]
            else -> null
        }
    }
    
    /**
     * Check if we have a rule-based response for this cue.
     */
    fun hasResponse(cue: AICue): Boolean {
        if (cue.type == AICueType.NONE) return false
        
        // Always have something for confirmations and task help
        if (cue.intent == AIIntent.CONFIRM) return true
        if (cue.intent == AIIntent.CHECKLIST) return true
        if (cue.intent == AIIntent.TIME_TRACKING) return true
        if (cue.intent == AIIntent.JOB_HELP) return true
        
        // Check if we have a cached translation
        if (cue.intent == AIIntent.TRANSLATE) {
            val phrase = cue.extractedQuery?.lowercase()?.trim() ?: ""
            return when (cue.detectedLanguage) {
                "es" -> spanishTranslations.containsKey(phrase)
                "fr" -> frenchTranslations.containsKey(phrase)
                else -> false
            }
        }
        
        return true // Default to having a generic response
    }
    
    // ════════════════════════════════════════════════════════════════════
    // PRIVATE HANDLERS
    // ════════════════════════════════════════════════════════════════════
    
    private fun handleTranslation(query: String, language: String): String {
        // Check cached translations
        val translation = when (language) {
            "es" -> spanishTranslations[query.trim()]
            "fr" -> frenchTranslations[query.trim()]
            else -> null
        }
        
        return translation ?: "[Translation requires full AI - please charge device or connect online]"
    }
    
    private fun handleConfirmation(query: String): String {
        // Look for keywords
        for ((key, response) in confirmResponses) {
            if (query.contains(key)) {
                return response
            }
        }
        return "✓ Acknowledged."
    }
    
    private fun handleChecklist(query: String, metadata: AIMetadata): String {
        // Try to detect trade type
        val trade = detectTrade(query, metadata.jobTitle)
        return getTradeChecklist(trade)
    }
    
    private fun handleTaskHelp(query: String, metadata: AIMetadata): String {
        // Generic task help
        return """
            Task Help:
            1. Review your current tasks
            2. Check materials are ready
            3. Complete tasks in order
            4. Mark done when finished
            
            Check Job Board for details.
        """.trimIndent()
    }
    
    private fun handleTimeTracking(query: String): String {
        // Check for time-related keywords
        for ((key, response) in timeResponses) {
            if (query.contains(key)) {
                return response
            }
        }
        return "Check Time Tracking tab for your hours and entries."
    }
    
    private fun handleJobHelp(query: String, metadata: AIMetadata): String {
        // Check for job-related keywords
        for ((key, response) in jobResponses) {
            if (query.contains(key)) {
                return response
            }
        }
        
        return if (metadata.jobTitle != null) {
            "Job: ${metadata.jobTitle}\nCheck Job Board for full details and workflow."
        } else {
            "Check Job Board for your current jobs and tasks."
        }
    }
    
    private fun handleQuestion(query: String, metadata: AIMetadata): String {
        // Try to match common questions
        return when {
            query.contains("how") && query.contains("clock") -> 
                "Go to Time Tracking, tap CLOCK IN/OUT button."
            query.contains("where") && query.contains("job") -> 
                "Your jobs are in the Job Board tab."
            query.contains("what") && query.contains("next") -> 
                "Check your current task in Job Board workflow."
            query.contains("help") -> 
                "Try: @AI checklist, @AI clock in, @AI materials"
            else -> 
                "[Complex question - full AI needed. Try simpler query or charge device.]"
        }
    }
    
    private fun handleGeneral(query: String, metadata: AIMetadata): String {
        // Random acknowledgment for general queries
        return acknowledgments.random() + "\n\n" +
               "[For detailed help, ensure device is charged for full AI.]"
    }
    
    private fun detectTrade(query: String, jobTitle: String?): String {
        val combined = "$query ${jobTitle ?: ""}".lowercase()
        
        return when {
            combined.contains("electric") || combined.contains("wire") || 
                combined.contains("outlet") -> "electrical"
            combined.contains("plumb") || combined.contains("pipe") || 
                combined.contains("water") || combined.contains("drain") -> "plumbing"
            combined.contains("hvac") || combined.contains("heat") || 
                combined.contains("cool") || combined.contains("ac") -> "hvac"
            combined.contains("wood") || combined.contains("frame") || 
                combined.contains("carpent") || combined.contains("cabinet") -> "carpentry"
            combined.contains("paint") || combined.contains("coat") || 
                combined.contains("finish") -> "painting"
            else -> "general"
        }
    }
}
