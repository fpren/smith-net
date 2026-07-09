package com.guildofsmiths.trademesh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import com.guildofsmiths.trademesh.ui.theme2.LocalSmithColors
import com.guildofsmiths.trademesh.ui.theme2.SmithType

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
    modifier: Modifier = Modifier
) {
    val colors = LocalSmithColors.current
    val beacons by BeaconRepository.beacons.collectAsState()
    val isMeshConnected by BoundaryEngine.isMeshConnected.collectAsState()
    val isScanning by BoundaryEngine.isScanning.collectAsState()
    val peersMap by PeerRepository.peers.collectAsState()
    val activePeerCount = peersMap.values.count { it.isActive() }
    
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    
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
    
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = colors.bgBase,
        floatingActionButton = {
            if (onCreateBeaconClick != null) {
                Text(
                    text = "+ NEW",
                    style = SmithType.action.copy(color = colors.accent),
                    modifier = Modifier
                        .clickable(onClick = onCreateBeaconClick)
                        .background(colors.bgPanel)
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
                    .background(colors.bgPanel)
                    .padding(horizontal = 20.dp, vertical = 20.dp)
            ) {
                // Brand with version and status dot
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = ConsoleTheme.APP_NAME,
                        style = SmithType.brand.copy(color = colors.ink)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = ConsoleTheme.APP_VERSION,
                        style = SmithType.version.copy(color = colors.inkMuted),
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
                                    isScanning -> colors.statusOnline
                                    isMeshConnected -> colors.attention
                                    else -> colors.inkMuted
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
                        style = SmithType.bodySmall.copy(color = colors.accent),
                        modifier = Modifier
                            .weight(1f)
                            .clickable(onClick = { onProfileClick?.invoke() })
                    )

                    if (onPeersClick != null) {
                        Text(
                            text = if (activePeerCount > 0) "PEERS ($activePeerCount)" else "PEERS",
                            style = SmithType.captionBold.copy(
                                color = if (activePeerCount > 0) colors.statusOnline else colors.inkMuted
                            ),
                            modifier = Modifier
                                .clickable(onClick = onPeersClick)
                                .padding(8.dp)
                        )
                    }

                    if (onSettingsClick != null) {
                        Text(
                            text = "SETTINGS",
                            style = SmithType.captionBold.copy(color = colors.inkMuted),
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
                                style = SmithType.caption.copy(color = colors.inkMuted)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        Text(
                            text = "no networks yet",
                            style = SmithType.bodySmall.copy(color = colors.inkMuted)
                        )
                        if (isRefreshing) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(text = "refreshing...", style = SmithType.caption.copy(color = colors.inkMuted))
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
                                Text(text = "refreshing...", style = SmithType.caption.copy(color = colors.inkMuted))
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
                    style = SmithType.caption.copy(color = colors.inkMuted)
                )
            }
        }
    }
}

@Composable
private fun BeaconRow(
    beacon: Beacon,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = LocalSmithColors.current
    val totalUnread = beacon.channels.sumOf { it.unreadCount }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = beacon.name, style = SmithType.bodyBold.copy(color = colors.ink))
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${beacon.channels.size} channels",
                style = SmithType.caption.copy(color = colors.inkMuted)
            )
        }

        if (totalUnread > 0) {
            Text(
                text = "$totalUnread",
                style = SmithType.captionBold.copy(color = colors.accent)
            )
            Spacer(modifier = Modifier.width(10.dp))
        }

        Text(
            text = "→",
            style = SmithType.header.copy(color = colors.inkMuted)
        )
    }
}

/** Non-composable color set for legacy call sites (e.g. InviteBanner.kt) that
 * cannot reach [LocalSmithColors]. Pinned to the light v2 palette — matches
 * ConsoleTheme's own light-only nature; revisit if those call sites become
 * composable-context and can read LocalSmithColors directly. */
object AppColors {
    val background = com.guildofsmiths.trademesh.ui.Tokens2.Light.BgBase
    val surface = com.guildofsmiths.trademesh.ui.Tokens2.Light.BgPanel
    val surfaceHighlight = com.guildofsmiths.trademesh.ui.Tokens2.Light.BgPanel
    val text = com.guildofsmiths.trademesh.ui.Tokens2.Light.Ink
    val textMuted = com.guildofsmiths.trademesh.ui.Tokens2.Light.InkMuted
    val accent = com.guildofsmiths.trademesh.ui.Tokens2.Light.Accent
    val accentGreen = com.guildofsmiths.trademesh.ui.Tokens2.Light.StatusOnline
    val accentOrange = com.guildofsmiths.trademesh.ui.Tokens2.Light.Attention
    val divider = com.guildofsmiths.trademesh.ui.Tokens2.Light.Line
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun BeaconListScreenPreview() {
    BeaconListScreen(onBeaconClick = { })
}
