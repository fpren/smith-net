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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.guildofsmiths.trademesh.service.SupabaseChat
import kotlinx.coroutines.launch

/**
 * Channels screen - Discover and join channels created by the dashboard
 */
@Composable
fun ChannelsScreen(
    onBackClick: () -> Unit,
    onChannelJoined: (String) -> Unit, // Navigate to channel after joining
    modifier: Modifier = Modifier
) {
    val availableChannels by SupabaseChat.availableChannels.collectAsState()
    val joinedChannelIds by SupabaseChat.joinedChannelIds.collectAsState()
    val isConnected by SupabaseChat.isConnected.collectAsState()
    
    var isLoading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    
    // Load channels on screen open
    LaunchedEffect(Unit) {
        isLoading = true
        SupabaseChat.fetchAvailableChannels()
        isLoading = false
    }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ConsoleTheme.background)
    ) {
        // Header
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
                Text(text = "CHANNELS", style = ConsoleTheme.title)
                Text(
                    text = if (isConnected) "${availableChannels.size} available" else "offline",
                    style = ConsoleTheme.caption
                )
            }
            
            // Refresh button
            Text(
                text = "[↻]",
                style = ConsoleTheme.title.copy(color = ConsoleTheme.accent),
                modifier = Modifier.clickable {
                    scope.launch {
                        isLoading = true
                        SupabaseChat.fetchAvailableChannels()
                        isLoading = false
                    }
                }
            )
        }
        
        ConsoleSeparator()
        
        // Status bar
        if (!isConnected) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleTheme.surface)
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "⚠ Connect to internet to discover channels",
                    style = ConsoleTheme.caption
                )
            }
            ConsoleSeparator()
        }
        
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "loading channels...", style = ConsoleTheme.bodySmall)
            }
        } else if (availableChannels.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "no channels available", style = ConsoleTheme.bodySmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "channels are created from the dashboard",
                        style = ConsoleTheme.caption
                    )
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                // Joined channels section
                val joined = availableChannels.filter { joinedChannelIds.contains(it.id) }
                if (joined.isNotEmpty()) {
                    item {
                        Text(
                            text = "JOINED",
                            style = ConsoleTheme.captionBold,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                        )
                    }
                    items(joined, key = { it.id }) { channel ->
                        android.util.Log.e("ChannelsScreen", "Rendering joined channel: ${channel.id} / ${channel.name}")
                        ChannelRow(
                            channel = channel,
                            isJoined = true,
                            onJoinClick = { /* Already joined */ },
                            onOpenClick = { 
                                android.util.Log.e("ChannelsScreen", "████ OPEN clicked: ${channel.id} ████")
                                onChannelJoined(channel.id) 
                            }
                        )
                        ConsoleSeparator()
                    }
                }
                
                // Available channels section
                val notJoined = availableChannels.filter { !joinedChannelIds.contains(it.id) }
                if (notJoined.isNotEmpty()) {
                    item {
                        Text(
                            text = "AVAILABLE",
                            style = ConsoleTheme.captionBold,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                        )
                    }
                    items(notJoined, key = { it.id }) { channel ->
                        ChannelRow(
                            channel = channel,
                            isJoined = false,
                            onJoinClick = {
                                android.util.Log.i("ChannelsScreen", "JOIN clicked for channel: ${channel.id}")
                                SupabaseChat.joinChannel(channel.id)
                                android.util.Log.i("ChannelsScreen", "Navigating to channel: ${channel.id}")
                                onChannelJoined(channel.id)
                            },
                            onOpenClick = null
                        )
                        ConsoleSeparator()
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelRow(
    channel: SupabaseChat.AvailableChannel,
    isJoined: Boolean,
    onJoinClick: () -> Unit,
    onOpenClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = onOpenClick != null, onClick = { onOpenClick?.invoke() })
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Channel indicator
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (isJoined) ConsoleTheme.success else ConsoleTheme.textDim)
        )
        
        Spacer(modifier = Modifier.width(12.dp))
        
        // Channel info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "#${channel.name}",
                style = if (isJoined) ConsoleTheme.bodyBold else ConsoleTheme.bodySmall
            )
            Text(
                text = channel.type,
                style = ConsoleTheme.caption
            )
        }
        
        // Action button
        if (isJoined) {
            Text(
                text = "OPEN",
                style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.accent),
                modifier = Modifier
                    .clickable(onClick = { onOpenClick?.invoke() })
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        } else {
            Text(
                text = "JOIN",
                style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.success),
                modifier = Modifier
                    .clickable(onClick = onJoinClick)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun ChannelsScreenPreview() {
    MaterialTheme {
        ChannelsScreen(
            onBackClick = { },
            onChannelJoined = { }
        )
    }
}

