package com.guildofsmiths.trademesh.ui

import android.util.Log
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guildofsmiths.trademesh.data.JobRepository
import com.guildofsmiths.trademesh.data.JobStorage
import com.guildofsmiths.trademesh.data.TimeStorage
import com.guildofsmiths.trademesh.data.UserPreferences
import com.guildofsmiths.trademesh.engine.BoundaryEngine
import com.guildofsmiths.trademesh.ui.jobboard.Job
import com.guildofsmiths.trademesh.ui.jobboard.JobStatus
import com.guildofsmiths.trademesh.ui.timetracking.TimeEntry
import com.guildofsmiths.trademesh.ui.invoice.Invoice
import com.guildofsmiths.trademesh.ui.invoice.InvoiceGenerator
import com.guildofsmiths.trademesh.ui.invoice.InvoicePreviewDialog
import com.guildofsmiths.trademesh.ui.components.MiniBarIndicator
import com.guildofsmiths.trademesh.ui.components.generateBarString
import java.util.UUID

/**
 * SMITHNET DASHBOARD — Tabbed Interface
 *
 * Clean, messenger-style design with:
 * - Header: username (clickable for profile) + status dot
 * - Tab bar: Plan | Jobs | Clock (no Chat - that's separate)
 * - Content area based on selected tab
 * - Footer: Current tab indicator + Settings
 */

enum class DashboardTab {
    JOBS,
    TIME,
    ARCHIVE,
    CHAT
}

@Composable
fun SmithNetDashboard(
    onProfileClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onJobBoardClick: () -> Unit,
    onTimeTrackingClick: () -> Unit,
    onMessagesClick: () -> Unit,
    onArchiveClick: () -> Unit = {},
    onPlanClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // No tab selected by default - dashboard shows Plan content
    var selectedTab by remember { mutableStateOf<DashboardTab?>(null) }
    
    val userName = remember { UserPreferences.getUserName() }
    val isMeshConnected by BoundaryEngine.isMeshConnected.collectAsState()
    val isGatewayConnected by BoundaryEngine.isGatewayConnected.collectAsState()
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF8F8FA))
    ) {
        // ════════════════════════════════════════════════════════════════
        // HEADER — Messenger Style
        // ════════════════════════════════════════════════════════════════
        
        SmithNetHeader(
            userName = userName,
            isOnline = isGatewayConnected || isMeshConnected,
            onProfileClick = onProfileClick
        )
        
        // ════════════════════════════════════════════════════════════════
        // TAB BAR — Plan | Jobs | Clock
        // ════════════════════════════════════════════════════════════════
        
        TabBar(
            selectedTab = selectedTab,
            onTabSelected = { tab ->
                selectedTab = tab
                when (tab) {
                    DashboardTab.JOBS -> onJobBoardClick()
                    DashboardTab.TIME -> onTimeTrackingClick()
                    DashboardTab.ARCHIVE -> onArchiveClick()
                    DashboardTab.CHAT -> onMessagesClick()
                }
            }
        )
        
        // ════════════════════════════════════════════════════════════════
        // CONTENT — Dashboard summary when on Plan tab
        // ════════════════════════════════════════════════════════════════
        
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            // Show quick plan entry that launches full PlanScreen
            DashboardPlanSummary(
                onOpenPlan = onPlanClick,
                onJobTap = { _ -> onJobBoardClick() },
                onArchiveClick = onArchiveClick
            )
        }
        
        // ════════════════════════════════════════════════════════════════
        // FOOTER — Current tab indicator + Settings
        // ════════════════════════════════════════════════════════════════
        
        SmithNetFooter(
            currentTab = selectedTab,
            onSettingsClick = onSettingsClick
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
// HEADER COMPONENT — Messenger Style
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun SmithNetHeader(
    userName: String,
    isOnline: Boolean,
    onProfileClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        // App name + version + status
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = ConsoleTheme.APP_NAME,
                style = ConsoleTheme.brand
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = ConsoleTheme.APP_VERSION,
                style = ConsoleTheme.version,
                modifier = Modifier.padding(bottom = 4.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            // Status dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (isOnline) Color(0xFFFF9500) else Color(0xFFAEAEB2))
            )
        }
        
        Spacer(modifier = Modifier.height(4.dp))
        
        // Username (clickable for profile)
        Text(
            text = userName,
            style = ConsoleTheme.body.copy(
                fontSize = 16.sp,
                color = Color(0xFF3A3A3C)
            ),
            modifier = Modifier.clickable(onClick = onProfileClick)
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
// TAB BAR COMPONENT — Matching Settings Icons
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun TabBar(
    selectedTab: DashboardTab?,
    onTabSelected: (DashboardTab) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        // Jobs tab - matches Settings [≡] JOB BOARD
        TabButton(
            icon = "[≡]",
            label = "JOBS",
            isSelected = selectedTab == DashboardTab.JOBS,
            onClick = { onTabSelected(DashboardTab.JOBS) }
        )
        
        // Time tab - clock icon ◷
        TabButton(
            icon = "[◷]",
            label = "TIME",
            isSelected = selectedTab == DashboardTab.TIME,
            onClick = { onTabSelected(DashboardTab.TIME) }
        )
        
        // Archive tab
        TabButton(
            icon = "[▤]",
            label = "ARCHIVE",
            isSelected = selectedTab == DashboardTab.ARCHIVE,
            onClick = { onTabSelected(DashboardTab.ARCHIVE) }
        )
        
        // Chat tab
        TabButton(
            icon = "[◇]",
            label = "CHAT",
            isSelected = selectedTab == DashboardTab.CHAT,
            onClick = { onTabSelected(DashboardTab.CHAT) }
        )
    }
    
    // Separator
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Color(0xFFE5E5EA))
    )
}

@Composable
private fun TabButton(
    icon: String,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = icon,
            style = ConsoleTheme.header.copy(
                fontSize = 18.sp,
                color = if (isSelected) ConsoleTheme.accent else ConsoleTheme.textMuted
            )
        )
        
        Spacer(modifier = Modifier.height(2.dp))
        
        Text(
            text = label,
            style = ConsoleTheme.caption.copy(
                fontSize = 10.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) Color(0xFF1C1C1E) else Color(0xFF8E8E93)
            )
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
// JOB LIVE STATUS — Clear indicators for job state
// ════════════════════════════════════════════════════════════════════════════

/**
 * Job Live Status - Clear signal of whether job is active or ready to close
 * 
 * STATUS RULES (deterministic, computed from job + time data):
 * 
 * LIVE:
 *   - Timer currently running for this job, OR
 *   - Job status is IN_PROGRESS, TODO, or BACKLOG (work pending)
 * 
 * READY_TO_CLOSE:
 *   - Job status is DONE or REVIEW, AND
 *   - No timer currently running, AND
 *   - Has logged time entries (completed work)
 * 
 * ARCHIVED:
 *   - Job has isArchived = true
 */
enum class JobLiveStatus(
    val label: String, 
    val icon: String, 
    val description: String,
    val canInvoice: Boolean
) {
    PENDING(
        label = "PENDING",
        icon = "○",
        description = "Scheduled, not started",
        canInvoice = false
    ),
    LIVE(
        label = "LIVE",
        icon = "●",
        description = "Work in progress",
        canInvoice = false
    ),
    READY_TO_CLOSE(
        label = "READY",
        icon = "✓",
        description = "Ready to close & invoice",
        canInvoice = true
    ),
    ARCHIVED(
        label = "ARCHIVED",
        icon = "▫",
        description = "Archived",
        canInvoice = false  // Already invoiced/closed
    )
}

/**
 * Invoice readiness status
 */
data class InvoiceReadiness(
    val isEnabled: Boolean,
    val reason: String,
    val prerequisites: List<String> = emptyList()
)

/**
 * Job with computed live status and invoice readiness
 */
data class DashboardJob(
    val job: Job,
    val liveStatus: JobLiveStatus,
    val invoiceReadiness: InvoiceReadiness,
    val totalMinutes: Int,
    val isTimerRunning: Boolean
)

/**
 * Compute live status from job and time entries.
 * 
 * RULES:
 * 1. ARCHIVED: job.isArchived == true
 * 2. PENDING: Job is BACKLOG/TODO with no time logged and no timer running
 * 3. LIVE: Timer running OR job IN_PROGRESS OR has time logged
 * 4. READY_TO_CLOSE: Job DONE/REVIEW AND no timer running AND has time logged
 */
private fun computeLiveStatus(
    job: Job,
    timeEntries: List<TimeEntry>,
    activeEntryId: String?
): JobLiveStatus {
    // Rule 1: Archived jobs
    if (job.isArchived || job.status == JobStatus.ARCHIVED) {
        return JobLiveStatus.ARCHIVED
    }
    
    // Find time entries for this job
    val jobTimeEntries = timeEntries.filter { entry ->
        entry.jobId == job.id || entry.jobTitle == job.title
    }
    
    // Check if timer is running for this job
    val isTimerRunning = jobTimeEntries.any { 
        it.id == activeEntryId && it.clockOutTime == null 
    }
    
    // Rule 2: Timer running = LIVE
    if (isTimerRunning) {
        return JobLiveStatus.LIVE
    }
    
    // Rule 3: Job status determines state
    val isJobComplete = job.status == JobStatus.DONE || job.status == JobStatus.REVIEW
    val hasCompletedTimeEntries = jobTimeEntries.any { it.clockOutTime != null }
    val hasAnyTimeEntries = jobTimeEntries.isNotEmpty()
    val isPendingStatus = job.status == JobStatus.BACKLOG || job.status == JobStatus.TODO
    
    return when {
        // READY_TO_CLOSE: Complete and has logged time
        isJobComplete && hasCompletedTimeEntries -> JobLiveStatus.READY_TO_CLOSE
        
        // PENDING: Not started yet (backlog/todo with no time)
        isPendingStatus && !hasAnyTimeEntries -> JobLiveStatus.PENDING
        
        // LIVE: Everything else (in progress, or has time logged)
        else -> JobLiveStatus.LIVE
    }
}

/**
 * Compute invoice readiness with clear reason.
 * 
 * RULES (ALL must be true for invoice to be enabled):
 * 1. Job status is DONE (not just REVIEW - inspection must be complete)
 * 2. No timer currently running for this job
 * 3. Has at least one completed time entry
 */
private fun computeInvoiceReadiness(
    job: Job,
    timeEntries: List<TimeEntry>,
    activeEntryId: String?
): InvoiceReadiness {
    val jobTimeEntries = timeEntries.filter { entry ->
        entry.jobId == job.id || entry.jobTitle == job.title
    }
    
    val isTimerRunning = jobTimeEntries.any { 
        it.id == activeEntryId && it.clockOutTime == null 
    }
    val hasCompletedTimeEntries = jobTimeEntries.any { it.clockOutTime != null }
    val totalMinutes = jobTimeEntries.filter { it.clockOutTime != null }
        .sumOf { it.durationMinutes ?: 0 }
    
    // Invoice ONLY available when DONE (inspection complete), not during REVIEW
    val isFullyComplete = job.status == JobStatus.DONE
    val isInReview = job.status == JobStatus.REVIEW
    
    return when {
        // READY: Fully complete + no timer + has time
        isFullyComplete && !isTimerRunning && hasCompletedTimeEntries -> {
            val hours = totalMinutes / 60
            val mins = totalMinutes % 60
            InvoiceReadiness(
                isEnabled = true,
                reason = "Ready (${hours}h ${mins}m logged)"
            )
        }
        
        // BLOCKED: In review (inspection pending)
        isInReview && hasCompletedTimeEntries -> InvoiceReadiness(
            isEnabled = false,
            reason = "Inspection pending",
            prerequisites = listOf("Complete inspection (mark as Done)")
        )
        
        // BLOCKED: Timer running
        isTimerRunning -> InvoiceReadiness(
            isEnabled = false,
            reason = "Timer running",
            prerequisites = listOf("Stop the running timer")
        )
        
        // BLOCKED: Job not done
        !isFullyComplete -> InvoiceReadiness(
            isEnabled = false,
            reason = "Job not complete",
            prerequisites = listOf("Complete job (mark as Done)")
        )
        
        // BLOCKED: No time logged
        !hasCompletedTimeEntries -> InvoiceReadiness(
            isEnabled = false,
            reason = "No time logged",
            prerequisites = listOf("Log time for this job")
        )
        
        else -> InvoiceReadiness(
            isEnabled = false,
            reason = "Not ready",
            prerequisites = listOf("Complete all prerequisites")
        )
    }
}

// Legacy: Keep for backward compatibility
enum class JobWorkState(val label: String, val icon: String) {
    PENDING("Pending", "○"),
    ACTIVE("Active", "▶"),
    COMPLETED("Completed", "✓")
}

private fun computeWorkState(
    job: Job,
    timeEntries: List<TimeEntry>,
    activeEntryId: String?
): JobWorkState {
    if (job.status == JobStatus.DONE || job.status == JobStatus.REVIEW) {
        return JobWorkState.COMPLETED
    }
    
    val jobTimeEntries = timeEntries.filter { entry ->
        entry.jobId == job.id || entry.jobTitle == job.title
    }
    
    val hasTimeEntries = jobTimeEntries.isNotEmpty()
    val isTimerRunning = jobTimeEntries.any { it.id == activeEntryId && it.clockOutTime == null }
    
    if (hasTimeEntries || isTimerRunning) {
        return JobWorkState.ACTIVE
    }
    
    return JobWorkState.PENDING
}

/**
 * Compute total minutes logged for a job
 */
private fun computeTotalMinutes(
    job: Job,
    timeEntries: List<TimeEntry>
): Int {
    val jobTimeEntries = timeEntries.filter { entry ->
        entry.jobId == job.id || entry.jobTitle == job.title
    }
    
    return jobTimeEntries.sumOf { entry ->
        entry.durationMinutes ?: if (entry.clockOutTime == null) {
            // Timer still running - compute current duration
            ((System.currentTimeMillis() - entry.clockInTime) / 60000).toInt()
        } else {
            0
        }
    }
}

/**
 * Format minutes as HH:MM
 */
private fun formatTime(minutes: Int): String {
    val hours = minutes / 60
    val mins = minutes % 60
    return String.format("%02d:%02d", hours, mins)
}

// ════════════════════════════════════════════════════════════════════════════
// DASHBOARD CONTENT — Job-state-aware work overview
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun DashboardPlanSummary(
    onOpenPlan: () -> Unit,
    onJobTap: (Job) -> Unit = {},
    onArchiveClick: () -> Unit = {}
) {
    // Trigger recomposition when jobs change
    var refreshTrigger by remember { mutableStateOf(0) }
    
    // Auto-refresh on every composition (screen entry)
    LaunchedEffect(Unit) {
        refreshTrigger++
    }
    
    // Load data from persistent storage - refresh on trigger change
    val jobs = remember(refreshTrigger) { 
        Log.d("Dashboard", "Loading jobs (trigger: $refreshTrigger)")
        JobStorage.loadJobs() 
    }
    val archivedJobs = remember(refreshTrigger) { JobStorage.loadArchivedJobs() }
    val timeEntries = remember(refreshTrigger) { TimeStorage.loadEntries() }
    val activeEntryId = remember(refreshTrigger) { TimeStorage.getActiveEntryId() }
    
    // Log load results
    LaunchedEffect(jobs, archivedJobs) {
        Log.d("Dashboard", "Loaded ${jobs.size} active jobs, ${archivedJobs.size} archived")
    }
    
    // Save state indicator
    var lastSaveStatus by remember { mutableStateOf<String?>(null) }
    
    // Archive action callback
    val onArchiveJob: (Job) -> Unit = { job ->
        // Archive the job
        val now = System.currentTimeMillis()
        val archivedJob = job.copy(
            isArchived = true,
            archivedAt = now,
            archiveReason = "Completed",
            status = JobStatus.ARCHIVED
        )
        
        // Update storage atomically
        val updatedJobs = jobs.filter { it.id != job.id }
        val updatedArchived = archivedJobs + archivedJob
        
        val jobsSaved = JobStorage.saveJobs(updatedJobs)
        val archivedSaved = JobStorage.saveArchivedJobs(updatedArchived)
        
        if (jobsSaved && archivedSaved) {
            Log.d("Dashboard", "Archive successful: ${job.title}")
            lastSaveStatus = "Saved ✓"
        } else {
            Log.e("Dashboard", "Archive failed: jobs=$jobsSaved, archived=$archivedSaved")
            lastSaveStatus = "Save failed"
        }
        
        // Sync to JobRepository
        JobRepository.updateJobs(updatedJobs.map { j ->
            JobRepository.SimpleJob(id = j.id, title = j.title, status = j.status.name)
        })
        
        // Trigger refresh
        refreshTrigger++
    }
    
    // Compute dashboard jobs with live status and invoice readiness (exclude archived)
    val dashboardJobs = remember(jobs, timeEntries, activeEntryId, refreshTrigger) {
        jobs.filter { !it.isArchived }.map { job ->
            val liveStatus = computeLiveStatus(job, timeEntries, activeEntryId)
            val invoiceReadiness = computeInvoiceReadiness(job, timeEntries, activeEntryId)
            val totalMinutes = computeTotalMinutes(job, timeEntries)
            val isTimerRunning = timeEntries.any { 
                (it.jobId == job.id || it.jobTitle == job.title) && 
                it.id == activeEntryId && 
                it.clockOutTime == null 
            }
            DashboardJob(job, liveStatus, invoiceReadiness, totalMinutes, isTimerRunning)
        }
    }
    
    // Calculate totals (no grouping - flat list)
    val totalTimeMinutes = dashboardJobs.sumOf { it.totalMinutes }
    val activeJobsCount = dashboardJobs.size
    val archivedCount = archivedJobs.size
    
    // Check for active timer
    val activeTimerJob = dashboardJobs.find { it.isTimerRunning }
    
    // Calculate today's time
    val todayStart = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
    
    val todayMinutes = timeEntries
        .filter { it.clockInTime >= todayStart }
        .sumOf { entry ->
            val endTime = entry.clockOutTime ?: System.currentTimeMillis()
            ((endTime - entry.clockInTime) / 60000).toInt()
        }
    
    // Calculate this week's time
    val weekStart = java.util.Calendar.getInstance().apply {
        set(java.util.Calendar.DAY_OF_WEEK, firstDayOfWeek)
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
    
    val weekMinutes = timeEntries
        .filter { it.clockInTime >= weekStart }
        .sumOf { entry ->
            val endTime = entry.clockOutTime ?: System.currentTimeMillis()
            ((endTime - entry.clockInTime) / 60000).toInt()
        }
    
    // Count job statuses
    val pendingJobs = dashboardJobs.count { job ->
        job.job.status == JobStatus.BACKLOG || job.job.status == JobStatus.TODO
    }
    val inProgressJobs = dashboardJobs.count { job ->
        job.job.status == JobStatus.IN_PROGRESS || job.job.status == JobStatus.REVIEW
    }
    
    // Count overdue and due soon jobs
    val now = System.currentTimeMillis()
    val oneWeekFromNow = now + (7 * 24 * 60 * 60 * 1000L)
    val overdueJobs = dashboardJobs.count { job ->
        job.job.dueDate != null && job.job.dueDate < now && job.job.status != JobStatus.DONE
    }
    val dueSoonJobs = dashboardJobs.count { job ->
        job.job.dueDate != null && job.job.dueDate in now..oneWeekFromNow
    }
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        // ════════════════════════════════════════════════════════════════
        // SUMMARY STATS
        // ════════════════════════════════════════════════════════════════
        
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Work Overview",
                    style = ConsoleTheme.header.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                
                // Save status indicator
                if (lastSaveStatus != null) {
                    Text(
                        text = lastSaveStatus!!,
                        style = ConsoleTheme.caption.copy(
                            fontSize = 10.sp,
                            color = if (lastSaveStatus == "Saved ✓") ConsoleTheme.success else ConsoleTheme.error
                        )
                    )
                }
            }
        }
        
        // Active timer banner (if running)
        if (activeTimerJob != null) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ConsoleTheme.warning.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(ConsoleTheme.warning)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Timer running",
                            style = ConsoleTheme.caption.copy(
                                fontSize = 11.sp,
                                color = ConsoleTheme.warning,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Text(
                            text = activeTimerJob.job.title,
                            style = ConsoleTheme.body.copy(
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = formatTime(activeTimerJob.totalMinutes),
                        style = ConsoleTheme.body.copy(
                            color = ConsoleTheme.warning,
                            fontWeight = FontWeight.Bold,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    )
                }
            }
        }
        
        // Main stats row - Job counts
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(8.dp))
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                StatItem(
                    value = "$pendingJobs",
                    label = "Pending",
                    color = ConsoleTheme.textMuted
                )
                StatItem(
                    value = "$inProgressJobs",
                    label = "In Progress",
                    color = ConsoleTheme.accent
                )
                StatItem(
                    value = formatTime(todayMinutes),
                    label = "Today",
                    color = ConsoleTheme.warning
                )
                StatItem(
                    value = formatTime(weekMinutes),
                    label = "This Week",
                    color = ConsoleTheme.accent
                )
            }
        }
        
        // Secondary stats row (due dates + archived) - using pixel bar indicators
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(8.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Overdue indicator with bar
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    // Pixel bar representation (max 5 for compact display)
                    val overdueBarValue = overdueJobs.coerceIn(0, 5)
                    Text(
                        text = generateBarString(overdueBarValue, 5),
                        style = ConsoleTheme.caption.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = if (overdueJobs > 0) ConsoleTheme.error else ConsoleTheme.textDim,
                            fontSize = 10.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$overdueJobs Overdue",
                        style = ConsoleTheme.caption.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (overdueJobs > 0) ConsoleTheme.error else ConsoleTheme.textMuted
                        )
                    )
                }
                
                // Due soon with bar
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    val dueSoonBarValue = dueSoonJobs.coerceIn(0, 5)
                    Text(
                        text = generateBarString(dueSoonBarValue, 5),
                        style = ConsoleTheme.caption.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = if (dueSoonJobs > 0) ConsoleTheme.warning else ConsoleTheme.textDim,
                            fontSize = 10.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$dueSoonJobs Due Soon",
                        style = ConsoleTheme.caption.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (dueSoonJobs > 0) ConsoleTheme.warning else ConsoleTheme.textMuted
                        )
                    )
                }
                
                // Archived count with bar
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    val archivedBarValue = (archivedCount.coerceIn(0, 50) / 10).coerceIn(0, 5)
                    Text(
                        text = generateBarString(archivedBarValue, 5),
                        style = ConsoleTheme.caption.copy(
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                            color = ConsoleTheme.textMuted,
                            fontSize = 10.sp
                        )
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$archivedCount Archived",
                        style = ConsoleTheme.caption.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = ConsoleTheme.textMuted
                        )
                    )
                    Text(
                        text = "Archived",
                        style = ConsoleTheme.caption.copy(
                            fontSize = 10.sp,
                            color = ConsoleTheme.textMuted
                        )
                    )
                }
                
                // Total time
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = formatTime(totalTimeMinutes),
                        style = ConsoleTheme.body.copy(
                            fontWeight = FontWeight.Bold,
                            color = ConsoleTheme.accent,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    )
                    Text(
                        text = "All Time",
                        style = ConsoleTheme.caption.copy(
                            fontSize = 10.sp,
                            color = ConsoleTheme.textMuted
                        )
                    )
                }
            }
        }
        
        // ════════════════════════════════════════════════════════════════
        // CREATE PLAN BUTTON
        // ════════════════════════════════════════════════════════════════
        
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(8.dp))
                    .clickable { onOpenPlan() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "[+]",
                    style = ConsoleTheme.header.copy(
                        fontSize = 20.sp,
                        color = ConsoleTheme.accent
                    )
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "CREATE PLAN",
                        style = ConsoleTheme.body.copy(
                            fontWeight = FontWeight.SemiBold
                        )
                    )
                    Text(
                        text = "Open the deterministic Planner",
                        style = ConsoleTheme.caption.copy(
                            color = Color(0xFF8E8E93)
                        )
                    )
                }
            }
        }
        
        // ════════════════════════════════════════════════════════════════
        // JOBS LIST — Flat list, no grouping
        // Timer running shown only via orange dot next to time
        // ════════════════════════════════════════════════════════════════
        
        items(dashboardJobs) { dashboardJob ->
            JobCardSimple(
                dashboardJob = dashboardJob,
                onArchive = { onArchiveJob(dashboardJob.job) },
                onTap = { onJobTap(dashboardJob.job) }
            )
        }
        
        // ════════════════════════════════════════════════════════════════
        // ARCHIVED COUNT (info only)
        // ════════════════════════════════════════════════════════════════
        
        if (archivedJobs.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF0F0F2), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "[▤]",
                            style = ConsoleTheme.body.copy(
                                color = ConsoleTheme.textMuted
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "${archivedJobs.size} archived job${if (archivedJobs.size != 1) "s" else ""}",
                            style = ConsoleTheme.caption.copy(
                                color = ConsoleTheme.textMuted
                            )
                        )
                    }
                    Text(
                        text = "View in Archive →",
                        style = ConsoleTheme.caption.copy(
                            color = ConsoleTheme.accent
                        ),
                        modifier = Modifier.clickable { onArchiveClick() }
                    )
                }
            }
        }
        
        // ════════════════════════════════════════════════════════════════
        // EMPTY STATE
        // ════════════════════════════════════════════════════════════════
        
        if (jobs.isEmpty()) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "No jobs yet",
                        style = ConsoleTheme.body.copy(
                            color = ConsoleTheme.textMuted
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Create a plan to generate jobs",
                        style = ConsoleTheme.caption.copy(
                            color = Color(0xFF8E8E93)
                        )
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// STAT ITEM — Single stat display
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun StatItem(
    value: String,
    label: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = ConsoleTheme.header.copy(
                fontSize = 24.sp,
                color = color,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
            )
        )
        Text(
            text = label,
            style = ConsoleTheme.caption.copy(
                fontSize = 11.sp
            )
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
// JOB CARD SIMPLE — Flat list card, no grouping, orange timer when running
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun JobCardSimple(
    dashboardJob: DashboardJob,
    onArchive: () -> Unit,
    onTap: () -> Unit = {}
) {
    val job = dashboardJob.job
    
    // Status color mapping
    val statusColor = when (job.status) {
        JobStatus.BACKLOG, JobStatus.TODO -> ConsoleTheme.textMuted
        JobStatus.IN_PROGRESS -> ConsoleTheme.accent
        JobStatus.REVIEW -> ConsoleTheme.warning
        JobStatus.DONE -> ConsoleTheme.success
        JobStatus.ARCHIVED -> ConsoleTheme.textDim
    }
    
    // Status label (short)
    val statusLabel = when (job.status) {
        JobStatus.BACKLOG -> "backlog"
        JobStatus.TODO -> "todo"
        JobStatus.IN_PROGRESS -> "active"
        JobStatus.REVIEW -> "review"
        JobStatus.DONE -> "done"
        JobStatus.ARCHIVED -> "archived"
    }
    
    // Format due date if present
    val dueDateText = job.dueDate?.let { dueDate ->
        val now = System.currentTimeMillis()
        val daysUntil = ((dueDate - now) / (1000 * 60 * 60 * 24)).toInt()
        when {
            daysUntil < 0 -> "overdue"
            daysUntil == 0 -> "today"
            daysUntil == 1 -> "tomorrow"
            daysUntil < 7 -> "${daysUntil}d"
            else -> {
                val sdf = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault())
                sdf.format(java.util.Date(dueDate))
            }
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(8.dp))
            .clickable { onTap() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status dot indicator (left side)
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusColor)
            )
            
            Spacer(modifier = Modifier.width(10.dp))
            
            // Job info (title + details)
            Column(modifier = Modifier.weight(1f)) {
                // Title row
                Text(
                    text = job.title,
                    style = ConsoleTheme.body.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                // Details row: client name, status label, due date
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Client name (if present)
                    if (!job.clientName.isNullOrBlank()) {
                        Text(
                            text = job.clientName,
                            style = ConsoleTheme.caption.copy(
                                fontSize = 11.sp,
                                color = ConsoleTheme.textMuted
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.widthIn(max = 100.dp)
                        )
                        Text(
                            text = "•",
                            style = ConsoleTheme.caption.copy(
                                fontSize = 10.sp,
                                color = ConsoleTheme.textDim
                            )
                        )
                    }
                    
                    // Status label
                    Text(
                        text = statusLabel,
                        style = ConsoleTheme.caption.copy(
                            fontSize = 10.sp,
                            color = statusColor
                        )
                    )
                    
                    // Due date (if present)
                    if (dueDateText != null) {
                        Text(
                            text = "•",
                            style = ConsoleTheme.caption.copy(
                                fontSize = 10.sp,
                                color = ConsoleTheme.textDim
                            )
                        )
                        Text(
                            text = dueDateText,
                            style = ConsoleTheme.caption.copy(
                                fontSize = 10.sp,
                                color = if (dueDateText == "overdue") ConsoleTheme.error else ConsoleTheme.textMuted
                            )
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Time with orange indicator when timer is running
            if (dashboardJob.totalMinutes > 0 || dashboardJob.isTimerRunning) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatTime(dashboardJob.totalMinutes),
                        style = ConsoleTheme.caption.copy(
                            color = if (dashboardJob.isTimerRunning) ConsoleTheme.warning else ConsoleTheme.textMuted,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    )
                    
                    // Orange dot indicator when timer is running
                    if (dashboardJob.isTimerRunning) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(ConsoleTheme.warning)
                        )
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// JOB CARD PENDING — For jobs not yet started
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun JobCardPending(dashboardJob: DashboardJob) {
    // Check document status
    val hasProposal = dashboardJob.job.tags.any { it.contains("proposal", ignoreCase = true) }
    val hasReport = dashboardJob.job.tags.any { it.contains("report", ignoreCase = true) }
    val hasInvoice = dashboardJob.job.tags.any { it.contains("invoice", ignoreCase = true) }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(8.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // PENDING indicator in square
        Box(
            modifier = Modifier
                .background(
                    ConsoleTheme.textMuted.copy(alpha = 0.1f),
                    RoundedCornerShape(4.dp)
                )
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .border(1.5.dp, ConsoleTheme.textMuted, CircleShape)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "NEW",
                    style = ConsoleTheme.caption.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = ConsoleTheme.textMuted
                    )
                )
            }
        }
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Job info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = dashboardJob.job.title,
                style = ConsoleTheme.body.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            
            // Created date
            Text(
                text = java.text.SimpleDateFormat("MM/dd", java.util.Locale.getDefault())
                    .format(java.util.Date(dashboardJob.job.createdAt)),
                style = ConsoleTheme.caption.copy(
                    color = ConsoleTheme.textDim,
                    fontSize = 10.sp
                )
            )
        }
        
        // Document status indicators: P R I
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "P",
                style = ConsoleTheme.caption.copy(
                    fontSize = 12.sp,
                    fontWeight = if (hasProposal) FontWeight.Bold else FontWeight.Normal,
                    color = if (hasProposal) ConsoleTheme.success else ConsoleTheme.textDim
                )
            )
            Text(
                text = "R",
                style = ConsoleTheme.caption.copy(
                    fontSize = 12.sp,
                    fontWeight = if (hasReport) FontWeight.Bold else FontWeight.Normal,
                    color = if (hasReport) ConsoleTheme.success else ConsoleTheme.textDim
                )
            )
            Text(
                text = "I",
                style = ConsoleTheme.caption.copy(
                    fontSize = 12.sp,
                    fontWeight = if (hasInvoice) FontWeight.Bold else FontWeight.Normal,
                    color = if (hasInvoice) ConsoleTheme.success else ConsoleTheme.textDim
                )
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// JOB CARD LIVE — For jobs that are in progress
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun JobCardLive(dashboardJob: DashboardJob) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status dot only (no badge text - section header already says LIVE)
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        if (dashboardJob.isTimerRunning) ConsoleTheme.warning 
                        else ConsoleTheme.accent
                    )
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Job info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dashboardJob.job.title,
                    style = ConsoleTheme.body.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                // Time logged (if any)
                if (dashboardJob.totalMinutes > 0) {
                    Text(
                        text = formatTime(dashboardJob.totalMinutes) + if (dashboardJob.isTimerRunning) " ●" else "",
                        style = ConsoleTheme.caption.copy(
                            color = if (dashboardJob.isTimerRunning) ConsoleTheme.warning else ConsoleTheme.textMuted,
                            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                        )
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
// JOB CARD READY TO CLOSE — For jobs ready for invoice
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun JobCardReadyToClose(
    dashboardJob: DashboardJob,
    onArchive: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var showArchiveConfirm by remember { mutableStateOf(false) }
    var showInvoice by remember { mutableStateOf(false) }
    var generatedInvoice by remember { mutableStateOf<Invoice?>(null) }
    
    // Load time entries for invoice generation
    val timeEntries = remember { TimeStorage.loadEntries() }
    val userName = remember { UserPreferences.getUserName() }
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(8.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // READY indicator (checkmark)
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(ConsoleTheme.success)
            )
            
            Spacer(modifier = Modifier.width(12.dp))
            
            // Job info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = dashboardJob.job.title,
                    style = ConsoleTheme.body.copy(fontWeight = FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Status badge
                    Box(
                        modifier = Modifier
                            .background(
                                ConsoleTheme.success.copy(alpha = 0.15f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "✓ READY",
                            style = ConsoleTheme.caption.copy(
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = ConsoleTheme.success
                            )
                        )
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    // Job status
                    Text(
                        text = dashboardJob.job.status.displayName,
                        style = ConsoleTheme.caption.copy(
                            color = ConsoleTheme.textMuted,
                            fontSize = 10.sp
                        )
                    )
                }
            }
            
            // Time display
            Text(
                text = formatTime(dashboardJob.totalMinutes),
                style = ConsoleTheme.body.copy(
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                    color = ConsoleTheme.text
                )
            )
        }
        
        // Action row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ConsoleTheme.success.copy(alpha = 0.05f))
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Invoice button (enabled)
            Row(
                modifier = Modifier
                    .background(ConsoleTheme.success, RoundedCornerShape(4.dp))
                    .clickable {
                        // Generate invoice from job + time entries
                        generatedInvoice = InvoiceGenerator.generateFromJob(
                            job = dashboardJob.job,
                            timeEntries = timeEntries,
                            providerName = userName
                        )
                        showInvoice = true
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "[I]",
                    style = ConsoleTheme.caption.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "INVOICE",
                    style = ConsoleTheme.caption.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                )
            }
            
            // Archive button
            Text(
                text = "[▤] ARCHIVE",
                style = ConsoleTheme.caption.copy(
                    color = ConsoleTheme.accent,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp
                ),
                modifier = Modifier
                    .clickable { showArchiveConfirm = true }
                    .padding(8.dp)
            )
        }
    }
    
    // Invoice preview dialog
    if (showInvoice && generatedInvoice != null) {
        InvoicePreviewDialog(
            invoice = generatedInvoice!!,
            onDismiss = { showInvoice = false },
            onShare = { invoiceText ->
                val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(android.content.Intent.EXTRA_SUBJECT, "Invoice - ${dashboardJob.job.title}")
                    putExtra(android.content.Intent.EXTRA_TEXT, invoiceText)
                }
                context.startActivity(android.content.Intent.createChooser(shareIntent, "Share Invoice"))
            }
        )
    }
    
    // Archive confirmation dialog
    if (showArchiveConfirm) {
        AlertDialog(
            onDismissRequest = { showArchiveConfirm = false },
            containerColor = Color.White,
            title = {
                Text(
                    text = "Archive Job?",
                    style = ConsoleTheme.header.copy(fontWeight = FontWeight.SemiBold)
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = dashboardJob.job.title,
                        style = ConsoleTheme.body.copy(fontWeight = FontWeight.Medium)
                    )
                    Text(
                        text = "Time logged: ${formatTime(dashboardJob.totalMinutes)}",
                        style = ConsoleTheme.caption
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "This job will be moved to Archive. Generate an invoice first if needed.",
                        style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                    )
                }
            },
            confirmButton = {
                Text(
                    text = "ARCHIVE",
                    style = ConsoleTheme.body.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = ConsoleTheme.accent
                    ),
                    modifier = Modifier
                        .clickable {
                            onArchive()
                            showArchiveConfirm = false
                        }
                        .padding(8.dp)
                )
            },
            dismissButton = {
                Text(
                    text = "CANCEL",
                    style = ConsoleTheme.body.copy(color = ConsoleTheme.textMuted),
                    modifier = Modifier
                        .clickable { showArchiveConfirm = false }
                        .padding(8.dp)
                )
            }
        )
    }
}

// Legacy JobCard - kept for compatibility
@Composable
private fun JobCard(
    dashboardJob: DashboardJob,
    stateColor: Color
) {
    JobCardLive(dashboardJob = dashboardJob)
}

// Legacy JobCardWithArchive - redirects to JobCardReadyToClose
@Composable
private fun JobCardWithArchive(
    dashboardJob: DashboardJob,
    stateColor: Color,
    onArchive: () -> Unit
) {
    JobCardReadyToClose(dashboardJob = dashboardJob, onArchive = onArchive)
}

// ════════════════════════════════════════════════════════════════════════════
// FOOTER COMPONENT — Shared across Plan, Jobs, Time (not Chat)
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun SmithNetFooter(
    currentTab: DashboardTab?,
    onSettingsClick: () -> Unit
) {
    // Don't show footer for Chat tab
    if (currentTab == DashboardTab.CHAT) return
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
    ) {
        // Separator
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0xFFE5E5EA))
        )
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Settings (right side only)
            Text(
                text = "SETTINGS",
                style = ConsoleTheme.caption.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF8E8E93)
                ),
                modifier = Modifier.clickable(onClick = onSettingsClick)
            )
        }
        
        // Made by
        Text(
            text = "made by guild of smiths",
            style = ConsoleTheme.caption.copy(
                fontSize = 12.sp,
                color = Color(0xFFAEAEB2)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
// SHARED FOOTER — Can be used by other screens (JobBoard, TimeTracking)
// ════════════════════════════════════════════════════════════════════════════

@Composable
fun SmithNetSharedFooter(
    onSettingsClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
    ) {
        // Separator
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0xFFE5E5EA))
        )
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Settings (right side only)
            Text(
                text = "SETTINGS",
                style = ConsoleTheme.caption.copy(
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF8E8E93)
                ),
                modifier = Modifier.clickable(onClick = onSettingsClick)
            )
        }
        
        // Made by
        Text(
            text = "made by guild of smiths",
            style = ConsoleTheme.caption.copy(
                fontSize = 12.sp,
                color = Color(0xFFAEAEB2)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
