package com.guildofsmiths.trademesh.ui

import android.util.Log
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
import android.os.Handler
import android.os.Looper
import com.guildofsmiths.trademesh.data.BeaconRepository
import com.guildofsmiths.trademesh.data.Channel
import com.guildofsmiths.trademesh.data.ChannelType
import com.guildofsmiths.trademesh.data.ChannelVisibility
import com.guildofsmiths.trademesh.data.UserPreferences
import com.guildofsmiths.trademesh.engine.BoundaryEngine
import com.guildofsmiths.trademesh.service.GatewayClient
import com.guildofsmiths.trademesh.service.SupabaseChat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// Main thread handler for UI callbacks
private val mainHandler = Handler(Looper.getMainLooper())

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
    var channelVisibility by remember { mutableStateOf(ChannelVisibility.PUBLIC) }
    var requiresApproval by remember { mutableStateOf(false) }
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

            Spacer(modifier = Modifier.height(24.dp))

            Text(text = "VISIBILITY", style = ConsoleTheme.captionBold)
            Spacer(modifier = Modifier.height(10.dp))

            // Visibility options
            Column {
                VisibilityOption(
                    visibility = ChannelVisibility.PUBLIC,
                    selected = channelVisibility == ChannelVisibility.PUBLIC,
                    description = "Anyone can join and see",
                    onSelect = { channelVisibility = ChannelVisibility.PUBLIC; requiresApproval = false }
                )

                VisibilityOption(
                    visibility = ChannelVisibility.PRIVATE,
                    selected = channelVisibility == ChannelVisibility.PRIVATE,
                    description = "Invite-only",
                    onSelect = { channelVisibility = ChannelVisibility.PRIVATE; requiresApproval = false }
                )

                VisibilityOption(
                    visibility = ChannelVisibility.RESTRICTED,
                    selected = channelVisibility == ChannelVisibility.RESTRICTED,
                    description = "Specific users only",
                    onSelect = { channelVisibility = ChannelVisibility.RESTRICTED; requiresApproval = false }
                )
            }

            // Approval toggle for private channels
            if (channelVisibility == ChannelVisibility.PRIVATE) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { requiresApproval = !requiresApproval }
                ) {
                    Text(
                        text = if (requiresApproval) "☑" else "☐",
                        style = ConsoleTheme.body,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = "Require admin approval for joins",
                        style = ConsoleTheme.bodySmall
                    )
                }
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
                                BeaconRepository.getChannel(beaconId, name) != null -> errorMessage = "Channel exists locally"
                                else -> {
                                    errorMessage = "Creating channel..."

                                    // Create in Supabase for global availability
                                    CoroutineScope(Dispatchers.IO).launch {
                                        // For now, create as public in Supabase (visibility is enforced locally)
                                        SupabaseChat.createChannel(name, channelType.name.lowercase(), creatorId) { supabaseChannel, supabaseError ->
                                            // Always run UI updates on main thread
                                            mainHandler.post {
                                                if (supabaseError != null) {
                                                    Log.e("CreateChannel", "Supabase creation failed", supabaseError)
                                                    
                                                    // Fallback: try backend
                                                    GatewayClient.createChannel(name, channelType.name.lowercase()) { backendChannel, backendError ->
                                                        mainHandler.post {
                                                            if (backendError != null) {
                                                                Log.e("CreateChannel", "Backend creation also failed", backendError)
                                                                // Final fallback: create locally only
                                                                errorMessage = "Offline - created locally only"
                                                                createChannelLocally(name, channelType, channelVisibility, requiresApproval, beaconId, creatorId, onChannelCreated)
                                                            } else if (backendChannel != null) {
                                                                val backendChannelId = backendChannel.getString("id")
                                                                val channel = Channel(
                                                                    id = backendChannelId,
                                                                    beaconId = beaconId,
                                                                    name = name,
                                                                    type = channelType,
                                                                    visibility = channelVisibility,
                                                                    requiresApproval = requiresApproval,
                                                                    creatorId = creatorId
                                                                )
                                                                BeaconRepository.addChannel(beaconId, channel)
                                                                BoundaryEngine.joinChannel(backendChannelId)
                                                                BoundaryEngine.broadcastChannelInvite(backendChannelId, name)
                                                                errorMessage = null
                                                                onChannelCreated(channel)
                                                            }
                                                        }
                                                    }
                                                } else if (supabaseChannel != null) {
                                                    Log.i("CreateChannel", "✅ Channel created in Supabase: #${supabaseChannel.name} (${supabaseChannel.id})")

                                                    // Create locally with the Supabase UUID
                                                    val channel = Channel(
                                                        id = supabaseChannel.id,
                                                        beaconId = beaconId,
                                                        name = supabaseChannel.name,
                                                        type = channelType,
                                                        visibility = channelVisibility,
                                                        requiresApproval = requiresApproval,
                                                        creatorId = creatorId
                                                    )
                                                    BeaconRepository.addChannel(beaconId, channel)
                                                    BoundaryEngine.joinChannel(supabaseChannel.id)

                                                    // Broadcast invite to nearby peers via mesh
                                                    BoundaryEngine.broadcastChannelInvite(supabaseChannel.id, supabaseChannel.name)

                                                    errorMessage = null
                                                    onChannelCreated(channel)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        .padding(vertical = 8.dp)
                )
            }
        }
    }
}

/**
 * Helper function to create channel locally (fallback when backend is unavailable)
 */
private fun createChannelLocally(
    name: String,
    channelType: ChannelType,
    visibility: ChannelVisibility,
    requiresApproval: Boolean,
    beaconId: String,
    creatorId: String,
    onChannelCreated: (Channel) -> Unit
) {
    val channel = Channel.createGroup(
        name = name,
        beaconId = beaconId,
        creatorId = creatorId,
        visibility = visibility,
        requiresApproval = requiresApproval
    )
    BeaconRepository.addChannel(beaconId, channel)
    BoundaryEngine.joinChannel(name)

    // Broadcast invite to nearby peers so they can discover this channel
    BoundaryEngine.broadcastChannelInvite(name, name)

    onChannelCreated(channel)
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

@Composable
private fun VisibilityOption(
    visibility: ChannelVisibility,
    selected: Boolean,
    description: String,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .background(
                if (selected) ConsoleTheme.accent.copy(alpha = 0.1f) else ConsoleTheme.surface
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (selected) "●" else "○",
                    style = ConsoleTheme.body,
                    color = if (selected) ConsoleTheme.accent else ConsoleTheme.textMuted,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = visibility.name,
                    style = ConsoleTheme.bodyBold.copy(
                        color = if (selected) ConsoleTheme.accent else ConsoleTheme.text
                    )
                )
            }
            Text(
                text = description,
                style = ConsoleTheme.caption,
                color = ConsoleTheme.textMuted,
                modifier = Modifier.padding(start = 24.dp, top = 2.dp)
            )
        }
    }
}
