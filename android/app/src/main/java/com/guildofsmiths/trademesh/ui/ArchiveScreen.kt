package com.guildofsmiths.trademesh.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.guildofsmiths.trademesh.data.Message
import com.guildofsmiths.trademesh.data.MessageRepository
import com.guildofsmiths.trademesh.data.TimeEntryRepository
import com.guildofsmiths.trademesh.ui.jobboard.Job
import com.guildofsmiths.trademesh.ui.jobboard.JobBoardViewModel
import com.guildofsmiths.trademesh.ui.jobboard.JobStage
import com.guildofsmiths.trademesh.ui.jobboard.JobStatus
import com.guildofsmiths.trademesh.ui.theme2.LocalSmithColors
import com.guildofsmiths.trademesh.ui.theme2.SmithErrorState
import com.guildofsmiths.trademesh.ui.theme2.SmithLoadingState
import com.guildofsmiths.trademesh.ui.theme2.SmithType
import com.guildofsmiths.trademesh.ui.timetracking.TimeEntry
import java.text.SimpleDateFormat
import java.util.*

/**
 * Audit / Archive Screen — comprehensive records view
 * Shows ALL records: active jobs, archived jobs, time entries, messages
 */
@Composable
fun ArchiveScreen(
    onNavigateBack: () -> Unit,
    onJobClick: ((String) -> Unit)? = null,
    viewModel: JobBoardViewModel = viewModel()
) {
    val colors = LocalSmithColors.current
    val activeJobs by viewModel.jobs.collectAsState()
    val archivedJobs by viewModel.archivedJobs.collectAsState()
    val jobsLoading by viewModel.isLoading.collectAsState()
    val jobsError by viewModel.error.collectAsState()
    val timeEntries by TimeEntryRepository.entries.collectAsState()
    var archivedMessages by remember { mutableStateOf(MessageRepository.getArchivedMessages()) }

    // Tabs: ALL JOBS | TIME | MESSAGES
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("ALL JOBS", "TIME", "MESSAGES")

    // Refresh messages when switching to that tab
    LaunchedEffect(selectedTab) {
        if (selectedTab == 2) {
            archivedMessages = MessageRepository.getArchivedMessages()
        }
    }

    // Combined stats
    val allJobs = activeJobs + archivedJobs
    val totalJobs = allJobs.size
    val closedJobs = allJobs.count { it.stage == JobStage.CLOSED || it.isArchived }
    val totalRevenue = allJobs.sumOf { job ->
        job.materials.sumOf { it.totalCost } + (job.hourlyRate * 8)
    }
    val totalHours = timeEntries.sumOf { entry ->
        if (entry.clockOutTime != null) (entry.clockOutTime - entry.clockInTime) / 3_600_000.0 else 0.0
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.bgBase)
    ) {
        ConsoleHeader(title = "ARCHIVE", onBackClick = onNavigateBack)
        ConsoleSeparator()

        // ── SUMMARY STATS BAR ─────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            AuditStatCard(Modifier.weight(1f), "TOTAL", totalJobs.toString(), colors.ink)
            AuditStatCard(Modifier.weight(1f), "CLOSED", closedJobs.toString(), colors.statusOnline)
            AuditStatCard(Modifier.weight(1f), "HOURS", String.format("%.0f", totalHours), colors.accent)
            AuditStatCard(Modifier.weight(1f), "VALUE", "$${String.format("%,.0f", totalRevenue)}", colors.attention)
        }

        // ── TABS ──────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            tabs.forEachIndexed { index, tab ->
                val count = when (index) {
                    0 -> allJobs.size
                    1 -> timeEntries.size
                    2 -> archivedMessages.size
                    else -> 0
                }
                val isSelected = selectedTab == index
                Text(
                    text = "$tab ($count)",
                    style = SmithType.captionBold.copy(
                        color = if (isSelected) colors.accent else colors.inkMuted
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(Tokens2.RadiusControl))
                        .then(
                            if (isSelected) Modifier.background(colors.accent.copy(alpha = 0.08f), RoundedCornerShape(Tokens2.RadiusControl))
                            else Modifier
                        )
                        .clickable { selectedTab = index }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }

        ConsoleSeparator()

        // ── TAB CONTENT ───────────────────────────────────
        when (selectedTab) {
            0 -> when {
                jobsError != null -> SmithErrorState(
                    message = jobsError ?: "Couldn't load jobs.",
                    onRetry = { viewModel.loadJobs() }
                )
                jobsLoading && allJobs.isEmpty() -> SmithLoadingState(label = "LOADING JOBS")
                else -> AllJobsTab(activeJobs, archivedJobs, viewModel, onJobClick)
            }
            1 -> TimeEntriesTab(timeEntries)
            2 -> MessagesTab(archivedMessages)
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// ALL JOBS TAB — active + archived, sortable
// ════════════════════════════════════════════════════════════════════

@Composable
private fun AllJobsTab(
    activeJobs: List<Job>,
    archivedJobs: List<Job>,
    viewModel: JobBoardViewModel,
    onJobClick: ((String) -> Unit)?
) {
    val colors = LocalSmithColors.current
    var showArchived by remember { mutableStateOf(true) }
    var showActive by remember { mutableStateOf(true) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // ── ACTIVE JOBS ───────────────────────────────
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showActive = !showActive }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "ACTIVE (${activeJobs.size})",
                    style = SmithType.captionBold.copy(color = colors.statusOnline)
                )
                Text(
                    if (showActive) "[—]" else "[+]",
                    style = SmithType.caption.copy(color = colors.inkMuted)
                )
            }
        }

        if (showActive) {
            items(activeJobs.sortedByDescending { it.updatedAt }, key = { "active-${it.id}" }) { job ->
                AuditJobRow(job = job, isArchived = false, onJobClick = onJobClick)
            }
            if (activeJobs.isEmpty()) {
                item {
                    Text("No active jobs.", style = SmithType.caption.copy(color = colors.inkMuted), modifier = Modifier.padding(8.dp))
                }
            }
        }

        item { Spacer(modifier = Modifier.height(8.dp)) }

        // ── ARCHIVED JOBS ─────────────────────────────
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showArchived = !showArchived }
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "ARCHIVED (${archivedJobs.size})",
                    style = SmithType.captionBold.copy(color = colors.inkMuted)
                )
                Text(
                    if (showArchived) "[—]" else "[+]",
                    style = SmithType.caption.copy(color = colors.inkMuted)
                )
            }
        }

        if (showArchived) {
            items(archivedJobs.sortedByDescending { it.archivedAt ?: it.updatedAt }, key = { "arch-${it.id}" }) { job ->
                AuditJobRow(job = job, isArchived = true, onJobClick = onJobClick, onRestore = { viewModel.restoreJob(job.id) })
            }
            if (archivedJobs.isEmpty()) {
                item {
                    Text("No archived jobs.", style = SmithType.caption.copy(color = colors.inkMuted), modifier = Modifier.padding(8.dp))
                }
            }
        }
    }
}

@Composable
private fun AuditJobRow(
    job: Job,
    isArchived: Boolean,
    onJobClick: ((String) -> Unit)?,
    onRestore: (() -> Unit)? = null
) {
    val colors = LocalSmithColors.current
    val materialsCost = job.materials.sumOf { it.totalCost }
    val laborCost = job.hourlyRate * 8
    val total = materialsCost + laborCost
    val crewNames = job.crew.joinToString(", ") { it.name }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bgPanel, RoundedCornerShape(Tokens2.RadiusCard))
            .border(
                0.5.dp,
                if (isArchived) colors.ink.copy(alpha = 0.04f) else colors.ink.copy(alpha = 0.06f),
                RoundedCornerShape(Tokens2.RadiusCard)
            )
            .clip(RoundedCornerShape(Tokens2.RadiusCard))
            .then(
                if (onJobClick != null) Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = rememberRipple(bounded = true),
                    onClick = { onJobClick(job.id) }
                ) else Modifier
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            // Title + stage
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${job.stage.icon} ${job.clientName ?: job.title}",
                    style = SmithType.bodySmall.copy(
                        color = if (isArchived) colors.inkMuted else colors.ink
                    ),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }

            // Details line
            Text(
                buildString {
                    append(job.stage.displayName)
                    if (job.clientAddress.isNotBlank()) append(" · ${job.clientAddress.take(25)}")
                },
                style = SmithType.caption.copy(color = colors.inkMuted)
            )

            // Crew line
            if (crewNames.isNotBlank()) {
                Text("Crew: $crewNames", style = SmithType.caption.copy(color = colors.statusOnline))
            }

            // Materials + cost
            if (job.materials.isNotEmpty()) {
                Text(
                    "${job.materials.size} materials · ${job.materials.count { it.checked }} checked",
                    style = SmithType.caption.copy(color = colors.inkMuted)
                )
            }

            // Archive date
            if (isArchived && job.archivedAt != null) {
                Text(
                    "Archived ${formatShortDate(job.archivedAt)}${job.archiveReason?.let { " · $it" } ?: ""}",
                    style = SmithType.caption.copy(color = colors.inkMuted)
                )
            }
        }

        // Right side: value + restore
        Column(horizontalAlignment = Alignment.End) {
            if (total > 0) {
                Text("$${String.format("%,.0f", total)}", style = SmithType.bodySmall.copy(color = colors.accent))
            }
            if (onRestore != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "[Restore]",
                    style = SmithType.action.copy(color = colors.accent),
                    modifier = Modifier.clickable { onRestore() }
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// TIME ENTRIES TAB
// ════════════════════════════════════════════════════════════════════

@Composable
private fun TimeEntriesTab(timeEntries: List<TimeEntry>) {
    val colors = LocalSmithColors.current
    if (timeEntries.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No time entries recorded.", style = SmithType.body.copy(color = colors.ink))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Clock in on a job to start tracking time.", style = SmithType.caption.copy(color = colors.inkMuted))
            }
        }
        return
    }

    // Group by date
    val dateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)
    val timeFormat = SimpleDateFormat("h:mm a", Locale.US)
    val grouped = timeEntries
        .sortedByDescending { it.clockInTime }
        .groupBy { dateFormat.format(Date(it.clockInTime)) }

    val totalHours = timeEntries.sumOf { entry ->
        if (entry.clockOutTime != null) (entry.clockOutTime - entry.clockInTime) / 3_600_000.0 else 0.0
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Total hours header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("TOTAL HOURS", style = SmithType.captionBold.copy(color = colors.inkMuted))
                Text(String.format("%.1f hrs", totalHours), style = SmithType.bodySmall.copy(color = colors.accent))
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        grouped.forEach { (date, entries) ->
            item {
                Text(date, style = SmithType.captionBold.copy(color = colors.inkMuted), modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
            }
            items(entries, key = { it.id }) { entry ->
                val duration = if (entry.clockOutTime != null) {
                    val mins = (entry.clockOutTime - entry.clockInTime) / 60_000
                    "${mins / 60}h ${mins % 60}m"
                } else "active"
                val startStr = timeFormat.format(Date(entry.clockInTime))
                val endStr = entry.clockOutTime?.let { timeFormat.format(Date(it)) } ?: "—"
                val title = entry.jobTitle ?: "General"

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.bgPanel, RoundedCornerShape(Tokens2.RadiusCard))
                        .border(0.5.dp, colors.ink.copy(alpha = 0.06f), RoundedCornerShape(Tokens2.RadiusCard))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(title, style = SmithType.bodySmall.copy(color = colors.ink), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("$startStr — $endStr", style = SmithType.caption.copy(color = colors.inkMuted))
                        if (entry.notes.isNotEmpty()) {
                            Text(entry.notes.joinToString(" · ") { it.text }, style = SmithType.caption.copy(color = colors.inkMuted), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Text(
                        duration,
                        style = SmithType.bodySmall.copy(
                            color = if (entry.clockOutTime == null) colors.statusOnline else colors.accent
                        )
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// MESSAGES TAB
// ════════════════════════════════════════════════════════════════════

@Composable
private fun MessagesTab(archivedMessages: List<Message>) {
    val colors = LocalSmithColors.current
    if (archivedMessages.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No archived messages.", style = SmithType.body.copy(color = colors.ink))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Archived messages will appear here.", style = SmithType.caption.copy(color = colors.inkMuted))
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(archivedMessages.sortedByDescending { it.archivedAt ?: it.timestamp }, key = { it.id }) { message ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.bgPanel, RoundedCornerShape(Tokens2.RadiusCard))
                    .border(0.5.dp, colors.ink.copy(alpha = 0.06f), RoundedCornerShape(Tokens2.RadiusCard))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            message.content.take(60) + if (message.content.length > 60) "..." else "",
                            style = SmithType.bodySmall.copy(color = colors.ink),
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        if (message.aiGenerated) {
                            Text(message.getAISourceLabel() ?: "[AI]", style = SmithType.captionBold.copy(color = colors.accent))
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(message.senderName, style = SmithType.caption.copy(color = colors.inkMuted))
                        Text("· #${message.channelId}", style = SmithType.caption.copy(color = colors.inkMuted))
                        message.archivedAt?.let {
                            Text("· ${formatShortDate(it)}", style = SmithType.caption.copy(color = colors.inkMuted))
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "[Restore]",
                        style = SmithType.action.copy(color = colors.accent),
                        modifier = Modifier.clickable { MessageRepository.unarchiveMessage(message.id) }
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// SHARED COMPONENTS
// ════════════════════════════════════════════════════════════════════

@Composable
private fun AuditStatCard(
    modifier: Modifier,
    label: String,
    value: String,
    color: Color
) {
    val colors = LocalSmithColors.current
    Column(
        modifier = modifier
            .background(colors.bgPanel, RoundedCornerShape(Tokens2.RadiusCard))
            .border(0.5.dp, colors.ink.copy(alpha = 0.06f), RoundedCornerShape(Tokens2.RadiusCard))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, style = SmithType.bodySmall.copy(color = color))
        Text(label, style = SmithType.caption.copy(color = colors.inkMuted))
    }
}

private fun formatShortDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MM/dd/yy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
