package com.guildofsmiths.trademesh.ui.dashboard

import android.view.ViewGroup
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import com.guildofsmiths.trademesh.ai.AISupervisor
import com.guildofsmiths.trademesh.data.ClockStatus
import com.guildofsmiths.trademesh.data.CrewPresenceInfo
import com.guildofsmiths.trademesh.data.CrewPresenceRepository
import com.guildofsmiths.trademesh.data.Permission
import com.guildofsmiths.trademesh.data.RoleContext
import com.guildofsmiths.trademesh.ui.ConsoleTheme
import com.guildofsmiths.trademesh.ui.jobboard.Job
import com.guildofsmiths.trademesh.ui.jobboard.JobStage

// ════════════════════════════════════════════════════════════════════
// MY TASKS MODULE — TEAM_MEMBER primary surface
// ════════════════════════════════════════════════════════════════════

@Composable
fun MyTasksModule() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ConsoleTheme.surface, RoundedCornerShape(4.dp))
            .border(0.5.dp, ConsoleTheme.text.copy(alpha = 0.06f), RoundedCornerShape(4.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("TODAY'S TASKS", style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.textMuted))
        Spacer(modifier = Modifier.height(4.dp))

        // Placeholder — will be populated when task assignment is implemented
        Text(
            text = "No tasks assigned yet.",
            style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted),
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Text(
            text = "Tasks will appear here when your team lead or dispatcher assigns work.",
            style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
        )
    }
}

// ════════════════════════════════════════════════════════════════════
// TEAM PRESENCE MODULE — TEAM_LEAD, FOREMAN, GC
// ════════════════════════════════════════════════════════════════════

@Composable
fun TeamPresenceModule(
    onCrewClick: () -> Unit = {},
    onMapClick: () -> Unit = {},
    onDispatchClick: () -> Unit = {},
    onCallPhone: (String) -> Unit = {},
    onMessageCrew: ((CrewPresenceInfo) -> Unit)? = null
) {
    val isExpanded = RoleContext.isForeman()
    val isGC = RoleContext.isGC()
    val crew = remember { CrewPresenceRepository.getCrewWithAI() }
    val activeCount = crew.count { it.status == ClockStatus.ON_CLOCK && it.id != "smith-ai" }

    // Crew profile sheet state
    var selectedCrewMember by remember { mutableStateOf<CrewPresenceInfo?>(null) }

    if (selectedCrewMember != null) {
        CrewProfileSheet(
            member = selectedCrewMember!!,
            onDismiss = { selectedCrewMember = null },
            onCall = onCallPhone,
            onMessage = onMessageCrew
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ConsoleTheme.surface, RoundedCornerShape(4.dp))
            .border(0.5.dp, ConsoleTheme.text.copy(alpha = 0.06f), RoundedCornerShape(4.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isGC) "SITES & SUBS" else if (isExpanded) "CREW STATUS" else "CREW",
                style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.textMuted)
            )
            Text(
                text = "$activeCount/${crew.size} active",
                style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (isGC) {
            // GC variant: grouped by site
            val bySite = crew.filter { it.currentSite != null }.groupBy { it.currentSite!! }
            val unassigned = crew.filter { it.currentSite == null }

            if (bySite.isEmpty() && unassigned.isEmpty()) {
                Text("No crew members connected.", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
            }

            bySite.forEach { (site, members) ->
                val siteActive = members.count { it.status == ClockStatus.ON_CLOCK }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(">", style = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.accent))
                        Text(site.take(30), style = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.text))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        members.forEach { m ->
                            val dot = if (m.status == ClockStatus.ON_CLOCK) "●" else "○"
                            val dotColor = if (m.status == ClockStatus.ON_CLOCK) ConsoleTheme.success else ConsoleTheme.textMuted
                            Text(dot, style = ConsoleTheme.caption.copy(color = dotColor))
                        }
                        Text("$siteActive/${members.size}", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
                    }
                }
                val trades = members.joinToString(", ") { it.name }
                Text("  $trades", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
            }
        } else {
            // Team Lead (compact) / Foreman (expanded) variant
            val sorted = crew.sortedWith(compareBy { if (it.status == ClockStatus.ON_CLOCK) 0 else 1 })
            val visible = if (!isExpanded) sorted.take(3) else sorted

            visible.forEach { member ->
                val isAI = member.id == "smith-ai"
                val dot = when {
                    isAI -> "◈"
                    member.status == ClockStatus.ON_CLOCK -> "●"
                    member.status == ClockStatus.ON_BREAK -> "◐"
                    else -> "○"
                }
                val dotColor = when {
                    isAI -> ConsoleTheme.accent
                    member.status == ClockStatus.ON_CLOCK -> ConsoleTheme.success
                    member.status == ClockStatus.ON_BREAK -> ConsoleTheme.accent
                    else -> ConsoleTheme.textMuted
                }
                val rightText = when {
                    isAI -> "Always on"
                    member.clockInTime != null -> {
                        val mins = (System.currentTimeMillis() - member.clockInTime) / 60_000
                        "${mins / 60}h ${mins % 60}m"
                    }
                    else -> {
                        val ago = (System.currentTimeMillis() - member.lastSeen) / 3_600_000
                        if (ago < 24) "${ago}h ago" else "yesterday"
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .then(
                            if (!isAI) Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = rememberRipple(bounded = true),
                                onClick = { selectedCrewMember = member }
                            ) else Modifier
                        )
                        .padding(vertical = 2.dp, horizontal = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "($dot) ${member.name}${if (isAI) " · ${member.trade}" else ""}",
                        style = ConsoleTheme.caption.copy(color = if (isAI) ConsoleTheme.accent else if (member.status == ClockStatus.OFF_CLOCK) ConsoleTheme.textMuted else ConsoleTheme.text)
                    )
                    Text(rightText, style = ConsoleTheme.caption.copy(color = if (isAI || member.status == ClockStatus.ON_CLOCK) ConsoleTheme.accent else ConsoleTheme.textMuted))
                }
            }

            if (!isExpanded && sorted.size > 3) {
                Text(
                    text = "[See all ${sorted.size} >]",
                    style = ConsoleTheme.action.copy(color = ConsoleTheme.accent),
                    modifier = Modifier.clickable { onCrewClick() }.padding(vertical = 4.dp)
                )
            }
        }

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.End
        ) {
            if (isExpanded) {
                Text("[Dispatch]", style = ConsoleTheme.action.copy(color = ConsoleTheme.accent), modifier = Modifier.clickable { onDispatchClick() }.padding(4.dp))
            }
            if (isGC) {
                Text("[Projects]", style = ConsoleTheme.action.copy(color = ConsoleTheme.accent), modifier = Modifier.clickable { onCrewClick() }.padding(4.dp))
            }
        }

        // Inline map — minimal site summary + OpenStreetMap
        if (isExpanded || isGC) {
            Spacer(modifier = Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(ConsoleTheme.text.copy(alpha = 0.06f)))
            Spacer(modifier = Modifier.height(8.dp))

            // Minimal site list — just name + dots + count
            val bySite = crew.filter { it.currentSite != null }.groupBy { it.currentSite!! }
            bySite.forEach { (site, members) ->
                val siteActive = members.count { it.status == ClockStatus.ON_CLOCK }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(site.take(30), style = ConsoleTheme.caption.copy(color = ConsoleTheme.text), modifier = Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        members.forEach { m ->
                            val dc = when (m.status) {
                                ClockStatus.ON_CLOCK -> ConsoleTheme.success
                                ClockStatus.ON_BREAK -> ConsoleTheme.accent
                                ClockStatus.OFF_CLOCK -> ConsoleTheme.textMuted
                            }
                            Text("●", style = ConsoleTheme.caption.copy(color = dc))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("$siteActive/${members.size}", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // OpenStreetMap
            CrewMapView(crew = crew)
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// DISPATCH MODULE — FOREMAN only (visually distinct from Jobs panel)
// ════════════════════════════════════════════════════════════════════

@Composable
fun DispatchModule(
    unassignedJobs: List<Job> = emptyList(),
    onAssignCrew: (Job) -> Unit = {},
    onJobClick: (String) -> Unit = {}
) {
    val urgencyColor = if (unassignedJobs.isNotEmpty()) Color(0xFFD97706) else ConsoleTheme.textMuted

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ConsoleTheme.surface, RoundedCornerShape(4.dp))
            .border(1.dp, urgencyColor.copy(alpha = 0.25f), RoundedCornerShape(4.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Header with urgency accent
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(">>", style = ConsoleTheme.bodySmall.copy(color = urgencyColor))
                Text("DISPATCH", style = ConsoleTheme.captionBold.copy(color = urgencyColor))
                if (unassignedJobs.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .background(urgencyColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "${unassignedJobs.size} unassigned",
                            style = ConsoleTheme.caption.copy(color = urgencyColor)
                        )
                    }
                }
            }
        }

        if (unassignedJobs.isEmpty()) {
            Text("All jobs assigned.", style = ConsoleTheme.caption.copy(color = ConsoleTheme.success), modifier = Modifier.padding(vertical = 4.dp))
        } else {
            unassignedJobs.take(4).forEach { job ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(urgencyColor.copy(alpha = 0.04f), RoundedCornerShape(4.dp))
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = rememberRipple(bounded = true),
                            onClick = { onJobClick(job.id) }
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "! ${job.clientName ?: job.title}",
                            style = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.text)
                        )
                        Text(
                            "${job.stage.displayName} · ${job.clientAddress.take(25)}",
                            style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                        )
                    }
                    Text(
                        "[Assign]",
                        style = ConsoleTheme.action.copy(color = urgencyColor),
                        modifier = Modifier
                            .clickable { onAssignCrew(job) }
                            .background(urgencyColor.copy(alpha = 0.10f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// CREW ASSIGNMENT DIALOG — pick crew member to assign to job
// ════════════════════════════════════════════════════════════════════

@Composable
fun CrewAssignDialog(
    job: Job,
    onAssign: (Job, CrewPresenceInfo) -> Unit,
    onDismiss: () -> Unit
) {
    val crew = remember { CrewPresenceRepository.getCrew() }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(ConsoleTheme.background, RoundedCornerShape(8.dp))
                .border(1.dp, ConsoleTheme.text.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("ASSIGN CREW", style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.accent))
            Text(
                job.clientName ?: job.title,
                style = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.text)
            )
            Text(
                job.clientAddress.take(40),
                style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
            )

            Box(Modifier.fillMaxWidth().height(0.5.dp).background(ConsoleTheme.text.copy(alpha = 0.08f)))

            if (crew.isEmpty()) {
                Text("No crew members available.", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
            } else {
                crew.forEach { member ->
                    val isBusy = member.currentJobId != null
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = rememberRipple(bounded = true),
                                onClick = { onAssign(job, member) }
                            )
                            .background(ConsoleTheme.surface, RoundedCornerShape(4.dp))
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                                Text(dot, style = ConsoleTheme.bodySmall.copy(color = dotColor))
                                Text(member.name, style = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.text))
                            }
                            Text(
                                if (isBusy) "${member.trade} · on ${member.currentJobTitle}" else "${member.trade} · available",
                                style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                            )
                        }
                        Text(
                            "[Select]",
                            style = ConsoleTheme.action.copy(color = ConsoleTheme.accent),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                }
            }

            // Cancel
            Text(
                "[Cancel]",
                style = ConsoleTheme.action.copy(color = ConsoleTheme.textMuted),
                modifier = Modifier
                    .clickable { onDismiss() }
                    .padding(vertical = 4.dp)
                    .align(Alignment.End)
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// CREW PROFILE SHEET — tappable crew member detail
// ════════════════════════════════════════════════════════════════════

@Composable
fun CrewProfileSheet(
    member: CrewPresenceInfo,
    onDismiss: () -> Unit,
    onCall: (String) -> Unit = {},
    onMessage: ((CrewPresenceInfo) -> Unit)? = null
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(ConsoleTheme.background, RoundedCornerShape(8.dp))
                .border(1.dp, ConsoleTheme.text.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Name + status dot
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                Text(dot, style = ConsoleTheme.bodyBold.copy(color = dotColor))
                Text(member.name, style = ConsoleTheme.bodyBold.copy(color = ConsoleTheme.text))
            }

            Text(member.trade, style = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.accent))
            Text(member.status.label, style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))

            Box(Modifier.fillMaxWidth().height(0.5.dp).background(ConsoleTheme.text.copy(alpha = 0.08f)))

            // Hours today
            if (member.clockInTime != null) {
                val mins = (System.currentTimeMillis() - member.clockInTime) / 60_000
                ProfileRow("Hours Today", "${mins / 60}h ${mins % 60}m")
            } else {
                val ago = (System.currentTimeMillis() - member.lastSeen) / 3_600_000
                ProfileRow("Last Seen", if (ago < 24) "${ago}h ago" else "yesterday")
            }

            // Current job
            if (member.currentJobTitle != null) {
                ProfileRow("Current Job", member.currentJobTitle)
            }
            if (member.currentSite != null) {
                ProfileRow("Site", member.currentSite)
            }

            // Task + progress
            if (member.taskDescription != null) {
                ProfileRow("Task", member.taskDescription)
                if (member.taskProgress != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Progress", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted), modifier = Modifier.width(80.dp))
                        Box(Modifier.weight(1f).height(6.dp).background(ConsoleTheme.text.copy(alpha = 0.06f), RoundedCornerShape(3.dp))) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(member.taskProgress / 100f).background(ConsoleTheme.accent, RoundedCornerShape(3.dp)))
                        }
                        Text("${member.taskProgress}%", style = ConsoleTheme.caption.copy(color = ConsoleTheme.accent))
                    }
                }
            }

            // Actions
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(ConsoleTheme.text.copy(alpha = 0.08f)))

            // Message button
            if (onMessage != null && member.userId.isNotBlank()) {
                Text(
                    "[Message ${member.name}]",
                    style = ConsoleTheme.action.copy(color = ConsoleTheme.accent),
                    modifier = Modifier
                        .clickable { onMessage(member); onDismiss() }
                        .padding(vertical = 4.dp)
                )
            }

            // Phone
            if (member.phone != null) {
                Text(
                    "[Call ${member.phone}]",
                    style = ConsoleTheme.action.copy(color = ConsoleTheme.textMuted),
                    modifier = Modifier
                        .clickable { onCall(member.phone) }
                        .padding(vertical = 4.dp)
                )
            }

            // Close
            Text(
                "[Close]",
                style = ConsoleTheme.action.copy(color = ConsoleTheme.textMuted),
                modifier = Modifier
                    .clickable { onDismiss() }
                    .padding(vertical = 4.dp)
                    .align(Alignment.End)
            )
        }
    }
}

@Composable
private fun ProfileRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted), modifier = Modifier.width(80.dp))
        Text(value, style = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.text), modifier = Modifier.weight(1f))
    }
}

// ════════════════════════════════════════════════════════════════════
// PROJECT OVERVIEW MODULE — GC only
// ════════════════════════════════════════════════════════════════════

@Composable
fun ProjectOverviewModule(
    onProjectsClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ConsoleTheme.surface, RoundedCornerShape(4.dp))
            .border(0.5.dp, ConsoleTheme.text.copy(alpha = 0.06f), RoundedCornerShape(4.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("PROJECTS", style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.textMuted))
            Text(
                text = "[+ New]",
                style = ConsoleTheme.action.copy(color = ConsoleTheme.accent),
                modifier = Modifier
                    .clickable { onProjectsClick() }
                    .padding(2.dp)
            )
        }

        Text(
            text = "No projects yet.",
            style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted),
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Text(
            text = "Create a project to manage multiple sites and subcontractors.",
            style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
        )
    }
}

// ════════════════════════════════════════════════════════════════════
// SITE MAP MODULE — FOREMAN, GC thumbnail
// ════════════════════════════════════════════════════════════════════

@Composable
fun SiteMapModule(
    onMapClick: () -> Unit = {},
    allJobs: List<Job> = emptyList()
) {
    val context = LocalContext.current
    val isSolo = com.guildofsmiths.trademesh.data.RoleContext.isSolo()
    val crew = CrewPresenceRepository.getCrew()
    val bySite = remember(crew) {
        crew.filter { it.currentSite != null }.groupBy { it.currentSite!! }
    }
    val activeJobs = remember(allJobs) { allJobs.filter { it.stage != JobStage.CLOSED } }

    // Coordinates for known addresses
    val siteCoords = mapOf(
        "847 Flatbush Ave, Brooklyn NY" to GeoPoint(40.6505, -73.9612),
        "55 W 125th St, Apt 4B, Manhattan NY" to GeoPoint(40.8088, -73.9442),
        "1220 Ocean Pkwy, Brooklyn NY" to GeoPoint(40.6275, -73.9685),
    )

    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ConsoleTheme.surface, RoundedCornerShape(4.dp))
            .border(0.5.dp, ConsoleTheme.text.copy(alpha = 0.06f), RoundedCornerShape(4.dp))
            .clip(RoundedCornerShape(4.dp))
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (isSolo) "JOB SITES" else "CREW MAP",
                style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.textMuted)
            )
            Text(
                "${activeJobs.size} active",
                style = ConsoleTheme.caption.copy(color = ConsoleTheme.accent)
            )
        }

        // Embedded map
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = rememberRipple(bounded = true),
                    onClick = onMapClick
                )
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(false) // disable touch on thumbnail
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        controller.setZoom(11.5)
                        controller.setCenter(GeoPoint(40.7128, -73.9560))
                    }
                },
                update = { mapView ->
                    mapView.overlays.clear()

                    if (isSolo) {
                        // Solo: show job site pins
                        activeJobs.forEach { job ->
                            val addr = job.clientAddress
                            if (addr.isNotBlank()) {
                                val coords = siteCoords[addr] ?: return@forEach
                                val marker = Marker(mapView).apply {
                                    position = coords
                                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                    title = job.clientName ?: job.title
                                    snippet = "${job.stage.displayName} · $addr"
                                }
                                mapView.overlays.add(marker)
                            }
                        }
                    } else {
                        // Team modes: show crew at sites
                        bySite.forEach { (site, members) ->
                            val coords = siteCoords[site] ?: return@forEach
                            val activeOnSite = members.count { it.status == ClockStatus.ON_CLOCK }
                            val marker = Marker(mapView).apply {
                                position = coords
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                title = members.firstOrNull()?.currentJobTitle ?: site
                                snippet = "$activeOnSite/${members.size} on site"
                            }
                            mapView.overlays.add(marker)
                        }
                    }

                    mapView.invalidate()
                }
            )

            // "Tap to expand" overlay
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(ConsoleTheme.background.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text("expand >", style = ConsoleTheme.caption.copy(color = ConsoleTheme.accent))
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// FINANCIALS MODULE — FOREMAN, GC
// ════════════════════════════════════════════════════════════════════

@Composable
fun FinancialsModule(
    allJobs: List<Job> = emptyList()
) {
    val clientNames = remember(allJobs) {
        listOf("All Clients") + allJobs
            .mapNotNull { it.clientName?.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }
    var selectedClient by remember { mutableStateOf("All Clients") }
    var showDropdown by remember { mutableStateOf(false) }

    val filteredJobs = remember(allJobs, selectedClient) {
        if (selectedClient == "All Clients") allJobs
        else allJobs.filter { it.clientName?.trim().equals(selectedClient, ignoreCase = true) }
    }

    // Compute financials
    val laborBudget = filteredJobs.sumOf { it.hourlyRate * 8 * it.crewSize }
    val materialsBudget = filteredJobs.sumOf { it.materials.sumOf { m -> m.quantity * m.unitCost } }
    val totalBudget = laborBudget + materialsBudget
    val materialsSpent = filteredJobs.sumOf { job -> job.materials.filter { it.checked }.sumOf { it.totalCost } }
    val laborSpent = filteredJobs.filter { it.stage == JobStage.IN_PROGRESS || it.stage == JobStage.REVIEW || it.stage == JobStage.INVOICE || it.stage == JobStage.CLOSED }
        .sumOf { it.hourlyRate * 8 }
    val totalSpent = materialsSpent + laborSpent
    val remaining = (totalBudget - totalSpent).coerceAtLeast(0.0)
    val earned = filteredJobs.filter { it.stage == JobStage.CLOSED }.sumOf { it.materials.sumOf { m -> m.totalCost } + (it.hourlyRate * 8) }
    val outstanding = filteredJobs.filter { it.stage == JobStage.INVOICE }.sumOf { it.materials.sumOf { m -> m.totalCost } + (it.hourlyRate * 8) }

    // Per-job breakdown for bars
    val jobBreakdown = remember(filteredJobs) {
        filteredJobs.map { job ->
            val jobBudget = job.materials.sumOf { m -> m.quantity * m.unitCost } + (job.hourlyRate * 8 * job.crewSize)
            val jobSpent = job.materials.filter { it.checked }.sumOf { it.totalCost } +
                if (job.stage != JobStage.LEAD && job.stage != JobStage.PROPOSAL && job.stage != JobStage.APPROVED) job.hourlyRate * 8 else 0.0
            Triple(job.clientName ?: job.title, jobBudget, jobSpent)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ConsoleTheme.surface, RoundedCornerShape(4.dp))
            .border(0.5.dp, ConsoleTheme.text.copy(alpha = 0.06f), RoundedCornerShape(4.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header with client dropdown
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("FINANCIALS", style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.textMuted))
            Box {
                Text(
                    text = "[$selectedClient v]",
                    style = ConsoleTheme.action.copy(color = ConsoleTheme.accent),
                    modifier = Modifier.clickable { showDropdown = !showDropdown }.padding(2.dp)
                )
                androidx.compose.material3.DropdownMenu(
                    expanded = showDropdown,
                    onDismissRequest = { showDropdown = false }
                ) {
                    clientNames.forEach { client ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = {
                                Text(
                                    client,
                                    style = ConsoleTheme.bodySmall.copy(
                                        color = if (client == selectedClient) ConsoleTheme.accent else ConsoleTheme.text
                                    )
                                )
                            },
                            onClick = { selectedClient = client; showDropdown = false }
                        )
                    }
                }
            }
        }

        // ── BUDGET RING + SUMMARY ──────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Ring chart: spent vs budget
            BudgetRing(
                spent = totalSpent,
                budget = totalBudget,
                modifier = Modifier.size(80.dp)
            )

            // Summary numbers
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f)
            ) {
                FinancialRow("Budget", totalBudget, ConsoleTheme.text)
                FinancialRow("Spent", totalSpent, Color(0xFFD97706))
                FinancialRow("Remaining", remaining, ConsoleTheme.success)
            }
        }

        Box(Modifier.fillMaxWidth().height(0.5.dp).background(ConsoleTheme.text.copy(alpha = 0.06f)))

        // ── EXPENSE BREAKDOWN BARS ─────────────────────────────
        Text("EXPENSES", style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.textMuted))

        // Labor vs Materials pie-like bars
        ExpenseBar("Labor", laborSpent, laborBudget, Color(0xFF1d4ed8))
        ExpenseBar("Materials", materialsSpent, materialsBudget, Color(0xFFD97706))

        Box(Modifier.fillMaxWidth().height(0.5.dp).background(ConsoleTheme.text.copy(alpha = 0.06f)))

        // ── PER-JOB BUDGET BARS ────────────────────────────────
        Text("BY JOB", style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.textMuted))

        jobBreakdown.forEach { (name, jobBudget, jobSpent) ->
            ExpenseBar(name, jobSpent, jobBudget, ConsoleTheme.accent)
        }

        Box(Modifier.fillMaxWidth().height(0.5.dp).background(ConsoleTheme.text.copy(alpha = 0.06f)))

        // ── EARNED / OUTSTANDING ───────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Earned", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
                Text("$${String.format("%,.0f", earned)}", style = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.success))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Outstanding", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
                Text("$${String.format("%,.0f", outstanding)}", style = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.accent))
            }
        }
    }
}

// ── BUDGET RING CHART ──────────────────────────────────────────

@Composable
private fun BudgetRing(
    spent: Double,
    budget: Double,
    modifier: Modifier = Modifier
) {
    val fraction = if (budget > 0) (spent / budget).toFloat().coerceIn(0f, 1f) else 0f
    val spentColor = if (fraction > 0.85f) Color(0xFFDC2626) else Color(0xFFD97706)
    val trackColor = ConsoleTheme.text.copy(alpha = 0.06f)
    val pct = (fraction * 100).toInt()

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 8.dp.toPx()
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

            // Track
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
            // Spent arc
            drawArc(
                color = spentColor,
                startAngle = -90f,
                sweepAngle = 360f * fraction,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }
        // Center text
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${pct}%", style = ConsoleTheme.bodySmall.copy(color = spentColor))
            Text("used", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
        }
    }
}

// ── EXPENSE BAR ────────────────────────────────────────────────

@Composable
private fun ExpenseBar(
    label: String,
    spent: Double,
    budget: Double,
    barColor: Color
) {
    val fraction = if (budget > 0) (spent / budget).toFloat().coerceIn(0f, 1f) else 0f

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = ConsoleTheme.caption.copy(color = ConsoleTheme.text))
            Text(
                "$${String.format("%,.0f", spent)} / $${String.format("%,.0f", budget)}",
                style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(ConsoleTheme.text.copy(alpha = 0.06f), RoundedCornerShape(3.dp))
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .background(barColor, RoundedCornerShape(3.dp))
            )
        }
    }
}

// ── FINANCIAL ROW ──────────────────────────────────────────────

@Composable
private fun FinancialRow(label: String, value: Double, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
        Text("$${String.format("%,.0f", value)}", style = ConsoleTheme.bodySmall.copy(color = color))
    }
}

// ════════════════════════════════════════════════════════════════════
// HUB STATUS MODULE — FOREMAN mesh hub
// ════════════════════════════════════════════════════════════════════

@Composable
fun HubStatusModule() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ConsoleTheme.surface, RoundedCornerShape(4.dp))
            .border(0.5.dp, ConsoleTheme.text.copy(alpha = 0.06f), RoundedCornerShape(4.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text("MESH HUB", style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.textMuted))
        Text(
            text = "0 peers connected · Hub idle",
            style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
        )
    }
}

// ════════════════════════════════════════════════════════════════════
// AI INBOX MODULE — supervisor insights
// ════════════════════════════════════════════════════════════════════

@Composable
fun AIInboxModule() {
    val insights by AISupervisor.insights.collectAsState()
    val autoPosted by AISupervisor.autoPosted.collectAsState()
    val isProcessing by AISupervisor.isProcessing.collectAsState()
    val mode = AISupervisor.getMode()

    if (mode == "off") return

    val isSolo = com.guildofsmiths.trademesh.data.RoleContext.isSolo()
    val rawInsights = if (mode == "semi-auto") insights else autoPosted.takeLast(3)
    // Solo users don't have crew — filter out crew-specific insights
    val displayInsights = if (isSolo) rawInsights.filter { it.type != AISupervisor.InsightType.CREW } else rawInsights
    if (displayInsights.isEmpty() && !isProcessing) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ConsoleTheme.surface, RoundedCornerShape(4.dp))
            .border(0.5.dp, ConsoleTheme.text.copy(alpha = 0.06f), RoundedCornerShape(4.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("(◈)", style = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.accent))
                Text("AI INSIGHTS", style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.textMuted))
            }
            if (isProcessing) {
                Text("analyzing...", style = ConsoleTheme.caption.copy(color = ConsoleTheme.accent))
            } else if (mode == "semi-auto" && insights.isNotEmpty()) {
                Text("${insights.size} pending", style = ConsoleTheme.caption.copy(color = ConsoleTheme.accent))
            }
        }

        displayInsights.forEach { insight ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleTheme.background, RoundedCornerShape(4.dp))
                    .border(0.5.dp, ConsoleTheme.text.copy(alpha = 0.04f), RoundedCornerShape(4.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                val icon = when (insight.type) {
                    AISupervisor.InsightType.ALERT -> "(!)"
                    AISupervisor.InsightType.SUMMARY -> "(i)"
                    AISupervisor.InsightType.DRAFT -> "(>)"
                    AISupervisor.InsightType.CHECKIN -> "(◈)"
                    AISupervisor.InsightType.STAGE -> "(→)"
                    AISupervisor.InsightType.CREW -> "(●)"
                    AISupervisor.InsightType.FINANCIAL -> "($)"
                }
                val iconColor = when (insight.type) {
                    AISupervisor.InsightType.ALERT -> ConsoleTheme.warning
                    AISupervisor.InsightType.CREW -> Color(0xFFD97706)
                    AISupervisor.InsightType.STAGE -> ConsoleTheme.success
                    AISupervisor.InsightType.FINANCIAL -> ConsoleTheme.warning
                    else -> ConsoleTheme.accent
                }

                // Title row with dismiss X
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(icon, style = ConsoleTheme.bodySmall.copy(color = iconColor))
                        Text(insight.title, style = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.text))
                    }
                    Text(
                        "[x]",
                        style = ConsoleTheme.action.copy(color = ConsoleTheme.textMuted),
                        modifier = Modifier
                            .clickable { AISupervisor.dismissInsight(insight.id) }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }

                Text(
                    insight.body,
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                )

                // Confirm/Reject for FINANCIAL insights (needs permission)
                if (insight.type == AISupervisor.InsightType.FINANCIAL && !insight.approved) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            "[Confirm]",
                            style = ConsoleTheme.action.copy(color = ConsoleTheme.success),
                            modifier = Modifier.clickable { AISupervisor.approveInsight(insight.id) }.padding(4.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "[Reject]",
                            style = ConsoleTheme.action.copy(color = ConsoleTheme.error),
                            modifier = Modifier.clickable { AISupervisor.dismissInsight(insight.id) }.padding(4.dp)
                        )
                    }
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// OPENSTREETMAP CREW VIEW
// ════════════════════════════════════════════════════════════════════

// Mock coordinates for demo sites (Brooklyn & Manhattan)
private val SITE_COORDS = mapOf(
    "847 Flatbush Ave, Brooklyn NY" to GeoPoint(40.6505, -73.9612),
    "55 W 125th St, Apt 4B, Manhattan NY" to GeoPoint(40.8088, -73.9442),
    "1220 Ocean Pkwy, Brooklyn NY" to GeoPoint(40.6275, -73.9685),
)

@Composable
fun CrewMapView(
    crew: List<com.guildofsmiths.trademesh.data.CrewPresenceInfo>
) {
    val context = LocalContext.current

    // Initialize osmdroid config
    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .clip(RoundedCornerShape(4.dp))
            .border(0.5.dp, ConsoleTheme.text.copy(alpha = 0.06f), RoundedCornerShape(4.dp))
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    setTileSource(TileSourceFactory.MAPNIK)
                    setMultiTouchControls(true)
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    // Center on NYC area
                    controller.setZoom(12.0)
                    controller.setCenter(GeoPoint(40.7128, -73.9560))
                }
            },
            update = { mapView ->
                mapView.overlays.clear()

                // Group crew by site and place markers
                val bySite = crew.filter { it.currentSite != null }.groupBy { it.currentSite!! }
                bySite.forEach { (site, members) ->
                    val coords = SITE_COORDS[site] ?: return@forEach
                    val activeCount = members.count { it.status == ClockStatus.ON_CLOCK }
                    val names = members.joinToString(", ") { it.name }
                    val jobTitle = members.firstOrNull()?.currentJobTitle ?: "Job site"

                    val marker = Marker(mapView).apply {
                        position = coords
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = jobTitle
                        snippet = "$names ($activeCount/${members.size} on site)"
                    }
                    mapView.overlays.add(marker)
                }

                mapView.invalidate()
            }
        )
    }
}

// ════════════════════════════════════════════════════════════════════
// ROLE-SPECIFIC QUICK ACTIONS
// ════════════════════════════════════════════════════════════════════

data class QuickActionItem(val label: String, val onClick: () -> Unit)

fun getQuickActions(
    role: com.guildofsmiths.trademesh.data.UserRole,
    onReport: () -> Unit,
    onSupply: () -> Unit,
    onArchive: () -> Unit,
    onClients: () -> Unit,
    onClockIn: () -> Unit,
    onComm: () -> Unit,
    onJobBoard: () -> Unit,
    onDispatch: () -> Unit = {},
): List<QuickActionItem> = when (role) {
    com.guildofsmiths.trademesh.data.UserRole.SOLO -> listOf(
        QuickActionItem("[Report]", onReport),
        QuickActionItem("[Supply]", onSupply),
        QuickActionItem("[Archive]", onArchive),
        QuickActionItem("[Clients]", onClients),
    )
    com.guildofsmiths.trademesh.data.UserRole.TEAM_MEMBER -> listOf(
        QuickActionItem("[Clock In]", onClockIn),
        QuickActionItem("[Messages]", onComm),
        QuickActionItem("[Supply]", onSupply),
        QuickActionItem("[Report]", onReport),
    )
    com.guildofsmiths.trademesh.data.UserRole.TEAM_LEAD -> listOf(
        QuickActionItem("[Crew]", onComm),
        QuickActionItem("[Report]", onReport),
        QuickActionItem("[Supply]", onSupply),
        QuickActionItem("[Clients]", onClients),
    )
    com.guildofsmiths.trademesh.data.UserRole.FOREMAN -> listOf(
        QuickActionItem("[Dispatch]", onDispatch),
        QuickActionItem("[Crew]", onComm),
        QuickActionItem("[Report]", onReport),
        QuickActionItem("[Supply]", onSupply),
    )
    com.guildofsmiths.trademesh.data.UserRole.GENERAL_CONTRACTOR -> listOf(
        QuickActionItem("[Sites]", onJobBoard),
        QuickActionItem("[Subs]", onClients),
        QuickActionItem("[Report]", onReport),
        QuickActionItem("[Contracts]", onArchive),
    )
    else -> listOf(
        QuickActionItem("[Report]", onReport),
        QuickActionItem("[Supply]", onSupply),
        QuickActionItem("[Archive]", onArchive),
        QuickActionItem("[Clients]", onClients),
    )
}
