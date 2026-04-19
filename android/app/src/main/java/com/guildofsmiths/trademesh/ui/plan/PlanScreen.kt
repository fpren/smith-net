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
import com.guildofsmiths.trademesh.ai.AIPrompts
import com.guildofsmiths.trademesh.ai.OpenRouterClient
import com.guildofsmiths.trademesh.data.IntentRepository
import com.guildofsmiths.trademesh.data.UserPreferences
import com.guildofsmiths.trademesh.ui.ConsoleTheme
import com.guildofsmiths.trademesh.ui.ConsoleSeparator

@Composable
fun PlanScreen(
    onNavigateToJob: () -> Unit = {}
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var selectedIntent by remember { mutableStateOf<IntentData?>(null) }
    var selectedVersion by remember { mutableStateOf<IntentVersionData?>(null) }

    val intents by IntentRepository.intents.collectAsState()
    val versions by IntentRepository.versions.collectAsState()

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
            Text(text = "PROPOSALS", style = ConsoleTheme.title)
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
                text = "[+] NEW PROPOSAL",
                style = ConsoleTheme.action,
                modifier = Modifier
                    .clickable { showCreateDialog = true }
                    .padding(4.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (intents.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleTheme.surface)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No proposals yet.\nCreate one to define scope, tasks,\nequipment, and crew for your jobs.",
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted),
                    textAlign = TextAlign.Center
                )
            }
        } else {
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

    // Dialogs
    if (showCreateDialog) {
        CreateIntentDialog(
            onDismiss = { showCreateDialog = false },
            onAssist = { scope, _ ->
                val trade = UserPreferences.getPrimaryTrade()
                // Try AI first
                val aiResponse = try {
                    OpenRouterClient.chat(
                        systemPrompt = AIPrompts.SYSTEM,
                        userMessage = AIPrompts.generateProposal(scope, trade),
                        maxTokens = 400
                    )
                } catch (_: Exception) { null }

                if (aiResponse != null) {
                    ProposalAssist.parseAIResponse(aiResponse)
                        ?: ProposalAssist.getRuleBasedSuggestion(scope, trade)
                } else {
                    ProposalAssist.getRuleBasedSuggestion(scope, trade)
                }
            },
            onCreate = { scopeStatement, clientName, tasks, equipment, supplies, crewSize ->
                val parties = if (clientName != null) listOf(clientName) else emptyList()
                IntentRepository.createIntent(
                    scopeStatement = scopeStatement,
                    parties = parties,
                    createdBy = "local-user",
                    taskDescriptions = tasks,
                    equipmentNeeded = equipment,
                    suppliesNeeded = supplies,
                    crewSize = crewSize
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
            },
            onCreateJob = { version ->
                IntentRepository.setPendingIntentForJob(version)
                onNavigateToJob()
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
            val taskCount = version.taskDescriptions.size
            val supplyCount = version.suppliesNeeded.size
            Text(
                text = "v${version.versionNumber}  ${version.status.displayName}  ${version.crewSize} crew  ${taskCount}T ${supplyCount}S",
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
