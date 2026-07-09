package com.guildofsmiths.trademesh.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.guildofsmiths.trademesh.ui.theme2.LocalSmithColors
import com.guildofsmiths.trademesh.ui.theme2.SmithType
import com.guildofsmiths.trademesh.data.RoleContext
import com.guildofsmiths.trademesh.data.Permission

/**
 * LEFT SIDEBAR - Collapsible Navigation
 *
 * Minimal sidebar on left edge with Job and Time shortcuts.
 * - Collapsed: Shows only icons
 * - First click: Expands to show labels
 * - Second click: Navigates to container
 */

@Composable
fun LeftSidebar(
    onNavigateToJob: () -> Unit,
    onNavigateToTime: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalSmithColors.current
    var expandedItem by remember { mutableStateOf<SidebarItem?>(null) }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(top = 60.dp),
        contentAlignment = Alignment.TopStart
    ) {
        Column(
            modifier = Modifier
                .background(
                    color = colors.bgPanel,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(
                        topEnd = 8.dp,
                        bottomEnd = 8.dp
                    )
                )
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Job Board Item — hidden for TEAM_MEMBER who only gets assigned tasks
            if (RoleContext.can(Permission.MANAGE_JOBS)) {
                SidebarButton(
                    icon = "[◧]",
                    label = "JOB",
                    isExpanded = expandedItem == SidebarItem.JOB,
                    onClick = {
                        if (expandedItem == SidebarItem.JOB) {
                            onNavigateToJob()
                            expandedItem = null
                        } else {
                            expandedItem = SidebarItem.JOB
                        }
                    }
                )
            }

            // Time Tracking Item
            SidebarButton(
                icon = "[◷]",
                label = "TIME",
                isExpanded = expandedItem == SidebarItem.TIME,
                onClick = {
                    if (expandedItem == SidebarItem.TIME) {
                        onNavigateToTime()
                        expandedItem = null
                    } else {
                        expandedItem = SidebarItem.TIME
                    }
                }
            )
        }
    }
}

@Composable
private fun SidebarButton(
    icon: String,
    label: String,
    isExpanded: Boolean,
    onClick: () -> Unit
) {
    val colors = LocalSmithColors.current
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(
                color = if (isExpanded) colors.accent.copy(alpha = 0.1f)
                        else Color.Transparent
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        // Icon (always visible)
        Text(
            text = icon,
            style = SmithType.bodyBold,
            color = colors.accent
        )

        // Label (animated expand/collapse)
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn(animationSpec = tween(200)) +
                    expandHorizontally(animationSpec = tween(200)),
            exit = fadeOut(animationSpec = tween(200)) +
                   shrinkHorizontally(animationSpec = tween(200))
        ) {
            Text(
                text = " $label",
                style = SmithType.body,
                color = colors.ink,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

private enum class SidebarItem {
    JOB,
    TIME
}
