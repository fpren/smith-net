package com.guildofsmiths.trademesh.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.guildofsmiths.trademesh.ui.ConsoleTheme

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
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(
                        topEnd = 8.dp,
                        bottomEnd = 8.dp
                    )
                )
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Job Board Item
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
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(
                color = if (isExpanded) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                        else Color.Transparent
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        // Icon (always visible)
        Text(
            text = icon,
            style = ConsoleTheme.bodyBold,
            color = MaterialTheme.colorScheme.primary
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
                style = ConsoleTheme.body,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}

private enum class SidebarItem {
    JOB,
    TIME
}
