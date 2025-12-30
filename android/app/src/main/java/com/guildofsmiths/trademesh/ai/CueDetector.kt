package com.guildofsmiths.trademesh.ai

import android.util.Log
import java.util.Locale
import java.util.regex.Pattern

/**
 * CueDetector - Detects AI invocation cues and context in messages
 * 
 * Scans messages for:
 * - Explicit cues: @AI, @assistant, @help
 * - Language detection (non-English triggers translation mode)
 * - Context detection (job board, time tracking, chat)
 * - Intent classification for routing
 * 
 * Returns structured cue information for the AIRouter.
 */
object CueDetector {
    
    private const val TAG = "CueDetector"
    
    // ════════════════════════════════════════════════════════════════════
    // PATTERNS
    // ════════════════════════════════════════════════════════════════════
    
    // Explicit AI cues - case insensitive
    private val AI_CUE_PATTERN = Pattern.compile(
        """(?i)@(ai|assistant|helper|smith|smithy|ayuda|aide)""",
        Pattern.CASE_INSENSITIVE
    )
    
    // Translation request patterns
    private val TRANSLATE_PATTERN = Pattern.compile(
        """(?i)(translate|traducir|traduire|traduzir|übersetzen)\s*:?\s*(.+)""",
        Pattern.CASE_INSENSITIVE
    )
    
    // Task/checklist request patterns
    private val TASK_PATTERN = Pattern.compile(
        """(?i)(checklist|task|tasks|todo|to-do|list|steps|what\s+do\s+i\s+need)""",
        Pattern.CASE_INSENSITIVE
    )
    
    // Confirmation request patterns  
    private val CONFIRM_PATTERN = Pattern.compile(
        """(?i)(confirm|verify|check|is\s+this\s+right|correct\?|good\?|ok\?)""",
        Pattern.CASE_INSENSITIVE
    )
    
    // Time tracking related patterns
    private val TIME_PATTERN = Pattern.compile(
        """(?i)(clock|time|hours|break|lunch|overtime|shift|punch)""",
        Pattern.CASE_INSENSITIVE
    )
    
    // Job/work related patterns
    private val JOB_PATTERN = Pattern.compile(
        """(?i)(job|work|project|material|tool|invoice|estimate|quote)""",
        Pattern.CASE_INSENSITIVE
    )
    
    // Common non-English phrases that indicate translation need
    private val NON_ENGLISH_INDICATORS = listOf(
        // Spanish
        "¿", "¡", "hola", "gracias", "por favor", "necesito", "dónde", "cómo", "qué",
        // French
        "bonjour", "merci", "s'il vous plaît", "où", "comment", "quoi",
        // Portuguese
        "olá", "obrigado", "por favor", "onde", "como",
        // German
        "guten tag", "danke", "bitte", "wie", "was", "wo"
    )
    
    // Language detection character patterns
    private val CYRILLIC_PATTERN = Pattern.compile("[\\p{IsCyrillic}]")
    private val CJK_PATTERN = Pattern.compile("[\\p{IsCJK}]|[\\u4e00-\\u9fff]")
    private val ARABIC_PATTERN = Pattern.compile("[\\p{IsArabic}]")
    private val DEVANAGARI_PATTERN = Pattern.compile("[\\p{IsDevanagari}]")
    
    // ════════════════════════════════════════════════════════════════════
    // PUBLIC API
    // ════════════════════════════════════════════════════════════════════
    
    /**
     * Detect AI cues in a message.
     * 
     * @param message The message text to analyze
     * @param context Optional context about where the message came from
     * @return AICue result with detected cue type, intent, and metadata
     */
    fun detect(message: String, context: MessageContext = MessageContext.CHAT): AICue {
        val trimmed = message.trim()
        
        // Check for explicit @AI cue
        val hasExplicitCue = AI_CUE_PATTERN.matcher(trimmed).find()
        
        if (!hasExplicitCue) {
            // No explicit cue - no AI processing
            return AICue(
                type = AICueType.NONE,
                intent = AIIntent.NONE,
                originalMessage = message,
                extractedQuery = null
            )
        }
        
        // Extract the query part (everything after the @AI cue)
        val query = extractQuery(trimmed)
        
        // Detect language
        val detectedLanguage = detectLanguage(query)
        val needsTranslation = detectedLanguage != "en" && detectedLanguage != "unknown"
        
        // Check for explicit translation request
        val translateMatcher = TRANSLATE_PATTERN.matcher(query)
        val isTranslationRequest = translateMatcher.find()
        val translationText = if (isTranslationRequest) {
            translateMatcher.group(2)?.trim()
        } else null
        
        // Determine intent
        val intent = determineIntent(query, context)
        
        // Determine cue type
        val cueType = when {
            isTranslationRequest || needsTranslation -> AICueType.TRANSLATION
            intent == AIIntent.CONFIRM -> AICueType.CONFIRMATION
            intent == AIIntent.TASK_HELP || intent == AIIntent.CHECKLIST -> AICueType.TASK_HELP
            else -> AICueType.GENERAL
        }
        
        val cue = AICue(
            type = cueType,
            intent = intent,
            originalMessage = message,
            extractedQuery = translationText ?: query,
            detectedLanguage = detectedLanguage,
            needsTranslation = needsTranslation,
            context = context
        )
        
        Log.d(TAG, "Detected cue: type=${cue.type}, intent=${cue.intent}, " +
                   "lang=${cue.detectedLanguage}, query=${cue.extractedQuery?.take(50)}")
        
        return cue
    }
    
    /**
     * Check if a message contains an AI cue without full analysis.
     * Faster check for pre-filtering messages.
     */
    fun hasAICue(message: String): Boolean {
        return AI_CUE_PATTERN.matcher(message).find()
    }
    
    /**
     * Estimate if the message would require significant processing.
     * Used for battery/resource gating decisions.
     */
    fun estimateComplexity(message: String): CueComplexity {
        val wordCount = message.split(Regex("\\s+")).size
        val hasTranslation = TRANSLATE_PATTERN.matcher(message).find() || 
                             detectLanguage(message) != "en"
        
        return when {
            wordCount > 100 -> CueComplexity.HIGH
            wordCount > 50 -> CueComplexity.MEDIUM
            hasTranslation -> CueComplexity.MEDIUM
            wordCount > 20 -> CueComplexity.LOW
            else -> CueComplexity.MINIMAL
        }
    }
    
    // ════════════════════════════════════════════════════════════════════
    // PRIVATE HELPERS
    // ════════════════════════════════════════════════════════════════════
    
    private fun extractQuery(message: String): String {
        // Remove the @AI cue and get the rest
        val withoutCue = AI_CUE_PATTERN.matcher(message).replaceFirst("").trim()
        
        // Remove common prefixes like "please", "can you", etc.
        val cleaned = withoutCue
            .replace(Regex("^(please|can you|could you|would you|help me)\\s*", RegexOption.IGNORE_CASE), "")
            .trim()
        
        return cleaned.ifEmpty { withoutCue }
    }
    
    private fun detectLanguage(text: String): String {
        val lower = text.lowercase(Locale.ROOT)
        
        // Check for non-Latin scripts first
        if (CYRILLIC_PATTERN.matcher(text).find()) return "ru"
        if (CJK_PATTERN.matcher(text).find()) return "zh"
        if (ARABIC_PATTERN.matcher(text).find()) return "ar"
        if (DEVANAGARI_PATTERN.matcher(text).find()) return "hi"
        
        // Check for Spanish indicators
        if (lower.contains("¿") || lower.contains("¡") ||
            NON_ENGLISH_INDICATORS.take(9).any { lower.contains(it) }) {
            return "es"
        }
        
        // Check for French indicators
        if (NON_ENGLISH_INDICATORS.slice(9..13).any { lower.contains(it) }) {
            return "fr"
        }
        
        // Check for Portuguese indicators
        if (NON_ENGLISH_INDICATORS.slice(14..17).any { lower.contains(it) }) {
            return "pt"
        }
        
        // Check for German indicators
        if (NON_ENGLISH_INDICATORS.slice(18..22).any { lower.contains(it) }) {
            return "de"
        }
        
        // Default to English for Latin script
        return if (text.matches(Regex("^[\\x00-\\x7F\\s]+$"))) "en" else "unknown"
    }
    
    private fun determineIntent(query: String, context: MessageContext): AIIntent {
        return when {
            TRANSLATE_PATTERN.matcher(query).find() -> AIIntent.TRANSLATE
            CONFIRM_PATTERN.matcher(query).find() -> AIIntent.CONFIRM
            TASK_PATTERN.matcher(query).find() -> {
                if (context == MessageContext.JOB_BOARD) AIIntent.CHECKLIST
                else AIIntent.TASK_HELP
            }
            TIME_PATTERN.matcher(query).find() -> AIIntent.TIME_TRACKING
            JOB_PATTERN.matcher(query).find() -> AIIntent.JOB_HELP
            query.endsWith("?") -> AIIntent.QUESTION
            else -> AIIntent.GENERAL
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// DATA CLASSES
// ════════════════════════════════════════════════════════════════════

/**
 * Type of AI cue detected
 */
enum class AICueType {
    NONE,           // No AI cue detected
    GENERAL,        // General AI query
    TRANSLATION,    // Translation request or non-English input
    TASK_HELP,      // Task or checklist request
    CONFIRMATION    // Confirmation or verification request
}

/**
 * Detected user intent
 */
enum class AIIntent {
    NONE,           // No AI involvement needed
    GENERAL,        // General conversation
    TRANSLATE,      // Explicit translation request
    CONFIRM,        // Confirmation request
    TASK_HELP,      // Help with tasks
    CHECKLIST,      // Generate checklist
    QUESTION,       // General question
    TIME_TRACKING,  // Time tracking related
    JOB_HELP        // Job/work related help
}

/**
 * Context where the message originated
 */
enum class MessageContext {
    CHAT,           // General chat/messaging
    JOB_BOARD,      // Job board context
    TIME_TRACKING,  // Time tracking context
    MESH            // BLE mesh message
}

/**
 * Estimated processing complexity
 */
enum class CueComplexity {
    MINIMAL,    // Simple acknowledgment
    LOW,        // Short response
    MEDIUM,     // Standard response
    HIGH        // Complex/long response
}

/**
 * Result of cue detection
 */
data class AICue(
    val type: AICueType,
    val intent: AIIntent,
    val originalMessage: String,
    val extractedQuery: String?,
    val detectedLanguage: String = "en",
    val needsTranslation: Boolean = false,
    val context: MessageContext = MessageContext.CHAT
) {
    /**
     * Whether this cue requires AI processing
     */
    val requiresAI: Boolean
        get() = type != AICueType.NONE
    
    /**
     * Whether this should use LLM or can use rule-based
     */
    val requiresLLM: Boolean
        get() = type == AICueType.TRANSLATION || 
                type == AICueType.GENERAL ||
                intent == AIIntent.QUESTION
}
