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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.guildofsmiths.trademesh.data.IntentRepository
import com.guildofsmiths.trademesh.data.UserPreferences
import com.guildofsmiths.trademesh.ui.ConsoleTheme
import kotlinx.coroutines.launch

// ════════════════════════════════════════════════════════════════════
// CREATE INTENT DIALOG — Proposal Template
// ════════════════════════════════════════════════════════════════════

@Composable
fun CreateIntentDialog(
    onDismiss: () -> Unit,
    onAssist: (suspend (scope: String, trade: String) -> ProposalSuggestion?)? = null,
    onCreate: (
        scopeStatement: String,
        clientName: String?,
        taskDescriptions: List<String>,
        equipmentNeeded: List<String>,
        suppliesNeeded: List<String>,
        crewSize: Int
    ) -> Unit
) {
    var scopeStatement by remember { mutableStateOf("") }
    var clientName by remember { mutableStateOf("") }
    var crewSizeText by remember { mutableStateOf("1") }

    // Dynamic list fields
    var taskLines by remember { mutableStateOf(listOf("")) }
    var equipmentLines by remember { mutableStateOf(listOf("")) }
    var supplyLines by remember { mutableStateOf(listOf("")) }

    // Assist state — auto-triggers when scope loses focus
    var isAssisting by remember { mutableStateOf(false) }
    var hasAssisted by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Helper to check if fields are still untouched
    fun fieldsAreEmpty() = taskLines.all { it.isBlank() } &&
        equipmentLines.all { it.isBlank() } && supplyLines.all { it.isBlank() }

    fun triggerAssist() {
        if (onAssist == null || hasAssisted || isAssisting) return
        if (scopeStatement.trim().length < 10 || !fieldsAreEmpty()) return
        isAssisting = true
        hasAssisted = true
        coroutineScope.launch {
            try {
                val suggestion = onAssist(scopeStatement.trim(), "")
                if (suggestion != null) {
                    if (suggestion.tasks.isNotEmpty()) taskLines = suggestion.tasks
                    if (suggestion.equipment.isNotEmpty()) equipmentLines = suggestion.equipment
                    if (suggestion.supplies.isNotEmpty()) supplyLines = suggestion.supplies
                    crewSizeText = suggestion.crewSize.toString()
                }
            } finally {
                isAssisting = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ConsoleTheme.surface,
        title = {
            Column {
                Text(text = "NEW PROPOSAL", style = ConsoleTheme.header)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Define scope, tasks, equipment, and crew",
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // ── SCOPE ──
                ProposalSection(label = "SCOPE OF WORK *") {
                    TextField(
                        value = scopeStatement,
                        onValueChange = { scopeStatement = it },
                        placeholder = { Text("Describe the work to be performed...", style = ConsoleTheme.caption) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { state ->
                                if (!state.isFocused) triggerAssist()
                            },
                        textStyle = ConsoleTheme.body,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedIndicatorColor = ConsoleTheme.accent,
                            unfocusedIndicatorColor = ConsoleTheme.text.copy(alpha = 0.2f)
                        )
                    )
                    if (isAssisting) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Generating suggestions...",
                            style = ConsoleTheme.caption.copy(color = ConsoleTheme.accent)
                        )
                    }
                }

                // ── CLIENT ──
                ProposalSection(label = "CLIENT") {
                    ProposalTextField(
                        value = clientName,
                        onValueChange = { clientName = it },
                        placeholder = "Client or property name"
                    )
                }

                // ── TASKS ──
                ProposalSection(label = "TASKS REQUIRED") {
                    DynamicListField(
                        lines = taskLines,
                        onLinesChange = { taskLines = it },
                        placeholder = "Task description"
                    )
                }

                // ── EQUIPMENT ──
                ProposalSection(label = "EQUIPMENT NEEDED") {
                    DynamicListField(
                        lines = equipmentLines,
                        onLinesChange = { equipmentLines = it },
                        placeholder = "Equipment item"
                    )
                }

                // ── SUPPLIES / MATERIALS ──
                ProposalSection(label = "SUPPLIES & MATERIALS") {
                    DynamicListField(
                        lines = supplyLines,
                        onLinesChange = { supplyLines = it },
                        placeholder = "Material or supply"
                    )
                }

                // ── CREW SIZE ──
                ProposalSection(label = "CREW SIZE") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val crewNum = crewSizeText.toIntOrNull() ?: 1
                        Text(
                            text = "[-]",
                            style = ConsoleTheme.action,
                            modifier = Modifier
                                .clickable {
                                    if (crewNum > 1) crewSizeText = (crewNum - 1).toString()
                                }
                                .padding(8.dp)
                        )
                        Text(
                            text = "$crewNum person${if (crewNum != 1) "s" else ""}",
                            style = ConsoleTheme.bodyBold
                        )
                        Text(
                            text = "[+]",
                            style = ConsoleTheme.action,
                            modifier = Modifier
                                .clickable {
                                    crewSizeText = (crewNum + 1).toString()
                                }
                                .padding(8.dp)
                        )
                    }
                }
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
                            clientName.trim().ifBlank { null },
                            taskLines.filter { it.isNotBlank() },
                            equipmentLines.filter { it.isNotBlank() },
                            supplyLines.filter { it.isNotBlank() },
                            crewSizeText.toIntOrNull() ?: 1
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
// PROPOSAL FORM HELPERS
// ════════════════════════════════════════════════════════════════════

@Composable
private fun ProposalSection(label: String, content: @Composable () -> Unit) {
    Column {
        Text(text = label, style = ConsoleTheme.captionBold)
        Spacer(modifier = Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun ProposalTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, style = ConsoleTheme.caption) },
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

@Composable
private fun DynamicListField(
    lines: List<String>,
    onLinesChange: (List<String>) -> Unit,
    placeholder: String
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEachIndexed { index, line ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${index + 1}.",
                    style = ConsoleTheme.caption,
                    modifier = Modifier.width(24.dp)
                )
                TextField(
                    value = line,
                    onValueChange = { newValue ->
                        val updated = lines.toMutableList()
                        updated[index] = newValue
                        onLinesChange(updated)
                    },
                    placeholder = { Text(placeholder, style = ConsoleTheme.caption) },
                    modifier = Modifier.weight(1f),
                    textStyle = ConsoleTheme.bodySmall,
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedIndicatorColor = ConsoleTheme.accent,
                        unfocusedIndicatorColor = ConsoleTheme.text.copy(alpha = 0.1f)
                    )
                )
                if (lines.size > 1) {
                    Text(
                        text = "[x]",
                        style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted),
                        modifier = Modifier
                            .clickable {
                                val updated = lines.toMutableList()
                                updated.removeAt(index)
                                onLinesChange(updated)
                            }
                            .padding(4.dp)
                    )
                }
            }
        }

        Text(
            text = "[+] Add",
            style = ConsoleTheme.caption.copy(color = ConsoleTheme.accent),
            modifier = Modifier
                .clickable { onLinesChange(lines + "") }
                .padding(vertical = 4.dp)
        )
    }
}

// ════════════════════════════════════════════════════════════════════
// INTENT DETAIL DIALOG
// ════════════════════════════════════════════════════════════════════

@Composable
fun IntentDetailDialog(
    intent: IntentData,
    version: IntentVersionData,
    onDismiss: () -> Unit,
    onCreateJob: (IntentVersionData) -> Unit = {}
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
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                when (version.status) {
                    IntentStatus.DRAFT -> DraftIntentUI(intent, version, onDismiss)
                    IntentStatus.PROPOSED -> ProposedIntentUI(intent, version, onDismiss)
                    IntentStatus.CONFIRMED -> ConfirmedIntentUI(intent, version, onDismiss, onCreateJob)
                    IntentStatus.SUPERSEDED -> SupersededIntentUI(intent, version, onDismiss)
                }
            }
        },
        confirmButton = {
            when (version.status) {
                IntentStatus.DRAFT -> {
                    val enabled = version.canPropose()
                    Text(
                        text = "[>] PROPOSE",
                        style = if (enabled) ConsoleTheme.action
                            else ConsoleTheme.action.copy(color = ConsoleTheme.textDim),
                        modifier = Modifier
                            .clickable(enabled = enabled) {
                                IntentRepository.proposeVersion(version.id)
                                onDismiss()
                            }
                            .padding(8.dp)
                    )
                }
                IntentStatus.PROPOSED -> {
                    val enabled = version.canConfirm()
                    Text(
                        text = "[v] CONFIRM",
                        style = if (enabled) ConsoleTheme.action
                            else ConsoleTheme.action.copy(color = ConsoleTheme.textDim),
                        modifier = Modifier
                            .clickable(enabled = enabled) {
                                IntentRepository.confirmVersion(version.id, UserPreferences.getUserId())
                                onDismiss()
                            }
                            .padding(8.dp)
                    )
                }
                IntentStatus.CONFIRMED -> {
                    Text(
                        text = "[+ JOB] CREATE JOB",
                        style = ConsoleTheme.action,
                        modifier = Modifier
                            .clickable {
                                onCreateJob(version)
                                onDismiss()
                            }
                            .padding(8.dp)
                    )
                }
                IntentStatus.SUPERSEDED -> Unit
            }
        },
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
// PROPOSAL DETAIL VIEW (shared across statuses)
// ════════════════════════════════════════════════════════════════════

@Composable
private fun ProposalDetails(version: IntentVersionData) {
    IntentDetailSection(label = "Version", value = "v${version.versionNumber}")
    IntentDetailSection(label = "Scope", value = version.scopeStatement)

    if (version.parties.isNotEmpty()) {
        IntentDetailSection(label = "Client", value = version.parties.joinToString(", "))
    }

    if (version.taskDescriptions.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "TASKS (${version.taskDescriptions.size})", style = ConsoleTheme.captionBold)
        version.taskDescriptions.forEachIndexed { i, task ->
            Text(
                text = "  ${i + 1}. $task",
                style = ConsoleTheme.bodySmall,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
    }

    if (version.equipmentNeeded.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "EQUIPMENT (${version.equipmentNeeded.size})", style = ConsoleTheme.captionBold)
        version.equipmentNeeded.forEach { item ->
            Text(
                text = "  - $item",
                style = ConsoleTheme.bodySmall,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
    }

    if (version.suppliesNeeded.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "SUPPLIES & MATERIALS (${version.suppliesNeeded.size})", style = ConsoleTheme.captionBold)
        version.suppliesNeeded.forEach { item ->
            Text(
                text = "  - $item",
                style = ConsoleTheme.bodySmall,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
    }

    Spacer(modifier = Modifier.height(4.dp))
    IntentDetailSection(label = "Crew Size", value = "${version.crewSize} person${if (version.crewSize != 1) "s" else ""}")

    if (version.intendedJobIds.isNotEmpty()) {
        IntentDetailSection(label = "Linked Jobs", value = "${version.intendedJobIds.size}")
    }
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
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ProposalDetails(version)
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
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "PROPOSED — AWAITING CONFIRMATION",
            style = ConsoleTheme.captionBold,
            color = ConsoleTheme.accent
        )
        Spacer(modifier = Modifier.height(4.dp))

        ProposalDetails(version)
    }
}

// ════════════════════════════════════════════════════════════════════
// CONFIRMED INTENT UI
// ════════════════════════════════════════════════════════════════════

@Composable
private fun ConfirmedIntentUI(
    intent: IntentData,
    version: IntentVersionData,
    onDismiss: () -> Unit,
    onCreateJob: (IntentVersionData) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "CONFIRMED — Ready for job execution",
            style = ConsoleTheme.captionBold,
            color = ConsoleTheme.accent
        )
        Spacer(modifier = Modifier.height(4.dp))

        ProposalDetails(version)

        if (version.confirmedBy != null) {
            IntentDetailSection(label = "Confirmed by", value = version.confirmedBy)
        }
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
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "SUPERSEDED — See newer version",
            style = ConsoleTheme.captionBold,
            color = ConsoleTheme.textMuted
        )
        Spacer(modifier = Modifier.height(4.dp))
        ProposalDetails(version)
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
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
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
