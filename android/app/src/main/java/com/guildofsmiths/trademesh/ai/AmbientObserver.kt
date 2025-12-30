package com.guildofsmiths.trademesh.ai

import android.util.Log
import com.guildofsmiths.trademesh.data.Message
import java.util.Locale
import java.util.regex.Pattern

/**
 * AmbientObserver - Automatically observes and analyzes messages for AI assistance
 *
 * Unlike CueDetector which looks for explicit @AI commands, AmbientObserver:
 * - Analyzes ALL messages when AI is enabled
 * - Detects contextual needs for assistance
 * - Routes to appropriate sub-agents automatically
 * - Maintains ambient, invisible AI presence
 *
 * Observation triggers:
 * - Language translation needs (non-English text)
 * - Time tracking confirmations (clock in/out, breaks)
 * - Task validation (checklists, procedures)
 * - Work coordination (materials, assignments)
 * - Quality assurance (safety checks, compliance)
 */
object AmbientObserver {

    private const val TAG = "AmbientObserver"

    // ════════════════════════════════════════════════════════════════════
    // ANALYSIS RESULT
    // ════════════════════════════════════════════════════════════════════

    private data class AnalysisResult(
        val assistanceType: AssistanceType,
        val confidence: Float,
        val subAgent: SubAgent?,
        val metadata: Map<String, Any>
    )

    // ════════════════════════════════════════════════════════════════════
    // CONTEXT PATTERNS (no explicit AI cues needed)
    // ════════════════════════════════════════════════════════════════════

    // Time tracking patterns
    private val TIME_PATTERNS = listOf(
        Pattern.compile("""(?i)(clock|time|hours|break|lunch|overtime|shift|punch|start|end)"""),
        Pattern.compile("""(?i)(on\s+(lunch|break)|taking\s+(lunch|break))"""),
        Pattern.compile("""(?i)(back\s+from|returning\s+from)""")
    )

    // Material and task patterns
    private val MATERIAL_PATTERNS = listOf(
        Pattern.compile("""(?i)(material|materials|tool|tools|equipment|supply|supplies)"""),
        Pattern.compile("""(?i)(need|needed|required|missing|out\s+of)"""),
        Pattern.compile("""(?i)(check|verify|confirm|validate)""")
    )

    // Task and checklist patterns
    private val TASK_PATTERNS = listOf(
        Pattern.compile("""(?i)(task|tasks|job|work|step|steps|procedure)"""),
        Pattern.compile("""(?i)(done|finished|completed|complete)"""),
        Pattern.compile("""(?i)(next|then|after|before)""")
    )

    // Safety and compliance patterns
    private val SAFETY_PATTERNS = listOf(
        Pattern.compile("""(?i)(safe|safety|danger|risk|hazard)"""),
        Pattern.compile("""(?i)(emergency|accident|injury|incident)"""),
        Pattern.compile("""(?i)(permit|inspection|compliance|regulation)""")
    )

    // Question patterns that might need assistance
    private val QUESTION_PATTERNS = listOf(
        Pattern.compile("""(?i)(how\s+(do|to|can|should))"""),
        Pattern.compile("""(?i)(what\s+(do|is|are|should))"""),
        Pattern.compile("""(?i)(where\s+(do|can|should))"""),
        Pattern.compile("""(?i)(when\s+(do|should|can))""")
    )

    // Onboarding trigger patterns
    private val ONBOARDING_PATTERNS = listOf(
        Pattern.compile("""(?i)(job|work|task|time|clock|material|tool)"""),
        Pattern.compile("""(?i)(help|how|what|start|begin)"""),
        Pattern.compile("""(?i)(electrician|hvac|plumber|carpenter|welder)""")
    )

    // ════════════════════════════════════════════════════════════════════
    // LANGUAGE DETECTION
    // ════════════════════════════════════════════════════════════════════

    private val NON_ENGLISH_INDICATORS = mapOf(
        "es" to listOf("¿", "¡", "hola", "gracias", "por favor", "necesito", "dónde", "cómo", "qué", "sí", "no"),
        "fr" to listOf("bonjour", "merci", "s'il vous plaît", "où", "comment", "quoi", "oui", "non"),
        "pt" to listOf("olá", "obrigado", "por favor", "onde", "como", "sim", "não"),
        "de" to listOf("guten tag", "danke", "bitte", "wie", "was", "wo", "ja", "nein"),
        "it" to listOf("ciao", "grazie", "per favore", "dove", "come", "cosa", "sì", "no")
    )

    private val CYRILLIC_PATTERN = Pattern.compile("[\\p{IsCyrillic}]")
    private val CJK_PATTERN = Pattern.compile("[\\p{IsCJK}]|[\\u4e00-\\u9fff]")
    private val ARABIC_PATTERN = Pattern.compile("[\\p{IsArabic}]")
    private val DEVANAGARI_PATTERN = Pattern.compile("[\\p{IsDevanagari}]")

    // ════════════════════════════════════════════════════════════════════
    // OBSERVATION RESULTS
    // ════════════════════════════════════════════════════════════════════

    data class Observation(
        val messageId: String,
        val message: Message,
        val needsAssistance: Boolean,
        val assistanceType: AssistanceType,
        val confidence: Float, // 0.0 to 1.0
        val context: MessageContext,
        val detectedLanguage: String = "en",
        val needsTranslation: Boolean = false,
        val subAgent: SubAgent? = null,
        val metadata: Map<String, Any> = emptyMap()
    )

    enum class AssistanceType {
        NONE,               // No assistance needed
        TRANSLATION,        // Language translation
        TIME_VALIDATION,    // Clock in/out confirmations
        MATERIAL_CHECK,     // Tool/material verification
        TASK_VALIDATION,    // Procedure/workflow validation
        SAFETY_CHECK,       // Safety compliance
        COORDINATION,       // Work coordination help
        SUMMARY,            // Activity summarization
        QUESTION_ANSWER     // General assistance
    }

    enum class SubAgent {
        TRANSLATOR,         // Language translation
        TIME_KEEPER,        // Time tracking validation
        MATERIAL_EXPERT,    // Tool/material knowledge
        TASK_VALIDATOR,     // Workflow validation
        SAFETY_OFFICER,     // Safety compliance
        COORDINATOR,        // Work coordination
        SUMMARIZER,         // Activity summarization
        ONBOARDING,         // Role setup prompts
        ASSISTANT           // General assistance
    }

    // ════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ════════════════════════════════════════════════════════════════════

    /**
     * Observe a message and determine if AI assistance is needed.
     * This is called automatically for ALL messages when AI is enabled.
     */
    fun observe(message: Message, context: MessageContext = MessageContext.CHAT): Observation {
        val content = message.content.trim()
        if (content.isEmpty()) {
            return Observation(
                messageId = message.id,
                message = message,
                needsAssistance = false,
                assistanceType = AssistanceType.NONE,
                confidence = 0f,
                context = context
            )
        }

        // Detect language
        val detectedLanguage = detectLanguage(content)
        val needsTranslation = detectedLanguage != "en" && detectedLanguage != "unknown"

        // If message is in another language, always offer translation
        if (needsTranslation) {
            return Observation(
                messageId = message.id,
                message = message,
                needsAssistance = true,
                assistanceType = AssistanceType.TRANSLATION,
                confidence = 0.9f,
                context = context,
                detectedLanguage = detectedLanguage,
                needsTranslation = true,
                subAgent = SubAgent.TRANSLATOR
            )
        }

        // Analyze content for assistance needs
        val assistance = analyzeContent(content, context)

        return Observation(
            messageId = message.id,
            message = message,
            needsAssistance = assistance.assistanceType != AssistanceType.NONE,
            assistanceType = assistance.assistanceType,
            confidence = assistance.confidence,
            context = context,
            detectedLanguage = detectedLanguage,
            needsTranslation = false,
            subAgent = assistance.subAgent,
            metadata = assistance.metadata
        )
    }

    /**
     * Check if a message needs immediate attention (high priority observations)
     */
    fun needsImmediateAttention(observation: Observation): Boolean {
        return when (observation.assistanceType) {
            AssistanceType.SAFETY_CHECK -> true
            AssistanceType.TIME_VALIDATION -> observation.confidence > 0.7f
            else -> false
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // CONTENT ANALYSIS
    // ════════════════════════════════════════════════════════════════════

    private fun analyzeContent(content: String, context: MessageContext): AnalysisResult {
        val lowerContent = content.lowercase()

        // Time tracking assistance
        val timeScore = calculatePatternScore(lowerContent, TIME_PATTERNS)
        if (timeScore > 0.6f) {
            return AnalysisResult(AssistanceType.TIME_VALIDATION, timeScore, SubAgent.TIME_KEEPER,
                mapOf("time_related" to true))
        }

        // Material/tool assistance
        val materialScore = calculatePatternScore(lowerContent, MATERIAL_PATTERNS)
        if (materialScore > 0.5f) {
            return AnalysisResult(AssistanceType.MATERIAL_CHECK, materialScore, SubAgent.MATERIAL_EXPERT,
                mapOf("material_related" to true))
        }

        // Task/workflow assistance
        val taskScore = calculatePatternScore(lowerContent, TASK_PATTERNS)
        if (taskScore > 0.5f) {
            return AnalysisResult(AssistanceType.TASK_VALIDATION, taskScore, SubAgent.TASK_VALIDATOR,
                mapOf("task_related" to true))
        }

        // Safety compliance
        val safetyScore = calculatePatternScore(lowerContent, SAFETY_PATTERNS)
        if (safetyScore > 0.7f) {
            return AnalysisResult(AssistanceType.SAFETY_CHECK, safetyScore, SubAgent.SAFETY_OFFICER,
                mapOf("safety_related" to true))
        }

        // Questions that might need help
        val questionScore = calculatePatternScore(lowerContent, QUESTION_PATTERNS)
        if (questionScore > 0.4f && context == MessageContext.JOB_BOARD) {
            return AnalysisResult(AssistanceType.QUESTION_ANSWER, questionScore, SubAgent.ASSISTANT,
                mapOf("question" to true, "context" to "job_board"))
        }

        // Onboarding prompts (if no role set and user is active)
        val onboardingScore = calculatePatternScore(lowerContent, ONBOARDING_PATTERNS)
        val hasRoleSet = com.guildofsmiths.trademesh.data.UserPreferences.hasTradeRoleSet()
        if (!hasRoleSet && onboardingScore > 0.3f) {
            return AnalysisResult(AssistanceType.QUESTION_ANSWER, onboardingScore, SubAgent.ONBOARDING,
                mapOf("onboarding_needed" to true))
        }

        // Coordination assistance (multi-person context)
        if (context == MessageContext.CHAT && containsMultipleReferences(content)) {
            return AnalysisResult(AssistanceType.COORDINATION, 0.6f, SubAgent.COORDINATOR,
                mapOf("coordination_needed" to true))
        }

        return AnalysisResult(AssistanceType.NONE, 0f, null, emptyMap())
    }

    private fun calculatePatternScore(content: String, patterns: List<Pattern>): Float {
        var totalScore = 0f
        var matchCount = 0

        for (pattern in patterns) {
            val matcher = pattern.matcher(content)
            if (matcher.find()) {
                matchCount++
                // Weight by pattern position and specificity
                val startPos = matcher.start()
                val positionWeight = if (startPos < 50) 1.0f else 0.7f
                totalScore += positionWeight
            }
        }

        return if (matchCount > 0) (totalScore / patterns.size).coerceAtMost(1.0f) else 0f
    }

    private fun containsMultipleReferences(content: String): Boolean {
        val pronouns = listOf("we", "us", "they", "them", "our", "their")
        val teamWords = listOf("team", "crew", "group", "everyone", "all")

        val pronounCount = pronouns.count { content.contains("\\b$it\\b".toRegex(RegexOption.IGNORE_CASE)) }
        val teamCount = teamWords.count { content.contains("\\b$it\\b".toRegex(RegexOption.IGNORE_CASE)) }

        return pronounCount >= 2 || teamCount >= 1 || (pronounCount >= 1 && teamCount >= 1)
    }

    private fun detectLanguage(content: String): String {
        // Check for non-English character sets
        if (CYRILLIC_PATTERN.matcher(content).find()) return "ru"
        if (CJK_PATTERN.matcher(content).find()) return "zh"
        if (ARABIC_PATTERN.matcher(content).find()) return "ar"
        if (DEVANAGARI_PATTERN.matcher(content).find()) return "hi"

        // Check for language-specific words
        val lowerContent = content.lowercase()
        for ((langCode, indicators) in NON_ENGLISH_INDICATORS) {
            val matches = indicators.count { lowerContent.contains(it) }
            if (matches >= 2) {
                return langCode
            }
        }

        return "en"
    }

    /**
     * Get a human-readable description of the observation
     */
    fun getObservationDescription(observation: Observation): String {
        return when (observation.assistanceType) {
            AssistanceType.TRANSLATION ->
                "Detected ${observation.detectedLanguage.uppercase()} text that may need translation"
            AssistanceType.TIME_VALIDATION ->
                "Time tracking activity detected - offering validation"
            AssistanceType.MATERIAL_CHECK ->
                "Material/tool reference detected - offering verification"
            AssistanceType.TASK_VALIDATION ->
                "Task/workflow reference detected - offering validation"
            AssistanceType.SAFETY_CHECK ->
                "Safety/compliance concern detected - offering guidance"
            AssistanceType.COORDINATION ->
                "Multi-person coordination opportunity detected"
            AssistanceType.QUESTION_ANSWER ->
                "Question detected that may need assistance"
            AssistanceType.SUMMARY ->
                "Work activity summary opportunity detected"
            AssistanceType.NONE ->
                "No assistance needed"
        }
    }
}















