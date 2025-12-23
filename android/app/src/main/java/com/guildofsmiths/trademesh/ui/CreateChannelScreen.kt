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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.guildofsmiths.trademesh.data.BeaconRepository
import com.guildofsmiths.trademesh.data.Channel
import com.guildofsmiths.trademesh.data.ChannelType
import com.guildofsmiths.trademesh.data.UserPreferences
import com.guildofsmiths.trademesh.engine.BoundaryEngine

/**
 * Create channel — bold, clean form.
 */
@Composable
fun CreateChannelScreen(
    beaconId: String,
    onBackClick: () -> Unit,
    onChannelCreated: (Channel) -> Unit,
    modifier: Modifier = Modifier
) {
    val creatorId = remember { UserPreferences.getUserId() }
    var channelName by remember { mutableStateOf("") }
    var channelType by remember { mutableStateOf(ChannelType.GROUP) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
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
                .clickable(onClick = onBackClick)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "←", style = ConsoleTheme.title)
            Spacer(modifier = Modifier.width(14.dp))
            Text(text = "NEW CHANNEL", style = ConsoleTheme.title)
        }
        
        ConsoleSeparator()
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
        ) {
            Text(text = "CHANNEL NAME", style = ConsoleTheme.captionBold)
            Spacer(modifier = Modifier.height(6.dp))
            
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleTheme.surface)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "#", style = ConsoleTheme.body.copy(color = ConsoleTheme.textMuted))
                Spacer(modifier = Modifier.width(8.dp))
                
                BasicTextField(
                    value = channelName,
                    onValueChange = {
                        channelName = it.take(20).lowercase().replace(" ", "-")
                        errorMessage = null
                    },
                    textStyle = ConsoleTheme.body,
                    cursorBrush = SolidColor(ConsoleTheme.cursor),
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    decorationBox = { innerTextField ->
                        Box {
                            if (channelName.isEmpty()) {
                                Text(
                                    text = "trades, help, random",
                                    style = ConsoleTheme.body.copy(color = ConsoleTheme.placeholder)
                                )
                            }
                            innerTextField()
                        }
                    }
                )
            }
            
            if (errorMessage != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = errorMessage!!,
                    style = ConsoleTheme.caption.copy(color = ConsoleTheme.warning)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(text = "TYPE", style = ConsoleTheme.captionBold)
            Spacer(modifier = Modifier.height(10.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(
                    text = "BROADCAST",
                    style = ConsoleTheme.bodyBold.copy(
                        color = if (channelType == ChannelType.BROADCAST) ConsoleTheme.accent else ConsoleTheme.textMuted
                    ),
                    modifier = Modifier
                        .clickable { channelType = ChannelType.BROADCAST }
                        .padding(6.dp)
                )
                
                Text(
                    text = "GROUP",
                    style = ConsoleTheme.bodyBold.copy(
                        color = if (channelType == ChannelType.GROUP) ConsoleTheme.accent else ConsoleTheme.textMuted
                    ),
                    modifier = Modifier
                        .clickable { channelType = ChannelType.GROUP }
                        .padding(6.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            if (channelName.trim().length >= 2) {
                Text(
                    text = "CREATE →",
                    style = ConsoleTheme.action,
                    modifier = Modifier
                        .clickable {
                            val name = channelName.trim()
                            when {
                                name.length < 2 -> errorMessage = "Name must be at least 2 characters"
                                BeaconRepository.getChannel(beaconId, name) != null -> errorMessage = "Channel exists"
                                else -> {
                                    val channel = Channel(
                                        id = name,
                                        beaconId = beaconId,
                                        name = name,
                                        type = channelType,
                                        creatorId = creatorId  // Set creator for ownership
                                    )
                                    BeaconRepository.addChannel(beaconId, channel)
                                    BoundaryEngine.joinChannel(name)
                                    
                                    // Broadcast invite to nearby peers so they can discover this channel
                                    BoundaryEngine.broadcastChannelInvite(name, name)
                                    
                                    onChannelCreated(channel)
                                }
                            }
                        }
                        .padding(vertical = 8.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun CreateChannelScreenPreview() {
    MaterialTheme {
        CreateChannelScreen(
            beaconId = "default",
            onBackClick = { },
            onChannelCreated = { }
        )
    }
}
