# Messenger Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Transform the messenger from a developer log into a proper chat app — flat chat list, connection state indicators, message grouping, read receipts, and typing indicators — while preserving the bracket aesthetic.

**Architecture:** 5 changes layered on existing messenger infrastructure. New `ChatListScreen` replaces the beacon/channel hierarchy as entry point. `ConversationScreen` gets connection state bar, background tint, message grouping, date separators, font split, read receipts, and typing indicator. `ChatManager` gains new WebSocket event types. Backend relays 2 new event types.

**Tech Stack:** Kotlin, Jetpack Compose, OkHttp WebSocket, Node.js/Express backend

**Working directory:** `/Users/fegensprenelon/smith-net`

---

### Task 1: Add DeliveryStatus to Message Model

**Files:**
- Modify: `android/app/src/main/java/com/guildofsmiths/trademesh/data/Message.kt`

- [ ] **Step 1: Add DeliveryStatus enum**

Add after the `MediaType` enum (after line 19):

```kotlin
enum class DeliveryStatus {
    PENDING,
    SENT,
    DELIVERED,
    READ
}
```

- [ ] **Step 2: Add deliveryStatus field to Message data class**

Add to the Message data class after the `syncedToCloud` field (after line 67):

```kotlin
    val deliveryStatus: DeliveryStatus = DeliveryStatus.SENT,
```

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/guildofsmiths/trademesh/data/Message.kt
git commit -m "feat: add DeliveryStatus enum and field to Message model"
```

---

### Task 2: Add WebSocket Events to Backend

**Files:**
- Modify: `backend/src/messageBus.ts`

- [ ] **Step 1: Read the WebSocket handler in the backend**

Read `backend/src/server.ts` to find where WebSocket message types are handled (the `switch` or `if` block that processes incoming WS messages). Also read `backend/src/messageBus.ts` fully.

- [ ] **Step 2: Add message_read relay to WebSocket handler**

In the WebSocket message handler (in `server.ts`), add handling for `message_read` type. When received, broadcast to all other clients in the same channel:

```typescript
case 'message_read': {
    const { messageId, channelId, readBy, readAt } = data;
    // Broadcast to all clients subscribed to this channel
    wss.clients.forEach(client => {
        if (client !== ws && client.readyState === WebSocket.OPEN) {
            client.send(JSON.stringify({
                type: 'message_read',
                messageId,
                channelId,
                readBy,
                readAt: readAt || Date.now()
            }));
        }
    });
    break;
}
```

- [ ] **Step 3: Add typing relay to WebSocket handler**

Add handling for `typing_start` and `typing_stop`:

```typescript
case 'typing_start':
case 'typing_stop': {
    const { channelId, userId, userName } = data;
    // Relay to all other clients — no persistence
    wss.clients.forEach(client => {
        if (client !== ws && client.readyState === WebSocket.OPEN) {
            client.send(JSON.stringify({
                type: data.type,
                channelId,
                userId,
                userName
            }));
        }
    });
    break;
}
```

- [ ] **Step 4: Commit**

```bash
git add backend/src/server.ts
git commit -m "feat: add message_read and typing_start/stop WebSocket relay events"
```

---

### Task 3: Add Connection State + Typing Events to ChatManager

**Files:**
- Modify: `android/app/src/main/java/com/guildofsmiths/trademesh/service/ChatManager.kt`

- [ ] **Step 1: Read ChatManager.kt fully**

Read the entire file to understand the WebSocket listener, message handling, and connection state tracking.

- [ ] **Step 2: Add observable connection state**

Add a StateFlow for connection state that the UI can observe:

```kotlin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ConnectionMode { ONLINE, MESH, OFFLINE }

// Inside the ChatManager object, add:
private val _connectionMode = MutableStateFlow(ConnectionMode.OFFLINE)
val connectionMode: StateFlow<ConnectionMode> = _connectionMode.asStateFlow()
```

Update the `isConnected`/`isAuthenticated` setters to also update `_connectionMode`:
- When WebSocket authenticates successfully → `_connectionMode.value = ConnectionMode.ONLINE`
- When WebSocket disconnects → `_connectionMode.value = ConnectionMode.OFFLINE`

- [ ] **Step 3: Add typing event methods**

Add methods to send typing events:

```kotlin
fun sendTypingStart(channelId: String) {
    val userId = UserPreferences.getUserId() ?: return
    val userName = UserPreferences.getUserName() ?: return
    webSocket?.send(JSONObject().apply {
        put("type", "typing_start")
        put("channelId", channelId)
        put("userId", userId)
        put("userName", userName)
    }.toString())
}

fun sendTypingStop(channelId: String) {
    val userId = UserPreferences.getUserId() ?: return
    webSocket?.send(JSONObject().apply {
        put("type", "typing_stop")
        put("channelId", channelId)
        put("userId", userId)
    }.toString())
}
```

- [ ] **Step 4: Add read receipt methods**

```kotlin
fun sendReadReceipt(messageId: String, channelId: String) {
    val userId = UserPreferences.getUserId() ?: return
    webSocket?.send(JSONObject().apply {
        put("type", "message_read")
        put("messageId", messageId)
        put("channelId", channelId)
        put("readBy", userId)
        put("readAt", System.currentTimeMillis())
    }.toString())
}
```

- [ ] **Step 5: Handle incoming typing and read events**

In the WebSocket `onMessage` handler, add cases for `typing_start`, `typing_stop`, and `message_read`. Create listener interfaces:

```kotlin
interface OnTypingListener {
    fun onTypingStarted(channelId: String, userId: String, userName: String)
    fun onTypingStopped(channelId: String, userId: String)
}

interface OnReadReceiptListener {
    fun onMessageRead(messageId: String, readBy: String, readAt: Long)
}

private var typingListener: OnTypingListener? = null
private var readReceiptListener: OnReadReceiptListener? = null

fun setTypingListener(listener: OnTypingListener?) { typingListener = listener }
fun setReadReceiptListener(listener: OnReadReceiptListener?) { readReceiptListener = listener }
```

In the message handler `when` block, add:
```kotlin
"typing_start" -> typingListener?.onTypingStarted(
    json.getString("channelId"), json.getString("userId"), json.getString("userName"))
"typing_stop" -> typingListener?.onTypingStopped(
    json.getString("channelId"), json.getString("userId"))
"message_read" -> readReceiptListener?.onMessageRead(
    json.getString("messageId"), json.getString("readBy"), json.getLong("readAt"))
```

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/com/guildofsmiths/trademesh/service/ChatManager.kt
git commit -m "feat: add connection state, typing events, and read receipts to ChatManager"
```

---

### Task 4: Create ChatListScreen

**Files:**
- Create: `android/app/src/main/java/com/guildofsmiths/trademesh/ui/ChatListScreen.kt`

- [ ] **Step 1: Read existing BeaconListScreen and ChannelListScreen**

Read both files to understand data sources, how channels/beacons are loaded, and the composable patterns used.

- [ ] **Step 2: Create ChatListScreen composable**

Create a new file with a flat conversation list. The screen should:
- Query all channels across all beacons (use `MessageRepository` or equivalent)
- Sort by most recent message timestamp (descending)
- Each row shows: initials avatar (colored circle with 2-letter initials), conversation name, last message preview (truncated to 1 line), relative timestamp, unread count badge
- DMs: show person name (e.g., "Carlos Vega")
- Groups: show channel name with `#` prefix (e.g., "#general")
- Avatar dot shows connection status: sage for mesh, gold for online, grey for offline (read from `ChatManager.connectionMode`)
- Header: `← MESSAGES` with ConsoleTheme styling
- Bottom: `[+ NEW]` button (same pattern as BeaconListScreen)
- Use ConsoleTheme colors/fonts throughout (Altara tokens)
- Typing indicator: if a user is typing in a channel, replace last message preview with `typing...` in gold

Key composables to create:
- `ChatListScreen(channels, onChannelClick, onNewClick, onBackClick)`
- `ChatRow(channel, lastMessage, unreadCount, isTyping, connectionMode, onClick)`

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/guildofsmiths/trademesh/ui/ChatListScreen.kt
git commit -m "feat: create flat ChatListScreen replacing beacon/channel hierarchy"
```

---

### Task 5: Wire ChatListScreen into Navigation

**Files:**
- Modify: `android/app/src/main/java/com/guildofsmiths/trademesh/ui/Navigation.kt`
- Modify: `android/app/src/main/java/com/guildofsmiths/trademesh/MainActivity.kt`

- [ ] **Step 1: Add CHAT_LIST route to NavRoutes**

In `Navigation.kt`, add:
```kotlin
const val CHAT_LIST = "chat_list"
```

Add a helper:
```kotlin
fun conversation(channelId: String) = "conversation/default/$channelId"
```

- [ ] **Step 2: Add ChatListScreen composable route to NavHost**

In `MainActivity.kt`, find the `NavHost` composable. Add a new route for `CHAT_LIST`:

```kotlin
composable(NavRoutes.CHAT_LIST) {
    WithToolbar(toolbarExpanded, { toolbarExpanded = !toolbarExpanded }, navController) {
        ChatListScreen(
            onChannelClick = { beaconId, channelId ->
                navController.navigate(NavRoutes.conversation(beaconId, channelId))
            },
            onNewClick = { /* navigate to create channel or peer picker */ },
            onBackClick = { navController.popBackStack() }
        )
    }
}
```

- [ ] **Step 3: Change [Msg] navigation target**

Find where `[Msg]` button navigates (in `DashboardScreen.kt` or wherever it's wired). Change from `NavRoutes.BEACON_LIST` to `NavRoutes.CHAT_LIST`.

- [ ] **Step 4: Update ConversationScreen back navigation**

In `ConversationScreen.kt`, find the back button/arrow click handler. Change it to navigate to `NavRoutes.CHAT_LIST` instead of popping to the channel list.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/guildofsmiths/trademesh/ui/Navigation.kt
git add android/app/src/main/java/com/guildofsmiths/trademesh/MainActivity.kt
git add android/app/src/main/java/com/guildofsmiths/trademesh/ui/dashboard/DashboardScreen.kt
git add android/app/src/main/java/com/guildofsmiths/trademesh/ui/ConversationScreen.kt
git commit -m "feat: wire ChatListScreen as primary messaging entry point"
```

---

### Task 6: Connection State Bar + Background Tint

**Files:**
- Modify: `android/app/src/main/java/com/guildofsmiths/trademesh/ui/ConversationScreen.kt`

- [ ] **Step 1: Read ConversationScreen.kt fully**

Read the entire file to understand the composable structure, where the header is, where the message list starts, and how the LazyColumn is set up.

- [ ] **Step 2: Add ConnectionStatusBar composable**

Create a composable that shows the current connection mode:

```kotlin
@Composable
private fun ConnectionStatusBar(mode: ConnectionMode) {
    val (color, label, detail) = when (mode) {
        ConnectionMode.ONLINE -> Triple(Color(0xFF9A6F2E), "[ONLINE ●]", "ws://connected")
        ConnectionMode.MESH -> Triple(Color(0xFF5A8C76), "[MESH ●]", "${BoundaryEngine.getPeerCount()} peers")
        ConnectionMode.OFFLINE -> Triple(Color(0xFF8C3A3A), "[OFFLINE ●]", "queued")
    }
    
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
```

- [ ] **Step 3: Add background tint based on connection mode**

Find the LazyColumn that holds the message list. Wrap it or set its background color based on `connectionMode`:

```kotlin
val mode by ChatManager.connectionMode.collectAsState()
val bgColor = when (mode) {
    ConnectionMode.ONLINE -> Color(0xFFF4F2EE)  // default warm
    ConnectionMode.MESH -> Color(0xFFF0F4F1)    // sage tint
    ConnectionMode.OFFLINE -> Color(0xFFF4F0EE) // brick tint
}
```

Apply `bgColor` as the background modifier on the message list container.

- [ ] **Step 4: Place the status bar**

Insert `ConnectionStatusBar(mode)` between the conversation header and the message LazyColumn.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/guildofsmiths/trademesh/ui/ConversationScreen.kt
git commit -m "feat: add connection status bar and background tint to conversation"
```

---

### Task 7: Message Layout Cleanup — Grouping, Date Separators, Font Split

**Files:**
- Modify: `android/app/src/main/java/com/guildofsmiths/trademesh/ui/ConversationScreen.kt`

- [ ] **Step 1: Read the MessageBlock composable**

Read the `MessageBlock` composable (around line 664+) to understand current rendering.

- [ ] **Step 2: Add DateSeparator composable**

```kotlin
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
            text = " $date ",
            style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted),
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        Box(modifier = Modifier.weight(1f).height(0.5.dp).background(ConsoleTheme.separatorFaint))
    }
}
```

- [ ] **Step 3: Add grouping logic to the message list**

In the LazyColumn `items` block, before rendering each message, determine:
1. Is this a different day than the previous message? → Insert `DateSeparator`
2. Is this the same sender as previous AND within 2 minutes? → Render as continuation (no header, 3dp gap)
3. Otherwise → Render with full header (6dp gap)

Create a helper:
```kotlin
private fun shouldShowHeader(current: Message, previous: Message?): Boolean {
    if (previous == null) return true
    if (current.senderId != previous.senderId) return true
    if (current.timestamp - previous.timestamp > 120_000) return true // 2 min
    return false
}

private fun isDifferentDay(current: Message, previous: Message?): Boolean {
    if (previous == null) return true
    val cal1 = java.util.Calendar.getInstance().apply { timeInMillis = current.timestamp }
    val cal2 = java.util.Calendar.getInstance().apply { timeInMillis = previous.timestamp }
    return cal1.get(java.util.Calendar.DAY_OF_YEAR) != cal2.get(java.util.Calendar.DAY_OF_YEAR)
        || cal1.get(java.util.Calendar.YEAR) != cal2.get(java.util.Calendar.YEAR)
}
```

- [ ] **Step 4: Update MessageBlock for font split and continuation mode**

Modify `MessageBlock` to accept a `showHeader: Boolean` parameter. When `showHeader` is false, skip the sender name/timestamp row and just render the content with 3dp top padding.

Change message body text from `ConsoleTheme.body` (monospace) to `ConsoleTheme.bodySmall` which now uses IBM Plex Sans (from the Altara theme update). Keep the header (sender, time, arrows) in `ConsoleTheme.caption`/`ConsoleTheme.timestamp` (IBM Plex Mono).

- [ ] **Step 5: Remove debug agent logging**

Remove the `#region agent log` block (lines ~718-747) that sends HTTP requests to `127.0.0.1:7242`. This is debug telemetry that shouldn't be in production.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/com/guildofsmiths/trademesh/ui/ConversationScreen.kt
git commit -m "feat: add message grouping, date separators, and font split to conversation"
```

---

### Task 8: Read Receipt Display

**Files:**
- Modify: `android/app/src/main/java/com/guildofsmiths/trademesh/ui/ConversationScreen.kt`

- [ ] **Step 1: Add read receipt indicator to MessageBlock**

In the `MessageBlock` composable, for sent messages (`isSentByMe`), add the delivery status indicator after the timestamp:

```kotlin
if (isSentByMe) {
    val statusText = when (message.deliveryStatus) {
        DeliveryStatus.PENDING -> "[...]"
        DeliveryStatus.SENT -> "[✓]"
        DeliveryStatus.DELIVERED -> "[✓✓]"
        DeliveryStatus.READ -> "[✓✓]"
    }
    val statusColor = if (message.deliveryStatus == DeliveryStatus.READ)
        Color(0xFF5A8C76) // sage
    else
        ConsoleTheme.textMuted
    
    Text(
        text = " $statusText",
        style = ConsoleTheme.timestamp.copy(color = statusColor)
    )
}
```

This goes in the header Row, after the timestamp Text, before the `▶` arrow.

- [ ] **Step 2: Wire read receipt listener**

In the ConversationScreen composable, set up the read receipt listener to update message delivery status when receipts arrive:

```kotlin
LaunchedEffect(Unit) {
    ChatManager.setReadReceiptListener(object : ChatManager.OnReadReceiptListener {
        override fun onMessageRead(messageId: String, readBy: String, readAt: Long) {
            // Update the message in the local list
            // This will require making the messages list mutable or using a ViewModel
        }
    })
}
```

- [ ] **Step 3: Send read receipts when messages are viewed**

When a received message scrolls into view and the conversation is active, send a read receipt:

```kotlin
// In the LazyColumn items, for received messages:
LaunchedEffect(message.id) {
    if (!isSentByMe && message.deliveryStatus != DeliveryStatus.READ) {
        delay(1000) // Wait 1 second to confirm the user actually read it
        ChatManager.sendReadReceipt(message.id, message.channelId)
    }
}
```

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/com/guildofsmiths/trademesh/ui/ConversationScreen.kt
git commit -m "feat: add read receipt display and sending to conversation"
```

---

### Task 9: Typing Indicators

**Files:**
- Modify: `android/app/src/main/java/com/guildofsmiths/trademesh/ui/ConversationScreen.kt`
- Modify: `android/app/src/main/java/com/guildofsmiths/trademesh/ui/ChatListScreen.kt`

- [ ] **Step 1: Add typing indicator composable to ConversationScreen**

Create a composable that shows above the input bar:

```kotlin
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
        style = ConsoleTheme.caption.copy(color = ConsoleTheme.textMuted),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    )
}
```

- [ ] **Step 2: Track typing state in ConversationScreen**

Add state to track who is typing:

```kotlin
var typingUsers by remember { mutableStateOf<Map<String, String>>(emptyMap()) } // userId -> userName

LaunchedEffect(channelId) {
    ChatManager.setTypingListener(object : ChatManager.OnTypingListener {
        override fun onTypingStarted(chId: String, userId: String, userName: String) {
            if (chId == channelId) typingUsers = typingUsers + (userId to userName)
        }
        override fun onTypingStopped(chId: String, userId: String) {
            if (chId == channelId) typingUsers = typingUsers - userId
        }
    })
}

// Auto-clear after 3 seconds
LaunchedEffect(typingUsers) {
    if (typingUsers.isNotEmpty()) {
        delay(3000)
        typingUsers = emptyMap()
    }
}
```

Place `TypingIndicator(typingUsers.values.toList())` above the input bar composable.

- [ ] **Step 3: Send typing events on text input**

In the input field's `onValueChange`, debounce and send typing events:

```kotlin
var lastTypingSent by remember { mutableStateOf(0L) }

// In the text field onChange:
val now = System.currentTimeMillis()
if (now - lastTypingSent > 1000) {
    ChatManager.sendTypingStart(channelId)
    lastTypingSent = now
}
```

When the user sends the message (or clears the field), call `ChatManager.sendTypingStop(channelId)`.

- [ ] **Step 4: Add typing indicator to ChatListScreen**

In the `ChatRow` composable created in Task 4, check if anyone is typing in that channel. If so, replace the last message preview with `typing...` in gold:

```kotlin
if (isTyping) {
    Text(
        text = "typing...",
        style = ConsoleTheme.caption.copy(color = ConsoleTheme.accent)
    )
} else {
    Text(
        text = lastMessagePreview,
        style = ConsoleTheme.caption,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}
```

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/guildofsmiths/trademesh/ui/ConversationScreen.kt
git add android/app/src/main/java/com/guildofsmiths/trademesh/ui/ChatListScreen.kt
git commit -m "feat: add typing indicators to conversation and chat list"
```

---

### Task 10: Build and Verify

- [ ] **Step 1: Build the app**

```bash
cd android && JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ./gradlew assembleDebug 2>&1 | tail -5
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: Install and launch on emulator**

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home ./gradlew installDebug
adb shell monkey -p com.guildofsmiths.trademesh -c android.intent.category.LAUNCHER 1
```

- [ ] **Step 3: Verify messenger flow**

1. Tap `[Msg]` → should see flat ChatListScreen (not BeaconListScreen)
2. Chat list shows conversations sorted by recent, with avatars + previews
3. Tap a conversation → opens directly (1 tap)
4. Connection status bar visible: `[ONLINE ●]` with gold-tinted bar
5. Messages show body in Plex Sans, metadata in Plex Mono
6. Consecutive messages from same sender within 2 min are grouped
7. Date separator appears between different days
8. Sent messages show `[✓]` after timestamp
9. Back button returns to ChatListScreen

- [ ] **Step 4: Commit any fixes**

```bash
git add -A
git commit -m "fix: address issues found during messenger cleanup verification"
```
