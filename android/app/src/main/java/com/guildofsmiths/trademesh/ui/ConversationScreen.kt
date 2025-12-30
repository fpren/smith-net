package com.guildofsmiths.trademesh.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
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
 * Message action types for swipe actions.
 */
enum class MessageAction {
    DELETE_FOR_ME,      // Delete only from this device
    DELETE_FOR_EVERYONE, // Delete from backend + all devices (requires permission)
    ARCHIVE             // Archive the message
}

/**
 * Smith Net conversation — bold, chic, left/right aligned.
 * Swipe RIGHT = Archive, Swipe LEFT = Delete options.
 */
@Composable
fun ConversationScreen(
    messages: List<Message>,
    onSendMessage: (String, Peer?) -> Unit,  // content + optional DM recipient
    onMessageAction: ((Message, MessageAction) -> Unit)? = null,  // Swipe action handler
    localUserId: String = "",
    channel: Channel? = null,
    beaconName: String? = null,
    canDeleteForAll: Boolean = false,  // True if user created channel or has permission
    onBackClick: (() -> Unit)? = null,
    onVoiceClick: (() -> Unit)? = null,
    onCameraClick: (() -> Unit)? = null,
    onVideoClick: (() -> Unit)? = null,
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
                
                // Swipe state
                var offsetX by remember { mutableStateOf(0f) }
                val animatedOffsetX by animateFloatAsState(
                    targetValue = offsetX,
                    label = "swipe"
                )
                
                // Action button width
                val actionButtonWidth = 80.dp
                val density = androidx.compose.ui.platform.LocalDensity.current
                
                // Left side: Archive (swipe right reveals)
                val maxSwipeRight = with(density) { actionButtonWidth.toPx() }
                
                // Right side: Delete options (swipe left reveals)
                // Show "Delete for all" only if user has permission (created channel or granted)
                val maxSwipeLeft = with(density) {
                    if (canDeleteForAll) actionButtonWidth.toPx() * 2 else actionButtonWidth.toPx()
                }
                
                // Fixed height for swipe actions
                val messageHeight = 60.dp
                
                // Can only delete YOUR OWN messages
                // Archive is available for any message (just hides from your view)
                val canDelete = isSentByMe
                val canDeleteAll = isSentByMe && canDeleteForAll
                
                // Adjust max swipe based on what's available
                val actualMaxSwipeLeft = with(density) {
                    when {
                        canDeleteAll -> actionButtonWidth.toPx() * 2  // Delete for me + Delete for all
                        canDelete -> actionButtonWidth.toPx()          // Only Delete for me
                        else -> 0f                                      // Can't delete others' messages
                    }
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(messageHeight)
                ) {
                    // LEFT SIDE - Archive button (revealed on swipe RIGHT)
                    // Archive is always available - just hides from YOUR view
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .width(actionButtonWidth)
                            .fillMaxHeight()
                            .background(ConsoleTheme.accent)
                            .clickable {
                                offsetX = 0f
                                onMessageAction?.invoke(message, MessageAction.ARCHIVE)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Archive",
                            style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.background)
                        )
                    }
                    
                    // RIGHT SIDE - Delete buttons (only for YOUR OWN messages)
                    if (canDelete) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Delete for me button (only for your own messages)
                            Box(
                                modifier = Modifier
                                    .width(actionButtonWidth)
                                    .fillMaxHeight()
                                    .background(ConsoleTheme.warning)
                                    .clickable {
                                        offsetX = 0f
                                        onMessageAction?.invoke(message, MessageAction.DELETE_FOR_ME)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "Delete",
                                        style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.background)
                                    )
                                    Text(
                                        "for me",
                                        style = ConsoleTheme.caption.copy(color = ConsoleTheme.background)
                                    )
                                }
                            }
                            
                            // Delete for everyone (only if you sent it AND have permission)
                            if (canDeleteAll) {
                                Box(
                                    modifier = Modifier
                                        .width(actionButtonWidth)
                                        .fillMaxHeight()
                                        .background(ConsoleTheme.error)
                                        .clickable {
                                            offsetX = 0f
                                            onMessageAction?.invoke(message, MessageAction.DELETE_FOR_EVERYONE)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            "Delete",
                                            style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.background)
                                        )
                                        Text(
                                            "for all",
                                            style = ConsoleTheme.caption.copy(color = ConsoleTheme.background)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    
                    // Foreground message (swipeable)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
                            .background(ConsoleTheme.background)
                            .draggable(
                                orientation = Orientation.Horizontal,
                                state = rememberDraggableState { delta ->
                                    // Swipe RIGHT = archive (always allowed)
                                    // Swipe LEFT = delete (only for own messages)
                                    val newOffset = (offsetX + delta).coerceIn(-actualMaxSwipeLeft, maxSwipeRight)
                                    offsetX = newOffset
                                },
                                onDragStopped = {
                                    // Snap to open or closed position
                                    offsetX = when {
                                        offsetX > maxSwipeRight / 2 -> maxSwipeRight  // Snap open right (archive)
                                        offsetX < -actualMaxSwipeLeft / 2 && canDelete -> -actualMaxSwipeLeft  // Snap open left (delete) - only if allowed
                                        else -> 0f  // Snap closed
                                    }
                                }
                            ),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        MessageBlock(message = message, isSentByMe = isSentByMe)
                    }
                }
                
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
        var recordingDuration by remember { mutableStateOf(0L) }
        
        // Recording timer
        LaunchedEffect(isRecording) {
            if (isRecording) {
                recordingDuration = 0L
                while (isRecording) {
                    kotlinx.coroutines.delay(100)
                    recordingDuration += 100
                }
            }
        }
        
        // For DM channels, always send to the DM peer
        val effectivePeer = if (isDmChannel) initialDmPeer else selectedPeer
        
        // Show RECORDING BAR when recording, otherwise show normal input
        if (isRecording) {
            // ═══════════════════════════════════════════════════════════
            // RECORDING MODE - Full width recording indicator
            // ═══════════════════════════════════════════════════════════
            RecordingBar(
                duration = recordingDuration,
                onCancel = {
                    isRecording = false
                    // Cancel recording without sending
                },
                onStop = {
                    isRecording = false
                    onVoiceClick?.invoke()  // Stop and send
                }
            )
        } else {
            // ═══════════════════════════════════════════════════════════
            // NORMAL INPUT MODE
            // ═══════════════════════════════════════════════════════════
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
                        
                        // Video option with pixel video icon
                        Row(
                            modifier = Modifier
                                .clickable(enabled = isOnline) {
                                    showAttachMenu = false
                                    onVideoClick?.invoke()
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PixelVideo(enabled = isOnline)
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "video",
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
                    // Pixel art mic — tap to start recording
                    PixelMicButton(
                        isRecording = false,
                        enabled = isOnline,
                        onStartRecording = { 
                            isRecording = true
                            onVoiceClick?.invoke()  // Start recording
                        },
                        onStopRecording = { }
                    )
                }
            }
        }
        
        // Offline media hint
        if (!isOnline && !isRecording) {
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
                } else if (!message.isMeshOrigin && !isSentByMe) {
                    Text(
                        text = " [online]",
                        style = ConsoleTheme.timestamp.copy(color = ConsoleTheme.textDim)
                    )
                }
                // #region agent log
                try {
                    val data = mapOf(
                        "sessionId" to "debug-session",
                        "runId" to "transport-indicators-test",
                        "hypothesisId" to "A",
                        "location" to "ConversationScreen.kt:707",
                        "message" to "Transport indicator check",
                        "data" to mapOf(
                            "messageId" to message.id.take(8),
                            "senderName" to message.senderName,
                            "isMeshOrigin" to message.isMeshOrigin,
                            "isSentByMe" to isSentByMe,
                            "hasSubIndicator" to (message.isMeshOrigin && !isSentByMe),
                            "hasOnlineIndicator" to (!message.isMeshOrigin && !isSentByMe)
                        ),
                        "timestamp" to System.currentTimeMillis()
                    )
                    val jsonPayload = org.json.JSONObject(data).toString()
                    val url = java.net.URL("http://127.0.0.1:7242/ingest/0adb3485-1a4e-45bf-a3c0-30e8c05573e2")
                    val connection = url.openConnection() as java.net.HttpURLConnection
                    connection.requestMethod = "POST"
                    connection.setRequestProperty("Content-Type", "application/json")
                    connection.doOutput = true
                    connection.outputStream.write(jsonPayload.toByteArray())
                    connection.inputStream.close()
                } catch (e: Exception) {
                    // Ignore logging errors
                }
                // #endregion
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
                        // Interactive media player (voice/image/file)
                        if (message.hasMedia()) {
                            MediaIndicator(
                                type = message.mediaType, 
                                media = message.media
                            )
                        }
                        // Text content
                        MessageTextContent(
                            message = message,
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
                        // Interactive media player (voice/image/file)
                        if (message.hasMedia()) {
                            MediaIndicator(
                                type = message.mediaType, 
                                media = message.media
                            )
                        }
                        // Text content
                        MessageTextContent(
                            message = message,
                            modifier = Modifier.padding(start = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Helper composable for rendering message text content
 */
@Composable
private fun MessageTextContent(
    message: Message,
    modifier: Modifier = Modifier
) {
    // Only show text if NO media, or if media is queued (offline placeholder)
                        // Don't show text when we have valid media with a remote URL
                        // Also hide text if it's just a media placeholder from Dashboard (e.g., "[VIDEO] filename")
                        val isMediaPlaceholderText = message.content.startsWith("[VIDEO]") || 
                            message.content.startsWith("[IMAGE]") ||
                            message.content.startsWith("[VOICE]") ||
                            message.content.startsWith("[FILE]")
                        val showTextContent = !message.hasMedia() && !isMediaPlaceholderText
                        if (showTextContent) {
                            Text(
                                text = message.content,
                                style = ConsoleTheme.body,
            modifier = modifier
                            )
    }
}

/**
 * Interactive media display — pixel art style with playback/open functionality.
 * - [■] document/file — tap to open
 * - [▶] [■■■■■■□□□□] voice — tap to play with animated progress
 * - [▣] image — tap to view full screen
 */
@Composable
private fun MediaIndicator(
    type: MediaType,
    media: com.guildofsmiths.trademesh.data.MediaAttachment?,
    modifier: Modifier = Modifier
) {
    if (type == MediaType.TEXT || media == null) return
    
    // Use the interactive media player
    InteractiveMediaPlayer(
        mediaType = type,
        media = media,
        modifier = modifier.padding(bottom = 4.dp)
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
 * Pixel art video/film — 8-bit style.
 */
@Composable
private fun PixelVideo(enabled: Boolean) {
    val color = if (enabled) ConsoleTheme.text else ConsoleTheme.textDim
    val px = 2.dp
    
    Box(
        modifier = Modifier.size(20.dp),
        contentAlignment = Alignment.Center
    ) {
        // Film frame body
        Box(modifier = Modifier.width(px * 6).height(px * 4).background(color))
        // Sprocket holes (top)
        Row(
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 1.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(modifier = Modifier.size(px).background(ConsoleTheme.background))
            Box(modifier = Modifier.size(px).background(ConsoleTheme.background))
        }
        // Sprocket holes (bottom)
        Row(
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 1.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Box(modifier = Modifier.size(px).background(ConsoleTheme.background))
            Box(modifier = Modifier.size(px).background(ConsoleTheme.background))
        }
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

/**
 * Recording bar - shows when voice recording is active.
 * Displays: [●] REC [||||] 0:05 [✕] [■]
 */
@Composable
private fun RecordingBar(
    duration: Long,
    onCancel: () -> Unit,
    onStop: () -> Unit
) {
    // Audio level (0-5) - updated from MediaHelper
    var audioLevel by remember { mutableStateOf(0) }
    
    // Poll audio level from recorder
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(100)
            audioLevel = com.guildofsmiths.trademesh.service.MediaHelper.getAudioLevel()
        }
    }
    
    // Format duration as m:ss
    val seconds = (duration / 1000).toInt()
    val mins = seconds / 60
    val secs = seconds % 60
    val timeText = "$mins:${secs.toString().padStart(2, '0')}"
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ConsoleTheme.surface)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Pulsing REC indicator
        val pulseAlpha = if ((duration / 500) % 2 == 0L) 1f else 0.5f
        Text(
            text = "[●]",
            style = ConsoleTheme.body.copy(
                color = ConsoleTheme.warning.copy(alpha = pulseAlpha)
            )
        )
        
        Spacer(modifier = Modifier.width(4.dp))
        
        Text(
            text = "REC",
            style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.warning)
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Audio level waveform: [|||||] - reacts to sound
        AudioLevelBars(level = audioLevel)
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Duration
        Text(
            text = timeText,
            style = ConsoleTheme.body.copy(color = ConsoleTheme.text)
        )
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Cancel button
        Text(
            text = "[✕]",
            style = ConsoleTheme.body.copy(color = ConsoleTheme.textMuted),
            modifier = Modifier
                .clickable(onClick = onCancel)
                .padding(4.dp)
        )
        
        Spacer(modifier = Modifier.width(8.dp))
        
        // Stop & Send button - [■] matches the [▶] play style
        Text(
            text = "[■]",
            style = ConsoleTheme.bodyBold.copy(color = ConsoleTheme.accent),
            modifier = Modifier
                .clickable(onClick = onStop)
                .padding(4.dp)
        )
    }
}

/**
 * Audio level bars that react to actual microphone input.
 * Shows [||||||||||] where bars light up based on volume level (0-12)
 */
@Composable
private fun AudioLevelBars(
    level: Int,
    modifier: Modifier = Modifier
) {
    val maxBars = 12
    // Scale level (0-6) to maxBars
    val activeBars = ((level / 6.0) * maxBars).toInt().coerceIn(0, maxBars)
    
    val pattern = buildString {
        append("[")
        for (i in 0 until maxBars) {
            if (i < activeBars) {
                append("|")
            } else {
                append("·")  // Dim dot for inactive
            }
        }
        append("]")
    }
    
    Text(
        text = pattern,
        style = ConsoleTheme.body.copy(
            color = when {
                activeBars >= 10 -> ConsoleTheme.warning  // Loud = orange/yellow
                activeBars >= 5 -> ConsoleTheme.accent    // Medium = accent color
                else -> ConsoleTheme.textMuted            // Quiet = muted
            }
        ),
        modifier = modifier
    )
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
