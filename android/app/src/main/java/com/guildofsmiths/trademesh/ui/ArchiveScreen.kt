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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.guildofsmiths.trademesh.data.Message
import com.guildofsmiths.trademesh.data.MessageRepository
import com.guildofsmiths.trademesh.ui.jobboard.Job
import com.guildofsmiths.trademesh.ui.jobboard.JobBoardViewModel
import com.guildofsmiths.trademesh.ui.jobboard.JobStatus
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

    var showJobs by remember { mutableStateOf(true) } // true = jobs, false = messages

    // Refresh archived messages when tab changes
    LaunchedEffect(showJobs) {
        if (!showJobs) {
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

        // Tab selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TabButton(
                text = "JOBS (${archivedJobs.size})",
                isSelected = showJobs,
                onClick = { showJobs = true }
            )
            TabButton(
                text = "MESSAGES (${archivedMessages.size})",
                isSelected = !showJobs,
                onClick = { showJobs = false }
            )
        }

        // Archive stats
        if (showJobs) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                ArchiveStatCard(
                    modifier = Modifier.weight(1f),
                    title = "COMPLETED",
                    count = archivedJobs.count { it.status == JobStatus.DONE },
                    icon = "✓"
                )
                ArchiveStatCard(
                    modifier = Modifier.weight(1f),
                    title = "TOTAL VALUE",
                    count = archivedJobs.sumOf { it.materials.sumOf { m -> m.totalCost.toInt() } },
                    icon = "$",
                    isCurrency = true
                )
                ArchiveStatCard(
                    modifier = Modifier.weight(1f),
                    title = "WITH MESSAGES",
                    count = archivedJobs.count { it.relatedMessageIds.isNotEmpty() },
                    icon = "💬"
                )
            }
        } else {
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

        ConsoleSeparator()

        // Archive content
        if (showJobs) {
            // Jobs view
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(archivedJobs.sortedByDescending { it.archivedAt ?: it.updatedAt }) { job ->
                    ArchivedJobCard(
                        job = job,
                        onView = { viewModel.selectJob(job) },
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
        } else {
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
    }
}

@Composable
private fun ArchiveStatCard(
    modifier: Modifier = Modifier,
    title: String,
    count: Int,
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
            text = if (isCurrency) "$${count}" else count.toString(),
            style = ConsoleTheme.header
        )
        Text(text = title, style = ConsoleTheme.caption)
    }
}

@Composable
private fun ArchivedJobCard(
    job: Job,
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

            // Archive info
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Archived ${job.archivedAt?.let { formatShortDate(it) } ?: "Unknown"}",
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.textDim)
                )
                job.archiveReason?.let { reason ->
                    Text(text = "• $reason", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textDim))
                }
            }

            // Job details
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = "Status: ${job.status.displayName}", style = ConsoleTheme.caption)
                if (job.materials.isNotEmpty()) {
                    val totalCost = job.materials.sumOf { it.totalCost }
                    Text(text = "Value: $${totalCost.toInt()}", style = ConsoleTheme.caption)
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