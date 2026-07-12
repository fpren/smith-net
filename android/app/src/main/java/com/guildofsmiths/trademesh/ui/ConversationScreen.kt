package com.guildofsmiths.trademesh.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Popup
import kotlin.math.roundToInt
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.guildofsmiths.trademesh.ui.components.SmithAvatar
import com.guildofsmiths.trademesh.ui.theme2.LocalSmithColors
import com.guildofsmiths.trademesh.ui.theme2.SmithConfirmDialog
import com.guildofsmiths.trademesh.ui.theme2.SmithSheet
import com.guildofsmiths.trademesh.ui.theme2.SmithType
import kotlinx.coroutines.launch
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
    onRetryMessage: ((String) -> Unit)? = null,  // Retry a FAILED own-message send
    localUserId: String = "",
    channel: Channel? = null,
    beaconName: String? = null,
    // Snapshot of unread count taken the moment this channel was opened
    // (before it was cleared) — drives the frozen "NEW" divider position.
    unreadAtOpen: Int = 0,
    canDeleteForAll: Boolean = false,  // True if user created channel or has permission
    onBackClick: (() -> Unit)? = null,
    onUnreadSnapshotConsumed: (() -> Unit)? = null,
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
    val colors = LocalSmithColors.current
    val isSmithAI = initialDmPeer?.userId == "smith-ai"
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // NEW divider position — frozen the first time it's computed for this
    // channel so late-arriving messages don't shift it. -1 means "no divider".
    // Keyed on messages.isEmpty() too: on cold start / deep link the messages
    // StateFlow can still be emptyList() the first frame channel.id is
    // non-null, which would freeze the index at the wrong (zero) size; this
    // lets it recompute once when messages first load, then stays frozen.
    val newDividerIndex = remember(channel?.id, messages.isEmpty()) {
        if (messages.isEmpty() || unreadAtOpen <= 0) -1
        else (messages.size - unreadAtOpen).coerceAtLeast(0)
    }

    // Retire the snapshot once the divider has frozen against real messages.
    // Runs post-composition (LaunchedEffect), so the remember above has already
    // read the value this frame. Re-fires on mid-open re-snapshots (unreadAtOpen
    // change) so they can't go stale either; the frozen divider is unaffected.
    LaunchedEffect(unreadAtOpen, messages.isEmpty()) {
        if (messages.isNotEmpty() && unreadAtOpen > 0) {
            onUnreadSnapshotConsumed?.invoke()
        }
    }

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

    // Emit a read receipt for each incoming message (not our own) exactly once
    // per message id, mirroring the web's once-per-id pattern. The remembered
    // set survives recomposition but not navigation away from this screen.
    val sentReadReceiptIds = remember { mutableSetOf<String>() }
    LaunchedEffect(messages) {
        messages.forEach { message ->
            if (message.senderId != localUserId && sentReadReceiptIds.add(message.id)) {
                ChatManager.sendReadReceipt(message.id, channel?.id ?: message.channelId)
            }
        }
    }

    // Peer selection for DM (initialize with passed-in peer if any)
    var showPeerSelector by remember { mutableStateOf(false) }
    var selectedPeer by remember { mutableStateOf(initialDmPeer) }
    val peers by PeerRepository.peers.collectAsState()
    val activePeers = peers.values.filter { it.isActive() }.sortedByDescending { it.lastSeen }
    
    // Online status for media
    val isNetOnline by BoundaryEngine.isOnline.collectAsState()
    val isOnline = isNetOnline

    // Connection mode for status bar and background tint. ONLINE is the
    // neutral chat surface; MESH/OFFLINE keep a faint job-matching wash
    // (sage/statusOnline for mesh, brick/statusError for offline) instead of
    // a baked hex, so the tint stays legible under both palettes.
    val connectionMode by ChatManager.connectionMode.collectAsState()
    val chatBgColor = when (connectionMode) {
        ConnectionMode.ONLINE -> colors.bgBase
        ConnectionMode.MESH -> colors.statusOnline.copy(alpha = 0.06f)
        ConnectionMode.OFFLINE -> colors.statusError.copy(alpha = 0.05f)
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

    // Long-press action sheet target (Task 9)
    var actionTarget by remember { mutableStateOf<Message?>(null) }
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.bgBase)
    ) {
        // Bold header - show peer name for DM, channel name otherwise
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.bgPanel)
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
                    style = SmithType.title.copy(color = colors.ink)
                )
                Spacer(modifier = Modifier.width(12.dp))
            }

            // Screen-pop context avatar (DM peer / channel glyph)
            if (isDmChannel && initialDmPeer != null) {
                com.guildofsmiths.trademesh.ui.components.SmithAvatar(
                    name = initialDmPeer.userName,
                    size = 36,
                    statusColor = if (isOnline) colors.statusOnline else colors.inkMuted
                )
                Spacer(modifier = Modifier.width(10.dp))
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isDmChannel && initialDmPeer != null) {
                        initialDmPeer.userName
                    } else {
                        channel?.name?.uppercase() ?: "GENERAL"
                    },
                    style = (if (isDmChannel) SmithType.commName.copy(fontSize = 17.sp) else SmithType.title).copy(color = colors.ink)
                )
                if (beaconName != null && !isDmChannel) {
                    Text(
                        text = beaconName,
                        style = SmithType.caption.copy(color = colors.inkMuted)
                    )
                } else if (isDmChannel) {
                    Text(
                        text = if (isOnline) "[*] online · direct message" else "direct message",
                        style = SmithType.commId.copy(color = colors.inkMuted)
                    )
                }
            }

            if (channel != null) {
                Box {
                    Text(
                        text = "⋯",
                        style = SmithType.title.copy(color = colors.inkMuted),
                        modifier = Modifier
                            .clickable { menuExpanded = true }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                    if (menuExpanded) {
                        Popup(onDismissRequest = { menuExpanded = false }) {
                            Column(
                                modifier = Modifier
                                    .background(colors.bgPanel, RoundedCornerShape(Tokens2.RadiusControl))
                                    .border(1.dp, colors.line, RoundedCornerShape(Tokens2.RadiusControl))
                                    .width(IntrinsicSize.Max),
                            ) {
                                Text(
                                    text = "Clear messages (this device)",
                                    style = SmithType.bodySmall.copy(color = colors.ink),
                                    modifier = Modifier.fillMaxWidth()
                                        .clickable {
                                            menuExpanded = false
                                            showClearDialog = true
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                )
                            }
                        }
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
                    .background(colors.bgBase)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "TO:",
                    style = SmithType.captionBold.copy(color = colors.inkMuted)
                )
                Spacer(modifier = Modifier.width(8.dp))

                Box {
                    Text(
                        text = selectedPeer?.userName ?: "everyone",
                        style = SmithType.bodyBold.copy(
                            color = if (selectedPeer != null) colors.accent else colors.ink
                        ),
                        modifier = Modifier
                            .clickable { showPeerSelector = true }
                            .padding(4.dp)
                    )

                    if (showPeerSelector) {
                        Popup(onDismissRequest = { showPeerSelector = false }) {
                            Column(
                                modifier = Modifier
                                    .background(colors.bgPanel, RoundedCornerShape(Tokens2.RadiusControl))
                                    .border(1.dp, colors.line, RoundedCornerShape(Tokens2.RadiusControl))
                                    .width(IntrinsicSize.Max),
                            ) {
                                Text(
                                    text = "everyone (group)",
                                    style = SmithType.body.copy(color = colors.ink),
                                    modifier = Modifier.fillMaxWidth()
                                        .clickable {
                                            selectedPeer = null
                                            showPeerSelector = false
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                )

                                if (activePeers.isNotEmpty()) {
                                    activePeers.forEach { peer ->
                                        Column(
                                            modifier = Modifier.fillMaxWidth()
                                                .clickable {
                                                    selectedPeer = peer
                                                    showPeerSelector = false
                                                }
                                                .padding(horizontal = 12.dp, vertical = 10.dp),
                                        ) {
                                            Text(peer.userName, style = SmithType.body.copy(color = colors.ink))
                                            Text(
                                                "${peer.rssi} dBm · ${peer.lastSeenAgo()}",
                                                style = SmithType.caption.copy(color = colors.inkMuted)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                if (selectedPeer != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "×",
                        style = SmithType.body.copy(color = colors.inkMuted),
                        modifier = Modifier
                            .clickable { selectedPeer = null }
                            .padding(4.dp)
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                if (activePeers.isNotEmpty()) {
                    Text(
                        text = "${activePeers.size} nearby",
                        style = SmithType.caption.copy(color = colors.inkMuted)
                    )
                }
            }

            ConsoleSeparator()
        }

        if (isSmithAI) {
            SmithAIStatusBanner()
        }

        // Messages
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(chatBgColor)
        ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
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
                            style = SmithType.bodySmall.copy(color = colors.inkMuted)
                        )
                    }
                }
            }
            
            itemsIndexed(items = messages, key = { _, msg -> msg.id }) { index, message ->
                if (index == newDividerIndex) {
                    NewMessagesDivider(chatBgColor = chatBgColor)
                }

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
                        .height(IntrinsicSize.Min)
                ) {
                    // Reveal layer: sized to match the foreground content's
                    // resolved height (via matchParentSize) instead of a fixed
                    // 60dp — tall/media messages no longer clip their swipe
                    // reveal backgrounds.
                    Box(modifier = Modifier.matchParentSize()) {
                        // LEFT SIDE - Archive button (revealed on swipe RIGHT)
                        // Archive is always available - just hides from YOUR view
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .width(actionButtonWidth)
                                .fillMaxHeight()
                                .background(colors.accent)
                                .clickable {
                                    offsetX = 0f
                                    onMessageAction?.invoke(message, MessageAction.ARCHIVE)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Archive",
                                style = SmithType.captionBold.copy(color = colors.inkOnAccent)
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
                                        .background(colors.attention)
                                        .clickable {
                                            offsetX = 0f
                                            onMessageAction?.invoke(message, MessageAction.DELETE_FOR_ME)
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            "Delete",
                                            style = SmithType.captionBold.copy(color = colors.inkOnAccent)
                                        )
                                        Text(
                                            "for me",
                                            style = SmithType.caption.copy(color = colors.inkOnAccent)
                                        )
                                    }
                                }

                                // Delete for everyone (only if you sent it AND have permission)
                                if (canDeleteAll) {
                                    Box(
                                        modifier = Modifier
                                            .width(actionButtonWidth)
                                            .fillMaxHeight()
                                            .background(colors.statusError)
                                            .clickable {
                                                offsetX = 0f
                                                onMessageAction?.invoke(message, MessageAction.DELETE_FOR_EVERYONE)
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text(
                                                "Delete",
                                                style = SmithType.captionBold.copy(color = colors.inkOnAccent)
                                            )
                                            Text(
                                                "for all",
                                                style = SmithType.caption.copy(color = colors.inkOnAccent)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Foreground message (swipeable)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset { IntOffset(animatedOffsetX.roundToInt(), 0) }
                            .background(colors.bgBase)
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
                        MessageBlock(
                            message = message,
                            isSentByMe = isSentByMe,
                            showHeader = showHeader,
                            onRetryMessage = onRetryMessage,
                            onLongPress = { actionTarget = message }
                        )
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

        // Jump-to-latest pill — shown once the reader has scrolled well above
        // the tail of the conversation.
        val showJumpToLatest = messages.size > 8 &&
            listState.firstVisibleItemIndex < messages.size - 8
        if (showJumpToLatest) {
            Text(
                text = "↓ LATEST",
                style = TextStyle(
                    fontFamily = ConsoleTheme.jetBrainsMono,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = colors.inkOnAccent,
                    letterSpacing = 0.5.sp
                ),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 12.dp)
                    .clip(RoundedCornerShape(Tokens2.RadiusPill))
                    .background(colors.accent)
                    .clickable {
                        scope.launch {
                            listState.animateScrollToItem(messages.size - 1)
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
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
                    .background(colors.bgPanel)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Pixel art + button for attachments
                Box {
                    PixelPlusButton(
                        enabled = isOnline,
                        onClick = { showAttachMenu = !showAttachMenu }
                    )

                    if (showAttachMenu) {
                        Popup(onDismissRequest = { showAttachMenu = false }) {
                            Column(
                                modifier = Modifier
                                    .background(colors.bgPanel, RoundedCornerShape(Tokens2.RadiusControl))
                                    .border(1.dp, colors.line, RoundedCornerShape(Tokens2.RadiusControl))
                                    .width(IntrinsicSize.Max),
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
                                        style = SmithType.body.copy(
                                            color = if (isOnline) colors.ink else colors.inkMuted
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
                                        style = SmithType.body.copy(
                                            color = if (isOnline) colors.ink else colors.inkMuted
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
                                        style = SmithType.body.copy(
                                            color = if (isOnline) colors.ink else colors.inkMuted
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
                
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
                    modifier = Modifier
                        .weight(1f)
                        .background(colors.bgBase, RoundedCornerShape(Tokens2.RadiusCard))
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    textStyle = SmithType.commBody.copy(color = colors.ink),
                    cursorBrush = SolidColor(colors.ink),
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
                                    style = SmithType.body.copy(color = colors.inkMuted)
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
                        text = if (isDmChannel || selectedPeer != null) "DM" else "send",
                        style = SmithType.commName.copy(color = colors.inkOnAccent, fontSize = 14.sp),
                        modifier = Modifier
                            .clickable {
                                ChatManager.sendTypingStop(channel?.id ?: "")
                                onSendMessage(inputText.trim(), effectivePeer)
                                inputText = ""
                                if (!isDmChannel) selectedPeer = null  // Only clear if not in DM channel
                            }
                            .background(colors.accent, RoundedCornerShape(Tokens2.RadiusCard))
                            .padding(horizontal = 16.dp, vertical = 8.dp)
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
                    .background(colors.bgPanel)
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "media requires online · text works on mesh",
                    style = SmithType.caption.copy(color = colors.inkMuted)
                )
            }
        }
    }

    if (showClearDialog && channel != null) {
        val channelLabel = if (channel.type == ChannelType.DM) "this DM" else "#${channel.name}"
        SmithConfirmDialog(
            title = "Clear messages on this device?",
            body = "Removes every message in $channelLabel from this device. Cloud history stays. New messages will still arrive.",
            confirmText = "CLEAR",
            onConfirm = {
                MessageRepository.clearChannel(channel.beaconId, channel.id)
                showClearDialog = false
            },
            onDismiss = { showClearDialog = false },
        )
    }

    // Long-press action sheet (Task 9) — COPY, DELETE FOR ME, DELETE FOR
    // EVERYONE (same canDeleteForAll && isSentByMe gate as the swipe path),
    // RETRY (FAILED own-messages only).
    actionTarget?.let { target ->
        val clipboardManager = LocalClipboardManager.current
        val targetIsSentByMe = if (localUserId.isNotEmpty()) {
            target.senderId == localUserId
        } else {
            !target.isMeshOrigin
        }
        val targetCanDeleteAll = targetIsSentByMe && canDeleteForAll

        SmithSheet(onDismiss = { actionTarget = null }) {
            ActionSheetRow(
                label = "COPY",
                color = colors.ink,
                onClick = {
                    clipboardManager.setText(AnnotatedString(target.content))
                    actionTarget = null
                }
            )
            // Gated the same way the swipe path gates its "Delete for me"
            // button (line ~481: `val canDelete = isSentByMe`) — you can only
            // delete your own messages from this device.
            if (targetIsSentByMe) {
                ActionSheetRow(
                    label = "DELETE FOR ME",
                    color = colors.statusError,
                    onClick = {
                        onMessageAction?.invoke(target, MessageAction.DELETE_FOR_ME)
                        actionTarget = null
                    }
                )
            }
            if (targetCanDeleteAll) {
                ActionSheetRow(
                    label = "DELETE FOR EVERYONE",
                    color = colors.statusError,
                    onClick = {
                        onMessageAction?.invoke(target, MessageAction.DELETE_FOR_EVERYONE)
                        actionTarget = null
                    }
                )
            }
            if (target.deliveryStatus == DeliveryStatus.FAILED) {
                ActionSheetRow(
                    label = "RETRY",
                    color = colors.ink,
                    onClick = {
                        onRetryMessage?.invoke(target.id)
                        actionTarget = null
                    }
                )
            }
        }
    }
}

/**
 * "NEW" boundary divider — hairline in the attention color with a centered,
 * uppercase label. Rendered once, at the frozen index computed when the
 * channel was opened (see [ConversationScreen]'s `newDividerIndex`).
 */
@Composable
private fun NewMessagesDivider(chatBgColor: Color) {
    val colors = LocalSmithColors.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.attention)
        )
        Text(
            text = "NEW",
            style = TextStyle(
                fontFamily = ConsoleTheme.jetBrainsMono,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                color = colors.attention,
                letterSpacing = 1.sp
            ),
            modifier = Modifier
                .background(chatBgColor)
                .padding(horizontal = 8.dp)
        )
    }
}

/**
 * Single full-width row in the long-press action sheet — matches the
 * SmithSheet row idiom used by InvoicePreviewBottomSheet's action rows.
 */
@Composable
private fun ActionSheetRow(label: String, color: Color, onClick: () -> Unit) {
    Text(
        text = label,
        style = TextStyle(
            fontFamily = ConsoleTheme.inter,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            color = color
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    )
}


@Composable
private fun TypingIndicator(typers: List<String>) {
    if (typers.isEmpty()) return
    val colors = LocalSmithColors.current
    val text = when {
        typers.size == 1 -> "◀ ${typers[0]} is typing..."
        typers.size == 2 -> "◀ ${typers[0]}, ${typers[1]} are typing..."
        else -> "◀ ${typers[0]} and ${typers.size - 1} others are typing..."
    }
    Text(
        text = text,
        style = SmithType.caption.copy(
            color = colors.inkMuted
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

@Composable
private fun DateSeparator(date: String) {
    val colors = LocalSmithColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f).height(0.5.dp).background(colors.line))
        Text(
            text = "── $date ──",
            style = SmithType.caption.copy(color = colors.inkMuted),
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Box(modifier = Modifier.weight(1f).height(0.5.dp).background(colors.line))
    }
}

// Left rail reserved for the first-of-group avatar; grouped rows start-pad
// by this width so bubbles line up under the header row above them.
private val MessageRailAvatarSize = 28.dp
private val MessageRailGap = 8.dp
private val MessageRailWidth = MessageRailAvatarSize + MessageRailGap

/**
 * Message block — always left-aligned (Signal/Slack-style single-column
 * feed). First-of-group rows show an avatar + sender + time + MeshChip;
 * grouped rows show the bubble only, start-padded to line up under it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBlock(
    message: Message,
    isSentByMe: Boolean,
    showHeader: Boolean = true,
    onRetryMessage: ((String) -> Unit)? = null,
    onLongPress: ((Message) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val colors = LocalSmithColors.current
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
        horizontalArrangement = Arrangement.Start
    ) {
        if (showHeader) {
            SmithAvatar(name = message.senderName, size = 28)
            Spacer(modifier = Modifier.width(MessageRailGap))
        } else {
            Spacer(modifier = Modifier.width(MessageRailWidth))
        }

        Column(horizontalAlignment = Alignment.Start) {
            if (showHeader) {
                // Header: sender + time + MeshChip + [DM] + [queued]
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = sender,
                        style = TextStyle(
                            fontFamily = ConsoleTheme.inter,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                            color = if (isSentByMe) colors.accent else colors.ink
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = time,
                        style = TextStyle(
                            fontFamily = ConsoleTheme.jetBrainsMono,
                            fontSize = 10.sp,
                            color = colors.inkMuted
                        )
                    )
                    if (message.isMeshOrigin) {
                        Spacer(modifier = Modifier.width(6.dp))
                        MeshChip()
                    }
                    if (message.isDirectMessage()) {
                        Text(
                            text = " [DM]",
                            style = SmithType.timestamp.copy(color = colors.accent)
                        )
                    }
                    if (message.isMediaQueued()) {
                        Text(
                            text = " [queued]",
                            style = SmithType.timestamp.copy(color = colors.attention)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(3.dp))
            } else {
                Spacer(modifier = Modifier.height(3.dp))
            }

            // Bubble — left-aligned, same shape for sent and received.
            // combinedClickable lives here (on the bubble), not on the
            // outer swipe container Box, so it never shares a pointer-input
            // node with the horizontal `draggable` on that container — see
            // Task 9 report for the gesture-coexistence reasoning.
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(Tokens2.RadiusBubble))
                    .background(colors.bgSunken)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = { onLongPress?.invoke(message) }
                    )
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Column(horizontalAlignment = Alignment.Start) {
                    // Interactive media player (voice/image/file)
                    if (message.hasMedia()) {
                        MediaIndicator(
                            type = message.mediaType,
                            media = message.media
                        )
                    }
                    // Text content
                    MessageTextContent(message = message)
                }
            }

            // Status microcopy — own messages only, every row (not gated by
            // showHeader) so PENDING/FAILED is visible even inside a group.
            if (isSentByMe) {
                MessageStatusLine(message = message, onRetryMessage = onRetryMessage)
            }
        }
    }
}

/**
 * Own-message delivery status, in mono uppercase words. SEEN (from Task 7's
 * read-receipt set, self already excluded at write time) takes precedence
 * over SENT/DELIVERED/READ. FAILED is tap-to-retry.
 */
@Composable
private fun MessageStatusLine(
    message: Message,
    onRetryMessage: ((String) -> Unit)?,
    modifier: Modifier = Modifier
) {
    val colors = LocalSmithColors.current
    val readByMessage by MessageRepository.readByMessage.collectAsState()
    val isSeen = readByMessage[message.id]?.isNotEmpty() == true

    // Precedence: failed > pending > seen > sent. A FAILED message keeps its
    // retry affordance even if a receipt arrived via the other transport.
    val (statusText, statusColor) = when {
        message.deliveryStatus == DeliveryStatus.FAILED -> "FAILED · TAP TO RETRY" to colors.attention
        message.deliveryStatus == DeliveryStatus.PENDING -> "PENDING" to colors.inkMuted
        isSeen -> "SEEN" to colors.statusOnline
        else -> "SENT" to colors.inkMuted
    }

    val isFailed = message.deliveryStatus == DeliveryStatus.FAILED
    Text(
        text = statusText,
        style = TextStyle(
            fontFamily = ConsoleTheme.jetBrainsMono,
            fontSize = 9.sp,
            color = statusColor
        ),
        modifier = modifier
            .padding(top = 2.dp)
            .then(
                if (isFailed) Modifier.clickable { onRetryMessage?.invoke(message.id) }
                else Modifier
            )
    )
}

/**
 * MeshChip — small pill badge marking a message as mesh-origin (replaces the
 * old [sub]/[online] text pair).
 */
@Composable
private fun MeshChip(modifier: Modifier = Modifier) {
    val colors = LocalSmithColors.current
    Text(
        text = "MESH",
        style = TextStyle(
            fontFamily = ConsoleTheme.jetBrainsMono,
            fontSize = 9.sp,
            color = colors.accent
        ),
        modifier = modifier
            .border(1.dp, colors.accent, RoundedCornerShape(Tokens2.RadiusPill))
            .padding(horizontal = 6.dp, vertical = 1.dp)
    )
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
                            val colors = LocalSmithColors.current
                            Text(
                                text = message.content,
                                style = SmithType.bodySmall.copy(color = colors.ink),
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
    val colors = LocalSmithColors.current
    val color = when {
        isRecording -> colors.attention
        enabled -> colors.ink
        else -> colors.inkMuted
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
                    .background(colors.attention, shape = androidx.compose.foundation.shape.CircleShape)
            )
        }
    }
}

private fun shouldShowHeader(current: Message, previous: Message?): Boolean {
    if (previous == null) return true
    if (current.senderId != previous.senderId) return true
    if (isDifferentDay(current, previous)) return true // grouping also breaks on day change
    if (current.timestamp - previous.timestamp > 420_000) return true // 7 min gap
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
    val colors = LocalSmithColors.current
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
            .background(colors.bgPanel)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Pulsing REC indicator
        val pulseAlpha = if ((duration / 500) % 2 == 0L) 1f else 0.5f
        Text(
            text = "[●]",
            style = SmithType.body.copy(
                color = colors.attention.copy(alpha = pulseAlpha)
            )
        )

        Spacer(modifier = Modifier.width(4.dp))

        Text(
            text = "REC",
            style = SmithType.captionBold.copy(color = colors.attention)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Audio level waveform: [|||||] - reacts to sound
        AudioLevelBars(level = audioLevel)

        Spacer(modifier = Modifier.width(8.dp))

        // Duration
        Text(
            text = timeText,
            style = SmithType.body.copy(color = colors.ink)
        )

        Spacer(modifier = Modifier.weight(1f))

        // Cancel button
        Text(
            text = "[✕]",
            style = SmithType.body.copy(color = colors.inkMuted),
            modifier = Modifier
                .clickable(onClick = onCancel)
                .padding(4.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Stop & Send button - [■] matches the [▶] play style
        Text(
            text = "[■]",
            style = SmithType.bodyBold.copy(color = colors.accent),
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
    val colors = LocalSmithColors.current
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
        style = SmithType.body.copy(
            color = when {
                activeBars >= 10 -> colors.attention  // Loud = orange/yellow
                activeBars >= 5 -> colors.accent      // Medium = accent color
                else -> colors.inkMuted               // Quiet = muted
            }
        ),
        modifier = modifier
    )
}

@Composable
private fun ConnectionStatusBar(mode: ConnectionMode, isEphemeral: Boolean = false) {
    val colors = LocalSmithColors.current
    val (color, label, baseDetail) = when (mode) {
        ConnectionMode.ONLINE -> Triple(colors.accent, "[ONLINE ●]", "ws://connected")
        ConnectionMode.MESH -> Triple(colors.statusOnline, "[MESH ●]", "peers nearby")
        ConnectionMode.OFFLINE -> Triple(colors.statusError, "[OFFLINE ●]", "queued")
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
            style = SmithType.caption.copy(color = color, fontWeight = FontWeight.SemiBold)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = detail,
            style = SmithType.timestamp.copy(color = colors.inkMuted)
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

@Composable
private fun SmithAIStatusBanner() {
    val agentState by com.guildofsmiths.trademesh.ai.AgentInitializer.agentState.collectAsState()
    val initProgress by com.guildofsmiths.trademesh.ai.AgentInitializer.initializationProgress.collectAsState()
    val modelState by com.guildofsmiths.trademesh.ai.LlamaInference.modelState.collectAsState()
    val gateState by com.guildofsmiths.trademesh.ai.BatteryGate.gateState.collectAsState()
    val backend = com.guildofsmiths.trademesh.ai.SmithAIBackendRouter.pick()

    val colors = LocalSmithColors.current
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
            .background(colors.bgPanel)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = SmithType.caption.copy(color = colors.accent)
        )
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(0.5.dp)
            .background(colors.line)
    )
}

@Composable
private fun ToolCallApprovalCard(
    pending: com.guildofsmiths.trademesh.ai.SmithAIToolExecutor.PendingToolCall,
    onApprove: () -> Unit,
    onDeny: () -> Unit
) {
    val colors = LocalSmithColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.bgPanel, RoundedCornerShape(Tokens2.RadiusCard))
            .border(0.5.dp, colors.accent.copy(alpha = 0.4f), RoundedCornerShape(Tokens2.RadiusCard))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = "[ACTION] ${pending.toolName}",
            style = SmithType.captionBold.copy(color = colors.accent)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = pending.argsSummary,
            style = SmithType.bodySmall.copy(color = colors.ink)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "[APPROVE]",
                style = SmithType.action.copy(color = colors.statusOnline),
                modifier = Modifier
                    .clickable(onClick = onApprove)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "[DENY]",
                style = SmithType.action.copy(color = colors.statusError),
                modifier = Modifier
                    .clickable(onClick = onDeny)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}
