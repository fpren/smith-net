# Phase 1: Messaging Unification Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Unify BLE mesh and IP chat into a single Message Bus with transport abstraction, vector clock ordering, UUID-based deduplication, and reliable reconciliation on transport switch.

**Architecture:** A Message Bus sits between consumers (UI, gateway, Synthesizer) and transports (BLE mesh, WebSocket/HTTP, Supabase). Every message gets a UUID at creation and a vector clock for causal ordering. Messages are stored locally first (Room on Android, in-memory on backend), then synced to Supabase. A Reconciliation Engine handles dedup, ordering, and merge on reconnect.

**Tech Stack:** TypeScript (backend), Kotlin (Android/Jetpack Compose), Supabase (persistence), Room (local Android DB), WebSocket (realtime transport)

**Spec:** `docs/superpowers/specs/2026-03-20-core-flow-redesign-design.md` (Phase 1 section)

**Note:** This is Phase 1 of 2. Phase 2 (Core Flow Redesign: Intent/Synthesizer/Ledger) has a separate plan and depends on this being complete.

---

## File Structure

### Backend — New Files
- `backend/src/messageBus.ts` — Unified message API: send, subscribe, getHistory. Single entry point for all message operations.
- `backend/src/reconciliationEngine.ts` — Dedup by UUID, vector clock merge, conflict resolution on reconnect.
- `backend/src/vectorClock.ts` — Vector clock implementation: increment, merge, compare, serialize/deserialize.

### Backend — Modified Files
- `backend/src/types.ts` — Add VectorClock, UnifiedMessage, TransportType types. Keep existing types for backward compat during migration.
- `backend/src/wsHandler.ts` — Route all message operations through MessageBus instead of direct messageStore access.
- `backend/src/messageStore.ts` — Replace in-memory store with Supabase-backed unified store reading from `message_bus_messages` table.
- `backend/src/gatewayManager.ts` — Use MessageBus for relay message injection/forwarding instead of direct broadcast.
- `backend/src/server.ts` — Add reconciliation API endpoints.

### Android — New Files
- `android/app/src/main/java/com/guildofsmiths/trademesh/data/VectorClock.kt` — Vector clock: increment, merge, compare, serialize to JSON.
- `android/app/src/main/java/com/guildofsmiths/trademesh/data/UnifiedMessage.kt` — New message model with UUID, vector clock, transport metadata.
- `android/app/src/main/java/com/guildofsmiths/trademesh/data/MessageBusRepository.kt` — Unified message API replacing MessageRepository. Local-first (Room) + Supabase sync.
- `android/app/src/main/java/com/guildofsmiths/trademesh/service/ReconciliationEngine.kt` — Android-side reconciliation on transport change.
- `android/app/src/main/java/com/guildofsmiths/trademesh/db/UnifiedMessageDao.kt` — Room DAO for unified messages.
- `android/app/src/main/java/com/guildofsmiths/trademesh/db/AppDatabase.kt` — Room database definition (or modify if exists).

### Android — Modified Files
- `android/app/src/main/java/com/guildofsmiths/trademesh/engine/BoundaryEngine.kt` — Route through MessageBus instead of direct MeshService/ChatManager/Supabase calls. Simplify to transport selection only.
- `android/app/src/main/java/com/guildofsmiths/trademesh/service/MeshService.kt` — Emit raw BLE payloads to MessageBus instead of directly creating Message objects.
- `android/app/src/main/java/com/guildofsmiths/trademesh/service/ChatManager.kt` — Emit received WS messages to MessageBus instead of directly to repository.
- `android/app/src/main/java/com/guildofsmiths/trademesh/service/GatewayClient.kt` — Use MessageBus for forwarding/receiving instead of direct BoundaryEngine calls.

### Database
- New Supabase migration: `supabase/migrations/002_message_bus.sql` — Creates `message_bus_messages` table with UUID, vector_clock (jsonb), channel_id, sender_id, content, transport_type, timestamps.

---

## Task 1: Vector Clock Implementation (Backend)

**Files:**
- Create: `backend/src/vectorClock.ts`
- Modify: `backend/src/types.ts`

- [ ] **Step 1: Add vector clock types to types.ts**

Open `backend/src/types.ts` and add at the end of the file:

```typescript
// === Message Bus Types (Phase 1) ===

export type TransportType = 'ble' | 'ip' | 'supabase' | 'gateway';

export interface VectorClockState {
  [deviceId: string]: number;
}

export interface UnifiedMessage {
  id: string;                    // UUID, generated at creation
  channelId: string;             // Channel UUID
  senderId: string;              // User UUID
  senderName: string;
  content: string;
  timestamp: number;             // Unix ms, for display only (not ordering)
  vectorClock: VectorClockState; // Causal ordering
  transportType: TransportType;  // Which transport carried this message
  mediaType?: 'TEXT' | 'IMAGE' | 'VOICE' | 'VIDEO' | 'FILE';
  mediaUrl?: string;
  aiGenerated?: boolean;
  aiModel?: string;
}
```

- [ ] **Step 2: Create vectorClock.ts with core operations**

Create `backend/src/vectorClock.ts`:

```typescript
import { VectorClockState } from './types';

export function createClock(): VectorClockState {
  return {};
}

export function increment(clock: VectorClockState, deviceId: string): VectorClockState {
  return { ...clock, [deviceId]: (clock[deviceId] || 0) + 1 };
}

export function merge(a: VectorClockState, b: VectorClockState): VectorClockState {
  const result: VectorClockState = { ...a };
  for (const [deviceId, count] of Object.entries(b)) {
    result[deviceId] = Math.max(result[deviceId] || 0, count);
  }
  return result;
}

// Returns: -1 if a < b, 1 if a > b, 0 if concurrent
export function compare(a: VectorClockState, b: VectorClockState): -1 | 0 | 1 {
  const allKeys = new Set([...Object.keys(a), ...Object.keys(b)]);
  let aGreater = false;
  let bGreater = false;

  for (const key of allKeys) {
    const aVal = a[key] || 0;
    const bVal = b[key] || 0;
    if (aVal > bVal) aGreater = true;
    if (bVal > aVal) bGreater = true;
  }

  if (aGreater && !bGreater) return 1;
  if (bGreater && !aGreater) return -1;
  return 0; // concurrent
}

export function serialize(clock: VectorClockState): string {
  return JSON.stringify(clock);
}

export function deserialize(json: string): VectorClockState {
  return JSON.parse(json);
}
```

- [ ] **Step 3: Verify backend compiles**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx tsc --noEmit --skipLibCheck`
Expected: No errors related to vectorClock or new types

- [ ] **Step 4: Commit**

```bash
git add backend/src/vectorClock.ts backend/src/types.ts
git commit -m "feat: add vector clock implementation and unified message types"
```

---

## Task 2: Vector Clock Implementation (Android)

**Files:**
- Create: `android/app/src/main/java/com/guildofsmiths/trademesh/data/VectorClock.kt`
- Create: `android/app/src/main/java/com/guildofsmiths/trademesh/data/UnifiedMessage.kt`

- [ ] **Step 1: Create VectorClock.kt**

```kotlin
package com.guildofsmiths.trademesh.data

import org.json.JSONObject

data class VectorClock(
    val state: Map<String, Int> = emptyMap()
) {
    fun increment(deviceId: String): VectorClock {
        val newState = state.toMutableMap()
        newState[deviceId] = (newState[deviceId] ?: 0) + 1
        return VectorClock(newState)
    }

    fun merge(other: VectorClock): VectorClock {
        val merged = state.toMutableMap()
        for ((deviceId, count) in other.state) {
            merged[deviceId] = maxOf(merged[deviceId] ?: 0, count)
        }
        return VectorClock(merged)
    }

    // Returns: -1 if this < other, 1 if this > other, 0 if concurrent
    fun compareTo(other: VectorClock): Int {
        val allKeys = state.keys + other.state.keys
        var thisGreater = false
        var otherGreater = false

        for (key in allKeys) {
            val thisVal = state[key] ?: 0
            val otherVal = other.state[key] ?: 0
            if (thisVal > otherVal) thisGreater = true
            if (otherVal > thisVal) otherGreater = true
        }

        return when {
            thisGreater && !otherGreater -> 1
            otherGreater && !thisGreater -> -1
            else -> 0
        }
    }

    fun toJson(): String {
        val obj = JSONObject()
        for ((key, value) in state) {
            obj.put(key, value)
        }
        return obj.toString()
    }

    companion object {
        fun fromJson(json: String): VectorClock {
            if (json.isBlank()) return VectorClock()
            val obj = JSONObject(json)
            val map = mutableMapOf<String, Int>()
            for (key in obj.keys()) {
                map[key] = obj.getInt(key)
            }
            return VectorClock(map)
        }
    }
}
```

- [ ] **Step 2: Create UnifiedMessage.kt**

```kotlin
package com.guildofsmiths.trademesh.data

import java.util.UUID

enum class TransportType {
    BLE, IP, SUPABASE, GATEWAY
}

data class UnifiedMessage(
    val id: String = UUID.randomUUID().toString(),
    val channelId: String,
    val senderId: String,
    val senderName: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val vectorClock: VectorClock = VectorClock(),
    val transportType: TransportType,
    val mediaType: String = "TEXT",
    val mediaUrl: String? = null,
    val aiGenerated: Boolean = false,
    val aiModel: String? = null,
    val syncedToRemote: Boolean = false
) {
    /**
     * Convert to compact BLE mesh payload (existing 20-byte format).
     * Only TEXT messages under size limit can be sent via BLE.
     */
    fun toBlePayload(senderHash: ByteArray, channelHash: ByteArray): ByteArray? {
        if (mediaType != "TEXT") return null
        val contentBytes = content.toByteArray(Charsets.UTF_8)
        if (contentBytes.size > 10) return null // needs chunking

        val payload = ByteArray(20)
        System.arraycopy(senderHash, 0, payload, 0, 4)
        System.arraycopy(channelHash, 0, payload, 4, 2)
        val ts = (timestamp / 1000).toInt()
        payload[6] = (ts shr 24).toByte()
        payload[7] = (ts shr 16).toByte()
        payload[8] = (ts shr 8).toByte()
        payload[9] = ts.toByte()
        System.arraycopy(contentBytes, 0, payload, 10, contentBytes.size)
        return payload
    }
}
```

- [ ] **Step 3: Verify Android project compiles**

Run: `cd /Users/fegensprenelon/smith-net/android && ./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL (new files are standalone, no dependencies to break)

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/com/guildofsmiths/trademesh/data/VectorClock.kt \
        android/app/src/main/java/com/guildofsmiths/trademesh/data/UnifiedMessage.kt
git commit -m "feat: add Android vector clock and unified message model"
```

---

## Task 3: Supabase Migration for Unified Message Store

**Files:**
- Create: `supabase/migrations/002_message_bus.sql`

- [ ] **Step 1: Create migration file**

```sql
-- Message Bus: Unified message storage for BLE mesh + IP chat
-- All messages stored here regardless of transport path

CREATE TABLE IF NOT EXISTS message_bus_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    channel_id UUID NOT NULL REFERENCES channels(id) ON DELETE CASCADE,
    sender_id UUID NOT NULL,
    sender_name TEXT NOT NULL,
    content TEXT NOT NULL DEFAULT '',
    timestamp TIMESTAMPTZ NOT NULL DEFAULT now(),
    vector_clock JSONB NOT NULL DEFAULT '{}',
    transport_type TEXT NOT NULL CHECK (transport_type IN ('ble', 'ip', 'supabase', 'gateway')),
    media_type TEXT NOT NULL DEFAULT 'TEXT' CHECK (media_type IN ('TEXT', 'IMAGE', 'VOICE', 'VIDEO', 'FILE')),
    media_url TEXT,
    ai_generated BOOLEAN NOT NULL DEFAULT false,
    ai_model TEXT,
    synced_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Index for channel history queries (most common access pattern)
CREATE INDEX idx_mbm_channel_timestamp ON message_bus_messages(channel_id, timestamp DESC);

-- Index for reconciliation: find unsynced messages
CREATE INDEX idx_mbm_unsynced ON message_bus_messages(synced_at) WHERE synced_at IS NULL;

-- Index for sender queries
CREATE INDEX idx_mbm_sender ON message_bus_messages(sender_id);

-- RLS: users can read messages in channels they belong to
ALTER TABLE message_bus_messages ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Users can read channel messages"
    ON message_bus_messages FOR SELECT
    USING (
        channel_id IN (
            SELECT id FROM channels
            WHERE visibility = 'public'
            OR id IN (SELECT channel_id FROM channel_members WHERE user_id = auth.uid())
        )
    );

CREATE POLICY "Authenticated users can insert messages"
    ON message_bus_messages FOR INSERT
    WITH CHECK (auth.uid() IS NOT NULL);
```

- [ ] **Step 2: Commit**

```bash
git add supabase/migrations/002_message_bus.sql
git commit -m "feat: add Supabase migration for unified message_bus_messages table"
```

---

## Task 4: Message Bus (Backend)

**Files:**
- Create: `backend/src/messageBus.ts`
- Modify: `backend/src/messageStore.ts`

- [ ] **Step 1: Create messageBus.ts**

```typescript
import { v4 as uuidv4 } from 'uuid';
import { UnifiedMessage, VectorClockState, TransportType } from './types';
import { increment, merge } from './vectorClock';
import { supabase } from './supabase';

type MessageHandler = (message: UnifiedMessage) => void;

// In-memory state
const channelSubscribers: Map<string, Set<MessageHandler>> = new Map();
const recentMessageIds: Set<string> = new Set();
const MAX_RECENT_IDS = 10000;

// Server's device ID for vector clock
const SERVER_DEVICE_ID = 'server-' + uuidv4().slice(0, 8);
let serverClock: VectorClockState = {};

export function createMessage(
  channelId: string,
  senderId: string,
  senderName: string,
  content: string,
  transportType: TransportType,
  existingId?: string,
  existingClock?: VectorClockState
): UnifiedMessage {
  serverClock = increment(serverClock, SERVER_DEVICE_ID);
  const messageClock = existingClock
    ? merge(increment(existingClock, SERVER_DEVICE_ID), serverClock)
    : { ...serverClock };

  return {
    id: existingId || uuidv4(),
    channelId,
    senderId,
    senderName,
    content,
    timestamp: Date.now(),
    vectorClock: messageClock,
    transportType,
  };
}

export function isDuplicate(messageId: string): boolean {
  if (recentMessageIds.has(messageId)) return true;
  recentMessageIds.add(messageId);
  if (recentMessageIds.size > MAX_RECENT_IDS) {
    const first = recentMessageIds.values().next().value;
    if (first) recentMessageIds.delete(first);
  }
  return false;
}

export function subscribe(channelId: string, handler: MessageHandler): () => void {
  if (!channelSubscribers.has(channelId)) {
    channelSubscribers.set(channelId, new Set());
  }
  channelSubscribers.get(channelId)!.add(handler);
  return () => channelSubscribers.get(channelId)?.delete(handler);
}

export function publish(message: UnifiedMessage): void {
  if (isDuplicate(message.id)) return;

  // Notify all subscribers for this channel
  const handlers = channelSubscribers.get(message.channelId);
  if (handlers) {
    for (const handler of handlers) {
      handler(message);
    }
  }

  // Persist to Supabase asynchronously
  persistMessage(message).catch(err =>
    console.error('[MessageBus] Failed to persist message:', err)
  );
}

async function persistMessage(message: UnifiedMessage): Promise<void> {
  const { error } = await supabase.from('message_bus_messages').upsert({
    id: message.id,
    channel_id: message.channelId,
    sender_id: message.senderId,
    sender_name: message.senderName,
    content: message.content,
    timestamp: new Date(message.timestamp).toISOString(),
    vector_clock: message.vectorClock,
    transport_type: message.transportType,
    media_type: message.mediaType || 'TEXT',
    media_url: message.mediaUrl,
    ai_generated: message.aiGenerated || false,
    ai_model: message.aiModel,
    synced_at: new Date().toISOString(),
  }, { onConflict: 'id' });

  if (error) throw error;
}

export async function getHistory(
  channelId: string,
  limit: number = 100,
  before?: number
): Promise<UnifiedMessage[]> {
  let query = supabase
    .from('message_bus_messages')
    .select('*')
    .eq('channel_id', channelId)
    .order('timestamp', { ascending: false })
    .limit(limit);

  if (before) {
    query = query.lt('timestamp', new Date(before).toISOString());
  }

  const { data, error } = await query;
  if (error) throw error;

  return (data || []).map(row => ({
    id: row.id,
    channelId: row.channel_id,
    senderId: row.sender_id,
    senderName: row.sender_name,
    content: row.content,
    timestamp: new Date(row.timestamp).getTime(),
    vectorClock: row.vector_clock,
    transportType: row.transport_type,
    mediaType: row.media_type,
    mediaUrl: row.media_url,
    aiGenerated: row.ai_generated,
    aiModel: row.ai_model,
  })).reverse();
}
```

- [ ] **Step 2: Verify backend compiles**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx tsc --noEmit --skipLibCheck`
Expected: No errors

- [ ] **Step 3: Commit**

```bash
git add backend/src/messageBus.ts
git commit -m "feat: add Message Bus with pub/sub, dedup, vector clocks, and Supabase persistence"
```

---

## Task 5: Reconciliation Engine (Backend)

**Files:**
- Create: `backend/src/reconciliationEngine.ts`
- Modify: `backend/src/server.ts`

- [ ] **Step 1: Create reconciliationEngine.ts**

```typescript
import { UnifiedMessage, VectorClockState } from './types';
import { compare, merge } from './vectorClock';
import { supabase } from './supabase';

export interface ReconciliationRequest {
  channelId: string;
  localMessageIds: string[];
  localClock: VectorClockState;
}

export interface ReconciliationResponse {
  missingOnClient: UnifiedMessage[];  // Server has, client doesn't
  missingOnServer: string[];          // Client has, server doesn't (client should push these)
  mergedClock: VectorClockState;
}

export async function reconcile(req: ReconciliationRequest): Promise<ReconciliationResponse> {
  // Fetch all server messages for this channel
  const { data: serverMessages, error } = await supabase
    .from('message_bus_messages')
    .select('*')
    .eq('channel_id', req.channelId)
    .order('timestamp', { ascending: true });

  if (error) throw error;

  const serverIds = new Set((serverMessages || []).map(m => m.id));
  const clientIds = new Set(req.localMessageIds);

  // Messages server has that client doesn't
  const missingOnClient: UnifiedMessage[] = (serverMessages || [])
    .filter(m => !clientIds.has(m.id))
    .map(row => ({
      id: row.id,
      channelId: row.channel_id,
      senderId: row.sender_id,
      senderName: row.sender_name,
      content: row.content,
      timestamp: new Date(row.timestamp).getTime(),
      vectorClock: row.vector_clock,
      transportType: row.transport_type,
      mediaType: row.media_type,
      mediaUrl: row.media_url,
      aiGenerated: row.ai_generated,
      aiModel: row.ai_model,
    }));

  // Message IDs client has that server doesn't
  const missingOnServer = req.localMessageIds.filter(id => !serverIds.has(id));

  // Merge vector clocks
  let mergedClock = req.localClock;
  for (const msg of serverMessages || []) {
    mergedClock = merge(mergedClock, msg.vector_clock);
  }

  return { missingOnClient, missingOnServer, mergedClock };
}

// Accept messages pushed from client during reconciliation
export async function acceptClientMessages(messages: UnifiedMessage[]): Promise<void> {
  if (messages.length === 0) return;

  const rows = messages.map(m => ({
    id: m.id,
    channel_id: m.channelId,
    sender_id: m.senderId,
    sender_name: m.senderName,
    content: m.content,
    timestamp: new Date(m.timestamp).toISOString(),
    vector_clock: m.vectorClock,
    transport_type: m.transportType,
    media_type: m.mediaType || 'TEXT',
    media_url: m.mediaUrl,
    ai_generated: m.aiGenerated || false,
    ai_model: m.aiModel,
    synced_at: new Date().toISOString(),
  }));

  const { error } = await supabase
    .from('message_bus_messages')
    .upsert(rows, { onConflict: 'id' });

  if (error) throw error;
}

// Sort messages by causal order using vector clocks, fall back to timestamp for concurrent
export function sortByCausalOrder(messages: UnifiedMessage[]): UnifiedMessage[] {
  return [...messages].sort((a, b) => {
    const cmp = compare(a.vectorClock, b.vectorClock);
    if (cmp !== 0) return cmp;
    return a.timestamp - b.timestamp; // concurrent: fall back to timestamp
  });
}
```

- [ ] **Step 2: Add reconciliation endpoints to server.ts**

Open `backend/src/server.ts` and add the reconciliation routes alongside existing routes. Find where routes are registered and add:

```typescript
import { reconcile, acceptClientMessages } from './reconciliationEngine';

// Add these routes where other API routes are defined:

app.post('/api/reconcile', async (req, res) => {
  try {
    const { channelId, localMessageIds, localClock } = req.body;
    const result = await reconcile({ channelId, localMessageIds, localClock });
    res.json(result);
  } catch (err) {
    console.error('[Reconcile] Error:', err);
    res.status(500).json({ error: 'Reconciliation failed' });
  }
});

app.post('/api/reconcile/push', async (req, res) => {
  try {
    const { messages } = req.body;
    await acceptClientMessages(messages);
    res.json({ accepted: messages.length });
  } catch (err) {
    console.error('[Reconcile] Push error:', err);
    res.status(500).json({ error: 'Push failed' });
  }
});
```

- [ ] **Step 3: Verify backend compiles**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx tsc --noEmit --skipLibCheck`
Expected: No errors

- [ ] **Step 4: Commit**

```bash
git add backend/src/reconciliationEngine.ts backend/src/server.ts
git commit -m "feat: add reconciliation engine with bidirectional sync and causal ordering"
```

---

## Task 6: Room Database for Local Message Storage (Android)

**Files:**
- Create: `android/app/src/main/java/com/guildofsmiths/trademesh/db/UnifiedMessageEntity.kt`
- Create: `android/app/src/main/java/com/guildofsmiths/trademesh/db/UnifiedMessageDao.kt`
- Create: `android/app/src/main/java/com/guildofsmiths/trademesh/db/AppDatabase.kt`

- [ ] **Step 1: Check if Room dependency exists in build.gradle**

Read `android/app/build.gradle.kts` and check for Room dependencies. If missing, add:

```kotlin
// In dependencies block:
implementation("androidx.room:room-runtime:2.6.1")
implementation("androidx.room:room-ktx:2.6.1")
kapt("androidx.room:room-compiler:2.6.1")
```

And ensure `kapt` plugin is applied:
```kotlin
plugins {
    // ... existing
    id("kotlin-kapt")
}
```

- [ ] **Step 2: Create UnifiedMessageEntity.kt**

```kotlin
package com.guildofsmiths.trademesh.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import com.guildofsmiths.trademesh.data.TransportType
import com.guildofsmiths.trademesh.data.UnifiedMessage
import com.guildofsmiths.trademesh.data.VectorClock

@Entity(tableName = "unified_messages")
data class UnifiedMessageEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "channel_id") val channelId: String,
    @ColumnInfo(name = "sender_id") val senderId: String,
    @ColumnInfo(name = "sender_name") val senderName: String,
    val content: String,
    val timestamp: Long,
    @ColumnInfo(name = "vector_clock") val vectorClockJson: String,
    @ColumnInfo(name = "transport_type") val transportType: String,
    @ColumnInfo(name = "media_type") val mediaType: String = "TEXT",
    @ColumnInfo(name = "media_url") val mediaUrl: String? = null,
    @ColumnInfo(name = "ai_generated") val aiGenerated: Boolean = false,
    @ColumnInfo(name = "ai_model") val aiModel: String? = null,
    @ColumnInfo(name = "synced_to_remote") val syncedToRemote: Boolean = false
) {
    fun toUnifiedMessage(): UnifiedMessage = UnifiedMessage(
        id = id,
        channelId = channelId,
        senderId = senderId,
        senderName = senderName,
        content = content,
        timestamp = timestamp,
        vectorClock = VectorClock.fromJson(vectorClockJson),
        transportType = TransportType.valueOf(transportType),
        mediaType = mediaType,
        mediaUrl = mediaUrl,
        aiGenerated = aiGenerated,
        aiModel = aiModel,
        syncedToRemote = syncedToRemote
    )

    companion object {
        fun from(msg: UnifiedMessage): UnifiedMessageEntity = UnifiedMessageEntity(
            id = msg.id,
            channelId = msg.channelId,
            senderId = msg.senderId,
            senderName = msg.senderName,
            content = msg.content,
            timestamp = msg.timestamp,
            vectorClockJson = msg.vectorClock.toJson(),
            transportType = msg.transportType.name,
            mediaType = msg.mediaType,
            mediaUrl = msg.mediaUrl,
            aiGenerated = msg.aiGenerated,
            aiModel = msg.aiModel,
            syncedToRemote = msg.syncedToRemote
        )
    }
}
```

- [ ] **Step 3: Create UnifiedMessageDao.kt**

```kotlin
package com.guildofsmiths.trademesh.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UnifiedMessageDao {
    @Query("SELECT * FROM unified_messages WHERE channel_id = :channelId ORDER BY timestamp ASC")
    fun getChannelMessages(channelId: String): Flow<List<UnifiedMessageEntity>>

    @Query("SELECT * FROM unified_messages WHERE channel_id = :channelId ORDER BY timestamp ASC")
    suspend fun getChannelMessagesList(channelId: String): List<UnifiedMessageEntity>

    @Query("SELECT id FROM unified_messages WHERE channel_id = :channelId")
    suspend fun getMessageIds(channelId: String): List<String>

    @Query("SELECT * FROM unified_messages WHERE synced_to_remote = 0")
    suspend fun getUnsyncedMessages(): List<UnifiedMessageEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: UnifiedMessageEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(messages: List<UnifiedMessageEntity>)

    @Query("UPDATE unified_messages SET synced_to_remote = 1 WHERE id IN (:ids)")
    suspend fun markSynced(ids: List<String>)

    @Query("DELETE FROM unified_messages WHERE channel_id = :channelId AND timestamp < :before")
    suspend fun deleteOlderThan(channelId: String, before: Long)

    @Query("DELETE FROM unified_messages WHERE channel_id = :channelId")
    suspend fun clearChannel(channelId: String)
}
```

- [ ] **Step 4: Create AppDatabase.kt**

```kotlin
package com.guildofsmiths.trademesh.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [UnifiedMessageEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun unifiedMessageDao(): UnifiedMessageDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "trademesh_db"
                ).fallbackToDestructiveMigration().build().also { INSTANCE = it }
            }
        }
    }
}
```

- [ ] **Step 5: Verify Android project compiles**

Run: `cd /Users/fegensprenelon/smith-net/android && ./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/com/guildofsmiths/trademesh/db/ \
        android/app/build.gradle.kts
git commit -m "feat: add Room database with UnifiedMessage entity and DAO"
```

---

## Task 7: MessageBus Repository (Android)

**Files:**
- Create: `android/app/src/main/java/com/guildofsmiths/trademesh/data/MessageBusRepository.kt`

- [ ] **Step 1: Create MessageBusRepository.kt**

This is the unified message API for Android — replaces direct usage of MessageRepository for new code.

```kotlin
package com.guildofsmiths.trademesh.data

import android.content.Context
import com.guildofsmiths.trademesh.db.AppDatabase
import com.guildofsmiths.trademesh.db.UnifiedMessageEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.util.UUID

class MessageBusRepository(context: Context) {
    private val dao = AppDatabase.getInstance(context).unifiedMessageDao()
    private val scope = CoroutineScope(Dispatchers.IO)
    private val seenIds = LinkedHashSet<String>(1000)
    private val deviceId = UUID.randomUUID().toString().take(12)
    private var localClock = VectorClock()

    private val listeners = mutableListOf<(UnifiedMessage) -> Unit>()

    fun addListener(listener: (UnifiedMessage) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (UnifiedMessage) -> Unit) {
        listeners.remove(listener)
    }

    fun getChannelMessages(channelId: String): Flow<List<UnifiedMessage>> {
        return dao.getChannelMessages(channelId).map { entities ->
            entities.map { it.toUnifiedMessage() }
        }
    }

    fun createAndPublish(
        channelId: String,
        senderId: String,
        senderName: String,
        content: String,
        transportType: TransportType,
        mediaType: String = "TEXT",
        mediaUrl: String? = null
    ): UnifiedMessage {
        localClock = localClock.increment(deviceId)

        val message = UnifiedMessage(
            id = UUID.randomUUID().toString(),
            channelId = channelId,
            senderId = senderId,
            senderName = senderName,
            content = content,
            transportType = transportType,
            vectorClock = localClock,
            mediaType = mediaType,
            mediaUrl = mediaUrl
        )

        publish(message)
        return message
    }

    fun publish(message: UnifiedMessage) {
        if (seenIds.contains(message.id)) return
        seenIds.add(message.id)
        if (seenIds.size > 5000) {
            val iter = seenIds.iterator()
            iter.next()
            iter.remove()
        }

        // Merge incoming clock
        localClock = localClock.merge(message.vectorClock)

        // Persist locally
        scope.launch {
            dao.insert(UnifiedMessageEntity.from(message))
        }

        // Notify listeners
        for (listener in listeners) {
            listener(message)
        }
    }

    suspend fun getUnsyncedMessages(): List<UnifiedMessage> {
        return dao.getUnsyncedMessages().map { it.toUnifiedMessage() }
    }

    suspend fun markSynced(ids: List<String>) {
        dao.markSynced(ids)
    }

    suspend fun getMessageIds(channelId: String): List<String> {
        return dao.getMessageIds(channelId)
    }

    suspend fun insertRemoteMessages(messages: List<UnifiedMessage>) {
        val entities = messages
            .filter { !seenIds.contains(it.id) }
            .map { msg ->
                seenIds.add(msg.id)
                localClock = localClock.merge(msg.vectorClock)
                UnifiedMessageEntity.from(msg.copy(syncedToRemote = true))
            }
        dao.insertAll(entities)
    }

    fun getLocalClock(): VectorClock = localClock

    suspend fun clearChannel(channelId: String) {
        dao.clearChannel(channelId)
    }
}
```

- [ ] **Step 2: Verify Android project compiles**

Run: `cd /Users/fegensprenelon/smith-net/android && ./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/guildofsmiths/trademesh/data/MessageBusRepository.kt
git commit -m "feat: add MessageBusRepository with local-first storage and dedup"
```

---

## Task 8: Android Reconciliation Engine

**Files:**
- Create: `android/app/src/main/java/com/guildofsmiths/trademesh/service/ReconciliationEngine.kt`

- [ ] **Step 1: Create ReconciliationEngine.kt**

```kotlin
package com.guildofsmiths.trademesh.service

import com.guildofsmiths.trademesh.data.MessageBusRepository
import com.guildofsmiths.trademesh.data.TransportType
import com.guildofsmiths.trademesh.data.UnifiedMessage
import com.guildofsmiths.trademesh.data.VectorClock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ReconciliationEngine(
    private val messageBus: MessageBusRepository,
    private val backendUrl: String
) {
    suspend fun reconcileChannel(channelId: String) = withContext(Dispatchers.IO) {
        try {
            val localIds = messageBus.getMessageIds(channelId)
            val localClock = messageBus.getLocalClock()

            // Step 1: Ask server what's different
            val response = postJson(
                "$backendUrl/api/reconcile",
                JSONObject().apply {
                    put("channelId", channelId)
                    put("localMessageIds", JSONArray(localIds))
                    put("localClock", JSONObject(localClock.state))
                }
            )

            // Step 2: Receive messages we're missing
            val missingOnClient = parseMessages(response.getJSONArray("missingOnClient"))
            if (missingOnClient.isNotEmpty()) {
                messageBus.insertRemoteMessages(missingOnClient)
            }

            // Step 3: Push messages server is missing
            val missingOnServerIds = parseStringArray(response.getJSONArray("missingOnServer"))
            if (missingOnServerIds.isNotEmpty()) {
                val unsyncedMessages = messageBus.getUnsyncedMessages()
                    .filter { it.id in missingOnServerIds }

                if (unsyncedMessages.isNotEmpty()) {
                    pushMessages(unsyncedMessages)
                    messageBus.markSynced(unsyncedMessages.map { it.id })
                }
            }
        } catch (e: Exception) {
            // Reconciliation is best-effort; log and continue
            e.printStackTrace()
        }
    }

    private fun pushMessages(messages: List<UnifiedMessage>) {
        val arr = JSONArray()
        for (msg in messages) {
            arr.put(JSONObject().apply {
                put("id", msg.id)
                put("channelId", msg.channelId)
                put("senderId", msg.senderId)
                put("senderName", msg.senderName)
                put("content", msg.content)
                put("timestamp", msg.timestamp)
                put("vectorClock", JSONObject(msg.vectorClock.state))
                put("transportType", msg.transportType.name.lowercase())
                put("mediaType", msg.mediaType)
                put("mediaUrl", msg.mediaUrl)
                put("aiGenerated", msg.aiGenerated)
                put("aiModel", msg.aiModel)
            })
        }
        postJson("$backendUrl/api/reconcile/push", JSONObject().put("messages", arr))
    }

    private fun postJson(url: String, body: JSONObject): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.outputStream.use { it.write(body.toString().toByteArray()) }
        val responseText = conn.inputStream.bufferedReader().readText()
        return JSONObject(responseText)
    }

    private fun parseMessages(arr: JSONArray): List<UnifiedMessage> {
        val result = mutableListOf<UnifiedMessage>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            result.add(UnifiedMessage(
                id = obj.getString("id"),
                channelId = obj.getString("channelId"),
                senderId = obj.getString("senderId"),
                senderName = obj.getString("senderName"),
                content = obj.getString("content"),
                timestamp = obj.getLong("timestamp"),
                vectorClock = VectorClock.fromJson(obj.getJSONObject("vectorClock").toString()),
                transportType = TransportType.valueOf(obj.getString("transportType").uppercase()),
                mediaType = obj.optString("mediaType", "TEXT"),
                mediaUrl = obj.optString("mediaUrl", null),
                aiGenerated = obj.optBoolean("aiGenerated", false),
                aiModel = obj.optString("aiModel", null),
                syncedToRemote = true
            ))
        }
        return result
    }

    private fun parseStringArray(arr: JSONArray): Set<String> {
        val result = mutableSetOf<String>()
        for (i in 0 until arr.length()) {
            result.add(arr.getString(i))
        }
        return result
    }
}
```

- [ ] **Step 2: Verify Android project compiles**

Run: `cd /Users/fegensprenelon/smith-net/android && ./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add android/app/src/main/java/com/guildofsmiths/trademesh/service/ReconciliationEngine.kt
git commit -m "feat: add Android reconciliation engine with bidirectional sync"
```

---

## Task 9: Wire BoundaryEngine to MessageBus

**Files:**
- Modify: `android/app/src/main/java/com/guildofsmiths/trademesh/engine/BoundaryEngine.kt`

This is the critical integration task. BoundaryEngine currently routes messages directly to MeshService, ChatManager, GatewayClient, and SupabaseChat. It needs to route through MessageBusRepository instead.

- [ ] **Step 1: Read current BoundaryEngine.kt thoroughly**

Read the full file to understand all routing paths, listeners, and state.

- [ ] **Step 2: Add MessageBusRepository as a dependency**

Add to BoundaryEngine's constructor/initialization:

```kotlin
private var messageBusRepo: MessageBusRepository? = null
private var reconciliationEngine: ReconciliationEngine? = null

fun initMessageBus(context: Context, backendUrl: String) {
    messageBusRepo = MessageBusRepository(context)
    reconciliationEngine = ReconciliationEngine(messageBusRepo!!, backendUrl)
}
```

- [ ] **Step 3: Modify routeMessage to publish through MessageBus**

In the `routeMessage()` function (or equivalent send method), before dispatching to transports, publish to MessageBus:

```kotlin
// At the start of the send path:
val unifiedMsg = messageBusRepo?.createAndPublish(
    channelId = channelId,
    senderId = currentUserId,
    senderName = currentUserName,
    content = content,
    transportType = if (hasInternet) TransportType.IP else TransportType.BLE,
    mediaType = mediaType,
    mediaUrl = mediaUrl
)

// Then dispatch to transports as before (MeshService, ChatManager, etc.)
// The MessageBus handles dedup, so even if the same message comes back
// via a different path, it won't be stored twice.
```

- [ ] **Step 4: Modify incoming message handlers to publish through MessageBus**

For each incoming path (mesh receive, WS receive, gateway receive, Supabase receive), wrap the message into UnifiedMessage and publish through MessageBus instead of directly adding to MessageRepository:

```kotlin
// Example for mesh message receive:
fun onMeshMessageReceived(payload: ByteArray, ...) {
    val unifiedMsg = UnifiedMessage(
        id = deriveMessageId(payload), // Use existing dedup logic
        channelId = resolveChannelId(channelHash),
        senderId = resolveSenderId(senderHash),
        senderName = resolveSenderName(senderHash),
        content = decodeContent(payload),
        transportType = TransportType.BLE,
        vectorClock = VectorClock() // BLE messages start with empty clock, merged on receive
    )
    messageBusRepo?.publish(unifiedMsg)
}
```

- [ ] **Step 5: Trigger reconciliation on transport change**

Add reconciliation call when connectivity changes:

```kotlin
// In the connectivity change handler:
fun onConnectivityChanged(isOnline: Boolean) {
    if (isOnline) {
        scope.launch {
            // Reconcile all active channels
            for (channelId in activeChannelIds) {
                reconciliationEngine?.reconcileChannel(channelId)
            }
        }
    }
}
```

- [ ] **Step 6: Verify Android project compiles**

Run: `cd /Users/fegensprenelon/smith-net/android && ./gradlew compileDebugKotlin 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add android/app/src/main/java/com/guildofsmiths/trademesh/engine/BoundaryEngine.kt
git commit -m "feat: wire BoundaryEngine to MessageBus with reconciliation on transport change"
```

---

## Task 10: Wire wsHandler to MessageBus (Backend)

**Files:**
- Modify: `backend/src/wsHandler.ts`

- [ ] **Step 1: Read current wsHandler.ts thoroughly**

Read the full file to understand all message handling paths.

- [ ] **Step 2: Import and use MessageBus**

Add import at top:
```typescript
import { createMessage, publish, subscribe, isDuplicate } from './messageBus';
```

- [ ] **Step 3: Modify message handling to go through MessageBus**

For each message handling path in wsHandler:

1. **Client sends message**: Instead of `messageStore.add()` + direct broadcast, use `publish()`:
```typescript
// Replace direct store + broadcast with:
const unifiedMsg = createMessage(channelId, senderId, senderName, content, 'ip');
publish(unifiedMsg);
```

2. **Gateway forwards mesh message**: Instead of direct store + broadcast:
```typescript
// Replace handleGatewayMessage internals with:
const unifiedMsg = createMessage(channelId, senderId, senderName, content, 'ble', existingId, existingClock);
publish(unifiedMsg);
```

3. **Subscribe WS clients to channels**: Use MessageBus subscribe:
```typescript
// For each client connected to a channel:
const unsub = subscribe(channelId, (msg) => {
    ws.send(JSON.stringify({ type: 'message', data: msg }));
});
// Store unsub for cleanup on disconnect
```

- [ ] **Step 4: Verify backend compiles**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx tsc --noEmit --skipLibCheck`
Expected: No errors

- [ ] **Step 5: Commit**

```bash
git add backend/src/wsHandler.ts
git commit -m "feat: route wsHandler message flow through MessageBus"
```

---

## Task 11: Wire gatewayManager to MessageBus (Backend)

**Files:**
- Modify: `backend/src/gatewayManager.ts`

- [ ] **Step 1: Read current gatewayManager.ts**

- [ ] **Step 2: Modify injectMessage to use MessageBus**

Replace direct message forwarding with MessageBus publish:
```typescript
import { createMessage, publish } from './messageBus';

// In injectMessage():
const unifiedMsg = createMessage(channelId, senderId, senderName, content, 'gateway');
publish(unifiedMsg);
// Then forward to relay as before
```

- [ ] **Step 3: Modify onMeshMessage to use MessageBus**

```typescript
// In onMeshMessage():
const unifiedMsg = createMessage(channelId, senderId, senderName, content, 'ble', existingId);
publish(unifiedMsg);
```

- [ ] **Step 4: Verify backend compiles**

Run: `cd /Users/fegensprenelon/smith-net/backend && npx tsc --noEmit --skipLibCheck`
Expected: No errors

- [ ] **Step 5: Commit**

```bash
git add backend/src/gatewayManager.ts
git commit -m "feat: route gatewayManager through MessageBus"
```

---

## Task 12: Integration Verification

This task verifies the full messaging pipeline works end-to-end.

- [ ] **Step 1: Start backend and verify it boots**

```bash
cd /Users/fegensprenelon/smith-net/backend && npm run dev
```

Expected: Server starts without errors, new routes registered.

- [ ] **Step 2: Verify reconciliation endpoint responds**

```bash
curl -X POST http://localhost:3000/api/reconcile \
  -H "Content-Type: application/json" \
  -d '{"channelId":"test-channel","localMessageIds":[],"localClock":{}}'
```

Expected: JSON response with `missingOnClient`, `missingOnServer`, `mergedClock` fields.

- [ ] **Step 3: Build Android APK**

```bash
cd /Users/fegensprenelon/smith-net/android && ./gradlew assembleDebug 2>&1 | tail -10
```

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit any fixes needed**

If any compilation or runtime issues were found and fixed, commit them:

```bash
git add -A
git commit -m "fix: resolve integration issues in messaging unification"
```

- [ ] **Step 5: Final commit — Phase 1 complete**

```bash
git add -A
git commit -m "milestone: Phase 1 messaging unification complete — MessageBus, vector clocks, reconciliation"
```

---

## Summary

| Task | Component | What It Does |
|------|-----------|-------------|
| 1 | Vector Clock (Backend) | Causal ordering primitives + unified message types |
| 2 | Vector Clock (Android) | Kotlin mirror of vector clock + UnifiedMessage model |
| 3 | Supabase Migration | `message_bus_messages` table with indexes and RLS |
| 4 | Message Bus (Backend) | Pub/sub, dedup, Supabase persistence |
| 5 | Reconciliation Engine (Backend) | Bidirectional sync, vector clock merge |
| 6 | Room Database (Android) | Local-first message storage |
| 7 | MessageBus Repository (Android) | Unified message API with local-first + dedup |
| 8 | Reconciliation Engine (Android) | Client-side bidirectional sync |
| 9 | Wire BoundaryEngine | Route Android messaging through MessageBus |
| 10 | Wire wsHandler | Route backend WS messaging through MessageBus |
| 11 | Wire gatewayManager | Route backend relay messaging through MessageBus |
| 12 | Integration Verification | End-to-end smoke test |
