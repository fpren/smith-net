package com.guildofsmiths.trademesh.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.guildofsmiths.trademesh.ui.ConsoleTheme
import com.guildofsmiths.trademesh.ui.ConsoleSeparator
import com.guildofsmiths.trademesh.ui.jobboard.Job
import com.guildofsmiths.trademesh.ui.jobboard.JobStage
import com.guildofsmiths.trademesh.data.RoleContext
import com.guildofsmiths.trademesh.data.Permission
import com.guildofsmiths.trademesh.data.BeaconRepository
import com.guildofsmiths.trademesh.data.PeerRepository
import com.guildofsmiths.trademesh.data.UserPreferences
import com.guildofsmiths.trademesh.service.ChatManager
import com.guildofsmiths.trademesh.service.ConnectionMode

@Composable
private fun ActionButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        text = label,
        style = ConsoleTheme.action,
        modifier = modifier
            .clickable { onClick() }
            .background(ConsoleTheme.surface)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
fun DashboardScreen(
    jobs: List<Job>,
    onJobClick: (String) -> Unit,
    onNewJob: () -> Unit,
    onClockIn: () -> Unit,
    onMessages: () -> Unit,
    onSettings: () -> Unit,
    onProfile: () -> Unit,
    onArchive: () -> Unit,
    viewModel: DashboardViewModel = viewModel()
) {
    LaunchedEffect(jobs) { viewModel.loadJobs(jobs) }

    val alerts by viewModel.alerts.collectAsState()
    val activeJobs by viewModel.jobs.collectAsState()
    val isClockedIn by viewModel.isClockedIn

    // Connection mode for dot indicator
    val connectionMode by ChatManager.connectionMode.collectAsState()

    // Unread count: sum unreadCount across all channels in all beacons
    val beacons by BeaconRepository.beacons.collectAsState()
    val totalUnreads = remember(beacons) {
        beacons.flatMap { it.channels }.sumOf { it.unreadCount }
    }

    // Active peers for crew panel
    val allPeers by PeerRepository.peers.collectAsState()
    val activePeers = remember(allPeers) {
        allPeers.values.filter { it.isActive() }.sortedByDescending { it.lastSeen }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ConsoleTheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        // ── Header ─────────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = viewModel.getBusinessName(), style = ConsoleTheme.title)
                Text(
                    text = UserPreferences.getUserName(),
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.accent),
                    modifier = Modifier.clickable { onProfile() }.padding(vertical = 2.dp)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Connection dot
                val dotColor = when (connectionMode) {
                    ConnectionMode.ONLINE  -> ConsoleTheme.success
                    ConnectionMode.MESH    -> ConsoleTheme.accent
                    ConnectionMode.OFFLINE -> ConsoleTheme.error
                }
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                // Msg badge
                val msgStyle = if (totalUnreads > 0)
                    ConsoleTheme.action.copy(color = ConsoleTheme.accent)
                else
                    ConsoleTheme.action.copy(color = ConsoleTheme.textMuted)
                val msgLabel = if (totalUnreads > 0) "[Msg $totalUnreads]" else "[Msg]"
                Text(
                    text = msgLabel,
                    style = msgStyle,
                    modifier = Modifier.clickable { onMessages() }.padding(4.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "[⚙]",
                    style = ConsoleTheme.action,
                    modifier = Modifier.clickable { onSettings() }.padding(4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        ConsoleSeparator()

        // ── Clock Status Strip ─────────────────────────────────────────
        Spacer(modifier = Modifier.height(8.dp))
        if (isClockedIn) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleTheme.success.copy(alpha = 0.08f))
                    .clickable { onClockIn() }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "[● ON CLOCK]",
                    style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.success)
                )
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleTheme.surface)
                    .clickable { onClockIn() }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "[○ OFF CLOCK]",
                    style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.textMuted)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Needs Attention ────────────────────────────────────────────
        if (alerts.isNotEmpty()) {
            Text(text = "NEEDS ATTENTION", style = ConsoleTheme.captionBold)
            Spacer(modifier = Modifier.height(8.dp))
            alerts.forEach { alert ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ConsoleTheme.surface)
                        .clickable { onJobClick(alert.jobId) }
                        .padding(12.dp)
                ) {
                    Text(
                        text = "! ",
                        style = ConsoleTheme.bodyBold,
                        color = ConsoleTheme.warning
                    )
                    Text(text = alert.message, style = ConsoleTheme.bodySmall)
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // ── Active Jobs ────────────────────────────────────────────────
        Text(text = "JOBS", style = ConsoleTheme.captionBold)
        Spacer(modifier = Modifier.height(8.dp))

        if (activeJobs.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleTheme.surface)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No active jobs.\nTap [+ NEW JOB] to get started.",
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                )
            }
        } else {
            activeJobs.forEach { job ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(ConsoleTheme.surface)
                        .clickable { onJobClick(job.id) }
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${job.stage.icon} ${job.clientName ?: job.title}",
                            style = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.text)
                        )
                        Text(
                            text = "${job.stage.displayName} · ${job.clientAddress.take(30)}",
                            style = ConsoleTheme.caption
                        )
                    }
                    Text(text = ">", style = ConsoleTheme.body, color = ConsoleTheme.textMuted)
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Quick Action Grid ──────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (RoleContext.can(Permission.MANAGE_JOBS)) {
                    ActionButton("[+ NEW JOB]") { onNewJob() }
                }
                ActionButton("[CLOCK IN]") { onClockIn() }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ActionButton("[MESSAGES]") { onMessages() }
                if (RoleContext.can(Permission.VIEW_FINANCIALS) || RoleContext.isSolo()) {
                    ActionButton("[INVOICES]") { onArchive() }
                }
            }
            if (RoleContext.can(Permission.MANAGE_CREW)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ActionButton("[CREW]") { /* future: navigate to crew screen */ }
                    ActionButton("[DISPATCH]") { /* future: navigate to dispatch screen */ }
                }
            }
        }

        // ── Crew Status Panel (foreman only) ───────────────────────────
        if (RoleContext.isForeman()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text("CREW", style = ConsoleTheme.captionBold)
            Spacer(modifier = Modifier.height(8.dp))

            if (activePeers.isEmpty()) {
                Text("No crew members connected", style = ConsoleTheme.caption)
            } else {
                activePeers.forEach { peer ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row {
                            Text(
                                text = "● ",
                                style = ConsoleTheme.caption.copy(color = ConsoleTheme.success)
                            )
                            Text(text = peer.userName, style = ConsoleTheme.bodySmall)
                        }
                        Text(text = "connected", style = ConsoleTheme.caption)
                    }
                }
            }
        }

        // ── Stats ──────────────────────────────────────────────────────
        Spacer(modifier = Modifier.height(16.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "${viewModel.getActiveJobCount()}", style = ConsoleTheme.bodyBold)
                Text(text = "Active", style = ConsoleTheme.caption)
            }
            if (RoleContext.can(Permission.VIEW_FINANCIALS) || RoleContext.isSolo()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$${String.format("%.0f", viewModel.getOutstandingTotal())}",
                        style = ConsoleTheme.bodyBold
                    )
                    Text(text = "Outstanding", style = ConsoleTheme.caption)
                }
            }
        }

        // ── Today Summary ──────────────────────────────────────────────
        Spacer(modifier = Modifier.height(16.dp))
        ConsoleSeparator()
        Spacer(modifier = Modifier.height(8.dp))

        val today = java.text.SimpleDateFormat("MMM d", java.util.Locale.US).format(java.util.Date())
        val blockedCount = activeJobs.count { it.stage == JobStage.BLOCKED }
        Text("TODAY · $today", style = ConsoleTheme.captionBold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${viewModel.getActiveJobCount()} jobs active · $blockedCount blocked",
            style = ConsoleTheme.caption
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ── Archive link ───────────────────────────────────────────────
        Text(
            text = "[Archive]",
            style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted),
            modifier = Modifier.clickable { onArchive() }.padding(4.dp)
        )
    }
}
