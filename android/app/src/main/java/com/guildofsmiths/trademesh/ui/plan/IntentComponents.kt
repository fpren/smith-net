package com.guildofsmiths.trademesh.ui.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.guildofsmiths.trademesh.ui.Tokens2
import com.guildofsmiths.trademesh.ui.theme2.LocalSmithColors
import com.guildofsmiths.trademesh.ui.theme2.SmithButton
import com.guildofsmiths.trademesh.ui.theme2.SmithButtonVariant
import com.guildofsmiths.trademesh.ui.theme2.SmithDialog
import com.guildofsmiths.trademesh.ui.theme2.SmithType
import kotlinx.coroutines.launch

// ════════════════════════════════════════════════════════════════════
// CREATE INTENT DIALOG — Proposal Template
// ════════════════════════════════════════════════════════════════════

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
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
    val colors = LocalSmithColors.current
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

    SmithDialog(
        title = "NEW PROPOSAL",
        onDismiss = onDismiss,
        ops = true,
        actions = {
            SmithButton(
                text = "CANCEL",
                onClick = onDismiss,
                variant = SmithButtonVariant.Ghost,
                shape = RoundedCornerShape(Tokens2.RadiusOps),
            )
            Spacer(modifier = Modifier.width(8.dp))
            SmithButton(
                text = "CREATE",
                onClick = {
                    onCreate(
                        scopeStatement.trim(),
                        clientName.trim().ifBlank { null },
                        taskLines.filter { it.isNotBlank() },
                        equipmentLines.filter { it.isNotBlank() },
                        supplyLines.filter { it.isNotBlank() },
                        crewSizeText.toIntOrNull() ?: 1
                    )
                },
                enabled = scopeStatement.isNotBlank(),
                shape = RoundedCornerShape(Tokens2.RadiusOps),
            )
        },
    ) {
        Text(
            text = "Define scope, tasks, equipment, and crew",
            style = SmithType.caption.copy(color = colors.inkMuted)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp)
                .verticalScroll(rememberScrollState())
                .semantics { testTagsAsResourceId = true },
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
                // ── SCOPE ──
                ProposalSection(label = "SCOPE OF WORK *") {
                    TextField(
                        value = scopeStatement,
                        onValueChange = { scopeStatement = it },
                        placeholder = { Text("Describe the work to be performed...", style = SmithType.caption.copy(color = colors.inkMuted)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("solo_e2e_intent_scope")
                            .onFocusChanged { state ->
                                if (!state.isFocused) triggerAssist()
                            },
                        textStyle = SmithType.body.copy(color = colors.ink),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White,
                            focusedIndicatorColor = colors.accent,
                            unfocusedIndicatorColor = colors.ink.copy(alpha = 0.2f)
                        )
                    )
                    if (isAssisting) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Generating suggestions...",
                            style = SmithType.caption.copy(color = colors.accent)
                        )
                    }
                }

                // ── CLIENT ──
                ProposalSection(label = "CLIENT") {
                    ProposalTextField(
                        value = clientName,
                        onValueChange = { clientName = it },
                        placeholder = "Client or property name",
                        modifier = Modifier.testTag("solo_e2e_intent_client")
                    )
                }

                // ── TASKS ──
                ProposalSection(label = "TASKS REQUIRED") {
                    DynamicListField(
                        lines = taskLines,
                        onLinesChange = { taskLines = it },
                        placeholder = "Task description",
                        tagPrefix = "solo_e2e_intent_task"
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
                            style = SmithType.action.copy(color = colors.accent),
                            modifier = Modifier
                                .clickable {
                                    if (crewNum > 1) crewSizeText = (crewNum - 1).toString()
                                }
                                .padding(8.dp)
                        )
                        Text(
                            text = "$crewNum person${if (crewNum != 1) "s" else ""}",
                            style = SmithType.bodyBold.copy(color = colors.ink)
                        )
                        Text(
                            text = "[+]",
                            style = SmithType.action.copy(color = colors.accent),
                            modifier = Modifier
                                .clickable {
                                    crewSizeText = (crewNum + 1).toString()
                                }
                                .padding(8.dp)
                        )
                    }
                }
            }
        }
}

// ════════════════════════════════════════════════════════════════════
// PROPOSAL FORM HELPERS
// ════════════════════════════════════════════════════════════════════

@Composable
private fun ProposalSection(label: String, content: @Composable () -> Unit) {
    val colors = LocalSmithColors.current
    Column {
        Text(text = label, style = SmithType.captionBold.copy(color = colors.inkMuted))
        Spacer(modifier = Modifier.height(4.dp))
        content()
    }
}

@Composable
private fun ProposalTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val colors = LocalSmithColors.current
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, style = SmithType.caption.copy(color = colors.inkMuted)) },
        modifier = modifier.fillMaxWidth(),
        textStyle = SmithType.body.copy(color = colors.ink),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.White,
            unfocusedContainerColor = Color.White,
            focusedIndicatorColor = colors.accent,
            unfocusedIndicatorColor = colors.ink.copy(alpha = 0.2f)
        )
    )
}

@Composable
private fun DynamicListField(
    lines: List<String>,
    onLinesChange: (List<String>) -> Unit,
    placeholder: String,
    tagPrefix: String? = null
) {
    val colors = LocalSmithColors.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        lines.forEachIndexed { index, line ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${index + 1}.",
                    style = SmithType.caption.copy(color = colors.inkMuted),
                    modifier = Modifier.width(24.dp)
                )
                TextField(
                    value = line,
                    onValueChange = { newValue ->
                        val updated = lines.toMutableList()
                        updated[index] = newValue
                        onLinesChange(updated)
                    },
                    placeholder = { Text(placeholder, style = SmithType.caption.copy(color = colors.inkMuted)) },
                    modifier = (if (tagPrefix != null) Modifier.testTag("${tagPrefix}_$index") else Modifier).weight(1f),
                    textStyle = SmithType.bodySmall.copy(color = colors.ink),
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedIndicatorColor = colors.accent,
                        unfocusedIndicatorColor = colors.ink.copy(alpha = 0.1f)
                    )
                )
                if (lines.size > 1) {
                    Text(
                        text = "[x]",
                        style = SmithType.caption.copy(color = colors.inkMuted),
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
            style = SmithType.caption.copy(color = colors.accent),
            modifier = Modifier
                .then(if (tagPrefix != null) Modifier.testTag("${tagPrefix}_add") else Modifier)
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
    val colors = LocalSmithColors.current
    SmithDialog(
        title = "${version.status.icon} ${version.scopeStatement.take(30)}",
        onDismiss = onDismiss,
        ops = true,
        actions = {
            SmithButton(
                text = "CLOSE",
                onClick = onDismiss,
                variant = SmithButtonVariant.Ghost,
                shape = RoundedCornerShape(Tokens2.RadiusOps),
            )
            when (version.status) {
                IntentStatus.DRAFT -> {
                    Spacer(modifier = Modifier.width(8.dp))
                    SmithButton(
                        text = "PROPOSE",
                        onClick = {
                            IntentRepository.proposeVersion(version.id)
                            onDismiss()
                        },
                        enabled = version.canPropose(),
                        shape = RoundedCornerShape(Tokens2.RadiusOps),
                    )
                }
                IntentStatus.PROPOSED -> {
                    Spacer(modifier = Modifier.width(8.dp))
                    SmithButton(
                        text = "CONFIRM",
                        onClick = {
                            IntentRepository.confirmVersion(version.id, UserPreferences.getUserId())
                            onDismiss()
                        },
                        enabled = version.canConfirm(),
                        shape = RoundedCornerShape(Tokens2.RadiusOps),
                    )
                }
                IntentStatus.CONFIRMED -> {
                    Spacer(modifier = Modifier.width(8.dp))
                    SmithButton(
                        text = "CREATE JOB",
                        onClick = {
                            onCreateJob(version)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(Tokens2.RadiusOps),
                    )
                }
                IntentStatus.SUPERSEDED -> Unit
            }
        },
    ) {
        Text(
            text = version.status.displayName.uppercase(),
            style = SmithType.caption,
            color = colors.accent
        )
        Spacer(modifier = Modifier.height(8.dp))
        when (version.status) {
            IntentStatus.DRAFT -> DraftIntentUI(intent, version, onDismiss)
            IntentStatus.PROPOSED -> ProposedIntentUI(intent, version, onDismiss)
            IntentStatus.CONFIRMED -> ConfirmedIntentUI(intent, version, onDismiss, onCreateJob)
            IntentStatus.SUPERSEDED -> SupersededIntentUI(intent, version, onDismiss)
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// PROPOSAL DETAIL VIEW (shared across statuses)
// ════════════════════════════════════════════════════════════════════

@Composable
private fun ProposalDetails(version: IntentVersionData) {
    val colors = LocalSmithColors.current
    IntentDetailSection(label = "Version", value = "v${version.versionNumber}")
    IntentDetailSection(label = "Scope", value = version.scopeStatement)

    if (version.parties.isNotEmpty()) {
        IntentDetailSection(label = "Client", value = version.parties.joinToString(", "))
    }

    if (version.taskDescriptions.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "TASKS (${version.taskDescriptions.size})", style = SmithType.captionBold.copy(color = colors.inkMuted))
        version.taskDescriptions.forEachIndexed { i, task ->
            Text(
                text = "  ${i + 1}. $task",
                style = SmithType.bodySmall.copy(color = colors.ink),
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
    }

    if (version.equipmentNeeded.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "EQUIPMENT (${version.equipmentNeeded.size})", style = SmithType.captionBold.copy(color = colors.inkMuted))
        version.equipmentNeeded.forEach { item ->
            Text(
                text = "  - $item",
                style = SmithType.bodySmall.copy(color = colors.ink),
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
    }

    if (version.suppliesNeeded.isNotEmpty()) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "SUPPLIES & MATERIALS (${version.suppliesNeeded.size})", style = SmithType.captionBold.copy(color = colors.inkMuted))
        version.suppliesNeeded.forEach { item ->
            Text(
                text = "  - $item",
                style = SmithType.bodySmall.copy(color = colors.ink),
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
    val colors = LocalSmithColors.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "PROPOSED — AWAITING CONFIRMATION",
            style = SmithType.captionBold,
            color = colors.accent
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
    val colors = LocalSmithColors.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "CONFIRMED — Ready for job execution",
            style = SmithType.captionBold,
            color = colors.accent
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
    val colors = LocalSmithColors.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "SUPERSEDED — See newer version",
            style = SmithType.captionBold,
            color = colors.inkMuted
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
    val colors = LocalSmithColors.current
    Column(modifier = Modifier.padding(vertical = 2.dp)) {
        Text(
            text = label,
            style = SmithType.captionBold,
            color = colors.ink.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            style = SmithType.body.copy(color = colors.ink)
        )
    }
}
