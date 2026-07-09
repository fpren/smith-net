package com.guildofsmiths.trademesh.ui.jobboard

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
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
import com.guildofsmiths.trademesh.ui.invoice.InvoicePreviewDialog
import com.guildofsmiths.trademesh.ui.theme2.LocalSmithColors
import com.guildofsmiths.trademesh.ui.theme2.SmithButton
import com.guildofsmiths.trademesh.ui.theme2.SmithButtonVariant
import com.guildofsmiths.trademesh.ui.theme2.SmithConfirmDialog
import com.guildofsmiths.trademesh.ui.theme2.SmithDialog
import com.guildofsmiths.trademesh.ui.theme2.SmithEmptyState
import com.guildofsmiths.trademesh.ui.theme2.SmithErrorState
import com.guildofsmiths.trademesh.ui.theme2.SmithLoadingState
import com.guildofsmiths.trademesh.ui.theme2.SmithType
import com.guildofsmiths.trademesh.data.ClientInfo
import com.guildofsmiths.trademesh.data.ClientRepository
import com.guildofsmiths.trademesh.data.RoleContext
import com.guildofsmiths.trademesh.data.Permission
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
    viewModel: JobBoardViewModel = viewModel(),
    /** Invoked when the user taps START WORKING (job-level) or ▶ on a task
     *  row. Caller should clock the user in with the supplied IDs. */
    onClockIn: (jobId: String, jobTitle: String, taskId: String?) -> Unit = { _, _, _ -> },
    /** Active clock entry context — supplied by caller from
     *  TimeTrackingViewModel.activeEntry. Used to decide whether to swap
     *  silently, prompt for confirmation, or no-op when the user starts
     *  another job/task. */
    currentlyClockedIn: Boolean = false,
    currentClockedInJobId: String? = null,
    currentClockedInJobTitle: String? = null,
    currentClockedInTaskId: String? = null,
    /** Called when the user confirms a clock-switch (clock-out current then
     *  clock-in new). The old job's stage is not regressed by the caller. */
    onSwitchClock: (jobId: String, jobTitle: String, taskId: String?) -> Unit = { _, _, _ -> },
    /** Invoked by the "+ NEW" button. Navigates to the guided NewJobFlow wizard
     *  (NavRoutes.NEW_JOB) so this matches the dashboard JOBS "[+ NEW]" action. */
    onNewJob: () -> Unit = {}
) {
    val colors = LocalSmithColors.current
    val jobs by viewModel.jobs.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    val selectedJob by viewModel.selectedJob.collectAsState()
    val tasks by viewModel.tasks.collectAsState()
    val generatedInvoice by viewModel.generatedInvoice.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Check for pending intent from proposal
    val pendingIntent by com.guildofsmiths.trademesh.data.IntentRepository.pendingIntentForJob.collectAsState()

    var showCreateDialog by remember { mutableStateOf(false) }
    var filterStatus by remember { mutableStateOf<JobStatus?>(null) }

    // Auto-open create dialog when arriving from a confirmed proposal
    LaunchedEffect(pendingIntent) {
        if (pendingIntent != null) {
            showCreateDialog = true
        }
    }

    // Stats for active jobs only
    val stats = remember(jobs) {
        mapOf(
            JobStatus.SCHEDULED to jobs.count { it.status == JobStatus.SCHEDULED },
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
            .background(colors.bgBase)
    ) {
        ConsoleHeader(
            title = when {
                RoleContext.isTeamMember() -> "MY TASKS"
                RoleContext.isGC() -> "PROJECTS"
                else -> "JOB BOARD"
            },
            onBackClick = onNavigateBack
        )
        ConsoleSeparator()

        // Stats Dashboard
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.bgPanel)
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            listOf(
                JobStatus.SCHEDULED to "SCHED",
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
                        .background(if (isSelected) colors.accent.copy(alpha = 0.1f) else colors.bgPanel)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = count.toString(),
                        style = SmithType.header.copy(
                            color = when {
                                isSelected -> colors.accent
                                status == JobStatus.IN_PROGRESS && count > 0 -> colors.attention
                                status == JobStatus.DONE && count > 0 -> colors.statusOnline
                                else -> colors.ink
                            }
                        )
                    )
                    Text(
                        text = label,
                        style = SmithType.caption.copy(
                            color = if (isSelected) colors.accent else colors.inkMuted
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
                style = SmithType.captionBold.copy(color = colors.inkMuted)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (filterStatus != null) {
                    Text(
                        text = "CLEAR",
                        style = SmithType.action.copy(color = colors.inkMuted),
                        modifier = Modifier.clickable { filterStatus = null }
                    )
                }
                if (!RoleContext.isTeamMember()) {
                    Text(
                        text = "+ NEW",
                        style = SmithType.action.copy(color = colors.accent),
                        modifier = Modifier.clickable { onNewJob() }
                    )
                }
            }
        }

        ConsoleSeparator()

        // Active jobs list with swipe actions — Smith trio per JobBoardViewModel's
        // isLoading/error flags (same signal ArchiveScreen's job tab already wires to).
        Box(modifier = Modifier.weight(1f)) {
            when {
                error != null -> SmithErrorState(
                    message = error ?: "Couldn't load jobs.",
                    onRetry = { viewModel.loadJobs() }
                )
                isLoading && jobs.isEmpty() -> SmithLoadingState(label = "LOADING JOBS")
                filteredJobs.isEmpty() -> SmithEmptyState(
                    title = if (filterStatus != null) "No ${filterStatus!!.displayName.lowercase()} jobs"
                            else "No jobs yet",
                    hint = if (RoleContext.isTeamMember()) "Tasks will appear when assigned by your lead"
                           else "Tap + NEW to create one"
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
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
                }
            }
        }
    }

    // Create Job Dialog with Preview
    if (showCreateDialog) {
        val intent = pendingIntent
        val savedClients = remember(jobs) { ClientRepository.getClients(jobs) }
        CreateJobDialogWithPreview(
            onDismiss = {
                showCreateDialog = false
                com.guildofsmiths.trademesh.data.IntentRepository.clearPendingIntentForJob()
            },
            onCreate = { title, desc, priority, expenses, crewSize, crew, materials, startDate, endDate, cName, cPhone, cAddr ->
                // Re-read the pending intent at click time (the captured `intent` may be
                // stale if a recomposition raced with the tap).
                val sourceIntent = com.guildofsmiths.trademesh.data.IntentRepository
                    .pendingIntentForJob.value ?: intent
                val resolvedClient = cName.ifBlank { sourceIntent?.parties?.firstOrNull().orEmpty() }
                if (resolvedClient.isNotBlank() && (cPhone.isNotBlank() || cAddr.isNotBlank())) {
                    ClientRepository.saveClientOverride(resolvedClient, resolvedClient, cPhone, cAddr)
                }
                viewModel.createJob(
                    title = title,
                    description = desc,
                    priority = priority,
                    expensesNote = expenses,
                    crewSize = crewSize,
                    crew = crew,
                    materials = materials,
                    estimatedStartDate = startDate,
                    estimatedEndDate = endDate,
                    clientName = resolvedClient.ifBlank { null },
                    clientPhone = cPhone,
                    clientAddress = cAddr,
                    equipmentList = sourceIntent?.equipmentNeeded ?: emptyList(),
                    taskDescriptions = sourceIntent?.taskDescriptions ?: emptyList(),
                    proposalId = sourceIntent?.intentId
                )
                showCreateDialog = false
                com.guildofsmiths.trademesh.data.IntentRepository.clearPendingIntentForJob()
            },
            initialTitle = intent?.scopeStatement ?: "",
            initialDescription = buildIntentDescription(intent),
            initialCrewSize = intent?.crewSize ?: 1,
            initialMaterials = intent?.suppliesNeeded?.map { Material(name = it) } ?: emptyList(),
            initialClientName = intent?.parties?.firstOrNull().orEmpty(),
            savedClients = savedClients
        )
    }

    // Pending switch-clock prompt (cross-job switch). Resolved by user tap.
    var pendingSwitch by remember { mutableStateOf<ClockSwitchRequest?>(null) }

    // Job Detail/Workflow Dialog
    selectedJob?.let { job ->
        JobWorkflowDialog(
            job = job,
            tasks = tasks,
            viewModel = viewModel,
            onDismiss = { viewModel.selectJob(null) },
            startClock = { jobId, jobTitle, taskId, performStart ->
                val activeJob = currentClockedInJobId
                val activeTask = currentClockedInTaskId
                when {
                    !currentlyClockedIn -> {
                        performStart()
                        onClockIn(jobId, jobTitle, taskId)
                    }
                    activeJob == jobId && activeTask == taskId -> Unit
                    activeJob == jobId && activeJob != null -> {
                        performStart()
                        onSwitchClock(jobId, jobTitle, taskId)
                    }
                    else -> {
                        // Cross-job: defer performStart so [x] CANCEL leaves
                        // task PENDING and old job's clock untouched.
                        pendingSwitch = ClockSwitchRequest(
                            jobId = jobId, jobTitle = jobTitle, taskId = taskId,
                            oldJobTitle = activeJob?.let { id ->
                                jobs.firstOrNull { it.id == id }?.let { j -> j.clientName ?: j.title }
                            } ?: currentClockedInJobTitle ?: "general time",
                            performStart = performStart
                        )
                    }
                }
            }
        )
    }

    pendingSwitch?.let { req ->
        SmithConfirmDialog(
            title = "Switch clock?",
            body = "You're on the clock for ${req.oldJobTitle}.\nClock out and start ${req.jobTitle}?",
            confirmText = "SWITCH",
            confirmIsDanger = false,
            onConfirm = {
                req.performStart()
                onSwitchClock(req.jobId, req.jobTitle, req.taskId)
                pendingSwitch = null
            },
            onDismiss = { pendingSwitch = null },
        )
    }

    // Invoice Preview Dialog + rich PREVIEW & SHARE bottom sheet
    var showRichPreview by remember { mutableStateOf(false) }
    generatedInvoice?.let { invoice ->
        val invoiceJob = jobs.firstOrNull { it.id == invoice.jobId }
        val timeEntries by com.guildofsmiths.trademesh.data.TimeEntryRepository.entries.collectAsState()
        val bolText = remember(invoiceJob, timeEntries) {
            invoiceJob?.let { com.guildofsmiths.trademesh.ui.expenses.BolFormatter.formatAsText(it, timeEntries) }
        }
        InvoicePreviewDialog(
            invoice = invoice,
            onDismiss = { viewModel.clearInvoice() },
            onShare = { text ->
                viewModel.markShared(invoice.id)  // record intent before the intent fires
                val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_SUBJECT, "Invoice ${invoice.invoiceNumber}")
                    putExtra(android.content.Intent.EXTRA_TEXT, text)
                }
                context.startActivity(android.content.Intent.createChooser(share, "Share Invoice"))
                viewModel.clearInvoice()
            },
            bolText = bolText,
            onShareBol = { bol ->
                val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_SUBJECT, "BOL — ${invoiceJob?.clientName ?: invoiceJob?.title}")
                    putExtra(android.content.Intent.EXTRA_TEXT, bol)
                }
                context.startActivity(android.content.Intent.createChooser(share, "Share BOL"))
            },
            onPreviewRendered = if (invoiceJob != null) {
                { showRichPreview = true }
            } else null
        )
        if (showRichPreview && invoiceJob != null) {
            com.guildofsmiths.trademesh.ui.expenses.InvoicePreviewBottomSheet(
                invoice = invoice,
                job = invoiceJob,
                timeEntries = timeEntries,
                onDismiss = {
                    showRichPreview = false
                    viewModel.clearInvoice()
                }
            )
        }
    }
}

@Composable
private fun JobRow(job: Job, onClick: () -> Unit) {
    val colors = LocalSmithColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bgPanel)
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
                style = SmithType.bodyBold.copy(
                    color = when (job.status) {
                        JobStatus.IN_PROGRESS -> colors.attention
                        JobStatus.REVIEW -> colors.accent
                        JobStatus.DONE -> colors.statusOnline
                        else -> colors.inkMuted
                    }
                )
            )
            
            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (job.priority == Priority.HIGH || job.priority == Priority.URGENT) {
                        Text(
                            text = if (job.priority == Priority.URGENT) "!!" else "!",
                            style = SmithType.bodyBold.copy(
                                color = if (job.priority == Priority.URGENT) colors.statusError else colors.attention
                            )
                        )
                    }
                    Text(text = job.title, style = SmithType.body.copy(color = colors.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = job.status.displayName, style = SmithType.caption.copy(color = colors.inkMuted))
                    if (job.crewSize > 1) {
                        Text(text = "${job.crewSize} crew", style = SmithType.caption.copy(color = colors.inkMuted))
                    }
                }
            }
        }

        Text(text = "VIEW >", style = SmithType.action.copy(color = colors.accent), modifier = Modifier.clickable { onClick() })
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
    val colors = LocalSmithColors.current
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
                    .background(colors.statusError.copy(alpha = 0.3f))
                    .padding(start = 16.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "DELETE →",
                    style = SmithType.bodyBold.copy(color = colors.statusError)
                )
            }
        }

        // Archive background (revealed on swipe left)
        if (offsetX < -50f) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(colors.attention.copy(alpha = 0.3f))
                    .padding(end = 16.dp),
                contentAlignment = Alignment.CenterEnd
            ) {
                Text(
                    text = "← ARCHIVE",
                    style = SmithType.bodyBold.copy(color = colors.attention)
                )
            }
        }
        
        // Main row with swipe gesture
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .background(colors.bgPanel)
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
                    style = SmithType.bodyBold.copy(
                        color = when (job.status) {
                            JobStatus.IN_PROGRESS -> colors.attention
                            JobStatus.REVIEW -> colors.accent
                            JobStatus.DONE -> colors.statusOnline
                            else -> colors.inkMuted
                        }
                    )
                )
                
                Column(modifier = Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (job.priority == Priority.HIGH || job.priority == Priority.URGENT) {
                            Text(
                                text = if (job.priority == Priority.URGENT) "!!" else "!",
                                style = SmithType.bodyBold.copy(
                                    color = if (job.priority == Priority.URGENT) colors.statusError else colors.attention
                                )
                            )
                        }
                        Text(text = job.title, style = SmithType.body.copy(color = colors.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = job.status.displayName, style = SmithType.caption.copy(color = colors.inkMuted))
                        // Show created date
                        Text(
                            text = formatShortDate(job.createdAt),
                            style = SmithType.caption.copy(color = colors.inkMuted)
                        )
                    }
                }
            }

            Text(text = ">", style = SmithType.action.copy(color = colors.accent))
        }
    }
    
    // Archive confirmation dialog
    if (showArchiveConfirm) {
        SmithConfirmDialog(
            title = "Archive job?",
            body = "${job.title}\n\nArchived jobs are saved for reference but hidden from active view.",
            confirmText = "ARCHIVE",
            confirmIsDanger = false,
            onConfirm = {
                onArchive()
                showArchiveConfirm = false
            },
            onDismiss = { showArchiveConfirm = false },
        )
    }

    // Delete confirmation dialog
    if (showDeleteConfirm) {
        SmithConfirmDialog(
            title = "Delete job?",
            body = "${job.title}\n\nThis will permanently delete the job and all associated data. This cannot be undone.",
            confirmText = "DELETE",
            onConfirm = {
                onDelete()
                showDeleteConfirm = false
            },
            onDismiss = { showDeleteConfirm = false },
        )
    }
}


// ════════════════════════════════════════════════════════════════════
// JOB WORKFLOW DIALOG - Shows details and controls advancement
// ════════════════════════════════════════════════════════════════════

private data class ClockSwitchRequest(
    val jobId: String,
    val jobTitle: String,
    val taskId: String?,
    val oldJobTitle: String,
    /** Local mutations (startTask, moveJob) that should ONLY apply if the user
     *  actually confirms the switch — otherwise we'd leave a task in
     *  IN_PROGRESS while time is tagged elsewhere. */
    val performStart: () -> Unit
)

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun JobWorkflowDialog(
    job: Job,
    tasks: List<Task>,
    viewModel: JobBoardViewModel,
    onDismiss: () -> Unit,
    /** Single entry point for "user wants to start working on this job/task".
     *  performStart applies the local Job/Task mutations; the screen-level
     *  router decides whether to invoke it eagerly or defer until the user
     *  confirms a cross-job clock switch. */
    startClock: (jobId: String, jobTitle: String, taskId: String?, performStart: () -> Unit) -> Unit = { _, _, _, _ -> }
) {
    val colors = LocalSmithColors.current
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
    var showClientPicker by remember { mutableStateOf(false) }
    var clientPickerSearch by remember { mutableStateOf("") }
    var clientNewName by remember { mutableStateOf("") }
    var clientNewPhone by remember { mutableStateOf("") }
    var clientNewAddress by remember { mutableStateOf("") }
    val allJobsForClients by viewModel.jobs.collectAsState()

    // Check if can advance
    val allTasksComplete = tasks.isEmpty() || tasks.all { it.status == TaskStatus.DONE }
    val allMaterialsChecked = job.materials.isEmpty() || job.materials.all { it.checked }
    val canAdvance = when (job.status) {
        JobStatus.SCHEDULED -> true // Scheduled jobs can be started
        JobStatus.TODO -> true // Can always start
        JobStatus.IN_PROGRESS -> allTasksComplete && allMaterialsChecked // Must complete work
        JobStatus.REVIEW -> true // Can finish review
        else -> false
    }

    SmithDialog(
        title = job.title,
        onDismiss = onDismiss,
        sizeFraction = 0.95f to 0.9f,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "[${job.status.displayName}]",
                    style = SmithType.captionBold.copy(
                        color = when (job.status) {
                            JobStatus.IN_PROGRESS -> colors.attention
                            JobStatus.REVIEW -> colors.accent
                            JobStatus.DONE -> colors.statusOnline
                            else -> colors.inkMuted
                        }
                    )
                )
                Text(text = job.priority.displayName, style = SmithType.caption.copy(color = colors.inkMuted))
            }
            Text(text = "X", style = SmithType.action.copy(color = colors.accent), modifier = Modifier.clickable { onDismiss() })
        }
        Spacer(modifier = Modifier.height(8.dp))
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
                // ═══════════════════════════════════════════════════
                // CLIENT
                // ═══════════════════════════════════════════════════
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = "CLIENT", style = SmithType.captionBold.copy(color = colors.inkMuted))
                        val cName = job.clientName
                        if (cName.isNullOrBlank()) {
                            Text(
                                text = "— not set —",
                                style = SmithType.body.copy(color = colors.inkMuted)
                            )
                        } else {
                            Text(text = cName, style = SmithType.body.copy(color = colors.ink))
                            if (job.clientPhone.isNotBlank()) Text(
                                text = job.clientPhone,
                                style = SmithType.bodySmall.copy(color = colors.inkMuted)
                            )
                            if (job.clientAddress.isNotBlank()) Text(
                                text = job.clientAddress,
                                style = SmithType.bodySmall.copy(color = colors.inkMuted)
                            )
                        }
                    }
                    Text(
                        text = if (showClientPicker) "[CLOSE]" else if (job.clientName.isNullOrBlank()) "[+ LINK]" else "[CHANGE]",
                        style = SmithType.action.copy(color = colors.accent),
                        modifier = Modifier.clickable {
                            showClientPicker = !showClientPicker
                            if (showClientPicker) {
                                clientNewName = job.clientName.orEmpty()
                                clientNewPhone = job.clientPhone
                                clientNewAddress = job.clientAddress
                                clientPickerSearch = ""
                            }
                        }
                    )
                }
                if (showClientPicker) {
                    val saved = remember(allJobsForClients) {
                        ClientRepository.getClients(allJobsForClients)
                    }
                    Column(
                        modifier = Modifier.fillMaxWidth().background(colors.bgPanel).padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (saved.isNotEmpty()) {
                            BasicTextField(
                                value = clientPickerSearch, onValueChange = { clientPickerSearch = it },
                                textStyle = SmithType.bodySmall.copy(color = colors.inkMuted), cursorBrush = SolidColor(colors.ink),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().background(colors.bgBase).padding(8.dp),
                                decorationBox = { inner ->
                                    Box {
                                        if (clientPickerSearch.isEmpty()) Text(
                                            "Search saved clients...",
                                            style = SmithType.bodySmall.copy(color = colors.inkMuted)
                                        )
                                        inner()
                                    }
                                }
                            )
                            saved
                                .filter { clientPickerSearch.isBlank() || it.name.contains(clientPickerSearch, ignoreCase = true) }
                                .take(6)
                                .forEach { c ->
                                    Text(
                                        text = "• ${c.name}" + if (c.jobCount > 0) "  (${c.jobCount})" else "",
                                        style = SmithType.bodySmall.copy(color = colors.accent),
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            viewModel.setClient(job.id, c.name, c.phone, c.address)
                                            showClientPicker = false
                                        }.padding(vertical = 4.dp)
                                    )
                                }
                            ConsoleSeparator()
                        }
                        Text("OR ADD NEW", style = SmithType.captionBold.copy(color = colors.inkMuted))
                        BasicTextField(
                            value = clientNewName, onValueChange = { clientNewName = it },
                            textStyle = SmithType.body.copy(color = colors.ink), cursorBrush = SolidColor(colors.ink), singleLine = true,
                            modifier = Modifier.fillMaxWidth().background(colors.bgBase).padding(8.dp),
                            decorationBox = { inner ->
                                Box {
                                    if (clientNewName.isEmpty()) Text(
                                        "Client name (e.g. Aegis Assure Inc)",
                                        style = SmithType.body.copy(color = colors.inkMuted)
                                    )
                                    inner()
                                }
                            }
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            BasicTextField(
                                value = clientNewPhone, onValueChange = { clientNewPhone = it },
                                textStyle = SmithType.bodySmall.copy(color = colors.inkMuted), cursorBrush = SolidColor(colors.ink), singleLine = true,
                                modifier = Modifier.weight(1f).background(colors.bgBase).padding(8.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                decorationBox = { inner ->
                                    Box {
                                        if (clientNewPhone.isEmpty()) Text(
                                            "Phone (optional)",
                                            style = SmithType.bodySmall.copy(color = colors.inkMuted)
                                        )
                                        inner()
                                    }
                                }
                            )
                            BasicTextField(
                                value = clientNewAddress, onValueChange = { clientNewAddress = it },
                                textStyle = SmithType.bodySmall.copy(color = colors.inkMuted), cursorBrush = SolidColor(colors.ink), singleLine = true,
                                modifier = Modifier.weight(1f).background(colors.bgBase).padding(8.dp),
                                decorationBox = { inner ->
                                    Box {
                                        if (clientNewAddress.isEmpty()) Text(
                                            "Address (optional)",
                                            style = SmithType.bodySmall.copy(color = colors.inkMuted)
                                        )
                                        inner()
                                    }
                                }
                            )
                        }
                        Text(
                            text = "[SAVE CLIENT]",
                            style = SmithType.action.copy(
                                color = if (clientNewName.isNotBlank()) colors.statusOnline else colors.inkMuted
                            ),
                            modifier = Modifier.clickable {
                                if (clientNewName.isNotBlank()) {
                                    if (clientNewPhone.isNotBlank() || clientNewAddress.isNotBlank()) {
                                        ClientRepository.saveClientOverride(
                                            clientNewName.trim(), clientNewName.trim(),
                                            clientNewPhone.trim(), clientNewAddress.trim()
                                        )
                                    }
                                    viewModel.setClient(
                                        job.id,
                                        clientNewName.trim(),
                                        clientNewPhone.trim(),
                                        clientNewAddress.trim()
                                    )
                                    showClientPicker = false
                                }
                            }
                        )
                    }
                }

                // ═══════════════════════════════════════════════════
                // JOB DETAILS
                // ═══════════════════════════════════════════════════
                if (job.description.isNotEmpty() && job.description.lowercase() != "n/a") {
                    Text(text = "DESCRIPTION", style = SmithType.captionBold.copy(color = colors.inkMuted))
                    Text(text = job.description, style = SmithType.body.copy(color = colors.ink))
                }

                if (job.expensesNote.isNotEmpty() && job.expensesNote.lowercase() != "n/a") {
                    Text(text = "EXPENSES (NOTE)", style = SmithType.captionBold.copy(color = colors.inkMuted))
                    Text(text = job.expensesNote, style = SmithType.body.copy(color = colors.ink))
                }

                if (job.crew.isNotEmpty()) {
                    Text(text = "CREW (${job.crew.size})", style = SmithType.captionBold.copy(color = colors.inkMuted))
                    job.crew.forEach { member ->
                        Text(text = "• ${member.name} - ${member.occupation}", style = SmithType.body.copy(color = colors.ink))
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
                        Text(text = "CHECKLIST", style = SmithType.captionBold.copy(color = colors.inkMuted))
                        val checkedCount = job.materials.count { it.checked }
                        Text(
                            text = "$checkedCount/${job.materials.size}",
                            style = SmithType.captionBold.copy(
                                color = if (allMaterialsChecked) colors.statusOnline else colors.inkMuted
                            )
                        )
                    }
                    
                    job.materials.forEachIndexed { index, material ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.bgPanel)
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
                                style = SmithType.bodyBold.copy(
                                    color = if (material.checked) colors.statusOnline else colors.inkMuted
                                )
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = material.name, style = SmithType.body.copy(color = colors.ink))
                                if (material.totalCost > 0) {
                                    Text(
                                        text = "$${String.format("%.2f", material.totalCost)} @ ${material.vendor.ifEmpty { "—" }}",
                                        style = SmithType.caption.copy(color = colors.statusOnline)
                                    )
                                } else if (material.notes.isNotEmpty()) {
                                    Text(text = material.notes, style = SmithType.caption.copy(color = colors.inkMuted))
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
                    Text(text = "TASKS", style = SmithType.captionBold.copy(color = colors.inkMuted))
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val doneCount = tasks.count { it.status == TaskStatus.DONE }
                        Text(
                            text = "$doneCount/${tasks.size}",
                            style = SmithType.captionBold.copy(
                                color = if (allTasksComplete && tasks.isNotEmpty()) colors.statusOnline 
                                        else colors.inkMuted
                            )
                        )
                        Text(
                            text = "+ ADD",
                            style = SmithType.action.copy(color = colors.accent),
                            modifier = Modifier.clickable { showAddTask = true }
                        )
                    }
                }

                tasks.forEach { task ->
                    // 3-state row: PENDING → IN_PROGRESS → DONE.
                    //   [ ] PENDING       tap → startTask + onClockIn(this task)
                    //   [▶] IN_PROGRESS   tap → completeTask (also clocks out
                    //                     via the regular clock pill if user
                    //                     wants — kept manual to avoid surprises)
                    //   [X] DONE          tap → toggleTask reverts to PENDING
                    val (label, color) = when (task.status) {
                        TaskStatus.DONE -> "[X]" to colors.statusOnline
                        TaskStatus.IN_PROGRESS -> "[▶]" to colors.accent
                        else -> "[ ]" to colors.inkMuted
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.bgPanel)
                            .clickable {
                                when (task.status) {
                                    TaskStatus.PENDING, TaskStatus.BLOCKED -> {
                                        // Defer task IN_PROGRESS flip + sticky job
                                        // stage flip until the screen-level router
                                        // decides this clock action actually happens.
                                        // Cross-job confirms run performStart only on
                                        // [v] SWITCH; on [x] CANCEL nothing changes.
                                        val performStart = {
                                            viewModel.startTask(task.id)
                                            if (job.status == JobStatus.TODO) {
                                                viewModel.moveJob(job.id, JobStatus.IN_PROGRESS)
                                            }
                                        }
                                        startClock(job.id, job.clientName ?: job.title, task.id, performStart)
                                    }
                                    // Marking DONE does NOT touch the clock or job stage.
                                    // (Per design: ending a task ≠ finishing the job.)
                                    TaskStatus.IN_PROGRESS -> viewModel.completeTask(task.id)
                                    TaskStatus.DONE -> viewModel.toggleTask(task.id)
                                }
                            }
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = label,
                            style = SmithType.bodyBold.copy(color = color)
                        )
                        Text(text = task.title, style = SmithType.body.copy(color = colors.ink), modifier = Modifier.weight(1f))
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
                            textStyle = SmithType.body.copy(color = colors.ink),
                            cursorBrush = SolidColor(colors.ink),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .background(colors.bgPanel)
                                .padding(10.dp),
                            decorationBox = { innerTextField ->
                                Box {
                                    if (newTaskTitle.isEmpty()) {
                                        Text("Task description...", style = SmithType.body.copy(color = colors.inkMuted))
                                    }
                                    innerTextField()
                                }
                            }
                        )
                        Text(
                            text = "ADD",
                            style = SmithType.action.copy(color = colors.accent),
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
                    Text(text = "WORK LOG", style = SmithType.captionBold.copy(color = colors.inkMuted))
                    Text(
                        text = "+ ADD NOTE",
                        style = SmithType.action.copy(color = colors.accent),
                        modifier = Modifier.clickable { showAddNote = true }
                    )
                }

                val expandedNotes = remember { mutableStateMapOf<Long, Boolean>() }
                job.workLog.forEach { note ->
                    val isLong = note.text.length > 140 || note.text.count { it == '\n' } >= 2
                    val expanded = expandedNotes[note.timestamp] == true
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.bgPanel)
                            .clickable(enabled = isLong) {
                                expandedNotes[note.timestamp] = !expanded
                            }
                            .padding(10.dp)
                    ) {
                        Text(
                            text = note.text,
                            style = SmithType.body.copy(color = colors.ink),
                            maxLines = if (!isLong || expanded) Int.MAX_VALUE else 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = formatTimestamp(note.timestamp),
                                style = SmithType.caption.copy(color = colors.inkMuted)
                            )
                            if (isLong) {
                                Text(
                                    text = if (expanded) "[COLLAPSE]" else "[EXPAND]",
                                    style = SmithType.caption.copy(color = colors.accent)
                                )
                            }
                        }
                    }
                }

                // Add note input
                if (showAddNote) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        BasicTextField(
                            value = newNote,
                            onValueChange = { newNote = it },
                            textStyle = SmithType.body.copy(color = colors.ink),
                            cursorBrush = SolidColor(colors.ink),
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.bgPanel)
                                .padding(10.dp)
                                .height(60.dp),
                            decorationBox = { innerTextField ->
                                Box {
                                    if (newNote.isEmpty()) {
                                        Text("Add work notes, extra work orders, etc...", style = SmithType.body.copy(color = colors.inkMuted))
                                    }
                                    innerTextField()
                                }
                            }
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "SAVE NOTE",
                                style = SmithType.action.copy(color = colors.accent),
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
                                style = SmithType.action.copy(color = colors.inkMuted),
                                modifier = Modifier.clickable { 
                                    showAddNote = false
                                    newNote = ""
                                }
                            )
                        }
                    }
                }

                if (job.workLog.isEmpty() && !showAddNote) {
                    Text(text = "No work logged yet", style = SmithType.caption.copy(color = colors.inkMuted))
                }

                ConsoleSeparator()

                // ═══════════════════════════════════════════════════
                // WORKFLOW ACTIONS
                // ═══════════════════════════════════════════════════
                when (job.status) {
                    JobStatus.TODO -> {
                        // Can start working — flips status AND clocks in.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.statusOnline.copy(alpha = 0.15f))
                                .clickable {
                                    // Job stage IS sticky and forward-only, so flipping
                                    // TODO→IN_PROGRESS is fine to do eagerly. The deferred
                                    // performStart here is a no-op for the job-level case
                                    // — the moveJob call above is already idempotent and
                                    // sticky.
                                    val performStart = {
                                        if (job.status == JobStatus.TODO) {
                                            viewModel.moveJob(job.id, JobStatus.IN_PROGRESS)
                                        }
                                    }
                                    startClock(job.id, job.clientName ?: job.title, null, performStart)
                                }
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "START WORKING >>", style = SmithType.header.copy(color = colors.statusOnline))
                        }
                    }
                    
                    JobStatus.IN_PROGRESS -> {
                        // Must complete tasks and materials first
                        if (!canAdvance) {
                            Text(
                                text = "! Complete all tasks and check off materials before submitting for review",
                                style = SmithType.caption.copy(color = colors.attention)
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (canAdvance) colors.accent.copy(alpha = 0.15f)
                                    else colors.inkMuted.copy(alpha = 0.1f)
                                )
                                .clickable(enabled = canAdvance) { showConfirmAdvance = true }
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "SUBMIT FOR REVIEW >>",
                                style = SmithType.header.copy(
                                    color = if (canAdvance) colors.accent else colors.inkMuted
                                )
                            )
                        }
                    }
                    
                    JobStatus.REVIEW -> {
                        Text(
                            text = "Review completed work. Add photos or notes for any issues found.",
                            style = SmithType.caption.copy(color = colors.inkMuted)
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.statusOnline.copy(alpha = 0.15f))
                                .clickable { viewModel.moveJob(job.id, JobStatus.DONE) }
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "MARK COMPLETE >>", style = SmithType.header.copy(color = colors.statusOnline))
                        }
                    }
                    
                    JobStatus.DONE -> {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                text = "✓ Job completed",
                                style = SmithType.bodyBold.copy(color = colors.statusOnline),
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                            
                            // Generate Invoice button — visible to solo users and foreman+
                            if (RoleContext.can(Permission.VIEW_FINANCIALS) || RoleContext.isSolo()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(colors.accent.copy(alpha = 0.15f))
                                        .clickable { viewModel.generateInvoice(job) }
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = "[$] GENERATE INVOICE", style = SmithType.header.copy(color = colors.accent))
                                }
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
                        style = SmithType.action.copy(color = colors.statusError),
                        modifier = Modifier.clickable { showDeleteConfirm = true }
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(text = "Delete this job?", style = SmithType.body.copy(color = colors.ink))
                        Text(
                            text = "YES",
                            style = SmithType.action.copy(color = colors.statusError),
                            modifier = Modifier.clickable { viewModel.deleteJob(job.id); onDismiss() }
                        )
                        Text(
                            text = "NO",
                            style = SmithType.action.copy(color = colors.inkMuted),
                            modifier = Modifier.clickable { showDeleteConfirm = false }
                        )
                    }
                }
        }
    }

    // Confirm advance dialog
    if (showConfirmAdvance) {
        SmithConfirmDialog(
            title = "Submit for review?",
            body = "This will mark the work phase as complete.\n\nMake sure all tasks are done and materials are checked.",
            confirmText = "SUBMIT",
            confirmIsDanger = false,
            onConfirm = {
                viewModel.moveJob(job.id, JobStatus.REVIEW)
                showConfirmAdvance = false
            },
            onDismiss = { showConfirmAdvance = false },
        )
    }
    
    // Material Cost Dialog - captures expense when checking off material
    showMaterialCostDialog?.let { materialIndex ->
        val material = job.materials.getOrNull(materialIndex)
        if (material != null) {
            SmithDialog(
                title = "Material purchased",
                onDismiss = { showMaterialCostDialog = null },
                actions = {
                    SmithButton(
                        text = "SKIP",
                        onClick = {
                            viewModel.toggleMaterial(job.id, materialIndex)
                            showMaterialCostDialog = null
                        },
                        variant = SmithButtonVariant.Ghost,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    SmithButton(
                        text = "SAVE",
                        onClick = {
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
                        },
                    )
                },
            ) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .semantics { testTagsAsResourceId = true },
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(text = material.name, style = SmithType.bodyBold.copy(color = colors.ink))
                        
                        // Quantity and Unit
                        Text(text = "QUANTITY", style = SmithType.captionBold.copy(color = colors.inkMuted))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            BasicTextField(
                                value = materialQty,
                                onValueChange = { materialQty = it.filter { c -> c.isDigit() || c == '.' } },
                                textStyle = SmithType.body.copy(color = colors.ink),
                                cursorBrush = SolidColor(colors.ink),
                                singleLine = true,
                                modifier = Modifier.width(80.dp).background(colors.bgPanel).padding(10.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                            )
                            
                            // Unit selector
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf("ea", "ft", "lot", "hr").forEach { unit ->
                                    Text(
                                        text = if (materialUnit == unit) "[$unit]" else unit,
                                        style = SmithType.action.copy(
                                            color = if (materialUnit == unit) colors.accent else colors.inkMuted
                                        ),
                                        modifier = Modifier.clickable { materialUnit = unit }
                                    )
                                }
                            }
                        }
                        
                        // Total Cost
                        Text(text = "TOTAL COST ($)", style = SmithType.captionBold.copy(color = colors.inkMuted))
                        BasicTextField(
                            value = materialCost,
                            onValueChange = { materialCost = it.filter { c -> c.isDigit() || c == '.' } },
                            textStyle = SmithType.body.copy(color = colors.ink),
                            cursorBrush = SolidColor(colors.ink),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().testTag("solo_e2e_material_cost").background(colors.bgPanel).padding(10.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            decorationBox = { innerTextField ->
                                Row {
                                    Text("$ ", style = SmithType.body.copy(color = colors.inkMuted))
                                    Box {
                                        if (materialCost.isEmpty()) {
                                            Text("0.00", style = SmithType.body.copy(color = colors.inkMuted))
                                        }
                                        innerTextField()
                                    }
                                }
                            }
                        )
                        
                        // Vendor
                        Text(text = "VENDOR (optional)", style = SmithType.captionBold.copy(color = colors.inkMuted))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("HD", "Lowes", "Supply", "Other").forEach { v ->
                                Text(
                                    text = if (materialVendor == v) "[$v]" else v,
                                    style = SmithType.action.copy(
                                        color = if (materialVendor == v) colors.accent else colors.inkMuted
                                    ),
                                    modifier = Modifier.clickable { materialVendor = v }
                                )
                            }
                        }
                        
                        ConsoleSeparator()
                        
                        Text(
                            text = "Skip cost to just mark as used",
                            style = SmithType.caption.copy(color = colors.inkMuted)
                        )
                    }
            }
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
    onCreate: (String, String, Priority, String, Int, List<CrewMember>, List<Material>, Long?, Long?, String, String, String) -> Unit,
    initialTitle: String = "",
    initialDescription: String = "",
    initialCrewSize: Int = 1,
    initialMaterials: List<Material> = emptyList(),
    initialClientName: String = "",
    initialClientPhone: String = "",
    initialClientAddress: String = "",
    savedClients: List<ClientInfo> = emptyList()
) {
    val colors = LocalSmithColors.current
    var currentStep by remember { mutableStateOf(JobDialogStep.EDIT) }

    var title by remember { mutableStateOf(initialTitle) }
    var description by remember { mutableStateOf(initialDescription) }
    var priority by remember { mutableStateOf(Priority.MEDIUM) }
    var expenses by remember { mutableStateOf("") }
    var crewSize by remember { mutableStateOf(initialCrewSize.toString()) }
    var crewMembers by remember { mutableStateOf(listOf<CrewMember>()) }
    var newMemberName by remember { mutableStateOf("") }
    var newMemberOccupation by remember { mutableStateOf("") }
    var materials by remember { mutableStateOf(initialMaterials) }
    var newMaterialName by remember { mutableStateOf("") }

    // Client fields
    var clientName by remember { mutableStateOf(initialClientName) }
    var clientPhone by remember { mutableStateOf(initialClientPhone) }
    var clientAddress by remember { mutableStateOf(initialClientAddress) }
    var clientPickerOpen by remember { mutableStateOf(false) }
    var clientSearch by remember { mutableStateOf("") }

    // Date fields
    var startDateStr by remember { mutableStateOf("") }
    var endDateStr by remember { mutableStateOf("") }

    // Track if user has entered any data (for dismiss protection)
    val hasEnteredData = title.isNotEmpty() || description.isNotEmpty() ||
                         expenses.isNotEmpty() || materials.isNotEmpty() ||
                         crewMembers.isNotEmpty() ||
                         clientName.isNotEmpty() || clientPhone.isNotEmpty() ||
                         clientAddress.isNotEmpty()
    
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
        targetValue = if (showWarningFlash) colors.statusError else colors.bgPanel,
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

    SmithDialog(
        title = if (currentStep == JobDialogStep.EDIT) "New job" else "Preview",
        onDismiss = handleDismiss,
        sizeFraction = 0.95f to 0.55f,
        actions = {
            SmithButton(text = "CANCEL", onClick = onDismiss, variant = SmithButtonVariant.Ghost)
            Spacer(modifier = Modifier.width(8.dp))
            if (currentStep == JobDialogStep.EDIT) {
                SmithButton(
                    text = "PREVIEW >>",
                    onClick = { if (title.isNotBlank()) currentStep = JobDialogStep.PREVIEW },
                    enabled = title.isNotBlank(),
                )
            } else {
                SmithButton(
                    text = "CREATE JOB",
                    onClick = {
                        onCreate(title, description, priority, expenses, crewSize.toIntOrNull() ?: 1,
                                 crewMembers, materials, parseDate(startDateStr), parseDate(endDateStr),
                                 clientName.trim(), clientPhone.trim(), clientAddress.trim())
                    },
                )
            }
        },
    ) {
        if (currentStep == JobDialogStep.PREVIEW) {
            Text(
                text = "[EDIT]",
                style = SmithType.action.copy(color = colors.accent),
                modifier = Modifier.clickable { currentStep = JobDialogStep.EDIT }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
        if (hasEnteredData && currentStep == JobDialogStep.EDIT) {
            Text(
                text = "Use CANCEL to close or PREVIEW to view summary",
                style = SmithType.caption.copy(color = colors.attention)
            )
            Spacer(modifier = Modifier.height(8.dp))
        }
            if (currentStep == JobDialogStep.EDIT) {
                // ═══════════════════════════════════════════════════════════════
                // EDIT MODE
                // ═══════════════════════════════════════════════════════════════
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).imePadding(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Title
                    Text(text = "JOB TITLE *", style = SmithType.captionBold.copy(color = colors.inkMuted))
                    BasicTextField(
                        value = title,
                        onValueChange = { title = it },
                        textStyle = SmithType.body.copy(color = colors.ink),
                        cursorBrush = SolidColor(colors.ink),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().background(inputBorderColor).padding(2.dp)
                            .background(colors.bgPanel).padding(10.dp),
                        decorationBox = { innerTextField ->
                            Box {
                                if (title.isEmpty()) Text("Enter job title...", style = SmithType.body.copy(color = colors.inkMuted))
                                innerTextField()
                            }
                        }
                    )

                    // Priority
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "PRIORITY:", style = SmithType.captionBold.copy(color = colors.inkMuted))
                        Priority.values().forEach { p ->
                            val isSelected = priority == p
                            Text(
                                text = if (isSelected) "[${p.displayName}]" else p.displayName,
                                style = SmithType.action.copy(
                                    color = when { isSelected && p == Priority.URGENT -> colors.statusError
                                        isSelected && p == Priority.HIGH -> colors.attention
                                        isSelected -> colors.accent
                                        else -> colors.inkMuted }
                                ),
                                modifier = Modifier.clickable { priority = p }
                            )
                        }
                    }

                    // Client
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "CLIENT (or N/A)", style = SmithType.captionBold.copy(color = colors.inkMuted))
                        if (savedClients.isNotEmpty()) {
                            Text(
                                text = if (clientPickerOpen) "[CLOSE]" else "[Choose saved profile ▾]",
                                style = SmithType.action.copy(color = colors.accent),
                                modifier = Modifier.clickable { clientPickerOpen = !clientPickerOpen }
                            )
                        }
                    }
                    if (clientPickerOpen && savedClients.isNotEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth().background(colors.bgPanel).padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            BasicTextField(
                                value = clientSearch, onValueChange = { clientSearch = it },
                                textStyle = SmithType.bodySmall.copy(color = colors.inkMuted), cursorBrush = SolidColor(colors.ink),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().background(colors.bgBase).padding(8.dp),
                                decorationBox = { innerTextField ->
                                    Box {
                                        if (clientSearch.isEmpty()) Text(
                                            "Search clients...",
                                            style = SmithType.bodySmall.copy(color = colors.inkMuted)
                                        )
                                        innerTextField()
                                    }
                                }
                            )
                            val filtered = savedClients.filter {
                                clientSearch.isBlank() || it.name.contains(clientSearch, ignoreCase = true)
                            }
                            if (filtered.isEmpty()) {
                                Text(
                                    text = "No matches. Type below to add new.",
                                    style = SmithType.bodySmall.copy(color = colors.inkMuted)
                                )
                            } else {
                                filtered.take(8).forEach { c ->
                                    Text(
                                        text = "• ${c.name}" + if (c.jobCount > 0) "  (${c.jobCount} job${if (c.jobCount == 1) "" else "s"})" else "",
                                        style = SmithType.bodySmall.copy(color = colors.accent),
                                        modifier = Modifier.fillMaxWidth().clickable {
                                            clientName = c.name
                                            clientPhone = c.phone
                                            clientAddress = c.address
                                            clientPickerOpen = false
                                            clientSearch = ""
                                        }.padding(vertical = 4.dp)
                                    )
                                }
                            }
                            Text(
                                text = "[+ NEW CLIENT — clear and type below]",
                                style = SmithType.action.copy(color = colors.attention),
                                modifier = Modifier.clickable {
                                    clientName = ""
                                    clientPhone = ""
                                    clientAddress = ""
                                    clientPickerOpen = false
                                    clientSearch = ""
                                }.padding(top = 4.dp)
                            )
                        }
                    }
                    BasicTextField(
                        value = clientName, onValueChange = { clientName = it },
                        textStyle = SmithType.body.copy(color = colors.ink), cursorBrush = SolidColor(colors.ink), singleLine = true,
                        modifier = Modifier.fillMaxWidth().background(inputBorderColor).padding(2.dp)
                            .background(colors.bgPanel).padding(10.dp),
                        decorationBox = { innerTextField ->
                            Box {
                                if (clientName.isEmpty()) Text(
                                    "Client name (e.g. Aegis Assure Inc)",
                                    style = SmithType.body.copy(color = colors.inkMuted)
                                )
                                innerTextField()
                            }
                        }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            BasicTextField(
                                value = clientPhone, onValueChange = { clientPhone = it },
                                textStyle = SmithType.bodySmall.copy(color = colors.inkMuted), cursorBrush = SolidColor(colors.ink), singleLine = true,
                                modifier = Modifier.fillMaxWidth().background(colors.bgPanel).padding(8.dp),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                decorationBox = { innerTextField ->
                                    Box {
                                        if (clientPhone.isEmpty()) Text(
                                            "Phone (optional)",
                                            style = SmithType.bodySmall.copy(color = colors.inkMuted)
                                        )
                                        innerTextField()
                                    }
                                }
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            BasicTextField(
                                value = clientAddress, onValueChange = { clientAddress = it },
                                textStyle = SmithType.bodySmall.copy(color = colors.inkMuted), cursorBrush = SolidColor(colors.ink), singleLine = true,
                                modifier = Modifier.fillMaxWidth().background(colors.bgPanel).padding(8.dp),
                                decorationBox = { innerTextField ->
                                    Box {
                                        if (clientAddress.isEmpty()) Text(
                                            "Address (optional)",
                                            style = SmithType.bodySmall.copy(color = colors.inkMuted)
                                        )
                                        innerTextField()
                                    }
                                }
                            )
                        }
                    }

                    // Description
                    Text(text = "DESCRIPTION (or N/A)", style = SmithType.captionBold.copy(color = colors.inkMuted))
                    BasicTextField(
                        value = description, onValueChange = { description = it },
                        textStyle = SmithType.bodySmall.copy(color = colors.inkMuted), cursorBrush = SolidColor(colors.ink),
                        modifier = Modifier.fillMaxWidth().background(inputBorderColor).padding(2.dp)
                            .background(colors.bgPanel).padding(10.dp).height(40.dp),
                        decorationBox = { innerTextField ->
                            Box { if (description.isEmpty()) Text("Description or N/A...", style = SmithType.bodySmall.copy(color = colors.inkMuted)); innerTextField() }
                        }
                    )

                    // Expenses
                    Text(text = "EXPENSES (or N/A)", style = SmithType.captionBold.copy(color = colors.inkMuted))
                    BasicTextField(
                        value = expenses, onValueChange = { expenses = it },
                        textStyle = SmithType.bodySmall.copy(color = colors.inkMuted), cursorBrush = SolidColor(colors.ink), singleLine = true,
                        modifier = Modifier.fillMaxWidth().background(inputBorderColor).padding(2.dp)
                            .background(colors.bgPanel).padding(10.dp),
                        decorationBox = { innerTextField ->
                            Box { if (expenses.isEmpty()) Text("$0.00 or N/A...", style = SmithType.bodySmall.copy(color = colors.inkMuted)); innerTextField() }
                        }
                    )

                    // Date fields (optional)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "START (MM/DD/YYYY)", style = SmithType.caption.copy(color = colors.inkMuted))
                            BasicTextField(
                                value = startDateStr, onValueChange = { startDateStr = it.take(10) },
                                textStyle = SmithType.bodySmall.copy(color = colors.inkMuted), cursorBrush = SolidColor(colors.ink), singleLine = true,
                                modifier = Modifier.fillMaxWidth().background(inputBorderColor).padding(1.dp)
                                    .background(colors.bgPanel).padding(8.dp),
                                decorationBox = { innerTextField ->
                                    Box { if (startDateStr.isEmpty()) Text("Optional", style = SmithType.bodySmall.copy(color = colors.inkMuted)); innerTextField() }
                                }
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "END (MM/DD/YYYY)", style = SmithType.caption.copy(color = colors.inkMuted))
                            BasicTextField(
                                value = endDateStr, onValueChange = { endDateStr = it.take(10) },
                                textStyle = SmithType.bodySmall.copy(color = colors.inkMuted), cursorBrush = SolidColor(colors.ink), singleLine = true,
                                modifier = Modifier.fillMaxWidth().background(inputBorderColor).padding(1.dp)
                                    .background(colors.bgPanel).padding(8.dp),
                                decorationBox = { innerTextField ->
                                    Box { if (endDateStr.isEmpty()) Text("Optional", style = SmithType.bodySmall.copy(color = colors.inkMuted)); innerTextField() }
                                }
                            )
                        }
                    }

                    ConsoleSeparator()

                    // Materials/Tools checklist
                    Text(text = "CHECKLIST (tools & materials)", style = SmithType.captionBold.copy(color = colors.inkMuted))
                    materials.forEach { material ->
                        Row(modifier = Modifier.fillMaxWidth().background(colors.bgPanel).padding(6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "• ${material.name}", style = SmithType.body.copy(color = colors.ink))
                            Text(text = "X", style = SmithType.action.copy(color = colors.statusError),
                                modifier = Modifier.clickable { materials = materials.filter { it != material } })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        BasicTextField(
                            value = newMaterialName, onValueChange = { newMaterialName = it },
                            textStyle = SmithType.bodySmall.copy(color = colors.inkMuted), cursorBrush = SolidColor(colors.ink), singleLine = true,
                            modifier = Modifier.weight(1f).background(colors.bgPanel).padding(6.dp),
                            decorationBox = { innerTextField ->
                                Box { if (newMaterialName.isEmpty()) Text("Add item...", style = SmithType.bodySmall.copy(color = colors.inkMuted)); innerTextField() }
                            }
                        )
                        Text(text = "+", style = SmithType.action.copy(color = colors.accent), modifier = Modifier.clickable {
                            if (newMaterialName.isNotBlank()) { materials = materials + Material(name = newMaterialName); newMaterialName = "" }
                        })
                    }

                    // Crew
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "CREW:", style = SmithType.captionBold.copy(color = colors.inkMuted))
                        BasicTextField(
                            value = crewSize, onValueChange = { crewSize = it.filter { c -> c.isDigit() }.ifEmpty { "1" } },
                            textStyle = SmithType.body.copy(color = colors.ink), cursorBrush = SolidColor(colors.ink), singleLine = true,
                            modifier = Modifier.width(40.dp).background(colors.bgPanel).padding(6.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                    if (crewMembers.isNotEmpty()) {
                        crewMembers.forEach { member ->
                            Row(modifier = Modifier.fillMaxWidth().background(colors.bgPanel).padding(4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(text = "${member.name} - ${member.occupation}", style = SmithType.bodySmall.copy(color = colors.inkMuted))
                                Text(text = "X", style = SmithType.action.copy(color = colors.statusError),
                                    modifier = Modifier.clickable { crewMembers = crewMembers.filter { it != member } })
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        BasicTextField(
                            value = newMemberName, onValueChange = { newMemberName = it },
                            textStyle = SmithType.bodySmall.copy(color = colors.inkMuted), cursorBrush = SolidColor(colors.ink), singleLine = true,
                            modifier = Modifier.weight(0.4f).background(colors.bgPanel).padding(4.dp),
                            decorationBox = { innerTextField ->
                                Box { if (newMemberName.isEmpty()) Text("Name", style = SmithType.bodySmall.copy(color = colors.inkMuted)); innerTextField() }
                            }
                        )
                        BasicTextField(
                            value = newMemberOccupation, onValueChange = { newMemberOccupation = it },
                            textStyle = SmithType.bodySmall.copy(color = colors.inkMuted), cursorBrush = SolidColor(colors.ink), singleLine = true,
                            modifier = Modifier.weight(0.4f).background(colors.bgPanel).padding(4.dp),
                            decorationBox = { innerTextField ->
                                Box { if (newMemberOccupation.isEmpty()) Text("Role", style = SmithType.bodySmall.copy(color = colors.inkMuted)); innerTextField() }
                            }
                        )
                        Text(text = "+", style = SmithType.action.copy(color = colors.accent), modifier = Modifier.clickable {
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
                        Text(text = title.ifEmpty { "(No title)" }, style = SmithType.header.copy(color = colors.ink))
                        Text(text = "[${priority.displayName}]", style = SmithType.bodyBold.copy(
                            color = when (priority) { Priority.URGENT -> colors.statusError; Priority.HIGH -> colors.attention; else -> colors.inkMuted }
                        ))
                    }
                    
                    // Created timestamp (will be set on create)
                    Text(text = "Created: ${formatShortDate(System.currentTimeMillis())}", style = SmithType.caption.copy(color = colors.inkMuted))

                    ConsoleSeparator()

                    // Client
                    if (clientName.isNotEmpty()) {
                        Text(text = "CLIENT", style = SmithType.captionBold.copy(color = colors.inkMuted))
                        Text(text = clientName, style = SmithType.body.copy(color = colors.ink))
                        if (clientPhone.isNotEmpty()) Text(text = clientPhone, style = SmithType.bodySmall.copy(color = colors.inkMuted))
                        if (clientAddress.isNotEmpty()) Text(text = clientAddress, style = SmithType.bodySmall.copy(color = colors.inkMuted))
                    }

                    // Description
                    if (description.isNotEmpty()) {
                        Text(text = "DESCRIPTION", style = SmithType.captionBold.copy(color = colors.inkMuted))
                        Text(text = description, style = SmithType.body.copy(color = colors.ink))
                    }
                    
                    // Expenses
                    if (expenses.isNotEmpty()) {
                        Text(text = "EXPENSES", style = SmithType.captionBold.copy(color = colors.inkMuted))
                        Text(text = expenses, style = SmithType.body.copy(color = colors.ink))
                    }
                    
                    // Dates
                    if (startDateStr.isNotEmpty() || endDateStr.isNotEmpty()) {
                        Text(text = "SCHEDULE", style = SmithType.captionBold.copy(color = colors.inkMuted))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            if (startDateStr.isNotEmpty()) Text(text = "Start: $startDateStr", style = SmithType.body.copy(color = colors.ink))
                            if (endDateStr.isNotEmpty()) Text(text = "End: $endDateStr", style = SmithType.body.copy(color = colors.ink))
                        }
                    }
                    
                    // Checklist
                    if (materials.isNotEmpty()) {
                        ConsoleSeparator()
                        Text(text = "CHECKLIST (${materials.size} items)", style = SmithType.captionBold.copy(color = colors.inkMuted))
                        materials.forEach { material ->
                            Text(text = "□ ${material.name}", style = SmithType.body.copy(color = colors.ink))
                        }
                    }
                    
                    // Crew
                    if (crewMembers.isNotEmpty() || (crewSize.toIntOrNull() ?: 1) > 1) {
                        ConsoleSeparator()
                        Text(text = "CREW (${crewSize})", style = SmithType.captionBold.copy(color = colors.inkMuted))
                        if (crewMembers.isNotEmpty()) {
                            crewMembers.forEach { member ->
                                Text(text = "• ${member.name} - ${member.occupation}", style = SmithType.body.copy(color = colors.ink))
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // Confirmation note
                    Text(
                        text = "Review above details. Tap CREATE JOB to add to board.",
                        style = SmithType.caption.copy(color = colors.attention)
                    )
                }
            }
    }
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

private fun buildIntentDescription(intent: com.guildofsmiths.trademesh.ui.plan.IntentVersionData?): String {
    if (intent == null) return ""
    val parts = mutableListOf<String>()
    if (intent.taskDescriptions.isNotEmpty()) {
        parts.add("Tasks:\n" + intent.taskDescriptions.joinToString("\n") { "- $it" })
    }
    if (intent.equipmentNeeded.isNotEmpty()) {
        parts.add("Equipment:\n" + intent.equipmentNeeded.joinToString("\n") { "- $it" })
    }
    if (intent.parties.isNotEmpty()) {
        parts.add("Client: ${intent.parties.joinToString(", ")}")
    }
    return parts.joinToString("\n\n")
}
