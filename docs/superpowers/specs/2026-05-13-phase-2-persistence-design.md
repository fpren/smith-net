# Phase 2 — Persistence + Structured Logs

**Date:** 2026-05-13
**Roadmap reference:** `docs/smith-net-implementation-roadmap.md` § Phase 2
**Predecessor tag:** `audit-2026-05-13`
**Target ship tag:** `phase-2-<date>` (~3 weeks)
**Status:** approved design, ready for implementation plan

---

## Goal

Eliminate in-memory state that the backend loses on restart. Move the audit chain into Postgres. Add JWT validation to the WebSocket upgrade. Introduce structured logging on the hottest routes. Close architecture-audit weak points #1, #4, #5, #6.

## Constraints (decided 2026-05-13)

1. **WS auth cutover is immediate and hard.** No dual-accept window. Old query-param `userId` path is deleted, not deprecated. The Android client must ship the matching cookie change in the same release window. This is safe because Android is the user's own client and the desktop portal is not yet on WebSocket.
2. **Logger: pino, hot paths only.** Adopt pino as the structured logger. Replace `console.log` in five hot modules only (`authRoutes.ts`, `jobsRoutes.ts`, `wsHandler.ts`, `usersService.ts`, `auditLog.ts`). Leave the other 35 backend modules on `console.log` and convert opportunistically as later phases touch them.
3. **Scope envelope: full roadmap Phase 2.** All six changes ship: users service, audit pg, channelRegistry persistence, gatewayManager persistence, WS JWT cutover, pino. No further trimming.
4. **Sequencing: vertical slices, weak-point priority.** Order is (1) users + FK drift, (2) audit_entries + pino, (3) WS JWT cutover, (4) channel/gateway persistence. Each slice ships independently. If Phase 3 becomes urgent, slices 1-2 can land alone and 3-4 can defer cleanly.

## Architecture

Phase 2 transforms 5 existing modules and adds 4 migrations, 2 new files (`usersService.ts`, `log.ts`), and 4 integration test files. No new processes; no new dependencies beyond `pino`.

### Migrations

The migration sequence picks up after `004_jobs_coords.sql` (shipped in Plan 4).

| # | File | Purpose |
|---|---|---|
| 005 | `005_users_table.sql` | `users` table replacing in-memory `UserStore` Map |
| 006 | `006_audit_entries.sql` | `audit_entries` table replacing JSONL-only audit |
| 007 | `007_channels.sql` | `channels` + `channel_members` for `channelRegistry` persistence |
| 008 | `008_gateway_sessions.sql` | `gateway_sessions` for `gatewayManager` persistence |

### New backend layer

```
backend/src/
├── usersService.ts       NEW — async pg-backed wrapper, same public API as UserStore
├── log.ts                NEW — pino factory + request-scoped child loggers
├── auth.ts               REFACTORED — userStore becomes thin delegate
├── auditLog.ts           REFACTORED — append() writes pg sync + buffers JSONL
├── wsHandler.ts          REFACTORED — JWT cookie validation on upgrade
├── channelRegistry.ts    REFACTORED — read-through pg cache
└── gatewayManager.ts     REFACTORED — persist relay metadata, drop stale on boot
```

### Slice 1 — Users service + FK drift fix

**Weak point closed:** #1 (`userStore` ↔ `profiles` FK drift, latent admin-001 bug from Plan 2).

**Migration `005_users_table.sql`:**

```sql
CREATE TABLE users (
  id              TEXT PRIMARY KEY,
  email_lower     TEXT UNIQUE NOT NULL,
  password_hash   TEXT NOT NULL,
  role            TEXT NOT NULL DEFAULT 'user',
  failed_attempts INTEGER NOT NULL DEFAULT 0,
  locked_until    TIMESTAMPTZ,
  email_verified_at TIMESTAMPTZ,
  refresh_tokens  JSONB NOT NULL DEFAULT '[]'::jsonb,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX users_email_lower_idx ON users (email_lower);

-- Seed the existing in-memory admin so the cutover doesn't break login.
-- Password hash must match what auth.ts produces today.
-- (Actual hash supplied at migration write-time, not committed here.)
INSERT INTO users (id, email_lower, password_hash, role)
VALUES ('admin-001', '<admin-email>', '<existing-hash>', 'admin');

-- Also align profiles row so jobsService transaction succeeds.
UPDATE profiles SET id = 'admin-001' WHERE id = 'admin';
```

**`usersService.ts`:** new ~250 LOC. Exports the same public API as the current `UserStore` class — `register`, `findByEmail`, `getUserById`, `validatePassword`, `storeRefreshToken`, `validateRefreshToken`, `revokeRefreshToken`, `recordFailedAttempt`, `clearFailedAttempts`, `isLocked`. All methods become `async`. Same hashing (bcrypt cost 12) and lockout rules (5 attempts, 15-min lockout).

**`auth.ts` refactor:** the existing `userStore` export becomes a thin delegate to `usersService`. The `UserStore` class is removed. Every caller (~30 sites in `authRoutes.ts`, `wsHandler.ts`, etc.) adds `await` to method calls.

**`jobsService.ts.create()`:** wrap user-create + profile-create in `pool.transaction()`. If either fails, both roll back. This is the structural fix that makes weak point #1 impossible to reintroduce.

### Slice 2 — audit_entries + pino

**Weak point closed:** #6 (audit JSONL only, not queryable).

**Migration `006_audit_entries.sql`** — schema from `smith-net-daemon-worker-queue-plan.md`:

```sql
CREATE TABLE audit_entries (
  id          BIGSERIAL PRIMARY KEY,
  actor_id    TEXT NOT NULL,
  route       TEXT NOT NULL,
  action      TEXT NOT NULL,
  payload     JSONB,
  prev_hash   TEXT,
  hash        TEXT NOT NULL,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX audit_entries_actor_idx ON audit_entries (actor_id, created_at DESC);
CREATE INDEX audit_entries_route_idx ON audit_entries (route, created_at DESC);
```

**`auditLog.ts` refactor:** `append()` writes synchronously to pg (Phase 2 interim). JSONL becomes a cold backup — buffered in memory, flushed once per minute via a single `setInterval`. The SHA256 chain logic stays identical; `prev_hash` is the previous row's `hash`. Phase 3 will replace the sync pg write with `enqueue({kind: 'audit_flush'})`.

**`log.ts` new:**

```ts
import pino from 'pino';
import { AsyncLocalStorage } from 'async_hooks';

const als = new AsyncLocalStorage<{ req_id: string; actor_id?: string; route?: string }>();
export const baseLogger = pino({ level: process.env.LOG_LEVEL ?? 'info' });

export function requestLogger() {
  const ctx = als.getStore();
  return ctx ? baseLogger.child(ctx) : baseLogger;
}

export function withRequestContext<T>(ctx: { req_id: string; actor_id?: string; route?: string }, fn: () => T): T {
  return als.run(ctx, fn);
}
```

Express middleware in `server.ts` calls `withRequestContext` per request, attaching `req_id` (uuid), `actor_id` (from JWT if present), and `route` (`req.method + req.path`).

**Hot-path migration:** the 5 hot modules listed above swap `console.log(...)` for `requestLogger().info({...}, '...')`. Other modules unchanged.

### Slice 3 — WS JWT cutover

**Weak point closed:** #4 (WS legacy auth, client-claimed `userId`).

**`wsHandler.ts` refactor:**

```
HTTP upgrade -> verifyClient(info, cb) -> parse Cookie header
                                       -> validate smithnet_access JWT
                                       -> attach { userId, userName } to ws
                                       -> on accept: socket carries identity from token
```

The existing `handleAuth(ws, payload)` method is deleted. Client cannot supply a `userId` in any message; identity is always from the JWT.

**Android cutover risk:** OkHttp WebSocket client must attach the `smithnet_access` cookie on the upgrade request. If today's Android client builds the WS URL with query-param `?userId=`, that needs to change to a cookie header. To be verified during slice 3 implementation — flagged as a known risk.

### Slice 4 — channelRegistry + gatewayManager persistence

**Weak point closed:** #5 (routing state in-memory).

**Migration `007_channels.sql`:**

```sql
CREATE TABLE channels (
  id              TEXT PRIMARY KEY,
  name            TEXT NOT NULL,
  type            TEXT NOT NULL,
  visibility      TEXT NOT NULL DEFAULT 'public',
  creator_id      TEXT NOT NULL,
  requires_approval BOOLEAN NOT NULL DEFAULT FALSE,
  is_archived     BOOLEAN NOT NULL DEFAULT FALSE,
  is_deleted      BOOLEAN NOT NULL DEFAULT FALSE,
  mesh_hash       INTEGER NOT NULL,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE channel_members (
  channel_id      TEXT NOT NULL REFERENCES channels(id) ON DELETE CASCADE,
  user_id         TEXT NOT NULL,
  joined_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (channel_id, user_id)
);

CREATE INDEX channels_mesh_hash_idx ON channels (mesh_hash);
CREATE INDEX channel_members_user_idx ON channel_members (user_id);
```

No TTL — channels are durable metadata.

**Migration `008_gateway_sessions.sql`:**

```sql
CREATE TABLE gateway_sessions (
  id              TEXT PRIMARY KEY,
  name            TEXT NOT NULL,
  capabilities    JSONB NOT NULL DEFAULT '[]'::jsonb,
  last_activity   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX gateway_sessions_last_activity_idx ON gateway_sessions (last_activity);
```

TTL semantics: rows where `last_activity < now() - interval '5 minutes'` are considered dead and not loaded into memory on boot. A cleanup job (Phase 3) deletes them. For Phase 2, dead rows stay in the table; they just don't get rebuilt into the in-memory map.

**`channelRegistry.ts` refactor:** every mutation writes through to pg. On boot, the constructor loads all non-deleted channels into the Map. Read paths still hit the Map (no per-read DB).

**`gatewayManager.ts` refactor:** `register()` inserts a row. `updateActivity()` updates `last_activity`. `unregister()` deletes the row. On boot, load rows where `last_activity > now() - interval '5 minutes'` into the Map. The `ws` reference is per-process — relays must reconnect to repopulate it.

## Testing

Each slice ships with one integration test file using the real Postgres via `DATABASE_URL_TEST`.

| Slice | Test file | Cases |
|---|---|---|
| 1 | `usersService.test.ts` | round-trip (create → login → restart pool → login), lockout after 5 attempts, refresh-token revoke, jobsService create transaction rollback on profile failure |
| 2 | `auditEntries.test.ts` | 10 sequential appends, recompute chain hash, assert match; pino output shape (req_id, actor_id, route present) |
| 3 | `wsJwtAuth.test.ts` | valid cookie upgrades, missing cookie returns 401, expired JWT returns 401, refresh-token-only cookie returns 401 |
| 4 | `channelGatewayPersistence.test.ts` | create channel → drop+reopen pool → channel still listed; relay register → heartbeat → 6-min wait → not loaded on next boot |

Pre-existing tests must continue to pass. The `userStore` -> `usersService` rename touches every test seed; update test helpers in lockstep.

## Risks

1. **Android cookie attachment on WS upgrade.** OkHttp's `Request.Builder()` for a WebSocket may not auto-attach cookies the way a browser does. If not, Android needs a one-line `header("Cookie", ...)` config. Verify during slice 3 implementation. If broken, slice 3 is paused until Android ships the matching change.
2. **`UserStore` is referenced from ~30 call sites.** Making every method `async` is mechanical but voluminous. High line-count diff in slice 1. Subagent reviews should focus on missed `await` rather than logic changes.
3. **admin-001 seed.** Migration 005 must seed the existing in-memory admin or post-cutover login fails. The password hash in the seed INSERT must match what `auth.ts` currently produces (bcrypt rounds 12 of the admin's known password). This needs a human-supplied value at migration-write time.
4. **pino + Jest.** pino's default transport spawns a worker thread, which occasionally interacts poorly with Jest's test isolation. Mitigation: in test environments, configure pino with `{ transport: { target: 'pino/file', options: { destination: 1 } } }` or use a sync stream.
5. **JSONL cold-backup buffering.** Phase 2's `auditLog.ts` writes to pg synchronously AND buffers JSONL with a 1-minute flush. If the process crashes between flushes, up to 60 seconds of JSONL is lost — but pg has all the data, so the cold backup just falls behind, not loses authoritative state. Acceptable.

## Done criteria

- All 4 slices merged into the feature branch
- Repo tagged `phase-2-<actual-ship-date>`
- `smith-net-architecture-audit.md` annotated: weak points #1, #4, #5, #6 marked `[closed in phase-2-<date>]`
- Restart smoke test passes manually:
  - existing user logs in after backend restart
  - existing channel still appears in `GET /api/channels`
  - relay that was registered before restart reconnects within 5 minutes and is in the active set
- 4 new integration test files green in CI
- pino active on the 5 hot modules; every log line they emit carries `req_id`, `actor_id` (when authenticated), and `route`
- No regression in existing test suite

## What Phase 2 does NOT do

- No background-job system. Audit pg writes stay synchronous in Phase 2 (Phase 3 moves them behind the queue).
- No daemons. Stale `gateway_sessions` rows accumulate; Phase 3's `cleanupDaemon` removes them.
- No LLM work. That is Phase 5.
- No API.ts split. That is Phase 4.
- No structured logging on the other 35 modules. They stay on `console.log` until later phases touch them.

## Cross-reference

| Audit weak point | Closed in slice |
|---|---|
| #1 userStore ↔ profiles FK drift | 1 |
| #4 WS legacy auth | 3 |
| #5 channelRegistry + gatewayManager in-memory | 4 |
| #6 auditLog.ts JSONL only | 2 (interim sync pg write) |

Weak points #2, #3, #7-#10 are out of scope for Phase 2 and addressed in Phases 3-5.
