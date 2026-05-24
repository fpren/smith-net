package com.guildofsmiths.trademesh.ai

import com.guildofsmiths.trademesh.data.UserPreferences

object SmithAIBackendRouter {

    enum class Backend { ON_DEVICE, CLOUD, OFFLINE }

    sealed class GenerationResponse {
        data class Ok(val text: String, val backend: Backend) : GenerationResponse()
        data class Failed(val reason: String, val backend: Backend) : GenerationResponse()
    }

    fun pick(): Backend {
        val onDeviceReady = LlamaInference.modelState.value == ModelState.READY
        val batteryStatus = BatteryGate.getAIStatus()
        val onDeviceUsable = onDeviceReady &&
            batteryStatus != AIAvailability.DISABLED &&
            batteryStatus != AIAvailability.RULE_BASED_ONLY

        if (onDeviceUsable) return Backend.ON_DEVICE

        val rawApiKey = UserPreferences.getOpenRouterApiKey()
        val hasUsableKey = rawApiKey.isNotBlank() &&
            (rawApiKey.startsWith("sk-or-") || rawApiKey.startsWith("sk-") || rawApiKey.startsWith("xai-"))

        return if (hasUsableKey) Backend.CLOUD else Backend.OFFLINE
    }

    suspend fun generate(systemPrompt: String, userMessage: String): GenerationResponse {
        val backend = pick()
        return when (backend) {
            Backend.ON_DEVICE -> generateOnDevice(systemPrompt, userMessage)
            Backend.CLOUD -> generateCloud(systemPrompt, userMessage)
            Backend.OFFLINE -> GenerationResponse.Failed(
                "AI is offline. Charge your device, or set an API key in Settings > SmithAI > Cloud.",
                Backend.OFFLINE
            )
        }
    }

    private suspend fun generateOnDevice(systemPrompt: String, userMessage: String): GenerationResponse {
        val maxTokens = BatteryGate.getRecommendedMaxTokens().coerceAtLeast(128)
        val wrapped = wrapForQwen3(systemPrompt, userMessage)
        return when (val r = LlamaInference.generate(wrapped, maxTokens = maxTokens, temperature = 0.4f)) {
            is GenerationResult.Success -> GenerationResponse.Ok(r.text.trim(), Backend.ON_DEVICE)
            is GenerationResult.Error -> GenerationResponse.Failed(r.message, Backend.ON_DEVICE)
        }
    }

    private suspend fun generateCloud(systemPrompt: String, userMessage: String): GenerationResponse {
        val response = OpenRouterClient.chat(
            systemPrompt = systemPrompt,
            userMessage = userMessage,
            maxTokens = 600
        )
        return if (response != null) {
            GenerationResponse.Ok(response.trim(), Backend.CLOUD)
        } else {
            GenerationResponse.Failed("Cloud request failed.", Backend.CLOUD)
        }
    }

    private fun wrapForQwen3(systemPrompt: String, userMessage: String): String {
        return buildString {
            append("<|im_start|>system\n")
            append(systemPrompt)
            append("<|im_end|>\n<|im_start|>user\n")
            append(userMessage)
            append("<|im_end|>\n<|im_start|>assistant\n")
        }
    }
}
