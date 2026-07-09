package com.guildofsmiths.trademesh.ui.newjob

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.guildofsmiths.trademesh.data.ClientInfo
import com.guildofsmiths.trademesh.data.ClientRepository
import com.guildofsmiths.trademesh.data.TradeDefaults
import com.guildofsmiths.trademesh.data.UserPreferences
import com.guildofsmiths.trademesh.ui.ConsoleHeader
import com.guildofsmiths.trademesh.ui.ConsoleSeparator
import com.guildofsmiths.trademesh.ui.theme2.LocalSmithColors
import com.guildofsmiths.trademesh.ui.theme2.SmithType
import com.guildofsmiths.trademesh.ui.jobboard.Job
import com.guildofsmiths.trademesh.ui.jobboard.Material

// ════════════════════════════════════════════════════════════════════
// DATA MODEL
// ════════════════════════════════════════════════════════════════════

data class NewJobData(
    val clientName: String,
    val clientPhone: String,
    val clientAddress: String,
    val description: String,
    val trade: String,
    val taskDescriptions: List<String>,
    val equipmentList: List<String>,
    val materials: List<Material>,
    val crewSize: Int,
    val estimatedDays: Int,
    val laborCost: Double,
    val materialsCost: Double,
    val totalCost: Double,
    // Parsed start date (ms). Drives the calendar entry + Scheduled status when
    // a job is created from the guided flow. Null only if the entered date was
    // blank/unparseable.
    val estimatedStartDate: Long?
)

/**
 * Parse the wizard's free-text "MM/DD/YYYY" start-date field to an epoch-ms at
 * 09:00 local. Returns null when blank or unparseable (caller treats that as
 * "no scheduled date").
 */
private fun parseStartDate(text: String): Long? {
    val t = text.trim()
    if (t.isBlank()) return null
    val fmt = java.text.SimpleDateFormat("MM/dd/yyyy", java.util.Locale.US).apply { isLenient = false }
    val parsed = runCatching { fmt.parse(t) }.getOrNull() ?: return null
    val cal = java.util.Calendar.getInstance()
    cal.time = parsed
    cal.set(java.util.Calendar.HOUR_OF_DAY, 9)
    cal.set(java.util.Calendar.MINUTE, 0)
    cal.set(java.util.Calendar.SECOND, 0)
    cal.set(java.util.Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

// ════════════════════════════════════════════════════════════════════
// MAIN FLOW
// ════════════════════════════════════════════════════════════════════

@Composable
fun NewJobFlow(
    onBack: () -> Unit,
    onJobCreated: (NewJobData) -> Unit,
    allJobs: List<Job> = emptyList()
) {
    val colors = LocalSmithColors.current
    // Navigation state
    var currentStep by remember { mutableStateOf(1) }

    // Step 1 — Client
    var clientName by remember { mutableStateOf("") }
    var clientPhone by remember { mutableStateOf("") }
    var clientAddress by remember { mutableStateOf("") }

    // Step 2 — Scope
    var description by remember { mutableStateOf("") }
    var selectedTrade by remember { mutableStateOf(UserPreferences.getPrimaryTrade()) }

    // Step 3 — What's Needed
    var tasks by remember { mutableStateOf(listOf("")) }
    var equipment by remember { mutableStateOf(listOf("")) }
    var materials by remember { mutableStateOf(listOf(Material(name = "", quantity = 1.0, unit = "ea", unitCost = 0.0))) }
    var crewSize by remember { mutableStateOf(1) }
    var showSuggestions by remember { mutableStateOf(false) }

    // Step 4 — Timeline & Price
    var estimatedStartDate by remember { mutableStateOf("") }
    var estimatedDays by remember { mutableStateOf("1") }
    val hourlyRate = UserPreferences.getHourlyRate()
    val daysInt = estimatedDays.toIntOrNull() ?: 1
    var laborCost by remember(daysInt) {
        mutableStateOf(daysInt * 8.0 * hourlyRate)
    }
    val materialsCostCalc = materials.sumOf { it.quantity * it.unitCost }
    var materialsCostOverride by remember(materialsCostCalc) {
        mutableStateOf(materialsCostCalc)
    }
    val totalCost = laborCost + materialsCostOverride

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgBase)
    ) {
        // Header with back navigation
        ConsoleHeader(
            title = when (currentStep) {
                1 -> "NEW JOB — CLIENT"
                2 -> "NEW JOB — SCOPE"
                3 -> "NEW JOB — RESOURCES"
                4 -> "NEW JOB — TIMELINE"
                else -> "NEW JOB"
            },
            subtitle = "Step $currentStep of 4",
            onBackClick = {
                if (currentStep > 1) currentStep-- else onBack()
            }
        )

        ConsoleSeparator()

        // Progress bar
        StepProgressBar(currentStep = currentStep, totalSteps = 4)

        ConsoleSeparator()

        // Step content
        Box(modifier = Modifier.weight(1f)) {
            when (currentStep) {
                1 -> StepClient(
                    clientName = clientName,
                    clientPhone = clientPhone,
                    clientAddress = clientAddress,
                    onClientNameChange = { clientName = it },
                    onClientPhoneChange = { clientPhone = it },
                    onClientAddressChange = { clientAddress = it },
                    existingClients = remember(allJobs) { ClientRepository.getClients(allJobs) },
                    onClientSelected = { client ->
                        clientName = client.name
                        clientPhone = client.phone
                        clientAddress = client.address
                    }
                )
                2 -> StepScope(
                    description = description,
                    onDescriptionChange = { description = it },
                    selectedTrade = selectedTrade,
                    onTradeSelected = { selectedTrade = it }
                )
                3 -> StepResources(
                    trade = selectedTrade,
                    tasks = tasks,
                    equipment = equipment,
                    materials = materials,
                    crewSize = crewSize,
                    showSuggestions = showSuggestions,
                    onTasksChange = { tasks = it },
                    onEquipmentChange = { equipment = it },
                    onMaterialsChange = { materials = it },
                    onCrewSizeChange = { crewSize = it },
                    onToggleSuggestions = { showSuggestions = !showSuggestions }
                )
                4 -> StepTimeline(
                    estimatedStartDate = estimatedStartDate,
                    estimatedDays = estimatedDays,
                    laborCost = laborCost,
                    materialsCost = materialsCostOverride,
                    totalCost = totalCost,
                    onStartDateChange = { estimatedStartDate = it },
                    onEstimatedDaysChange = { estimatedDays = it },
                    onLaborCostChange = { laborCost = it },
                    onMaterialsCostChange = { materialsCostOverride = it }
                )
            }
        }

        ConsoleSeparator()

        // Bottom navigation button
        val isStep1Valid = clientName.isNotBlank()
        val canAdvance = when (currentStep) {
            1 -> isStep1Valid
            else -> true
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.bgPanel)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.End
        ) {
            val buttonLabel = if (currentStep == 4) "CREATE JOB" else "NEXT →"
            val buttonColor = if (canAdvance) colors.accent else colors.inkMuted

            Text(
                text = buttonLabel,
                style = SmithType.action.copy(color = buttonColor, fontSize = 15.sp),
                modifier = Modifier
                    .then(
                        if (canAdvance) Modifier.clickable {
                            if (currentStep < 4) {
                                currentStep++
                            } else {
                                val cleanTasks = tasks.filter { it.isNotBlank() }
                                val cleanEquipment = equipment.filter { it.isNotBlank() }
                                val cleanMaterials = materials.filter { it.name.isNotBlank() }
                                onJobCreated(
                                    NewJobData(
                                        clientName = clientName.trim(),
                                        clientPhone = clientPhone.trim(),
                                        clientAddress = clientAddress.trim(),
                                        description = description.trim(),
                                        trade = selectedTrade.trim(),
                                        taskDescriptions = cleanTasks,
                                        equipmentList = cleanEquipment,
                                        materials = cleanMaterials,
                                        crewSize = crewSize,
                                        estimatedDays = daysInt,
                                        laborCost = laborCost,
                                        materialsCost = materialsCostOverride,
                                        totalCost = totalCost,
                                        estimatedStartDate = parseStartDate(estimatedStartDate)
                                    )
                                )
                            }
                        } else Modifier
                    )
                    .padding(8.dp)
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// PROGRESS BAR
// ════════════════════════════════════════════════════════════════════

@Composable
private fun StepProgressBar(currentStep: Int, totalSteps: Int) {
    val colors = LocalSmithColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for (i in 1..totalSteps) {
            val active = i <= currentStep
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(3.dp)
                    .background(if (active) colors.accent else colors.line)
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// STEP 1 — CLIENT
// ════════════════════════════════════════════════════════════════════

@Composable
private fun StepClient(
    clientName: String,
    clientPhone: String,
    clientAddress: String,
    onClientNameChange: (String) -> Unit,
    onClientPhoneChange: (String) -> Unit,
    onClientAddressChange: (String) -> Unit,
    existingClients: List<ClientInfo> = emptyList(),
    onClientSelected: (ClientInfo) -> Unit = {}
) {
    val colors = LocalSmithColors.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Existing clients picker
        if (existingClients.isNotEmpty()) {
            item {
                SectionLabel("EXISTING CLIENTS")
            }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.bgPanel)
                        .border(1.dp, colors.line)
                        .padding(4.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    existingClients.forEach { client ->
                        val isSelected = client.name.equals(clientName, ignoreCase = true)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (isSelected) Modifier.background(colors.accent.copy(alpha = 0.10f))
                                    else Modifier
                                )
                                .clickable { onClientSelected(client) }
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = client.name,
                                    style = SmithType.bodySmall.copy(
                                        color = if (isSelected) colors.accent else colors.ink
                                    )
                                )
                                if (client.address.isNotBlank()) {
                                    Text(
                                        text = "${client.jobCount} jobs · ${client.address.take(30)}",
                                        style = SmithType.caption.copy(color = colors.inkMuted)
                                    )
                                }
                            }
                            if (isSelected) {
                                Text("✓", style = SmithType.bodySmall.copy(color = colors.accent))
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    text = "— or enter new client below —",
                    style = SmithType.caption.copy(color = colors.inkMuted),
                    modifier = Modifier.fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally)
                )
            }
        }

        item {
            SectionLabel("CLIENT INFO")
        }
        item {
            ConsoleField(
                label = "CLIENT NAME *",
                value = clientName,
                hint = "e.g. Jane Smith",
                onValueChange = onClientNameChange,
                modifier = Modifier.testTag("solo_e2e_newjob_client_name")
            )
        }
        item {
            ConsoleField(
                label = "PHONE",
                value = clientPhone,
                hint = "e.g. 555-867-5309",
                onValueChange = onClientPhoneChange,
                keyboardType = KeyboardType.Phone
            )
        }
        item {
            ConsoleField(
                label = "JOB SITE ADDRESS",
                value = clientAddress,
                hint = "e.g. 1234 Elm St, Denver CO",
                onValueChange = onClientAddressChange,
                modifier = Modifier.testTag("solo_e2e_newjob_client_address")
            )
        }
        item {
            if (clientName.isBlank()) {
                Text(
                    text = "* Client name required to continue.",
                    style = SmithType.caption.copy(color = colors.attention)
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// STEP 2 — SCOPE
// ════════════════════════════════════════════════════════════════════

@Composable
private fun StepScope(
    description: String,
    onDescriptionChange: (String) -> Unit,
    selectedTrade: String,
    onTradeSelected: (String) -> Unit
) {
    val colors = LocalSmithColors.current
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionLabel("TRADE")
        }
        item {
            com.guildofsmiths.trademesh.ui.components.TradePickerField(
                selected = selectedTrade,
                onTradeSelected = onTradeSelected
            )
        }
        item {
            SectionLabel("SCOPE OF WORK")
        }
        item {
            ConsoleMultilineField(
                label = "DESCRIBE THE WORK",
                value = description,
                hint = "What needs to be done? Provide details about the job...",
                onValueChange = onDescriptionChange,
                minLines = 5
            )
        }
        item {
            // Photo placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(colors.bgPanel)
                    .border(1.dp, colors.line)
                    .clickable { /* camera integration — future task */ },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "[  ADD PHOTOS  ]",
                    style = SmithType.action.copy(color = colors.accent)
                )
            }
        }
        item {
            Text(
                text = "Photo attachment available in a future update.",
                style = SmithType.caption.copy(color = colors.inkMuted)
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// STEP 3 — RESOURCES
// ════════════════════════════════════════════════════════════════════

@Composable
private fun StepResources(
    trade: String,
    tasks: List<String>,
    equipment: List<String>,
    materials: List<Material>,
    crewSize: Int,
    showSuggestions: Boolean,
    onTasksChange: (List<String>) -> Unit,
    onEquipmentChange: (List<String>) -> Unit,
    onMaterialsChange: (List<Material>) -> Unit,
    onCrewSizeChange: (Int) -> Unit,
    onToggleSuggestions: () -> Unit
) {
    val colors = LocalSmithColors.current
    val tradeDefaults = TradeDefaults.getForTrade(
        trade.ifBlank { UserPreferences.getOccupation() }
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // ── Suggestions ──────────────────────────────────────────────
        if (tradeDefaults != null) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SectionLabel("TRADE SUGGESTIONS")
                    Text(
                        text = if (showSuggestions) "[ HIDE ]" else "[ SUGGESTIONS ]",
                        style = SmithType.action.copy(color = colors.accent),
                        modifier = Modifier.clickable { onToggleSuggestions() }
                    )
                }
            }
            if (showSuggestions) {
                item {
                    SuggestionPanel(
                        tradeDefault = tradeDefaults,
                        currentTasks = tasks,
                        currentEquipment = equipment,
                        currentMaterials = materials,
                        onAddTask = { onTasksChange(tasks + it) },
                        onAddEquipment = { onEquipmentChange(equipment + it) },
                        onAddMaterial = { onMaterialsChange(materials + it) }
                    )
                }
            }
        }

        // ── Tasks ─────────────────────────────────────────────────────
        item {
            SectionLabel("TASKS")
        }
        itemsIndexed(tasks) { index, task ->
            DynamicListRow(
                value = task,
                hint = "e.g. Install outlet",
                onValueChange = { updated ->
                    onTasksChange(tasks.toMutableList().also { it[index] = updated })
                },
                onRemove = if (tasks.size > 1) ({
                    onTasksChange(tasks.toMutableList().also { it.removeAt(index) })
                }) else null
            )
        }
        item {
            AddRowButton(label = "+ ADD TASK") {
                onTasksChange(tasks + "")
            }
        }

        // ── Equipment ─────────────────────────────────────────────────
        item {
            SectionLabel("EQUIPMENT")
        }
        itemsIndexed(equipment) { index, equip ->
            DynamicListRow(
                value = equip,
                hint = "e.g. Multimeter",
                onValueChange = { updated ->
                    onEquipmentChange(equipment.toMutableList().also { it[index] = updated })
                },
                onRemove = if (equipment.size > 1) ({
                    onEquipmentChange(equipment.toMutableList().also { it.removeAt(index) })
                }) else null
            )
        }
        item {
            AddRowButton(label = "+ ADD EQUIPMENT") {
                onEquipmentChange(equipment + "")
            }
        }

        // ── Materials ─────────────────────────────────────────────────
        item {
            SectionLabel("MATERIALS")
        }
        itemsIndexed(materials) { index, mat ->
            MaterialRow(
                material = mat,
                onUpdate = { updated ->
                    onMaterialsChange(materials.toMutableList().also { it[index] = updated })
                },
                onRemove = if (materials.size > 1) ({
                    onMaterialsChange(materials.toMutableList().also { it.removeAt(index) })
                }) else null
            )
        }
        item {
            AddRowButton(label = "+ ADD MATERIAL") {
                onMaterialsChange(materials + Material(name = "", quantity = 1.0, unit = "ea", unitCost = 0.0))
            }
        }

        // ── Crew Size ─────────────────────────────────────────────────
        item {
            SectionLabel("CREW SIZE")
        }
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "−",
                    style = SmithType.header.copy(color = colors.accent),
                    modifier = Modifier.clickable { if (crewSize > 1) onCrewSizeChange(crewSize - 1) }
                )
                Text(
                    text = "$crewSize",
                    style = SmithType.header.copy(color = colors.ink)
                )
                Text(
                    text = "+",
                    style = SmithType.header.copy(color = colors.accent),
                    modifier = Modifier.clickable { onCrewSizeChange(crewSize + 1) }
                )
                Text(
                    text = if (crewSize == 1) "person" else "people",
                    style = SmithType.bodySmall.copy(color = colors.inkMuted)
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// STEP 4 — TIMELINE & PRICE
// ════════════════════════════════════════════════════════════════════

@Composable
private fun StepTimeline(
    estimatedStartDate: String,
    estimatedDays: String,
    laborCost: Double,
    materialsCost: Double,
    totalCost: Double,
    onStartDateChange: (String) -> Unit,
    onEstimatedDaysChange: (String) -> Unit,
    onLaborCostChange: (Double) -> Unit,
    onMaterialsCostChange: (Double) -> Unit
) {
    val colors = LocalSmithColors.current
    var laborCostText by remember(laborCost) { mutableStateOf("%.2f".format(laborCost)) }
    var materialsCostText by remember(materialsCost) { mutableStateOf("%.2f".format(materialsCost)) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SectionLabel("TIMELINE")
        }
        item {
            ConsoleField(
                label = "ESTIMATED START DATE",
                value = estimatedStartDate,
                hint = "MM/DD/YYYY",
                onValueChange = onStartDateChange,
                keyboardType = KeyboardType.Number,
                modifier = Modifier.testTag("solo_e2e_newjob_start_date")
            )
        }
        item {
            ConsoleField(
                label = "ESTIMATED DURATION (days)",
                value = estimatedDays,
                hint = "e.g. 3",
                onValueChange = onEstimatedDaysChange,
                keyboardType = KeyboardType.Number
            )
        }

        item { ConsoleSeparator() }

        item {
            SectionLabel("ESTIMATE")
        }
        item {
            Text(
                text = "LABOR COST",
                style = SmithType.caption.copy(color = colors.inkMuted)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "$", style = SmithType.bodySmall.copy(color = colors.inkMuted))
                Spacer(modifier = Modifier.width(4.dp))
                BasicTextField(
                    value = laborCostText,
                    onValueChange = { v ->
                        laborCostText = v
                        v.toDoubleOrNull()?.let { onLaborCostChange(it) }
                    },
                    textStyle = SmithType.body.copy(color = colors.ink),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    cursorBrush = SolidColor(colors.ink),
                    modifier = Modifier.width(160.dp)
                )
            }
            Text(
                text = "auto-calc: ${estimatedDays.toIntOrNull() ?: 1} days × 8 hrs × ${"%.2f".format(UserPreferences.getHourlyRate())}/hr",
                style = SmithType.caption.copy(color = colors.inkMuted)
            )
        }
        item {
            Text(
                text = "MATERIALS COST",
                style = SmithType.caption.copy(color = colors.inkMuted)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "$", style = SmithType.bodySmall.copy(color = colors.inkMuted))
                Spacer(modifier = Modifier.width(4.dp))
                BasicTextField(
                    value = materialsCostText,
                    onValueChange = { v ->
                        materialsCostText = v
                        v.toDoubleOrNull()?.let { onMaterialsCostChange(it) }
                    },
                    textStyle = SmithType.body.copy(color = colors.ink),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    cursorBrush = SolidColor(colors.ink),
                    modifier = Modifier.width(160.dp)
                )
            }
            Text(
                text = "summed from materials list",
                style = SmithType.caption.copy(color = colors.inkMuted)
            )
        }

        item { ConsoleSeparator() }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "TOTAL ESTIMATE", style = SmithType.bodyBold.copy(color = colors.ink))
                Text(
                    text = "$${"%.2f".format(totalCost)}",
                    style = SmithType.header.copy(color = colors.accent)
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// SUGGESTION PANEL
// ════════════════════════════════════════════════════════════════════

@Composable
private fun SuggestionPanel(
    tradeDefault: com.guildofsmiths.trademesh.data.TradeDefault,
    currentTasks: List<String>,
    currentEquipment: List<String>,
    currentMaterials: List<Material>,
    onAddTask: (String) -> Unit,
    onAddEquipment: (String) -> Unit,
    onAddMaterial: (Material) -> Unit
) {
    val colors = LocalSmithColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bgPanel)
            .border(1.dp, colors.line)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = "COMMON TASKS", style = SmithType.captionBold.copy(color = colors.inkMuted))
        FlowRow(tradeDefault.commonTasks) { task ->
            val alreadyAdded = currentTasks.any { it.equals(task, ignoreCase = true) }
            SuggestionChip(label = task, added = alreadyAdded) {
                if (!alreadyAdded) onAddTask(task)
            }
        }

        ConsoleSeparator()

        Text(text = "COMMON EQUIPMENT", style = SmithType.captionBold.copy(color = colors.inkMuted))
        FlowRow(tradeDefault.commonEquipment) { equip ->
            val alreadyAdded = currentEquipment.any { it.equals(equip, ignoreCase = true) }
            SuggestionChip(label = equip, added = alreadyAdded) {
                if (!alreadyAdded) onAddEquipment(equip)
            }
        }

        ConsoleSeparator()

        Text(text = "COMMON MATERIALS", style = SmithType.captionBold.copy(color = colors.inkMuted))
        FlowRow(tradeDefault.commonMaterials) { mat ->
            val alreadyAdded = currentMaterials.any { it.name.equals(mat.name, ignoreCase = true) }
            SuggestionChip(label = "${mat.name} (${mat.unit})", added = alreadyAdded) {
                if (!alreadyAdded) onAddMaterial(
                    Material(name = mat.name, unit = mat.unit, unitCost = mat.typicalPrice, quantity = 1.0)
                )
            }
        }
    }
}

@Composable
private fun <T> FlowRow(items: List<T>, itemContent: @Composable (T) -> Unit) {
    // Simple wrapping row using Column + Row batches
    val rows = items.chunked(3)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowItems.forEach { item ->
                    itemContent(item)
                }
            }
        }
    }
}

@Composable
private fun SuggestionChip(label: String, added: Boolean, onClick: () -> Unit) {
    val colors = LocalSmithColors.current
    // Selected = accent fill + on-accent text, the app's standard selected-pill
    // treatment (theme toggle, stage chips). accent-on-accent was invisible.
    val bg = if (added) colors.accent else colors.bgBase
    val textColor = if (added) colors.inkOnAccent else colors.inkMuted
    Box(
        modifier = Modifier
            .background(bg)
            .border(1.dp, colors.line)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = if (added) "+ $label" else label,
            style = SmithType.caption.copy(color = textColor)
        )
    }
}

// ════════════════════════════════════════════════════════════════════
// REUSABLE COMPONENTS
// ════════════════════════════════════════════════════════════════════

@Composable
private fun SectionLabel(text: String) {
    val colors = LocalSmithColors.current
    Text(
        text = text,
        style = SmithType.captionBold.copy(
            color = colors.inkMuted,
            letterSpacing = 1.sp
        )
    )
}

@Composable
private fun ConsoleField(
    label: String,
    value: String,
    hint: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier
) {
    val colors = LocalSmithColors.current
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, style = SmithType.caption.copy(color = colors.inkMuted))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.bgPanel)
                .border(1.dp, colors.line)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            if (value.isEmpty()) {
                Text(text = hint, style = SmithType.body.copy(color = colors.inkMuted))
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = SmithType.body.copy(color = colors.ink),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                cursorBrush = SolidColor(colors.ink),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ConsoleMultilineField(
    label: String,
    value: String,
    hint: String,
    onValueChange: (String) -> Unit,
    minLines: Int = 3
) {
    val colors = LocalSmithColors.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = label, style = SmithType.caption.copy(color = colors.inkMuted))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.bgPanel)
                .border(1.dp, colors.line)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            if (value.isEmpty()) {
                Text(text = hint, style = SmithType.body.copy(color = colors.inkMuted))
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = SmithType.body.copy(color = colors.ink),
                cursorBrush = SolidColor(colors.ink),
                minLines = minLines,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun DynamicListRow(
    value: String,
    hint: String,
    onValueChange: (String) -> Unit,
    onRemove: (() -> Unit)?
) {
    val colors = LocalSmithColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(text = "▸", style = SmithType.caption.copy(color = colors.inkMuted))
        Box(
            modifier = Modifier
                .weight(1f)
                .background(colors.bgPanel)
                .border(1.dp, colors.line)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            if (value.isEmpty()) {
                Text(text = hint, style = SmithType.bodySmall.copy(color = colors.inkMuted))
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = SmithType.bodySmall.copy(color = colors.inkMuted),
                cursorBrush = SolidColor(colors.ink),
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (onRemove != null) {
            Text(
                text = "✕",
                style = SmithType.caption.copy(color = colors.statusError),
                modifier = Modifier.clickable { onRemove() }
            )
        } else {
            Spacer(modifier = Modifier.width(16.dp))
        }
    }
}

@Composable
private fun MaterialRow(
    material: Material,
    onUpdate: (Material) -> Unit,
    onRemove: (() -> Unit)?
) {
    val colors = LocalSmithColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bgPanel)
            .border(1.dp, colors.line)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "MATERIAL", style = SmithType.caption.copy(color = colors.inkMuted))
            if (onRemove != null) {
                Text(
                    text = "✕ REMOVE",
                    style = SmithType.caption.copy(color = colors.statusError),
                    modifier = Modifier.clickable { onRemove() }
                )
            }
        }
        // Name
        BasicTextField(
            value = material.name,
            onValueChange = { onUpdate(material.copy(name = it)) },
            textStyle = SmithType.bodySmall.copy(color = colors.inkMuted),
            cursorBrush = SolidColor(colors.ink),
            decorationBox = { inner ->
                Box(modifier = Modifier.fillMaxWidth()) {
                    if (material.name.isEmpty()) {
                        Text(text = "Name (e.g. 12/2 Romex)", style = SmithType.bodySmall.copy(color = colors.inkMuted))
                    }
                    inner()
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Quantity
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "QTY", style = SmithType.caption.copy(color = colors.inkMuted))
                BasicTextField(
                    value = if (material.quantity == 0.0) "" else material.quantity.toString().trimEnd('0').trimEnd('.'),
                    onValueChange = { v -> onUpdate(material.copy(quantity = v.toDoubleOrNull() ?: 1.0)) },
                    textStyle = SmithType.bodySmall.copy(color = colors.inkMuted),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    cursorBrush = SolidColor(colors.ink),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            // Unit
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "UNIT", style = SmithType.caption.copy(color = colors.inkMuted))
                BasicTextField(
                    value = material.unit,
                    onValueChange = { v -> onUpdate(material.copy(unit = v)) },
                    textStyle = SmithType.bodySmall.copy(color = colors.inkMuted),
                    cursorBrush = SolidColor(colors.ink),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            // Unit Price
            Column(modifier = Modifier.weight(2f)) {
                Text(text = "UNIT PRICE ($)", style = SmithType.caption.copy(color = colors.inkMuted))
                BasicTextField(
                    value = if (material.unitCost == 0.0) "" else "%.2f".format(material.unitCost),
                    onValueChange = { v -> onUpdate(material.copy(unitCost = v.toDoubleOrNull() ?: 0.0)) },
                    textStyle = SmithType.bodySmall.copy(color = colors.inkMuted),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    cursorBrush = SolidColor(colors.ink),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        // Line total
        val lineTotal = material.quantity * material.unitCost
        if (lineTotal > 0.0) {
            Text(
                text = "= $${"%.2f".format(lineTotal)}",
                style = SmithType.caption.copy(color = colors.inkMuted)
            )
        }
    }
}

@Composable
private fun AddRowButton(label: String, onClick: () -> Unit) {
    val colors = LocalSmithColors.current
    Text(
        text = label,
        style = SmithType.action.copy(color = colors.accent),
        modifier = Modifier
            .clickable { onClick() }
            .padding(vertical = 4.dp)
    )
}
