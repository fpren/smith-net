package com.guildofsmiths.trademesh.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guildofsmiths.trademesh.data.BeaconRepository
import com.guildofsmiths.trademesh.data.Channel
import com.guildofsmiths.trademesh.data.ChannelType
import com.guildofsmiths.trademesh.data.ClientRepository
import com.guildofsmiths.trademesh.data.ColleagueRepository
import com.guildofsmiths.trademesh.data.PeerRepository
import com.guildofsmiths.trademesh.data.SupabaseAuth
import com.guildofsmiths.trademesh.engine.BoundaryEngine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// ═════════════════════════════════════════════════════════════════════
// CONTACT ROLE — categorizes each conversation
// ═════════════════════════════════════════════════════════════════════

enum class ContactRole { CREW, CLIENT, DISPATCH, AI, UNKNOWN }

/**
 * ChatListScreen — Hybrid layout (A+C).
 *
 * Quick contacts strip on top + filter tabs (ALL/CREW/CLIENTS/DISPATCH)
 * + mesh peers bar + conversation list. SmithAI always first in strip.
 */
@Composable
fun ChatListScreen(
    onChannelClick: (beaconId: String, channelId: String) -> Unit,
    onNewClick: () -> Unit,
    onBackClick: () -> Unit,
    onPeersClick: () -> Unit = {},
    onSmithAIClick: () -> Unit = {},
    onIncomingClick: () -> Unit = {},
    typingState: Map<String, Boolean> = emptyMap(),
    modifier: Modifier = Modifier
) {
    val beacons by BeaconRepository.beacons.collectAsState()
    val isMeshConnected by BoundaryEngine.isMeshConnected.collectAsState()
    val isNetOnline by BoundaryEngine.isOnline.collectAsState()
    val isOnline = isNetOnline
    val currentUser by SupabaseAuth.currentUser.collectAsState()
    val currentUserId = currentUser?.id ?: ""
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(CommTab.ALL) }
    var channelToDelete by remember { mutableStateOf<Pair<String, Channel>?>(null) }

    // Flatten all visible channels across all beacons, sorted by most recent message
    val allChannels: List<Pair<String, Channel>> = remember(beacons) {
        beacons
            .flatMap { beacon ->
                beacon.channels
                    .filter { it.isVisible() }
                    .map { beacon.id to it }
            }
            .sortedByDescending { (_, ch) -> ch.lastMessageTime ?: ch.createdAt }
    }

    // Tag each channel with a role
    val taggedChannels = remember(allChannels) {
        allChannels.map { (beaconId, channel) ->
            Triple(beaconId, channel, resolveRole(channel))
        }
    }

    // Filter by selected tab
    val filteredChannels = remember(taggedChannels, selectedTab) {
        when (selectedTab) {
            CommTab.ALL -> taggedChannels
            CommTab.CREW -> taggedChannels.filter { it.third == ContactRole.CREW || (it.second.type != ChannelType.DM && it.second.id != "general") }
            CommTab.CLIENTS -> taggedChannels.filter { it.third == ContactRole.CLIENT }
            CommTab.DISPATCH -> taggedChannels.filter { it.third == ContactRole.DISPATCH }
        }
    }

    // Badge counts per tab
    val crewUnread = taggedChannels.filter { it.third == ContactRole.CREW }.sumOf { it.second.unreadCount }
    val clientUnread = taggedChannels.filter { it.third == ContactRole.CLIENT }.sumOf { it.second.unreadCount }
    val dispatchUnread = taggedChannels.filter { it.third == ContactRole.DISPATCH }.sumOf { it.second.unreadCount }
    val totalUnread = taggedChannels.sumOf { it.second.unreadCount }

    // Incoming = unread DMs from people not in your network (stranger cross-org
    // DMs auto-open; this surfaces them). Drives the header [Inbox N] entry.
    val incomingCount = taggedChannels.count { (_, ch, role) ->
        ch.type == ChannelType.DM && role != ContactRole.CREW && ch.unreadCount > 0
    }

    // Mesh peer count
    val meshPeerCount = remember(isMeshConnected) {
        if (isMeshConnected) PeerRepository.getActivePeers().size else 0
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
                title = "COMM",
                subtitle = when {
                    isOnline -> "online"
                    isMeshConnected && meshPeerCount > 0 -> "mesh · $meshPeerCount peer${if (meshPeerCount != 1) "s" else ""}"
                    isMeshConnected -> "mesh · scanning"
                    else -> "offline"
                },
                onBackClick = onBackClick,
                actionText = if (incomingCount > 0) "[Inbox $incomingCount]" else "[Inbox]",
                onActionClick = onIncomingClick,
                modifier = Modifier.background(ConsoleTheme.surface)
            )

            ConsoleSeparator()

            // ── Quick Contacts Strip ──
            QuickContactsStrip(
                channels = allChannels,
                isMeshConnected = isMeshConnected,
                onChannelClick = onChannelClick,
                onSmithAIClick = onSmithAIClick
            )

            ConsoleSeparator()

            // ── Filter Tabs ──
            CommTabs(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                totalUnread = totalUnread,
                crewUnread = crewUnread,
                clientUnread = clientUnread,
                dispatchUnread = dispatchUnread
            )

            // ── Mesh Bar ──
            if (isMeshConnected) {
                MeshBar(peerCount = meshPeerCount, onClick = onPeersClick)
            }

            ConsoleSeparator()

            // ── Conversation List ──
            if (filteredChannels.isEmpty()) {
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
                            text = "tap [+ NEW] to message a client or colleague",
                            style = ConsoleTheme.caption
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(
                        filteredChannels,
                        key = { (beaconId, ch, _) -> "$beaconId:${ch.id}" }
                    ) { (beaconId, channel, _) ->
                        SwipeableChatRow(
                            channel = channel,
                            isOwner = channel.isOwner(currentUserId),
                            isArchived = channel.isArchived,
                            isTyping = typingState[channel.id] == true,
                            isMeshConnected = isMeshConnected,
                            onClick = { onChannelClick(beaconId, channel.id) },
                            onArchive = { BeaconRepository.archiveChannel(beaconId, channel.id, currentUserId) },
                            onUnarchive = { BeaconRepository.unarchiveChannel(beaconId, channel.id, currentUserId) },
                            onDelete = { channelToDelete = beaconId to channel }
                        )
                        ConsoleSeparator()
                    }
                }
            }
        }
    }

    channelToDelete?.let { (beaconId, channel) ->
        DeleteChatDialog(
            channel = channel,
            onConfirm = {
                scope.launch {
                    BeaconRepository.deleteChannel(beaconId, channel.id, currentUserId)
                    BoundaryEngine.broadcastChannelDeletion(channel.id, channel.name)
                }
                channelToDelete = null
            },
            onDismiss = { channelToDelete = null }
        )
    }
}

@Composable
private fun DeleteChatDialog(
    channel: Channel,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val isDm = channel.type == ChannelType.DM
    val title = if (isDm) "Hide DM?" else "Delete #${channel.name}?"
    val body = if (isDm) {
        "Hides this DM from your list. The other participant keeps their copy."
    } else {
        "Deletes the channel and all its messages. This cannot be undone."
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = ConsoleTheme.bodyBold) },
        text = { Text(body, style = ConsoleTheme.bodySmall) },
        confirmButton = {
            Text(
                text = if (isDm) "[HIDE]" else "[DELETE]",
                style = ConsoleTheme.action.copy(color = ConsoleTheme.accent),
                modifier = Modifier
                    .clickable(onClick = onConfirm)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        },
        dismissButton = {
            Text(
                text = "[CANCEL]",
                style = ConsoleTheme.action,
                modifier = Modifier
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        },
        containerColor = ConsoleTheme.surface
    )
}

// ═════════════════════════════════════════════════════════════════════
// QUICK CONTACTS STRIP
// ═════════════════════════════════════════════════════════════════════

@Composable
private fun QuickContactsStrip(
    channels: List<Pair<String, Channel>>,
    isMeshConnected: Boolean,
    onChannelClick: (beaconId: String, channelId: String) -> Unit,
    onSmithAIClick: () -> Unit
) {
    val scrollState = rememberScrollState()

    val agentState by com.guildofsmiths.trademesh.ai.AgentInitializer.agentState.collectAsState()
    val modelState by com.guildofsmiths.trademesh.ai.LlamaInference.modelState.collectAsState()
    val backend = com.guildofsmiths.trademesh.ai.SmithAIBackendRouter.pick()
    val (smithLabel, smithStatusColor) = when {
        agentState == com.guildofsmiths.trademesh.ai.AgentState.WAKING -> "Waking" to ConsoleTheme.textDim
        backend == com.guildofsmiths.trademesh.ai.SmithAIBackendRouter.Backend.ON_DEVICE &&
            modelState == com.guildofsmiths.trademesh.ai.ModelState.READY -> "AI Ready" to ConsoleTheme.accent
        backend == com.guildofsmiths.trademesh.ai.SmithAIBackendRouter.Backend.CLOUD -> "Cloud AI" to ConsoleTheme.success
        else -> "AI Offline" to ConsoleTheme.error
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ConsoleTheme.surface)
            .horizontalScroll(scrollState)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // SmithAI always first
        QuickContact(
            initials = "AI",
            name = smithLabel,
            avatarBrush = Brush.linearGradient(listOf(ConsoleTheme.accent, Color(0xFF8C6B2A))),
            statusColor = smithStatusColor,
            showStatus = true,
            onClick = onSmithAIClick
        )

        // Recent contacts from channels (DMs only, sorted by last message)
        val dmChannels = channels
            .filter { (_, ch) -> ch.type == ChannelType.DM }
            .take(7)

        dmChannels.forEach { (beaconId, channel) ->
            QuickContact(
                initials = chatInitials(channel),
                name = channel.name.split(" ").firstOrNull() ?: channel.name.take(6),
                avatarColor = avatarColorFor(channel),
                statusColor = when {
                    isMeshConnected -> ConsoleTheme.accent  // gold = mesh
                    else -> ConsoleTheme.success             // green = online
                },
                showStatus = true,
                onClick = { onChannelClick(beaconId, channel.id) }
            )
        }
    }
}

@Composable
private fun QuickContact(
    initials: String,
    name: String,
    avatarColor: Color = ConsoleTheme.accent,
    avatarBrush: Brush? = null,
    statusColor: Color = ConsoleTheme.textDim,
    showStatus: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Box(modifier = Modifier.size(32.dp)) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .then(
                        if (avatarBrush != null) Modifier.background(avatarBrush)
                        else Modifier.background(avatarColor)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    style = ConsoleTheme.captionBold.copy(
                        color = ConsoleTheme.surface,
                        fontSize = 10.sp
                    )
                )
            }
            if (showStatus) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(ConsoleTheme.background)
                        .align(Alignment.BottomEnd)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(statusColor)
                            .align(Alignment.Center)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = name,
            style = ConsoleTheme.caption.copy(fontSize = 8.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ═════════════════════════════════════════════════════════════════════
// FILTER TABS
// ═════════════════════════════════════════════════════════════════════

private enum class CommTab { ALL, CREW, CLIENTS, DISPATCH }

@Composable
private fun CommTabs(
    selectedTab: CommTab,
    onTabSelected: (CommTab) -> Unit,
    totalUnread: Int,
    crewUnread: Int,
    clientUnread: Int,
    dispatchUnread: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ConsoleTheme.surface),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        CommTabItem("ALL", totalUnread, selectedTab == CommTab.ALL) { onTabSelected(CommTab.ALL) }
        CommTabItem("CREW", crewUnread, selectedTab == CommTab.CREW) { onTabSelected(CommTab.CREW) }
        CommTabItem("CLIENTS", clientUnread, selectedTab == CommTab.CLIENTS) { onTabSelected(CommTab.CLIENTS) }
        CommTabItem("DISPATCH", dispatchUnread, selectedTab == CommTab.DISPATCH) { onTabSelected(CommTab.DISPATCH) }
    }
}

@Composable
private fun CommTabItem(
    label: String,
    unreadCount: Int,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = ConsoleTheme.captionBold.copy(
                    fontSize = 10.sp,
                    color = if (isSelected) ConsoleTheme.accent else ConsoleTheme.textMuted,
                    letterSpacing = 0.5.sp
                )
            )
            if (unreadCount > 0) {
                Spacer(modifier = Modifier.width(3.dp))
                Box(
                    modifier = Modifier
                        .background(ConsoleTheme.accent, CircleShape)
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                        style = ConsoleTheme.caption.copy(
                            color = Color.White,
                            fontSize = 7.sp
                        )
                    )
                }
            }
        }
        if (isSelected) {
            Spacer(modifier = Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .size(width = 40.dp, height = 2.dp)
                    .background(ConsoleTheme.accent)
            )
        } else {
            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

// ═════════════════════════════════════════════════════════════════════
// MESH BAR
// ═════════════════════════════════════════════════════════════════════

@Composable
private fun MeshBar(peerCount: Int, onClick: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "mesh_pulse")
    val alpha by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.4f,
        animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
        label = "pulse"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ConsoleTheme.accent.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(ConsoleTheme.accent.copy(alpha = alpha))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "MESH · $peerCount peer${if (peerCount != 1) "s" else ""} nearby",
            style = ConsoleTheme.caption.copy(
                color = ConsoleTheme.accent,
                fontSize = 9.sp,
                letterSpacing = 0.5.sp
            ),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "›",
            style = ConsoleTheme.body.copy(color = ConsoleTheme.accent)
        )
    }
}

// ═════════════════════════════════════════════════════════════════════
// CHAT ROW
// ═════════════════════════════════════════════════════════════════════

/**
 * Wraps ChatRow in a horizontal swipe gesture. Swipe right reveals DELETE,
 * swipe left reveals ARCHIVE/RESTORE. `#general` has no swipe (system channel).
 * DELETE is suppressed for non-owners on non-DM channels (only owners + DM peers
 * can dismiss a conversation). ARCHIVE always available — it's local-only.
 */
@Composable
private fun SwipeableChatRow(
    channel: Channel,
    isOwner: Boolean,
    isArchived: Boolean,
    isTyping: Boolean,
    isMeshConnected: Boolean,
    onClick: () -> Unit,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit,
    onDelete: () -> Unit,
) {
    val canSwipe = channel.id != "general"
    val canDelete = channel.type == ChannelType.DM || isOwner

    var offsetX by remember { mutableFloatStateOf(0f) }
    val swipeThreshold = with(LocalDensity.current) { 80.dp.toPx() }
    val animatedOffset by animateFloatAsState(targetValue = offsetX, label = "chatSwipeOffset")

    Box(modifier = Modifier.fillMaxWidth()) {
        if (canSwipe) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(ConsoleTheme.surface),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .then(if (canDelete) Modifier.clickable(onClick = onDelete) else Modifier)
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (canDelete) {
                        Text(
                            text = if (channel.type == ChannelType.DM) "HIDE" else "DELETE",
                            style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.textDim)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = if (isArchived) onUnarchive else onArchive)
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Text(
                        text = if (isArchived) "RESTORE" else "ARCHIVE",
                        style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.textDim)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .offset { IntOffset(animatedOffset.roundToInt(), 0) }
                .background(ConsoleTheme.background)
                .then(
                    if (canSwipe) {
                        Modifier.draggable(
                            orientation = Orientation.Horizontal,
                            state = rememberDraggableState { delta ->
                                offsetX = (offsetX + delta).coerceIn(-swipeThreshold, swipeThreshold)
                            },
                            onDragStopped = {
                                when {
                                    offsetX > swipeThreshold * 0.6f && canDelete -> onDelete()
                                    offsetX < -swipeThreshold * 0.6f ->
                                        if (isArchived) onUnarchive() else onArchive()
                                }
                                offsetX = 0f
                            }
                        )
                    } else Modifier
                )
        ) {
            ChatRow(
                channel = channel,
                isTyping = isTyping,
                isMeshConnected = isMeshConnected,
                onClick = onClick
            )
        }
    }
}

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
        // Avatar with status dot (photo when available, initials fallback)
        com.guildofsmiths.trademesh.ui.components.SmithAvatar(
            name = chatDisplayName(channel),
            size = 44,
            statusColor = if (isMeshConnected) ConsoleTheme.success else ConsoleTheme.textDim
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
                val marker = when {
                    channel.unreadCount > 0 && channel.lastMessageOutgoing != true -> "[x]"
                    channel.lastMessageOutgoing == true -> "[>]"
                    else -> "[<]"
                }
                val markerColor = when (marker) {
                    "[x]" -> ConsoleTheme.error
                    "[>]" -> ConsoleTheme.textDim
                    else -> ConsoleTheme.textMuted
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = marker,
                        style = ConsoleTheme.commTimestamp.copy(color = markerColor)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = channel.lastMessagePreview,
                        style = ConsoleTheme.caption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
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

// ═════════════════════════════════════════════════════════════════════
// AVATAR
// ═════════════════════════════════════════════════════════════════════

@Composable
private fun ChatAvatar(
    channel: Channel,
    isOnline: Boolean,
    modifier: Modifier = Modifier
) {
    val initials = chatInitials(channel)
    val avatarColor = avatarColorFor(channel)

    Box(modifier = modifier.size(44.dp)) {
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

        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(ConsoleTheme.background)
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

// ═════════════════════════════════════════════════════════════════════
// UNREAD BADGE
// ═════════════════════════════════════════════════════════════════════

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

// ═════════════════════════════════════════════════════════════════════
// ROLE RESOLUTION
// ═════════════════════════════════════════════════════════════════════

/**
 * Resolve a channel's contact role by checking repositories.
 */
private fun resolveRole(channel: Channel): ContactRole {
    if (channel.id == "smithai" || channel.name.lowercase().contains("smithai")) {
        return ContactRole.AI
    }
    if (channel.name.lowercase().contains("dispatch") || channel.name.lowercase().contains("hq")) {
        return ContactRole.DISPATCH
    }
    if (channel.type == ChannelType.DM) {
        val peerName = channel.name.trim().lowercase()
        // Check colleague repository
        val colleagues = ColleagueRepository.getAll()
        if (colleagues.any { it.name.lowercase() == peerName || it.id in channel.members }) {
            return ContactRole.CREW
        }
        // Check crew presence (simulated team members like Dana W.)
        val crewMembers = com.guildofsmiths.trademesh.data.CrewPresenceRepository.getCrew()
        if (crewMembers.any { it.name.lowercase() == peerName }) {
            return ContactRole.CREW
        }
        // Check if the channel ID contains "client_" prefix (from NewConversationScreen DM creation)
        if (channel.id.contains("client_")) {
            return ContactRole.CLIENT
        }
        // Default unrecognized DMs to CLIENT (most DMs for solo are with clients)
        return ContactRole.CLIENT
    }
    // Group channels are crew
    if (channel.type == ChannelType.GROUP || channel.type == ChannelType.BROADCAST) {
        return ContactRole.CREW
    }
    return ContactRole.UNKNOWN
}

// ═════════════════════════════════════════════════════════════════════
// HELPERS
// ═════════════════════════════════════════════════════════════════════

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
 */
private fun avatarColorFor(channel: Channel): Color {
    val palette = listOf(
        ConsoleTheme.accent,
        ConsoleTheme.success,
        ConsoleTheme.warning,
        ConsoleTheme.error,
        Color(0xFF6B5E8C),
        Color(0xFF3A6B8C),
    )
    val index = Math.abs(channel.id.hashCode()) % palette.size
    return palette[index]
}

/**
 * Relative timestamp — "now" / "5m" / "2:45pm" / "Mar 14"
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

// ═════════════════════════════════════════════════════════════════════
// PREVIEW
// ═════════════════════════════════════════════════════════════════════

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
