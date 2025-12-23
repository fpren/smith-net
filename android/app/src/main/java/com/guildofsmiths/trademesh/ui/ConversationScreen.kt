package com.guildofsmiths.trademesh.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.guildofsmiths.trademesh.data.Channel
import com.guildofsmiths.trademesh.data.ChannelType
import com.guildofsmiths.trademesh.data.MediaType
import com.guildofsmiths.trademesh.data.Message
import com.guildofsmiths.trademesh.data.Peer
import com.guildofsmiths.trademesh.data.PeerRepository
import com.guildofsmiths.trademesh.engine.BoundaryEngine
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Smith Net conversation — bold, chic, left/right aligned.
 * Now with media buttons and DM selection.
 */
@Composable
fun ConversationScreen(
    messages: List<Message>,
    onSendMessage: (String, Peer?) -> Unit,  // content + optional DM recipient
    localUserId: String = "",
    channel: Channel? = null,
    beaconName: String? = null,
    onBackClick: (() -> Unit)? = null,
    onVoiceClick: (() -> Unit)? = null,
    onCameraClick: (() -> Unit)? = null,
    onFileClick: (() -> Unit)? = null,
    initialDmPeer: Peer? = null,  // Pre-select peer for DM (from Peers screen)
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }
    
    // Peer selection for DM (initialize with passed-in peer if any)
    var showPeerSelector by remember { mutableStateOf(false) }
    var selectedPeer by remember { mutableStateOf(initialDmPeer) }
    val peers by PeerRepository.peers.collectAsState()
    val activePeers = peers.values.filter { it.isActive() }.sortedByDescending { it.lastSeen }
    
    // Online status for media
    val isOnline by BoundaryEngine.isOnline.collectAsState()
    
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    
    // Check if this is a DM channel
    val isDmChannel = channel?.id?.startsWith("dm_") == true
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ConsoleTheme.background)
    ) {
        // Bold header - show peer name for DM, channel name otherwise
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ConsoleTheme.surface)
                .then(
                    if (onBackClick != null) Modifier.clickable(onClick = onBackClick)
                    else Modifier
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBackClick != null) {
                Text(
                    text = "←",
                    style = ConsoleTheme.title.copy(color = ConsoleTheme.text)
                )
                Spacer(modifier = Modifier.width(14.dp))
            }
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    // For DM channels, show "DM · peername", otherwise show channel name
                    text = if (isDmChannel && initialDmPeer != null) {
                        "DM · ${initialDmPeer.userName}"
                    } else {
                        channel?.name?.uppercase() ?: "GENERAL"
                    },
                    style = ConsoleTheme.title
                )
                if (beaconName != null && !isDmChannel) {
                    Text(
                        text = beaconName,
                        style = ConsoleTheme.caption
                    )
                } else if (isDmChannel) {
                    Text(
                        text = "private conversation",
                        style = ConsoleTheme.caption
                    )
                }
            }
            
            // Online/Offline indicator
            Text(
                text = if (isOnline) "ONLINE" else "MESH",
                style = ConsoleTheme.captionBold.copy(
                    color = if (isOnline) ConsoleTheme.success else ConsoleTheme.textMuted
                )
            )
        }
        
        ConsoleSeparator()
        
        // DM selector bar - only show for non-DM channels
        if (!isDmChannel && (selectedPeer != null || activePeers.isNotEmpty())) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleTheme.background)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TO:",
                    style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.textMuted)
                )
                Spacer(modifier = Modifier.width(8.dp))
                
                Box {
                    Text(
                        text = selectedPeer?.userName ?: "everyone",
                        style = ConsoleTheme.bodyBold.copy(
                            color = if (selectedPeer != null) ConsoleTheme.accent else ConsoleTheme.text
                        ),
                        modifier = Modifier
                            .clickable { showPeerSelector = true }
                            .padding(4.dp)
                    )
                    
                    DropdownMenu(
                        expanded = showPeerSelector,
                        onDismissRequest = { showPeerSelector = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text("everyone (group)", style = ConsoleTheme.body)
                            },
                            onClick = {
                                selectedPeer = null
                                showPeerSelector = false
                            }
                        )
                        
                        if (activePeers.isNotEmpty()) {
                            activePeers.forEach { peer ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(peer.userName, style = ConsoleTheme.body)
                                            Text(
                                                "${peer.rssi} dBm · ${peer.lastSeenAgo()}",
                                                style = ConsoleTheme.caption
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedPeer = peer
                                        showPeerSelector = false
                                    }
                                )
                            }
                        }
                    }
                }
                
                if (selectedPeer != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "×",
                        style = ConsoleTheme.body.copy(color = ConsoleTheme.textMuted),
                        modifier = Modifier
                            .clickable { selectedPeer = null }
                            .padding(4.dp)
                    )
                }
                
                Spacer(modifier = Modifier.weight(1f))
                
                if (activePeers.isNotEmpty()) {
                    Text(
                        text = "${activePeers.size} nearby",
                        style = ConsoleTheme.caption
                    )
                }
            }
            
            ConsoleSeparator()
        }
        
        // Messages
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(12.dp)) }
            
            if (messages.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "no messages yet",
                            style = ConsoleTheme.bodySmall
                        )
                    }
                }
            }
            
            items(items = messages, key = { it.id }) { message ->
                val isSentByMe = if (localUserId.isNotEmpty()) {
                    message.senderId == localUserId
                } else {
                    !message.isMeshOrigin
                }
                
                MessageBlock(message = message, isSentByMe = isSentByMe)
                
                // Variable gap
                val gap = if (message.content.length > 50) 12.dp else 8.dp
                Spacer(modifier = Modifier.height(gap))
            }
            
            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
        
        ConsoleSeparator()
        
        // Input bar — pixel art + and hold-to-record voice
        var showAttachMenu by remember { mutableStateOf(false) }
        var isRecording by remember { mutableStateOf(false) }
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(ConsoleTheme.surface)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pixel art + button for attachments
            Box {
                PixelPlusButton(
                    enabled = isOnline,
                    onClick = { showAttachMenu = !showAttachMenu }
                )
                
                // Borderless transparent popup menu
                androidx.compose.material3.DropdownMenu(
                    expanded = showAttachMenu,
                    onDismissRequest = { showAttachMenu = false },
                    modifier = Modifier.background(ConsoleTheme.background)
                ) {
                    // Photo option with pixel camera
                    Row(
                        modifier = Modifier
                            .clickable(enabled = isOnline) {
                                showAttachMenu = false
                                onCameraClick?.invoke()
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PixelCamera(enabled = isOnline)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "photo",
                            style = ConsoleTheme.body.copy(
                                color = if (isOnline) ConsoleTheme.text else ConsoleTheme.textDim
                            )
                        )
                    }
                    
                    // File option with pixel file
                    Row(
                        modifier = Modifier
                            .clickable(enabled = isOnline) {
                                showAttachMenu = false
                                onFileClick?.invoke()
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PixelFile(enabled = isOnline)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "file",
                            style = ConsoleTheme.body.copy(
                                color = if (isOnline) ConsoleTheme.text else ConsoleTheme.textDim
                            )
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(text = ">", style = ConsoleTheme.prompt)
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // For DM channels, always send to the DM peer
            val effectivePeer = if (isDmChannel) initialDmPeer else selectedPeer
            
            BasicTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                textStyle = ConsoleTheme.body,
                cursorBrush = SolidColor(ConsoleTheme.cursor),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    if (inputText.isNotBlank()) {
                        onSendMessage(inputText.trim(), effectivePeer)
                        inputText = ""
                        if (!isDmChannel) selectedPeer = null  // Only clear if not in DM channel
                    }
                }),
                singleLine = true,
                decorationBox = { innerTextField ->
                    Box {
                        if (inputText.isEmpty()) {
                            Text(
                                text = if (isDmChannel) "message ${initialDmPeer?.userName ?: ""}" 
                                       else if (selectedPeer != null) "DM ${selectedPeer?.userName}" 
                                       else "message",
                                style = ConsoleTheme.body.copy(color = ConsoleTheme.placeholder)
                            )
                        }
                        innerTextField()
                    }
                }
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            // Right side: SEND or Voice record button
            if (inputText.isNotBlank()) {
                Text(
                    text = if (isDmChannel || selectedPeer != null) "DM" else "SEND",
                    style = ConsoleTheme.action.copy(
                        color = if (isDmChannel || selectedPeer != null) ConsoleTheme.accent else ConsoleTheme.action.color
                    ),
                    modifier = Modifier
                        .clickable {
                            onSendMessage(inputText.trim(), effectivePeer)
                            inputText = ""
                            if (!isDmChannel) selectedPeer = null  // Only clear if not in DM channel
                        }
                        .padding(4.dp)
                )
            } else {
                // Pixel art mic — hold to record
                PixelMicButton(
                    isRecording = isRecording,
                    enabled = isOnline,
                    onStartRecording = { 
                        isRecording = true
                        onVoiceClick?.invoke()
                    },
                    onStopRecording = { 
                        isRecording = false
                        // Voice recording stopped — would trigger send
                    }
                )
            }
        }
        
        // Offline media hint
        if (!isOnline) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(ConsoleTheme.surface)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "media requires online · text works on mesh",
                    style = ConsoleTheme.caption
                )
            }
        }
    }
}


/**
 * Message block with left/right alignment.
 * Bold sender, clear hierarchy. Shows media placeholders.
 */
@Composable
private fun MessageBlock(
    message: Message,
    isSentByMe: Boolean,
    modifier: Modifier = Modifier
) {
    val time = formatTime(message.timestamp)
    // Try to get a better display name from PeerRepository
    val peerName = if (!isSentByMe) {
        PeerRepository.getPeer(message.senderId)?.userName
    } else null
    val sender = when {
        isSentByMe -> "You"
        peerName != null && peerName != message.senderId -> peerName
        message.senderName.length > 8 -> message.senderName
        else -> message.senderName // Show whatever we have
    }
    
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isSentByMe) Arrangement.End else Arrangement.Start
    ) {
        Column(
            horizontalAlignment = if (isSentByMe) Alignment.End else Alignment.Start
        ) {
            // Header: arrow + sender + time + [sub] + [DM]
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isSentByMe) "▶" else "◀",
                    style = ConsoleTheme.prefix.copy(
                        color = if (isSentByMe) ConsoleTheme.sentPrefix else ConsoleTheme.receivedPrefix
                    )
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = sender,
                    style = ConsoleTheme.captionBold.copy(
                        color = if (isSentByMe) ConsoleTheme.accent else ConsoleTheme.textSecondary
                    )
                )
                Text(
                    text = " · $time",
                    style = ConsoleTheme.timestamp
                )
                if (message.isMeshOrigin && !isSentByMe) {
                    Text(
                        text = " [sub]",
                        style = ConsoleTheme.timestamp.copy(color = ConsoleTheme.textDim)
                    )
                }
                if (message.isDirectMessage()) {
                    Text(
                        text = " [DM]",
                        style = ConsoleTheme.timestamp.copy(color = ConsoleTheme.accent)
                    )
                }
                if (message.isMediaQueued()) {
                    Text(
                        text = " [queued]",
                        style = ConsoleTheme.timestamp.copy(color = ConsoleTheme.warning)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(3.dp))
            
            // Content — with media indicator
            Row {
                if (isSentByMe) {
                    Column(horizontalAlignment = Alignment.End) {
                        // Media type indicator
                        if (message.hasMedia()) {
                            MediaIndicator(message.mediaType, message.media?.fileName)
                        }
                        Text(
                            text = if (message.hasMedia()) message.getMeshPlaceholder() else message.content,
                            style = ConsoleTheme.body,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                    }
                    // Faint vertical line
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .height(18.dp)
                            .background(ConsoleTheme.sentLine)
                    )
                } else {
                    Column(horizontalAlignment = Alignment.Start) {
                        // Media type indicator
                        if (message.hasMedia()) {
                            MediaIndicator(message.mediaType, message.media?.fileName)
                        }
                        Text(
                            text = if (message.hasMedia()) message.getMeshPlaceholder() else message.content,
                            style = ConsoleTheme.body,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Simple text-based media indicator — no emojis.
 */
@Composable
private fun MediaIndicator(
    type: MediaType,
    fileName: String?
) {
    val indicator = when (type) {
        MediaType.IMAGE -> "[image]"
        MediaType.VOICE -> "[voice]"
        MediaType.FILE -> "[${fileName ?: "file"}]"
        MediaType.TEXT -> return
    }
    
    Text(
        text = indicator,
        style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted),
        modifier = Modifier.padding(bottom = 2.dp)
    )
}

/**
 * Pixel art camera — 8-bit style.
 */
@Composable
private fun PixelCamera(enabled: Boolean) {
    val color = if (enabled) ConsoleTheme.text else ConsoleTheme.textDim
    val px = 2.dp
    
    Box(
        modifier = Modifier.size(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Viewfinder bump
            Row {
                Spacer(modifier = Modifier.width(px * 2))
                Box(modifier = Modifier.width(px * 2).height(px).background(color))
            }
            // Camera body
            Box(modifier = Modifier.width(px * 6).height(px * 4).background(color))
        }
        // Lens (hole in center)
        Box(
            modifier = Modifier
                .size(px * 2)
                .background(ConsoleTheme.background)
        )
    }
}

/**
 * Pixel art file/document — 8-bit style.
 */
@Composable
private fun PixelFile(enabled: Boolean) {
    val color = if (enabled) ConsoleTheme.text else ConsoleTheme.textDim
    val px = 2.dp
    
    Box(
        modifier = Modifier.size(20.dp),
        contentAlignment = Alignment.Center
    ) {
        // Document shape with folded corner
        Box(modifier = Modifier.width(px * 5).height(px * 6).background(color))
        // Folded corner (top right)
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 2.dp, top = 2.dp)
                .width(px * 2)
                .height(px * 2)
                .background(ConsoleTheme.background)
        )
    }
}

/**
 * Pixel art + button — 8-bit style, drawn with Box composables.
 * Always clickable (menu shows, options may be disabled).
 */
@Composable
private fun PixelPlusButton(
    enabled: Boolean,
    onClick: () -> Unit
) {
    val color = if (enabled) ConsoleTheme.text else ConsoleTheme.textMuted
    val px = 3.dp // pixel size — bigger
    
    Box(
        modifier = Modifier
            .size(32.dp)
            .clickable(onClick = onClick) // Always clickable
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        // Horizontal bar of +
        Box(
            modifier = Modifier
                .width(px * 5)
                .height(px)
                .background(color)
        )
        // Vertical bar of +
        Box(
            modifier = Modifier
                .width(px)
                .height(px * 5)
                .background(color)
        )
    }
}

/**
 * Pixel art mic button — 8-bit style, hold to record.
 * Shows recording indicator when active.
 */
@Composable
private fun PixelMicButton(
    isRecording: Boolean,
    enabled: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit
) {
    val color = when {
        isRecording -> ConsoleTheme.warning
        enabled -> ConsoleTheme.text
        else -> ConsoleTheme.textMuted
    }
    val px = 3.dp // bigger pixels
    
    Box(
        modifier = Modifier
            .size(36.dp)
            .pointerInput(enabled) {
                if (enabled) {
                    detectTapGestures(
                        onPress = {
                            onStartRecording()
                            tryAwaitRelease()
                            onStopRecording()
                        }
                    )
                }
            }
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        // Pixel art microphone shape — bigger
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // Mic head top
            Box(
                modifier = Modifier
                    .width(px * 3)
                    .height(px)
                    .background(color)
            )
            // Mic head body
            Box(
                modifier = Modifier
                    .width(px * 3)
                    .height(px * 3)
                    .background(color)
            )
            // Mic stem
            Box(
                modifier = Modifier
                    .width(px)
                    .height(px * 2)
                    .background(color)
            )
            // Mic base
            Box(
                modifier = Modifier
                    .width(px * 5)
                    .height(px)
                    .background(color)
            )
        }
        
        // Recording indicator - red dot
        if (isRecording) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(8.dp)
                    .background(ConsoleTheme.warning, shape = androidx.compose.foundation.shape.CircleShape)
            )
        }
    }
}

private fun formatTime(timestamp: Long): String {
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun ConversationScreenPreview() {
    val sampleMessages = listOf(
        Message(
            id = "1",
            senderId = "04cd63",
            senderName = "04cd63",
            timestamp = System.currentTimeMillis() - 3600000,
            content = "Material delivery confirmed.",
            isMeshOrigin = true
        ),
        Message(
            id = "2",
            senderId = "me",
            senderName = "Me",
            timestamp = System.currentTimeMillis() - 3500000,
            content = "Copy that.",
            isMeshOrigin = false
        )
    )
    
    MaterialTheme {
        Surface {
            ConversationScreen(
                messages = sampleMessages,
                onSendMessage = { _, _ -> },
                localUserId = "me",
                channel = Channel(
                    id = "general",
                    beaconId = "default",
                    name = "general",
                    type = ChannelType.GROUP
                ),
                beaconName = ConsoleTheme.APP_NAME,
                onBackClick = {}
            )
        }
    }
}
