package com.guildofsmiths.trademesh.ui.expenses

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.guildofsmiths.trademesh.data.ExpenseCategoryRepository
import com.guildofsmiths.trademesh.data.TimeEntryRepository
import com.guildofsmiths.trademesh.data.UserPreferences
import com.guildofsmiths.trademesh.ai.AISupervisor
import com.guildofsmiths.trademesh.ui.ConsoleHeader
import com.guildofsmiths.trademesh.ui.ConsoleTheme
import com.guildofsmiths.trademesh.ui.jobboard.FreightTerm
import com.guildofsmiths.trademesh.ui.jobboard.Job
import com.guildofsmiths.trademesh.ui.jobboard.JobBoardViewModel
import com.guildofsmiths.trademesh.ui.jobboard.JobExpense
import com.guildofsmiths.trademesh.ui.timetracking.EntryType
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun JobExpenseDetailScreen(
    jobId: String,
    viewModel: JobBoardViewModel,
    onBack: () -> Unit
) {
    val jobs by viewModel.jobs.collectAsState()
    val timeEntries by TimeEntryRepository.entries.collectAsState()
    val categories by ExpenseCategoryRepository.categories.collectAsState()
    val context = LocalContext.current

    val job = jobs.firstOrNull { it.id == jobId }
    if (job == null) {
        Column(modifier = Modifier.fillMaxSize().background(ConsoleTheme.background)) {
            ConsoleHeader(title = "EXPENSES", onBackClick = onBack)
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Job not found.", style = ConsoleTheme.body)
            }
        }
        return
    }

    var showAddSheet by remember { mutableStateOf(false) }
    var showDepositDialog by remember { mutableStateOf(false) }

    // Derived data
    val bolNumber = "GS-${jobId.take(6).uppercase()}"
    val jobEntries = timeEntries.filter { it.clockOutTime != null && (it.jobId == jobId || it.jobTitle == job.title) }
    val laborRows = remember(jobEntries, job.hourlyRate) {
        jobEntries.map { e ->
            val hours = (e.durationMinutes ?: 0) / 60.0
            val rate = when (e.entryType) {
                EntryType.OVERTIME -> job.hourlyRate * 1.5
                else -> job.hourlyRate
            }
            LaborRow(
                id = e.id,
                date = e.clockInTime,
                entryTypeLabel = e.entryType.displayName,
                hours = hours,
                rate = rate
            )
        }.sortedByDescending { it.date }
    }

    val materialRows = remember(job.materials) { job.materials }
    val expenseRows = remember(job.expenses) { job.expenses.sortedByDescending { it.incurredAt } }

    val laborSubtotal = laborRows.sumOf { it.hours * it.rate }
    val materialSubtotal = materialRows.sumOf { it.totalCost }
    val otherSubtotal = expenseRows.filter { it.category != "labor" && it.category != "material" }.sumOf { it.totalCost }
    val grandTotal = laborSubtotal + materialSubtotal + otherSubtotal + expenseRows.filter { it.category == "material" }.sumOf { it.totalCost }

    val estimatedCount = expenseRows.count { it.aiEstimated }

    val dateFmt = remember { SimpleDateFormat("MMM d yy", Locale.US) }

    Column(modifier = Modifier.fillMaxSize().background(ConsoleTheme.background)) {
        ConsoleHeader(title = "BILL OF WORK & EXPENSES", onBackClick = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // BOL header card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleTheme.surface, RoundedCornerShape(4.dp))
                    .border(0.5.dp, ConsoleTheme.text.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("BOL #: $bolNumber", style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.text))
                    Text(dateFmt.format(Date()), style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
                }
                Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(0.5.dp).background(ConsoleTheme.text.copy(alpha = 0.08f)))

                Row(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("SHIP FROM", style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.textMuted))
                        Text(UserPreferences.getBusinessName().ifBlank { "My Business" }, style = ConsoleTheme.caption)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("SHIP TO", style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.textMuted))
                        Text(job.clientName ?: "—", style = ConsoleTheme.caption)
                        if (job.clientAddress.isNotBlank()) {
                            Text(job.clientAddress, style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
                        }
                        if (job.clientPhone.isNotBlank()) {
                            Text(job.clientPhone, style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
                        }
                    }
                }

                Text(
                    "Job: ${job.title} · Stage: ${job.stage.displayName}",
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.text)
                )

                // Deposit row (tappable)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { showDepositDialog = true }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Deposit collected: $${String.format("%.2f", job.depositCollected)}${if (!job.depositNote.isNullOrBlank()) " · ${job.depositNote}" else ""}",
                        style = ConsoleTheme.caption.copy(color = ConsoleTheme.text)
                    )
                    Text("[edit]", style = ConsoleTheme.caption.copy(color = ConsoleTheme.accent))
                }
            }

            // Line items
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleTheme.surface, RoundedCornerShape(4.dp))
                    .border(0.5.dp, ConsoleTheme.text.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                    .padding(10.dp)
            ) {
                // Column headers
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Text("UNIT", style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.textMuted), modifier = Modifier.width(36.dp))
                    Text("DESCRIPTION", style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.textMuted), modifier = Modifier.weight(1f))
                    Text("QTY", style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.textMuted), modifier = Modifier.width(44.dp))
                    Text("RATE", style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.textMuted), modifier = Modifier.width(64.dp))
                    Text("TOTAL", style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.textMuted), modifier = Modifier.width(70.dp))
                }
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(ConsoleTheme.text.copy(alpha = 0.1f)))

                // LABOR rows (read-only, tap jumps to Time Tracking)
                laborRows.forEach { lr ->
                    val dateLabel = SimpleDateFormat("MMM d", Locale.US).format(Date(lr.date))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("[L]", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted), modifier = Modifier.width(36.dp))
                        Text(
                            "Labor $dateLabel · ${lr.entryTypeLabel}",
                            style = ConsoleTheme.caption.copy(color = ConsoleTheme.text),
                            modifier = Modifier.weight(1f),
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(String.format("%.2f", lr.hours), style = ConsoleTheme.caption.copy(color = ConsoleTheme.text), modifier = Modifier.width(44.dp))
                        Text(String.format("%.2f", lr.rate), style = ConsoleTheme.caption.copy(color = ConsoleTheme.text), modifier = Modifier.width(64.dp))
                        Text("$${String.format("%.2f", lr.hours * lr.rate)}", style = ConsoleTheme.caption.copy(color = ConsoleTheme.accent), modifier = Modifier.width(70.dp))
                    }
                }

                // MATERIAL rows (editable-ish — existing materials)
                materialRows.forEachIndexed { idx, m ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("[M]", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted), modifier = Modifier.width(36.dp))
                        Text(m.name, style = ConsoleTheme.caption.copy(color = ConsoleTheme.text), modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(String.format("%.2f", m.quantity), style = ConsoleTheme.caption.copy(color = ConsoleTheme.text), modifier = Modifier.width(44.dp))
                        Text(String.format("%.2f", m.unitCost), style = ConsoleTheme.caption.copy(color = ConsoleTheme.text), modifier = Modifier.width(64.dp))
                        Text("$${String.format("%.2f", m.totalCost)}", style = ConsoleTheme.caption.copy(color = ConsoleTheme.accent), modifier = Modifier.width(70.dp))
                    }
                }

                // JOB EXPENSE rows — fully editable
                expenseRows.forEach { exp ->
                    ExpenseRowEditable(
                        expense = exp,
                        onUpdate = { mutated -> viewModel.updateExpense(jobId, exp.id) { mutated } },
                        onDelete = { viewModel.deleteExpense(jobId, exp.id) }
                    )
                }

                if (laborRows.isEmpty() && materialRows.isEmpty() && expenseRows.isEmpty()) {
                    Box(Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                        Text("No line items yet.", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
                    }
                }

                Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(0.5.dp).background(ConsoleTheme.text.copy(alpha = 0.1f)))

                SubtotalRow("Subtotal Labor", laborSubtotal)
                SubtotalRow("Subtotal Materials", materialSubtotal + expenseRows.filter { it.category == "material" }.sumOf { it.totalCost })
                SubtotalRow("Subtotal Other", otherSubtotal)

                Box(Modifier.fillMaxWidth().padding(vertical = 4.dp).height(0.5.dp).background(ConsoleTheme.text.copy(alpha = 0.2f)))

                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("GRAND TOTAL", style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.text))
                    Text("$${String.format("%.2f", grandTotal)}", style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.accent))
                }

                if (job.depositCollected > 0) {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Deposit applied", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
                        Text("−$${String.format("%.2f", job.depositCollected)}", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
                    }
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Net unbilled", style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.text))
                        Text("$${String.format("%.2f", grandTotal - job.depositCollected)}", style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.accent))
                    }
                }

                if (estimatedCount > 0) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "$estimatedCount item${if (estimatedCount != 1) "s" else ""} estimated — review rates marked [AI est.]",
                        style = ConsoleTheme.caption.copy(color = ConsoleTheme.warning)
                    )
                }
            }

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ActionButton(label = "[+ Add expense]", accent = true, modifier = Modifier.weight(1f)) {
                    showAddSheet = true
                }
                ActionButton(label = "[Export PDF]", accent = false, modifier = Modifier.weight(1f)) {
                    Toast.makeText(context, "Export coming soon", Toast.LENGTH_SHORT).show()
                }
                ActionButton(label = "[Share]", accent = false, modifier = Modifier.weight(1f)) {
                    Toast.makeText(context, "Share coming soon", Toast.LENGTH_SHORT).show()
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }

    if (showAddSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            sheetState = sheetState,
            containerColor = ConsoleTheme.surface
        ) {
            AddExpenseSheet(
                job = job,
                allJobs = jobs,
                onSave = { newExp ->
                    viewModel.addExpense(jobId, newExp)
                    showAddSheet = false
                },
                onCancel = { showAddSheet = false }
            )
        }
    }

    if (showDepositDialog) {
        DepositDialog(
            current = job.depositCollected,
            currentNote = job.depositNote ?: "",
            onSave = { amount, note ->
                viewModel.updateDeposit(job.id, amount, note.ifBlank { null })
                showDepositDialog = false
            },
            onDismiss = { showDepositDialog = false }
        )
    }
}

private data class LaborRow(
    val id: String,
    val date: Long,
    val entryTypeLabel: String,
    val hours: Double,
    val rate: Double
)

@Composable
private fun SubtotalRow(label: String, amount: Double) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
        Text("$${String.format("%.2f", amount)}", style = ConsoleTheme.caption.copy(color = ConsoleTheme.text))
    }
}

@Composable
private fun ActionButton(label: String, accent: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .background(
                if (accent) ConsoleTheme.accent.copy(alpha = 0.14f) else ConsoleTheme.surface,
                RoundedCornerShape(4.dp)
            )
            .border(0.5.dp, ConsoleTheme.text.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
            .clip(RoundedCornerShape(4.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(bounded = true, color = ConsoleTheme.accent),
                onClick = onClick
            )
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = ConsoleTheme.action.copy(
                color = if (accent) ConsoleTheme.accent else ConsoleTheme.text
            )
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExpenseRowEditable(
    expense: JobExpense,
    onUpdate: (JobExpense) -> Unit,
    onDelete: () -> Unit
) {
    val def = ExpenseCategoryRepository.resolve(expense.category)
    var expanded by remember { mutableStateOf(false) }
    var editingDesc by remember { mutableStateOf(false) }
    var descDraft by remember(expense.id) { mutableStateOf(expense.description) }
    var qtyDraft by remember(expense.id) { mutableStateOf(expense.quantity.cleanStr()) }
    var rateDraft by remember(expense.id) { mutableStateOf(expense.unitCost.cleanStr()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { expanded = !expanded },
                onLongClick = { onDelete() }
            )
            .padding(vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(def.shortCode, style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted), modifier = Modifier.width(36.dp))

            // Description cell — tap to edit
            Box(modifier = Modifier.weight(1f)) {
                if (editingDesc) {
                    BasicTextField(
                        value = descDraft,
                        onValueChange = { descDraft = it },
                        singleLine = true,
                        textStyle = ConsoleTheme.caption.copy(color = ConsoleTheme.text),
                        cursorBrush = SolidColor(ConsoleTheme.cursor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ConsoleTheme.background, RoundedCornerShape(2.dp))
                            .padding(4.dp)
                    )
                } else {
                    Text(
                        text = expense.description.ifBlank { "(tap to edit)" },
                        style = ConsoleTheme.caption.copy(
                            color = if (expense.description.isBlank()) ConsoleTheme.textMuted else ConsoleTheme.text
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editingDesc = true }
                            .padding(end = 4.dp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }

            NumericCell(
                value = qtyDraft, onValueChange = { qtyDraft = it },
                onCommit = {
                    val v = qtyDraft.toDoubleOrNull()
                    if (v != null && v != expense.quantity) onUpdate(expense.copy(quantity = v))
                },
                width = 44.dp
            )
            NumericCell(
                value = rateDraft, onValueChange = { rateDraft = it },
                onCommit = {
                    val v = rateDraft.toDoubleOrNull()
                    if (v != null && v != expense.unitCost) onUpdate(expense.copy(unitCost = v, aiEstimated = false))
                },
                width = 64.dp
            )
            Text(
                "$${String.format("%.2f", expense.totalCost)}",
                style = ConsoleTheme.caption.copy(color = ConsoleTheme.accent),
                modifier = Modifier.width(70.dp)
            )
        }

        if (editingDesc) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Text("[save]",
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.accent),
                    modifier = Modifier
                        .clickable {
                            onUpdate(expense.copy(description = descDraft))
                            editingDesc = false
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
                Text("[cancel]",
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted),
                    modifier = Modifier
                        .clickable {
                            descDraft = expense.description
                            editingDesc = false
                        }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        if (expanded) {
            Spacer(Modifier.height(4.dp))
            Column(modifier = Modifier.padding(start = 36.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (expense.vendor.isNotBlank()) {
                    Text("Vendor: ${expense.vendor}", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
                }
                if (!expense.referenceNumber.isNullOrBlank()) {
                    Text("Ref: ${expense.referenceNumber}", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
                }
                Text("Unit: ${expense.unit}", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
                if (expense.hazardous) {
                    Text("[HM]", style = ConsoleTheme.caption.copy(color = ConsoleTheme.warning))
                }
                if (expense.aiEstimated) {
                    Text("[AI est.] — tap rate to override",
                        style = ConsoleTheme.caption.copy(color = ConsoleTheme.warning))
                }
                Text("Long-press row to delete",
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
            }
        }
    }
}

@Composable
private fun NumericCell(
    value: String,
    onValueChange: (String) -> Unit,
    onCommit: () -> Unit,
    width: androidx.compose.ui.unit.Dp
) {
    BasicTextField(
        value = value,
        onValueChange = { new ->
            // allow digits + one decimal
            if (new.matches(Regex("^\\d*\\.?\\d*$"))) onValueChange(new)
        },
        singleLine = true,
        textStyle = ConsoleTheme.caption.copy(color = ConsoleTheme.text),
        cursorBrush = SolidColor(ConsoleTheme.cursor),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.width(width).padding(end = 4.dp)
    )
    // commit when field loses focus — simpler: commit on every pause.
    LaunchedEffect(value) {
        kotlinx.coroutines.delay(600)
        onCommit()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddExpenseSheet(
    job: Job,
    allJobs: List<Job>,
    onSave: (JobExpense) -> Unit,
    onCancel: () -> Unit
) {
    val categories by ExpenseCategoryRepository.categories.collectAsState()
    val visible = categories.filter { !it.hidden }.sortedBy { it.sortOrder }

    var categoryId by remember { mutableStateOf(visible.firstOrNull()?.id ?: "material") }
    var description by remember { mutableStateOf("") }
    var qty by remember { mutableStateOf("1") }
    var unit by remember { mutableStateOf("ea") }
    var unitCost by remember { mutableStateOf("") }
    var vendor by remember { mutableStateOf("") }
    var refNo by remember { mutableStateOf("") }
    var hazardous by remember { mutableStateOf(false) }
    var freight by remember { mutableStateOf(FreightTerm.NA) }

    // Price autofill — historical then AI
    LaunchedEffect(description, categoryId) {
        if (description.length < 3 || unitCost.isNotBlank()) return@LaunchedEffect
        val hit = com.guildofsmiths.trademesh.data.ExpensePriceHistoryRepository
            .mostRecent(allJobs, description, categoryId)
        if (hit != null) {
            unitCost = hit.unitCost.cleanStr()
            if (vendor.isBlank()) vendor = hit.vendor
            if (unit == "ea" && hit.unit.isNotBlank()) unit = hit.unit
        } else {
            val est = AISupervisor.estimateItemPrice(description, categoryId, unit, vendor)
            if (est != null) {
                unitCost = est.cleanStr()
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("ADD EXPENSE", style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.text))

        // Category picker
        Text("Category", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            visible.forEach { c ->
                val selected = c.id == categoryId
                Box(
                    modifier = Modifier
                        .background(
                            if (selected) ConsoleTheme.accent else ConsoleTheme.background,
                            RoundedCornerShape(4.dp)
                        )
                        .border(0.5.dp, ConsoleTheme.text.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { categoryId = c.id }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        "${c.shortCode} ${c.displayName}",
                        style = ConsoleTheme.caption.copy(color = if (selected) Color.White else ConsoleTheme.text)
                    )
                }
            }
        }

        LabeledInput("Description", description) { description = it }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.weight(1f)) { LabeledInput("Qty", qty, numeric = true) { qty = it } }
            Box(modifier = Modifier.weight(1f)) { LabeledInput("Unit", unit) { unit = it } }
            Box(modifier = Modifier.weight(1f)) { LabeledInput("Rate", unitCost, numeric = true) { unitCost = it } }
        }
        LabeledInput("Vendor / Authority / Sub", vendor) { vendor = it }
        LabeledInput("Ref # (receipt / permit / invoice)", refNo) { refNo = it }

        // Freight term
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FreightTerm.entries.forEach { ft ->
                val selected = ft == freight
                Box(
                    modifier = Modifier
                        .background(
                            if (selected) ConsoleTheme.accent.copy(alpha = 0.2f) else ConsoleTheme.background,
                            RoundedCornerShape(4.dp)
                        )
                        .border(0.5.dp, ConsoleTheme.text.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                        .clip(RoundedCornerShape(4.dp))
                        .clickable { freight = ft }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(ft.displayName, style = ConsoleTheme.caption.copy(color = if (selected) ConsoleTheme.accent else ConsoleTheme.textMuted))
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .border(1.dp, ConsoleTheme.warning, RoundedCornerShape(2.dp))
                    .background(if (hazardous) ConsoleTheme.warning.copy(alpha = 0.3f) else Color.Transparent)
                    .clickable { hazardous = !hazardous }
            )
            Spacer(Modifier.width(8.dp))
            Text("Hazardous material (HM)", style = ConsoleTheme.caption.copy(color = ConsoleTheme.text))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton(label = "[cancel]", accent = false, modifier = Modifier.weight(1f), onClick = onCancel)
            ActionButton(label = "[save]", accent = true, modifier = Modifier.weight(1f)) {
                val q = qty.toDoubleOrNull() ?: 1.0
                val c = unitCost.toDoubleOrNull() ?: 0.0
                onSave(
                    JobExpense(
                        category = categoryId,
                        description = description.trim(),
                        quantity = q,
                        unit = unit.trim().ifBlank { "ea" },
                        unitCost = c,
                        vendor = vendor.trim(),
                        referenceNumber = refNo.trim().ifBlank { null },
                        hazardous = hazardous,
                        freightTerm = freight,
                        aiEstimated = false
                    )
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun LabeledInput(
    label: String,
    value: String,
    numeric: Boolean = false,
    onChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.text),
            cursorBrush = SolidColor(ConsoleTheme.cursor),
            keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
            modifier = Modifier
                .fillMaxWidth()
                .background(ConsoleTheme.background, RoundedCornerShape(2.dp))
                .border(0.5.dp, ConsoleTheme.text.copy(alpha = 0.12f), RoundedCornerShape(2.dp))
                .padding(8.dp)
        )
    }
}

@Composable
private fun DepositDialog(
    current: Double,
    currentNote: String,
    onSave: (Double, String) -> Unit,
    onDismiss: () -> Unit
) {
    var amount by remember { mutableStateOf(current.cleanStr()) }
    var note by remember { mutableStateOf(currentNote) }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .background(ConsoleTheme.surface, RoundedCornerShape(6.dp))
                .border(0.5.dp, ConsoleTheme.text.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("DEPOSIT", style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.text))
            LabeledInput("Amount", amount, numeric = true) { amount = it }
            LabeledInput("Note (check #, method)", note) { note = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton(label = "[cancel]", accent = false, modifier = Modifier.weight(1f), onClick = onDismiss)
                ActionButton(label = "[save]", accent = true, modifier = Modifier.weight(1f)) {
                    onSave(amount.toDoubleOrNull() ?: 0.0, note)
                }
            }
        }
    }
}

// Helpers

private fun Double.cleanStr(): String {
    if (this == 0.0) return ""
    return if (this == this.toLong().toDouble()) this.toLong().toString() else String.format("%.2f", this)
}
