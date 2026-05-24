package com.guildofsmiths.trademesh.ai

import android.content.Context
import android.util.Log
import com.guildofsmiths.trademesh.data.Message
import com.guildofsmiths.trademesh.data.MessageRepository
import com.guildofsmiths.trademesh.data.UserPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

object SmithAIConversationOrchestrator {

    private const val TAG = "SmithAIOrch"

    private val _pendingToolCalls = MutableStateFlow<List<SmithAIToolExecutor.PendingToolCall>>(emptyList())
    val pendingToolCalls: StateFlow<List<SmithAIToolExecutor.PendingToolCall>> = _pendingToolCalls.asStateFlow()

    private val orchestratorScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun handleTurn(
        context: Context,
        beaconId: String,
        channelId: String,
        userMessage: Message
    ) {
        orchestratorScope.launch {
            val gate = SmithAITierGate.requireAdvanced(context)
            if (gate is SmithAITierGate.GateResult.Blocked) {
                appendAiText(
                    beaconId, channelId,
                    "[ADVANCED REQUIRED] SmithAI chat is part of the Advanced tier. Open Settings > Subscription to upgrade."
                )
                return@launch
            }

            // Best-effort auto-wake (non-blocking from the user's perspective; we still proceed via cloud if needed)
            try { AgentInitializer.wakeAgentIfModelDownloaded(context) } catch (e: Exception) {
                Log.w(TAG, "auto-wake failed (non-fatal)", e)
            }

            val placeholderId = appendPlaceholder(beaconId, channelId)

            val recentTurns = MessageRepository.allMessages.value
                .filter { it.beaconId == beaconId && it.channelId == channelId }
                .sortedBy { it.timestamp }
                .takeLast(20)

            val build = SmithAIContextBuilder.build(
                userMessage = userMessage.content,
                recentTurns = recentTurns,
                currentChannelId = channelId
            )

            var response = SmithAIBackendRouter.generate(build.systemPrompt, userMessage.content)

            if (response is SmithAIBackendRouter.GenerationResponse.Failed) {
                replacePlaceholder(beaconId, channelId, placeholderId,
                    text = "[${response.backend.name}] ${response.reason}",
                    backend = response.backend
                )
                return@launch
            }

            response = response as SmithAIBackendRouter.GenerationResponse.Ok
            val backend = response.backend
            val firstText = response.text

            when (val parsed = SmithAIToolExecutor.parse(firstText)) {
                is SmithAIToolExecutor.ParseResult.NoToolCall -> {
                    replacePlaceholder(beaconId, channelId, placeholderId, firstText, backend)
                }
                is SmithAIToolExecutor.ParseResult.Malformed -> {
                    val retry = retryAfterError(build.systemPrompt, userMessage.content, firstText, parsed.errorJson)
                    when (retry) {
                        is SmithAIBackendRouter.GenerationResponse.Ok -> {
                            replacePlaceholder(beaconId, channelId, placeholderId, retry.text, retry.backend)
                        }
                        is SmithAIBackendRouter.GenerationResponse.Failed -> {
                            replacePlaceholder(beaconId, channelId, placeholderId,
                                "I had trouble forming a response. Try rephrasing.", backend
                            )
                        }
                    }
                }
                is SmithAIToolExecutor.ParseResult.Parsed -> {
                    val tool = SmithAIToolRegistry.byName(parsed.name)
                    if (tool == null) {
                        replacePlaceholder(beaconId, channelId, placeholderId,
                            "I don't have a way to do that yet.", backend
                        )
                    } else {
                        when (val ex = SmithAIToolExecutor.validateAndExecuteRead(parsed)) {
                            is SmithAIToolExecutor.ExecutionResult.ReadOk -> {
                                val followup = SmithAIBackendRouter.generate(
                                    build.systemPrompt,
                                    "Tool ${parsed.name} returned: ${ex.resultJson}\n\nRespond to the user using this data. Original question: \"${userMessage.content}\""
                                )
                                val finalText = (followup as? SmithAIBackendRouter.GenerationResponse.Ok)?.text
                                    ?: "Looked it up but couldn't summarize. Raw: ${ex.resultJson.take(200)}"
                                replacePlaceholder(beaconId, channelId, placeholderId, finalText, backend)
                            }
                            is SmithAIToolExecutor.ExecutionResult.WritePending -> {
                                _pendingToolCalls.value = _pendingToolCalls.value + ex.pending
                                replacePlaceholder(beaconId, channelId, placeholderId,
                                    "[ACTION QUEUED] ${ex.pending.toolName}: ${ex.pending.argsSummary}\nApprove or deny below.",
                                    backend,
                                    aiContext = "smithai-action-pending"
                                )
                            }
                            is SmithAIToolExecutor.ExecutionResult.Failed -> {
                                replacePlaceholder(beaconId, channelId, placeholderId,
                                    "I tried to use a tool but it failed: ${ex.errorJson.take(160)}",
                                    backend
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun approve(context: Context, beaconId: String, channelId: String, pendingId: String) {
        orchestratorScope.launch {
            val pending = _pendingToolCalls.value.firstOrNull { it.id == pendingId } ?: return@launch
            val senderId = UserPreferences.getUserId()
            val senderName = UserPreferences.getDisplayName()
            val resultText = SmithAIToolExecutor.executeApprovedWrite(
                pending = pending,
                beaconId = beaconId,
                senderId = senderId,
                senderName = senderName
            )
            SmithAIAuditLog.append(context, pending.toolName, pending.arguments.toString(), approved = true, resultSummary = resultText)
            _pendingToolCalls.value = _pendingToolCalls.value.filterNot { it.id == pendingId }
            appendAiText(beaconId, channelId, "[DONE] $resultText")
        }
    }

    fun deny(context: Context, beaconId: String, channelId: String, pendingId: String) {
        orchestratorScope.launch {
            val pending = _pendingToolCalls.value.firstOrNull { it.id == pendingId } ?: return@launch
            SmithAIAuditLog.append(context, pending.toolName, pending.arguments.toString(), approved = false, resultSummary = "denied by user")
            _pendingToolCalls.value = _pendingToolCalls.value.filterNot { it.id == pendingId }
            appendAiText(beaconId, channelId, "[CANCELLED] Did not run ${pending.toolName}.")
        }
    }

    private suspend fun retryAfterError(
        systemPrompt: String,
        originalUserMsg: String,
        @Suppress("UNUSED_PARAMETER") firstResponse: String,
        errorJson: String
    ): SmithAIBackendRouter.GenerationResponse {
        return SmithAIBackendRouter.generate(
            systemPrompt,
            "Your previous response had a tool-call error: $errorJson\n\nDo not retry the broken call. Either pick a different tool or answer the user in plain text. Original question: \"$originalUserMsg\""
        )
    }

    private fun appendPlaceholder(beaconId: String, channelId: String): String {
        val id = "smithai-pending-${UUID.randomUUID().toString().take(10)}"
        val placeholder = Message(
            id = id,
            beaconId = beaconId,
            channelId = channelId,
            senderId = "ai-assistant",
            senderName = "SmithAI",
            content = "[. . .]",
            aiGenerated = true,
            aiSource = "local",
            aiContext = "smithai-thinking"
        )
        MessageRepository.addMessage(placeholder)
        return id
    }

    private fun replacePlaceholder(
        beaconId: String,
        channelId: String,
        placeholderId: String,
        text: String,
        backend: SmithAIBackendRouter.Backend,
        aiContext: String = "smithai-chat"
    ) {
        MessageRepository.removeMessage(placeholderId)
        val msg = Message(
            beaconId = beaconId,
            channelId = channelId,
            senderId = "ai-assistant",
            senderName = "SmithAI",
            content = text,
            aiGenerated = true,
            aiModel = when (backend) {
                SmithAIBackendRouter.Backend.ON_DEVICE -> "qwen3-1.7b-q4"
                SmithAIBackendRouter.Backend.CLOUD -> "openrouter"
                SmithAIBackendRouter.Backend.OFFLINE -> "offline"
            },
            aiSource = when (backend) {
                SmithAIBackendRouter.Backend.ON_DEVICE -> "local"
                SmithAIBackendRouter.Backend.CLOUD -> "cloud"
                SmithAIBackendRouter.Backend.OFFLINE -> "local"
            },
            aiContext = aiContext
        )
        MessageRepository.addMessage(msg)
    }

    private fun appendAiText(beaconId: String, channelId: String, text: String, aiContext: String = "smithai-chat") {
        val msg = Message(
            beaconId = beaconId,
            channelId = channelId,
            senderId = "ai-assistant",
            senderName = "SmithAI",
            content = text,
            aiGenerated = true,
            aiSource = "local",
            aiContext = aiContext
        )
        MessageRepository.addMessage(msg)
    }
}
