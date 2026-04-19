package com.guildofsmiths.trademesh.ui.dispatch

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.guildofsmiths.trademesh.data.ClockStatus
import com.guildofsmiths.trademesh.data.CrewPresenceInfo
import com.guildofsmiths.trademesh.data.CrewPresenceRepository
import com.guildofsmiths.trademesh.ui.ConsoleHeader
import com.guildofsmiths.trademesh.ui.ConsoleSeparator
import com.guildofsmiths.trademesh.ui.ConsoleTheme
import com.guildofsmiths.trademesh.ui.dashboard.CrewAssignDialog
import com.guildofsmiths.trademesh.ui.dashboard.CrewProfileSheet
import com.guildofsmiths.trademesh.ui.jobboard.Job
import com.guildofsmiths.trademesh.ui.jobboard.JobBoardViewModel
import com.guildofsmiths.trademesh.ui.jobboard.JobStage

@Composable
fun DispatchScreen(
    onBack: () -> Unit,
    onJobClick: (String) -> Unit = {},
    onCallPhone: (String) -> Unit = {},
    onMessageCrew: ((CrewPresenceInfo) -> Unit)? = null,
    viewModel: JobBoardViewModel = viewModel()
) {
    val allJobs by viewModel.jobs.collectAsState()
    val crew = remember { CrewPresenceRepository.getCrew() }

    var assigningJob by remember { mutableStateOf<Job?>(null) }
    var selectedCrew by remember { mutableStateOf<CrewPresenceInfo?>(null) }

    // Split jobs
    val activeJobs = allJobs.filter { it.stage != JobStage.CLOSED }
    val unassignedJobs = activeJobs.filter { it.crew.isEmpty() }
    val assignedJobs = activeJobs.filter { it.crew.isNotEmpty() }

    // Crew assignment dialog
    if (assigningJob != null) {
        CrewAssignDialog(
            job = assigningJob!!,
            onAssign = { job, crewMember ->
                viewModel.assignCrewToJob(job.id, crewMember)
                assigningJob = null
            },
            onDismiss = { assigningJob = null }
        )
    }

    // Crew profile sheet
    if (selectedCrew != null) {
        CrewProfileSheet(
            member = selectedCrew!!,
            onDismiss = { selectedCrew = null },
            onCall = onCallPhone,
            onMessage = onMessageCrew
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ConsoleTheme.background)
    ) {
        ConsoleHeader(title = "DISPATCH", onBackClick = onBack)
        ConsoleSeparator()

        // Stats bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DispatchStat(Modifier.weight(1f), "UNASSIGNED", unassignedJobs.size, Color(0xFFD97706))
            DispatchStat(Modifier.weight(1f), "ASSIGNED", assignedJobs.size, ConsoleTheme.success)
            DispatchStat(Modifier.weight(1f), "CREW", crew.size, ConsoleTheme.accent)
        }

        ConsoleSeparator()

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── UNASSIGNED JOBS ────────────────────────────────
            if (unassignedJobs.isNotEmpty()) {
                item {
                    Text(
                        "UNASSIGNED (${unassignedJobs.size})",
                        style = ConsoleTheme.captionBold.copy(color = Color(0xFFD97706)),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                items(unassignedJobs, key = { it.id }) { job ->
                    DispatchJobCard(
                        job = job,
                        isUnassigned = true,
                        onJobClick = { onJobClick(job.id) },
                        onAssign = { assigningJob = job }
                    )
                }

                item { Spacer(modifier = Modifier.height(8.dp)) }
            }

            // ── ASSIGNED JOBS ─────────────────────────────────
            if (assignedJobs.isNotEmpty()) {
                item {
                    Text(
                        "ASSIGNED (${assignedJobs.size})",
                        style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.success),
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
                items(assignedJobs, key = { it.id }) { job ->
                    DispatchJobCard(
                        job = job,
                        isUnassigned = false,
                        onJobClick = { onJobClick(job.id) },
                        onAssign = { assigningJob = job }
                    )
                }

                item { Spacer(modifier = Modifier.height(8.dp)) }
            }

            // ── CREW ROSTER ───────────────────────────────────
            item {
                Box(Modifier.fillMaxWidth().height(0.5.dp).background(ConsoleTheme.text.copy(alpha = 0.08f)))
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "CREW ROSTER",
                    style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.textMuted),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            items(crew, key = { it.id }) { member ->
                CrewRosterCard(
                    member = member,
                    assignedJob = allJobs.find { it.id == member.currentJobId },
                    onClick = { selectedCrew = member }
                )
            }

            // Empty state
            if (activeJobs.isEmpty()) {
                item {
                    Text(
                        "No active jobs to dispatch.",
                        style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted),
                        modifier = Modifier.padding(vertical = 16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DispatchStat(
    modifier: Modifier,
    label: String,
    count: Int,
    color: Color
) {
    Column(
        modifier = modifier
            .background(ConsoleTheme.surface, RoundedCornerShape(4.dp))
            .border(0.5.dp, ConsoleTheme.text.copy(alpha = 0.06f), RoundedCornerShape(4.dp))
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(count.toString(), style = ConsoleTheme.bodyBold.copy(color = color))
        Text(label, style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
    }
}

@Composable
private fun DispatchJobCard(
    job: Job,
    isUnassigned: Boolean,
    onJobClick: () -> Unit,
    onAssign: () -> Unit
) {
    val borderColor = if (isUnassigned) Color(0xFFD97706).copy(alpha = 0.25f) else ConsoleTheme.text.copy(alpha = 0.06f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ConsoleTheme.surface, RoundedCornerShape(4.dp))
            .border(0.5.dp, borderColor, RoundedCornerShape(4.dp))
            .clip(RoundedCornerShape(4.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(bounded = true),
                onClick = onJobClick
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (isUnassigned) "! ${job.clientName ?: job.title}" else "${job.clientName ?: job.title}",
                style = ConsoleTheme.bodySmall.copy(
                    color = if (isUnassigned) ConsoleTheme.text else ConsoleTheme.text
                ),
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                "${job.stage.displayName} · ${job.clientAddress.take(30)}",
                style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
            )

            // Show assigned crew
            if (job.crew.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    job.crew.forEach { member ->
                        Text(
                            "(${member.name})",
                            style = ConsoleTheme.caption.copy(color = ConsoleTheme.success)
                        )
                    }
                }
            }
        }

        Text(
            if (isUnassigned) "[Assign]" else "[+ Crew]",
            style = ConsoleTheme.action.copy(
                color = if (isUnassigned) Color(0xFFD97706) else ConsoleTheme.accent
            ),
            modifier = Modifier
                .clickable { onAssign() }
                .padding(start = 8.dp, top = 2.dp)
        )
    }
}

@Composable
private fun CrewRosterCard(
    member: CrewPresenceInfo,
    assignedJob: Job?,
    onClick: () -> Unit
) {
    val dot = when (member.status) {
        ClockStatus.ON_CLOCK -> "●"
        ClockStatus.ON_BREAK -> "◐"
        ClockStatus.OFF_CLOCK -> "○"
    }
    val dotColor = when (member.status) {
        ClockStatus.ON_CLOCK -> ConsoleTheme.success
        ClockStatus.ON_BREAK -> ConsoleTheme.accent
        ClockStatus.OFF_CLOCK -> ConsoleTheme.textMuted
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ConsoleTheme.surface, RoundedCornerShape(4.dp))
            .border(0.5.dp, ConsoleTheme.text.copy(alpha = 0.06f), RoundedCornerShape(4.dp))
            .clip(RoundedCornerShape(4.dp))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = rememberRipple(bounded = true),
                onClick = onClick
            )
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Text(dot, style = ConsoleTheme.bodySmall.copy(color = dotColor))
            Column {
                Text(member.name, style = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.text))
                Text(
                    buildString {
                        append(member.trade)
                        if (member.currentJobTitle != null) append(" · ${member.currentJobTitle}")
                        else append(" · available")
                    },
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Hours
        if (member.clockInTime != null) {
            val mins = (System.currentTimeMillis() - member.clockInTime) / 60_000
            Text("${mins / 60}h ${mins % 60}m", style = ConsoleTheme.caption.copy(color = ConsoleTheme.accent))
        } else {
            Text(member.status.label, style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
        }
    }
}
