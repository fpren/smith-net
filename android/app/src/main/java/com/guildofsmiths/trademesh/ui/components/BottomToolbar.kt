package com.guildofsmiths.trademesh.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.guildofsmiths.trademesh.ui.Tokens2
import com.guildofsmiths.trademesh.ui.theme2.LocalSmithColors
import com.guildofsmiths.trademesh.ui.theme2.SmithType
import com.guildofsmiths.trademesh.data.RoleContext
import com.guildofsmiths.trademesh.data.Permission

/**
 * BOTTOM TOOLBAR - Popup Navigation
 *
 * Slides up from bottom-left corner to provide quick access to Plan, Job, and Time screens.
 * Uses bracket notation icons matching ConsoleTheme design language.
 */

@Composable
fun BottomToolbar(
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onNavigateToPlan: () -> Unit,
    onNavigateToJob: () -> Unit,
    onNavigateToTime: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(bottom = 16.dp, start = 16.dp),
        contentAlignment = Alignment.BottomStart
    ) {
        // Expanded toolbar (slides up from bottom)
        AnimatedVisibility(
            visible = isExpanded,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(durationMillis = 300)
            ) + fadeIn(animationSpec = tween(durationMillis = 300)),
            exit = slideOutVertically(
                targetOffsetY = { it },
                animationSpec = tween(durationMillis = 300)
            ) + fadeOut(animationSpec = tween(durationMillis = 300))
        ) {
            ExpandedToolbar(
                onNavigateToPlan = {
                    onNavigateToPlan()
                    onToggle()  // Collapse after navigation
                },
                onNavigateToJob = {
                    onNavigateToJob()
                    onToggle()
                },
                onNavigateToTime = {
                    onNavigateToTime()
                    onToggle()
                },
                onCollapse = onToggle
            )
        }

        // Trigger button (always visible when collapsed)
        if (!isExpanded) {
            TriggerButton(onClick = onToggle)
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// EXPANDED TOOLBAR
// ════════════════════════════════════════════════════════════════════

@Composable
private fun ExpandedToolbar(
    onNavigateToPlan: () -> Unit,
    onNavigateToJob: () -> Unit,
    onNavigateToTime: () -> Unit,
    onCollapse: () -> Unit
) {
    val colors = LocalSmithColors.current
    Column(
        modifier = Modifier
            .width(160.dp)
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(Tokens2.RadiusControl)
            )
            .background(
                color = colors.bgPanel,
                shape = RoundedCornerShape(Tokens2.RadiusControl)
            )
            .padding(vertical = 4.dp)
    ) {
        // Plan option
        ToolbarItem(
            icon = "[◫]",
            label = "PLAN",
            onClick = onNavigateToPlan
        )

        // Separator
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.line)
                .padding(horizontal = 8.dp)
        )

        // Job option — hidden for TEAM_MEMBER who only gets assigned tasks
        if (RoleContext.can(Permission.MANAGE_JOBS)) {
            ToolbarItem(
                icon = "[◧]",
                label = "JOB",
                onClick = onNavigateToJob
            )

            // Separator
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.line)
                    .padding(horizontal = 8.dp)
            )
        }

        // Time option
        ToolbarItem(
            icon = "[◷]",
            label = "TIME",
            onClick = onNavigateToTime
        )

        // Separator
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.line)
                .padding(horizontal = 8.dp)
        )

        // Collapse button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onCollapse)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "[▼]",
                style = SmithType.action.copy(
                    color = colors.ink.copy(alpha = 0.6f)
                )
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// TOOLBAR ITEM
// ════════════════════════════════════════════════════════════════════

@Composable
private fun ToolbarItem(
    icon: String,
    label: String,
    onClick: () -> Unit
) {
    val colors = LocalSmithColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = icon,
            style = SmithType.body,
            color = colors.accent
        )
        Text(
            text = label,
            style = SmithType.bodyBold.copy(color = colors.ink)
        )
    }
}

// ════════════════════════════════════════════════════════════════════
// TRIGGER BUTTON
// ════════════════════════════════════════════════════════════════════

@Composable
private fun TriggerButton(onClick: () -> Unit) {
    val colors = LocalSmithColors.current
    Box(
        modifier = Modifier
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(Tokens2.RadiusControl)
            )
            .background(
                color = colors.accent,
                shape = RoundedCornerShape(Tokens2.RadiusControl)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = "[▲ ⊞]",
            style = SmithType.bodyBold,
            color = colors.inkOnAccent
        )
    }
}
