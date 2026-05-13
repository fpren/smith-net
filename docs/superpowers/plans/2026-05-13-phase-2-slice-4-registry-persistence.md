# Phase 2 Slice 4 — channelRegistry + gatewayManager persistence

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Persist `channelRegistry` and `gatewayManager` state to Postgres so routing survives backend restart. Closes audit weak point #5 — the final open weak point in Phase 2 scope.

**Architecture:** Migration 007 creates a `channels` table with JSONB columns for the ACL arrays (memberIds / allowedUsers / blockedUsers / pendingRequests). Migration 008 creates `gateway_sessions` with a `last_activity` TTL column. `channelRegistry` becomes a write-through pg cache: every mutation method becomes async, runs the in-memory update, then UPSERTs the full row to pg. On boot, `channelRegistry.initialize()` SELECTs from pg, rebuilds the Map + mesh-hash index. `gatewayManager` follows the same pattern: `register`/`updateActivity`/`unregister` write through; on boot, load rows where `last_activity > now() - 5min` (stale ones aren't restored because their relay reconnections are already dead). The WS reference (per-process) is rebuilt when the relay reconnects. The existing `channelRegistry.rehydrate()` block in `server.ts` boot sequence is removed — `initialize()` handles it.

**Tech Stack:** Node + Express + TypeScript + `pg.Pool` + Jest. No new deps.

**Prerequisites:**
- Slices 1, 2, 3 shipped (tags `phase-2-slice-1`, `phase-2-slice-2`, `phase-2-slice-3`). 130/130 tests passing.
- `usersService` from Slice 1; pino from Slice 2; WS JWT from Slice 3.
- The existing `channel_members` table from migration 002 stays as-is (orphan from a never-completed feature). Slice 4 doesn't touch it — channel membership is stored as JSONB on the `channels` row instead.
- `DATABASE_URL` is set and `psql` is on PATH.
- Working branch is `feat/relay-hetzner-postgres`.

**Reference docs:**
- Spec: `docs/superpowers/specs/2026-05-13-phase-2-persistence-design.md` (Slice 4 section)
- Audit: `docs/smith-net-architecture-audit.md` (weak point #5)
- CLAUDE.md: no inline fire-and-forget — all writes through pg must await

---

## File Map

| File | Action | Purpose |
|---|---|---|
| `backend/migrations/007_channels.sql` | Create | `channels` table with JSONB ACL columns |
| `backend/migrations/008_gateway_sessions.sql` | Create | `gateway_sessions` table with `last_activity` TTL |
| `backend/src/channelRegistry.ts` | Modify | Add `persistChannel`, async mutations, `initialize()` loads from pg |
| `backend/src/gatewayManager.ts` | Modify | Persist on register/updateActivity/unregister; load fresh sessions on init |
| `backend/src/server.ts` | Modify | Remove the rehydrate IIFE; await `channelRegistry.initialize()`; await `gatewayManager.initialize()` |
| Caller files (api.ts, wsHandler.ts, possibly authRoutes) | Modify | Add `await` to ~20 mutation call sites |
| `backend/src/__tests__/channelRegistryPersist.test.ts` | Create | Round-trip restart test for channels |
| `backend/src/__tests__/gatewayManagerPersist.test.ts` | Create | Round-trip restart test for gateway sessions |
| `docs/smith-net-architecture-audit.md` | Modify (Task 6) | Annotate weak point #5 `[closed in slice 4, commit <SHA>]` |

---

## Task 1 — Migrations 007 + 008

**Files:**
- Create: `backend/migrations/007_channels.sql`
- Create: `backend/migrations/008_gateway_sessions.sql`

### Step 1: Write 007_channels.sql

Create `backend/migrations/007_channels.sql`:

```sql
-- 007_channels.sql
-- Phase 2 Slice 4: persist channelRegistry state. JSONB columns hold the
-- ACL arrays so the entire Channel struct round-trips through one row.

CREATE TABLE IF NOT EXISTS channels (
  id                TEXT PRIMARY KEY,
  name              TEXT NOT NULL,
  type              TEXT NOT NULL,
  visibility        TEXT NOT NULL DEFAULT 'public',
  creator_id        TEXT NOT NULL,
  member_ids        JSONB NOT NULL DEFAULT '[]'::jsonb,
  allowed_users     JSONB NOT NULL DEFAULT '[]'::jsonb,
  blocked_users     JSONB NOT NULL DEFAULT '[]'::jsonb,
  pending_requests  JSONB NOT NULL DEFAULT '[]'::jsonb,
  requires_approval BOOLEAN NOT NULL DEFAULT FALSE,
  is_archived       BOOLEAN NOT NULL DEFAULT FALSE,
  is_deleted        BOOLEAN NOT NULL DEFAULT FALSE,
  mesh_hash         INTEGER NOT NULL,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS channels_mesh_hash_idx ON channels (mesh_hash);
CREATE INDEX IF NOT EXISTS channels_creator_idx   ON channels (creator_id);
```

### Step 2: Write 008_gateway_sessions.sql

Create `backend/migrations/008_gateway_sessions.sql`:

```sql
-- 008_gateway_sessions.sql
-- Phase 2 Slice 4: persist gatewayManager relay metadata. The WS reference
-- itself is per-process and not persisted — relays must reconnect to
-- repopulate it. `last_activity` is the TTL anchor: rows older than 5min
-- are considered dead and skipped at boot-time rebuild. A Phase 3 cleanup
-- daemon will DELETE them.

CREATE TABLE IF NOT EXISTS gateway_sessions (
  id             TEXT PRIMARY KEY,
  name           TEXT NOT NULL,
  capabilities   JSONB NOT NULL DEFAULT '[]'::jsonb,
  last_activity  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS gateway_sessions_last_activity_idx
  ON gateway_sessions (last_activity);
```

### Step 3: Apply both migrations

```bash
export PATH=/opt/homebrew/opt/postgresql@16/bin:$PATH
export DATABASE_URL=postgres://fegensprenelon@localhost:5432/smithnet
cd backend && psql "$DATABASE_URL" -f migrations/007_channels.sql && psql "$DATABASE_URL" -f migrations/008_gateway_sessions.sql
```

Expected: `CREATE TABLE`, `CREATE INDEX` for each. No errors.

### Step 4: Verify schemas

```bash
psql "$DATABASE_URL" -c "\d channels" | head -20
psql "$DATABASE_URL" -c "\d gateway_sessions" | head -10
```

Expected: `channels` has 14 columns including the 4 JSONB ACL columns. `gateway_sessions` has 5 columns with `last_activity` as TIMESTAMPTZ NOT NULL.

### Step 5: Commit

```bash
git add backend/migrations/007_channels.sql backend/migrations/008_gateway_sessions.sql
git commit -m "feat(db): migrations 007 channels + 008 gateway_sessions (Phase 2 Slice 4)"
```

---

## Task 2 — channelRegistry: persistChannel helper + async create() + initialize() loads from pg

**Files:**
- Modify: `backend/src/channelRegistry.ts`
- Create: `backend/src/__tests__/channelRegistryPersist.test.ts`

### Step 1: Write the failing test

Create `backend/src/__tests__/channelRegistryPersist.test.ts`:

```typescript
import { pg, isPgEnabled } from '../db';
import { channelRegistry } from '../channelRegistry';

const describeDb = isPgEnabled() ? describe : describe.skip;

async function cleanChannels() {
  if (!isPgEnabled()) return;
  await pg!.query('DELETE FROM channels');
  // Also reset the in-memory map so loads after each test start fresh.
  (channelRegistry as any).channels.clear();
  (channelRegistry as any).meshHashIndex.clear();
}

describeDb('channelRegistry persistence', () => {
  beforeEach(cleanChannels);
  afterAll(async () => { await pg?.end(); });

  it('create() writes a row to channels and stays in the in-memory map', async () => {
    const ch = await channelRegistry.create('test-A', 'broadcast', 'user-1');
    expect(ch.id).toBeTruthy();
    expect(channelRegistry.get(ch.id)).toBeDefined();

    const rows = await pg!.query('SELECT id, name, type, creator_id, mesh_hash FROM channels WHERE id = $1', [ch.id]);
    expect(rows.rowCount).toBe(1);
    expect(rows.rows[0].name).toBe('test-A');
    expect(rows.rows[0].type).toBe('broadcast');
    expect(rows.rows[0].creator_id).toBe('user-1');
    expect(rows.rows[0].mesh_hash).toBe(ch.meshHash);
  });

  it('initialize() loads existing rows into the in-memory map', async () => {
    const ch = await channelRegistry.create('test-B', 'group', 'user-2');
    (channelRegistry as any).channels.clear();
    (channelRegistry as any).meshHashIndex.clear();
    expect(channelRegistry.get(ch.id)).toBeUndefined();

    await channelRegistry.initialize();
    expect(channelRegistry.get(ch.id)?.name).toBe('test-B');
  });
});
```

### Step 2: Run the test to verify it fails

```bash
cd backend && npx jest src/__tests__/channelRegistryPersist.test.ts
```

Expected: FAIL — either `create()` is sync (returns Channel directly, not Promise<Channel>) or the SQL row never appears.

### Step 3: Implement persistChannel helper + make create() async

In `backend/src/channelRegistry.ts`:

**3a. Add the pg import at the top:**

```typescript
import { pg, isPgEnabled } from './db';
```

**3b. Add a private helper inside the class:**

```typescript
private async persistChannel(channel: Channel): Promise<void> {
  if (!isPgEnabled() || !pg) return; // dev-mode fallback: in-memory only
  await pg.query(
    `INSERT INTO channels (
       id, name, type, visibility, creator_id,
       member_ids, allowed_users, blocked_users, pending_requests,
       requires_approval, is_archived, is_deleted, mesh_hash, updated_at
     ) VALUES (
       $1, $2, $3, $4, $5,
       $6::jsonb, $7::jsonb, $8::jsonb, $9::jsonb,
       $10, $11, $12, $13, NOW()
     )
     ON CONFLICT (id) DO UPDATE SET
       name              = EXCLUDED.name,
       type              = EXCLUDED.type,
       visibility        = EXCLUDED.visibility,
       creator_id        = EXCLUDED.creator_id,
       member_ids        = EXCLUDED.member_ids,
       allowed_users     = EXCLUDED.allowed_users,
       blocked_users     = EXCLUDED.blocked_users,
       pending_requests  = EXCLUDED.pending_requests,
       requires_approval = EXCLUDED.requires_approval,
       is_archived       = EXCLUDED.is_archived,
       is_deleted        = EXCLUDED.is_deleted,
       updated_at        = NOW()`,
    [
      channel.id, channel.name, channel.type, channel.visibility, channel.creatorId,
      JSON.stringify(channel.memberIds),
      JSON.stringify(channel.allowedUsers),
      JSON.stringify(channel.blockedUsers),
      JSON.stringify(channel.pendingRequests),
      channel.requiresApproval, channel.isArchived, channel.isDeleted, channel.meshHash,
    ]
  );
}
```

**3c. Make `create()` async and call persistChannel:**

Replace the existing `create()` method:

```typescript
async create(
  name: string,
  type: Channel['type'],
  creatorId: string,
  memberIds?: string[],
  visibility: ChannelVisibility = 'public',
  requiresApproval: boolean = false
): Promise<Channel> {
  const id = uuidv4();
  const meshHash = this.computeMeshHash(id);

  const channel: Channel = {
    id,
    name,
    type,
    visibility,
    creatorId,
    createdAt: Date.now(),
    memberIds: memberIds || [creatorId],
    allowedUsers: [],
    blockedUsers: [],
    pendingRequests: [],
    requiresApproval,
    isArchived: false,
    isDeleted: false,
    meshHash,
  };

  this.channels.set(id, channel);
  this.meshHashIndex.set(meshHash, id);
  await this.persistChannel(channel);

  requestLogger().info({ event: 'channel_created', id, name, visibility, meshHash }, 'channel created');
  return channel;
}
```

If `requestLogger` isn't imported, add `import { requestLogger } from './log';` at the top.

**3d. Replace initialize() to load from pg:**

```typescript
async initialize(): Promise<void> {
  if (!isPgEnabled() || !pg) {
    requestLogger().info({ event: 'channel_registry_initialized', mode: 'memory_only' }, 'channel registry initialized (no DB)');
    return;
  }
  const { rows } = await pg.query<{
    id: string; name: string; type: string; visibility: string; creator_id: string;
    member_ids: string[]; allowed_users: string[]; blocked_users: string[]; pending_requests: string[];
    requires_approval: boolean; is_archived: boolean; is_deleted: boolean; mesh_hash: number;
    created_at: Date;
  }>(
    `SELECT * FROM channels WHERE is_deleted = FALSE ORDER BY created_at ASC`
  );
  let count = 0;
  for (const row of rows) {
    const channel: Channel = {
      id: row.id,
      name: row.name,
      type: row.type as Channel['type'],
      visibility: row.visibility as ChannelVisibility,
      creatorId: row.creator_id,
      memberIds: row.member_ids ?? [],
      allowedUsers: row.allowed_users ?? [],
      blockedUsers: row.blocked_users ?? [],
      pendingRequests: row.pending_requests ?? [],
      requiresApproval: row.requires_approval,
      isArchived: row.is_archived,
      isDeleted: row.is_deleted,
      meshHash: row.mesh_hash,
      createdAt: row.created_at.getTime(),
    };
    this.channels.set(row.id, channel);
    this.meshHashIndex.set(row.mesh_hash, row.id);
    count++;
  }
  requestLogger().info({ event: 'channel_registry_initialized', count }, 'channel registry loaded from pg');
}
```

The `rehydrate(rows)` method becomes redundant but DO NOT delete it yet — server.ts boot still calls it from the rehydrate IIFE. Task 4 removes that IIFE.

### Step 4: Verify the test passes

```bash
cd backend && npx jest src/__tests__/channelRegistryPersist.test.ts
```

Expected: PASS — 2 tests pass.

### Step 5: Run tsc

```bash
cd backend && npx tsc --noEmit
```

Expected: type errors at callers of `channelRegistry.create(...)` because it now returns `Promise<Channel>` instead of `Channel`. **Do not fix them yet** — Task 4 wires up server.ts and Task 5 fixes all callers in bulk.

If tsc has OTHER errors (unrelated to the async change), escalate.

### Step 6: Commit

```bash
git add backend/src/channelRegistry.ts backend/src/__tests__/channelRegistryPersist.test.ts
git commit -m "feat(channels): persistChannel + async create() + pg-loading initialize()"
```

---

## Task 3 — channelRegistry: persist remaining mutations + caller updates

**Files:**
- Modify: `backend/src/channelRegistry.ts` (mutation methods)
- Modify: callers across the backend that call mutation methods

### Step 1: Find all callers of mutation methods

```bash
grep -rn "channelRegistry\." backend/src/ --include="*.ts" | grep -v "channelRegistry.ts\|__tests__" | head -40
```

Note the file:line locations. The mutation methods are:
- `create` (already async from Task 2)
- `update`, `archive`, `delete`
- `addMember`, `removeMember`
- `requestAccess`, `respondToAccessRequest`
- `updateUserAccess`, `updateVisibility`
- `subscribeUserToChannels` — this one mutates `memberIds` for broadcast channels; needs persistence too

Read-only methods (`get`, `getByMeshHash`, `findByName`, `list`, `listForUser`, `canUserAccess`, `canUserSeeInList`, `getAccessStatus`) stay synchronous.

### Step 2: Convert each mutation method to async + call persistChannel

For each of the 9 remaining mutation methods, the pattern is:
1. Add `async` to the signature
2. Change return type to `Promise<X>` (where X was the old return type)
3. After the in-memory mutation, `await this.persistChannel(channel)` (if a channel was modified)

For methods that may modify multiple channels (like `subscribeUserToChannels`), loop and persist each modified one.

Example — `update`:

```typescript
async update(id: string, updates: Partial<Channel>): Promise<Channel | undefined> {
  const channel = this.channels.get(id);
  if (!channel) return undefined;

  const updated = { ...channel, ...updates };
  this.channels.set(id, updated);
  await this.persistChannel(updated);
  return updated;
}
```

Example — `archive`:

```typescript
async archive(id: string): Promise<boolean> {
  const channel = this.channels.get(id);
  if (!channel) return false;
  channel.isArchived = true;
  await this.persistChannel(channel);
  return true;
}
```

Example — `delete` (soft delete):

```typescript
async delete(id: string): Promise<boolean> {
  const channel = this.channels.get(id);
  if (!channel) return false;
  channel.isDeleted = true;
  await this.persistChannel(channel);
  return true;
}
```

Example — `addMember`:

```typescript
async addMember(channelId: string, userId: string): Promise<boolean> {
  const channel = this.channels.get(channelId);
  if (!channel) return false;
  if (!channel.memberIds.includes(userId)) {
    channel.memberIds.push(userId);
    await this.persistChannel(channel);
  }
  return true;
}
```

Example — `subscribeUserToChannels` (multiple channels may change):

```typescript
async subscribeUserToChannels(userId: string): Promise<string[]> {
  const channelIds: string[] = [];
  const modified: Channel[] = [];
  for (const channel of this.channels.values()) {
    if (!channel.isDeleted && !channel.isArchived) {
      if (channel.type === 'broadcast') {
        if (!channel.memberIds.includes(userId)) {
          channel.memberIds.push(userId);
          modified.push(channel);
        }
        channelIds.push(channel.id);
      } else if (channel.memberIds.includes(userId)) {
        channelIds.push(channel.id);
      }
    }
  }
  for (const c of modified) await this.persistChannel(c);
  return channelIds;
}
```

Apply the same pattern to `removeMember`, `requestAccess`, `respondToAccessRequest`, `updateUserAccess`, `updateVisibility`.

### Step 3: Update all callers to await

Run:

```bash
grep -rn "channelRegistry\.\(create\|update\|archive\|delete\|addMember\|removeMember\|requestAccess\|respondToAccessRequest\|updateUserAccess\|updateVisibility\|subscribeUserToChannels\)\b" backend/src/ --include="*.ts" | grep -v "channelRegistry.ts\|__tests__"
```

For every match:
1. Add `await` before the call
2. Make the containing function `async` if it isn't already
3. If the call result is destructured / used in a conditional, ensure the destructure/conditional handles a Promise unwrap

Files most likely impacted: `api.ts`, `wsHandler.ts`, `adminRoutes.ts`. The Slice 3 `wsHandler.onConnection` already calls `channelRegistry.subscribeUserToChannels(userId)` synchronously — that needs `await`.

### Step 4: Run the full backend test suite

```bash
cd backend && npx jest
```

Expected: 132+ tests passing (130 prior + 2 from Task 2's persist test).

If tests fail with "Cannot await on undefined" or similar, the implementer missed an `await`. Add it.

### Step 5: Run tsc

```bash
cd backend && npx tsc --noEmit
```

Expected: clean.

### Step 6: Commit

```bash
git add backend/src/channelRegistry.ts backend/src/
git commit -m "feat(channels): all mutations async + persist + caller updates"
```

---

## Task 4 — server.ts: replace rehydrate IIFE with awaited initialize()

**Files:**
- Modify: `backend/src/server.ts`

### Step 1: Find the rehydrate block

In `backend/src/server.ts`, look around lines 295-320. The current shape:

```typescript
wsHandler.initialize(wss);
// ...
channelRegistry.initialize();   // sync no-op
// ...
(async () => {
  try {
    const { pg, isPgEnabled } = await import('./db');
    if (!isPgEnabled() || !pg) return;
    const { rows } = await pg.query(
      `SELECT id, name, type, creator_id, created_at, is_archived, is_deleted
         FROM channels
        WHERE is_deleted = FALSE OR is_deleted IS NULL`
    );
    channelRegistry.rehydrate(rows);
  } catch (err) {
    console.warn('[Startup] Channel rehydrate skipped:', (err as Error).message);
  }
})();
```

### Step 2: Replace with awaited initialize()

The boot code can't be top-level `await` outside an async wrapper, but the existing IIFE pattern works. Replace the IIFE with:

```typescript
(async () => {
  try {
    await channelRegistry.initialize();
  } catch (err) {
    requestLogger().error({ err, event: 'channel_registry_init_failed' }, 'channel registry init failed at boot');
  }
})();
```

The old synchronous `channelRegistry.initialize();` call ABOVE the IIFE is now redundant — it just logged. Replace it with the new async initialize() in the IIFE. Remove the duplicate.

If `requestLogger` isn't imported in server.ts, add `import { requestLogger } from './log';` near the top.

### Step 3: Delete or deprecate the `rehydrate()` method

`channelRegistry.rehydrate(rows)` is no longer called. In `channelRegistry.ts`, delete the entire `rehydrate()` method.

### Step 4: Run the full suite

```bash
cd backend && npx jest
```

Expected: 132+ tests passing.

### Step 5: Run tsc

```bash
cd backend && npx tsc --noEmit
```

Expected: clean.

### Step 6: Commit

```bash
git add backend/src/server.ts backend/src/channelRegistry.ts
git commit -m "feat(channels): boot uses async initialize(); delete rehydrate()"
```

---

## Task 5 — gatewayManager persistence

**Files:**
- Modify: `backend/src/gatewayManager.ts`
- Create: `backend/src/__tests__/gatewayManagerPersist.test.ts`

### Step 1: Write the failing test

Create `backend/src/__tests__/gatewayManagerPersist.test.ts`:

```typescript
import { pg, isPgEnabled } from '../db';
import { gatewayManager } from '../gatewayManager';

const describeDb = isPgEnabled() ? describe : describe.skip;

async function cleanSessions() {
  if (!isPgEnabled()) return;
  await pg!.query('DELETE FROM gateway_sessions');
  // Clear the in-memory map and any session timestamps without the live WS.
  (gatewayManager as any).relays.clear();
}

describeDb('gatewayManager persistence', () => {
  beforeEach(cleanSessions);
  afterAll(async () => { await pg?.end(); });

  it('register() writes a row to gateway_sessions', async () => {
    // We pass a fake ws object — register only stores the WS reference in
    // memory; pg gets the relay metadata only.
    const fakeWs: any = { readyState: 1 };
    const relay = await gatewayManager.register('relay-A', 'alpha', ['ble'], fakeWs);
    expect(relay.id).toBe('relay-A');

    const rows = await pg!.query('SELECT id, name FROM gateway_sessions WHERE id = $1', ['relay-A']);
    expect(rows.rowCount).toBe(1);
    expect(rows.rows[0].name).toBe('alpha');
  });

  it('initialize() loads non-stale rows (< 5 min old) but skips stale ones', async () => {
    const fakeWs: any = { readyState: 1 };
    await gatewayManager.register('relay-fresh', 'fresh', ['ble'], fakeWs);
    // Insert a manually-aged stale row
    await pg!.query(
      `INSERT INTO gateway_sessions (id, name, capabilities, last_activity, created_at)
       VALUES ($1, $2, '[]'::jsonb, NOW() - INTERVAL '6 minutes', NOW() - INTERVAL '6 minutes')`,
      ['relay-stale', 'stale']
    );

    // Wipe in-memory and re-init
    (gatewayManager as any).relays.clear();
    await gatewayManager.initialize();

    expect(gatewayManager.get('relay-fresh')).toBeDefined();
    expect(gatewayManager.get('relay-stale')).toBeUndefined();
  });

  it('unregister() deletes the pg row', async () => {
    const fakeWs: any = { readyState: 1 };
    await gatewayManager.register('relay-to-go', 'goner', [], fakeWs);
    await gatewayManager.unregister('relay-to-go');
    const rows = await pg!.query('SELECT id FROM gateway_sessions WHERE id = $1', ['relay-to-go']);
    expect(rows.rowCount).toBe(0);
  });
});
```

### Step 2: Verify failure

```bash
cd backend && npx jest src/__tests__/gatewayManagerPersist.test.ts
```

Expected: FAIL — `register` is sync, returns `GatewayRelay` not `Promise<GatewayRelay>`, no pg row appears.

### Step 3: Refactor gatewayManager.ts

In `backend/src/gatewayManager.ts`:

**3a. Add pg import at the top:**

```typescript
import { pg, isPgEnabled } from './db';
```

**3b. Make `register()` async with pg write:**

Replace existing `register()`:

```typescript
async register(relayId: string, name: string, capabilities: string[], ws: WebSocket): Promise<GatewayRelay> {
  const relay: GatewayRelay = {
    id: relayId,
    name,
    connectedAt: Date.now(),
    lastActivity: Date.now(),
    capabilities,
  };

  this.relays.set(relayId, { relay, ws });

  if (isPgEnabled() && pg) {
    await pg.query(
      `INSERT INTO gateway_sessions (id, name, capabilities, last_activity, created_at)
       VALUES ($1, $2, $3::jsonb, NOW(), NOW())
       ON CONFLICT (id) DO UPDATE SET
         name          = EXCLUDED.name,
         capabilities  = EXCLUDED.capabilities,
         last_activity = NOW()`,
      [relayId, name, JSON.stringify(capabilities)]
    );
  }

  requestLogger().info({ event: 'gateway_registered', relayId, name }, 'gateway relay registered');
  return relay;
}
```

If `requestLogger` isn't imported, add `import { requestLogger } from './log';`.

**3c. Make `unregister()` async with pg delete:**

```typescript
async unregister(relayId: string): Promise<void> {
  const connected = this.relays.get(relayId);
  if (connected) {
    this.relays.delete(relayId);
    if (isPgEnabled() && pg) {
      await pg.query('DELETE FROM gateway_sessions WHERE id = $1', [relayId]);
    }
    requestLogger().info({ event: 'gateway_unregistered', relayId, name: connected.relay.name }, 'gateway relay unregistered');
  }
}
```

**3d. Make `updateActivity()` async + persist last_activity:**

```typescript
async updateActivity(relayId: string): Promise<void> {
  const connected = this.relays.get(relayId);
  if (connected) {
    connected.relay.lastActivity = Date.now();
    if (isPgEnabled() && pg) {
      await pg.query(
        `UPDATE gateway_sessions SET last_activity = NOW() WHERE id = $1`,
        [relayId]
      );
    }
  }
}
```

**3e. Add `initialize()` to load fresh sessions on boot:**

```typescript
async initialize(): Promise<void> {
  if (!isPgEnabled() || !pg) {
    requestLogger().info({ event: 'gateway_manager_initialized', mode: 'memory_only' }, 'gateway manager initialized (no DB)');
    return;
  }
  const { rows } = await pg.query<{
    id: string; name: string; capabilities: string[]; last_activity: Date; created_at: Date;
  }>(
    `SELECT id, name, capabilities, last_activity, created_at
       FROM gateway_sessions
      WHERE last_activity > NOW() - INTERVAL '5 minutes'`
  );
  // Note: the WS reference is per-process. We rebuild metadata but
  // not the live socket — relays must reconnect to repopulate that.
  // The map entries here have ws=null; injectMessage / broadcastToRelays
  // already check ws.readyState so null/dead refs are skipped naturally.
  for (const row of rows) {
    const relay: GatewayRelay = {
      id: row.id,
      name: row.name,
      connectedAt: row.created_at.getTime(),
      lastActivity: row.last_activity.getTime(),
      capabilities: row.capabilities ?? [],
    };
    // Cast to any because ConnectedRelay.ws is typed as WebSocket;
    // post-restart we don't have a real WS until the relay reconnects.
    this.relays.set(row.id, { relay, ws: null as any });
  }
  requestLogger().info({ event: 'gateway_manager_initialized', count: rows.length }, 'gateway manager loaded from pg');
}
```

**3f. Update `injectMessage` and `broadcastToRelays` to handle null ws:**

The existing `injectMessage` already does `if (!connected || connected.ws.readyState !== WebSocket.OPEN) return false;` — but `connected.ws` could be null after a boot-time rehydrate. Add a null-safe check:

```typescript
injectMessage(relayId: string, message: Message): boolean {
  const connected = this.relays.get(relayId);
  if (!connected || !connected.ws || connected.ws.readyState !== WebSocket.OPEN) {
    requestLogger().info({ event: 'gateway_inject_skipped', relayId, reason: 'no_open_ws' }, 'gateway inject skipped');
    return false;
  }
  // ...rest of existing body
}
```

If the existing body uses a `console.log` for the "Cannot inject" log, replace with the structured logger as shown.

### Step 4: Wire gatewayManager.initialize() into server.ts boot

In `backend/src/server.ts`, find the `wsHandler.initialize(wss);` line. Add a new async boot block:

```typescript
(async () => {
  try {
    await gatewayManager.initialize();
  } catch (err) {
    requestLogger().error({ err, event: 'gateway_manager_init_failed' }, 'gateway manager init failed at boot');
  }
})();
```

This can be next to the channelRegistry.initialize() block.

### Step 5: Update all callers of gatewayManager.register/updateActivity/unregister/forceDisconnect

```bash
grep -rn "gatewayManager\.\(register\|updateActivity\|unregister\|forceDisconnect\)\b" backend/src/ --include="*.ts" | grep -v "gatewayManager.ts\|__tests__"
```

Add `await` to each call site. `forceDisconnect()` may also need pg cleanup — if it does, mirror the `unregister()` pattern (delete the pg row). For Phase 2 interim, calling `unregister()` from inside `forceDisconnect()` is also fine.

### Step 6: Run the suite

```bash
cd backend && npx jest
```

Expected: 135+ passing (132 from prior tasks + 3 new gatewayManagerPersist tests).

### Step 7: Run tsc

```bash
cd backend && npx tsc --noEmit
```

Expected: clean.

### Step 8: Commit

```bash
git add backend/src/gatewayManager.ts backend/src/__tests__/gatewayManagerPersist.test.ts backend/src/server.ts backend/src/
git commit -m "feat(gateway): persist relay sessions; 5min TTL load on boot"
```

---

## Task 6 — Slice 4 closeout: restart smoke, audit annotation, tag

**Files:**
- Modify: `docs/smith-net-architecture-audit.md`

### Step 1: Run full suite one final time

```bash
cd backend && npx jest
```

Expected: 135+ passing. 0 failing.

### Step 2: Manual restart smoke for channels

```bash
export PATH=/opt/homebrew/opt/postgresql@16/bin:$PATH
export DATABASE_URL=postgres://fegensprenelon@localhost:5432/smithnet

# Boot
cd backend && (DATABASE_URL=$DATABASE_URL npm run dev > /tmp/p2s4-smoke.log 2>&1 &)
sleep 8

# Register + login + create a channel via the API
SMOKE_EMAIL="slice4-smoke-$(date +%s)@example.com"
curl -s -X POST http://localhost:3030/api/auth/register \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$SMOKE_EMAIL\",\"password\":\"password123\",\"displayName\":\"S4Smoke\"}" > /dev/null

RESPONSE=$(curl -s -i -X POST http://localhost:3030/api/auth/login \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$SMOKE_EMAIL\",\"password\":\"password123\"}")
TOKEN=$(echo "$RESPONSE" | grep -oE 'smithnet_access=[^;]+' | head -1 | cut -d= -f2)

CHANNEL_NAME="smoke-channel-$(date +%s)"
curl -s -X POST http://localhost:3030/api/channels \
  -H 'Content-Type: application/json' \
  -H "Cookie: smithnet_access=$TOKEN" \
  -d "{\"name\":\"$CHANNEL_NAME\",\"type\":\"broadcast\"}" \
  | head -c 200

# Verify pg row
psql "$DATABASE_URL" -c "SELECT name FROM channels WHERE name = '$CHANNEL_NAME';"

# Kill backend
pkill -f "tsx watch src/server.ts" 2>/dev/null
pkill -f "node.*server.ts" 2>/dev/null
sleep 3

# Restart
cd backend && (DATABASE_URL=$DATABASE_URL npm run dev > /tmp/p2s4-smoke-restart.log 2>&1 &)
sleep 8

# List channels — the pre-restart one should still appear
curl -s -H "Cookie: smithnet_access=$TOKEN" http://localhost:3030/api/channels \
  | grep -o "$CHANNEL_NAME"

# Stop
pkill -f "tsx watch src/server.ts" 2>/dev/null
pkill -f "node.*server.ts" 2>/dev/null
```

Expected:
- pg query after create returns 1 row
- After kill + restart, `GET /api/channels` includes the channel name (proves boot-time pg load worked)

If the channel name appears in the post-restart list, the slice is shippable. Note the result.

If `POST /api/channels` requires fields the curl call doesn't pass, adjust — read `api.ts` for the actual route + payload shape. The smoke is about end-to-end persistence, not perfect curl syntax.

### Step 3: Annotate weak point #5 in audit doc

Find the heading in `docs/smith-net-architecture-audit.md`:

```bash
grep -n "channelRegistry\|gatewayManager\|routing" docs/smith-net-architecture-audit.md | head -10
```

Find the weak point #5 heading. Use Task 2's commit SHA (the `feat(channels): persistChannel + async create()...` commit). Run:

```bash
git log --oneline --grep="persistChannel" | head -1
```

Use Edit to append the annotation:

```markdown
### 5. ... [closed in slice 4, commit <SHA>]
```

### Step 4: Commit + tag

```bash
git add docs/smith-net-architecture-audit.md
git commit -m "chore(phase-2): close slice 4 — channelRegistry + gatewayManager persistence

- channels table is write-through pg cache; initialize() loads on boot
- gateway_sessions table holds relay metadata; 5min TTL on boot load
- WS reference rebuilt only when a relay reconnects post-restart
- audit weak point #5 marked closed"
git tag -a phase-2-slice-4 -m "Phase 2 Slice 4 — registry persistence (audit weak point #5)"
git tag -a phase-2 -m "Phase 2 complete — weak points #1, #4, #5, #6 closed"
```

The `phase-2` tag marks the whole phase as shipped. Phase 3 starts after this.

### Step 5: Verify

```bash
git log --oneline phase-2-slice-3..phase-2-slice-4
git tag --list 'phase-2*'
```

Expected: 4 slice tags + 1 phase tag = 5 phase-2 tags.

---

## What slice 4 did NOT do

- Did **not** add a cleanup daemon for stale `gateway_sessions`. Phase 3.
- Did **not** persist `channel_members` to the legacy table — used JSONB on `channels` instead.
- Did **not** add channel-list pagination — for now `initialize()` loads all non-deleted channels.

## Phase 2 complete

After Slice 4 ships:
- Backend restart loses NO state: users, channels, gateway sessions, audit chain all in pg
- WS auth is JWT-enforced at upgrade
- Audit chain is queryable in pg with SHA chain integrity
- 5 weak points from the audit (#1, #4, #5, #6) are closed
- The remaining audit work (#2 background-job system, #3 dead Phase-0 routes, #7-#10) is Phases 3-5
