package com.guildofsmiths.trademesh.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.guildofsmiths.trademesh.data.IntentRepository
import com.guildofsmiths.trademesh.ui.ConsoleTheme
import com.guildofsmiths.trademesh.ui.ConsoleSeparator
import com.guildofsmiths.trademesh.ui.components.LeftSidebar

@Composable
fun PlanScreen(
    onNavigateToMessages: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToProfile: () -> Unit,
    onNavigateToJob: () -> Unit = {},
    onNavigateToTime: () -> Unit = {}
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedIntent by remember { mutableStateOf<IntentData?>(null) }
    var selectedVersion by remember { mutableStateOf<IntentVersionData?>(null) }

    val intents by IntentRepository.intents.collectAsState()
    val versions by IntentRepository.versions.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        // Main content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(ConsoleTheme.background)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "[◫] INTENT", style = ConsoleTheme.title)
                Row {
                    Text(
                        text = "[Msg]",
                        style = ConsoleTheme.body,
                        modifier = Modifier.clickable { onNavigateToMessages() }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "[⚙]",
                        style = ConsoleTheme.body,
                        modifier = Modifier.clickable { onNavigateToSettings() }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "[Prof]",
                        style = ConsoleTheme.body,
                        modifier = Modifier.clickable { onNavigateToProfile() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            ConsoleSeparator()
            Spacer(modifier = Modifier.height(16.dp))

            // Create intent button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "INTENTS", style = ConsoleTheme.captionBold)
                Text(
                    text = "[+] NEW",
                    style = ConsoleTheme.action,
                    modifier = Modifier
                        .clickable { showCreateDialog = true }
                        .padding(4.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            if (intents.isEmpty()) {
                // Empty state
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ConsoleTheme.surface)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No intents yet.\nCreate one to define scope for your jobs.",
                        style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                // Intent list
                intents.forEach { intent ->
                    val currentVersion = versions.find { it.id == intent.currentVersionId }
                    if (currentVersion != null) {
                        IntentListItem(
                            intent = intent,
                            version = currentVersion,
                            onClick = {
                                selectedIntent = intent
                                selectedVersion = currentVersion
                            }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }

        // Left sidebar (overlay)
        LeftSidebar(
            onNavigateToJob = onNavigateToJob,
            onNavigateToTime = onNavigateToTime,
            modifier = Modifier.align(Alignment.CenterStart)
        )
    }

    // Dialogs
    if (showCreateDialog) {
        CreateIntentDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { scopeStatement, clientName ->
                val parties = if (clientName != null) listOf(clientName) else emptyList()
                IntentRepository.createIntent(
                    scopeStatement = scopeStatement,
                    parties = parties,
                    createdBy = "local-user"
                )
                showCreateDialog = false
            }
        )
    }

    if (selectedIntent != null && selectedVersion != null) {
        IntentDetailDialog(
            intent = selectedIntent!!,
            version = selectedVersion!!,
            onDismiss = {
                selectedIntent = null
                selectedVersion = null
            }
        )
    }
}

// ════════════════════════════════════════════════════════════════════
// INTENT LIST ITEM
// ════════════════════════════════════════════════════════════════════

@Composable
private fun IntentListItem(
    intent: IntentData,
    version: IntentVersionData,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ConsoleTheme.surface)
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${version.status.icon} ${version.scopeStatement.take(40)}",
                style = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.text)
            )
            Text(
                text = "v${version.versionNumber}  ${version.status.displayName}  ${version.intendedJobIds.size} jobs",
                style = ConsoleTheme.caption
            )
        }
        Text(
            text = ">",
            style = ConsoleTheme.body,
            color = ConsoleTheme.textMuted
        )
    }
}
