package com.guildofsmiths.trademesh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.guildofsmiths.trademesh.data.BeaconRepository
import com.guildofsmiths.trademesh.data.Peer
import com.guildofsmiths.trademesh.data.PeerRepository
import com.guildofsmiths.trademesh.data.UserPreferences
import com.guildofsmiths.trademesh.engine.BoundaryEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Peers screen — bold header, clean list.
 */
@Composable
fun PeersScreen(
    onBackClick: () -> Unit,
    onPeerClick: (Peer) -> Unit,
    onStartChat: ((String) -> Unit)? = null, // Navigate to DM channel
    modifier: Modifier = Modifier
) {
    val peersMap by PeerRepository.peers.collectAsState()
    val peers = peersMap.values.sortedByDescending { it.lastSeen }
    val activePeers = peers.filter { it.isActive() }
    val isScanning by BoundaryEngine.isScanning.collectAsState()
    
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    // Refresh function - restarts BLE scanning to discover peers
    fun onRefresh() {
        scope.launch {
            isRefreshing = true
            BoundaryEngine.requestPeerDiscovery()
            delay(3000) // Show refreshing state for 3 seconds
            isRefreshing = false
        }
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ConsoleTheme.background)
    ) {
        // Header - consistent with main screen style
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ConsoleTheme.surface)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "←", 
                style = ConsoleTheme.title,
                modifier = Modifier.clickable(onClick = onBackClick)
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = "PEERS", style = ConsoleTheme.title)
                Text(
                    text = if (isRefreshing) "scanning..." else "${activePeers.size} active · ${peers.size} total",
                    style = ConsoleTheme.caption
                )
            }
        }
        
        ConsoleSeparator()
        
        // Pull-to-refresh nested scroll connection
        val listState = rememberLazyListState()
        var pullOffset by remember { mutableFloatStateOf(0f) }
        val pullThreshold = 150f
        
        val nestedScrollConnection = remember {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    // When pulling down and list is at top, accumulate offset
                    if (available.y > 0 && listState.firstVisibleItemIndex == 0 && 
                        listState.firstVisibleItemScrollOffset == 0 && !isRefreshing) {
                        pullOffset = (pullOffset + available.y).coerceIn(0f, pullThreshold * 1.5f)
                        return Offset(0f, available.y * 0.5f) // Consume half for resistance feel
                    }
                    return Offset.Zero
                }
                
                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    // Reset pull offset when scrolling up
                    if (available.y < 0) {
                        pullOffset = (pullOffset + available.y).coerceAtLeast(0f)
                    }
                    return Offset.Zero
                }
                
                override suspend fun onPreFling(available: Velocity): Velocity {
                    // Trigger refresh if pulled past threshold
                    if (pullOffset >= pullThreshold && !isRefreshing) {
                        onRefresh()
                    }
                    pullOffset = 0f
                    return Velocity.Zero
                }
            }
        }
        
        if (peers.isEmpty()) {
            // Empty state with pull-down gesture detection
            var dragOffset by remember { mutableFloatStateOf(0f) }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(isRefreshing) {
                        if (!isRefreshing) {
                            awaitPointerEventScope {
                                while (true) {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    var totalDrag = 0f
                                    drag(down.id) { change ->
                                        val dragDelta = change.positionChange().y
                                        if (dragDelta > 0) { // Only track downward drags
                                            totalDrag += dragDelta
                                            dragOffset = totalDrag.coerceIn(0f, pullThreshold * 1.5f)
                                        }
                                        change.consume()
                                    }
                                    // When drag ends, check if we should trigger refresh
                                    if (dragOffset >= pullThreshold) {
                                        onRefresh()
                                    }
                                    dragOffset = 0f
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    // Show pull indicator when dragging
                    if (dragOffset > 20f) {
                        Text(
                            text = if (dragOffset >= pullThreshold) "release to scan" else "pull down...",
                            style = ConsoleTheme.caption
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Text(text = "no peers discovered", style = ConsoleTheme.bodySmall)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = if (isRefreshing) "scanning for peers..." else "pull down to scan",
                        style = ConsoleTheme.caption
                    )
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(nestedScrollConnection)
            ) {
                // Show refreshing indicator at top
                if (isRefreshing) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "scanning...", style = ConsoleTheme.caption)
                        }
                    }
                }
                
                items(peers, key = { it.userId }) { peer ->
                    PeerRow(
                        peer = peer,
                        onClick = { onPeerClick(peer) },
                        onStartChat = if (onStartChat != null) {
                            {
                                // Create or get existing DM and navigate to it
                                val myUserId = UserPreferences.getUserId()
                                val dm = BeaconRepository.getOrCreateDM(
                                    beaconId = "default",
                                    myUserId = myUserId,
                                    otherUserId = peer.userId,
                                    otherUserName = peer.userName
                                )
                                onStartChat(dm.id)
                            }
                        } else null
                    )
                    ConsoleSeparator()
                }
            }
        }
    }
}

@Composable
private fun PeerRow(
    peer: Peer,
    onClick: () -> Unit,
    onStartChat: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val isActive = peer.isActive()
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (isActive) ConsoleTheme.success else ConsoleTheme.textDim)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = peer.userName,
                style = if (isActive) ConsoleTheme.bodyBold else ConsoleTheme.bodySmall
            )
            Text(
                text = "${peer.lastSeenAgo()} · ${peer.messageCount} msgs",
                style = ConsoleTheme.caption
            )
        }
        
        Text(
            text = "${peer.rssi} dBm",
            style = ConsoleTheme.caption
        )
        
        if (onStartChat != null) {
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "CHAT",
                style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.accent),
                modifier = Modifier
                    .clickable(onClick = onStartChat)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun PeersScreenPreview() {
    MaterialTheme {
        PeersScreen(
            onBackClick = { },
            onPeerClick = { },
            onStartChat = { }
        )
    }
}
