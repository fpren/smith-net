package com.guildofsmiths.trademesh.ai

import android.content.Context

object SmithAITierGate {

    enum class Tier { OPEN, SOLO, ADVANCED, ENTERPRISE }

    sealed class GateResult {
        object Allowed : GateResult()
        data class Blocked(
            val currentTier: Tier,
            val tierRequired: Tier,
            val feature: String,
            val upgradeRoute: String
        ) : GateResult()
    }

    private const val PREFS_NAME = "trademesh_prefs"
    private const val KEY_USER_TIER = "user_tier"
    private const val DEFAULT_TIER = "advanced"

    fun requireAdvanced(context: Context): GateResult {
        val tier = currentTier(context)
        return if (tier == Tier.ADVANCED || tier == Tier.ENTERPRISE) {
            GateResult.Allowed
        } else {
            GateResult.Blocked(
                currentTier = tier,
                tierRequired = Tier.ADVANCED,
                feature = "smithai_chat",
                upgradeRoute = "settings_upgrade"
            )
        }
    }

    fun currentTier(context: Context): Tier {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_USER_TIER, DEFAULT_TIER) ?: DEFAULT_TIER
        return when (raw.lowercase()) {
            "open", "free" -> Tier.OPEN
            "solo" -> Tier.SOLO
            "advanced", "hybrid" -> Tier.ADVANCED
            "enterprise" -> Tier.ENTERPRISE
            else -> Tier.ADVANCED
        }
    }
}
