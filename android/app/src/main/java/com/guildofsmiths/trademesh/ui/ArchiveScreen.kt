package com.guildofsmiths.trademesh.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.guildofsmiths.trademesh.data.Message
import com.guildofsmiths.trademesh.data.MessageRepository
import com.guildofsmiths.trademesh.data.TimeStorage
import com.guildofsmiths.trademesh.ui.jobboard.Job
import com.guildofsmiths.trademesh.ui.jobboard.JobBoardViewModel
import com.guildofsmiths.trademesh.ui.jobboard.JobStatus
import com.guildofsmiths.trademesh.ui.jobboard.Priority
import com.guildofsmiths.trademesh.ui.timetracking.TimeEntry
import com.guildofsmiths.trademesh.ui.components.PixelBarRating
import com.guildofsmiths.trademesh.ui.components.PerformanceMetricBar
import com.guildofsmiths.trademesh.ui.components.BenchmarkComparisonBar
import com.guildofsmiths.trademesh.ui.components.generateBarString
import com.guildofsmiths.trademesh.analytics.PerformanceAnalytics
import com.guildofsmiths.trademesh.utils.ExecutionItemSerializer
import com.guildofsmiths.trademesh.ui.documents.DocumentGenerator
import com.guildofsmiths.trademesh.ui.invoice.InvoiceGenerator
import com.guildofsmiths.trademesh.ui.invoice.InvoiceFormatter
import com.guildofsmiths.trademesh.data.UserPreferences
import java.text.SimpleDateFormat
import java.util.*

/**
 * C-13: Archive Screen
 * Separate component for viewing archived jobs, time entries, and history
 */
@Composable
fun ArchiveScreen(
    onNavigateBack: () -> Unit,
    viewModel: JobBoardViewModel = viewModel()
) {
    val archivedJobs by viewModel.archivedJobs.collectAsState()
    var archivedMessages by remember { mutableStateOf(MessageRepository.getArchivedMessages()) }
    val isLoading by viewModel.isLoading.collectAsState()
    
    // Load time entries for computing job time summaries
    val timeEntries = remember { TimeStorage.loadEntries() }

    // Tab state: 0 = jobs, 1 = messages, 2 = analytics
    var selectedTab by remember { mutableStateOf(0) }
    
    // Job Summary Dialog state
    var selectedJobForSummary by remember { mutableStateOf<Job?>(null) }
    
    // Calculate analytics data
    val historicalAnalytics = remember(archivedJobs, timeEntries) {
        PerformanceAnalytics.calculateHistoricalAnalytics(archivedJobs, timeEntries)
    }

    // Refresh archived messages when tab changes
    LaunchedEffect(selectedTab) {
        if (selectedTab == 1) {
            archivedMessages = MessageRepository.getArchivedMessages()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ConsoleTheme.background)
    ) {
        // Header
        ConsoleHeader(
            title = "ARCHIVE",
            onBackClick = onNavigateBack
        )

        ConsoleSeparator()

        // Tab selector (3 tabs)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TabButton(
                text = "JOBS (${archivedJobs.size})",
                isSelected = selectedTab == 0,
                onClick = { selectedTab = 0 }
            )
            TabButton(
                text = "MESSAGES (${archivedMessages.size})",
                isSelected = selectedTab == 1,
                onClick = { selectedTab = 1 }
            )
            TabButton(
                text = "ANALYTICS",
                isSelected = selectedTab == 2,
                onClick = { selectedTab = 2 }
            )
        }

        // Archive stats based on tab
        when (selectedTab) {
            0 -> {
                // Jobs stats
                val totalArchivedMinutes = archivedJobs.sumOf { job ->
                    computeJobTime(job, timeEntries)
                }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ArchiveStatCard(
                        modifier = Modifier.weight(1f),
                        title = "ARCHIVED",
                        count = archivedJobs.size,
                        icon = "▤"
                    )
                    ArchiveStatCard(
                        modifier = Modifier.weight(1f),
                        title = "TOTAL TIME",
                        displayValue = formatTimeMinutes(totalArchivedMinutes),
                        icon = "◷"
                    )
                    ArchiveStatCard(
                        modifier = Modifier.weight(1f),
                        title = "TOTAL VALUE",
                        count = archivedJobs.sumOf { it.materials.sumOf { m -> m.totalCost.toInt() } },
                        icon = "$",
                        isCurrency = true
                    )
                }
            }
            1 -> {
                // Messages stats
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ArchiveStatCard(
                        modifier = Modifier.weight(1f),
                        title = "TOTAL",
                        count = archivedMessages.size,
                        icon = "💬"
                    )
                    ArchiveStatCard(
                        modifier = Modifier.weight(1f),
                        title = "AI GENERATED",
                        count = archivedMessages.count { it.aiGenerated },
                        icon = "[AI]"
                    )
                    ArchiveStatCard(
                        modifier = Modifier.weight(1f),
                        title = "LINKED TO JOBS",
                        count = archivedMessages.count { it.relatedJobId != null },
                        icon = "▫"
                    )
                }
            }
            2 -> {
                // Analytics summary stats with pixel bars
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    ArchiveStatCard(
                        modifier = Modifier.weight(1f),
                        title = "COMPLETED",
                        count = historicalAnalytics.totalJobsCompleted,
                        icon = "✓"
                    )
                    ArchiveStatCard(
                        modifier = Modifier.weight(1f),
                        title = "AVG RATING",
                        displayValue = String.format("%.1f/10", historicalAnalytics.avgSatisfactionRating),
                        icon = "█"
                    )
                    ArchiveStatCard(
                        modifier = Modifier.weight(1f),
                        title = "HOURS",
                        displayValue = String.format("%.0f", historicalAnalytics.totalHoursWorked),
                        icon = "◷"
                    )
                }
            }
        }

        ConsoleSeparator()

        // Archive content based on tab
        when (selectedTab) {
            0 -> {
                // Jobs view
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(archivedJobs.sortedByDescending { it.archivedAt ?: it.updatedAt }) { job ->
                        ArchivedJobCard(
                            job = job,
                            timeEntries = timeEntries,
                            onView = { selectedJobForSummary = job },
                            onRestore = { viewModel.restoreJob(job.id) },
                            onDelete = { viewModel.deleteArchivedJob(job.id) }
                        )
                    }

                    if (archivedJobs.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(text = "No archived jobs", style = ConsoleTheme.body)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Completed jobs will appear here",
                                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                                )
                            }
                        }
                    }
                }
            }
            1 -> {
                // Messages view
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(archivedMessages.sortedByDescending { it.archivedAt ?: it.timestamp }) { message ->
                        ArchivedMessageCard(
                            message = message,
                            onView = { /* Could navigate to conversation */ },
                            onRestore = { MessageRepository.unarchiveMessage(message.id) },
                            onDelete = { MessageRepository.deleteArchivedMessage(message.id) }
                        )
                    }

                    if (archivedMessages.isEmpty()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(text = "No archived messages", style = ConsoleTheme.body)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Archived messages will appear here",
                                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                                )
                            }
                        }
                    }
                }
            }
            2 -> {
                // Analytics view
                ArchiveAnalyticsDashboard(
                    analytics = historicalAnalytics,
                    archivedJobs = archivedJobs,
                    timeEntries = timeEntries,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
    
    // ════════════════════════════════════════════════════════════════
    // JOB SUMMARY DIALOG ("Baseball Card")
    // ════════════════════════════════════════════════════════════════
    
    selectedJobForSummary?.let { job ->
        JobSummaryDialog(
            job = job,
            timeEntries = timeEntries,
            onDismiss = { selectedJobForSummary = null },
            onRestore = { 
                viewModel.restoreJob(job.id)
                selectedJobForSummary = null
            }
        )
    }
}

@Composable
private fun ArchiveStatCard(
    modifier: Modifier = Modifier,
    title: String,
    count: Int = 0,
    displayValue: String? = null,
    icon: String,
    isCurrency: Boolean = false
) {
    Column(
        modifier = modifier
            .background(ConsoleTheme.surface)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = icon, style = ConsoleTheme.header.copy(fontSize = ConsoleTheme.header.fontSize * 1.2f))
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = displayValue ?: (if (isCurrency) "$${count}" else count.toString()),
            style = ConsoleTheme.header.copy(
                fontFamily = if (displayValue != null) androidx.compose.ui.text.font.FontFamily.Monospace else ConsoleTheme.header.fontFamily
            )
        )
        Text(text = title, style = ConsoleTheme.caption)
    }
}

/**
 * Compute total minutes logged for a job from time entries
 */
private fun computeJobTime(job: Job, timeEntries: List<TimeEntry>): Int {
    val jobTimeEntries = timeEntries.filter { entry ->
        entry.jobId == job.id || entry.jobTitle == job.title
    }
    return jobTimeEntries.sumOf { entry ->
        entry.durationMinutes ?: 0
    }
}

/**
 * Format minutes as HH:MM
 */
private fun formatTimeMinutes(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return String.format("%02d:%02d", hours, mins)
}

@Composable
private fun ArchivedJobCard(
    job: Job,
    timeEntries: List<TimeEntry>,
    onView: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }
    
    // Compute time for this job
    val totalMinutes = remember(job, timeEntries) { computeJobTime(job, timeEntries) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ConsoleTheme.surface.copy(alpha = 0.8f))
            .clickable { onView() }
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // Title and status
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text = "▫", style = ConsoleTheme.bodyBold)
                Text(text = job.title, style = ConsoleTheme.body, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (job.priority.name == "HIGH" || job.priority.name == "URGENT") {
                    Text(
                        text = if (job.priority.name == "URGENT") "!!" else "!",
                        style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.warning)
                    )
                }
            }

            // Archive info with time
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Archived ${job.archivedAt?.let { formatShortDate(it) } ?: "Unknown"}",
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.textDim)
                )
                Text(
                    text = "• Time: ${formatTimeMinutes(totalMinutes)}",
                    style = ConsoleTheme.caption.copy(
                        color = if (totalMinutes > 0) ConsoleTheme.accent else ConsoleTheme.textDim,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                )
            }

            // Job details
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                job.archiveReason?.let { reason ->
                    Text(text = "Reason: $reason", style = ConsoleTheme.caption)
                }
                if (job.materials.isNotEmpty()) {
                    val totalCost = job.materials.sumOf { it.totalCost }
                    Text(text = "Materials: $${totalCost.toInt()}", style = ConsoleTheme.caption)
                }
            }
        }

        // Actions
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "RESTORE",
                style = ConsoleTheme.action.copy(color = ConsoleTheme.accent),
                modifier = Modifier.clickable { showRestoreConfirm = true }
            )
            Text(
                text = "X",
                style = ConsoleTheme.action.copy(color = ConsoleTheme.error),
                modifier = Modifier.clickable { showDeleteConfirm = true }
            )
        }
    }

    // Restore confirmation
    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            containerColor = ConsoleTheme.background,
            title = { Text("RESTORE JOB?", style = ConsoleTheme.header) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = job.title, style = ConsoleTheme.bodyBold)
                    Text(
                        text = "This will restore the job to active status. It will reappear in the Job Board.",
                        style = ConsoleTheme.caption
                    )
                }
            },
            confirmButton = {
                Text(
                    text = "RESTORE",
                    style = ConsoleTheme.action,
                    modifier = Modifier.clickable {
                        onRestore()
                        showRestoreConfirm = false
                    }
                )
            },
            dismissButton = {
                Text(
                    text = "CANCEL",
                    style = ConsoleTheme.action.copy(color = ConsoleTheme.textMuted),
                    modifier = Modifier.clickable { showRestoreConfirm = false }
                )
            }
        )
    }

    // Delete confirmation
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = ConsoleTheme.background,
            title = { Text("DELETE PERMANENTLY?", style = ConsoleTheme.header) },
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
// TAB BUTTON
// ════════════════════════════════════════════════════════════════════

@Composable
private fun TabButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = text,
        style = ConsoleTheme.bodyBold.copy(
            color = if (isSelected) ConsoleTheme.accent else ConsoleTheme.textMuted
        ),
        modifier = Modifier
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

// ════════════════════════════════════════════════════════════════════
// ARCHIVED MESSAGE CARD
// ════════════════════════════════════════════════════════════════════

@Composable
private fun ArchivedMessageCard(
    message: Message,
    onView: () -> Unit,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRestoreConfirm by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ConsoleTheme.surface.copy(alpha = 0.8f))
            .clickable { onView() }
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // Message content preview
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(text = "💬", style = ConsoleTheme.bodyBold)
                Text(
                    text = message.content.take(60) + if (message.content.length > 60) "..." else "",
                    style = ConsoleTheme.body,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (message.aiGenerated) {
                    Text(text = message.getAISourceLabel() ?: "[AI]", style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.accent))
                }
            }

            // Archive info
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "From: ${message.senderName}",
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                )
                Text(
                    text = "Archived ${message.archivedAt?.let { formatShortDate(it) } ?: "Unknown"}",
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                )
            }

            // Channel and job info
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "#${message.channelId}", style = ConsoleTheme.caption)
                message.relatedJobId?.let { jobId ->
                    Text(text = "Job: ${jobId.take(8)}...", style = ConsoleTheme.caption.copy(color = ConsoleTheme.accent))
                }
            }
        }

        // Actions
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "RESTORE",
                style = ConsoleTheme.action.copy(color = ConsoleTheme.accent),
                modifier = Modifier.clickable { showRestoreConfirm = true }
            )
            Text(
                text = "X",
                style = ConsoleTheme.action.copy(color = ConsoleTheme.error),
                modifier = Modifier.clickable { showDeleteConfirm = true }
            )
        }
    }

    // Restore confirmation
    if (showRestoreConfirm) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirm = false },
            containerColor = ConsoleTheme.background,
            title = { Text("RESTORE MESSAGE?", style = ConsoleTheme.header) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "This message will reappear in the conversation.", style = ConsoleTheme.caption)
                }
            },
            confirmButton = {
                Text(
                    text = "RESTORE",
                    style = ConsoleTheme.action,
                    modifier = Modifier.clickable {
                        onRestore()
                        showRestoreConfirm = false
                    }
                )
            },
            dismissButton = {
                Text(
                    text = "CANCEL",
                    style = ConsoleTheme.action.copy(color = ConsoleTheme.textMuted),
                    modifier = Modifier.clickable { showRestoreConfirm = false }
                )
            }
        )
    }

    // Delete confirmation
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = ConsoleTheme.background,
            title = { Text("DELETE MESSAGE?", style = ConsoleTheme.header) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "This will permanently delete the message.", style = ConsoleTheme.caption.copy(color = ConsoleTheme.error))
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

private fun formatShortDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MM/dd/yy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

// ════════════════════════════════════════════════════════════════════════════
// JOB SUMMARY DIALOG - "Baseball Card" view of archived job
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun JobSummaryDialog(
    job: Job,
    timeEntries: List<TimeEntry>,
    onDismiss: () -> Unit,
    onRestore: () -> Unit
) {
    val totalMinutes = computeJobTime(job, timeEntries)
    val totalHours = totalMinutes / 60.0
    val totalMaterialsCost = job.materials.sumOf { it.totalCost }
    val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ConsoleTheme.background,
        modifier = Modifier.fillMaxWidth(0.95f),
        title = {
            Column {
                // Status badge row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                when (job.status) {
                                    JobStatus.DONE -> ConsoleTheme.success
                                    JobStatus.ARCHIVED -> ConsoleTheme.textMuted
                                    else -> ConsoleTheme.accent
                                },
                                androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = job.status.displayName.uppercase(),
                            style = ConsoleTheme.caption.copy(
                                color = androidx.compose.ui.graphics.Color.White,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                        )
                    }
                    
                    if (job.priority == Priority.HIGH || job.priority == Priority.URGENT) {
                        Text(
                            text = if (job.priority == Priority.URGENT) "⚡ URGENT" else "! HIGH PRIORITY",
                            style = ConsoleTheme.caption.copy(color = ConsoleTheme.warning)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Job Title
                Text(
                    text = job.title,
                    style = ConsoleTheme.header.copy(fontSize = ConsoleTheme.header.fontSize * 1.1f)
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ════════════════════════════════════════════════════════════
                // SUMMARY STATS ROW
                // ════════════════════════════════════════════════════════════
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Time logged
                    SummaryStatBox(
                        icon = "⏱",
                        value = String.format("%.1fh", totalHours),
                        label = "TIME"
                    )
                    
                    // Materials cost
                    SummaryStatBox(
                        icon = "$",
                        value = totalMaterialsCost.toInt().toString(),
                        label = "MATERIALS"
                    )
                    
                    // Crew size
                    SummaryStatBox(
                        icon = "👤",
                        value = job.crewSize.toString(),
                        label = "CREW"
                    )
                }
                
                // ════════════════════════════════════════════════════════════
                // CLIENT FEEDBACK (if collected)
                // ════════════════════════════════════════════════════════════
                
                job.clientSatisfactionBars?.let { rating ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ConsoleTheme.surface, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "CLIENT EXPERIENCE",
                            style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.textMuted)
                        )
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(text = "Satisfaction:", style = ConsoleTheme.body)
                            PixelBarRating(
                                value = rating,
                                maxValue = 10,
                                showValue = true
                            )
                        }
                        
                        job.clientFeedbackText?.let { feedback ->
                            Text(
                                text = "\"$feedback\"",
                                style = ConsoleTheme.caption.copy(
                                    color = ConsoleTheme.textMuted,
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                ),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                
                // ════════════════════════════════════════════════════════════
                // INTERNAL PERFORMANCE METRICS (if scored)
                // ════════════════════════════════════════════════════════════
                
                val hasPerformanceData = job.profitabilityScore != null || 
                                         job.operationalScore != null || 
                                         job.timeManagementScore != null || 
                                         job.qualityScore != null
                
                if (hasPerformanceData) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ConsoleTheme.surface, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "PERFORMANCE METRICS",
                            style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.textMuted)
                        )
                        
                        job.profitabilityScore?.let { score ->
                            PerformanceMetricBar(
                                label = "Profitability",
                                value = score,
                                description = job.actualProfitMargin?.let { "${it.toInt()}% margin" }
                            )
                        }
                        
                        job.operationalScore?.let { score ->
                            PerformanceMetricBar(
                                label = "Operations",
                                value = score,
                                description = "efficiency"
                            )
                        }
                        
                        job.timeManagementScore?.let { score ->
                            PerformanceMetricBar(
                                label = "Time Mgmt",
                                value = score,
                                description = job.actualCompletionTime?.let { "${it}h actual" }
                            )
                        }
                        
                        job.qualityScore?.let { score ->
                            PerformanceMetricBar(
                                label = "Quality",
                                value = score,
                                description = "workmanship"
                            )
                        }
                    }
                }
                
                // ════════════════════════════════════════════════════════════
                // BENCHMARK COMPARISON (if available)
                // ════════════════════════════════════════════════════════════
                
                val hasBenchmarkData = job.marketLaborRate != null || job.marketCompletionTime != null
                
                if (hasBenchmarkData) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(ConsoleTheme.surface, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "VS INDUSTRY BENCHMARKS",
                            style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.textMuted)
                        )
                        
                        job.marketLaborRate?.let { marketRate ->
                            val yourRate = job.actualLaborRate ?: 85.0
                            BenchmarkComparisonBar(
                                label = "Labor Rate",
                                yourValue = yourRate,
                                marketValue = marketRate,
                                unit = "/hr",
                                higherIsBetter = true
                            )
                        }
                        
                        job.marketCompletionTime?.let { marketTime ->
                            val yourTime = job.actualCompletionTime ?: totalHours
                            BenchmarkComparisonBar(
                                label = "Completion",
                                yourValue = yourTime,
                                marketValue = marketTime,
                                unit = "h",
                                higherIsBetter = false
                            )
                        }
                        
                        job.marketProfitMargin?.let { marketMargin ->
                            val yourMargin = job.actualProfitMargin ?: 22.0
                            BenchmarkComparisonBar(
                                label = "Profit Margin",
                                yourValue = yourMargin,
                                marketValue = marketMargin,
                                unit = "%",
                                higherIsBetter = true
                            )
                        }
                    }
                }
                
                Divider(color = ConsoleTheme.textDim.copy(alpha = 0.3f))
                
                // ════════════════════════════════════════════════════════════
                // JOB DETAILS
                // ════════════════════════════════════════════════════════════
                
                // Client & Location
                if (job.clientName != null || job.location != null) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        job.clientName?.let {
                            Row {
                                Text(text = "CLIENT: ", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
                                Text(text = it, style = ConsoleTheme.bodyBold)
                            }
                        }
                        job.location?.let {
                            Row {
                                Text(text = "LOCATION: ", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
                                Text(text = it, style = ConsoleTheme.body)
                            }
                        }
                    }
                }
                
                // Description
                if (job.description.isNotBlank()) {
                    Column {
                        Text(text = "DESCRIPTION", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
                        Text(
                            text = job.description,
                            style = ConsoleTheme.body,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                
                // ════════════════════════════════════════════════════════════
                // TIMELINE
                // ════════════════════════════════════════════════════════════
                
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = "TIMELINE", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column {
                            Text(text = "Created", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textDim))
                            Text(text = dateFormat.format(Date(job.createdAt)), style = ConsoleTheme.body)
                        }
                        
                        job.completedAt?.let {
                            Column {
                                Text(text = "Completed", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textDim))
                                Text(text = dateFormat.format(Date(it)), style = ConsoleTheme.body.copy(color = ConsoleTheme.success))
                            }
                        }
                        
                        job.archivedAt?.let {
                            Column {
                                Text(text = "Archived", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textDim))
                                Text(text = dateFormat.format(Date(it)), style = ConsoleTheme.body)
                            }
                        }
                    }
                }
                
                // ════════════════════════════════════════════════════════════
                // MATERIALS SUMMARY
                // ════════════════════════════════════════════════════════════
                
                if (job.materials.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "MATERIALS (${job.materials.size})",
                            style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                        )
                        
                        job.materials.take(5).forEach { material ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "${if (material.checked) "✓ " else "○ "}${material.name}",
                                    style = ConsoleTheme.body.copy(
                                        color = if (material.checked) ConsoleTheme.success else ConsoleTheme.text
                                    ),
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (material.totalCost > 0) {
                                    Text(
                                        text = "$${material.totalCost.toInt()}",
                                        style = ConsoleTheme.caption.copy(color = ConsoleTheme.accent)
                                    )
                                }
                            }
                        }
                        
                        if (job.materials.size > 5) {
                            Text(
                                text = "... +${job.materials.size - 5} more items",
                                style = ConsoleTheme.caption.copy(color = ConsoleTheme.textDim)
                            )
                        }
                    }
                }
                
                // ════════════════════════════════════════════════════════════
                // ARCHIVE REASON
                // ════════════════════════════════════════════════════════════
                
                job.archiveReason?.let { reason ->
                    Column {
                        Text(text = "ARCHIVE REASON", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
                        Text(text = reason, style = ConsoleTheme.body)
                    }
                }
                
                // ════════════════════════════════════════════════════════════
                // WORK LOG SUMMARY
                // ════════════════════════════════════════════════════════════
                
                if (job.workLog.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "WORK LOG (${job.workLog.size} entries)",
                            style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                        )
                        
                        // Show last entry
                        job.workLog.lastOrNull()?.let { entry ->
                            Text(
                                text = "Latest: \"${entry.text}\"",
                                style = ConsoleTheme.caption.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
                
                // ════════════════════════════════════════════════════════════
                // DOCUMENT REGENERATION
                // ════════════════════════════════════════════════════════════
                
                val hasExecutionItems = !job.executionItemsJson.isNullOrBlank()
                val context = androidx.compose.ui.platform.LocalContext.current
                
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ConsoleTheme.surface, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "REGENERATE DOCUMENTS",
                        style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.textMuted)
                    )
                    
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Proposal button
                        Text(
                            text = "[PROPOSAL]",
                            style = ConsoleTheme.captionBold.copy(
                                color = if (hasExecutionItems) ConsoleTheme.accent else ConsoleTheme.textDim
                            ),
                            modifier = Modifier
                                .background(
                                    ConsoleTheme.surface,
                                    androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                                )
                                .then(
                                    if (hasExecutionItems) {
                                        Modifier.clickable {
                                            val items = ExecutionItemSerializer.deserialize(job.executionItemsJson)
                                            val proposal = DocumentGenerator.generateProposal(
                                                executionItems = items,
                                                title = job.title,
                                                providerName = UserPreferences.getUserName()
                                            )
                                            val text = DocumentGenerator.formatProposalAsText(proposal)
                                            shareDocument(context, "Proposal - ${job.title}", text)
                                        }
                                    } else Modifier
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                        
                        // Report button
                        Text(
                            text = "[REPORT]",
                            style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.accent),
                            modifier = Modifier
                                .background(
                                    ConsoleTheme.surface,
                                    androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                                )
                                .clickable {
                                    val report = DocumentGenerator.generateReport(
                                        job = job,
                                        timeEntries = timeEntries,
                                        providerName = UserPreferences.getUserName()
                                    )
                                    val text = DocumentGenerator.formatReportAsText(report)
                                    shareDocument(context, "Report - ${job.title}", text)
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                        
                        // Invoice button
                        Text(
                            text = "[INVOICE]",
                            style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.accent),
                            modifier = Modifier
                                .background(
                                    ConsoleTheme.surface,
                                    androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                                )
                                .clickable {
                                    val invoice = InvoiceGenerator.generateFromJob(
                                        job = job,
                                        timeEntries = timeEntries,
                                        providerName = UserPreferences.getUserName()
                                    )
                                    val text = InvoiceFormatter.formatAsText(invoice)
                                    shareDocument(context, "Invoice - ${job.title}", text)
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                    
                    if (!hasExecutionItems) {
                        Text(
                            text = "Proposal requires stored execution items",
                            style = ConsoleTheme.caption.copy(
                                color = ConsoleTheme.textDim,
                                fontSize = 10.sp
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Restore button
                Text(
                    text = "RESTORE",
                    style = ConsoleTheme.action.copy(color = ConsoleTheme.accent),
                    modifier = Modifier.clickable { onRestore() }
                )
            }
        },
        dismissButton = {
            Text(
                text = "CLOSE",
                style = ConsoleTheme.action.copy(color = ConsoleTheme.textMuted),
                modifier = Modifier.clickable { onDismiss() }
            )
        }
    )
}

/**
 * Share a document via Android share sheet.
 */
private fun shareDocument(context: android.content.Context, subject: String, text: String) {
    val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_SUBJECT, subject)
        putExtra(android.content.Intent.EXTRA_TEXT, text)
    }
    context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Document"))
}

@Composable
private fun SummaryStatBox(
    icon: String,
    value: String,
    label: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .background(ConsoleTheme.surface, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .padding(12.dp)
    ) {
        Text(text = icon, style = ConsoleTheme.header)
        Text(text = value, style = ConsoleTheme.bodyBold)
        Text(text = label, style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
    }
}

// ════════════════════════════════════════════════════════════════════════════
// ARCHIVE ANALYTICS DASHBOARD - Interactive performance analytics
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun ArchiveAnalyticsDashboard(
    analytics: PerformanceAnalytics.HistoricalAnalytics,
    archivedJobs: List<Job>,
    timeEntries: List<TimeEntry>,
    modifier: Modifier = Modifier
) {
    // Selected metric for chart display
    var selectedMetric by remember { mutableStateOf("satisfaction") }
    
    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(16.dp)
    ) {
        // ════════════════════════════════════════════════════════════════
        // PERFORMANCE SCORECARD SECTION
        // ════════════════════════════════════════════════════════════════
        
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleTheme.surface, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "PERFORMANCE SCORECARD",
                    style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.textMuted)
                )
                
                // Client Satisfaction
                PerformanceMetricBar(
                    label = "Client Rating",
                    value = analytics.avgSatisfactionRating.toInt().coerceIn(1, 10),
                    description = String.format("%.1f avg", analytics.avgSatisfactionRating)
                )
                
                // Profitability
                PerformanceMetricBar(
                    label = "Profitability",
                    value = analytics.avgProfitabilityScore,
                    description = "margin"
                )
                
                // Operational
                PerformanceMetricBar(
                    label = "Operations",
                    value = analytics.avgOperationalScore,
                    description = "efficiency"
                )
                
                // Time Management
                PerformanceMetricBar(
                    label = "Time Mgmt",
                    value = analytics.avgTimeManagementScore,
                    description = "vs estimate"
                )
                
                // Quality
                PerformanceMetricBar(
                    label = "Quality",
                    value = analytics.avgQualityScore,
                    description = "issues"
                )
            }
        }
        
        // ════════════════════════════════════════════════════════════════
        // INTERACTIVE METRIC SELECTOR
        // ════════════════════════════════════════════════════════════════
        
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleTheme.surface, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "TREND ANALYSIS",
                    style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.textMuted)
                )
                
                // Metric selector buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MetricSelectorButton(
                        text = "Rating",
                        isSelected = selectedMetric == "satisfaction",
                        onClick = { selectedMetric = "satisfaction" }
                    )
                    MetricSelectorButton(
                        text = "Profit",
                        isSelected = selectedMetric == "profit",
                        onClick = { selectedMetric = "profit" }
                    )
                    MetricSelectorButton(
                        text = "Time",
                        isSelected = selectedMetric == "time",
                        onClick = { selectedMetric = "time" }
                    )
                    MetricSelectorButton(
                        text = "Quality",
                        isSelected = selectedMetric == "quality",
                        onClick = { selectedMetric = "quality" }
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Visual bar chart representation
                BarChartDisplay(
                    archivedJobs = archivedJobs,
                    selectedMetric = selectedMetric,
                    timeEntries = timeEntries
                )
            }
        }
        
        // ════════════════════════════════════════════════════════════════
        // REVENUE & TIME SUMMARY
        // ════════════════════════════════════════════════════════════════
        
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Revenue card
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(ConsoleTheme.surface, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "$", style = ConsoleTheme.header)
                    Text(
                        text = String.format("$%.0f", analytics.totalRevenue),
                        style = ConsoleTheme.bodyBold.copy(color = ConsoleTheme.success)
                    )
                    Text(
                        text = "Total Revenue",
                        style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                    )
                }
                
                // Hours card
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .background(ConsoleTheme.surface, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = "◷", style = ConsoleTheme.header)
                    Text(
                        text = String.format("%.0fh", analytics.totalHoursWorked),
                        style = ConsoleTheme.bodyBold.copy(color = ConsoleTheme.accent)
                    )
                    Text(
                        text = "Total Hours",
                        style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                    )
                }
            }
        }
        
        // ════════════════════════════════════════════════════════════════
        // TOP PERFORMING JOBS
        // ════════════════════════════════════════════════════════════════
        
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleTheme.surface, androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "TOP RATED JOBS",
                    style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.textMuted)
                )
                
                val topJobs = archivedJobs
                    .filter { it.clientSatisfactionBars != null }
                    .sortedByDescending { it.clientSatisfactionBars }
                    .take(3)
                
                if (topJobs.isEmpty()) {
                    Text(
                        text = "No rated jobs yet",
                        style = ConsoleTheme.caption.copy(color = ConsoleTheme.textDim)
                    )
                } else {
                    topJobs.forEach { job ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = job.title,
                                style = ConsoleTheme.body,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            PixelBarRating(
                                value = job.clientSatisfactionBars ?: 0,
                                maxValue = 10,
                                showValue = true
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricSelectorButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = "[$text]",
        style = ConsoleTheme.captionBold.copy(
            color = if (isSelected) ConsoleTheme.accent else ConsoleTheme.textMuted
        ),
        modifier = Modifier
            .background(
                if (isSelected) ConsoleTheme.accent.copy(alpha = 0.1f) 
                else androidx.compose.ui.graphics.Color.Transparent,
                androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
private fun BarChartDisplay(
    archivedJobs: List<Job>,
    selectedMetric: String,
    timeEntries: List<TimeEntry>
) {
    // Get last 6 jobs for chart
    val recentJobs = archivedJobs
        .sortedByDescending { it.archivedAt ?: it.updatedAt }
        .take(6)
        .reversed()
    
    if (recentJobs.isEmpty()) {
        Text(
            text = "No data to display",
            style = ConsoleTheme.caption.copy(color = ConsoleTheme.textDim),
            modifier = Modifier.padding(vertical = 16.dp)
        )
        return
    }
    
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        recentJobs.forEachIndexed { index, job ->
            val value = when (selectedMetric) {
                "satisfaction" -> job.clientSatisfactionBars ?: 5
                "profit" -> job.profitabilityScore ?: 5
                "time" -> job.timeManagementScore ?: 5
                "quality" -> job.qualityScore ?: 5
                else -> 5
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Job number
                Text(
                    text = "${index + 1}.",
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.textDim),
                    modifier = Modifier.width(20.dp)
                )
                
                // Bar representation
                Text(
                    text = generateBarString(value, 10),
                    style = ConsoleTheme.caption.copy(
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        color = when {
                            value <= 3 -> androidx.compose.ui.graphics.Color(0xFFE74C3C)
                            value <= 5 -> androidx.compose.ui.graphics.Color(0xFFF39C12)
                            value <= 7 -> androidx.compose.ui.graphics.Color(0xFF3498DB)
                            else -> androidx.compose.ui.graphics.Color(0xFF27AE60)
                        },
                        fontSize = 10.sp
                    )
                )
                
                // Value
                Text(
                    text = "$value",
                    style = ConsoleTheme.captionBold,
                    modifier = Modifier.width(24.dp)
                )
                
                // Job name (truncated)
                Text(
                    text = job.title.take(15) + if (job.title.length > 15) "..." else "",
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted),
                    maxLines = 1
                )
            }
        }
    }
}