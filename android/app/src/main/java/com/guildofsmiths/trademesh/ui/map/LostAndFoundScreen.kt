package com.guildofsmiths.trademesh.ui.map

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.guildofsmiths.trademesh.data.CrewPresenceRepository
import com.guildofsmiths.trademesh.data.LocationTrailRepository
import com.guildofsmiths.trademesh.db.LocationPointEntity
import com.guildofsmiths.trademesh.ui.ConsoleHeader
import com.guildofsmiths.trademesh.ui.Tokens2
import com.guildofsmiths.trademesh.ui.theme2.LocalSmithColors
import com.guildofsmiths.trademesh.ui.theme2.SmithType
import com.guildofsmiths.trademesh.ui.theme2.tabular
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun LostAndFoundScreen(
    targetUserId: String,
    onBack: () -> Unit
) {
    val colors = LocalSmithColors.current
    val context = LocalContext.current
    val crew by CrewPresenceRepository.crew.collectAsState()
    val member = remember(crew, targetUserId) {
        crew.firstOrNull { it.userId == targetUserId }
    }

    var lastKnown by remember { mutableStateOf<LocationPointEntity?>(null) }
    var trail by remember { mutableStateOf<List<LocationPointEntity>>(emptyList()) }

    LaunchedEffect(targetUserId) {
        lastKnown = LocationTrailRepository.getLastKnown(targetUserId)
        trail = LocationTrailRepository.getRecent(targetUserId, limit = 50)
    }

    Column(modifier = Modifier.fillMaxSize().background(colors.bgBase)) {
        ConsoleHeader(title = "LOST & FOUND", onBackClick = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(9.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                member?.name ?: targetUserId,
                style = SmithType.bodyBold.copy(color = colors.ink)
            )
            if (member?.trade != null) {
                Text(
                    "${member.trade} · ${member.status.label}",
                    style = SmithType.caption.copy(color = colors.inkMuted)
                )
            }

            val lk = lastKnown
            if (lk == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.bgPanel, RoundedCornerShape(Tokens2.RadiusOps))
                        .border(1.dp, colors.line, RoundedCornerShape(Tokens2.RadiusOps))
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No location history yet.", style = SmithType.caption.copy(color = colors.inkMuted))
                }
            } else {
                val age = formatAge(System.currentTimeMillis() - lk.timestamp)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.bgPanel, RoundedCornerShape(Tokens2.RadiusOps))
                        .border(1.dp, colors.line, RoundedCornerShape(Tokens2.RadiusOps))
                        .padding(9.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text("LAST SEEN", style = SmithType.captionBold.copy(color = colors.inkMuted))
                    Text("$age ago", style = SmithType.bodyBold.copy(color = colors.accent).tabular)
                    Text(
                        "${String.format("%.6f", lk.latitude)}, ${String.format("%.6f", lk.longitude)}",
                        style = SmithType.caption.copy(color = colors.ink).tabular
                    )
                    lk.accuracyMeters?.let {
                        Text("Accuracy: ${String.format("%.0f", it)} m · source: ${lk.source}",
                            style = SmithType.caption.copy(color = colors.inkMuted))
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .background(colors.accent.copy(alpha = 0.14f), RoundedCornerShape(Tokens2.RadiusOps))
                                .border(1.dp, colors.line, RoundedCornerShape(Tokens2.RadiusOps))
                                .clip(RoundedCornerShape(Tokens2.RadiusOps))
                                .clickable {
                                    val uri = Uri.parse("geo:${lk.latitude},${lk.longitude}?q=${lk.latitude},${lk.longitude}(${member?.name ?: "Last seen"})")
                                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("[Directions]", style = SmithType.action.copy(color = colors.accent))
                        }
                        if (member?.phone != null) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(colors.bgPanel, RoundedCornerShape(Tokens2.RadiusOps))
                                    .border(1.dp, colors.line, RoundedCornerShape(Tokens2.RadiusOps))
                                    .clip(RoundedCornerShape(Tokens2.RadiusOps))
                                    .clickable {
                                        context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${member.phone}")))
                                    }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("[Call]", style = SmithType.action.copy(color = colors.ink))
                            }
                        }
                    }
                }
            }

            // Trail
            if (trail.isNotEmpty()) {
                Text("TRAIL (last ${trail.size} points)",
                    style = SmithType.captionBold.copy(color = colors.inkMuted))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.bgPanel, RoundedCornerShape(Tokens2.RadiusOps))
                        .border(1.dp, colors.line, RoundedCornerShape(Tokens2.RadiusOps))
                        .padding(vertical = 4.dp)
                ) {
                    val fmt = SimpleDateFormat("MMM d · HH:mm:ss", Locale.US)
                    trail.sortedByDescending { it.timestamp }.forEach { p ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val uri = Uri.parse("geo:${p.latitude},${p.longitude}?q=${p.latitude},${p.longitude}")
                                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(fmt.format(Date(p.timestamp)),
                                style = SmithType.caption.copy(color = colors.ink).tabular)
                            Text(
                                "${String.format("%.5f", p.latitude)}, ${String.format("%.5f", p.longitude)}",
                                style = SmithType.caption.copy(color = colors.inkMuted).tabular
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

private fun formatAge(ms: Long): String {
    val sec = ms / 1000
    val min = sec / 60
    val hr = min / 60
    val day = hr / 24
    return when {
        sec < 60 -> "${sec}s"
        min < 60 -> "${min}m"
        hr < 24 -> "${hr}h"
        else -> "${day}d"
    }
}
