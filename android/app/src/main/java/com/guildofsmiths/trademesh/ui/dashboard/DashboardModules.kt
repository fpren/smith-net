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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import com.guildofsmiths.trademesh.ai.AISupervisor
import com.guildofsmiths.trademesh.data.ClockStatus
import com.guildofsmiths.trademesh.data.CrewPresenceInfo
import com.guildofsmiths.trademesh.data.CrewPresenceRepository
import com.guildofsmiths.trademesh.data.Permission
import com.guildofsmiths.trademesh.data.RoleContext
import com.guildofsmiths.trademesh.ui.jobboard.Job
import com.guildofsmiths.trademesh.ui.jobboard.JobStage
import com.guildofsmiths.trademesh.ui.map.JobDetailPanel
import com.guildofsmiths.trademesh.ui.map.SiteDetailPanel
import com.guildofsmiths.trademesh.ui.Tokens2
import com.guildofsmiths.trademesh.ui.theme2.LocalSmithColors
import com.guildofsmiths.trademesh.ui.theme2.SmithCard
import com.guildofsmiths.trademesh.ui.theme2.SmithType

// ════════════════════════════════════════════════════════════════════
// MY TASKS MODULE — TEAM_MEMBER primary surface
// ════════════════════════════════════════════════════════════════════

@Composable
fun MyTasksModule() {
    val colors = LocalSmithColors.current
    SmithCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("TODAY'S TASKS", style = SmithType.captionBold.copy(color = colors.inkMuted))
            Spacer(modifier = Modifier.height(4.dp))

            // Placeholder — will be populated when task assignment is implemented
            Text(
                text = "No tasks assigned yet.",
                style = SmithType.caption.copy(color = colors.inkMuted),
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Text(
                text = "Tasks will appear here when your team lead or dispatcher assigns work.",
                style = SmithType.caption.copy(color = colors.inkMuted)
            )
        }
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
    val colors = LocalSmithColors.current
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

    SmithCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isGC) "SITES & SUBS" else if (isExpanded) "CREW STATUS" else "CREW",
                style = SmithType.captionBold.copy(color = colors.inkMuted)
            )
            Text(
                text = "$activeCount/${crew.size} active",
                style = SmithType.caption.copy(color = colors.inkMuted)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        if (isGC) {
            // GC variant: grouped by site
            val bySite = crew.filter { it.currentSite != null }.groupBy { it.currentSite!! }
            val unassigned = crew.filter { it.currentSite == null }

            if (bySite.isEmpty() && unassigned.isEmpty()) {
                Text("No crew members connected.", style = SmithType.caption.copy(color = colors.inkMuted))
            }

            bySite.forEach { (site, members) ->
                val siteActive = members.count { it.status == ClockStatus.ON_CLOCK }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(">", style = SmithType.bodySmall.copy(color = colors.accent))
                        Text(site.take(30), style = SmithType.bodySmall.copy(color = colors.ink))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        members.forEach { m ->
                            val dot = if (m.status == ClockStatus.ON_CLOCK) "●" else "○"
                            val dotColor = if (m.status == ClockStatus.ON_CLOCK) colors.statusOnline else colors.inkMuted
                            Text(dot, style = SmithType.caption.copy(color = dotColor))
                        }
                        Text("$siteActive/${members.size}", style = SmithType.caption.copy(color = colors.inkMuted))
                    }
                }
                val trades = members.joinToString(", ") { it.name }
                Text("  $trades", style = SmithType.caption.copy(color = colors.inkMuted))
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
                    isAI -> colors.accent
                    member.status == ClockStatus.ON_CLOCK -> colors.statusOnline
                    member.status == ClockStatus.ON_BREAK -> colors.accent
                    else -> colors.inkMuted
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
                        .clip(RoundedCornerShape(Tokens2.RadiusControl))
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
                        style = SmithType.caption.copy(color = if (isAI) colors.accent else if (member.status == ClockStatus.OFF_CLOCK) colors.inkMuted else colors.ink)
                    )
                    Text(rightText, style = SmithType.caption.copy(color = if (isAI || member.status == ClockStatus.ON_CLOCK) colors.accent else colors.inkMuted))
                }
            }

            if (!isExpanded && sorted.size > 3) {
                Text(
                    text = "[See all ${sorted.size} >]",
                    style = SmithType.action.copy(color = colors.accent),
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
                Text("[Dispatch]", style = SmithType.action.copy(color = colors.accent), modifier = Modifier.clickable { onDispatchClick() }.padding(4.dp))
            }
            if (isGC) {
                Text("[Projects]", style = SmithType.action.copy(color = colors.accent), modifier = Modifier.clickable { onCrewClick() }.padding(4.dp))
            }
        }

        // Inline map — minimal site summary + OpenStreetMap
        if (isExpanded || isGC) {
            Spacer(modifier = Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.ink.copy(alpha = 0.06f)))
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
                    Text(site.take(30), style = SmithType.caption.copy(color = colors.ink), modifier = Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        members.forEach { m ->
                            val dc = when (m.status) {
                                ClockStatus.ON_CLOCK -> colors.statusOnline
                                ClockStatus.ON_BREAK -> colors.accent
                                ClockStatus.OFF_CLOCK -> colors.inkMuted
                            }
                            Text("●", style = SmithType.caption.copy(color = dc))
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("$siteActive/${members.size}", style = SmithType.caption.copy(color = colors.inkMuted))
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // OpenStreetMap
            CrewMapView(crew = crew)
        }
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
    val colors = LocalSmithColors.current
    val urgencyColor = if (unassignedJobs.isNotEmpty()) colors.attention else colors.inkMuted

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(Tokens2.RadiusCard))
            .background(colors.bgPanel, RoundedCornerShape(Tokens2.RadiusCard))
            .border(1.dp, urgencyColor.copy(alpha = 0.25f), RoundedCornerShape(Tokens2.RadiusCard))
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
                Text(">>", style = SmithType.bodySmall.copy(color = urgencyColor))
                Text("DISPATCH", style = SmithType.captionBold.copy(color = urgencyColor))
                if (unassignedJobs.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .background(urgencyColor.copy(alpha = 0.15f), RoundedCornerShape(Tokens2.RadiusPill))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            "${unassignedJobs.size} unassigned",
                            style = SmithType.caption.copy(color = urgencyColor)
                        )
                    }
                }
            }
        }

        if (unassignedJobs.isEmpty()) {
            Text("All jobs assigned.", style = SmithType.caption.copy(color = colors.statusOnline), modifier = Modifier.padding(vertical = 4.dp))
        } else {
            unassignedJobs.take(4).forEach { job ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(urgencyColor.copy(alpha = 0.04f), RoundedCornerShape(Tokens2.RadiusControl))
                        .clip(RoundedCornerShape(Tokens2.RadiusControl))
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
                            style = SmithType.bodySmall.copy(color = colors.ink)
                        )
                        Text(
                            "${job.stage.displayName} · ${job.clientAddress.take(25)}",
                            style = SmithType.caption.copy(color = colors.inkMuted)
                        )
                    }
                    Text(
                        "[Assign]",
                        style = SmithType.action.copy(color = urgencyColor),
                        modifier = Modifier
                            .clickable { onAssignCrew(job) }
                            .background(urgencyColor.copy(alpha = 0.10f), RoundedCornerShape(Tokens2.RadiusControl))
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
    val colors = LocalSmithColors.current
    val crew = remember { CrewPresenceRepository.getCrew() }

    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.bgBase, RoundedCornerShape(Tokens2.RadiusCard))
                .border(1.dp, colors.ink.copy(alpha = 0.10f), RoundedCornerShape(Tokens2.RadiusCard))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("ASSIGN CREW", style = SmithType.captionBold.copy(color = colors.accent))
            Text(
                job.clientName ?: job.title,
                style = SmithType.bodySmall.copy(color = colors.ink)
            )
            Text(
                job.clientAddress.take(40),
                style = SmithType.caption.copy(color = colors.inkMuted)
            )

            Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.ink.copy(alpha = 0.08f)))

            if (crew.isEmpty()) {
                Text("No crew members available.", style = SmithType.caption.copy(color = colors.inkMuted))
            } else {
                crew.forEach { member ->
                    val isBusy = member.currentJobId != null
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Tokens2.RadiusControl))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = rememberRipple(bounded = true),
                                onClick = { onAssign(job, member) }
                            )
                            .background(colors.bgPanel, RoundedCornerShape(Tokens2.RadiusControl))
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
                                    ClockStatus.ON_CLOCK -> colors.statusOnline
                                    ClockStatus.ON_BREAK -> colors.accent
                                    ClockStatus.OFF_CLOCK -> colors.inkMuted
                                }
                                Text(dot, style = SmithType.bodySmall.copy(color = dotColor))
                                Text(member.name, style = SmithType.bodySmall.copy(color = colors.ink))
                            }
                            Text(
                                if (isBusy) "${member.trade} · on ${member.currentJobTitle}" else "${member.trade} · available",
                                style = SmithType.caption.copy(color = colors.inkMuted)
                            )
                        }
                        Text(
                            "[Select]",
                            style = SmithType.action.copy(color = colors.accent),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                }
            }

            // Cancel
            Text(
                "[Cancel]",
                style = SmithType.action.copy(color = colors.inkMuted),
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
    val colors = LocalSmithColors.current
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.bgBase, RoundedCornerShape(Tokens2.RadiusCard))
                .border(1.dp, colors.ink.copy(alpha = 0.10f), RoundedCornerShape(Tokens2.RadiusCard))
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
                    ClockStatus.ON_CLOCK -> colors.statusOnline
                    ClockStatus.ON_BREAK -> colors.accent
                    ClockStatus.OFF_CLOCK -> colors.inkMuted
                }
                Text(dot, style = SmithType.bodyBold.copy(color = dotColor))
                Text(member.name, style = SmithType.bodyBold.copy(color = colors.ink))
            }

            Text(member.trade, style = SmithType.bodySmall.copy(color = colors.accent))
            Text(member.status.label, style = SmithType.caption.copy(color = colors.inkMuted))

            Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.ink.copy(alpha = 0.08f)))

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
                        Text("Progress", style = SmithType.caption.copy(color = colors.inkMuted), modifier = Modifier.width(80.dp))
                        Box(Modifier.weight(1f).height(6.dp).background(colors.ink.copy(alpha = 0.06f), RoundedCornerShape(Tokens2.RadiusPill))) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(member.taskProgress / 100f).background(colors.accent, RoundedCornerShape(Tokens2.RadiusPill)))
                        }
                        Text("${member.taskProgress}%", style = SmithType.caption.copy(color = colors.accent))
                    }
                }
            }

            // Actions
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.ink.copy(alpha = 0.08f)))

            // Message button
            if (onMessage != null && member.userId.isNotBlank()) {
                Text(
                    "[Message ${member.name}]",
                    style = SmithType.action.copy(color = colors.accent),
                    modifier = Modifier
                        .clickable { onMessage(member); onDismiss() }
                        .padding(vertical = 4.dp)
                )
            }

            // Phone
            if (member.phone != null) {
                Text(
                    "[Call ${member.phone}]",
                    style = SmithType.action.copy(color = colors.inkMuted),
                    modifier = Modifier
                        .clickable { onCall(member.phone) }
                        .padding(vertical = 4.dp)
                )
            }

            // Close
            Text(
                "[Close]",
                style = SmithType.action.copy(color = colors.inkMuted),
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
    val colors = LocalSmithColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = SmithType.caption.copy(color = colors.inkMuted), modifier = Modifier.width(80.dp))
        Text(value, style = SmithType.bodySmall.copy(color = colors.ink), modifier = Modifier.weight(1f))
    }
}

// ════════════════════════════════════════════════════════════════════
// PROJECT OVERVIEW MODULE — GC only
// ════════════════════════════════════════════════════════════════════

@Composable
fun ProjectOverviewModule(
    onProjectsClick: () -> Unit = {}
) {
    val colors = LocalSmithColors.current
    SmithCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("PROJECTS", style = SmithType.captionBold.copy(color = colors.inkMuted))
                Text(
                    text = "[+ New]",
                    style = SmithType.action.copy(color = colors.accent),
                    modifier = Modifier
                        .clickable { onProjectsClick() }
                        .padding(2.dp)
                )
            }

            Text(
                text = "No projects yet.",
                style = SmithType.caption.copy(color = colors.inkMuted),
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Text(
                text = "Create a project to manage multiple sites and subcontractors.",
                style = SmithType.caption.copy(color = colors.inkMuted)
            )
        }
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
    val colors = LocalSmithColors.current
    val context = LocalContext.current
    val isSolo = com.guildofsmiths.trademesh.data.RoleContext.isSolo()
    val crew = CrewPresenceRepository.getCrew()
    val bySite = remember(crew) {
        crew.filter { it.currentSite != null }.groupBy { it.currentSite!! }
    }
    val activeJobs = remember(allJobs) { allJobs.filter { it.stage != JobStage.CLOSED } }

    // Demo crew-site coordinates — used ONLY for crew-presence markers
    // (crew.currentSite is a bare address string with no coords of its own).
    // Job markers plot from job.latitude/longitude; never add jobs here.
    val siteCoords = mapOf(
        "847 Flatbush Ave, Brooklyn NY" to GeoPoint(40.6505, -73.9612),
        "55 W 125th St, Apt 4B, Manhattan NY" to GeoPoint(40.8088, -73.9442),
        "1220 Ocean Pkwy, Brooklyn NY" to GeoPoint(40.6275, -73.9685),
    )

    var selectedSite by remember { mutableStateOf<String?>(null) }
    var selectedJob by remember { mutableStateOf<Job?>(null) }
    val framed = remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
    }

    SmithCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
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
                style = SmithType.captionBold.copy(color = colors.inkMuted)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${activeJobs.size} active",
                    style = SmithType.caption.copy(color = colors.accent)
                )
                Text(
                    "[OPEN MAP]",
                    style = SmithType.caption.copy(color = colors.accent),
                    modifier = Modifier.clickable { onMapClick() }
                )
            }
        }

        // Embedded interactive map — pan/zoom and marker taps work in place.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
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
                        controller.setZoom(11.5)
                        controller.setCenter(GeoPoint(40.7128, -73.9560))
                    }
                },
                update = { mapView ->
                    mapView.overlays.clear()
                    val placedCoords = mutableListOf<GeoPoint>()

                    if (isSolo) {
                        // Geocoded coords only — jobs not yet geocoded have no pin.
                        activeJobs.forEach { job ->
                            val lat = job.latitude
                            val lng = job.longitude
                            if (lat == null || lng == null) return@forEach
                            val addr = job.clientAddress
                            val coords = GeoPoint(lat, lng)
                            val marker = Marker(mapView).apply {
                                position = coords
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                title = job.clientName ?: job.title
                                snippet = if (addr.isNotBlank()) "${job.stage.displayName} · $addr"
                                          else job.stage.displayName
                                setOnMarkerClickListener { _, _ ->
                                    selectedJob = job
                                    selectedSite = null
                                    true
                                }
                            }
                            mapView.overlays.add(marker)
                            placedCoords.add(coords)
                        }
                    } else {
                        bySite.forEach { (site, members) ->
                            val coords = siteCoords[site] ?: return@forEach
                            val activeOnSite = members.count { it.status == ClockStatus.ON_CLOCK }
                            val marker = Marker(mapView).apply {
                                position = coords
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                                title = members.firstOrNull()?.currentJobTitle ?: site
                                snippet = "$activeOnSite/${members.size} on site"
                                setOnMarkerClickListener { _, _ ->
                                    selectedSite = site
                                    selectedJob = null
                                    true
                                }
                            }
                            mapView.overlays.add(marker)
                            placedCoords.add(coords)
                        }
                    }

                    if (!framed.value && placedCoords.isNotEmpty()) {
                        val frame = {
                            if (placedCoords.size == 1) {
                                val p = placedCoords[0]
                                mapView.controller.setCenter(p)
                                mapView.controller.setZoom(15.0)
                            } else {
                                val box = BoundingBox.fromGeoPointsSafe(placedCoords)
                                mapView.zoomToBoundingBox(box.increaseByScale(1.3f), false, 24)
                            }
                        }
                        if (mapView.width > 0 && mapView.height > 0) {
                            frame()
                        } else {
                            mapView.addOnFirstLayoutListener { _, _, _, _, _ -> frame() }
                        }
                        framed.value = true
                    }

                    mapView.invalidate()
                }
            )
        }

        selectedSite?.let { site ->
            SiteDetailPanel(
                site = site,
                members = bySite[site].orEmpty(),
                onCrewTap = { /* no-op on dashboard */ },
                onClose = { selectedSite = null }
            )
        }
        selectedJob?.let { job ->
            JobDetailPanel(
                job = job,
                onJumpToJob = {
                    selectedJob = null
                    onMapClick()
                },
                onClose = { selectedJob = null }
            )
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
    val colors = LocalSmithColors.current
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

    SmithCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Header with client dropdown
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("FINANCIALS", style = SmithType.captionBold.copy(color = colors.inkMuted))
            Box {
                Text(
                    text = "[$selectedClient v]",
                    style = SmithType.action.copy(color = colors.accent),
                    modifier = Modifier.clickable { showDropdown = !showDropdown }.padding(2.dp)
                )
                if (showDropdown) {
                    Popup(onDismissRequest = { showDropdown = false }) {
                        Column(
                            modifier = Modifier
                                .background(colors.bgPanel, RoundedCornerShape(Tokens2.RadiusControl))
                                .border(1.dp, colors.line, RoundedCornerShape(Tokens2.RadiusControl))
                                .width(IntrinsicSize.Max),
                        ) {
                            clientNames.forEach { client ->
                                Text(
                                    text = client,
                                    style = SmithType.bodySmall.copy(
                                        color = if (client == selectedClient) colors.accent else colors.ink
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                        .clickable { selectedClient = client; showDropdown = false }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                )
                            }
                        }
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
                FinancialRow("Budget", totalBudget, colors.ink)
                FinancialRow("Spent", totalSpent, colors.attention)
                FinancialRow("Remaining", remaining, colors.statusOnline)
            }
        }

        Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.ink.copy(alpha = 0.06f)))

        // ── EXPENSE BREAKDOWN BARS ─────────────────────────────
        Text("EXPENSES", style = SmithType.captionBold.copy(color = colors.inkMuted))

        // Labor vs Materials pie-like bars
        ExpenseBar("Labor", laborSpent, laborBudget, colors.accent)
        ExpenseBar("Materials", materialsSpent, materialsBudget, colors.attention)

        Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.ink.copy(alpha = 0.06f)))

        // ── PER-JOB BUDGET BARS ────────────────────────────────
        Text("BY JOB", style = SmithType.captionBold.copy(color = colors.inkMuted))

        jobBreakdown.forEach { (name, jobBudget, jobSpent) ->
            ExpenseBar(name, jobSpent, jobBudget, colors.accent)
        }

        Box(Modifier.fillMaxWidth().height(0.5.dp).background(colors.ink.copy(alpha = 0.06f)))

        // ── EARNED / OUTSTANDING ───────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Earned", style = SmithType.caption.copy(color = colors.inkMuted))
                Text("$${String.format("%,.0f", earned)}", style = SmithType.bodySmall.copy(color = colors.statusOnline))
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("Outstanding", style = SmithType.caption.copy(color = colors.inkMuted))
                Text("$${String.format("%,.0f", outstanding)}", style = SmithType.bodySmall.copy(color = colors.accent))
            }
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
    val colors = LocalSmithColors.current
    val fraction = if (budget > 0) (spent / budget).toFloat().coerceIn(0f, 1f) else 0f
    val spentColor = if (fraction > 0.85f) colors.statusError else colors.attention
    val trackColor = colors.ink.copy(alpha = 0.06f)
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
            Text("${pct}%", style = SmithType.bodySmall.copy(color = spentColor))
            Text("used", style = SmithType.caption.copy(color = colors.inkMuted))
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
    val colors = LocalSmithColors.current
    val fraction = if (budget > 0) (spent / budget).toFloat().coerceIn(0f, 1f) else 0f

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = SmithType.caption.copy(color = colors.ink))
            Text(
                "$${String.format("%,.0f", spent)} / $${String.format("%,.0f", budget)}",
                style = SmithType.caption.copy(color = colors.inkMuted)
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(colors.ink.copy(alpha = 0.06f), RoundedCornerShape(Tokens2.RadiusPill))
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .background(barColor, RoundedCornerShape(Tokens2.RadiusPill))
            )
        }
    }
}

// ── FINANCIAL ROW ──────────────────────────────────────────────

@Composable
private fun FinancialRow(label: String, value: Double, color: Color) {
    val colors = LocalSmithColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = SmithType.caption.copy(color = colors.inkMuted))
        Text("$${String.format("%,.0f", value)}", style = SmithType.bodySmall.copy(color = color))
    }
}

// ════════════════════════════════════════════════════════════════════
// HUB STATUS MODULE — FOREMAN mesh hub
// ════════════════════════════════════════════════════════════════════

@Composable
fun HubStatusModule() {
    val colors = LocalSmithColors.current
    SmithCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("MESH HUB", style = SmithType.captionBold.copy(color = colors.inkMuted))
            Text(
                text = "0 peers connected · Hub idle",
                style = SmithType.caption.copy(color = colors.inkMuted)
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════
// AI INBOX MODULE — supervisor insights
// ════════════════════════════════════════════════════════════════════

@Composable
fun AIInboxModule() {
    val colors = LocalSmithColors.current
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

    SmithCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("(◈)", style = SmithType.bodySmall.copy(color = colors.accent))
                Text("SMITHAI", style = SmithType.captionBold.copy(color = colors.inkMuted))
            }
            if (isProcessing) {
                Text("analyzing...", style = SmithType.caption.copy(color = colors.accent))
            } else if (mode == "semi-auto" && insights.isNotEmpty()) {
                Text("${insights.size} pending", style = SmithType.caption.copy(color = colors.accent))
            }
        }

        displayInsights.forEach { insight ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.bgBase, RoundedCornerShape(Tokens2.RadiusCard))
                    .border(0.5.dp, colors.ink.copy(alpha = 0.04f), RoundedCornerShape(Tokens2.RadiusCard))
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
                    AISupervisor.InsightType.ALERT -> colors.attention
                    AISupervisor.InsightType.CREW -> colors.attention
                    AISupervisor.InsightType.STAGE -> colors.statusOnline
                    AISupervisor.InsightType.FINANCIAL -> colors.attention
                    else -> colors.accent
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
                        Text(icon, style = SmithType.bodySmall.copy(color = iconColor))
                        Text(insight.title, style = SmithType.bodySmall.copy(color = colors.ink))
                    }
                    Text(
                        "[x]",
                        style = SmithType.action.copy(color = colors.inkMuted),
                        modifier = Modifier
                            .clickable { AISupervisor.dismissInsight(insight.id) }
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }

                Text(
                    insight.body,
                    style = SmithType.caption.copy(color = colors.inkMuted)
                )

                // Confirm/Reject for FINANCIAL insights (needs permission)
                if (insight.type == AISupervisor.InsightType.FINANCIAL && !insight.approved) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            "[Confirm]",
                            style = SmithType.action.copy(color = colors.statusOnline),
                            modifier = Modifier.clickable { AISupervisor.approveInsight(insight.id) }.padding(4.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "[Reject]",
                            style = SmithType.action.copy(color = colors.statusError),
                            modifier = Modifier.clickable { AISupervisor.dismissInsight(insight.id) }.padding(4.dp)
                        )
                    }
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
// Demo crew-site coordinates — used ONLY for crew-presence markers (see note
// in SiteMapModule). Job markers plot from job.latitude/longitude.
private val SITE_COORDS = mapOf(
    "847 Flatbush Ave, Brooklyn NY" to GeoPoint(40.6505, -73.9612),
    "55 W 125th St, Apt 4B, Manhattan NY" to GeoPoint(40.8088, -73.9442),
    "1220 Ocean Pkwy, Brooklyn NY" to GeoPoint(40.6275, -73.9685),
)

@Composable
fun CrewMapView(
    crew: List<com.guildofsmiths.trademesh.data.CrewPresenceInfo>,
    activeJobs: List<Job> = emptyList(),
    onSiteClick: (siteAddress: String) -> Unit = {},
    onJobClick: (jobId: String) -> Unit = {},
    fillContainer: Boolean = false
) {
    val colors = LocalSmithColors.current
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        Configuration.getInstance().userAgentValue = context.packageName
    }

    val sizeModifier = if (fillContainer) {
        Modifier.fillMaxSize()
    } else {
        Modifier.fillMaxWidth().height(200.dp)
    }
    val cornerModifier = if (fillContainer) {
        Modifier
    } else {
        Modifier
            .clip(RoundedCornerShape(Tokens2.RadiusOps))
            .border(0.5.dp, colors.ink.copy(alpha = 0.06f), RoundedCornerShape(Tokens2.RadiusOps))
    }

    Box(modifier = sizeModifier.then(cornerModifier)) {
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
                    controller.setZoom(12.0)
                    controller.setCenter(GeoPoint(40.7128, -73.9560))
                    // Re-nudge the zoom on every layout change so osmdroid
                    // recomputes its tile viewport when the View grows.
                    addOnLayoutChangeListener { v, _, _, _, _, oldL, oldT, oldR, oldB ->
                        val mv = v as MapView
                        if (mv.width != (oldR - oldL) || mv.height != (oldB - oldT)) {
                            val z = mv.zoomLevelDouble
                            mv.controller.setZoom(z + 0.001)
                            mv.controller.setZoom(z)
                            mv.invalidate()
                        }
                    }
                }
            },
            update = { mapView ->
                mapView.overlays.clear()

                // Crew-site markers
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
                        setOnMarkerClickListener { _, _ ->
                            onSiteClick(site)
                            true
                        }
                    }
                    mapView.overlays.add(marker)
                }

                // Job-site markers — plotted from the job's geocoded
                // latitude/longitude (backend geocode worker; demo seeds carry
                // theirs inline). Jobs without coords yet simply have no pin.
                // Skip jobs already represented by a crew-on-site marker.
                activeJobs.forEach { job ->
                    val lat = job.latitude
                    val lng = job.longitude
                    if (lat == null || lng == null) return@forEach
                    val addr = job.clientAddress
                    if (addr.isNotBlank() && bySite.containsKey(addr)) return@forEach
                    val marker = Marker(mapView).apply {
                        position = GeoPoint(lat, lng)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                        title = job.clientName ?: job.title
                        snippet = if (addr.isNotBlank()) "${job.stage.displayName} · $addr"
                                  else job.stage.displayName
                        setOnMarkerClickListener { _, _ ->
                            onJobClick(job.id)
                            true
                        }
                    }
                    mapView.overlays.add(marker)
                }

                // When filling the parent (Map screen), the MapView's tile
                // viewport often lags behind its View bounds and leaves the
                // perimeter blank. Forcing a tiny pan + re-center kicks
                // osmdroid to refetch tiles for the full visible rect.
                if (fillContainer) {
                    listOf(50L, 200L, 600L).forEach { delay ->
                        mapView.postDelayed({
                            val center = GeoPoint(40.7128, -73.9560)
                            // Big synthetic pan to force osmdroid to re-fetch
                            // tiles for the full visible rect, then snap back.
                            mapView.scrollBy(0, 1000)
                            mapView.scrollBy(0, -1000)
                            mapView.controller.setCenter(center)
                            mapView.invalidate()
                        }, delay)
                    }
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
    onExpenses: () -> Unit = {},
    onMap: () -> Unit = {},
): List<QuickActionItem> = when (role) {
    com.guildofsmiths.trademesh.data.UserRole.SOLO -> listOf(
        QuickActionItem("[Report]", onReport),
        QuickActionItem("[Supply]", onSupply),
        QuickActionItem("[Clients]", onClients),
        QuickActionItem("[Archive]", onArchive),
    )
    com.guildofsmiths.trademesh.data.UserRole.TEAM_MEMBER -> listOf(
        QuickActionItem("[Report]", onReport),
        QuickActionItem("[Supply]", onSupply),
        QuickActionItem("[Archive]", onArchive),
        QuickActionItem("[Map]", onMap),
    )
    com.guildofsmiths.trademesh.data.UserRole.TEAM_LEAD -> listOf(
        QuickActionItem("[Report]", onReport),
        QuickActionItem("[Supply]", onSupply),
        QuickActionItem("[Clients]", onClients),
        QuickActionItem("[Archive]", onArchive),
    )
    com.guildofsmiths.trademesh.data.UserRole.FOREMAN -> listOf(
        QuickActionItem("[Jobs]", onJobBoard),
        QuickActionItem("[Report]", onReport),
        QuickActionItem("[Supply]", onSupply),
        QuickActionItem("[Clients]", onClients),
    )
    com.guildofsmiths.trademesh.data.UserRole.GENERAL_CONTRACTOR -> listOf(
        QuickActionItem("[Dispatch]", onDispatch),
        QuickActionItem("[Report]", onReport),
        QuickActionItem("[Clients]", onClients),
        QuickActionItem("[Archive]", onArchive),
    )
    else -> listOf(
        QuickActionItem("[Report]", onReport),
        QuickActionItem("[Supply]", onSupply),
        QuickActionItem("[Clients]", onClients),
        QuickActionItem("[Archive]", onArchive),
    )
}
