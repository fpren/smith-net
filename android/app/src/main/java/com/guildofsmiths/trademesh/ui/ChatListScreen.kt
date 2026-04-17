package com.guildofsmiths.trademesh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guildofsmiths.trademesh.data.BeaconRepository
import com.guildofsmiths.trademesh.data.Channel
import com.guildofsmiths.trademesh.data.ChannelType
import com.guildofsmiths.trademesh.engine.BoundaryEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * ChatListScreen — Flat WhatsApp-style conversation list.
 *
 * Collapses the old Beacon → Channel hierarchy into a single scrollable list,
 * sorted by most recent message. Each row shows avatar, name, preview and
 * unread badge. Part of the Messenger Cleanup (Task 4).
 */
@Composable
fun ChatListScreen(
    onChannelClick: (beaconId: String, channelId: String) -> Unit,
    onNewClick: () -> Unit,
    onBackClick: () -> Unit,
    // Typing states: keyed by channelId → true if someone is typing.
    // Wired in Task 9; pass emptyMap() until then.
    typingState: Map<String, Boolean> = emptyMap(),
    modifier: Modifier = Modifier
) {
    val beacons by BeaconRepository.beacons.collectAsState()
    val isMeshConnected by BoundaryEngine.isMeshConnected.collectAsState()

    // Flatten all visible channels across all beacons, sorted by most recent message.
    val allChannels: List<Pair<String, Channel>> = remember(beacons) {
        beacons
            .flatMap { beacon ->
                beacon.channels
                    .filter { it.isVisible() }
                    .map { beacon.id to it }
            }
            .sortedByDescending { (_, ch) -> ch.lastMessageTime ?: ch.createdAt }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ConsoleTheme.background,
        floatingActionButton = {
            Text(
                text = "[+ NEW]",
                style = ConsoleTheme.action,
                modifier = Modifier
                    .clickable(onClick = onNewClick)
                    .background(ConsoleTheme.accent)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                color = ConsoleTheme.surface
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Header
            ConsoleHeader(
                title = "MESSAGES",
                subtitle = if (isMeshConnected) "mesh connected" else null,
                onBackClick = onBackClick,
                modifier = Modifier.background(ConsoleTheme.surface)
            )

            ConsoleSeparator()

            // Conversation list
            if (allChannels.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "no conversations yet",
                            style = ConsoleTheme.bodySmall
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "tap [+ NEW] to start one",
                            style = ConsoleTheme.caption
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(allChannels, key = { (beaconId, ch) -> "$beaconId:${ch.id}" }) { (beaconId, channel) ->
                        ChatRow(
                            channel = channel,
                            isTyping = typingState[channel.id] == true,
                            isMeshConnected = isMeshConnected,
                            onClick = { onChannelClick(beaconId, channel.id) }
                        )
                        ConsoleSeparator()
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ChatRow
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ChatRow(
    channel: Channel,
    isTyping: Boolean,
    isMeshConnected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar with status dot
        ChatAvatar(
            channel = channel,
            isOnline = isMeshConnected
        )

        Spacer(modifier = Modifier.width(12.dp))

        // Name + preview
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = chatDisplayName(channel),
                style = ConsoleTheme.bodyBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            if (isTyping) {
                Text(
                    text = "typing...",
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.accent),
                    maxLines = 1
                )
            } else if (channel.lastMessagePreview != null) {
                Text(
                    text = channel.lastMessagePreview,
                    style = ConsoleTheme.caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    text = "no messages yet",
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.textDim),
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Timestamp + unread badge
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (channel.lastMessageTime != null) {
                Text(
                    text = chatFormatTime(channel.lastMessageTime),
                    style = ConsoleTheme.timestamp
                )
            }

            if (channel.unreadCount > 0) {
                UnreadBadge(count = channel.unreadCount)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Avatar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ChatAvatar(
    channel: Channel,
    isOnline: Boolean,
    modifier: Modifier = Modifier
) {
    val initials = chatInitials(channel)
    val avatarColor = avatarColorFor(channel)

    Box(modifier = modifier.size(44.dp)) {
        // Colored circle
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(avatarColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initials,
                style = ConsoleTheme.captionBold.copy(
                    color = ConsoleTheme.surface,
                    fontSize = 14.sp
                )
            )
        }

        // Status dot — bottom-right corner
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(ConsoleTheme.background)  // Ring using background color
                .align(Alignment.BottomEnd)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (isOnline) ConsoleTheme.success else ConsoleTheme.textDim
                    )
                    .align(Alignment.Center)
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Unread badge
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun UnreadBadge(count: Int) {
    Box(
        modifier = Modifier
            .size(18.dp)
            .clip(CircleShape)
            .background(ConsoleTheme.accent),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            style = ConsoleTheme.caption.copy(
                color = Color.White,
                fontSize = 9.sp
            )
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helpers
// ─────────────────────────────────────────────────────────────────────────────

/** Display name: "@person" for DMs, "#channel" for groups/broadcast. */
private fun chatDisplayName(channel: Channel): String = when (channel.type) {
    ChannelType.DM -> "@${channel.name}"
    else -> "#${channel.name}"
}

/**
 * 2-letter initials.
 * DMs: first letters of each word in person's name ("Jay Smith" → "JS").
 * Groups: first 2 chars of channel name, uppercased.
 */
private fun chatInitials(channel: Channel): String {
    return when (channel.type) {
        ChannelType.DM -> {
            val parts = channel.name.trim().split(Regex("\\s+"))
            when {
                parts.size >= 2 -> "${parts[0].firstOrNull() ?: ""}${parts[1].firstOrNull() ?: ""}".uppercase()
                parts.size == 1 -> parts[0].take(2).uppercase()
                else -> "??"
            }
        }
        else -> channel.name.take(2).uppercase()
    }
}

/**
 * Deterministic accent color for an avatar based on channel id.
 * Cycles through a set of warm palette tones.
 */
private fun avatarColorFor(channel: Channel): Color {
    val palette = listOf(
        ConsoleTheme.accent,           // gold
        ConsoleTheme.success,          // sage
        ConsoleTheme.warning,          // sienna
        ConsoleTheme.error,            // brick
        Color(0xFF6B5E8C),             // dusty purple
        Color(0xFF3A6B8C),             // slate blue
    )
    val index = Math.abs(channel.id.hashCode()) % palette.size
    return palette[index]
}

/**
 * Relative timestamp — mirrors the format used in ChannelListScreen.
 * "now" / "5m" / "2:45pm" / "Mar 14"
 */
private fun chatFormatTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000L -> "now"
        diff < 3_600_000L -> "${diff / 60_000}m"
        diff < 86_400_000L -> SimpleDateFormat("h:mma", Locale.getDefault())
            .format(Date(timestamp))
            .lowercase()
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Preview
// ─────────────────────────────────────────────────────────────────────────────

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun ChatListScreenPreview() {
    MaterialTheme {
        ChatListScreen(
            onChannelClick = { _, _ -> },
            onNewClick = {},
            onBackClick = {}
        )
    }
}
