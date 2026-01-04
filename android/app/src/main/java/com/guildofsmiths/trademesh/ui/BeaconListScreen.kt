package com.guildofsmiths.trademesh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.drag
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.guildofsmiths.trademesh.data.Beacon
import com.guildofsmiths.trademesh.data.BeaconRepository
import com.guildofsmiths.trademesh.data.PeerRepository
import com.guildofsmiths.trademesh.data.UserPreferences
import com.guildofsmiths.trademesh.engine.BoundaryEngine

/**
 * Smith Net — Main beacon list with bold branding.
 */
@Composable
fun BeaconListScreen(
    onBeaconClick: (Beacon) -> Unit,
    onSettingsClick: (() -> Unit)? = null,
    onPeersClick: (() -> Unit)? = null,
    onProfileClick: (() -> Unit)? = null,
    onCreateBeaconClick: (() -> Unit)? = null,
    onBackGesture: (() -> Unit)? = null, // Gesture-based back navigation
    modifier: Modifier = Modifier
) {
    val beacons by BeaconRepository.beacons.collectAsState()
    val isMeshConnected by BoundaryEngine.isMeshConnected.collectAsState()
    val isScanning by BoundaryEngine.isScanning.collectAsState()
    val peersMap by PeerRepository.peers.collectAsState()
    val activePeerCount = peersMap.values.count { it.isActive() }
    
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
    // Gesture navigation state - swipe right to go back
    var swipeOffset by remember { mutableFloatStateOf(0f) }
    val swipeThreshold = 150f // Pixels to trigger back navigation
    
    // Refresh function - discovers peers and channels
    fun onRefresh() {
        scope.launch {
            isRefreshing = true
            BoundaryEngine.requestPeerDiscovery()
            BoundaryEngine.requestChannelDiscovery()
            delay(2000)
            isRefreshing = false
        }
    }
    
    // Pull-to-refresh state
    val listState = rememberLazyListState()
    var pullOffset by remember { mutableFloatStateOf(0f) }
    val pullThreshold = 150f
    
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y > 0 && listState.firstVisibleItemIndex == 0 && 
                    listState.firstVisibleItemScrollOffset == 0 && !isRefreshing) {
                    pullOffset = (pullOffset + available.y).coerceIn(0f, pullThreshold * 1.5f)
                    return Offset(0f, available.y * 0.5f)
                }
                return Offset.Zero
            }
            
            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (available.y < 0) {
                    pullOffset = (pullOffset + available.y).coerceAtLeast(0f)
                }
                return Offset.Zero
            }
            
            override suspend fun onPreFling(available: Velocity): Velocity {
                if (pullOffset >= pullThreshold && !isRefreshing) {
                    onRefresh()
                }
                pullOffset = 0f
                return Velocity.Zero
            }
        }
    }
    
    // Gesture navigation wrapper - swipe right to go back
    Box(
        modifier = modifier
            .fillMaxSize()
            .offset { IntOffset(swipeOffset.roundToInt(), 0) }
            .pointerInput(onBackGesture) {
                if (onBackGesture != null) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (swipeOffset > swipeThreshold) {
                                onBackGesture()
                            }
                            swipeOffset = 0f
                        },
                        onDragCancel = { swipeOffset = 0f },
                        onHorizontalDrag = { _, dragAmount ->
                            // Only allow swipe right (positive direction)
                            if (dragAmount > 0 || swipeOffset > 0) {
                                swipeOffset = (swipeOffset + dragAmount).coerceIn(0f, swipeThreshold * 1.5f)
                            }
                        }
                    )
                }
            }
    ) {
        // Visual indicator for back gesture
        if (swipeOffset > 20f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp)
            ) {
                Text(
                    text = if (swipeOffset > swipeThreshold) "← release" else "←",
                    style = ConsoleTheme.caption.copy(
                        color = ConsoleTheme.textMuted.copy(alpha = (swipeOffset / swipeThreshold).coerceIn(0.3f, 1f))
                    )
                )
            }
        }
        
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = ConsoleTheme.background,
        floatingActionButton = {
            if (onCreateBeaconClick != null) {
                Text(
                    text = "+ NEW",
                    style = ConsoleTheme.action,
                    modifier = Modifier
                        .clickable(onClick = onCreateBeaconClick)
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
            // Big bold header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleTheme.surface)
                    .padding(horizontal = 20.dp, vertical = 20.dp)
            ) {
                // Brand with version and status dot
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = ConsoleTheme.APP_NAME,
                        style = ConsoleTheme.brand
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = ConsoleTheme.APP_VERSION,
                        style = ConsoleTheme.version,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    // Subtle status dot
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isScanning -> ConsoleTheme.success
                                    isMeshConnected -> ConsoleTheme.warning
                                    else -> ConsoleTheme.textDim
                                }
                            )
                    )
                }
                
                Spacer(modifier = Modifier.height(4.dp))
                
                // User + nav row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = UserPreferences.getDisplayName(),
                        style = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.accent),
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = { onProfileClick?.invoke() })
                    )
                    
                    if (onPeersClick != null) {
                        Text(
                            text = if (activePeerCount > 0) "PEERS ($activePeerCount)" else "PEERS",
                            style = ConsoleTheme.captionBold.copy(
                                color = if (activePeerCount > 0) ConsoleTheme.success else ConsoleTheme.textMuted
                            ),
                            modifier = Modifier
                                .clickable(onClick = onPeersClick)
                                .padding(8.dp)
                        )
                    }
                    
                    // NOTE: PLAN link removed - Messenger is communication-only
                    // Navigation to Planner is via Dashboard tab bar

                    if (onSettingsClick != null) {
                        Text(
                            text = "SETTINGS",
                            style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.textMuted),
                            modifier = Modifier
                                .clickable(onClick = onSettingsClick)
                                .padding(8.dp)
                        )
                    }
                }
            }
            
            ConsoleSeparator()
            
            // Beacon list with pull-to-refresh
            if (beacons.isEmpty()) {
                // Empty state with pull-down gesture
                var dragOffset by remember { mutableFloatStateOf(0f) }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .pointerInput(isRefreshing) {
                            if (!isRefreshing) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val down = awaitFirstDown(requireUnconsumed = false)
                                        var totalDrag = 0f
                                        drag(down.id) { change ->
                                            val dragDelta = change.positionChange().y
                                            if (dragDelta > 0) {
                                                totalDrag += dragDelta
                                                dragOffset = totalDrag.coerceIn(0f, pullThreshold * 1.5f)
                                            }
                                            change.consume()
                                        }
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
                        if (dragOffset > 20f) {
                            Text(
                                text = if (dragOffset >= pullThreshold) "release to refresh" else "pull down...",
                                style = ConsoleTheme.caption
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        Text(
                            text = "no networks yet",
                            style = ConsoleTheme.bodySmall
                        )
                        if (isRefreshing) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "refreshing...", style = ConsoleTheme.caption)
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .nestedScroll(nestedScrollConnection)
                ) {
                    // Refreshing indicator
                    if (isRefreshing) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = "refreshing...", style = ConsoleTheme.caption)
                            }
                        }
                    }
                    
                    items(beacons, key = { it.id }) { beacon ->
                        BeaconRow(
                            beacon = beacon,
                            onClick = { onBeaconClick(beacon) }
                        )
                        ConsoleSeparator()
                    }
                }
            }
            
            // Footer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "made by ${ConsoleTheme.STUDIO}",
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.textDim)
                )
            }
        }
    }
    } // Close gesture navigation wrapper Box
}

@Composable
private fun BeaconRow(
    beacon: Beacon,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalUnread = beacon.channels.sumOf { it.unreadCount }
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = beacon.name, style = ConsoleTheme.bodyBold)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${beacon.channels.size} channels",
                style = ConsoleTheme.caption
            )
        }
        
        if (totalUnread > 0) {
            Text(
                text = "$totalUnread",
                style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.accent)
            )
            Spacer(modifier = Modifier.width(10.dp))
        }
        
        Text(
            text = "→",
            style = ConsoleTheme.header.copy(color = ConsoleTheme.textDim)
        )
    }
}

object AppColors {
    val background = ConsoleTheme.background
    val surface = ConsoleTheme.surface
    val surfaceHighlight = ConsoleTheme.surface
    val text = ConsoleTheme.text
    val textMuted = ConsoleTheme.textMuted
    val accent = ConsoleTheme.accent
    val accentGreen = ConsoleTheme.success
    val accentOrange = ConsoleTheme.warning
    val divider = ConsoleTheme.separator
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun BeaconListScreenPreview() {
    MaterialTheme {
        BeaconListScreen(onBeaconClick = { })
    }
}
