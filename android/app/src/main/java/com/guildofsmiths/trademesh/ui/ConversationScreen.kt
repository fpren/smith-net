package com.guildofsmiths.trademesh.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.guildofsmiths.trademesh.service.ChatManager
import com.guildofsmiths.trademesh.service.ConnectionMode
import com.guildofsmiths.trademesh.data.Channel
import com.guildofsmiths.trademesh.data.ChannelType
import com.guildofsmiths.trademesh.data.DeliveryStatus
import com.guildofsmiths.trademesh.data.MediaType
import com.guildofsmiths.trademesh.data.Message
import com.guildofsmiths.trademesh.data.MessageRepository
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
    pendingToolCalls: List<com.guildofsmiths.trademesh.ai.SmithAIToolExecutor.PendingToolCall> = emptyList(),
    onApproveToolCall: (String) -> Unit = {},
    onDenyToolCall: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isSmithAI = initialDmPeer?.userId == "smith-ai"
    val listState = rememberLazyListState()
    var inputText by remember { mutableStateOf("") }
    var typingUsers by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var lastTypingSent by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) {
        ChatManager.setTypingListener(object : ChatManager.OnTypingListener {
            override fun onTypingStarted(channelId: String, userId: String, userName: String) {
                typingUsers = typingUsers + (userId to userName)
            }
            override fun onTypingStopped(channelId: String, userId: String) {
                typingUsers = typingUsers - userId
            }
        })
    }

    // Peer selection for DM (initialize with passed-in peer if any)
    var showPeerSelector by remember { mutableStateOf(false) }
    var selectedPeer by remember { mutableStateOf(initialDmPeer) }
    val peers by PeerRepository.peers.collectAsState()
    val activePeers = peers.values.filter { it.isActive() }.sortedByDescending { it.lastSeen }
    
    // Online status for media
    val isNetOnline by BoundaryEngine.isOnline.collectAsState()
    val isOnline = isNetOnline

    // Connection mode for status bar and background tint.
    val connectionMode by ChatManager.connectionMode.collectAsState()
    val chatBgColor = when (connectionMode) {
        ConnectionMode.ONLINE -> Color(0xFFF4F2EE)   // default warm
        ConnectionMode.MESH -> Color(0xFFF0F4F1)     // sage tint
        ConnectionMode.OFFLINE -> Color(0xFFF4F0EE)  // brick tint
    }
    
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    
    // Check if this is a DM channel
    val isDmChannel = channel?.id?.startsWith("dm_") == true

    // Channel-options overflow menu + clear confirmation
    var menuExpanded by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    
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

            if (channel != null) {
                Box {
                    Text(
                        text = "⋯",
                        style = ConsoleTheme.title.copy(color = ConsoleTheme.textMuted),
                        modifier = Modifier
                            .clickable { menuExpanded = true }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier
                            .background(ConsoleTheme.surface, RoundedCornerShape(4.dp))
                            .border(
                                0.5.dp,
                                ConsoleTheme.text.copy(alpha = 0.12f),
                                RoundedCornerShape(4.dp)
                            )
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = "Clear messages (this device)",
                                    style = ConsoleTheme.bodySmall.copy(color = ConsoleTheme.text)
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                showClearDialog = true
                            },
                            modifier = Modifier.background(ConsoleTheme.surface)
                        )
                    }
                }
            }
        }

        ConsoleSeparator()

        ConnectionStatusBar(
            mode = connectionMode,
            isEphemeral = channel?.persistence == com.guildofsmiths.trademesh.data.ChannelPersistence.EPHEMERAL
        )

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
                        onDismissRequest = { showPeerSelector = false },
                        modifier = Modifier
                            .background(ConsoleTheme.surface, RoundedCornerShape(4.dp))
                            .border(
                                0.5.dp,
                                ConsoleTheme.text.copy(alpha = 0.12f),
                                RoundedCornerShape(4.dp)
                            )
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text("everyone (group)", style = ConsoleTheme.body.copy(color = ConsoleTheme.text))
                            },
                            onClick = {
                                selectedPeer = null
                                showPeerSelector = false
                            },
                            modifier = Modifier.background(ConsoleTheme.surface)
                        )

                        if (activePeers.isNotEmpty()) {
                            activePeers.forEach { peer ->
                                DropdownMenuItem(
                                    text = {
                                        Column {
                                            Text(peer.userName, style = ConsoleTheme.body.copy(color = ConsoleTheme.text))
                                            Text(
                                                "${peer.rssi} dBm · ${peer.lastSeenAgo()}",
                                                style = ConsoleTheme.caption
                                            )
                                        }
                                    },
                                    onClick = {
                                        selectedPeer = peer
                                        showPeerSelector = false
                                    },
                                    modifier = Modifier.background(ConsoleTheme.surface)
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

        if (isSmithAI) {
            SmithAIStatusBanner()
        }

        // Messages
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(chatBgColor)
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
            
            itemsIndexed(items = messages, key = { _, msg -> msg.id }) { index, message ->
                val previous = if (index > 0) messages[index - 1] else null
                val showHeader = shouldShowHeader(message, previous)

                val isSentByMe = if (localUserId.isNotEmpty()) {
                    message.senderId == localUserId
                } else {
                    !message.isMeshOrigin
                }

                if (isDifferentDay(message, previous)) {
                    DateSeparator(date = formatDate(message.timestamp))
                }

                Spacer(modifier = Modifier.height(if (showHeader) 6.dp else 3.dp))

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
                        MessageBlock(message = message, isSentByMe = isSentByMe, showHeader = showHeader)
                    }
                }
            }

            if (isSmithAI && pendingToolCalls.isNotEmpty()) {
                items(items = pendingToolCalls, key = { it.id }) { pending ->
                    Spacer(modifier = Modifier.height(8.dp))
                    ToolCallApprovalCard(
                        pending = pending,
                        onApprove = { onApproveToolCall(pending.id) },
                        onDeny = { onDenyToolCall(pending.id) }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }

        ConsoleSeparator()

        TypingIndicator(typingUsers.values.toList())

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
                    
                    androidx.compose.material3.DropdownMenu(
                        expanded = showAttachMenu,
                        onDismissRequest = { showAttachMenu = false },
                        modifier = Modifier
                            .background(ConsoleTheme.surface, RoundedCornerShape(4.dp))
                            .border(
                                0.5.dp,
                                ConsoleTheme.text.copy(alpha = 0.12f),
                                RoundedCornerShape(4.dp)
                            )
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
                    onValueChange = {
                        inputText = it
                        val now = System.currentTimeMillis()
                        if (now - lastTypingSent > 1000) {
                            ChatManager.sendTypingStart(channel?.id ?: "")
                            lastTypingSent = now
                        }
                    },
                    modifier = Modifier.weight(1f),
                    textStyle = ConsoleTheme.body,
                    cursorBrush = SolidColor(ConsoleTheme.cursor),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        if (inputText.isNotBlank()) {
                            ChatManager.sendTypingStop(channel?.id ?: "")
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
                                ChatManager.sendTypingStop(channel?.id ?: "")
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

    if (showClearDialog && channel != null) {
        val channelLabel = if (channel.type == ChannelType.DM) "this DM" else "#${channel.name}"
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = {
                Text(
                    text = "Clear messages on this device?",
                    style = ConsoleTheme.bodyBold
                )
            },
            text = {
                Text(
                    text = "Removes every message in $channelLabel from this device. Cloud history stays. New messages will still arrive.",
                    style = ConsoleTheme.bodySmall
                )
            },
            confirmButton = {
                Text(
                    text = "[CLEAR]",
                    style = ConsoleTheme.action.copy(color = ConsoleTheme.accent),
                    modifier = Modifier
                        .clickable {
                            MessageRepository.clearChannel(channel.beaconId, channel.id)
                            showClearDialog = false
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            },
            dismissButton = {
                Text(
                    text = "[CANCEL]",
                    style = ConsoleTheme.action,
                    modifier = Modifier
                        .clickable { showClearDialog = false }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            },
            containerColor = ConsoleTheme.surface
        )
    }
}


@Composable
private fun TypingIndicator(typers: List<String>) {
    if (typers.isEmpty()) return
    val text = when {
        typers.size == 1 -> "◀ ${typers[0]} is typing..."
        typers.size == 2 -> "◀ ${typers[0]}, ${typers[1]} are typing..."
        else -> "◀ ${typers[0]} and ${typers.size - 1} others are typing..."
    }
    Text(
        text = text,
        style = ConsoleTheme.caption.copy(
            color = ConsoleTheme.textMuted
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

@Composable
private fun DateSeparator(date: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f).height(0.5.dp).background(ConsoleTheme.separatorFaint))
        Text(
            text = "── $date ──",
            style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted),
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Box(modifier = Modifier.weight(1f).height(0.5.dp).background(ConsoleTheme.separatorFaint))
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
    showHeader: Boolean = true,
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
            if (showHeader) {
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
                    if (isSentByMe) {
                        val statusText = when (message.deliveryStatus) {
                            DeliveryStatus.PENDING -> "[...]"
                            DeliveryStatus.SENT -> "[✓]"
                            DeliveryStatus.DELIVERED -> "[✓✓]"
                            DeliveryStatus.READ -> "[✓✓]"
                        }
                        val statusColor = if (message.deliveryStatus == DeliveryStatus.READ)
                            Color(0xFF5A8C76)  // sage green
                        else
                            ConsoleTheme.textMuted
                        Text(
                            text = " $statusText",
                            style = ConsoleTheme.timestamp.copy(color = statusColor)
                        )
                    }
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
            } else {
                Spacer(modifier = Modifier.height(3.dp))
            }
            
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
                                style = ConsoleTheme.bodySmall,
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

private fun shouldShowHeader(current: Message, previous: Message?): Boolean {
    if (previous == null) return true
    if (current.senderId != previous.senderId) return true
    if (current.timestamp - previous.timestamp > 120_000) return true // 2 min gap
    return false
}

private fun isDifferentDay(current: Message, previous: Message?): Boolean {
    if (previous == null) return true
    val cal1 = java.util.Calendar.getInstance().apply { timeInMillis = current.timestamp }
    val cal2 = java.util.Calendar.getInstance().apply { timeInMillis = previous.timestamp }
    return cal1.get(java.util.Calendar.DAY_OF_YEAR) != cal2.get(java.util.Calendar.DAY_OF_YEAR)
        || cal1.get(java.util.Calendar.YEAR) != cal2.get(java.util.Calendar.YEAR)
}

private fun formatDate(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.US)
    return sdf.format(java.util.Date(timestamp))
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

@Composable
private fun ConnectionStatusBar(mode: ConnectionMode, isEphemeral: Boolean = false) {
    val (color, label, baseDetail) = when (mode) {
        ConnectionMode.ONLINE -> Triple(Color(0xFF9A6F2E), "[ONLINE ●]", "ws://connected")
        ConnectionMode.MESH -> Triple(Color(0xFF5A8C76), "[MESH ●]", "peers nearby")
        ConnectionMode.OFFLINE -> Triple(Color(0xFF8C3A3A), "[OFFLINE ●]", "queued")
    }
    val detail = if (isEphemeral) "ephemeral · $baseDetail" else baseDetail

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = ConsoleTheme.caption.copy(color = color, fontWeight = FontWeight.SemiBold)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = detail,
            style = ConsoleTheme.timestamp.copy(color = ConsoleTheme.textMuted)
        )
    }
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

@Composable
private fun SmithAIStatusBanner() {
    val agentState by com.guildofsmiths.trademesh.ai.AgentInitializer.agentState.collectAsState()
    val initProgress by com.guildofsmiths.trademesh.ai.AgentInitializer.initializationProgress.collectAsState()
    val modelState by com.guildofsmiths.trademesh.ai.LlamaInference.modelState.collectAsState()
    val gateState by com.guildofsmiths.trademesh.ai.BatteryGate.gateState.collectAsState()
    val backend = com.guildofsmiths.trademesh.ai.SmithAIBackendRouter.pick()

    val label = when {
        agentState == com.guildofsmiths.trademesh.ai.AgentState.WAKING ->
            "[AI WAKING ${(initProgress * 100).toInt()}%]"
        backend == com.guildofsmiths.trademesh.ai.SmithAIBackendRouter.Backend.ON_DEVICE &&
            modelState == com.guildofsmiths.trademesh.ai.ModelState.READY ->
            "[AI READY] on-device · battery ${gateState.batteryLevel}%"
        backend == com.guildofsmiths.trademesh.ai.SmithAIBackendRouter.Backend.CLOUD ->
            "[CLOUD] battery ${gateState.batteryLevel}%"
        modelState == com.guildofsmiths.trademesh.ai.ModelState.NOT_LOADED ->
            "[MODEL NOT LOADED] open Settings to download Qwen3"
        else ->
            "[AI OFFLINE] charge device or set API key in Settings"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(ConsoleTheme.surface)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = ConsoleTheme.caption.copy(color = ConsoleTheme.accent)
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(ConsoleTheme.separator)
    )
}

@Composable
private fun ToolCallApprovalCard(
    pending: com.guildofsmiths.trademesh.ai.SmithAIToolExecutor.PendingToolCall,
    onApprove: () -> Unit,
    onDeny: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(ConsoleTheme.surface, RoundedCornerShape(4.dp))
            .border(0.5.dp, ConsoleTheme.accent.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = "[ACTION] ${pending.toolName}",
            style = ConsoleTheme.captionBold.copy(color = ConsoleTheme.accent)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = pending.argsSummary,
            style = ConsoleTheme.bodySmall
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "[APPROVE]",
                style = ConsoleTheme.action.copy(color = ConsoleTheme.success),
                modifier = Modifier
                    .clickable(onClick = onApprove)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "[DENY]",
                style = ConsoleTheme.action.copy(color = ConsoleTheme.error),
                modifier = Modifier
                    .clickable(onClick = onDeny)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}
