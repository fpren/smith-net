package com.guildofsmiths.trademesh.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.guildofsmiths.trademesh.data.IntentRepository
import com.guildofsmiths.trademesh.ui.ConsoleTheme

/**
 * INTENT COMPONENTS - Dialogs and Workflow UI
 *
 * Contains Create Dialog and Detail Dialog for Intent lifecycle states.
 * Follows the same AlertDialog + ConsoleTheme pattern as PlanComponents.kt.
 */

// ════════════════════════════════════════════════════════════════════
// CREATE INTENT DIALOG
// ════════════════════════════════════════════════════════════════════

@Composable
fun CreateIntentDialog(
    onDismiss: () -> Unit,
    onCreate: (scopeStatement: String, clientName: String?) -> Unit
) {
    var scopeStatement by remember { mutableStateOf("") }
    var clientName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ConsoleTheme.surface,
        title = {
            Text(
                text = "[+] NEW INTENT",
                style = ConsoleTheme.header
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Scope Statement (required)
                Text("Scope Statement *", style = ConsoleTheme.captionBold)
                TextField(
                    value = scopeStatement,
                    onValueChange = { scopeStatement = it },
                    placeholder = { Text("What will be done?", style = ConsoleTheme.caption) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = ConsoleTheme.body,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedIndicatorColor = ConsoleTheme.accent,
                        unfocusedIndicatorColor = ConsoleTheme.text.copy(alpha = 0.2f)
                    )
                )

                // Client name (optional)
                Text("Client", style = ConsoleTheme.captionBold)
                TextField(
                    value = clientName,
                    onValueChange = { clientName = it },
                    placeholder = { Text("Client name", style = ConsoleTheme.caption) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = ConsoleTheme.body,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedIndicatorColor = ConsoleTheme.accent,
                        unfocusedIndicatorColor = ConsoleTheme.text.copy(alpha = 0.2f)
                    )
                )
            }
        },
        confirmButton = {
            Text(
                text = "[OK] CREATE",
                style = if (scopeStatement.isNotBlank()) ConsoleTheme.action
                    else ConsoleTheme.action.copy(color = ConsoleTheme.textDim),
                modifier = Modifier
                    .clickable(enabled = scopeStatement.isNotBlank()) {
                        onCreate(
                            scopeStatement.trim(),
                            clientName.trim().ifBlank { null }
                        )
                    }
                    .padding(8.dp)
            )
        },
        dismissButton = {
            Text(
                text = "[x] CANCEL",
                style = ConsoleTheme.action.copy(color = ConsoleTheme.text.copy(alpha = 0.6f)),
                modifier = Modifier
                    .clickable(onClick = onDismiss)
                    .padding(8.dp)
            )
        }
    )
}

// ════════════════════════════════════════════════════════════════════
// INTENT DETAIL DIALOG
// ════════════════════════════════════════════════════════════════════

@Composable
fun IntentDetailDialog(
    intent: IntentData,
    version: IntentVersionData,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ConsoleTheme.surface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${version.status.icon} ${version.scopeStatement.take(30)}",
                    style = ConsoleTheme.header
                )
                Text(
                    text = version.status.displayName.uppercase(),
                    style = ConsoleTheme.caption,
                    color = ConsoleTheme.accent
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Render workflow-specific UI based on status
                when (version.status) {
                    IntentStatus.DRAFT -> DraftIntentUI(intent, version, onDismiss)
                    IntentStatus.PROPOSED -> ProposedIntentUI(intent, version, onDismiss)
                    IntentStatus.CONFIRMED -> ConfirmedIntentUI(intent, version, onDismiss)
                    IntentStatus.SUPERSEDED -> SupersededIntentUI(intent, version, onDismiss)
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            Text(
                text = "[x] CLOSE",
                style = ConsoleTheme.action,
                modifier = Modifier
                    .clickable(onClick = onDismiss)
                    .padding(8.dp)
            )
        }
    )
}

// ════════════════════════════════════════════════════════════════════
// DRAFT INTENT UI
// ════════════════════════════════════════════════════════════════════

@Composable
private fun DraftIntentUI(
    intent: IntentData,
    version: IntentVersionData,
    onDismiss: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        IntentDetailSection(label = "Version", value = "v${version.versionNumber}")
        IntentDetailSection(label = "Scope", value = version.scopeStatement)
        IntentDetailSection(label = "Jobs", value = "${version.intendedJobIds.size} linked")
        if (version.parties.isNotEmpty()) {
            IntentDetailSection(label = "Parties", value = version.parties.joinToString(", "))
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Ready to propose this intent?",
            style = ConsoleTheme.body,
            color = ConsoleTheme.text.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "[>] PROPOSE",
            style = if (version.canPropose()) ConsoleTheme.action
                else ConsoleTheme.action.copy(color = ConsoleTheme.textDim),
            modifier = Modifier
                .clickable(enabled = version.canPropose()) {
                    IntentRepository.proposeVersion(version.id)
                    onDismiss()
                }
                .padding(vertical = 8.dp)
        )
    }
}

// ════════════════════════════════════════════════════════════════════
// PROPOSED INTENT UI
// ════════════════════════════════════════════════════════════════════

@Composable
private fun ProposedIntentUI(
    intent: IntentData,
    version: IntentVersionData,
    onDismiss: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "PROPOSED INTENT",
            style = ConsoleTheme.captionBold,
            color = ConsoleTheme.accent
        )

        IntentDetailSection(label = "Version", value = "v${version.versionNumber}")
        IntentDetailSection(label = "Scope", value = version.scopeStatement)
        IntentDetailSection(label = "Jobs", value = "${version.intendedJobIds.size} linked")

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "All parties agree to this scope?",
            style = ConsoleTheme.body,
            color = ConsoleTheme.text.copy(alpha = 0.7f)
        )

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = "[v] CONFIRM",
            style = if (version.canConfirm()) ConsoleTheme.action
                else ConsoleTheme.action.copy(color = ConsoleTheme.textDim),
            modifier = Modifier
                .clickable(enabled = version.canConfirm()) {
                    IntentRepository.confirmVersion(version.id, "local-user")
                    onDismiss()
                }
                .padding(vertical = 8.dp)
        )
    }
}

// ════════════════════════════════════════════════════════════════════
// CONFIRMED INTENT UI
// ════════════════════════════════════════════════════════════════════

@Composable
private fun ConfirmedIntentUI(
    intent: IntentData,
    version: IntentVersionData,
    onDismiss: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "CONFIRMED - Ready for job execution",
            style = ConsoleTheme.captionBold,
            color = ConsoleTheme.accent
        )

        IntentDetailSection(label = "Version", value = "v${version.versionNumber}")
        IntentDetailSection(label = "Scope", value = version.scopeStatement)
        IntentDetailSection(label = "Jobs", value = "${version.intendedJobIds.size} linked")
        if (version.confirmedBy != null) {
            IntentDetailSection(label = "Confirmed by", value = version.confirmedBy)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Linked jobs
        if (version.intendedJobIds.isNotEmpty()) {
            Text(
                text = "LINKED JOBS (${version.intendedJobIds.size})",
                style = ConsoleTheme.captionBold,
                color = ConsoleTheme.accent
            )
            version.intendedJobIds.forEach { jobId ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White)
                        .padding(8.dp)
                ) {
                    Text(
                        text = "  Job ID: $jobId",
                        style = ConsoleTheme.caption
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "[+ JOB] CREATE JOB",
            style = ConsoleTheme.action,
            modifier = Modifier
                .clickable {
                    // TODO: Navigate to Job Board to create job linked to this intent
                }
                .padding(vertical = 8.dp)
        )
    }
}

// ════════════════════════════════════════════════════════════════════
// SUPERSEDED INTENT UI
// ════════════════════════════════════════════════════════════════════

@Composable
private fun SupersededIntentUI(
    intent: IntentData,
    version: IntentVersionData,
    onDismiss: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "SUPERSEDED - See newer version",
            style = ConsoleTheme.captionBold,
            color = ConsoleTheme.textMuted
        )

        IntentDetailSection(label = "Version", value = "v${version.versionNumber}")
        IntentDetailSection(label = "Scope", value = version.scopeStatement)

        if (version.supersededBy != null) {
            IntentDetailSection(label = "Superseded by", value = version.supersededBy.take(16) + "...")
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// HELPER COMPONENTS
// ════════════════════════════════════════════════════════════════════

@Composable
private fun IntentDetailSection(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = ConsoleTheme.captionBold,
            color = ConsoleTheme.text.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            style = ConsoleTheme.body
        )
    }
}
