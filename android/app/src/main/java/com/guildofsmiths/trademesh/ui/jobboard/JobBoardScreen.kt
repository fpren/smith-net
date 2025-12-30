package com.guildofsmiths.trademesh.ui.jobboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.imePadding
import androidx.lifecycle.viewmodel.compose.viewModel
import com.guildofsmiths.trademesh.ui.ConsoleHeader
import com.guildofsmiths.trademesh.ui.ConsoleSeparator
import com.guildofsmiths.trademesh.ui.ConsoleTheme
import com.guildofsmiths.trademesh.ui.invoice.InvoicePreviewDialog
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

/**
 * C-11: Job Board Screen
 * Workflow-based job management
 */

@Composable
fun JobBoardScreen(
    onNavigateBack: () -> Unit,
    viewModel: JobBoardViewModel = viewModel()
) {
    val jobs by viewModel.jobs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val selectedJob by viewModel.selectedJob.collectAsState()
    val tasks by viewModel.tasks.collectAsState()
    val generatedInvoice by viewModel.generatedInvoice.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var filterStatus by remember { mutableStateOf<JobStatus?>(null) }

    // Stats for active jobs only
    val stats = remember(jobs) {
        mapOf(
            JobStatus.TODO to jobs.count { it.status == JobStatus.TODO },
            JobStatus.IN_PROGRESS to jobs.count { it.status == JobStatus.IN_PROGRESS },
            JobStatus.REVIEW to jobs.count { it.status == JobStatus.REVIEW },
            JobStatus.DONE to jobs.count { it.status == JobStatus.DONE }
        )
    }

    val filteredJobs = remember(jobs, filterStatus) {
        if (filterStatus == null) jobs
        else jobs.filter { it.status == filterStatus }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ConsoleTheme.background)
    ) {
        ConsoleHeader(title = "JOB BOARD", onBackClick = onNavigateBack)
        ConsoleSeparator()

        error?.let { errorMsg ->
            Text(
                text = "! $errorMsg",
                style = ConsoleTheme.caption.copy(color = ConsoleTheme.error),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // Stats Dashboard
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ConsoleTheme.surface)
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf(
                JobStatus.TODO to "TO DO",
                JobStatus.IN_PROGRESS to "WORKING",
                JobStatus.REVIEW to "CHECK",
                JobStatus.DONE to "DONE"
            ).forEach { (status, label) ->
                val count = stats[status] ?: 0
                val isSelected = filterStatus == status
                
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable { filterStatus = if (filterStatus == status) null else status }
                        .background(if (isSelected) ConsoleTheme.accent.copy(alpha = 0.1f) else ConsoleTheme.surface)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = count.toString(),
                        style = ConsoleTheme.header.copy(
                            color = when {
                                isSelected -> ConsoleTheme.accent
                                status == JobStatus.IN_PROGRESS && count > 0 -> ConsoleTheme.warning
                                status == JobStatus.DONE && count > 0 -> ConsoleTheme.success
                                else -> ConsoleTheme.text
                            }
                        )
                    )
                    Text(
                        text = label,
                        style = ConsoleTheme.caption.copy(
                            color = if (isSelected) ConsoleTheme.accent else ConsoleTheme.textMuted
                        )
                    )
                }
            }
        }

        ConsoleSeparator()

        // Action bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (filterStatus != null) "${filterStatus!!.displayName} (${filteredJobs.size})"
                       else "ALL JOBS (${jobs.size})",
                style = ConsoleTheme.captionBold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (filterStatus != null) {
                    Text(
                        text = "CLEAR",
                        style = ConsoleTheme.action.copy(color = ConsoleTheme.textMuted),
                        modifier = Modifier.clickable { filterStatus = null }
                    )
                }
                Text(
                    text = "+ NEW",
                    style = ConsoleTheme.action,
                    modifier = Modifier.clickable { showCreateDialog = true }
                )
            }
        }

        ConsoleSeparator()

        if (isLoading) {
            Text(text = "Loading...", style = ConsoleTheme.caption, modifier = Modifier.padding(16.dp))
        }

        // Active jobs list with swipe actions
        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(filteredJobs.sortedByDescending { it.updatedAt }) { job ->
                SwipeableJobRow(
                    job = job,
                    onClick = { viewModel.selectJob(job) },
                    onArchive = { viewModel.archiveJob(job.id) },
                    onDelete = { viewModel.deleteJob(job.id) }
                )
            }

            if (filteredJobs.isEmpty() && !isLoading) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (filterStatus != null) "No ${filterStatus!!.displayName.lowercase()} jobs"
                                   else "No jobs yet",
                            style = ConsoleTheme.body
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(text = "Tap + NEW to create one", style = ConsoleTheme.caption)
                    }
                }
            }
        }
    }

    // Create Job Dialog with Preview
    if (showCreateDialog) {
        CreateJobDialogWithPreview(
            onDismiss = { showCreateDialog = false },
            onCreate = { title, desc, priority, expenses, crewSize, crew, materials, startDate, endDate ->
                viewModel.createJob(title, desc, priority, "", expenses, crewSize, crew, materials, startDate, endDate)
                showCreateDialog = false
            }
        )
    }

    // Job Detail/Workflow Dialog
    selectedJob?.let { job ->
        JobWorkflowDialog(
            job = job,
            tasks = tasks,
            viewModel = viewModel,
            onDismiss = { viewModel.selectJob(null) }
        )
    }

    // Invoice Preview Dialog
    generatedInvoice?.let { invoice ->
        InvoicePreviewDialog(
            invoice = invoice,
            onDismiss = { viewModel.clearInvoice() },
            onShare = { text ->
                // TODO: Implement share functionality
                viewModel.clearInvoice()
            }
        )
    }
}

@Composable
private fun JobRow(job: Job, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ConsoleTheme.surface)
            .clickable { onClick() }
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = when (job.status) {
                    JobStatus.TODO -> "[  ]"
                    JobStatus.IN_PROGRESS -> "[>>]"
                    JobStatus.REVIEW -> "[??]"
                    JobStatus.DONE -> "[OK]"
                    else -> "[--]"
                },
                style = ConsoleTheme.bodyBold.copy(
                    color = when (job.status) {
                        JobStatus.IN_PROGRESS -> ConsoleTheme.warning
                        JobStatus.REVIEW -> ConsoleTheme.accent
                        JobStatus.DONE -> ConsoleTheme.success
                        else -> ConsoleTheme.textMuted
                    }
                )
            )
            
            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (job.priority == Priority.HIGH || job.priority == Priority.URGENT) {
                        Text(
                            text = if (job.priority == Priority.URGENT) "!!" else "!",
                            style = ConsoleTheme.bodyBold.copy(
                                color = if (job.priority == Priority.URGENT) ConsoleTheme.error else ConsoleTheme.warning
                            )
                        )
                    }
                    Text(text = job.title, style = ConsoleTheme.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = job.status.displayName, style = ConsoleTheme.caption)
                    if (job.crewSize > 1) {
                        Text(text = "${job.crewSize} crew", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
                    }
                }
            }
        }

        Text(text = "VIEW >", style = ConsoleTheme.action, modifier = Modifier.clickable { onClick() })
    }
}

// ════════════════════════════════════════════════════════════════════
// SWIPEABLE JOB ROW (swipe left to archive)
// ════════════════════════════════════════════════════════════════════

@Composable
private fun SwipeableJobRow(
    job: Job,
    onClick: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit
) {
    var offsetX by remember { mutableStateOf(0f) }
    val archiveThreshold = -150f
    val deleteThreshold = 150f
    var showArchiveConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        // Delete background (revealed on swipe right)
        if (offsetX > 50f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(ConsoleTheme.error.copy(alpha = 0.3f))
                    .padding(start = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "DELETE →",
                    style = ConsoleTheme.bodyBold.copy(color = ConsoleTheme.error)
                )
            }
        }

        // Archive background (revealed on swipe left)
        if (offsetX < -50f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(ConsoleTheme.warning.copy(alpha = 0.3f))
                    .padding(end = 16.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = "← ARCHIVE",
                    style = ConsoleTheme.bodyBold.copy(color = ConsoleTheme.warning)
                )
            }
        }
        
        // Main row with swipe gesture
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .background(ConsoleTheme.surface)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (offsetX > deleteThreshold) {
                                showDeleteConfirm = true
                            } else if (offsetX < archiveThreshold) {
                                showArchiveConfirm = true
                            }
                            offsetX = 0f
                        },
                        onDragCancel = { offsetX = 0f },
                        onHorizontalDrag = { _, dragAmount ->
                            offsetX = (offsetX + dragAmount).coerceIn(-200f, 200f)
                        }
                    )
                }
                .clickable { onClick() }
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = when (job.status) {
                        JobStatus.TODO -> "[  ]"
                        JobStatus.IN_PROGRESS -> "[>>]"
                        JobStatus.REVIEW -> "[??]"
                        JobStatus.DONE -> "[OK]"
                        else -> "[--]"
                    },
                    style = ConsoleTheme.bodyBold.copy(
                        color = when (job.status) {
                            JobStatus.IN_PROGRESS -> ConsoleTheme.warning
                            JobStatus.REVIEW -> ConsoleTheme.accent
                            JobStatus.DONE -> ConsoleTheme.success
                            else -> ConsoleTheme.textMuted
                        }
                    )
                )
                
                Column(modifier = Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (job.priority == Priority.HIGH || job.priority == Priority.URGENT) {
                            Text(
                                text = if (job.priority == Priority.URGENT) "!!" else "!",
                                style = ConsoleTheme.bodyBold.copy(
                                    color = if (job.priority == Priority.URGENT) ConsoleTheme.error else ConsoleTheme.warning
                                )
                            )
                        }
                        Text(text = job.title, style = ConsoleTheme.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = job.status.displayName, style = ConsoleTheme.caption)
                        // Show created date
                        Text(
                            text = formatShortDate(job.createdAt),
                            style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                        )
                    }
                }
            }

            Text(text = ">", style = ConsoleTheme.action)
        }
    }
    
    // Archive confirmation dialog
    if (showArchiveConfirm) {
        AlertDialog(
            onDismissRequest = { showArchiveConfirm = false },
            containerColor = ConsoleTheme.background,
            title = { Text("ARCHIVE JOB?", style = ConsoleTheme.header) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = job.title, style = ConsoleTheme.bodyBold)
                    Text(
                        text = "Archived jobs are saved for reference but hidden from active view.",
                        style = ConsoleTheme.caption
                    )
                }
            },
            confirmButton = {
                Text(
                    text = "ARCHIVE",
                    style = ConsoleTheme.action,
                    modifier = Modifier.clickable {
                        onArchive()
                        showArchiveConfirm = false
                    }
                )
            },
            dismissButton = {
                Text(
                    text = "CANCEL",
                    style = ConsoleTheme.action.copy(color = ConsoleTheme.textMuted),
                    modifier = Modifier.clickable { showArchiveConfirm = false }
                )
            }
        )
    }

    // Delete confirmation dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = ConsoleTheme.background,
            title = { Text("DELETE JOB?", style = ConsoleTheme.header) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = job.title, style = ConsoleTheme.bodyBold)
                    Text(
                        text = "This will permanently delete the job and all associated data. This cannot be undone.",
                        style = ConsoleTheme.caption.copy(color = ConsoleTheme.error)
                    )
                }
            },
            confirmButton = {
                Text(
                    text = "DELETE",
                    style = ConsoleTheme.action.copy(color = ConsoleTheme.error),
                    modifier = Modifier.clickable {
                        onDelete()
                        showDeleteConfirm = false
                    }
                )
            },
            dismissButton = {
                Text(
                    text = "CANCEL",
                    style = ConsoleTheme.action.copy(color = ConsoleTheme.textMuted),
                    modifier = Modifier.clickable { showDeleteConfirm = false }
                )
            }
        )
    }
}


// ════════════════════════════════════════════════════════════════════
// JOB WORKFLOW DIALOG - Shows details and controls advancement
// ════════════════════════════════════════════════════════════════════

@Composable
private fun JobWorkflowDialog(
    job: Job,
    tasks: List<Task>,
    viewModel: JobBoardViewModel,
    onDismiss: () -> Unit
) {
    var showAddTask by remember { mutableStateOf(false) }
    var newTaskTitle by remember { mutableStateOf("") }
    var showAddNote by remember { mutableStateOf(false) }
    var newNote by remember { mutableStateOf("") }
    var showConfirmAdvance by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showMaterialCostDialog by remember { mutableStateOf<Int?>(null) }
    var materialCost by remember { mutableStateOf("") }
    var materialQty by remember { mutableStateOf("1") }
    var materialUnit by remember { mutableStateOf("ea") }
    var materialVendor by remember { mutableStateOf("") }

    // Check if can advance
    val allTasksComplete = tasks.isEmpty() || tasks.all { it.status == TaskStatus.DONE }
    val allMaterialsChecked = job.materials.isEmpty() || job.materials.all { it.checked }
    val canAdvance = when (job.status) {
        JobStatus.TODO -> true // Can always start
        JobStatus.IN_PROGRESS -> allTasksComplete && allMaterialsChecked // Must complete work
        JobStatus.REVIEW -> true // Can finish review
        else -> false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ConsoleTheme.background,
        modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.9f),
        title = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = job.title, style = ConsoleTheme.header)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = "[${job.status.displayName}]",
                                style = ConsoleTheme.captionBold.copy(
                                    color = when (job.status) {
                                        JobStatus.IN_PROGRESS -> ConsoleTheme.warning
                                        JobStatus.REVIEW -> ConsoleTheme.accent
                                        JobStatus.DONE -> ConsoleTheme.success
                                        else -> ConsoleTheme.textMuted
                                    }
                                )
                            )
                            Text(text = job.priority.displayName, style = ConsoleTheme.caption)
                        }
                    }
                    Text(text = "X", style = ConsoleTheme.action, modifier = Modifier.clickable { onDismiss() })
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ═══════════════════════════════════════════════════
                // JOB DETAILS
                // ═══════════════════════════════════════════════════
                if (job.description.isNotEmpty() && job.description.lowercase() != "n/a") {
                    Text(text = "DESCRIPTION", style = ConsoleTheme.captionBold)
                    Text(text = job.description, style = ConsoleTheme.body)
                }

                if (job.expenses.isNotEmpty() && job.expenses.lowercase() != "n/a") {
                    Text(text = "EXPENSES", style = ConsoleTheme.captionBold)
                    Text(text = job.expenses, style = ConsoleTheme.body)
                }

                if (job.crew.isNotEmpty()) {
                    Text(text = "CREW (${job.crew.size})", style = ConsoleTheme.captionBold)
                    job.crew.forEach { member ->
                        Text(text = "• ${member.name} - ${member.occupation}", style = ConsoleTheme.body)
                    }
                }

                ConsoleSeparator()

                // ═══════════════════════════════════════════════════
                // MATERIALS & TOOLS CHECKLIST (must check off before advancing from WORKING)
                // ═══════════════════════════════════════════════════
                if (job.materials.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "CHECKLIST", style = ConsoleTheme.captionBold)
                        val checkedCount = job.materials.count { it.checked }
                        Text(
                            text = "$checkedCount/${job.materials.size}",
                            style = ConsoleTheme.captionBold.copy(
                                color = if (allMaterialsChecked) ConsoleTheme.success else ConsoleTheme.textMuted
                            )
                        )
                    }
                    
                    job.materials.forEachIndexed { index, material ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ConsoleTheme.surface)
                                .clickable { 
                                    if (!material.checked) {
                                        // Show cost dialog when checking off
                                        showMaterialCostDialog = index
                                        materialCost = ""
                                        materialQty = "1"
                                        materialUnit = "ea"
                                        materialVendor = ""
                                    } else {
                                        // Just uncheck
                                        viewModel.toggleMaterial(job.id, index)
                                    }
                                }
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (material.checked) "[X]" else "[  ]",
                                style = ConsoleTheme.bodyBold.copy(
                                    color = if (material.checked) ConsoleTheme.success else ConsoleTheme.textMuted
                                )
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = material.name, style = ConsoleTheme.body)
                                if (material.totalCost > 0) {
                                    Text(
                                        text = "$${String.format("%.2f", material.totalCost)} @ ${material.vendor.ifEmpty { "—" }}",
                                        style = ConsoleTheme.caption.copy(color = ConsoleTheme.success)
                                    )
                                } else if (material.notes.isNotEmpty()) {
                                    Text(text = material.notes, style = ConsoleTheme.caption)
                                }
                            }
                        }
                    }
                }

                ConsoleSeparator()

                // ═══════════════════════════════════════════════════
                // TASKS (must complete before advancing from WORKING)
                // ═══════════════════════════════════════════════════
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "TASKS", style = ConsoleTheme.captionBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val doneCount = tasks.count { it.status == TaskStatus.DONE }
                        Text(
                            text = "$doneCount/${tasks.size}",
                            style = ConsoleTheme.captionBold.copy(
                                color = if (allTasksComplete && tasks.isNotEmpty()) ConsoleTheme.success 
                                        else ConsoleTheme.textMuted
                            )
                        )
                        Text(
                            text = "+ ADD",
                            style = ConsoleTheme.action,
                            modifier = Modifier.clickable { showAddTask = true }
                        )
                    }
                }

                tasks.forEach { task ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ConsoleTheme.surface)
                            .clickable { viewModel.toggleTask(task.id) }
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (task.status == TaskStatus.DONE) "[X]" else "[  ]",
                            style = ConsoleTheme.bodyBold.copy(
                                color = if (task.status == TaskStatus.DONE) ConsoleTheme.success 
                                        else ConsoleTheme.textMuted
                            )
                        )
                        Text(text = task.title, style = ConsoleTheme.body, modifier = Modifier.weight(1f))
                    }
                }

                // Add task input
                if (showAddTask) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BasicTextField(
                            value = newTaskTitle,
                            onValueChange = { newTaskTitle = it },
                            textStyle = ConsoleTheme.body,
                            cursorBrush = SolidColor(ConsoleTheme.cursor),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .background(ConsoleTheme.surface)
                                .padding(10.dp),
                            decorationBox = { innerTextField ->
                                Box {
                                    if (newTaskTitle.isEmpty()) {
                                        Text("Task description...", style = ConsoleTheme.body.copy(color = ConsoleTheme.placeholder))
                                    }
                                    innerTextField()
                                }
                            }
                        )
                        Text(
                            text = "ADD",
                            style = ConsoleTheme.action,
                            modifier = Modifier.clickable {
                                if (newTaskTitle.isNotBlank()) {
                                    viewModel.createTask(job.id, newTaskTitle)
                                    newTaskTitle = ""
                                    showAddTask = false
                                }
                            }
                        )
                    }
                }

                ConsoleSeparator()

                // ═══════════════════════════════════════════════════
                // WORK LOG / NOTES
                // ═══════════════════════════════════════════════════
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "WORK LOG", style = ConsoleTheme.captionBold)
                    Text(
                        text = "+ ADD NOTE",
                        style = ConsoleTheme.action,
                        modifier = Modifier.clickable { showAddNote = true }
                    )
                }

                job.workLog.forEach { note ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ConsoleTheme.surface)
                            .padding(10.dp)
                    ) {
                        Text(text = note.text, style = ConsoleTheme.body)
                        Text(
                            text = formatTimestamp(note.timestamp),
                            style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                        )
                    }
                }

                // Add note input
                if (showAddNote) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        BasicTextField(
                            value = newNote,
                            onValueChange = { newNote = it },
                            textStyle = ConsoleTheme.body,
                            cursorBrush = SolidColor(ConsoleTheme.cursor),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ConsoleTheme.surface)
                                .padding(10.dp)
                                .height(60.dp),
                            decorationBox = { innerTextField ->
                                Box {
                                    if (newNote.isEmpty()) {
                                        Text("Add work notes, extra work orders, etc...", style = ConsoleTheme.body.copy(color = ConsoleTheme.placeholder))
                                    }
                                    innerTextField()
                                }
                            }
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "SAVE NOTE",
                                style = ConsoleTheme.action,
                                modifier = Modifier.clickable {
                                    if (newNote.isNotBlank()) {
                                        viewModel.addWorkLog(job.id, newNote)
                                        newNote = ""
                                        showAddNote = false
                                    }
                                }
                            )
                            Text(
                                text = "CANCEL",
                                style = ConsoleTheme.action.copy(color = ConsoleTheme.textMuted),
                                modifier = Modifier.clickable { 
                                    showAddNote = false
                                    newNote = ""
                                }
                            )
                        }
                    }
                }

                if (job.workLog.isEmpty() && !showAddNote) {
                    Text(text = "No work logged yet", style = ConsoleTheme.caption)
                }

                ConsoleSeparator()

                // ═══════════════════════════════════════════════════
                // WORKFLOW ACTIONS
                // ═══════════════════════════════════════════════════
                when (job.status) {
                    JobStatus.TODO -> {
                        // Can start working
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ConsoleTheme.success.copy(alpha = 0.15f))
                                .clickable { viewModel.moveJob(job.id, JobStatus.IN_PROGRESS) }
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "START WORKING >>", style = ConsoleTheme.header.copy(color = ConsoleTheme.success))
                        }
                    }
                    
                    JobStatus.IN_PROGRESS -> {
                        // Must complete tasks and materials first
                        if (!canAdvance) {
                            Text(
                                text = "! Complete all tasks and check off materials before submitting for review",
                                style = ConsoleTheme.caption.copy(color = ConsoleTheme.warning)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (canAdvance) ConsoleTheme.accent.copy(alpha = 0.15f)
                                    else ConsoleTheme.textDim.copy(alpha = 0.1f)
                                )
                                .clickable(enabled = canAdvance) { showConfirmAdvance = true }
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "SUBMIT FOR REVIEW >>",
                                style = ConsoleTheme.header.copy(
                                    color = if (canAdvance) ConsoleTheme.accent else ConsoleTheme.textDim
                                )
                            )
                        }
                    }
                    
                    JobStatus.REVIEW -> {
                        Text(
                            text = "Review completed work. Add photos or notes for any issues found.",
                            style = ConsoleTheme.caption
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ConsoleTheme.success.copy(alpha = 0.15f))
                                .clickable { viewModel.moveJob(job.id, JobStatus.DONE) }
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "MARK COMPLETE >>", style = ConsoleTheme.header.copy(color = ConsoleTheme.success))
                        }
                    }
                    
                    JobStatus.DONE -> {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "✓ Job completed",
                                style = ConsoleTheme.bodyBold.copy(color = ConsoleTheme.success),
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                            
                            // Generate Invoice button
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(ConsoleTheme.accent.copy(alpha = 0.15f))
                                    .clickable { viewModel.generateInvoice(job) }
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "[$] GENERATE INVOICE", style = ConsoleTheme.header.copy(color = ConsoleTheme.accent))
                            }
                        }
                    }
                    
                    else -> {}
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Delete option
                if (!showDeleteConfirm) {
                    Text(
                        text = "DELETE JOB",
                        style = ConsoleTheme.action.copy(color = ConsoleTheme.error),
                        modifier = Modifier.clickable { showDeleteConfirm = true }
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(text = "Delete this job?", style = ConsoleTheme.body)
                        Text(
                            text = "YES",
                            style = ConsoleTheme.action.copy(color = ConsoleTheme.error),
                            modifier = Modifier.clickable { viewModel.deleteJob(job.id); onDismiss() }
                        )
                        Text(
                            text = "NO",
                            style = ConsoleTheme.action.copy(color = ConsoleTheme.textMuted),
                            modifier = Modifier.clickable { showDeleteConfirm = false }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {}
    )

    // Confirm advance dialog
    if (showConfirmAdvance) {
        AlertDialog(
            onDismissRequest = { showConfirmAdvance = false },
            containerColor = ConsoleTheme.background,
            title = { Text("SUBMIT FOR REVIEW?", style = ConsoleTheme.header) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("This will mark the work phase as complete.", style = ConsoleTheme.body)
                    Text("Make sure all tasks are done and materials are checked.", style = ConsoleTheme.caption)
                }
            },
            confirmButton = {
                Text(
                    text = "SUBMIT",
                    style = ConsoleTheme.action,
                    modifier = Modifier.clickable {
                        viewModel.moveJob(job.id, JobStatus.REVIEW)
                        showConfirmAdvance = false
                    }
                )
            },
            dismissButton = {
                Text(
                    text = "CANCEL",
                    style = ConsoleTheme.action.copy(color = ConsoleTheme.textMuted),
                    modifier = Modifier.clickable { showConfirmAdvance = false }
                )
            }
        )
    }
    
    // Material Cost Dialog - captures expense when checking off material
    showMaterialCostDialog?.let { materialIndex ->
        val material = job.materials.getOrNull(materialIndex)
        if (material != null) {
            AlertDialog(
                onDismissRequest = { showMaterialCostDialog = null },
                containerColor = ConsoleTheme.background,
                title = { Text("MATERIAL PURCHASED", style = ConsoleTheme.header) },
                text = {
                    Column(
                        modifier = Modifier.verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(text = material.name, style = ConsoleTheme.bodyBold)
                        
                        // Quantity and Unit
                        Text(text = "QUANTITY", style = ConsoleTheme.captionBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BasicTextField(
                                value = materialQty,
                                onValueChange = { materialQty = it.filter { c -> c.isDigit() || c == '.' } },
                                textStyle = ConsoleTheme.body,
                                cursorBrush = SolidColor(ConsoleTheme.cursor),
                                singleLine = true,
                                modifier = Modifier.width(80.dp).background(ConsoleTheme.surface).padding(10.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                            )
                            
                            // Unit selector
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("ea", "ft", "lot", "hr").forEach { unit ->
                                    Text(
                                        text = if (materialUnit == unit) "[$unit]" else unit,
                                        style = ConsoleTheme.action.copy(
                                            color = if (materialUnit == unit) ConsoleTheme.accent else ConsoleTheme.textMuted
                                        ),
                                        modifier = Modifier.clickable { materialUnit = unit }
                                    )
                                }
                            }
                        }
                        
                        // Total Cost
                        Text(text = "TOTAL COST ($)", style = ConsoleTheme.captionBold)
                        BasicTextField(
                            value = materialCost,
                            onValueChange = { materialCost = it.filter { c -> c.isDigit() || c == '.' } },
                            textStyle = ConsoleTheme.body,
                            cursorBrush = SolidColor(ConsoleTheme.cursor),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().background(ConsoleTheme.surface).padding(10.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            decorationBox = { innerTextField ->
                                Row {
                                    Text("$ ", style = ConsoleTheme.body.copy(color = ConsoleTheme.textMuted))
                                    Box {
                                        if (materialCost.isEmpty()) {
                                            Text("0.00", style = ConsoleTheme.body.copy(color = ConsoleTheme.placeholder))
                                        }
                                        innerTextField()
                                    }
                                }
                            }
                        )
                        
                        // Vendor
                        Text(text = "VENDOR (optional)", style = ConsoleTheme.captionBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("HD", "Lowes", "Supply", "Other").forEach { v ->
                                Text(
                                    text = if (materialVendor == v) "[$v]" else v,
                                    style = ConsoleTheme.action.copy(
                                        color = if (materialVendor == v) ConsoleTheme.accent else ConsoleTheme.textMuted
                                    ),
                                    modifier = Modifier.clickable { materialVendor = v }
                                )
                            }
                        }
                        
                        ConsoleSeparator()
                        
                        Text(
                            text = "Skip cost to just mark as used",
                            style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                        )
                    }
                },
                confirmButton = {
                    Text(
                        text = "SAVE",
                        style = ConsoleTheme.action,
                        modifier = Modifier.clickable {
                            val qty = materialQty.toDoubleOrNull() ?: 1.0
                            val cost = materialCost.toDoubleOrNull() ?: 0.0
                            val unitCost = if (qty > 0) cost / qty else 0.0
                            viewModel.updateMaterialCost(
                                jobId = job.id,
                                materialIndex = materialIndex,
                                quantity = qty,
                                unit = materialUnit,
                                unitCost = unitCost,
                                totalCost = cost,
                                vendor = materialVendor
                            )
                            showMaterialCostDialog = null
                        }
                    )
                },
                dismissButton = {
                    Text(
                        text = "SKIP",
                        style = ConsoleTheme.action.copy(color = ConsoleTheme.textMuted),
                        modifier = Modifier.clickable {
                            viewModel.toggleMaterial(job.id, materialIndex)
                            showMaterialCostDialog = null
                        }
                    )
                }
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// CREATE JOB DIALOG WITH PREVIEW
// ════════════════════════════════════════════════════════════════════

private enum class JobDialogStep {
    EDIT,
    PREVIEW
}

@Composable
private fun CreateJobDialogWithPreview(
    onDismiss: () -> Unit,
    onCreate: (String, String, Priority, String, Int, List<CrewMember>, List<Material>, Long?, Long?) -> Unit
) {
    var currentStep by remember { mutableStateOf(JobDialogStep.EDIT) }
    
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var priority by remember { mutableStateOf(Priority.MEDIUM) }
    var expenses by remember { mutableStateOf("") }
    var crewSize by remember { mutableStateOf("1") }
    var crewMembers by remember { mutableStateOf(listOf<CrewMember>()) }
    var newMemberName by remember { mutableStateOf("") }
    var newMemberOccupation by remember { mutableStateOf("") }
    var materials by remember { mutableStateOf(listOf<Material>()) }
    var newMaterialName by remember { mutableStateOf("") }
    
    // Date fields
    var startDateStr by remember { mutableStateOf("") }
    var endDateStr by remember { mutableStateOf("") }
    
    // Track if user has entered any data (for dismiss protection)
    val hasEnteredData = title.isNotEmpty() || description.isNotEmpty() || 
                         expenses.isNotEmpty() || materials.isNotEmpty() || 
                         crewMembers.isNotEmpty()
    
    // Warning flash state when trying to dismiss with data
    var showWarningFlash by remember { mutableStateOf(false) }
    
    // Auto-reset warning flash
    LaunchedEffect(showWarningFlash) {
        if (showWarningFlash) {
            kotlinx.coroutines.delay(300)
            showWarningFlash = false
        }
    }
    
    // Border color for warning flash
    val inputBorderColor by animateColorAsState(
        targetValue = if (showWarningFlash) ConsoleTheme.error else ConsoleTheme.surface,
        animationSpec = tween(durationMillis = 150),
        label = "inputBorder"
    )
    
    // Custom dismiss handler
    val handleDismiss: () -> Unit = {
        if (hasEnteredData) {
            // Flash warning instead of dismissing
            showWarningFlash = true
        } else {
            onDismiss()
        }
    }
    
    // Parse dates
    val dateFormat = remember { SimpleDateFormat("MM/dd/yyyy", Locale.US) }
    fun parseDate(str: String): Long? {
        return try {
            if (str.isBlank()) null else dateFormat.parse(str)?.time
        } catch (e: Exception) { null }
    }

    AlertDialog(
        onDismissRequest = handleDismiss,
        containerColor = ConsoleTheme.background,
        modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.55f),
        title = { 
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (currentStep == JobDialogStep.EDIT) "NEW JOB" else "PREVIEW",
                        style = ConsoleTheme.header
                    )
                    if (currentStep == JobDialogStep.PREVIEW) {
                        Text(
                            text = "[EDIT]",
                            style = ConsoleTheme.action.copy(color = ConsoleTheme.accent),
                            modifier = Modifier.clickable { currentStep = JobDialogStep.EDIT }
                        )
                    }
                }
                if (hasEnteredData && currentStep == JobDialogStep.EDIT) {
                    Text(
                        text = "Use CANCEL to close or PREVIEW to view summary",
                        style = ConsoleTheme.caption.copy(color = ConsoleTheme.warning)
                    )
                }
            }
        },
        text = {
            if (currentStep == JobDialogStep.EDIT) {
                // ═══════════════════════════════════════════════════════════════
                // EDIT MODE
                // ═══════════════════════════════════════════════════════════════
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Title
                    Text(text = "JOB TITLE *", style = ConsoleTheme.captionBold)
                    BasicTextField(
                        value = title,
                        onValueChange = { title = it },
                        textStyle = ConsoleTheme.body,
                        cursorBrush = SolidColor(ConsoleTheme.cursor),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().background(inputBorderColor).padding(2.dp)
                            .background(ConsoleTheme.surface).padding(10.dp),
                        decorationBox = { innerTextField ->
                            Box {
                                if (title.isEmpty()) Text("Enter job title...", style = ConsoleTheme.body.copy(color = ConsoleTheme.placeholder))
                                innerTextField()
                            }
                        }
                    )

                    // Priority
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "PRIORITY:", style = ConsoleTheme.captionBold)
                        Priority.values().forEach { p ->
                            val isSelected = priority == p
                            Text(
                                text = if (isSelected) "[${p.displayName}]" else p.displayName,
                                style = ConsoleTheme.action.copy(
                                    color = when { isSelected && p == Priority.URGENT -> ConsoleTheme.error
                                        isSelected && p == Priority.HIGH -> ConsoleTheme.warning
                                        isSelected -> ConsoleTheme.accent
                                        else -> ConsoleTheme.textMuted }
                                ),
                                modifier = Modifier.clickable { priority = p }
                            )
                        }
                    }

                    // Description
                    Text(text = "DESCRIPTION (or N/A)", style = ConsoleTheme.captionBold)
                    BasicTextField(
                        value = description, onValueChange = { description = it },
                        textStyle = ConsoleTheme.bodySmall, cursorBrush = SolidColor(ConsoleTheme.cursor),
                        modifier = Modifier.fillMaxWidth().background(inputBorderColor).padding(2.dp)
                            .background(ConsoleTheme.surface).padding(10.dp).height(40.dp),
                        decorationBox = { innerTextField ->
                            Box { if (description.isEmpty()) Text("Description or N/A...", style = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.placeholder)); innerTextField() }
                        }
                    )

                    // Expenses
                    Text(text = "EXPENSES (or N/A)", style = ConsoleTheme.captionBold)
                    BasicTextField(
                        value = expenses, onValueChange = { expenses = it },
                        textStyle = ConsoleTheme.bodySmall, cursorBrush = SolidColor(ConsoleTheme.cursor), singleLine = true,
                        modifier = Modifier.fillMaxWidth().background(inputBorderColor).padding(2.dp)
                            .background(ConsoleTheme.surface).padding(10.dp),
                        decorationBox = { innerTextField ->
                            Box { if (expenses.isEmpty()) Text("$0.00 or N/A...", style = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.placeholder)); innerTextField() }
                        }
                    )

                    // Date fields (optional)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "START (MM/DD/YYYY)", style = ConsoleTheme.caption)
                            BasicTextField(
                                value = startDateStr, onValueChange = { startDateStr = it.take(10) },
                                textStyle = ConsoleTheme.bodySmall, cursorBrush = SolidColor(ConsoleTheme.cursor), singleLine = true,
                                modifier = Modifier.fillMaxWidth().background(inputBorderColor).padding(1.dp)
                                    .background(ConsoleTheme.surface).padding(8.dp),
                                decorationBox = { innerTextField ->
                                    Box { if (startDateStr.isEmpty()) Text("Optional", style = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.placeholder)); innerTextField() }
                                }
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "END (MM/DD/YYYY)", style = ConsoleTheme.caption)
                            BasicTextField(
                                value = endDateStr, onValueChange = { endDateStr = it.take(10) },
                                textStyle = ConsoleTheme.bodySmall, cursorBrush = SolidColor(ConsoleTheme.cursor), singleLine = true,
                                modifier = Modifier.fillMaxWidth().background(inputBorderColor).padding(1.dp)
                                    .background(ConsoleTheme.surface).padding(8.dp),
                                decorationBox = { innerTextField ->
                                    Box { if (endDateStr.isEmpty()) Text("Optional", style = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.placeholder)); innerTextField() }
                                }
                            )
                        }
                    }

                    ConsoleSeparator()

                    // Materials/Tools checklist
                    Text(text = "CHECKLIST (tools & materials)", style = ConsoleTheme.captionBold)
                    materials.forEach { material ->
                        Row(modifier = Modifier.fillMaxWidth().background(ConsoleTheme.surface).padding(6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "• ${material.name}", style = ConsoleTheme.body)
                            Text(text = "X", style = ConsoleTheme.action.copy(color = ConsoleTheme.error),
                                modifier = Modifier.clickable { materials = materials.filter { it != material } })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        BasicTextField(
                            value = newMaterialName, onValueChange = { newMaterialName = it },
                            textStyle = ConsoleTheme.bodySmall, cursorBrush = SolidColor(ConsoleTheme.cursor), singleLine = true,
                            modifier = Modifier.weight(1f).background(ConsoleTheme.surface).padding(6.dp),
                            decorationBox = { innerTextField ->
                                Box { if (newMaterialName.isEmpty()) Text("Add item...", style = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.placeholder)); innerTextField() }
                            }
                        )
                        Text(text = "+", style = ConsoleTheme.action, modifier = Modifier.clickable {
                            if (newMaterialName.isNotBlank()) { materials = materials + Material(name = newMaterialName); newMaterialName = "" }
                        })
                    }

                    // Crew
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "CREW:", style = ConsoleTheme.captionBold)
                        BasicTextField(
                            value = crewSize, onValueChange = { crewSize = it.filter { c -> c.isDigit() }.ifEmpty { "1" } },
                            textStyle = ConsoleTheme.body, cursorBrush = SolidColor(ConsoleTheme.cursor), singleLine = true,
                            modifier = Modifier.width(40.dp).background(ConsoleTheme.surface).padding(6.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                    if (crewMembers.isNotEmpty()) {
                        crewMembers.forEach { member ->
                            Row(modifier = Modifier.fillMaxWidth().background(ConsoleTheme.surface).padding(4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "${member.name} - ${member.occupation}", style = ConsoleTheme.bodySmall)
                                Text(text = "X", style = ConsoleTheme.action.copy(color = ConsoleTheme.error),
                                    modifier = Modifier.clickable { crewMembers = crewMembers.filter { it != member } })
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        BasicTextField(
                            value = newMemberName, onValueChange = { newMemberName = it },
                            textStyle = ConsoleTheme.bodySmall, cursorBrush = SolidColor(ConsoleTheme.cursor), singleLine = true,
                            modifier = Modifier.weight(0.4f).background(ConsoleTheme.surface).padding(4.dp),
                            decorationBox = { innerTextField ->
                                Box { if (newMemberName.isEmpty()) Text("Name", style = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.placeholder)); innerTextField() }
                            }
                        )
                        BasicTextField(
                            value = newMemberOccupation, onValueChange = { newMemberOccupation = it },
                            textStyle = ConsoleTheme.bodySmall, cursorBrush = SolidColor(ConsoleTheme.cursor), singleLine = true,
                            modifier = Modifier.weight(0.4f).background(ConsoleTheme.surface).padding(4.dp),
                            decorationBox = { innerTextField ->
                                Box { if (newMemberOccupation.isEmpty()) Text("Role", style = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.placeholder)); innerTextField() }
                            }
                        )
                        Text(text = "+", style = ConsoleTheme.action, modifier = Modifier.clickable {
                            if (newMemberName.isNotBlank()) {
                                crewMembers = crewMembers + CrewMember(name = newMemberName, occupation = newMemberOccupation.ifBlank { "Worker" })
                                newMemberName = ""; newMemberOccupation = ""
                            }
                        })
                    }
                }
            } else {
                // ═══════════════════════════════════════════════════════════════
                // PREVIEW MODE
                // ═══════════════════════════════════════════════════════════════
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Title and Priority
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Text(text = title.ifEmpty { "(No title)" }, style = ConsoleTheme.header)
                        Text(text = "[${priority.displayName}]", style = ConsoleTheme.bodyBold.copy(
                            color = when (priority) { Priority.URGENT -> ConsoleTheme.error; Priority.HIGH -> ConsoleTheme.warning; else -> ConsoleTheme.textMuted }
                        ))
                    }
                    
                    // Created timestamp (will be set on create)
                    Text(text = "Created: ${formatShortDate(System.currentTimeMillis())}", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
                    
                    ConsoleSeparator()
                    
                    // Description
                    if (description.isNotEmpty()) {
                        Text(text = "DESCRIPTION", style = ConsoleTheme.captionBold)
                        Text(text = description, style = ConsoleTheme.body)
                    }
                    
                    // Expenses
                    if (expenses.isNotEmpty()) {
                        Text(text = "EXPENSES", style = ConsoleTheme.captionBold)
                        Text(text = expenses, style = ConsoleTheme.body)
                    }
                    
                    // Dates
                    if (startDateStr.isNotEmpty() || endDateStr.isNotEmpty()) {
                        Text(text = "SCHEDULE", style = ConsoleTheme.captionBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            if (startDateStr.isNotEmpty()) Text(text = "Start: $startDateStr", style = ConsoleTheme.body)
                            if (endDateStr.isNotEmpty()) Text(text = "End: $endDateStr", style = ConsoleTheme.body)
                        }
                    }
                    
                    // Checklist
                    if (materials.isNotEmpty()) {
                        ConsoleSeparator()
                        Text(text = "CHECKLIST (${materials.size} items)", style = ConsoleTheme.captionBold)
                        materials.forEach { material ->
                            Text(text = "□ ${material.name}", style = ConsoleTheme.body)
                        }
                    }
                    
                    // Crew
                    if (crewMembers.isNotEmpty() || (crewSize.toIntOrNull() ?: 1) > 1) {
                        ConsoleSeparator()
                        Text(text = "CREW (${crewSize})", style = ConsoleTheme.captionBold)
                        if (crewMembers.isNotEmpty()) {
                            crewMembers.forEach { member ->
                                Text(text = "• ${member.name} - ${member.occupation}", style = ConsoleTheme.body)
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Confirmation note
                    Text(
                        text = "Review above details. Tap CREATE JOB to add to board.",
                        style = ConsoleTheme.caption.copy(color = ConsoleTheme.warning)
                    )
                }
            }
        },
        confirmButton = {
            if (currentStep == JobDialogStep.EDIT) {
                Text(
                    text = "PREVIEW >>",
                    style = ConsoleTheme.action.copy(color = if (title.isNotBlank()) ConsoleTheme.accent else ConsoleTheme.textDim),
                    modifier = Modifier.clickable {
                        if (title.isNotBlank()) currentStep = JobDialogStep.PREVIEW
                    }
                )
            } else {
                Text(
                    text = "CREATE JOB",
                    style = ConsoleTheme.action.copy(color = ConsoleTheme.success),
                    modifier = Modifier.clickable {
                        onCreate(title, description, priority, expenses, crewSize.toIntOrNull() ?: 1, 
                                 crewMembers, materials, parseDate(startDateStr), parseDate(endDateStr))
                    }
                )
            }
        },
        dismissButton = {
            Text(
                text = "CANCEL",
                style = ConsoleTheme.action.copy(color = ConsoleTheme.textMuted),
                modifier = Modifier.clickable { onDismiss() }
            )
        }
    )
}

// ════════════════════════════════════════════════════════════════════
// HELPERS
// ════════════════════════════════════════════════════════════════════

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun formatShortDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MM/dd/yy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
