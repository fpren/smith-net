package com.guildofsmiths.trademesh.ui.map

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.guildofsmiths.trademesh.data.ClockStatus
import com.guildofsmiths.trademesh.data.CrewPresenceInfo
import com.guildofsmiths.trademesh.ui.Tokens2
import com.guildofsmiths.trademesh.ui.jobboard.Job
import com.guildofsmiths.trademesh.ui.theme2.LocalSmithColors
import com.guildofsmiths.trademesh.ui.theme2.SmithType
import com.guildofsmiths.trademesh.ui.theme2.tabular

@Composable
internal fun SiteDetailPanel(
    modifier: Modifier = Modifier,
    site: String,
    members: List<CrewPresenceInfo>,
    onCrewTap: (CrewPresenceInfo) -> Unit,
    onClose: () -> Unit
) {
    val colors = LocalSmithColors.current
    val activeOnSite = members.count { it.status == ClockStatus.ON_CLOCK }
    val jobTitle = members.firstOrNull()?.currentJobTitle

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(9.dp)
            .background(colors.bgPanel, RoundedCornerShape(Tokens2.RadiusOps))
            .border(1.dp, colors.line, RoundedCornerShape(Tokens2.RadiusOps))
            .clip(RoundedCornerShape(Tokens2.RadiusOps))
            .padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (jobTitle != null) {
                    Text(jobTitle, style = SmithType.bodySmall.copy(color = colors.ink))
                }
                Text(site, style = SmithType.caption.copy(color = colors.inkMuted))
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    members.forEach { m ->
                        val dotColor = when (m.status) {
                            ClockStatus.ON_CLOCK -> colors.statusOnline
                            ClockStatus.ON_BREAK -> colors.accent
                            ClockStatus.OFF_CLOCK -> colors.inkMuted
                        }
                        Text("●", style = SmithType.caption.copy(color = dotColor))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("$activeOnSite/${members.size}", style = SmithType.caption.copy(color = colors.inkMuted).tabular)
                }
                Text(
                    "[x]",
                    style = SmithType.caption.copy(color = colors.accent),
                    modifier = Modifier.clickable(onClick = onClose).padding(start = 4.dp)
                )
            }
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(colors.line))

        if (members.isEmpty()) {
            Text("No crew on site.", style = SmithType.caption.copy(color = colors.inkMuted))
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                members.forEach { member ->
                    val dotColor = when (member.status) {
                        ClockStatus.ON_CLOCK -> colors.statusOnline
                        ClockStatus.ON_BREAK -> colors.accent
                        ClockStatus.OFF_CLOCK -> colors.inkMuted
                    }
                    val hoursText = if (member.clockInTime != null) {
                        val mins = (System.currentTimeMillis() - member.clockInTime) / 60_000
                        "${mins / 60}h ${mins % 60}m"
                    } else "--"

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Tokens2.RadiusOps))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = rememberRipple(bounded = true),
                                onClick = { onCrewTap(member) }
                            )
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("(●)", style = SmithType.caption.copy(color = dotColor))
                            Text(
                                "${member.name} · ${member.trade}",
                                style = SmithType.bodySmall.copy(color = colors.ink)
                            )
                        }
                        Text(hoursText, style = SmithType.caption.copy(color = colors.accent).tabular)
                    }
                }
            }
        }
    }
}

@Composable
internal fun JobDetailPanel(
    modifier: Modifier = Modifier,
    job: Job,
    onJumpToJob: () -> Unit,
    onClose: () -> Unit
) {
    val colors = LocalSmithColors.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(9.dp)
            .background(colors.bgPanel, RoundedCornerShape(Tokens2.RadiusOps))
            .border(1.dp, colors.line, RoundedCornerShape(Tokens2.RadiusOps))
            .clip(RoundedCornerShape(Tokens2.RadiusOps))
            .padding(9.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    job.clientName ?: job.title,
                    style = SmithType.bodySmall.copy(color = colors.ink)
                )
                Text(
                    "${job.stage.displayName} · ${job.clientAddress}",
                    style = SmithType.caption.copy(color = colors.inkMuted)
                )
                if (job.crew.isNotEmpty()) {
                    Text(
                        "Assigned: ${job.crew.joinToString(", ") { it.name }}",
                        style = SmithType.caption.copy(color = colors.statusOnline)
                    )
                } else {
                    Text(
                        "No crew assigned",
                        style = SmithType.caption.copy(color = colors.inkMuted)
                    )
                }
            }
            Text(
                "[x]",
                style = SmithType.caption.copy(color = colors.accent),
                modifier = Modifier.clickable(onClick = onClose).padding(start = 4.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                "[OPEN JOB >]",
                style = SmithType.action.copy(color = colors.accent),
                modifier = Modifier.clickable(onClick = onJumpToJob).padding(4.dp)
            )
        }
    }
}
