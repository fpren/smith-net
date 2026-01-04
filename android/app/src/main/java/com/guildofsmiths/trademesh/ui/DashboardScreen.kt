package com.guildofsmiths.trademesh.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guildofsmiths.trademesh.data.JobRepository
import com.guildofsmiths.trademesh.data.JobStorage
import com.guildofsmiths.trademesh.data.UserPreferences
import com.guildofsmiths.trademesh.engine.BoundaryEngine
import com.guildofsmiths.trademesh.ui.jobboard.Job
import com.guildofsmiths.trademesh.ui.jobboard.JobStatus

/**
 * SMITHNET DASHBOARD — Central Operational Hub
 *
 * The Dashboard is NOT a form, NOT a wizard, and NOT a content editor.
 * It is the central hub that reflects system state and routes the user
 * into Plan, Job Board, Messages, and Time.
 *
 * Core Principle: Shows WHAT EXISTS and WHAT IS ACTIVE.
 * Creation and editing happen elsewhere.
 */

// ════════════════════════════════════════════════════════════════════
// DATA CLASSES
// ════════════════════════════════════════════════════════════════════

data class PlanSnapshot(
    val issueStatement: String,
    val confirmedScopeCount: Int,
    val assumptionsCount: Int,
    val exclusionsCount: Int
)

data class PendingJob(
    val id: String,
    val title: String,
    val description: String? = null,
    val isSelected: Boolean = false
)

enum class DashboardMode {
    PLAN,
    JOB_BOARD,
    MESSAGES
}

@Composable
fun DashboardScreen(
    onPlanClick: () -> Unit,
    onJobBoardClick: () -> Unit,
    onMessagesClick: () -> Unit,
    onTimeClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onProfileClick: () -> Unit,
    onArchiveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // State
    val isMeshConnected by BoundaryEngine.isMeshConnected.collectAsState()
    val isGatewayConnected by BoundaryEngine.isGatewayConnected.collectAsState()
    val activeJobs by JobRepository.activeJobs.collectAsState()

    // Pending jobs from Plan (not yet transferred)
    var pendingJobs by remember { mutableStateOf<List<PendingJob>>(emptyList()) }
    var selectedJobIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Active plan snapshot (would come from PlanViewModel in real implementation)
    var activePlan by remember { mutableStateOf<PlanSnapshot?>(null) }

    // Menu state
    var showMenu by remember { mutableStateOf(false) }
    
    // Completed jobs from storage (for document generation)
    var completedJobs by remember { mutableStateOf<List<Job>>(emptyList()) }
    var selectedCompletedJob by remember { mutableStateOf<Job?>(null) }
    var showDocumentDialog by remember { mutableStateOf(false) }
    
    // Load completed jobs
    LaunchedEffect(Unit) {
        val allJobs = JobStorage.loadJobs()
        completedJobs = allJobs.filter { it.status == JobStatus.DONE && !it.isArchived }
    }

    // Job counts by status
    val jobCounts = remember(activeJobs) {
        mapOf(
            "TODO" to activeJobs.count { it.status == "TODO" },
            "IN_PROGRESS" to activeJobs.count { it.status == "IN_PROGRESS" },
            "REVIEW" to activeJobs.count { it.status == "REVIEW" },
            "DONE" to activeJobs.count { it.status == "DONE" }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ConsoleTheme.background)
    ) {
        // ════════════════════════════════════════════════════════════════
        // TOP BAR (GLOBAL, ALWAYS VISIBLE)
        // ════════════════════════════════════════════════════════════════

        DashboardTopBar(
            isMeshConnected = isMeshConnected,
            isGatewayConnected = isGatewayConnected,
            onMenuClick = { showMenu = !showMenu },
            onSettingsClick = onSettingsClick
        )

        // Dropdown menu
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false },
            modifier = Modifier.background(ConsoleTheme.surface)
        ) {
            DropdownMenuItem(
                text = { Text("PROFILE", style = ConsoleTheme.body) },
                onClick = {
                    showMenu = false
                    onProfileClick()
                }
            )
            DropdownMenuItem(
                text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("NETWORK", style = ConsoleTheme.body)
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isMeshConnected || isGatewayConnected)
                                        ConsoleTheme.success
                                    else ConsoleTheme.error
                                )
                        )
                    }
                },
                onClick = { showMenu = false }
            )
            DropdownMenuItem(
                text = { Text("ARCHIVE", style = ConsoleTheme.body) },
                onClick = {
                    showMenu = false
                    onArchiveClick()
                }
            )
        }

        ConsoleSeparator()

        // ════════════════════════════════════════════════════════════════
        // PRIMARY MODE STRIP
        // ════════════════════════════════════════════════════════════════

        ModeStrip(
            onPlanClick = onPlanClick,
            onJobBoardClick = onJobBoardClick,
            onMessagesClick = onMessagesClick
        )

        ConsoleSeparator()

        // ════════════════════════════════════════════════════════════════
        // MAIN CONTENT (Scrollable)
        // ════════════════════════════════════════════════════════════════

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }

            // ════════════════════════════════════════════════════════════════
            // ACTIVE CONTEXT SECTION (READ-ONLY)
            // ════════════════════════════════════════════════════════════════

            item {
                ActiveContextSection(
                    planSnapshot = activePlan,
                    jobCounts = jobCounts
                )
            }

            // ════════════════════════════════════════════════════════════════
            // COMPLETED JOBS - Ready for Invoice/Report Generation
            // ════════════════════════════════════════════════════════════════
            
            if (completedJobs.isNotEmpty()) {
                item {
                    Text(
                        text = "COMPLETED JOBS",
                        style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.success)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Select a job to generate Invoice or Report",
                        style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                    )
                }
                
                items(completedJobs.take(5)) { job ->
                    CompletedJobCard(
                        job = job,
                        onSelect = {
                            selectedCompletedJob = job
                            showDocumentDialog = true
                        }
                    )
                }
                
                if (completedJobs.size > 5) {
                    item {
                        Text(
                            text = "+ ${completedJobs.size - 5} more in Job Board",
                            style = ConsoleTheme.caption.copy(color = ConsoleTheme.accent),
                            modifier = Modifier
                                .clickable(onClick = onJobBoardClick)
                                .padding(vertical = 8.dp)
                        )
                    }
                }
            }

            // ════════════════════════════════════════════════════════════════
            // TIME TRACKING QUICK ACCESS
            // ════════════════════════════════════════════════════════════════

            item {
                TimeQuickAccess(
                    onTimeClick = onTimeClick
                )
            }

            // ════════════════════════════════════════════════════════════════
            // GENERATED JOBS (TRANSFER ZONE)
            // ════════════════════════════════════════════════════════════════

            if (pendingJobs.isNotEmpty()) {
                item {
                    Text(
                        text = "PENDING TRANSFER",
                        style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.warning)
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "${pendingJobs.size} jobs ready",
                            style = ConsoleTheme.caption
                        )
                        Text(
                            text = "[SELECT ALL]",
                            style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.accent),
                            modifier = Modifier.clickable {
                                selectedJobIds = if (selectedJobIds.size == pendingJobs.size) {
                                    emptySet()
                                } else {
                                    pendingJobs.map { it.id }.toSet()
                                }
                            }
                        )
                    }
                }

                items(pendingJobs) { job ->
                    PendingJobCard(
                        job = job,
                        isSelected = selectedJobIds.contains(job.id),
                        onToggle = {
                            selectedJobIds = if (selectedJobIds.contains(job.id)) {
                                selectedJobIds - job.id
                            } else {
                                selectedJobIds + job.id
                            }
                        }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp)) // Space for bottom action
            }
        }

        // ════════════════════════════════════════════════════════════════
        // PRIMARY ACTION (BOTTOM)
        // ════════════════════════════════════════════════════════════════

        if (selectedJobIds.isNotEmpty()) {
            TransferAction(
                selectedCount = selectedJobIds.size,
                onTransfer = {
                    // Transfer selected jobs to Job Board
                    val jobsToTransfer = pendingJobs.filter { selectedJobIds.contains(it.id) }
                    jobsToTransfer.forEach { job ->
                        JobRepository.addJob(
                            JobRepository.SimpleJob(
                                id = job.id,
                                title = job.title,
                                status = "TODO"
                            )
                        )
                    }
                    // Clear pending jobs after transfer
                    pendingJobs = pendingJobs.filter { !selectedJobIds.contains(it.id) }
                    selectedJobIds = emptySet()
                }
            )
        }
    }
    
    // ════════════════════════════════════════════════════════════════
    // DOCUMENT GENERATION DIALOG
    // ════════════════════════════════════════════════════════════════
    
    if (showDocumentDialog && selectedCompletedJob != null) {
        DocumentGenerationDialog(
            job = selectedCompletedJob!!,
            onDismiss = { 
                showDocumentDialog = false
                selectedCompletedJob = null
            },
            onNavigateToPlan = {
                showDocumentDialog = false
                selectedCompletedJob = null
                onPlanClick()
            }
        )
    }
}

// ════════════════════════════════════════════════════════════════════
// TOP BAR COMPONENT
// ════════════════════════════════════════════════════════════════════

@Composable
private fun DashboardTopBar(
    isMeshConnected: Boolean,
    isGatewayConnected: Boolean,
    onMenuClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ConsoleTheme.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // LEFT: Hamburger menu
        Text(
            text = "≡",
            style = ConsoleTheme.header.copy(fontSize = 24.sp),
            modifier = Modifier
                .clickable(onClick = onMenuClick)
                .padding(4.dp)
        )

        Spacer(modifier = Modifier.weight(1f))

        // CENTER: App name + status
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "SMITHNET",
                style = ConsoleTheme.brand.copy(fontSize = 20.sp, letterSpacing = 2.sp)
            )

            // Status indicator
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                isGatewayConnected -> ConsoleTheme.success
                                isMeshConnected -> ConsoleTheme.warning
                                else -> ConsoleTheme.textMuted
                            }
                        )
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = when {
                        isGatewayConnected -> "ONLINE"
                        isMeshConnected -> "MESH"
                        else -> "OFFLINE"
                    },
                    style = ConsoleTheme.caption.copy(fontSize = 9.sp)
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // RIGHT: Settings gear
        Text(
            text = "⚙",
            style = ConsoleTheme.header.copy(fontSize = 20.sp),
            modifier = Modifier
                .clickable(onClick = onSettingsClick)
                .padding(4.dp)
        )
    }
}

// ════════════════════════════════════════════════════════════════════
// MODE STRIP COMPONENT
// ════════════════════════════════════════════════════════════════════

@Composable
private fun ModeStrip(
    onPlanClick: () -> Unit,
    onJobBoardClick: () -> Unit,
    onMessagesClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ConsoleTheme.surface)
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        ModeButton(
            text = "PLAN",
            isHighlighted = true,
            onClick = onPlanClick
        )
        ModeButton(
            text = "JOB BOARD",
            onClick = onJobBoardClick
        )
        ModeButton(
            text = "MESSAGES",
            onClick = onMessagesClick
        )
    }
}

@Composable
private fun ModeButton(
    text: String,
    isHighlighted: Boolean = false,
    onClick: () -> Unit
) {
    Text(
        text = "[ $text ]",
        style = ConsoleTheme.bodyBold.copy(
            color = if (isHighlighted) ConsoleTheme.accent else ConsoleTheme.text,
            fontSize = 13.sp
        ),
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    )
}

// ════════════════════════════════════════════════════════════════════
// ACTIVE CONTEXT SECTION
// ════════════════════════════════════════════════════════════════════

@Composable
private fun ActiveContextSection(
    planSnapshot: PlanSnapshot?,
    jobCounts: Map<String, Int>
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "ACTIVE CONTEXT",
            style = ConsoleTheme.captionBold
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Plan snapshot
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(ConsoleTheme.surface)
                .padding(12.dp)
        ) {
            if (planSnapshot != null) {
                Column {
                    Text(
                        text = planSnapshot.issueStatement,
                        style = ConsoleTheme.body,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${planSnapshot.confirmedScopeCount} scope items • ${planSnapshot.assumptionsCount} assumptions • ${planSnapshot.exclusionsCount} exclusions",
                        style = ConsoleTheme.caption
                    )
                }
            } else {
                Text(
                    text = "No active plan",
                    style = ConsoleTheme.body.copy(color = ConsoleTheme.textMuted)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Job counts
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            JobCountBadge(
                label = "TO DO",
                count = jobCounts["TODO"] ?: 0,
                color = ConsoleTheme.text
            )
            JobCountBadge(
                label = "WORKING",
                count = jobCounts["IN_PROGRESS"] ?: 0,
                color = ConsoleTheme.warning
            )
            JobCountBadge(
                label = "CHECK",
                count = jobCounts["REVIEW"] ?: 0,
                color = ConsoleTheme.accent
            )
            JobCountBadge(
                label = "DONE",
                count = jobCounts["DONE"] ?: 0,
                color = ConsoleTheme.success
            )
        }
    }
}

@Composable
private fun JobCountBadge(
    label: String,
    count: Int,
    color: Color
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = count.toString(),
            style = ConsoleTheme.header.copy(
                color = if (count > 0) color else ConsoleTheme.textMuted,
                fontSize = 24.sp
            )
        )
        Text(
            text = label,
            style = ConsoleTheme.caption.copy(fontSize = 10.sp)
        )
    }
}

// ════════════════════════════════════════════════════════════════════
// TIME QUICK ACCESS
// ════════════════════════════════════════════════════════════════════

@Composable
private fun TimeQuickAccess(
    onTimeClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ConsoleTheme.surface)
            .clickable(onClick = onTimeClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "[◷]",
            style = ConsoleTheme.header
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "TIME",
                style = ConsoleTheme.bodyBold
            )
            Text(
                text = "Clock in/out • Track hours",
                style = ConsoleTheme.caption
            )
        }
        Text(
            text = ">",
            style = ConsoleTheme.header
        )
    }
}

// ════════════════════════════════════════════════════════════════════
// PENDING JOB CARD (PERFORATED / DASHED)
// ════════════════════════════════════════════════════════════════════

@Composable
private fun PendingJobCard(
    job: PendingJob,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    val dashedBorder = remember {
        PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                // Draw dashed border (perforated effect)
                drawRect(
                    color = if (isSelected) Color(0xFF28A745) else Color(0xFFD8D8DC),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 2f,
                        pathEffect = dashedBorder
                    )
                )
            }
            .clickable(onClick = onToggle)
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        // Checkbox
        Text(
            text = if (isSelected) "☑" else "☐",
            style = ConsoleTheme.header.copy(
                color = if (isSelected) ConsoleTheme.success else ConsoleTheme.textMuted
            )
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = job.title,
                style = ConsoleTheme.bodyBold.copy(
                    color = if (isSelected) ConsoleTheme.success else ConsoleTheme.text
                )
            )
            if (!job.description.isNullOrEmpty()) {
                Text(
                    text = job.description,
                    style = ConsoleTheme.caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// COMPLETED JOB CARD
// ════════════════════════════════════════════════════════════════════

@Composable
private fun CompletedJobCard(
    job: Job,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ConsoleTheme.surface)
            .border(1.dp, ConsoleTheme.success.copy(alpha = 0.3f))
            .clickable(onClick = onSelect)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status indicator
        Text(
            text = "[✓]",
            style = ConsoleTheme.bodyBold.copy(color = ConsoleTheme.success)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = job.title,
                style = ConsoleTheme.body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Completed • Ready for documents",
                style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
            )
        }
        
        Text(
            text = "[DOCS]",
            style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.accent)
        )
    }
}

// ════════════════════════════════════════════════════════════════════
// DOCUMENT GENERATION DIALOG
// ════════════════════════════════════════════════════════════════════

@Composable
private fun DocumentGenerationDialog(
    job: Job,
    onDismiss: () -> Unit,
    onNavigateToPlan: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ConsoleTheme.background,
        title = {
            Column {
                Text(
                    text = "GENERATE DOCUMENTS",
                    style = ConsoleTheme.header
                )
                Text(
                    text = job.title,
                    style = ConsoleTheme.body.copy(color = ConsoleTheme.accent),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Select documents to generate for this completed job:",
                    style = ConsoleTheme.body
                )
                
                // Document options
                DocumentOption(
                    title = "INVOICE",
                    description = "Generate billing invoice with labor and materials",
                    onClick = {
                        // TODO: Generate invoice in Plan with job data
                        onNavigateToPlan()
                    }
                )
                
                DocumentOption(
                    title = "REPORT",
                    description = "Work completion report with task summary",
                    onClick = {
                        onNavigateToPlan()
                    }
                )
                
                DocumentOption(
                    title = "INVOICE + REPORT",
                    description = "Generate both documents together",
                    isHighlighted = true,
                    onClick = {
                        onNavigateToPlan()
                    }
                )
                
                Text(
                    text = "Documents will be generated in the Plan view.",
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            Text(
                text = "CLOSE",
                style = ConsoleTheme.action.copy(color = ConsoleTheme.textMuted),
                modifier = Modifier.clickable(onClick = onDismiss)
            )
        }
    )
}

@Composable
private fun DocumentOption(
    title: String,
    description: String,
    isHighlighted: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (isHighlighted) ConsoleTheme.success.copy(alpha = 0.1f) 
                else ConsoleTheme.surface
            )
            .border(
                width = if (isHighlighted) 1.dp else 0.dp,
                color = if (isHighlighted) ConsoleTheme.success else Color.Transparent
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = ConsoleTheme.bodyBold.copy(
                    color = if (isHighlighted) ConsoleTheme.success else ConsoleTheme.text
                )
            )
            Text(
                text = description,
                style = ConsoleTheme.caption
            )
        }
        
        Text(
            text = "→",
            style = ConsoleTheme.header.copy(
                color = if (isHighlighted) ConsoleTheme.success else ConsoleTheme.accent
            )
        )
    }
}

// ════════════════════════════════════════════════════════════════════
// TRANSFER ACTION
// ════════════════════════════════════════════════════════════════════

@Composable
private fun TransferAction(
    selectedCount: Int,
    onTransfer: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(ConsoleTheme.success)
            .clickable(onClick = onTransfer)
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "[ TRANSFER $selectedCount SELECTED JOBS ]",
            style = ConsoleTheme.bodyBold.copy(
                color = Color.White,
                fontSize = 15.sp
            )
        )
    }
}