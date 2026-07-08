package com.guildofsmiths.trademesh.ui.comm

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.guildofsmiths.trademesh.data.BeaconRepository
import com.guildofsmiths.trademesh.data.Channel
import com.guildofsmiths.trademesh.data.ChannelType
import com.guildofsmiths.trademesh.data.ColleagueRepository
import com.guildofsmiths.trademesh.ui.ConsoleHeader
import com.guildofsmiths.trademesh.ui.ConsoleSeparator
import com.guildofsmiths.trademesh.ui.ConsoleTheme
import com.guildofsmiths.trademesh.ui.components.SmithAvatar

/**
 * Incoming & Requests front. Stranger cross-org DMs auto-open (no backend gate),
 * so this surfaces DM channels whose peer is NOT a known colleague:
 *   requests = those with unread, history = the rest.
 */
@Composable
fun IncomingScreen(
    onOpen: (beaconId: String, channelId: String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val beacons by BeaconRepository.beacons.collectAsState()
    var tab by remember { mutableStateOf(0) } // 0 = requests, 1 = history

    val strangerDms: List<Pair<String, Channel>> = remember(beacons) {
        val colleagueIds = ColleagueRepository.getAll().map { it.id }.toSet()
        val colleagueNames = ColleagueRepository.getAll().map { it.name.lowercase() }.toSet()
        beacons.flatMap { beacon ->
            beacon.channels
                .filter { it.isVisible() && it.type == ChannelType.DM }
                .filter { ch ->
                    val knownById = ch.members.any { it in colleagueIds }
                    val knownByName = ch.name.trim().lowercase() in colleagueNames
                    !knownById && !knownByName
                }
                .map { beacon.id to it }
        }.sortedByDescending { (_, ch) -> ch.lastMessageTime ?: ch.createdAt }
    }

    val requests = strangerDms.filter { it.second.unreadCount > 0 }
    val history = strangerDms.filter { it.second.unreadCount == 0 }
    val shown = if (tab == 0) requests else history

    Column(modifier = modifier.fillMaxSize().background(ConsoleTheme.background)) {
        ConsoleHeader(title = "INCOMING", onBackClick = onBack, modifier = Modifier.background(ConsoleTheme.surface))
        ConsoleSeparator()

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SegPill("requests" + if (requests.isNotEmpty()) " ${requests.size}" else "", tab == 0) { tab = 0 }
            SegPill("history", tab == 1) { tab = 1 }
        }

        if (shown.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    if (tab == 0) "No new requests." else "No past requests.",
                    style = ConsoleTheme.commBody.copy(color = ConsoleTheme.textMuted)
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(shown, key = { (b, ch) -> "$b:${ch.id}" }) { (beaconId, ch) ->
                    AnimatedVisibility(visible = true, enter = slideInVertically { it / 2 }) {
                        IncomingRow(ch) { onOpen(beaconId, ch.id) }
                    }
                    ConsoleSeparator()
                }
            }
        }
    }
}

@Composable
private fun SegPill(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = ConsoleTheme.commId.copy(color = if (active) ConsoleTheme.surface else ConsoleTheme.textMuted),
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (active) ConsoleTheme.accent else ConsoleTheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
private fun IncomingRow(channel: Channel, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SmithAvatar(name = channel.name, size = 40, statusColor = ConsoleTheme.success)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(channel.name, style = ConsoleTheme.commName, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                channel.lastMessagePreview ?: "wants to connect",
                style = ConsoleTheme.commTimestamp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (channel.unreadCount > 0) {
            Spacer(Modifier.width(8.dp))
            Text("${channel.unreadCount}", style = ConsoleTheme.commId.copy(color = ConsoleTheme.accent))
        }
    }
}
