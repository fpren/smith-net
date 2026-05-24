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
import com.guildofsmiths.trademesh.ui.ConsoleTheme
import com.guildofsmiths.trademesh.ui.jobboard.Job

@Composable
internal fun SiteDetailPanel(
    modifier: Modifier = Modifier,
    site: String,
    members: List<CrewPresenceInfo>,
    onCrewTap: (CrewPresenceInfo) -> Unit,
    onClose: () -> Unit
) {
    val activeOnSite = members.count { it.status == ClockStatus.ON_CLOCK }
    val jobTitle = members.firstOrNull()?.currentJobTitle

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp)
            .background(ConsoleTheme.surface, RoundedCornerShape(4.dp))
            .border(0.5.dp, ConsoleTheme.text.copy(alpha = 0.06f), RoundedCornerShape(4.dp))
            .clip(RoundedCornerShape(4.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (jobTitle != null) {
                    Text(jobTitle, style = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.text))
                }
                Text(site, style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    members.forEach { m ->
                        val dotColor = when (m.status) {
                            ClockStatus.ON_CLOCK -> ConsoleTheme.success
                            ClockStatus.ON_BREAK -> ConsoleTheme.accent
                            ClockStatus.OFF_CLOCK -> ConsoleTheme.textMuted
                        }
                        Text("●", style = ConsoleTheme.caption.copy(color = dotColor))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("$activeOnSite/${members.size}", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
                }
                Text(
                    "[x]",
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.accent),
                    modifier = Modifier.clickable(onClick = onClose).padding(start = 4.dp)
                )
            }
        }

        Box(Modifier.fillMaxWidth().height(0.5.dp).background(ConsoleTheme.text.copy(alpha = 0.06f)))

        if (members.isEmpty()) {
            Text("No crew on site.", style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted))
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
                        ClockStatus.ON_CLOCK -> ConsoleTheme.success
                        ClockStatus.ON_BREAK -> ConsoleTheme.accent
                        ClockStatus.OFF_CLOCK -> ConsoleTheme.textMuted
                    }
                    val hoursText = if (member.clockInTime != null) {
                        val mins = (System.currentTimeMillis() - member.clockInTime) / 60_000
                        "${mins / 60}h ${mins % 60}m"
                    } else "--"

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
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
                            Text("(●)", style = ConsoleTheme.caption.copy(color = dotColor))
                            Text(
                                "${member.name} · ${member.trade}",
                                style = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.text)
                            )
                        }
                        Text(hoursText, style = ConsoleTheme.caption.copy(color = ConsoleTheme.accent))
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
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp)
            .background(ConsoleTheme.surface, RoundedCornerShape(4.dp))
            .border(0.5.dp, ConsoleTheme.text.copy(alpha = 0.06f), RoundedCornerShape(4.dp))
            .clip(RoundedCornerShape(4.dp))
            .padding(12.dp),
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
                    style = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.text)
                )
                Text(
                    "${job.stage.displayName} · ${job.clientAddress}",
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                )
                if (job.crew.isNotEmpty()) {
                    Text(
                        "Assigned: ${job.crew.joinToString(", ") { it.name }}",
                        style = ConsoleTheme.caption.copy(color = ConsoleTheme.success)
                    )
                } else {
                    Text(
                        "No crew assigned",
                        style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                    )
                }
            }
            Text(
                "[x]",
                style = ConsoleTheme.caption.copy(color = ConsoleTheme.accent),
                modifier = Modifier.clickable(onClick = onClose).padding(start = 4.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                "[OPEN JOB >]",
                style = ConsoleTheme.action.copy(color = ConsoleTheme.accent),
                modifier = Modifier.clickable(onClick = onJumpToJob).padding(4.dp)
            )
        }
    }
}
