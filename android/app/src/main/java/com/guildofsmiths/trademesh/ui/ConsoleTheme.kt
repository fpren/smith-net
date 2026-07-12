@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.guildofsmiths.trademesh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guildofsmiths.trademesh.R
import com.guildofsmiths.trademesh.ui.theme2.LocalSmithColors
import com.guildofsmiths.trademesh.ui.theme2.SmithType

/**
 * smith net — Altara Design Tokens
 *
 * Warm parchment palette, Syne display headers, Inter body / JetBrains Mono data.
 * Indie studio, not startup.
 */
object ConsoleTheme {

    // Brand
    const val APP_NAME = "smith net"
    const val APP_VERSION = "0.1"
    const val BUILD_HASH = "a3f2c1"
    const val STUDIO = "guild of smiths"

    // ═══════════════════════════════════════════════════════════════
    // FONTS
    // ═══════════════════════════════════════════════════════════════

    val syne = FontFamily(
        Font(R.font.syne_semibold, FontWeight.SemiBold),
        Font(R.font.syne_bold, FontWeight.Bold),
    )
    val inter = FontFamily(
        Font(
            R.font.inter_variable, FontWeight.Normal,
            variationSettings = FontVariation.Settings(FontVariation.weight(400)),
        ),
        Font(
            R.font.inter_variable, FontWeight.Medium,
            variationSettings = FontVariation.Settings(FontVariation.weight(500)),
        ),
        Font(
            R.font.inter_variable, FontWeight.SemiBold,
            variationSettings = FontVariation.Settings(FontVariation.weight(600)),
        ),
    )

    // Comm-surface fonts (sanctioned exception, 2026-06-15): the redesigned
    // comm "softphone" pairs a proportional sans (Public Sans, names +
    // conversation) with a technical mono (JetBrains Mono, ids + dialpad +
    // timestamps). Additive ONLY — applied via the comm* TextStyles below and
    // referenced by the comm* SmithType styles.
    val publicSans = FontFamily(
        Font(R.font.public_sans_regular, FontWeight.Normal),
        Font(R.font.public_sans_medium, FontWeight.Medium),
        Font(R.font.public_sans_semibold, FontWeight.SemiBold),
    )
    val jetBrainsMono = FontFamily(
        Font(R.font.jetbrains_mono_regular, FontWeight.Normal),
        Font(R.font.jetbrains_mono_medium, FontWeight.Medium),
    )
}

/**
 * Chic header with bold back arrow.
 */
@Composable
fun ConsoleHeader(
    title: String,
    subtitle: String? = null,
    onBackClick: (() -> Unit)? = null,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalSmithColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.bgBase)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBackClick != null) {
            Text(
                text = "←",
                style = TextStyle(
                    fontFamily = ConsoleTheme.jetBrainsMono,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.ink
                ),
                modifier = Modifier.clickable(onClick = onBackClick)
            )
            Spacer(modifier = Modifier.width(14.dp))
        }

        androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = SmithType.header.copy(color = colors.ink))
            if (subtitle != null) {
                Text(text = subtitle, style = SmithType.caption.copy(color = colors.inkMuted))
            }
        }

        if (actionText != null && onActionClick != null) {
            Text(
                text = actionText,
                style = SmithType.action.copy(color = colors.accent),
                modifier = Modifier
                    .clickable(onClick = onActionClick)
                    .padding(4.dp)
            )
        }
    }
}

/**
 * Faint separator. Renders from the Smith token so the hairline matches the
 * swept crew screens (Design System v2 Plan 4B) -- the last parchment leak.
 */
@Composable
fun ConsoleSeparator(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(LocalSmithColors.current.line)
    )
}

/**
 * Role-aware bottom navigation bar.
 * Tab labels change based on the user's current role.
 */
@Composable
fun BottomNavBar(
    currentRoute: String,
    onHome: () -> Unit,
    onJobs: () -> Unit,
    onComm: () -> Unit,
    onPlan: () -> Unit,
    onClockIn: () -> Unit = {},
    onDispatch: () -> Unit = {}
) {
    val colors = LocalSmithColors.current
    val role = com.guildofsmiths.trademesh.data.RoleContext.role

    // Role-specific nav tabs
    data class NavItem(val label: String, val activeRoute: String, val onClick: () -> Unit)
    val tabs = when (role) {
        com.guildofsmiths.trademesh.data.UserRole.TEAM_MEMBER -> listOf(
            NavItem("[Home]", "dashboard", onHome),
            NavItem("[Tasks]", "job_board", onJobs),
            NavItem("[Comm]", "chat_list", onComm),
            NavItem("[Clock]", "time_clock", onClockIn),
        )
        com.guildofsmiths.trademesh.data.UserRole.TEAM_LEAD -> listOf(
            NavItem("[Home]", "dashboard", onHome),
            NavItem("[Jobs]", "job_board", onJobs),
            NavItem("[Dispatch]", "dispatch", onDispatch),
            NavItem("[Comm]", "chat_list", onComm),
        )
        com.guildofsmiths.trademesh.data.UserRole.FOREMAN -> listOf(
            NavItem("[Home]", "dashboard", onHome),
            NavItem("[Dispatch]", "dispatch", onDispatch),
            NavItem("[Map]", "map", onPlan),
            NavItem("[Comm]", "chat_list", onComm),
        )
        com.guildofsmiths.trademesh.data.UserRole.GENERAL_CONTRACTOR -> listOf(
            NavItem("[Home]", "dashboard", onHome),
            NavItem("[Projects]", "job_board", onJobs),
            NavItem("[Map]", "map", onPlan),
            NavItem("[Comm]", "chat_list", onComm),
        )
        else -> listOf(
            NavItem("[Home]", "dashboard", onHome),
            NavItem("[Jobs]", "job_board", onJobs),
            NavItem("[Comm]", "chat_list", onComm),
            NavItem("[Plan]", "plan", onPlan),
        )
    }

    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.bgPanel)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(colors.ink.copy(alpha = 0.08f))
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEach { tab ->
                BottomNavTab(tab.label, currentRoute == tab.activeRoute, tab.onClick)
            }
        }
        Text(
            text = "\u00A9 2026 Guild of Smiths",
            style = SmithType.captionBold.copy(color = colors.inkMuted.copy(alpha = 0.5f)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp)
                .wrapContentWidth(Alignment.CenterHorizontally)
        )
    }
}

@Composable
private fun BottomNavTab(label: String, isActive: Boolean, onClick: () -> Unit) {
    val colors = LocalSmithColors.current
    Text(
        text = label,
        style = SmithType.action.copy(color = if (isActive) colors.accent else colors.inkMuted),
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}
