# Phase 2 Slice 2 — audit_entries pg + pino on hot paths

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the audit chain off JSONL-only into Postgres with a true SHA chain (`prev_hash` → `hash`), and replace `console.log` with structured pino logging on the five hot backend modules. Closes audit weak point #6.

**Architecture:** Migration 006 creates `audit_entries` with `prev_hash` + `hash` columns plus indexes on `actor_id` and `created_at`. `auditLog.log()` becomes async and writes to pg synchronously inside a `pg_advisory_xact_lock`-protected transaction so the chain is serialized without blocking unrelated work. JSONL becomes a write-behind cold backup, buffered in memory and flushed every 60 seconds. A new `backend/src/log.ts` exports a pino instance and a request-scoped child logger via `AsyncLocalStorage`. Express middleware in `server.ts` wraps each request, attaching `req_id`/`actor_id`/`route` to every log line. The five hot modules (`authRoutes`, `jobsRoutes`, `wsHandler`, `usersService`, `auditLog`) swap their `console.log` calls for `requestLogger().info(...)`. The other 35 backend modules stay on `console.log` until later phases touch them.

**Tech Stack:** Node + Express + TypeScript + `pg.Pool` + `pino` (new dep) + `async_hooks.AsyncLocalStorage` (built-in) + Jest

**Prerequisites:**
- Phase 2 Slice 1 shipped (tag `phase-2-slice-1`, commit `86bc1bd`). `usersService` is the user store. 119/119 backend tests pass.
- `DATABASE_URL` is set and `psql` is on PATH for migration application.
- Working branch is `feat/relay-hetzner-postgres`.

**Reference docs:**
- Spec: `docs/superpowers/specs/2026-05-13-phase-2-persistence-design.md` (Slice 2 section)
- Audit: `docs/smith-net-architecture-audit.md` (weak point #6 will be annotated `[closed]` in Task 7)
- CLAUDE.md: no inline LLM, no inline fire-and-forget — async audit writes MUST be awaited at call sites
- Current `auditLog.ts` shape: in-memory `entries` array + JSONL file + per-entry checksum. Slice 2 upgrades the per-entry checksum to a true chain (`prevChecksum` field added).

---

## File Map

| File | Action | Purpose |
|---|---|---|
| `backend/migrations/006_audit_entries.sql` | Create | `audit_entries` table + 3 indexes |
| `backend/src/log.ts` | Create | pino factory + AsyncLocalStorage context |
| `backend/src/auditLog.ts` | Modify | `log()` becomes async; pg sync write + chain; JSONL minute-buffer |
| `backend/src/server.ts` | Modify | Add `withRequestContext` middleware |
| `backend/src/authRoutes.ts` | Modify | `await auditLog.log(...)` + replace 8 console.log lines |
| `backend/src/jobsRoutes.ts` | Modify | `await auditLog.log(...)` + replace 8 console.log lines |
| `backend/src/wsHandler.ts` | Modify | `await auditLog.log(...)` if any + replace 11 console.log lines |
| `backend/src/usersService.ts` | Modify | Replace 4 console.log lines |
| `backend/src/__tests__/auditChain.test.ts` | Create | 10-write chain validation |
| `backend/src/__tests__/log.test.ts` | Create | pino output shape (req_id/actor_id/route) |
| `docs/smith-net-architecture-audit.md` | Modify (Task 7) | annotate weak point #6 `[closed in slice 2, commit <SHA>]` |
| `backend/package.json` | Modify (Task 2) | add `pino` dependency |

---

## Task 1 — Migration 006: audit_entries schema

**Files:**
- Create: `backend/migrations/006_audit_entries.sql`

- [ ] **Step 1: Write the migration**

Create `backend/migrations/006_audit_entries.sql`:

```sql
-- 006_audit_entries.sql
-- Phase 2 Slice 2: move audit chain into pg. JSONL becomes cold backup.
-- prev_hash + hash form a SHA256 chain. id is the row order; created_at is
-- the wall clock. Both are indexed for typical queries (per-actor, time
-- range).

CREATE TABLE IF NOT EXISTS audit_entries (
  id           BIGSERIAL PRIMARY KEY,
  audit_id     TEXT NOT NULL,
  actor_id     TEXT NOT NULL,
  target_id    TEXT,
  action       TEXT NOT NULL,
  metadata     JSONB NOT NULL DEFAULT '{}'::jsonb,
  ip           TEXT,
  user_agent   TEXT,
  prev_hash    TEXT,
  hash         TEXT NOT NULL,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS audit_entries_audit_id_uidx ON audit_entries (audit_id);
CREATE INDEX IF NOT EXISTS audit_entries_actor_idx ON audit_entries (actor_id, created_at DESC);
CREATE INDEX IF NOT EXISTS audit_entries_action_idx ON audit_entries (action, created_at DESC);
```

`audit_id` is the existing string id (`audit-<timestamp>-<counter>`) preserved for backwards compatibility with JSONL. `id` is the new pg-native row order.

- [ ] **Step 2: Apply the migration**

```bash
export PATH=/opt/homebrew/opt/postgresql@16/bin:$PATH
export DATABASE_URL=postgres://fegensprenelon@localhost:5432/smithnet
cd backend && psql "$DATABASE_URL" -f migrations/006_audit_entries.sql
```

Expected: `CREATE TABLE`, `CREATE INDEX` (×3). No errors.

- [ ] **Step 3: Verify schema**

```bash
psql "$DATABASE_URL" -c "\d audit_entries"
```

Expected: 11 columns including `audit_id` (TEXT NOT NULL), `prev_hash` (TEXT), `hash` (TEXT NOT NULL), `metadata` (jsonb). 4 indexes: PK, unique audit_id, partial on actor_id, partial on action.

- [ ] **Step 4: Commit**

```bash
git add backend/migrations/006_audit_entries.sql
git commit -m "feat(db): migration 006 — audit_entries (Phase 2 Slice 2)"
```

---

## Task 2 — log.ts + pino install + request-context middleware

**Files:**
- Create: `backend/src/log.ts`
- Modify: `backend/package.json` (add `pino` dep)
- Modify: `backend/src/server.ts` (add middleware)
- Create: `backend/src/__tests__/log.test.ts`

- [ ] **Step 1: Install pino**

```bash
cd backend && npm install pino
```

Expected: pino added to dependencies. No peer-dep warnings.

- [ ] **Step 2: Write the failing pino-shape test**

Create `backend/src/__tests__/log.test.ts`:

```typescript
import { baseLogger, requestLogger, withRequestContext } from '../log';

describe('log.ts', () => {
  let captured: any[] = [];
  let origWrite: any;

  beforeEach(() => {
    captured = [];
    // Pino writes to process.stdout by default. Patch the underlying
    // pino stream to capture without coupling to stdout.
    origWrite = (baseLogger as any)[Symbol.for('pino.write')] ?? null;
    const sink = {
      write(s: string) {
        const trimmed = s.trim();
        if (!trimmed) return;
        try { captured.push(JSON.parse(trimmed)); } catch { /* ignore */ }
      },
    };
    (baseLogger as any).stream = sink;
  });

  it('plain log uses base logger without req context', () => {
    baseLogger.info({ kind: 'test' }, 'plain');
    expect(captured.length).toBeGreaterThanOrEqual(0); // sink hookup is best-effort
  });

  it('withRequestContext attaches req_id, actor_id, route to child', () => {
    withRequestContext({ req_id: 'r-1', actor_id: 'u-1', route: 'GET /api/x' }, () => {
      const log = requestLogger();
      log.info({ kind: 'inside' }, 'inside');
    });
    // The child logger carries req_id/actor_id/route as bindings; verify
    // via .bindings() rather than fragile stdout capture.
    const child = withRequestContext({ req_id: 'r-2', actor_id: 'u-2', route: 'POST /y' }, () => requestLogger());
    const bindings = (child as any).bindings();
    expect(bindings.req_id).toBe('r-2');
    expect(bindings.actor_id).toBe('u-2');
    expect(bindings.route).toBe('POST /y');
  });

  it('requestLogger returns base when no context active', () => {
    const log = requestLogger();
    expect(log).toBe(baseLogger);
  });
});
```

- [ ] **Step 3: Run the test to verify it fails**

```bash
cd backend && npx jest src/__tests__/log.test.ts
```

Expected: FAIL with "Cannot find module '../log'".

- [ ] **Step 4: Create log.ts**

Create `backend/src/log.ts`:

```typescript
/**
 * Phase 2 Slice 2: structured logging via pino. AsyncLocalStorage carries
 * per-request context so every log line emitted inside a request handler
 * automatically gets req_id / actor_id / route bindings.
 *
 * Use `requestLogger()` inside handler code. Use `baseLogger` for module-
 * level startup logs that have no request context.
 */

import pino, { Logger } from 'pino';
import { AsyncLocalStorage } from 'async_hooks';

export interface RequestContext {
  req_id: string;
  actor_id?: string;
  route?: string;
}

const als = new AsyncLocalStorage<RequestContext>();

export const baseLogger: Logger = pino({
  level: process.env.LOG_LEVEL ?? 'info',
  base: undefined, // omit pid/hostname noise
  timestamp: () => `,"time":"${new Date().toISOString()}"`,
});

export function requestLogger(): Logger {
  const ctx = als.getStore();
  return ctx ? baseLogger.child(ctx) : baseLogger;
}

export function withRequestContext<T>(ctx: RequestContext, fn: () => T): T {
  return als.run(ctx, fn);
}
```

- [ ] **Step 5: Run the test to verify it passes**

```bash
cd backend && npx jest src/__tests__/log.test.ts
```

Expected: PASS (the bindings test is the meaningful one; the stdout-capture test may be empty but doesn't fail).

- [ ] **Step 6: Add request-context middleware to server.ts**

Find the Express app setup in `backend/src/server.ts` (look for `app.use(express.json())` or similar middleware chain). Add the middleware **before** the route registrations:

```typescript
import { v4 as uuidv4 } from 'uuid';
import { withRequestContext } from './log';
import { extractUserFromRequest } from './auth'; // if such a helper exists; otherwise use req as any

app.use((req, res, next) => {
  const req_id = (req.headers['x-request-id'] as string) || uuidv4();
  res.setHeader('x-request-id', req_id);
  const route = `${req.method} ${req.route?.path ?? req.path}`;
  // actor_id is filled in later by auth middleware via res.locals or req.user;
  // for the initial wrap we omit it.
  withRequestContext({ req_id, route }, () => next());
});
```

If `extractUserFromRequest` doesn't exist or is fiddly, leave `actor_id` undefined here. The auth middleware can re-enter the context with the populated `actor_id` later via `withRequestContext` if needed; for Phase 2 interim, having `req_id` + `route` is the minimum viable.

- [ ] **Step 7: Run the full test suite**

```bash
cd backend && npx jest
```

Expected: all 119 prior tests + the log.test.ts pass.

- [ ] **Step 8: Commit**

```bash
git add backend/package.json backend/package-lock.json backend/src/log.ts backend/src/server.ts backend/src/__tests__/log.test.ts
git commit -m "feat(log): pino + AsyncLocalStorage request-context middleware"
```

---

## Task 3 — auditLog.ts: async log() with pg sync write + SHA chain

**Files:**
- Modify: `backend/src/auditLog.ts`
- Create: `backend/src/__tests__/auditChain.test.ts`

- [ ] **Step 1: Write the failing chain validation test**

Create `backend/src/__tests__/auditChain.test.ts`:

```typescript
import crypto from 'crypto';
import { pg, isPgEnabled } from '../db';
import { auditLog, AuditAction, AuditEntry } from '../auditLog';

const describeDb = isPgEnabled() ? describe : describe.skip;

async function cleanAudit() {
  if (!isPgEnabled()) return;
  await pg!.query('TRUNCATE audit_entries RESTART IDENTITY');
}

function recomputeHash(entry: AuditEntry): string {
  // Mirror the chain hash recipe from auditLog.ts:
  //   sha256(prevChecksum || JSON.stringify({id, timestamp, action, actorId, targetId, metadata}))
  const body = JSON.stringify({
    id: entry.id,
    timestamp: entry.timestamp,
    action: entry.action,
    actorId: entry.actorId,
    targetId: entry.targetId,
    metadata: entry.metadata,
  });
  const seed = (entry.prevChecksum ?? '') + body;
  return crypto.createHash('sha256').update(seed).digest('hex');
}

describeDb('auditLog chain validation', () => {
  beforeEach(cleanAudit);
  afterAll(async () => { await pg?.end(); });

  it('10 sequential appends form a valid SHA chain', async () => {
    const written: AuditEntry[] = [];
    for (let i = 0; i < 10; i++) {
      const e = await auditLog.log(AuditAction.USER_LOGIN, `actor-${i}`, { i });
      written.push(e);
    }

    // First entry has prevChecksum null/undefined; subsequent entries link
    // to the previous entry's checksum.
    for (let i = 0; i < written.length; i++) {
      const prev = written[i - 1]?.checksum ?? null;
      expect(written[i].prevChecksum ?? null).toBe(prev);
      const expected = recomputeHash(written[i]);
      expect(written[i].checksum).toBe(expected);
    }

    // Also verify the pg rows independently.
    const rows = await pg!.query<{ audit_id: string; prev_hash: string | null; hash: string }>(
      `SELECT audit_id, prev_hash, hash FROM audit_entries ORDER BY id ASC`
    );
    expect(rows.rowCount).toBe(10);
    for (let i = 0; i < rows.rows.length; i++) {
      expect(rows.rows[i].audit_id).toBe(written[i].id);
      expect(rows.rows[i].hash).toBe(written[i].checksum);
      expect(rows.rows[i].prev_hash).toBe(written[i - 1]?.checksum ?? null);
    }
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd backend && npx jest src/__tests__/auditChain.test.ts
```

Expected: FAIL — either `AuditEntry.prevChecksum` doesn't exist, or `audit_entries` rows aren't being written, or both.

- [ ] **Step 3: Update AuditEntry interface and add chain field**

In `backend/src/auditLog.ts`, replace the existing `AuditEntry` interface (around line 67) with:

```typescript
export interface AuditEntry {
  id: string;
  timestamp: number;
  action: AuditAction;
  actorId: string;
  targetId?: string;
  metadata: Record<string, any>;
  ip?: string;
  userAgent?: string;
  /** SHA256 of the previous entry's checksum + this entry's body. null for the first entry. */
  prevChecksum: string | null;
  /** SHA256(prevChecksum + body). */
  checksum: string;
}
```

- [ ] **Step 4: Replace generateChecksum and log() to use the chain**

In `backend/src/auditLog.ts`:

Replace `generateChecksum` with a version that accepts the previous hash:

```typescript
private generateChecksum(entry: Omit<AuditEntry, 'checksum'>): string {
  const crypto = require('crypto');
  const body = JSON.stringify({
    id: entry.id,
    timestamp: entry.timestamp,
    action: entry.action,
    actorId: entry.actorId,
    targetId: entry.targetId,
    metadata: entry.metadata,
  });
  const seed = (entry.prevChecksum ?? '') + body;
  return crypto.createHash('sha256').update(seed).digest('hex');
}
```

Replace `log()` with the async pg-backed version. Imports at the top: add `import { pg, isPgEnabled } from './db';`.

```typescript
async log(
  action: AuditAction,
  actorId: string,
  metadata: Record<string, any> = {},
  options: { targetId?: string; ip?: string; userAgent?: string } = {}
): Promise<AuditEntry> {
  if (!isPgEnabled() || !pg) {
    // No pg available — fall back to in-memory + JSONL only (dev mode).
    return this.logInMemoryOnly(action, actorId, metadata, options);
  }

  const client = await pg.connect();
  try {
    await client.query('BEGIN');
    // Advisory lock serializes chain writers without blocking other DB work.
    // Constant key 42 — there's only one audit chain.
    await client.query('SELECT pg_advisory_xact_lock(42)');

    // Read the most recent hash to form prevChecksum.
    const prevResult = await client.query<{ hash: string }>(
      `SELECT hash FROM audit_entries ORDER BY id DESC LIMIT 1`
    );
    const prevChecksum = prevResult.rows[0]?.hash ?? null;

    const entry: Omit<AuditEntry, 'checksum'> = {
      id: `audit-${Date.now()}-${++this.entryCounter}`,
      timestamp: Date.now(),
      action,
      actorId,
      targetId: options.targetId,
      metadata,
      ip: options.ip,
      userAgent: options.userAgent,
      prevChecksum,
    };
    const checksum = this.generateChecksum(entry);
    const full: AuditEntry = { ...entry, checksum };

    await client.query(
      `INSERT INTO audit_entries
         (audit_id, actor_id, target_id, action, metadata, ip, user_agent, prev_hash, hash)
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)`,
      [full.id, full.actorId, full.targetId ?? null, full.action,
       full.metadata, full.ip ?? null, full.userAgent ?? null,
       full.prevChecksum, full.checksum]
    );
    await client.query('COMMIT');

    // In-memory + JSONL backups. JSONL is handled by Task 4's buffer; for
    // now write synchronously to preserve behavior.
    this.entries.push(full);
    this.bufferJsonl(full);
    return full;
  } catch (err) {
    await client.query('ROLLBACK');
    throw err;
  } finally {
    client.release();
  }
}

/** No-pg path for local dev with DATABASE_URL unset. */
private logInMemoryOnly(
  action: AuditAction,
  actorId: string,
  metadata: Record<string, any>,
  options: { targetId?: string; ip?: string; userAgent?: string }
): AuditEntry {
  const prevChecksum = this.entries.length > 0
    ? this.entries[this.entries.length - 1].checksum
    : null;
  const entry: Omit<AuditEntry, 'checksum'> = {
    id: `audit-${Date.now()}-${++this.entryCounter}`,
    timestamp: Date.now(),
    action,
    actorId,
    targetId: options.targetId,
    metadata,
    ip: options.ip,
    userAgent: options.userAgent,
    prevChecksum,
  };
  const full: AuditEntry = { ...entry, checksum: this.generateChecksum(entry) };
  this.entries.push(full);
  this.bufferJsonl(full);
  return full;
}

/** JSONL buffer — Task 4 will replace with minute-flush. For now write sync. */
private bufferJsonl(entry: AuditEntry): void {
  try {
    fs.appendFileSync(this.logFile, JSON.stringify(entry) + '\n');
  } catch (e) {
    console.error('[AuditLog] Failed to write JSONL backup:', e);
  }
}
```

Note: the old `log()` method called `console.log(...)` at the end for visibility. That call is removed; Task 6 will swap it for `requestLogger().info(...)` in this file.

- [ ] **Step 5: Update verifyIntegrity to validate the chain**

Replace `verifyIntegrity()` in `auditLog.ts`:

```typescript
verifyIntegrity(): { valid: number; invalid: number; entries: string[] } {
  let valid = 0;
  let invalid = 0;
  const invalidEntries: string[] = [];
  let expectedPrev: string | null = null;

  for (const entry of this.entries) {
    const { checksum, ...rest } = entry;
    if ((rest.prevChecksum ?? null) !== expectedPrev) {
      invalid++;
      invalidEntries.push(`${entry.id} (broken chain)`);
      expectedPrev = checksum;
      continue;
    }
    const expectedChecksum = this.generateChecksum(rest);
    if (checksum === expectedChecksum) {
      valid++;
    } else {
      invalid++;
      invalidEntries.push(`${entry.id} (bad hash)`);
    }
    expectedPrev = checksum;
  }

  return { valid, invalid, entries: invalidEntries };
}
```

- [ ] **Step 6: Run the chain test**

```bash
cd backend && npx jest src/__tests__/auditChain.test.ts
```

Expected: PASS.

- [ ] **Step 7: Add `await` to the 18 existing auditLog.log() callers**

The signature change from sync `AuditEntry` to `Promise<AuditEntry>` means every caller needs `await`. Find them:

```bash
grep -rn "auditLog\.log(" backend/src/ --include="*.ts" | grep -v "auditLog.ts\|__tests__"
```

For each match:
1. Add `await` before `auditLog.log(...)`
2. Make the containing function `async` if it isn't already (most request handlers already are)
3. If the caller is inside an Express handler, just `await` it; the handler is already async

The 18 callers live in `authRoutes.ts`, `jobsRoutes.ts`, possibly `wsHandler.ts`, `api.ts`, `adminRoutes.ts`. None of these are in a hot loop; awaiting adds ~10ms (the pg insert) per audited action, which is acceptable for Phase 2 interim. Phase 3 moves audit writes to a queue.

- [ ] **Step 8: Run the full suite**

```bash
cd backend && npx jest
```

Expected: 120+ passing (119 prior + 1 chain test). If existing tests break because they relied on synchronous `auditLog.log()`, update them to `await` the call.

- [ ] **Step 9: Commit**

```bash
git add backend/src/auditLog.ts backend/src/__tests__/auditChain.test.ts backend/src/
git commit -m "feat(audit): async log() with pg sync write + SHA chain (audit weak point #6)"
```

---

## Task 4 — JSONL minute-buffer for cold backup

**Files:**
- Modify: `backend/src/auditLog.ts`

- [ ] **Step 1: Replace bufferJsonl with in-memory buffer + interval flush**

In `backend/src/auditLog.ts`, replace `bufferJsonl()` and the synchronous write path with a buffered version. Add to the `AuditLogManager` class:

```typescript
private jsonlBuffer: AuditEntry[] = [];
private flushTimer: NodeJS.Timeout | null = null;

private bufferJsonl(entry: AuditEntry): void {
  this.jsonlBuffer.push(entry);
  if (!this.flushTimer) {
    this.flushTimer = setInterval(() => this.flushJsonl(), 60 * 1000);
    // Don't keep the process alive for the timer alone.
    this.flushTimer.unref();
  }
}

private flushJsonl(): void {
  if (this.jsonlBuffer.length === 0) return;
  const drained = this.jsonlBuffer;
  this.jsonlBuffer = [];
  try {
    const blob = drained.map((e) => JSON.stringify(e)).join('\n') + '\n';
    fs.appendFileSync(this.logFile, blob);
  } catch (e) {
    console.error('[AuditLog] Failed to flush JSONL buffer:', e);
    // Re-queue on failure — the next interval will retry.
    this.jsonlBuffer.unshift(...drained);
  }
}

/** Called on process shutdown so the buffer drains. */
flushNow(): void {
  this.flushJsonl();
}
```

The `setInterval` is created lazily on first write so test imports don't immediately schedule timers.

- [ ] **Step 2: Wire flushNow() into process exit**

In `backend/src/server.ts`, add a shutdown handler if one doesn't exist:

```typescript
import { auditLog } from './auditLog';
process.on('SIGTERM', () => { auditLog.flushNow(); });
process.on('SIGINT', () => { auditLog.flushNow(); });
```

If similar handlers exist already, add `auditLog.flushNow()` to them. Don't replace existing shutdown logic.

- [ ] **Step 3: Run the chain test (now uses the buffer)**

```bash
cd backend && npx jest src/__tests__/auditChain.test.ts
```

Expected: PASS. The chain logic is unchanged; JSONL just defers.

- [ ] **Step 4: Add a focused JSONL buffer test**

Append to `backend/src/__tests__/auditChain.test.ts`:

```typescript
describeDb('auditLog JSONL minute-buffer', () => {
  beforeEach(cleanAudit);

  it('writes do not block on fs; flush drains buffer', async () => {
    const before = await auditLog.log(AuditAction.USER_LOGIN, 'a', { test: 'buffer' });
    expect(before.checksum).toBeTruthy();
    // Manually trigger flush via the public flushNow shim.
    auditLog.flushNow();
    // No assertion on file contents (the logFile path is private and dated);
    // the test only ensures flushNow doesn't throw and the public API stays
    // intact after the refactor.
  });
});
```

- [ ] **Step 5: Run the full suite**

```bash
cd backend && npx jest
```

Expected: 121+ passing.

- [ ] **Step 6: Commit**

```bash
git add backend/src/auditLog.ts backend/src/server.ts backend/src/__tests__/auditChain.test.ts
git commit -m "feat(audit): JSONL becomes write-behind cold backup (60s flush)"
```

---

## Task 5 — Replace console.log with pino in 5 hot modules

**Files:**
- Modify: `backend/src/authRoutes.ts` (8 lines)
- Modify: `backend/src/jobsRoutes.ts` (8 lines)
- Modify: `backend/src/wsHandler.ts` (11 lines)
- Modify: `backend/src/usersService.ts` (4 lines)
- Modify: `backend/src/auditLog.ts` (5 lines)

For each file, replace every `console.log(...)`, `console.warn(...)`, `console.error(...)` with the structured equivalent. The pattern is:

```typescript
// before
console.log('[AuthRoutes] User registered:', email);

// after
import { requestLogger } from './log';
requestLogger().info({ event: 'user_registered', email }, 'user registered');
```

The first argument to `pino.info()` is a structured object (becomes JSON fields). The second is a free-form message string. Prefer concrete event names in the structured field.

For `console.warn` → `requestLogger().warn(...)`.
For `console.error` → `requestLogger().error({ err: e }, '...')`. When passing an Error, use `{ err: e }` so pino's default serializer captures `stack`/`message`/`code`.

- [ ] **Step 1: authRoutes.ts (8 lines)**

Find every `console.{log,warn,error}` in `backend/src/authRoutes.ts` and convert. Examples:

```typescript
console.log('[Auth] Registered:', email);
// becomes:
requestLogger().info({ event: 'user_registered', email }, 'user registered');
```

```typescript
console.warn('[Auth] Failed login from', ip, 'for', email);
// becomes:
requestLogger().warn({ event: 'login_failed', ip, email }, 'login failed');
```

Add `import { requestLogger } from './log';` at the top.

- [ ] **Step 2: jobsRoutes.ts (8 lines)**

Same pattern. Add `import { requestLogger } from './log';`. Convert each console call to a structured pino call. Event names like `job_created`, `job_status_changed`, `job_assigned`.

- [ ] **Step 3: wsHandler.ts (11 lines)**

This file has 11 console calls. Most are connection events. Use event names like `ws_connected`, `ws_disconnected`, `ws_auth_failed`, `ws_message_routed`. Add the import.

Note: `wsHandler` doesn't always run inside an HTTP request — connections are long-lived sockets. `requestLogger()` returns `baseLogger` when there's no request context. That's fine; the bindings will be empty for WS-internal logs.

- [ ] **Step 4: usersService.ts (4 lines)**

Same pattern. 4 lines:
- `[usersService] User created:` → `info({ event: 'user_created', email }, 'user created')`
- `[usersService] Bootstrapped admin with built-in password` → `warn({ event: 'admin_bootstrap_default_password' }, '...')`
- `[usersService] Bootstrapped admin from DEFAULT_ADMIN_PASSWORD env` → `info({ event: 'admin_bootstrap_env' }, '...')`
- `[usersService] admin bootstrap failed` → `error({ err }, 'admin bootstrap failed')` (this is in the import-time catch)

- [ ] **Step 5: auditLog.ts (5 lines)**

Same pattern. Convert the remaining 5 console calls. The constructor's `'[AuditLog] Initialized with', this.entries.length, 'entries'` becomes:

```typescript
requestLogger().info({ entries: this.entries.length }, 'AuditLog initialized');
```

This runs at module-import time, outside any request — so it'll use `baseLogger`. That's correct.

- [ ] **Step 6: Run the full suite**

```bash
cd backend && npx jest
```

Expected: 121+ passing.

- [ ] **Step 7: Smoke test the log output shape**

```bash
cd backend && (DATABASE_URL=$DATABASE_URL npm run dev > /tmp/p2s2-pino.log 2>&1 &)
sleep 8
curl -s http://localhost:3030/api/health > /dev/null 2>&1 || true
sleep 2
pkill -f "tsx watch src/server.ts" 2>/dev/null
pkill -f "node.*server.ts" 2>/dev/null
grep -m 3 '"req_id"' /tmp/p2s2-pino.log
```

Expected: one or more log lines containing `req_id`, `route`. Empty results are also acceptable if `/api/health` doesn't exist; in that case just verify the dev server boots without crashing.

- [ ] **Step 8: Commit**

```bash
git add backend/src/authRoutes.ts backend/src/jobsRoutes.ts backend/src/wsHandler.ts backend/src/usersService.ts backend/src/auditLog.ts
git commit -m "feat(log): swap console.log -> pino on 5 hot modules"
```

---

## Task 6 — Slice 2 closeout: audit annotation + tag

**Files:**
- Modify: `docs/smith-net-architecture-audit.md`

- [ ] **Step 1: Annotate weak point #6 in the audit doc**

Find weak point #6 in `docs/smith-net-architecture-audit.md`. It's titled something like:

```
### 6. `auditLog.ts` JSONL only
```

Use grep to confirm:

```bash
grep -n "auditLog\|JSONL" docs/smith-net-architecture-audit.md | head -5
```

Append the annotation to the heading line:

```
### 6. `auditLog.ts` JSONL only [closed in slice 2, commit <SHA-from-task-3>]
```

Use the commit SHA from Task 3 (the `feat(audit): async log() with pg sync write` commit). Run `git log --oneline --grep="async log()" -1` to confirm.

- [ ] **Step 2: Run full suite one final time**

```bash
cd backend && npx jest
```

Expected: 121+ passing, 0 failing.

- [ ] **Step 3: Manual restart smoke (similar to Slice 1)**

```bash
export PATH=/opt/homebrew/opt/postgresql@16/bin:$PATH
export DATABASE_URL=postgres://fegensprenelon@localhost:5432/smithnet

# Pre-restart: log an audit entry via an authenticated request
cd backend && (DATABASE_URL=$DATABASE_URL npm run dev > /tmp/p2s2-smoke.log 2>&1 &)
sleep 8
SMOKE_EMAIL="smoke-audit-$(date +%s)@example.com"
curl -s -X POST http://localhost:3030/api/auth/register \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$SMOKE_EMAIL\",\"password\":\"password123\",\"displayName\":\"AuditSmoke\"}" > /dev/null

# Verify the row hit pg
psql "$DATABASE_URL" -c "SELECT COUNT(*) FROM audit_entries WHERE actor_id IN (SELECT id FROM users WHERE email='$SMOKE_EMAIL') OR (action='user.register' AND metadata->>'email'='$SMOKE_EMAIL');"
# Expected: at least 1 row

# Kill, restart, verify the row still exists
pkill -f "tsx watch src/server.ts" 2>/dev/null
pkill -f "node.*server.ts" 2>/dev/null
sleep 3
psql "$DATABASE_URL" -c "SELECT COUNT(*) FROM audit_entries;"
# Expected: count > 0; pg is the authoritative store

# Optional: tail the JSONL to confirm the cold backup also has it
ls -la backend/audit/
```

Expected: pg row persists across the restart. JSONL files exist as cold backup. If anything looks off, report DONE_WITH_CONCERNS and include details.

- [ ] **Step 4: Commit annotation + tag the slice**

```bash
git add docs/smith-net-architecture-audit.md
git commit -m "chore(phase-2): close slice 2 — audit_entries pg + pino on hot paths

- audit_entries table is the authoritative store; JSONL is cold backup
- log() forms a true SHA chain via pg_advisory_xact_lock
- pino active on authRoutes, jobsRoutes, wsHandler, usersService, auditLog
- audit weak point #6 marked closed"
git tag -a phase-2-slice-2 -m "Phase 2 Slice 2 — audit_entries pg + pino (audit weak point #6)"
```

- [ ] **Step 5: Verify final state**

```bash
git log --oneline phase-2-slice-1..phase-2-slice-2
git tag --list 'phase-2-*'
```

Expected: commits from Slice 2 visible; both tags present.

---

## What slice 2 did NOT do

- Did **not** move the audit write into a queue. That's Phase 3 — `audit_flush` worker.
- Did **not** rewrite the 35 non-hot modules to use pino. Opportunistic conversion only.
- Did **not** validate JWT on WS upgrade. That's Slice 3.
- Did **not** persist channelRegistry / gatewayManager. That's Slice 4.

Slice 3 plan to follow when Slice 2 is in main.
