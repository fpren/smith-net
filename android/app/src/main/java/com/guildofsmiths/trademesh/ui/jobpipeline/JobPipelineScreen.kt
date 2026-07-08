package com.guildofsmiths.trademesh.ui.jobpipeline

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*
import com.guildofsmiths.trademesh.data.TimeEntryRepository
import com.guildofsmiths.trademesh.data.UserPreferences
import kotlinx.coroutines.launch
import com.guildofsmiths.trademesh.ui.ConsoleHeader
import com.guildofsmiths.trademesh.ui.ConsoleSeparator
import com.guildofsmiths.trademesh.ui.ConsoleTheme
import com.guildofsmiths.trademesh.ui.invoice.InvoiceGenerator
import com.guildofsmiths.trademesh.ui.invoice.InvoiceFormatter
import com.guildofsmiths.trademesh.ui.invoice.InvoicePreviewDialog
import com.guildofsmiths.trademesh.ui.jobboard.*
import com.guildofsmiths.trademesh.ui.theme2.SmithButton
import com.guildofsmiths.trademesh.ui.theme2.SmithButtonVariant
import com.guildofsmiths.trademesh.ui.theme2.SmithDialog

@Composable
fun JobPipelineScreen(
    job: Job,
    onBack: () -> Unit,
    onStageAction: (Job, JobStage) -> Unit,
    onToggleMaterial: (Int) -> Unit,
    onClockIn: () -> Unit,
    onClockOut: () -> Unit = {},
    isClockedInThisJob: Boolean = false,
    onAddNote: ((String) -> Unit)? = null,
    onAddPhoto: (() -> Unit)? = null,
    onAddMaterial: ((Material, orderIt: Boolean, vendor: String?) -> Unit)? = null,
    onSummarizeToday: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val shareScope = androidx.compose.runtime.rememberCoroutineScope()
    var showInvoice by remember { mutableStateOf(false) }
    var invoiceDetailLevel by remember { mutableStateOf(com.guildofsmiths.trademesh.ui.invoice.InvoiceDetailLevel.STANDARD) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showAddNoteDialog by remember { mutableStateOf(false) }
    var showAddMaterialDialog by remember { mutableStateOf(false) }
    var showProposal by remember { mutableStateOf(false) }
    var noteDraft by remember { mutableStateOf("") }
    val timeEntries by TimeEntryRepository.entries.collectAsState()

    val proposal = remember(job, showProposal) {
        if (!showProposal) null
        else com.guildofsmiths.trademesh.ui.proposal.ProposalGenerator.generateFromJob(
            job = job,
            providerName = UserPreferences.getUserName(),
            providerBusiness = UserPreferences.getBusinessName(),
            providerTrade = "${UserPreferences.getPrimaryTrade()} — Guild of Smiths",
            hourlyRate = UserPreferences.getHourlyRate().takeIf { it > 0 } ?: job.hourlyRate.takeIf { it > 0 } ?: 85.0,
            estimatedHours = job.estimatedHours.takeIf { it > 0 } ?: 8.0
        )
    }
    if (showProposal && proposal != null) {
        com.guildofsmiths.trademesh.ui.proposal.ProposalPreviewDialog(
            proposal = proposal,
            onDismiss = { showProposal = false },
            onShare = {
                shareScope.launch {
                    val text = com.guildofsmiths.trademesh.ui.proposal.ProposalFormatter.formatAsText(proposal)
                    val url = com.guildofsmiths.trademesh.data.PublicLinkClient
                        .createProposalLink(job.id, proposal)
                    val body = if (url != null) "View proposal: $url\n\n$text" else text
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "${proposal.proposalNumber} — ${proposal.jobTitle}")
                        putExtra(Intent.EXTRA_TEXT, body)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share proposal"))
                }
            }
        )
    }

    // Generate invoice when requested (reacts to detail level changes).
    // Keep invoice computed whenever job or detail level changes so both the
    // dialog AND the rich preview sheet can reference it independently.
    var showRichPreview by remember { mutableStateOf(false) }
    val invoice = remember(job, showInvoice, showRichPreview, invoiceDetailLevel) {
        if (!showInvoice && !showRichPreview) null
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

    if (showAddMenu) {
        AddMenuDialog(
            onDismiss = { showAddMenu = false },
            onAddNote = {
                showAddMenu = false
                showAddNoteDialog = true
            },
            onAddPhoto = onAddPhoto?.let { cb ->
                {
                    showAddMenu = false
                    cb()
                }
            },
            onAddMaterial = onAddMaterial?.let {
                {
                    showAddMenu = false
                    showAddMaterialDialog = true
                }
            },
            onMarkComplete = {
                showAddMenu = false
                onStageAction(job, JobStage.REVIEW)
            }
        )
    }

    if (showAddNoteDialog) {
        AddNoteDialog(
            value = noteDraft,
            onValueChange = { noteDraft = it },
            onSave = {
                val text = noteDraft.trim()
                if (text.isNotEmpty()) onAddNote?.invoke(text)
                noteDraft = ""
                showAddNoteDialog = false
            },
            onCancel = {
                noteDraft = ""
                showAddNoteDialog = false
            }
        )
    }

    if (showAddMaterialDialog && onAddMaterial != null) {
        AddMaterialDialog(
            existing = job.materials,
            onSave = { material, orderIt, vendor ->
                onAddMaterial(material, orderIt, vendor)
                showAddMaterialDialog = false
            },
            onCancel = { showAddMaterialDialog = false }
        )
    }

    // Invoice preview dialog + rich PREVIEW & SHARE bottom sheet
    if (showInvoice && invoice != null) {
        val bolText = remember(job, timeEntries) {
            com.guildofsmiths.trademesh.ui.expenses.BolFormatter.formatAsText(job, timeEntries)
        }
        InvoicePreviewDialog(
            invoice = invoice,
            onDismiss = { showInvoice = false },
            onShare = { invoiceText ->
                // Try to create a public /i/<uuid> page and share the link + text;
                // fall back to text-only if the link can't be created (offline/error).
                shareScope.launch {
                    val url = com.guildofsmiths.trademesh.data.PublicLinkClient
                        .createInvoiceLink(job.id, invoice, job.clientName)
                    val body = if (url != null) "View invoice: $url\n\n$invoiceText" else invoiceText
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Invoice ${invoice.invoiceNumber} — ${job.clientName ?: job.title}")
                        putExtra(Intent.EXTRA_TEXT, body)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share Invoice"))
                }
            },
            bolText = bolText,
            onShareBol = { bol ->
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_SUBJECT, "BOL — ${job.clientName ?: job.title}")
                    putExtra(Intent.EXTRA_TEXT, bol)
                }
                context.startActivity(Intent.createChooser(shareIntent, "Share BOL"))
            },
            onPreviewRendered = {
                showInvoice = false
                showRichPreview = true
            }
        )
    }
    if (showRichPreview && invoice != null) {
        com.guildofsmiths.trademesh.ui.expenses.InvoicePreviewBottomSheet(
            invoice = invoice,
            job = job,
            timeEntries = timeEntries,
            onDismiss = { showRichPreview = false }
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
                            .shadow(1.dp, RoundedCornerShape(4.dp))
                            .background(ConsoleTheme.surface, RoundedCornerShape(4.dp))
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

            // Recent work notes (IN_PROGRESS only — add via + menu)
            if (job.stage == JobStage.IN_PROGRESS) {
                val recentNotes = job.workLog.takeLast(3).reversed()
                if (recentNotes.isNotEmpty()) {
                    ConsoleSeparator()
                    SectionHeader("NOTES")
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
            val materialsCost = job.materials.sumOf {
                if (it.totalCost > 0) it.totalCost else it.quantity * it.unitCost
            }
            val hoursLogged = timeEntries
                .filter { it.jobId == job.id && (it.durationMinutes ?: 0) > 0 }
                .sumOf { it.durationMinutes ?: 0 } / 60.0
            val effectiveRate = if (job.hourlyRate > 0) job.hourlyRate else 85.0
            val laborCost = hoursLogged * effectiveRate
            val total = laborCost + materialsCost
            val deposit = job.depositCollected
            val balanceDue = total - deposit

            if (hoursLogged > 0) {
                Text(
                    text = "Hours:     ${String.format("%.2fh", hoursLogged)} × $${String.format("%.2f", effectiveRate)}",
                    style = ConsoleTheme.bodySmall
                )
            } else {
                Text(
                    text = "Hours:     — (clock in to log)",
                    style = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.textMuted)
                )
            }
            Text(text = "Labor:     $${String.format("%.2f", laborCost)}", style = ConsoleTheme.bodySmall)
            Text(text = "Materials: $${String.format("%.2f", materialsCost)}", style = ConsoleTheme.bodySmall)
            Text(
                text = "Total:     $${String.format("%.2f", total)}",
                style = ConsoleTheme.bodyBold
            )
            if (deposit > 0) {
                Text(
                    text = "Deposit:  -$${String.format("%.2f", deposit)}" +
                            (job.depositNote?.let { "    ($it)" } ?: ""),
                    style = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.textMuted)
                )
                val balanceLabel = if (balanceDue < 0) "Credit:" else "Balance:"
                val balanceAmt = "$${String.format("%.2f", kotlin.math.abs(balanceDue))}"
                Text(
                    text = "$balanceLabel   $balanceAmt",
                    style = ConsoleTheme.bodyBold.copy(color = ConsoleTheme.accent)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Stage-specific actions
            when (job.stage) {
                JobStage.LEAD -> {
                    OutlinedActionButton("CREATE PROPOSAL") {
                        onStageAction(job, JobStage.PROPOSAL)
                        showProposal = true
                    }
                }
                JobStage.PROPOSAL -> {
                    OutlinedActionButton("PREVIEW PROPOSAL") { showProposal = true }
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedActionButton("SHARE WITH CLIENT") { showProposal = true }
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedActionButton("MARK APPROVED") { onStageAction(job, JobStage.APPROVED) }
                }
                JobStage.APPROVED -> {
                    OutlinedActionButton("START WORK") { onStageAction(job, JobStage.IN_PROGRESS) }
                }
                JobStage.IN_PROGRESS -> {
                    // Clock in/out for this job. Logged time feeds the invoice's
                    // Hours/Labor lines (hourlyRate x hoursLogged) on REVIEW/INVOICE.
                    if (isClockedInThisJob) {
                        OutlinedActionButton(
                            text = "CLOCK OUT",
                            color = ConsoleTheme.warning,
                            modifier = Modifier.testTag("solo_e2e_job_clock_out")
                        ) { onClockOut() }
                    } else {
                        OutlinedActionButton(
                            text = "CLOCK IN",
                            modifier = Modifier.testTag("solo_e2e_job_clock_in")
                        ) { onClockIn() }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(Modifier.testTag("solo_e2e_job_add_menu")) {
                        com.guildofsmiths.trademesh.ui.PixelPlusButton(
                            enabled = true,
                            onClick = { showAddMenu = true }
                        )
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
                    OutlinedActionButton("PREVIEW INVOICE") { showInvoice = true }
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedActionButton("GENERATE INVOICE") { onStageAction(job, JobStage.INVOICE) }
                }
                JobStage.INVOICE -> {
                    // Detail level picker
                    if (job.dailyLogs.isNotEmpty()) {
                        InvoiceDetailPicker(invoiceDetailLevel) { invoiceDetailLevel = it }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    OutlinedActionButton("VIEW INVOICE") { showInvoice = true }
                    Spacer(modifier = Modifier.height(4.dp))
                    val nothingBillable = total <= 0.0
                    if (nothingBillable) {
                        Text(
                            text = "! Nothing to bill - clock in to log hours or add materials",
                            style = ConsoleTheme.caption.copy(color = ConsoleTheme.warning)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    OutlinedActionButton("MARK PAID — CLOSE", enabled = !nothingBillable) {
                        onStageAction(job, JobStage.CLOSED)
                    }
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
private fun OutlinedActionButton(
    text: String,
    color: Color = ConsoleTheme.accent,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val drawColor = if (enabled) color else ConsoleTheme.textMuted
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "btnScale"
    )
    val elevation by animateDpAsState(
        targetValue = if (pressed || !enabled) 0.dp else 2.dp,
        animationSpec = tween(120),
        label = "btnElev"
    )
    Text(
        text = text,
        style = ConsoleTheme.action.copy(color = drawColor),
        modifier = modifier
            .scale(scale)
            .shadow(elevation, RoundedCornerShape(4.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onClick
            )
            .background(ConsoleTheme.background, RoundedCornerShape(4.dp))
            .border(1.dp, drawColor, RoundedCornerShape(4.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

@Composable
private fun AddMenuDialog(
    onDismiss: () -> Unit,
    onAddNote: () -> Unit,
    onAddPhoto: (() -> Unit)?,
    onAddMaterial: (() -> Unit)?,
    onMarkComplete: () -> Unit
) {
    SmithDialog(
        title = "ADD",
        onDismiss = onDismiss,
        actions = {
            SmithButton(text = "CLOSE", onClick = onDismiss, variant = SmithButtonVariant.Ghost)
        },
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AddMenuRow(
                label = "Note",
                icon = { com.guildofsmiths.trademesh.ui.PixelNote(enabled = true) },
                enabled = true,
                onClick = onAddNote
            )
            AddMenuRow(
                label = "Photo",
                icon = { com.guildofsmiths.trademesh.ui.PixelCamera(enabled = onAddPhoto != null) },
                enabled = onAddPhoto != null,
                onClick = { onAddPhoto?.invoke() }
            )
            AddMenuRow(
                label = "Material",
                icon = { com.guildofsmiths.trademesh.ui.PixelPackage(enabled = onAddMaterial != null) },
                enabled = onAddMaterial != null,
                onClick = { onAddMaterial?.invoke() }
            )
            Spacer(modifier = Modifier.height(4.dp))
            AddMenuRow(
                label = "Mark complete",
                icon = { com.guildofsmiths.trademesh.ui.PixelCheckmark(enabled = true) },
                enabled = true,
                onClick = onMarkComplete,
                accent = true
            )
        }
    }
}

@Composable
private fun AddMenuRow(
    label: String,
    icon: @Composable () -> Unit,
    enabled: Boolean,
    onClick: () -> Unit,
    accent: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ConsoleTheme.surface)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Text(
            text = label,
            style = ConsoleTheme.action.copy(
                color = when {
                    !enabled -> ConsoleTheme.textMuted
                    accent -> ConsoleTheme.accent
                    else -> ConsoleTheme.text
                }
            )
        )
    }
}

@Composable
private fun AddNoteDialog(
    value: String,
    onValueChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    SmithDialog(
        title = "ADD NOTE",
        onDismiss = onCancel,
        actions = {
            SmithButton(text = "CANCEL", onClick = onCancel, variant = SmithButtonVariant.Ghost)
            Spacer(modifier = Modifier.width(8.dp))
            SmithButton(text = "SAVE", onClick = onSave)
        },
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = ConsoleTheme.body,
            cursorBrush = SolidColor(ConsoleTheme.cursor),
            modifier = Modifier
                .fillMaxWidth()
                .background(ConsoleTheme.surface)
                .padding(10.dp)
                .height(100.dp),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text("Work notes, extras, issues...", style = ConsoleTheme.body.copy(color = ConsoleTheme.placeholder))
                    }
                    inner()
                }
            }
        )
    }
}

private data class MaterialVendor(val tag: String, val label: String, val url: String?)

private val MATERIAL_VENDORS = listOf(
    MaterialVendor("HD", "Home Depot", "https://www.homedepot.com/s/"),
    MaterialVendor("Lowe's", "Lowe's", "https://www.lowes.com/search?searchTerm="),
    MaterialVendor("Supply", "SupplyHouse", "https://www.supplyhouse.com/search?q="),
    MaterialVendor("Amazon", "Amazon", "https://www.amazon.com/s?k="),
    MaterialVendor("Store", "Buy in store", null)
)

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun AddMaterialDialog(
    existing: List<Material>,
    onSave: (Material, orderIt: Boolean, vendor: String?) -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var showVendorPicker by remember { mutableStateOf(false) }
    val catalog = remember {
        val occ = UserPreferences.getOccupation()
        val preferred = com.guildofsmiths.trademesh.data.TradeDefaults.getForTrade(occ)?.commonMaterials.orEmpty()
        val preferredNames = preferred.map { it.name.lowercase() }.toSet()
        val rest = com.guildofsmiths.trademesh.data.TradeDefaults.allCommonMaterials()
            .filter { it.name.lowercase() !in preferredNames }
        preferred + rest
    }
    val suggestions = remember(query, catalog) {
        if (query.isBlank()) {
            catalog.take(8)
        } else {
            val terms = com.guildofsmiths.trademesh.data.TradeDefaults.expandQuery(query)
            catalog.filter { mat -> terms.any { t -> mat.name.contains(t, ignoreCase = true) } }.take(8)
        }
    }
    val existingNames = remember(existing) { existing.map { it.name.lowercase() }.toSet() }
    val hasExactMatch = remember(query, suggestions) {
        val q = query.trim()
        q.isNotBlank() && suggestions.any { it.name.equals(q, ignoreCase = true) }
    }

    fun materialFrom(text: String): Material {
        // A typed price makes this a purchased line item: stamp unit/total cost
        // (so it feeds the invoice materials subtotal) and mark it checked.
        val cost = price.trim().toDoubleOrNull() ?: 0.0
        val match = catalog.firstOrNull { it.name.equals(text, ignoreCase = true) }
        return Material(
            name = match?.name ?: text.trim(),
            unit = match?.unit ?: "ea",
            quantity = 1.0,
            unitCost = cost,
            totalCost = cost,
            checked = cost > 0.0
        )
    }

    SmithDialog(
        title = "ADD MATERIAL",
        onDismiss = onCancel,
        actions = {
            SmithButton(text = "CANCEL", onClick = onCancel, variant = SmithButtonVariant.Ghost)
            Spacer(modifier = Modifier.width(8.dp))
            SmithButton(
                text = "ORDER",
                onClick = { showVendorPicker = true },
                enabled = query.isNotBlank(),
            )
            Spacer(modifier = Modifier.width(8.dp))
            SmithButton(
                text = "SAVE",
                onClick = { onSave(materialFrom(query), false, null) },
                enabled = query.isNotBlank(),
            )
        },
    ) {
        Column(
            modifier = Modifier.semantics { testTagsAsResourceId = true },
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                textStyle = ConsoleTheme.body,
                cursorBrush = SolidColor(ConsoleTheme.cursor),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("solo_e2e_job_material_search")
                    .background(ConsoleTheme.surface)
                    .padding(10.dp),
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) {
                            Text("Type a material name...", style = ConsoleTheme.body.copy(color = ConsoleTheme.placeholder))
                        }
                        inner()
                    }
                }
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$", style = ConsoleTheme.body)
                Spacer(modifier = Modifier.width(6.dp))
                BasicTextField(
                    value = price,
                    onValueChange = { new -> price = new.filter { it.isDigit() || it == '.' } },
                    textStyle = ConsoleTheme.body,
                    cursorBrush = SolidColor(ConsoleTheme.cursor),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("solo_e2e_job_material_cost")
                        .background(ConsoleTheme.surface)
                        .padding(10.dp),
                    decorationBox = { inner ->
                        Box {
                            if (price.isEmpty()) {
                                Text("Price (e.g. 189.99)", style = ConsoleTheme.body.copy(color = ConsoleTheme.placeholder))
                            }
                            inner()
                        }
                    }
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                if (query.isNotBlank() && !hasExactMatch) {
                    Text(
                        text = "[+]  Add \"${query.trim()}\" as new",
                        style = ConsoleTheme.action.copy(color = ConsoleTheme.accent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSave(materialFrom(query), false, null) }
                            .padding(vertical = 8.dp)
                    )
                }
                suggestions.forEach { mat ->
                    val already = mat.name.lowercase() in existingNames
                    val marker = if (already) "[x]" else "[ ]"
                    val suffix = if (already) "  — added" else ""
                    Text(
                        text = "$marker  ${mat.name}  (${mat.unit})$suffix",
                        style = ConsoleTheme.caption.copy(
                            color = if (already) ConsoleTheme.textMuted else ConsoleTheme.text
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !already) { query = mat.name }
                            .padding(vertical = 6.dp)
                    )
                }
            }
        }
    }

    if (showVendorPicker && query.isNotBlank()) {
        SmithDialog(
            title = "ORDER FROM",
            onDismiss = { showVendorPicker = false },
            actions = {
                SmithButton(
                    text = "CANCEL",
                    onClick = { showVendorPicker = false },
                    variant = SmithButtonVariant.Ghost,
                )
            },
        ) {
            Text(query, style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
            Spacer(modifier = Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                MATERIAL_VENDORS.forEach { vendor ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ConsoleTheme.surface, RoundedCornerShape(4.dp))
                            .clickable {
                                showVendorPicker = false
                                if (vendor.url != null) {
                                    val url = vendor.url + Uri.encode(query.trim())
                                    runCatching {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    }
                                }
                                onSave(materialFrom(query), true, vendor.label)
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            vendor.tag,
                            style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.accent),
                            modifier = Modifier.width(60.dp)
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(vendor.label, style = ConsoleTheme.bodySmall)
                            Text(
                                if (vendor.url != null) "Search & order" else "Pickup / in-store",
                                style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                            )
                        }
                    }
                }
            }
        }
    }
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
