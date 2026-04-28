package com.guildofsmiths.trademesh.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.guildofsmiths.trademesh.ui.ConsoleTheme
import com.guildofsmiths.trademesh.ui.jobboard.Job
import com.guildofsmiths.trademesh.ui.jobboard.JobStage
import com.guildofsmiths.trademesh.ai.AISupervisor
import com.guildofsmiths.trademesh.data.ChannelType
import com.guildofsmiths.trademesh.data.CrewPresenceRepository
import com.guildofsmiths.trademesh.data.RoleContext
import com.guildofsmiths.trademesh.data.Permission
import com.guildofsmiths.trademesh.data.BeaconRepository
import com.guildofsmiths.trademesh.ui.jobboard.JobBoardViewModel

// ════════════════════════════════════════════════════════════════════
// DASHBOARD SCREEN — Role-Adaptive Command Surface
// ════════════════════════════════════════════════════════════════════

@Composable
fun DashboardScreen(
    jobs: List<Job>,
    onJobClick: (String) -> Unit,
    onNewJob: () -> Unit,
    onClockIn: () -> Unit,
    onComm: () -> Unit,
    onSettings: () -> Unit,
    onProfile: () -> Unit,
    onArchive: () -> Unit,
    onJobBoard: () -> Unit = {},
    onDispatch: () -> Unit = {},
    onMessageCrew: ((com.guildofsmiths.trademesh.data.CrewPresenceInfo) -> Unit)? = null,
    onTimeTracking: () -> Unit = {},
    onPlan: () -> Unit = {},
    onReport: () -> Unit = {},
    onSupply: () -> Unit = {},
    onClients: () -> Unit = {},
    onMap: () -> Unit = {},
    onExpenses: () -> Unit = {},
    viewModel: DashboardViewModel = viewModel()
) {
    LaunchedEffect(jobs) {
        viewModel.loadJobs(jobs)
        viewModel.refreshClockState()
        // Start AI supervisor loop — works for all roles including Solo
        AISupervisor.startLoop(jobs)
    }

    // Refresh clock state every time dashboard is visible
    LaunchedEffect(Unit) {
        while (true) {
            viewModel.refreshClockState()
            kotlinx.coroutines.delay(5000) // Check every 5 seconds
        }
    }

    val activeJobs by viewModel.jobs.collectAsState()
    val isClockedIn by viewModel.isClockedIn
    val activeEntryInfo = remember(isClockedIn) { viewModel.getActiveEntryInfo() }

    // Live clock tick: drives recomposition every second while clocked in
    // so the dashboard's "ON CLOCK Xh Ym Zs" pill updates in real time.
    var nowMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(isClockedIn) {
        while (isClockedIn) {
            nowMs = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000)
        }
    }

    // Update AI with latest jobs when they change
    LaunchedEffect(activeJobs) {
        AISupervisor.updateJobs(activeJobs)
    }

    var selectedDay by remember { mutableStateOf<Int?>(null) }

    // Crew assignment dialog state
    var assigningJob by remember { mutableStateOf<com.guildofsmiths.trademesh.ui.jobboard.Job?>(null) }
    val activityOwner = LocalContext.current as ViewModelStoreOwner
    val jobViewModel: JobBoardViewModel = viewModel(viewModelStoreOwner = activityOwner)

    if (assigningJob != null) {
        CrewAssignDialog(
            job = assigningJob!!,
            onAssign = { job, crewMember ->
                jobViewModel.assignCrewToJob(job.id, crewMember)
                assigningJob = null
            },
            onDismiss = { assigningJob = null }
        )
    }

    val beacons by BeaconRepository.beacons.collectAsState()
    // In Solo mode, exclude crew DM channels from notification banner
    val isSoloMode = RoleContext.isSolo()
    val crewNames = remember(isSoloMode) {
        if (isSoloMode) CrewPresenceRepository.getCrew().map { it.name }.toSet() else emptySet()
    }
    val relevantChannels = remember(beacons, isSoloMode, crewNames) {
        beacons.flatMap { it.channels }.let { channels ->
            if (isSoloMode) channels.filter { ch ->
                ch.type != ChannelType.DM || ch.name !in crewNames
            } else channels
        }
    }
    val totalUnreads = remember(relevantChannels) {
        relevantChannels.sumOf { it.unreadCount }
    }
    val latestMessage = remember(relevantChannels) {
        relevantChannels
            .filter { it.lastMessagePreview != null && it.lastMessageTime != null }
            .maxByOrNull { it.lastMessageTime ?: 0L }
    }

    // Resolve which modules to show based on current role
    val modules = resolveModules(RoleContext.role)

    // Role-specific quick actions
    val quickActions = getQuickActions(
        role = RoleContext.role,
        onReport = onReport,
        onSupply = onSupply,
        onArchive = onArchive,
        onClients = onClients,
        onClockIn = onClockIn,
        onComm = onComm,
        onJobBoard = onJobBoard,
        onDispatch = onDispatch,
        onExpenses = onExpenses,
        onMap = onMap,
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ConsoleTheme.background)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Render modules in order
            modules.forEach { module ->
                when (module) {
                    DashboardModule.HEADER -> {
                        // ── HEADER ─────────────────────────────────────
                        val headerTitle = if (RoleContext.isTeamMember()) {
                            "Team" // Will show org name when org system is wired
                        } else {
                            viewModel.getBusinessName()
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = headerTitle,
                                style = ConsoleTheme.bodyBold.copy(color = ConsoleTheme.text),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = rememberRipple(bounded = true),
                                        onClick = { onProfile() }
                                    )
                                    .padding(4.dp)
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                val clockBg = if (isClockedIn) ConsoleTheme.success.copy(alpha = 0.10f) else ConsoleTheme.surface
                                val clockColor = if (isClockedIn) ConsoleTheme.success else ConsoleTheme.textMuted
                                val clockLabel = if (isClockedIn) {
                                    val elapsed = if (activeEntryInfo.second > 0) {
                                        val secs = ((nowMs - activeEntryInfo.second) / 1000).coerceAtLeast(0)
                                        val h = secs / 3600
                                        val m = (secs % 3600) / 60
                                        val s = secs % 60
                                        if (h > 0) "${h}h ${m}m ${s}s"
                                        else "${m}m ${s}s"
                                    } else ""
                                    "● ON CLOCK $elapsed"
                                } else {
                                    "○ OFF CLOCK"
                                }

                                Box(
                                    modifier = Modifier
                                        .background(clockBg, RoundedCornerShape(4.dp))
                                        .border(0.5.dp, ConsoleTheme.text.copy(alpha = 0.06f), RoundedCornerShape(4.dp))
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = rememberRipple(bounded = true, color = ConsoleTheme.accent),
                                            onClick = { onClockIn() }
                                        )
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(clockLabel, style = ConsoleTheme.caption.copy(color = clockColor))
                                }

                                Box(
                                    modifier = Modifier
                                        .background(ConsoleTheme.surface, RoundedCornerShape(4.dp))
                                        .border(0.5.dp, ConsoleTheme.text.copy(alpha = 0.06f), RoundedCornerShape(4.dp))
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = rememberRipple(bounded = true),
                                            onClick = { onSettings() }
                                        )
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text("[⚙]", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
                                }
                            }
                        }
                    }

                    DashboardModule.MESSAGE_STRIP -> {
                        // Show last 3 recent channel events
                        val recentChannels = remember(relevantChannels) {
                            relevantChannels
                                .filter { it.lastMessagePreview != null && it.lastMessageTime != null }
                                .sortedByDescending { it.lastMessageTime ?: 0L }
                                .take(3)
                        }

                        if (recentChannels.isNotEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(ConsoleTheme.surface, RoundedCornerShape(4.dp))
                                    .border(0.5.dp, ConsoleTheme.text.copy(alpha = 0.04f), RoundedCornerShape(4.dp))
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = rememberRipple(bounded = true),
                                        onClick = { onComm() }
                                    )
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                recentChannels.forEach { ch ->
                                    val age = formatTimeAgo(ch.lastMessageTime ?: 0L)
                                    val preview = (ch.lastMessagePreview ?: "").take(35)
                                    val name = ch.name
                                    val hasUnread = ch.unreadCount > 0
                                    val color = if (hasUnread) ConsoleTheme.accent else ConsoleTheme.textMuted

                                    Text(
                                        text = "$name: \"$preview\"  $age",
                                        style = ConsoleTheme.caption.copy(color = color),
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    DashboardModule.AI_INBOX -> AIInboxModule()
                    DashboardModule.MY_TASKS -> MyTasksModule()
                    DashboardModule.TEAM_PRESENCE -> {
                        val ctx = androidx.compose.ui.platform.LocalContext.current
                        TeamPresenceModule(
                            onMapClick = { /* Navigate to map via bottom nav */ },
                            onDispatchClick = onJobBoard,
                            onCallPhone = { phone ->
                                val intent = android.content.Intent(
                                    android.content.Intent.ACTION_DIAL,
                                    android.net.Uri.parse("tel:$phone")
                                )
                                ctx.startActivity(intent)
                            },
                            onMessageCrew = onMessageCrew
                        )
                    }
                    DashboardModule.DISPATCH -> {
                        // Show jobs that have no crew assigned
                        val unassigned = activeJobs.filter { it.crew.isEmpty() && it.stage != JobStage.CLOSED }
                        DispatchModule(
                            unassignedJobs = unassigned,
                            onAssignCrew = { job -> assigningJob = job },
                            onJobClick = onJobClick
                        )
                    }
                    DashboardModule.PROJECT_OVERVIEW -> ProjectOverviewModule()
                    DashboardModule.SITE_MAP -> SiteMapModule(onMapClick = onMap, allJobs = jobs)
                    DashboardModule.FINANCIALS -> {
                        FinancialsModule(allJobs = jobs)
                    }
                    DashboardModule.HUB_STATUS -> HubStatusModule()

                    DashboardModule.JOBS_PANEL -> {
                        // ── JOBS (dominant module) ─────────────────────
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ConsoleTheme.surface, RoundedCornerShape(4.dp))
                                .border(0.5.dp, ConsoleTheme.text.copy(alpha = 0.06f), RoundedCornerShape(4.dp))
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("JOBS", style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.textMuted))
                                    Text("${viewModel.getActiveJobCount()} active", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
                                    val outstanding = viewModel.getOutstandingTotal()
                                    if (outstanding > 0 && (RoleContext.can(Permission.VIEW_FINANCIALS) || RoleContext.isSolo())) {
                                        Text("· $${String.format("%.0f", outstanding)} owed", style = ConsoleTheme.caption.copy(color = ConsoleTheme.accent))
                                    }
                                }
                                if (RoleContext.can(Permission.MANAGE_JOBS)) {
                                    Text(
                                        text = "[+ NEW]",
                                        style = ConsoleTheme.action.copy(color = ConsoleTheme.accent),
                                        modifier = Modifier.clickable { onNewJob() }.padding(2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            val displayJobs = if (selectedDay != null) viewModel.getJobsForDay(selectedDay!!) else viewModel.getPrioritizedJobs()
                            val visibleJobs = if (selectedDay != null) displayJobs else displayJobs.take(3)

                            for (slot in 0 until 3) {
                                val job = visibleJobs.getOrNull(slot)
                                if (job != null) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp)
                                            .clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = rememberRipple(bounded = true),
                                                onClick = { onJobClick(job.id) }
                                            )
                                            .padding(vertical = 8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "${job.stage.icon} ${job.clientName ?: job.title}",
                                                style = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.text)
                                            )
                                            val timeContext = formatJobTimeContext(job)
                                            val addressShort = job.clientAddress.take(25)
                                            val moneyContext = if (job.stage == JobStage.INVOICE) {
                                                val invoiceTotal = job.materials.sumOf { it.totalCost } + (job.hourlyRate * 8)
                                                " · $${String.format("%.0f", invoiceTotal)}"
                                            } else ""
                                            Text(
                                                text = "${job.stage.displayName} · $addressShort · $timeContext$moneyContext",
                                                style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted),
                                                maxLines = 1, overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        Text(">", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted), modifier = Modifier.padding(start = 8.dp))
                                    }
                                } else if (slot == 0 && activeJobs.isEmpty()) {
                                    Box(modifier = Modifier.fillMaxWidth().height(52.dp).padding(vertical = 8.dp), contentAlignment = Alignment.CenterStart) {
                                        Text("No active jobs. Tap [+ NEW] to get started.", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
                                    }
                                } else {
                                    Spacer(modifier = Modifier.fillMaxWidth().height(52.dp))
                                }
                                if (slot < 2) {
                                    Box(Modifier.fillMaxWidth().height(0.5.dp).background(ConsoleTheme.text.copy(alpha = 0.06f)))
                                }
                            }

                            if (selectedDay != null) {
                                Text("[Clear filter]", style = ConsoleTheme.action.copy(color = ConsoleTheme.accent), modifier = Modifier.clickable { selectedDay = null }.padding(vertical = 6.dp))
                            } else if (displayJobs.size > 3) {
                                Text("[See all ${displayJobs.size} jobs >]", style = ConsoleTheme.action.copy(color = ConsoleTheme.accent), modifier = Modifier.clickable { onJobBoard() }.padding(vertical = 6.dp))
                            }
                        }
                    }

                    DashboardModule.GETTING_STARTED -> {
                        val needsProfile = !com.guildofsmiths.trademesh.data.UserPreferences.isOnboardingDataComplete()
                        val needsJob = !viewModel.hasAnyJobs() && RoleContext.can(Permission.MANAGE_JOBS)
                        if (needsProfile || needsJob) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(ConsoleTheme.surface, RoundedCornerShape(4.dp))
                                    .border(0.5.dp, ConsoleTheme.text.copy(alpha = 0.06f), RoundedCornerShape(4.dp))
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text("GETTING STARTED", style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.textMuted))
                                if (needsProfile) GettingStartedRow("[Set up profile]", onProfile)
                                if (needsJob) GettingStartedRow("[Create first job]", onNewJob)
                            }
                        }
                    }

                    DashboardModule.PROGRESS -> {
                        val completedCount = viewModel.getCompletedThisMonth()
                        val totalCount = viewModel.getTotalThisMonth()
                        val earned = viewModel.getEarnedThisMonth()
                        val owed = viewModel.getOutstandingTotal()
                        val spent = viewModel.getSpentToDate()
                        val minutesWorked = viewModel.getMinutesWorkedToDate()
                        @Suppress("UNUSED_EXPRESSION") nowMs
                        val minutesToday = viewModel.getMinutesWorkedToday()

                        if (completedCount > 0 || earned > 0 || spent > 0 || minutesToday > 0) {
                            val progress = if (totalCount > 0) completedCount.toFloat() / totalCount else 0f
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(ConsoleTheme.surface, RoundedCornerShape(4.dp))
                                    .border(0.5.dp, ConsoleTheme.text.copy(alpha = 0.06f), RoundedCornerShape(4.dp))
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = rememberRipple(bounded = true),
                                        onClick = { onReport() }
                                    )
                                    .padding(14.dp)
                            ) {
                                Text("PROGRESS", style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.textMuted))
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Box(Modifier.weight(1f).height(6.dp).background(ConsoleTheme.text.copy(alpha = 0.06f), RoundedCornerShape(3.dp))) {
                                        Box(Modifier.fillMaxHeight().fillMaxWidth(progress).background(ConsoleTheme.accent, RoundedCornerShape(3.dp)))
                                    }
                                    Text("$completedCount/$totalCount this month", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                val commonJobs = viewModel.getCommonJobTypes()
                                if (commonJobs.isNotEmpty()) {
                                    Text("Common: ${commonJobs.joinToString(", ")}", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
                                }
                                if (earned > 0 || owed > 0) {
                                    Text("$${String.format("%.0f", earned)} earned · $${String.format("%.0f", owed)} owed", style = ConsoleTheme.caption.copy(color = ConsoleTheme.accent))
                                }
                                // Cumulative "spent" — labor hours × rate + materials
                                // across every job, regardless of stage. Surfaces work
                                // that hasn't been formally invoiced/closed yet.
                                if (spent > 0 || minutesWorked > 0) {
                                    val h = minutesWorked / 60
                                    val m = minutesWorked % 60
                                    val hoursLabel = if (h > 0) "${h}h ${m}m" else "${m}m"
                                    Text(
                                        "$${String.format("%.0f", spent)} spent · $hoursLabel worked",
                                        style = ConsoleTheme.caption.copy(color = ConsoleTheme.text)
                                    )
                                }
                                if (minutesToday > 0) {
                                    val h = minutesToday / 60
                                    val m = minutesToday % 60
                                    val todayLabel = if (h > 0) "${h}h ${m}m" else "${m}m"
                                    Text(
                                        "Today: $todayLabel",
                                        style = ConsoleTheme.caption.copy(color = ConsoleTheme.success)
                                    )
                                }
                            }
                        }
                    }

                    DashboardModule.CALENDAR -> {
                        MonthCalendar(
                            scheduledDays = viewModel.getScheduledDays(),
                            selectedDay = selectedDay,
                            onDayClick = { day -> selectedDay = if (selectedDay == day) null else day }
                        )
                    }

                    DashboardModule.QUICK_ACTIONS -> {
                        val rows = quickActions.chunked(2)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            rows.forEach { row ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    row.forEach { action ->
                                        QuickAction(action.label, Modifier.weight(1f), action.onClick)
                                    }
                                    if (row.size == 1) Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }

                    DashboardModule.ACTIVITY_LOG -> {
                        val todayActivity = viewModel.getTodayActivity().take(3)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(ConsoleTheme.surface, RoundedCornerShape(4.dp))
                                .border(0.5.dp, ConsoleTheme.text.copy(alpha = 0.06f), RoundedCornerShape(4.dp))
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text("TODAY", style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.textMuted))
                            if (todayActivity.isEmpty()) {
                                Text("No activity yet today.", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted), modifier = Modifier.padding(vertical = 4.dp))
                            } else {
                                todayActivity.forEach { event ->
                                    Text(
                                        text = event.description,
                                        style = ConsoleTheme.caption.copy(color = ConsoleTheme.text),
                                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .then(if (event.jobId != null) Modifier.clickable { onJobClick(event.jobId) } else Modifier)
                                            .padding(vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// MONTH CALENDAR — full month grid with day selection
// ════════════════════════════════════════════════════════════════════

@Composable
private fun MonthCalendar(
    scheduledDays: Set<Int>,
    selectedDay: Int?,
    onDayClick: (Int) -> Unit
) {
    val cal = java.util.Calendar.getInstance()
    val todayOfMonth = cal.get(java.util.Calendar.DAY_OF_MONTH)

    // Month info
    val monthCal = java.util.Calendar.getInstance()
    monthCal.set(java.util.Calendar.DAY_OF_MONTH, 1)
    val firstDayOfWeek = monthCal.get(java.util.Calendar.DAY_OF_WEEK) - 1 // 0=Sun
    val daysInMonth = monthCal.getActualMaximum(java.util.Calendar.DAY_OF_MONTH)
    val monthName = java.text.SimpleDateFormat("MMM yyyy", java.util.Locale.US)
        .format(monthCal.time).uppercase()

    val dayNames = listOf("S", "M", "T", "W", "T", "F", "S")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ConsoleTheme.surface, RoundedCornerShape(4.dp))
            .border(0.5.dp, ConsoleTheme.text.copy(alpha = 0.06f), RoundedCornerShape(4.dp))
            .padding(14.dp)
    ) {
        // Header: SCHEDULE + month label
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("SCHEDULE", style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.textMuted))
            Text(monthName, style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Day name headers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            dayNames.forEach { name ->
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(name, style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Calendar grid
        val totalCells = firstDayOfWeek + daysInMonth
        val rows = (totalCells + 6) / 7

        for (row in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth().height(32.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                for (col in 0..6) {
                    val cellIndex = row * 7 + col
                    val dayNum = cellIndex - firstDayOfWeek + 1

                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        if (dayNum in 1..daysInMonth) {
                            val isToday = dayNum == todayOfMonth
                            val hasJob = scheduledDays.contains(dayNum)
                            val isSelected = dayNum == selectedDay

                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .then(
                                        when {
                                            isSelected -> Modifier.background(ConsoleTheme.accent, CircleShape)
                                            isToday && hasJob -> Modifier.background(ConsoleTheme.accent, CircleShape)
                                            isToday -> Modifier.border(1.5.dp, ConsoleTheme.accent, CircleShape)
                                            hasJob -> Modifier.background(ConsoleTheme.accent.copy(alpha = 0.10f), CircleShape)
                                            else -> Modifier
                                        }
                                    )
                                    .clickable { onDayClick(dayNum) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "$dayNum",
                                    style = ConsoleTheme.caption.copy(
                                        color = when {
                                            isSelected -> Color.White
                                            isToday && hasJob -> Color.White
                                            isToday -> ConsoleTheme.accent
                                            hasJob -> ConsoleTheme.accent
                                            else -> ConsoleTheme.textMuted
                                        }
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// QUICK ACTION BUTTON (Fix 5: ripple)
// ════════════════════════════════════════════════════════════════════

@Composable
private fun QuickAction(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .background(ConsoleTheme.surface, RoundedCornerShape(4.dp))
            .border(0.5.dp, ConsoleTheme.text.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
            .clip(RoundedCornerShape(4.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(bounded = true, color = ConsoleTheme.accent),
                onClick = onClick
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = ConsoleTheme.action.copy(color = ConsoleTheme.text))
    }
}

// ════════════════════════════════════════════════════════════════════
// GETTING STARTED ROW (Fix 9)
// ════════════════════════════════════════════════════════════════════

@Composable
private fun GettingStartedRow(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        style = ConsoleTheme.action.copy(color = ConsoleTheme.accent),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(bounded = true, color = ConsoleTheme.accent),
                onClick = onClick
            )
            .padding(vertical = 8.dp, horizontal = 4.dp)
    )
}

// ════════════════════════════════════════════════════════════════════
// HELPERS
// ════════════════════════════════════════════════════════════════════

private fun formatTimeAgo(timestamp: Long): String {
    val diff = System.currentTimeMillis() - timestamp
    val minutes = diff / 60_000
    val hours = diff / 3_600_000
    val days = diff / 86_400_000
    return when {
        minutes < 1 -> "now"
        minutes < 60 -> "${minutes}m"
        hours < 24 -> "${hours}h"
        else -> "${days}d"
    }
}

private fun formatJobTimeContext(job: Job): String {
    val diff = System.currentTimeMillis() - job.updatedAt
    val days = diff / 86_400_000
    val hours = diff / 3_600_000
    return when {
        job.stage == JobStage.IN_PROGRESS && job.createdAt > 0 -> {
            val elapsed = (System.currentTimeMillis() - job.createdAt) / 86_400_000
            "Day ${elapsed + 1}"
        }
        hours < 1 -> "updated now"
        hours < 24 -> "${hours}h ago"
        else -> "${days}d ago"
    }
}
