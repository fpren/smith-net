package com.guildofsmiths.trademesh.ai

import android.util.Log
import com.guildofsmiths.trademesh.data.Message
import java.util.*

/**
 * SubAgents - Specialized AI assistants for different domains
 *
 * Each sub-agent handles a specific type of assistance:
 * - Translator: Language translation
 * - TimeKeeper: Time tracking validation
 * - MaterialExpert: Tool/material knowledge
 * - TaskValidator: Workflow validation
 * - SafetyOfficer: Safety compliance
 * - Coordinator: Work coordination
 * - Summarizer: Activity summarization
 * - Assistant: General assistance
 */
object SubAgents {

    private const val TAG = "SubAgents"

    // ════════════════════════════════════════════════════════════════════
    // SUB-AGENT RESPONSES
    // ════════════════════════════════════════════════════════════════════

    /**
     * Process an observation with the appropriate sub-agent
     */
    fun process(observation: AmbientObserver.Observation): SubAgentResponse {
        Log.d(TAG, "Processing observation: ${observation.assistanceType} (${observation.confidence})")

        return when (observation.subAgent) {
            AmbientObserver.SubAgent.TRANSLATOR -> Translator.process(observation)
            AmbientObserver.SubAgent.TIME_KEEPER -> TimeKeeper.process(observation)
            AmbientObserver.SubAgent.MATERIAL_EXPERT -> MaterialExpert.process(observation)
            AmbientObserver.SubAgent.TASK_VALIDATOR -> TaskValidator.process(observation)
            AmbientObserver.SubAgent.SAFETY_OFFICER -> SafetyOfficer.process(observation)
            AmbientObserver.SubAgent.COORDINATOR -> Coordinator.process(observation)
            AmbientObserver.SubAgent.SUMMARIZER -> Summarizer.process(observation)
            AmbientObserver.SubAgent.ONBOARDING -> Onboarding.process(observation)
            AmbientObserver.SubAgent.ASSISTANT -> Assistant.process(observation)
            null -> SubAgentResponse.none(observation.messageId)
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // RESPONSE FORMAT
    // ════════════════════════════════════════════════════════════════════

    data class SubAgentResponse(
        val messageId: String,
        val responseType: ResponseType,
        val content: String,
        val confidence: Float,
        val actions: List<Action> = emptyList(),
        val metadata: Map<String, Any> = emptyMap()
    ) {
        enum class ResponseType {
            NONE,           // No response needed
            SUGGESTION,     // Gentle suggestion
            VALIDATION,     // Confirm/correct action
            INFORMATION,    // Provide information
            WARNING,        // Safety/compliance warning
            ASSISTANCE      // Active help
        }

        companion object {
            fun none(messageId: String) = SubAgentResponse(messageId, ResponseType.NONE, "", 0f)
            fun suggestion(messageId: String, content: String, confidence: Float = 0.7f) =
                SubAgentResponse(messageId, ResponseType.SUGGESTION, content, confidence)
            fun validation(messageId: String, content: String, confidence: Float = 0.8f) =
                SubAgentResponse(messageId, ResponseType.VALIDATION, content, confidence)
            fun information(messageId: String, content: String, confidence: Float = 0.6f) =
                SubAgentResponse(messageId, ResponseType.INFORMATION, content, confidence)
            fun warning(messageId: String, content: String, confidence: Float = 0.9f) =
                SubAgentResponse(messageId, ResponseType.WARNING, content, confidence)
        }
    }

    data class Action(
        val type: ActionType,
        val target: String,
        val parameters: Map<String, Any> = emptyMap()
    ) {
        enum class ActionType {
            LOG_TIME,           // Log time entry
            CHECK_MATERIAL,     // Verify material availability
            VALIDATE_TASK,      // Confirm task completion
            ALERT_SAFETY,       // Safety alert
            COORDINATE_TEAM,    // Team coordination
            SUMMARIZE_ACTIVITY  // Activity summary
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // TRANSLATOR SUB-AGENT
    // ════════════════════════════════════════════════════════════════════

    object Translator {
        fun process(observation: AmbientObserver.Observation): SubAgentResponse {
            val lang = observation.detectedLanguage
            val content = observation.message.content

            // Simple translations for common phrases
            val translation = getSimpleTranslation(content, lang)
            if (translation != null) {
            return SubAgentResponse(
                observation.messageId,
                SubAgentResponse.ResponseType.INFORMATION,
                "\"$content\" → \"$translation\" (auto-translated from ${lang.uppercase()})",
                0.8f
            )
            }

            return SubAgentResponse.suggestion(
                observation.messageId,
                "This message appears to be in ${lang.uppercase()}. Would you like me to translate it?",
                0.9f
            )
        }

        private fun getSimpleTranslation(text: String, fromLang: String): String? {
            val lowerText = text.lowercase().trim()

            return when (fromLang) {
                "es" -> when (lowerText) {
                    "hola" -> "Hello"
                    "gracias" -> "Thank you"
                    "por favor" -> "Please"
                    "sí" -> "Yes"
                    "no" -> "No"
                    "ayuda" -> "Help"
                    else -> null
                }
                "fr" -> when (lowerText) {
                    "bonjour" -> "Hello"
                    "merci" -> "Thank you"
                    "s'il vous plaît" -> "Please"
                    "oui" -> "Yes"
                    "non" -> "No"
                    "aide" -> "Help"
                    else -> null
                }
                else -> null
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // TIME KEEPER SUB-AGENT
    // ════════════════════════════════════════════════════════════════════

    object TimeKeeper {
        fun process(observation: AmbientObserver.Observation): SubAgentResponse {
            val content = observation.message.content.lowercase()
            val tradeRole = com.guildofsmiths.trademesh.data.UserPreferences.getTradeRole()
            val roleKnowledge = com.guildofsmiths.trademesh.data.OccupationalForms.getKnowledgeBase(tradeRole)

            // Clock in/out confirmations with role context
            if (content.contains("clock") && (content.contains("in") || content.contains("out"))) {
                val safetyNote = roleKnowledge.safetyProtocols.firstOrNull()
                    ?: "Follow proper safety protocols"
                return SubAgentResponse(
                    observation.messageId,
                    SubAgentResponse.ResponseType.VALIDATION,
                    "✓ Time entry logged. Current session: ${getCurrentSessionTime()}. As a ${tradeRole.displayName}: $safetyNote.",
                    0.9f,
                    listOf(Action(Action.ActionType.LOG_TIME, "time_entry", mapOf("auto_logged" to true)))
                )
            }

            // Break/lunch tracking with role-specific dexterity reminders
            if (content.contains("lunch") || content.contains("break")) {
                val breakType = if (content.contains("lunch")) "lunch" else "break"
                val dexterityTip = roleKnowledge.breakReminders.firstOrNull()
                    ?: "Take time to rest your hands and joints"
                return SubAgentResponse(
                    observation.messageId,
                    SubAgentResponse.ResponseType.VALIDATION,
                    "✓ $breakType break started. $dexterityTip. Remember to clock back in when you return.",
                    0.8f
                )
            }

            // General time validation with role context
            return SubAgentResponse.suggestion(
                observation.messageId,
                "As a ${tradeRole.displayName}, accurate time logging helps track your specialized work. Use the Time Clock feature.",
                0.6f
            )
        }

        private fun getCurrentSessionTime(): String {
            // Simplified - in real implementation would get from TimeTrackingViewModel
            return "2h 15m"
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // MATERIAL EXPERT SUB-AGENT
    // ════════════════════════════════════════════════════════════════════

    object MaterialExpert {
        fun process(observation: AmbientObserver.Observation): SubAgentResponse {
            val content = observation.message.content.lowercase()
            val tradeRole = com.guildofsmiths.trademesh.data.UserPreferences.getTradeRole()
            val roleKnowledge = com.guildofsmiths.trademesh.data.OccupationalForms.getKnowledgeBase(tradeRole)

            // Missing materials with tool recommendations
            if (content.contains("missing") || content.contains("need") || content.contains("out of")) {
                val toolTip = roleKnowledge.tools.firstOrNull()?.let {
                    "Remember proper ${it.name} grip technique: ${it.gripTechnique}"
                } ?: "Use proper tool handling techniques"
                return SubAgentResponse.assistance(
                    observation.messageId,
                    "📋 Material request noted. Check your job's material checklist. As a ${tradeRole.displayName}: $toolTip.",
                    0.8f,
                    listOf(Action(Action.ActionType.CHECK_MATERIAL, "material_inventory"))
                )
            }

            // Material verification with safety context
            if (content.contains("check") || content.contains("verify")) {
                val safetyTip = roleKnowledge.safetyProtocols.firstOrNull()
                    ?: "Follow standard safety protocols"
                return SubAgentResponse(
                    observation.messageId,
                    SubAgentResponse.ResponseType.VALIDATION,
                    "✅ Material checklist available in your job details. As a ${tradeRole.displayName}: $safetyTip while verifying equipment.",
                    0.7f
                )
            }

            // General material assistance with role context
            return SubAgentResponse(
                observation.messageId,
                SubAgentResponse.ResponseType.INFORMATION,
                "💡 As a ${tradeRole.displayName}, keep your material and tool checklists updated for proper ${roleKnowledge.regulations.firstOrNull() ?: "trade standard"} compliance.",
                0.5f
            )
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // TASK VALIDATOR SUB-AGENT
    // ════════════════════════════════════════════════════════════════════

    object TaskValidator {
        fun process(observation: AmbientObserver.Observation): SubAgentResponse {
            val content = observation.message.content.lowercase()
            val tradeRole = com.guildofsmiths.trademesh.data.UserPreferences.getTradeRole()
            val roleKnowledge = com.guildofsmiths.trademesh.data.OccupationalForms.getKnowledgeBase(tradeRole)

            // Task completion
            if (content.contains("done") || content.contains("finished") || content.contains("completed")) {
                return SubAgentResponse(
                    observation.messageId,
                    SubAgentResponse.ResponseType.VALIDATION,
                    "✅ Task completion noted. As a ${tradeRole.displayName}, remember to document all work performed per ${roleKnowledge.regulations.firstOrNull() ?: "trade standards"}.",
                    0.8f,
                    listOf(Action(Action.ActionType.VALIDATE_TASK, "task_completion"))
                )
            }

            // Next steps with role-specific guidance
            if (content.contains("next") || content.contains("then") || content.contains("after")) {
                val dexterityTip = roleKnowledge.breakReminders.firstOrNull()
                    ?: "Take regular breaks to prevent fatigue"
                return SubAgentResponse.suggestion(
                    observation.messageId,
                    "📝 As a ${tradeRole.displayName}, update your job checklist systematically. $dexterityTip.",
                    0.6f
                )
            }

            // General task help with role context
            return SubAgentResponse(
                observation.messageId,
                SubAgentResponse.ResponseType.INFORMATION,
                "🔧 Use the Job Board to create ${tradeRole.displayName}-specific task checklists with proper safety protocols.",
                0.5f
            )
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // SAFETY OFFICER SUB-AGENT
    // ════════════════════════════════════════════════════════════════════

    object SafetyOfficer {
        fun process(observation: AmbientObserver.Observation): SubAgentResponse {
            val content = observation.message.content.lowercase()

            // Safety concerns
            if (content.contains("danger") || content.contains("risk") || content.contains("hazard")) {
                return SubAgentResponse(
                    observation.messageId,
                    SubAgentResponse.ResponseType.WARNING,
                    "⚠️ Safety concern detected. Please ensure proper safety protocols are followed.",
                    0.9f,
                    listOf(Action(Action.ActionType.ALERT_SAFETY, "safety_alert"))
                )
            }

            // Emergency situations
            if (content.contains("emergency") || content.contains("accident") || content.contains("injury")) {
                return SubAgentResponse(
                    observation.messageId,
                    SubAgentResponse.ResponseType.WARNING,
                    "🚨 Emergency situation detected. Follow emergency protocols and contact supervisor if needed.",
                    1.0f,
                    listOf(Action(Action.ActionType.ALERT_SAFETY, "emergency_alert"))
                )
            }

            // General safety compliance
            if (content.contains("permit") || content.contains("inspection")) {
                return SubAgentResponse.validation(
                    observation.messageId,
                    "📋 Safety compliance check recommended. Ensure all permits and inspections are current.",
                    0.8f
                )
            }

            return SubAgentResponse.none(observation.messageId)
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // COORDINATOR SUB-AGENT
    // ════════════════════════════════════════════════════════════════════

    object Coordinator {
        fun process(observation: AmbientObserver.Observation): SubAgentResponse {
            val content = observation.message.content.lowercase()

            // Team coordination
            if (content.contains("team") || content.contains("crew") || content.contains("everyone")) {
                return SubAgentResponse.assistance(
                    observation.messageId,
                    "👥 Team coordination noted. Consider using the Job Board to assign tasks and track progress.",
                    0.7f,
                    listOf(Action(Action.ActionType.COORDINATE_TEAM, "team_coordination"))
                )
            }

            // Schedule coordination
            if (content.contains("schedule") || content.contains("timing") || content.contains("when")) {
                return SubAgentResponse(
                    observation.messageId,
                    SubAgentResponse.ResponseType.INFORMATION,
                    "📅 For scheduling coordination, check the Time Clock and Job Board for team availability.",
                    0.6f
                )
            }

            return SubAgentResponse.none(observation.messageId)
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // SUMMARIZER SUB-AGENT
    // ════════════════════════════════════════════════════════════════════

    object Summarizer {
        fun process(observation: AmbientObserver.Observation): SubAgentResponse {
            // Only summarize at appropriate times (end of shift, job completion, etc.)
            val content = observation.message.content.lowercase()

            if (content.contains("shift") && content.contains("end") ||
                content.contains("job") && content.contains("done") ||
                content.contains("finished") && content.contains("day")) {

                return SubAgentResponse(
                    observation.messageId,
                    SubAgentResponse.ResponseType.INFORMATION,
                    "📊 Work summary: Tasks completed, time logged, materials verified. Great work today!",
                    0.7f,
                    listOf(Action(Action.ActionType.SUMMARIZE_ACTIVITY, "daily_summary"))
                )
            }

            return SubAgentResponse.none(observation.messageId)
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // ONBOARDING SUB-AGENT
    // ════════════════════════════════════════════════════════════════════

    object Onboarding {
        fun process(observation: AmbientObserver.Observation): SubAgentResponse {
            val content = observation.message.content.lowercase()
            val hasRoleSet = com.guildofsmiths.trademesh.data.UserPreferences.hasTradeRoleSet()

            // Only provide onboarding prompts if no role is set
            if (hasRoleSet) {
                return SubAgentResponse.none(observation.messageId)
            }

            // Trigger onboarding after some activity (messages, time tracking, etc.)
            val messageCount = observation.message.content.length
            val isActiveUser = messageCount > 10 // Rough heuristic for engaged user

            if (isActiveUser && (content.contains("job") || content.contains("work") || content.contains("time"))) {
                return SubAgentResponse.suggestion(
                    observation.messageId,
                    "👋 Hey there! Head to Settings and pick your trade (Electrician, HVAC, Plumber, etc.)? It'll make my tips way more useful for your kind of work. No pressure though! 😊",
                    0.7f
                )
            }

            // Less frequent, casual reminders
            if (Math.random() < 0.1 && (content.contains("help") || content.contains("how") || content.contains("what"))) {
                return SubAgentResponse(
                    observation.messageId,
                    SubAgentResponse.ResponseType.INFORMATION,
                    "💡 Pro tip: Set your trade role in Settings for personalized suggestions tailored to your specialty!",
                    0.4f
                )
            }

            return SubAgentResponse.none(observation.messageId)
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // GENERAL ASSISTANT SUB-AGENT
    // ════════════════════════════════════════════════════════════════════

    object Assistant {
        fun process(observation: AmbientObserver.Observation): SubAgentResponse {
            val content = observation.message.content.lowercase()
            val tradeRole = com.guildofsmiths.trademesh.data.UserPreferences.getTradeRole()
            val hasRoleSet = com.guildofsmiths.trademesh.data.UserPreferences.hasTradeRoleSet()

            // Questions about how to do things with role context
            if (content.startsWith("how")) {
                val roleContext = if (hasRoleSet) "as a ${tradeRole.displayName}" else "in your trade work"
                return SubAgentResponse.assistance(
                    observation.messageId,
                    "🤔 I can help with that $roleContext! Check the Job Board for detailed procedures and checklists.",
                    0.6f
                )
            }

            // General assistance with role context
            val roleTip = if (hasRoleSet) {
                "The Job Board and Time Clock are designed for ${tradeRole.displayName} workflows."
            } else {
                "The Job Board and Time Clock are designed to make coordination easier."
            }

            return SubAgentResponse(
                observation.messageId,
                SubAgentResponse.ResponseType.SUGGESTION,
                "💡 Need help with your workflow? $roleTip",
                0.4f
            )
        }
    }

    // Helper function to create assistance responses
    private fun SubAgentResponse.Companion.assistance(
        messageId: String,
        content: String,
        confidence: Float = 0.7f,
        actions: List<Action> = emptyList()
    ) = SubAgentResponse(messageId, SubAgentResponse.ResponseType.ASSISTANCE, content, confidence, actions)
}















