package com.guildofsmiths.trademesh.ui.jobpipeline

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*
import com.guildofsmiths.trademesh.data.TimeEntryRepository
import com.guildofsmiths.trademesh.data.UserPreferences
import com.guildofsmiths.trademesh.ui.ConsoleHeader
import com.guildofsmiths.trademesh.ui.ConsoleSeparator
import com.guildofsmiths.trademesh.ui.ConsoleTheme
import com.guildofsmiths.trademesh.ui.invoice.InvoiceGenerator
import com.guildofsmiths.trademesh.ui.invoice.InvoiceFormatter
import com.guildofsmiths.trademesh.ui.invoice.InvoicePreviewDialog
import com.guildofsmiths.trademesh.ui.jobboard.*

@Composable
fun JobPipelineScreen(
    job: Job,
    onBack: () -> Unit,
    onStageAction: (Job, JobStage) -> Unit,
    onToggleMaterial: (Int) -> Unit,
    onClockIn: () -> Unit,
    onShareProposal: () -> Unit,
    onShareInvoice: () -> Unit,
    onAddNote: ((String) -> Unit)? = null,
    onSummarizeToday: (() -> Unit)? = null
) {
    val context = LocalContext.current
    var showInvoice by remember { mutableStateOf(false) }
    var invoiceDetailLevel by remember { mutableStateOf(com.guildofsmiths.trademesh.ui.invoice.InvoiceDetailLevel.STANDARD) }
    val timeEntries by TimeEntryRepository.entries.collectAsState()

    // Generate invoice when requested (reacts to detail level changes)
    val invoice = remember(job, showInvoice, invoiceDetailLevel) {
        if (!showInvoice) null
        else InvoiceGenerator.generateFromJob(
            job = job,
            timeEntries = timeEntries,
            providerName = UserPreferences.getUserName(),
            providerBusiness = UserPreferences.getBusinessName(),
            providerTrade = "${UserPreferences.getPrimaryTrade()} — Guild of Smiths",
            hourlyRate = UserPreferences.getHourlyRate().takeIf { it > 0 } ?: 85.0,
            taxRate = 8.25,
            detailLevel = invoiceDetailLevel
        )
    }

    // Invoice preview dialog
    if (showInvoice && invoice != null) {
        InvoicePreviewDialog(
            invoice = invoice,
            onDismiss = { showInvoice = false },
            onShare = { invoiceText ->
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "Invoice ${invoice.invoiceNumber} — ${job.clientName ?: job.title}")
                    putExtra(Intent.EXTRA_TEXT, invoiceText)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share Invoice"))
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ConsoleTheme.background)
    ) {
        ConsoleHeader(
            title = job.clientName ?: job.title,
            onBackClick = onBack
        )

        JobStageBar(currentStage = job.stage)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Client Info
            if (job.clientName?.isNotBlank() == true || job.clientPhone.isNotBlank()) {
                SectionHeader("CLIENT")
                if (job.clientName?.isNotBlank() == true) {
                    Text(text = job.clientName, style = ConsoleTheme.body)
                }
                if (job.clientPhone.isNotBlank()) {
                    Text(
                        text = job.clientPhone,
                        style = ConsoleTheme.body.copy(color = ConsoleTheme.accent),
                        modifier = Modifier.clickable {
                            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${job.clientPhone}"))
                            context.startActivity(intent)
                        }
                    )
                }
                if (job.clientAddress.isNotBlank()) {
                    Text(
                        text = job.clientAddress,
                        style = ConsoleTheme.body.copy(color = ConsoleTheme.accent),
                        modifier = Modifier.clickable {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=${Uri.encode(job.clientAddress)}"))
                            context.startActivity(intent)
                        }
                    )
                }
            }

            // Scope
            if (job.description.isNotBlank()) {
                SectionHeader("SCOPE")
                Text(text = job.description, style = ConsoleTheme.bodySmall)
            }

            // Tasks
            if (job.materials.isNotEmpty() || job.workLog.isNotEmpty()) {
                ConsoleSeparator()
            }

            // Materials
            if (job.materials.isNotEmpty()) {
                SectionHeader("MATERIALS (${job.materials.count { it.checked }}/${job.materials.size})")
                job.materials.forEachIndexed { index, material ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ConsoleTheme.surface)
                            .clickable { onToggleMaterial(index) }
                            .padding(8.dp)
                    ) {
                        Text(
                            text = if (material.checked) "[x]" else "[ ]",
                            style = ConsoleTheme.body,
                            modifier = Modifier.width(32.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = material.name, style = ConsoleTheme.bodySmall)
                            if (material.quantity > 0 && material.unitCost > 0) {
                                Text(
                                    text = "${material.quantity} ${material.unit} × $${material.unitCost}",
                                    style = ConsoleTheme.caption
                                )
                            }
                        }
                        if (material.totalCost > 0) {
                            Text(text = "$${String.format("%.2f", material.totalCost)}", style = ConsoleTheme.bodySmall)
                        }
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                }
            }

            // Equipment
            if (job.equipmentList.isNotEmpty()) {
                SectionHeader("EQUIPMENT")
                job.equipmentList.forEach { item ->
                    Text(text = "  - $item", style = ConsoleTheme.bodySmall)
                }
            }

            // ── ADD NOTE (IN_PROGRESS only) ─────────────────
            if (job.stage == JobStage.IN_PROGRESS && onAddNote != null) {
                ConsoleSeparator()
                SectionHeader("ADD NOTE")
                var noteText by remember { mutableStateOf("") }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BasicTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        textStyle = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.text),
                        modifier = Modifier
                            .weight(1f)
                            .background(ConsoleTheme.surface, RoundedCornerShape(4.dp))
                            .border(0.5.dp, ConsoleTheme.text.copy(alpha = 0.10f), RoundedCornerShape(4.dp))
                            .padding(10.dp),
                        decorationBox = { innerTextField ->
                            if (noteText.isEmpty()) {
                                Text("What did you work on?", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
                            }
                            innerTextField()
                        }
                    )
                    Text(
                        "[ADD]",
                        style = ConsoleTheme.action.copy(color = ConsoleTheme.accent),
                        modifier = Modifier
                            .clickable {
                                if (noteText.isNotBlank()) {
                                    onAddNote(noteText.trim())
                                    noteText = ""
                                }
                            }
                            .background(ConsoleTheme.surface, RoundedCornerShape(4.dp))
                            .padding(10.dp)
                    )
                }

                // Show recent work notes
                val recentNotes = job.workLog.takeLast(3).reversed()
                if (recentNotes.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    recentNotes.forEach { note ->
                        val time = SimpleDateFormat("h:mm a", Locale.US).format(Date(note.timestamp))
                        Text(
                            "$time — ${note.text}",
                            style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                        )
                    }
                }
            }

            // ── DAILY LOGS ────────────────────────────────────
            if (job.dailyLogs.isNotEmpty()) {
                ConsoleSeparator()
                var showAllLogs by remember { mutableStateOf(false) }
                SectionHeader("DAILY LOGS (${job.dailyLogs.size})")

                val displayLogs = if (showAllLogs) job.dailyLogs.sortedByDescending { it.date }
                    else job.dailyLogs.sortedByDescending { it.date }.take(2)

                displayLogs.forEach { log ->
                    var expanded by remember { mutableStateOf(false) }
                    val dateStr = SimpleDateFormat("MMM d, yyyy", Locale.US).format(Date(log.date))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ConsoleTheme.surface, RoundedCornerShape(4.dp))
                            .border(0.5.dp, ConsoleTheme.text.copy(alpha = 0.06f), RoundedCornerShape(4.dp))
                            .clickable { expanded = !expanded }
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(dateStr, style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.text))
                            Text("${String.format("%.1f", log.hoursWorked)}h", style = ConsoleTheme.caption.copy(color = ConsoleTheme.accent))
                        }
                        Text(log.summaryDetailed, style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))

                        if (expanded && log.summaryNarrative != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("AI REPORT", style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.accent))
                            Text(log.summaryNarrative, style = ConsoleTheme.caption.copy(color = ConsoleTheme.text))
                        }
                        if (expanded && log.workerNotes.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("NOTES", style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.textMuted))
                            log.workerNotes.forEach { note ->
                                Text("- ${note.text}", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                if (job.dailyLogs.size > 2 && !showAllLogs) {
                    Text(
                        "[Show all ${job.dailyLogs.size} logs]",
                        style = ConsoleTheme.action.copy(color = ConsoleTheme.accent),
                        modifier = Modifier.clickable { showAllLogs = true }
                    )
                }
            }

            // Price Breakdown
            ConsoleSeparator()
            SectionHeader("PRICE")
            val materialsCost = job.materials.sumOf { it.totalCost }
            val laborCost = job.hourlyRate * 8 // placeholder — actual hours from time entries
            Text(text = "Labor: $${String.format("%.2f", laborCost)}", style = ConsoleTheme.bodySmall)
            Text(text = "Materials: $${String.format("%.2f", materialsCost)}", style = ConsoleTheme.bodySmall)
            Text(
                text = "Total: $${String.format("%.2f", laborCost + materialsCost)}",
                style = ConsoleTheme.bodyBold
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Stage-specific actions
            when (job.stage) {
                JobStage.LEAD -> {
                    ActionButton("[CREATE PROPOSAL]") { onStageAction(job, JobStage.PROPOSAL) }
                }
                JobStage.PROPOSAL -> {
                    ActionButton("[SHARE WITH CLIENT]") { onShareProposal() }
                }
                JobStage.APPROVED -> {
                    ActionButton("[START WORK]") { onStageAction(job, JobStage.IN_PROGRESS) }
                }
                JobStage.IN_PROGRESS -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionButton("[CLOCK IN]") { onClockIn() }
                        ActionButton("[MARK COMPLETE]") { onStageAction(job, JobStage.REVIEW) }
                    }
                    if (onSummarizeToday != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        ActionButton("[SUMMARIZE TODAY]") { onSummarizeToday() }
                    }
                }
                JobStage.REVIEW -> {
                    val unchecked = job.materials.count { !it.checked }
                    if (unchecked > 0) {
                        Text(
                            text = "! $unchecked materials not checked off",
                            style = ConsoleTheme.caption.copy(color = ConsoleTheme.warning)
                        )
                    }
                    // Detail level picker
                    if (job.dailyLogs.isNotEmpty()) {
                        InvoiceDetailPicker(invoiceDetailLevel) { invoiceDetailLevel = it }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    ActionButton("[PREVIEW INVOICE]") { showInvoice = true }
                    Spacer(modifier = Modifier.height(4.dp))
                    ActionButton("[GENERATE INVOICE]") { onStageAction(job, JobStage.INVOICE) }
                }
                JobStage.INVOICE -> {
                    // Detail level picker
                    if (job.dailyLogs.isNotEmpty()) {
                        InvoiceDetailPicker(invoiceDetailLevel) { invoiceDetailLevel = it }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    ActionButton("[VIEW INVOICE]") { showInvoice = true }
                    Spacer(modifier = Modifier.height(4.dp))
                    ActionButton("[MARK PAID — CLOSE]") { onStageAction(job, JobStage.CLOSED) }
                }
                JobStage.CLOSED -> {
                    Text(text = "Job closed.", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(text = text, style = ConsoleTheme.captionBold)
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun ActionButton(text: String, onClick: () -> Unit) {
    Text(
        text = text,
        style = ConsoleTheme.action,
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(ConsoleTheme.surface)
            .padding(12.dp)
    )
}

@Composable
private fun InvoiceDetailPicker(
    selected: com.guildofsmiths.trademesh.ui.invoice.InvoiceDetailLevel,
    onSelect: (com.guildofsmiths.trademesh.ui.invoice.InvoiceDetailLevel) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("Invoice detail:", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
        com.guildofsmiths.trademesh.ui.invoice.InvoiceDetailLevel.entries.forEach { level ->
            val label = when (level) {
                com.guildofsmiths.trademesh.ui.invoice.InvoiceDetailLevel.STANDARD -> "Standard"
                com.guildofsmiths.trademesh.ui.invoice.InvoiceDetailLevel.DETAILED -> "Detailed"
                com.guildofsmiths.trademesh.ui.invoice.InvoiceDetailLevel.ADVANCED -> "Advanced"
            }
            val isSelected = selected == level
            Text(
                "[$label]",
                style = ConsoleTheme.action.copy(
                    color = if (isSelected) ConsoleTheme.accent else ConsoleTheme.textMuted
                ),
                modifier = Modifier
                    .clickable { onSelect(level) }
                    .then(
                        if (isSelected) Modifier.background(ConsoleTheme.accent.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                        else Modifier
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}
