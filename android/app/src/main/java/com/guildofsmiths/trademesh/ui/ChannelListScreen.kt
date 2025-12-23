package com.guildofsmiths.trademesh.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.guildofsmiths.trademesh.data.BeaconRepository
import com.guildofsmiths.trademesh.data.Channel
import com.guildofsmiths.trademesh.data.ChannelType
import com.guildofsmiths.trademesh.data.UserPreferences
import com.guildofsmiths.trademesh.engine.BoundaryEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Channel list — bold headers, clean rows with swipe actions for owners.
 * Pull down to refresh and discover new channels.
 */
@Composable
fun ChannelListScreen(
    beaconId: String,
    onChannelClick: (Channel) -> Unit,
    onBackClick: () -> Unit,
    onCreateChannel: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val beacons by BeaconRepository.beacons.collectAsState()
    val beacon = remember(beacons, beaconId) {
        beacons.find { it.id == beaconId }
    }
    val currentUserId = remember { UserPreferences.getUserId() }
    val scope = rememberCoroutineScope()
    
    // State for delete confirmation dialog
    var channelToDelete by remember { mutableStateOf<Channel?>(null) }
    
    // State for showing archived channels
    var showArchived by remember { mutableStateOf(false) }
    
    // Pull-to-refresh state
    var isRefreshing by remember { mutableStateOf(false) }
    var lastRefreshTime by remember { mutableStateOf<Long?>(null) }
    
    // Get visible and archived channels
    val visibleChannels = remember(beacons, beaconId) {
        BeaconRepository.getBeacon(beaconId)?.channels?.filter { it.isVisible() } ?: emptyList()
    }
    val archivedChannels = remember(beacons, beaconId) {
        BeaconRepository.getBeacon(beaconId)?.channels?.filter { it.isArchived && !it.isDeleted } ?: emptyList()
    }
    
    // Delete confirmation dialog
    if (channelToDelete != null) {
        DeleteChannelDialog(
            channelName = channelToDelete!!.name,
            onConfirm = {
                val deletedChannel = channelToDelete!!
                BeaconRepository.deleteChannel(beaconId, deletedChannel.id, currentUserId)
                // Broadcast deletion to peers so they remove it too
                BoundaryEngine.broadcastChannelDeletion(deletedChannel.id, deletedChannel.name)
                channelToDelete = null
            },
            onDismiss = { channelToDelete = null }
        )
    }
    
    // Refresh function - triggers BLE scan for channel invites
    fun onRefresh() {
        scope.launch {
            isRefreshing = true
            
            // Request channel discovery via BLE
            BoundaryEngine.requestChannelDiscovery()
            
            // Brief delay to allow BLE responses
            delay(2000)
            
            lastRefreshTime = System.currentTimeMillis()
            isRefreshing = false
        }
    }
    
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = ConsoleTheme.background,
        floatingActionButton = {
            if (onCreateChannel != null) {
                Text(
                    text = "+ NEW",
                    style = ConsoleTheme.action,
                    modifier = Modifier
                        .clickable(onClick = onCreateChannel)
                        .background(ConsoleTheme.surface)
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleTheme.surface)
                    .clickable(onClick = onBackClick)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "←",
                    style = ConsoleTheme.title
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = beacon?.name?.uppercase() ?: "UNKNOWN",
                        style = ConsoleTheme.title
                    )
                    Text(
                        text = "${visibleChannels.size} channels" + 
                               if (archivedChannels.isNotEmpty()) " · ${archivedChannels.size} archived" else "",
                        style = ConsoleTheme.caption
                    )
                }
                
                // Toggle archived view
                if (archivedChannels.isNotEmpty()) {
                    Text(
                        text = if (showArchived) "ACTIVE" else "ARCHIVED",
                        style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.textMuted),
                        modifier = Modifier
                            .clickable { showArchived = !showArchived }
                            .padding(4.dp)
                    )
                }
            }
            
            // Invite banner
            InviteBanner(
                onAccept = { _, channelId ->
                    beacon?.channels?.find { it.id == channelId }?.let { channel ->
                        onChannelClick(channel)
                    }
                },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
            )
            
            ConsoleSeparator()
            
            // Channel list with pull-to-refresh
            if (beacon != null) {
                val channelsToShow = if (showArchived) archivedChannels else visibleChannels
                
                // Pull-to-refresh state
                var pullOffset by remember { mutableFloatStateOf(0f) }
                val pullThreshold = with(LocalDensity.current) { 80.dp.toPx() }
                val listState = rememberLazyListState()
                
                // Nested scroll connection for pull-to-refresh
                val nestedScrollConnection = remember {
                    object : NestedScrollConnection {
                        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                            // When pulling down and we have pull offset, consume scroll to reduce offset
                            if (pullOffset > 0 && available.y < 0) {
                                val consumed = available.y.coerceAtLeast(-pullOffset)
                                pullOffset += consumed
                                return Offset(0f, consumed)
                            }
                            return Offset.Zero
                        }
                        
                        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                            // When at top and pulling down, accumulate pull offset
                            if (available.y > 0 && listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0) {
                                pullOffset = (pullOffset + available.y).coerceAtMost(pullThreshold * 1.5f)
                                return Offset(0f, available.y)
                            }
                            return Offset.Zero
                        }
                        
                        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                            if (pullOffset > pullThreshold) {
                                onRefresh()
                            }
                            pullOffset = 0f
                            return Velocity.Zero
                        }
                    }
                }
                
                Column(modifier = Modifier.fillMaxSize()) {
                    // Pull indicator - always at top
                    if (pullOffset > 0 || isRefreshing) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isRefreshing) "discovering..." 
                                       else if (pullOffset > pullThreshold) "↑ release to refresh"
                                       else "↓ pull to discover",
                                style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                            )
                        }
                    }
                    
                    if (channelsToShow.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (showArchived) "no archived channels" else "no channels yet",
                                    style = ConsoleTheme.caption
                                )
                                if (!showArchived) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "REFRESH",
                                        style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.accent),
                                        modifier = Modifier
                                            .clickable { onRefresh() }
                                            .padding(8.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(nestedScrollConnection)
                        ) {
                            // Refreshing indicator as first item
                            if (isRefreshing) {
                                item {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 8.dp),
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Text(
                                            text = "discovering...",
                                            style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                                        )
                                    }
                                }
                            }
                            
                            // Separate DMs and Channels
                            val dms = channelsToShow.filter { it.type == ChannelType.DM }
                            val channels = channelsToShow.filter { it.type != ChannelType.DM }
                            
                            // Show channels first
                            if (channels.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "CHANNELS",
                                        style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.textMuted),
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                                    )
                                }
                                
                                items(channels, key = { it.id }) { channel ->
                                    SwipeableChannelRow(
                                        channel = channel,
                                        isOwner = channel.isOwner(currentUserId),
                                        isArchived = showArchived,
                                        onClick = { onChannelClick(channel) },
                                        onArchive = {
                                            BeaconRepository.archiveChannel(beaconId, channel.id, currentUserId)
                                        },
                                        onUnarchive = {
                                            BeaconRepository.unarchiveChannel(beaconId, channel.id, currentUserId)
                                        },
                                        onDelete = {
                                            channelToDelete = channel
                                        },
                                        onInvite = if (channel.type == ChannelType.GROUP && !showArchived) {
                                            { BoundaryEngine.broadcastChannelInvite(channel.id, channel.name) }
                                        } else null
                                    )
                                    ConsoleSeparator()
                                }
                            }
                            
                            // Show DMs section
                            if (dms.isNotEmpty()) {
                                item {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "DIRECT MESSAGES",
                                        style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.textMuted),
                                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                                    )
                                }
                                
                                items(dms, key = { it.id }) { dm ->
                                    SwipeableChannelRow(
                                        channel = dm,
                                        isOwner = dm.members.contains(currentUserId),
                                        isArchived = showArchived,
                                        onClick = { onChannelClick(dm) },
                                        onArchive = {
                                            BeaconRepository.archiveChannel(beaconId, dm.id, currentUserId)
                                        },
                                        onUnarchive = {
                                            BeaconRepository.unarchiveChannel(beaconId, dm.id, currentUserId)
                                        },
                                        onDelete = {
                                            channelToDelete = dm
                                        },
                                        onInvite = null // No invites for DMs
                                    )
                                    ConsoleSeparator()
                                }
                            }
                        }
                    }
                }
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "Beacon not found", style = ConsoleTheme.bodySmall)
                }
            }
        }
    }
}

/**
 * Delete confirmation dialog — console style.
 */
@Composable
private fun DeleteChannelDialog(
    channelName: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ConsoleTheme.background,
        title = {
            Text(
                text = "DELETE CHANNEL",
                style = ConsoleTheme.title
            )
        },
        text = {
            Text(
                text = "Delete #$channelName? This cannot be undone.",
                style = ConsoleTheme.body
            )
        },
        confirmButton = {
            Text(
                text = "DELETE",
                style = ConsoleTheme.action.copy(color = ConsoleTheme.error),
                modifier = Modifier
                    .clickable(onClick = onConfirm)
                    .padding(8.dp)
            )
        },
        dismissButton = {
            Text(
                text = "CANCEL",
                style = ConsoleTheme.action,
                modifier = Modifier
                    .clickable(onClick = onDismiss)
                    .padding(8.dp)
            )
        }
    )
}

/**
 * Swipeable channel row with archive/delete actions.
 * Swipe left: Archive, Swipe right: Delete
 */
@Composable
private fun SwipeableChannelRow(
    channel: Channel,
    isOwner: Boolean,
    isArchived: Boolean,
    onClick: () -> Unit,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit,
    onDelete: () -> Unit,
    onInvite: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // Skip swipe for system channels like #general
    val canSwipe = isOwner && channel.id != "general"
    
    var offsetX by remember { mutableFloatStateOf(0f) }
    val swipeThreshold = with(LocalDensity.current) { 80.dp.toPx() }
    
    // Animate back to center when released
    val animatedOffset by animateFloatAsState(
        targetValue = offsetX,
        label = "swipeOffset"
    )
    
    Box(modifier = modifier.fillMaxWidth()) {
        // Background actions (revealed on swipe)
        if (canSwipe) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(ConsoleTheme.surface),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left side - DELETE (revealed on swipe right)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onDelete)
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = "DELETE",
                        style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.textDim)
                    )
                }
                
                // Right side - ARCHIVE/UNARCHIVE (revealed on swipe left)
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
        
        // Foreground row (draggable)
        Row(
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
                                    offsetX > swipeThreshold * 0.6f -> {
                                        // Swiped right - delete
                                        onDelete()
                                    }
                                    offsetX < -swipeThreshold * 0.6f -> {
                                        // Swiped left - archive/unarchive
                                        if (isArchived) onUnarchive() else onArchive()
                                    }
                                }
                                offsetX = 0f  // Snap back
                            }
                        )
                    } else Modifier
                )
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Show @ for DMs, # for channels
                    Text(
                        text = if (channel.type == ChannelType.DM) "@" else "#",
                        style = ConsoleTheme.bodyBold.copy(color = ConsoleTheme.textMuted)
                    )
                    Text(
                        text = channel.name,
                        style = ConsoleTheme.bodyBold
                    )
                    if (isOwner && channel.id != "general" && channel.type != ChannelType.DM) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "·",
                            style = ConsoleTheme.caption
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "owner",
                            style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted)
                        )
                    }
                }
                
                if (channel.lastMessagePreview != null) {
                    Text(
                        text = channel.lastMessagePreview,
                        style = ConsoleTheme.caption,
                        maxLines = 1
                    )
                }
            }
            
            Column(horizontalAlignment = Alignment.End) {
                if (channel.lastMessageTime != null) {
                    Text(
                        text = formatTime(channel.lastMessageTime),
                        style = ConsoleTheme.caption
                    )
                }
                
                if (channel.unreadCount > 0) {
                    Text(
                        text = "${channel.unreadCount}",
                        style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.accent)
                    )
                }
            }
            
            if (onInvite != null) {
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "INVITE",
                    style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.textMuted),
                    modifier = Modifier
                        .clickable(onClick = onInvite)
                        .padding(4.dp)
                )
            }
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60_000 -> "now"
        diff < 3600_000 -> "${diff / 60_000}m"
        diff < 86400_000 -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(timestamp))
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun ChannelListScreenPreview() {
    MaterialTheme {
        ChannelListScreen(
            beaconId = "default",
            onChannelClick = { },
            onBackClick = { }
        )
    }
}
