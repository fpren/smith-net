# Phase 4 Slice 1 — Daemons + admin health

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development.

**Goal:** Ship 3 long-running daemons alongside Phase 3 workers so the queue is self-healing and the operator has a single observability endpoint. Closes the manual stuck-row recipe from `OPERATIONS.md`.

**Architecture:** New `backend/src/daemons/` directory. Each daemon exports `tick()` and a static interval. The runner loops the daemon ticks on their own cadence (separate from worker ticks, which run on demand-driven loops). A new `worker_heartbeats` table records last-beat timestamps; `/api/admin/health` aggregates from there + `background_jobs`.

**Tech Stack:** TypeScript, Express, pg, jest.

**Scope guardrails:**
- THREE daemons only: heartbeat, queueWatcher, cleanup. `presenceWatcher` is intentionally deferred — its semantics (auto-end stale shifts? mark stale? notify?) need a product call.
- No new workers in this slice (no `cleanupWorker`, no `invoiceDraftWorker`, etc.). The `cleanupDaemon` does work directly because the work is small enough (DELETE statements + an existing in-process call).
- No api.ts split, no Phase-0 dead-route decision. Those are subsequent Phase 4 slices.
- The existing `setInterval` in `server.ts` for `auditLog.cleanupOldEntries()` is REMOVED and replaced by `cleanupDaemon`. The wsHandler per-connection presence ping STAYS (per-connection, not system-wide).

---

## Daemon vs worker

Workers `tick()` returns `true` if it did work; the runner sleeps 1s when no work. Daemons run on a fixed cadence regardless of whether they did anything, so the runner needs a different loop:

```typescript
async function daemonLoop(name: string, intervalMs: number, fn: () => Promise<void>) {
  while (!SHUTDOWN.stop) {
    const startedAt = Date.now();
    await fn().catch((e) =>
      baseLogger.error({ event: 'daemon_tick_error', name, err: e }, 'daemon tick error')
    );
    const elapsed = Date.now() - startedAt;
    const wait = Math.max(0, intervalMs - elapsed);
    if (wait > 0) await new Promise((r) => setTimeout(r, wait));
  }
}
```

This pattern (subtract elapsed time) keeps the cadence stable even when a tick takes non-trivial time.

---

## File Structure

**Create:**
- `backend/migrations/011_worker_heartbeats.sql`
- `backend/src/daemons/heartbeatDaemon.ts`
- `backend/src/daemons/queueWatcherDaemon.ts`
- `backend/src/daemons/cleanupDaemon.ts`
- `backend/src/healthRoutes.ts`
- `backend/src/__tests__/heartbeat-daemon.test.ts`
- `backend/src/__tests__/queue-watcher-daemon.test.ts`
- `backend/src/__tests__/cleanup-daemon.test.ts`
- `backend/src/__tests__/health-routes.test.ts`

**Modify:**
- `backend/src/workers/runner.ts` — register daemons via `daemonLoop`
- `backend/src/server.ts` — mount `healthRouter`; remove the `setInterval(auditLog.cleanupOldEntries, 24h)` (cleanupDaemon now owns it)
- `OPERATIONS.md` — note daemons take over from the manual recipe

---

## Task 0: Baseline

- [ ] **Step 1:** Confirm 178 tests pass

```bash
cd /Users/fegensprenelon/smith-net/backend
DATABASE_URL='postgres://fegensprenelon@localhost:5432/smithnet' npm test 2>&1 | grep -E "Tests:|Test Suites:" | tail -4
```
Expected: 24 suites / 178 tests, all green.

---

## Task 1: Migration 011 — worker_heartbeats

**Files:**
- Create: `backend/migrations/011_worker_heartbeats.sql`

- [ ] **Step 1:** Write the migration

```sql
-- 011_worker_heartbeats.sql
-- Phase 4 Slice 1: heartbeat table for daemons + workers.
-- Each row is one running worker/daemon process. UPSERTed on every tick.
-- /api/admin/health reads this to show liveness.

CREATE TABLE IF NOT EXISTS worker_heartbeats (
  worker_id    TEXT PRIMARY KEY,
  kinds        TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
  last_beat_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  started_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS worker_heartbeats_recent_idx
  ON worker_heartbeats (last_beat_at DESC);
```

The `kinds` array records which worker loops this process registered (e.g. `['geocode','audit_flush','email']`). The /admin/health endpoint uses it to identify which kinds are "live" (worker_heartbeats has a recent row whose kinds[] contains that kind).

- [ ] **Step 2:** Apply the migration

Check the existing migration runner pattern:
```bash
grep -n "migration\|migrate\|.sql" backend/src/db.ts backend/src/server.ts 2>/dev/null | head
```

Migrations in this repo are applied at server startup via `db.ts` (verify by reading). If automatic, just running the test suite once after the new file lands will create the table. If manual, apply it directly:
```bash
DATABASE_URL='postgres://fegensprenelon@localhost:5432/smithnet' node -e "
const { Client } = require('/Users/fegensprenelon/smith-net/backend/node_modules/pg');
const c = new Client({ connectionString: process.env.DATABASE_URL });
const fs = require('fs');
const sql = fs.readFileSync('/Users/fegensprenelon/smith-net/backend/migrations/011_worker_heartbeats.sql','utf8');
c.connect().then(() => c.query(sql)).then(() => { console.log('ok'); return c.end(); });
"
```

- [ ] **Step 3:** Commit

```bash
cd /Users/fegensprenelon/smith-net
git add backend/migrations/011_worker_heartbeats.sql
git commit -m "feat(db): migration 011 — worker_heartbeats table"
```

---

## Task 2: heartbeatDaemon

**Files:**
- Create: `backend/src/daemons/heartbeatDaemon.ts`
- Create: `backend/src/__tests__/heartbeat-daemon.test.ts`

Cadence: 30s. Each tick UPSERTs into `worker_heartbeats` with the current `workerId` and the list of kinds this process serves.

- [ ] **Step 1:** Write `heartbeatDaemon.ts`

```typescript
// backend/src/daemons/heartbeatDaemon.ts
//
// Phase 4 Slice 1: heartbeat daemon.
// UPSERTs a row into worker_heartbeats every 30s so /api/admin/health
// can see which workers/daemons are alive.

import { pg, isPgEnabled } from '../db';

export const INTERVAL_MS = 30_000;

export async function tick(workerId: string, kinds: string[]): Promise<void> {
  if (!isPgEnabled() || !pg) return;
  await pg.query(
    `INSERT INTO worker_heartbeats (worker_id, kinds, last_beat_at, started_at)
     VALUES ($1, $2, NOW(), NOW())
     ON CONFLICT (worker_id) DO UPDATE
       SET kinds = EXCLUDED.kinds,
           last_beat_at = NOW()`,
    [workerId, kinds]
  );
}
```

- [ ] **Step 2:** Write the test

```typescript
// backend/src/__tests__/heartbeat-daemon.test.ts
import { pg, isPgEnabled } from '../db';
import { tick as heartbeatTick } from '../daemons/heartbeatDaemon';

const describeDb = isPgEnabled() ? describe : describe.skip;

async function cleanHeartbeats() {
  if (!isPgEnabled()) return;
  await pg!.query(`DELETE FROM worker_heartbeats WHERE worker_id LIKE 'test-%'`);
}

describeDb('heartbeatDaemon', () => {
  beforeEach(cleanHeartbeats);
  afterAll(async () => { await pg?.end(); });

  it('inserts a heartbeat row on first tick', async () => {
    await heartbeatTick('test-worker-1', ['geocode', 'audit_flush']);
    const r = await pg!.query<{ worker_id: string; kinds: string[]; last_beat_at: Date }>(
      `SELECT worker_id, kinds, last_beat_at FROM worker_heartbeats WHERE worker_id=$1`,
      ['test-worker-1']
    );
    expect(r.rowCount).toBe(1);
    expect(r.rows[0].kinds).toEqual(['geocode', 'audit_flush']);
    expect(Date.now() - r.rows[0].last_beat_at.getTime()).toBeLessThan(2000);
  });

  it('updates last_beat_at on subsequent ticks (UPSERT)', async () => {
    await heartbeatTick('test-worker-2', ['email']);
    const first = await pg!.query<{ last_beat_at: Date; started_at: Date }>(
      `SELECT last_beat_at, started_at FROM worker_heartbeats WHERE worker_id=$1`,
      ['test-worker-2']
    );
    const firstStartedAt = first.rows[0].started_at;

    await new Promise((r) => setTimeout(r, 50));
    await heartbeatTick('test-worker-2', ['email']);
    const second = await pg!.query<{ last_beat_at: Date; started_at: Date }>(
      `SELECT last_beat_at, started_at FROM worker_heartbeats WHERE worker_id=$1`,
      ['test-worker-2']
    );
    expect(second.rows[0].last_beat_at.getTime()).toBeGreaterThan(first.rows[0].last_beat_at.getTime());
    // started_at should NOT change on UPSERT — it's stamped on first INSERT only.
    expect(second.rows[0].started_at.getTime()).toBe(firstStartedAt.getTime());
  });

  it('updates kinds array if the set changes', async () => {
    await heartbeatTick('test-worker-3', ['geocode']);
    await heartbeatTick('test-worker-3', ['geocode', 'email']);
    const r = await pg!.query<{ kinds: string[] }>(
      `SELECT kinds FROM worker_heartbeats WHERE worker_id=$1`,
      ['test-worker-3']
    );
    expect(r.rows[0].kinds).toEqual(['geocode', 'email']);
  });
});
```

- [ ] **Step 3:** Run the new test

```bash
cd /Users/fegensprenelon/smith-net/backend
DATABASE_URL='postgres://fegensprenelon@localhost:5432/smithnet' npx jest --testPathPattern=heartbeat-daemon.test 2>&1 | tail -10
```
Expected: 3 tests pass.

- [ ] **Step 4:** Commit

```bash
cd /Users/fegensprenelon/smith-net
git add backend/src/daemons/heartbeatDaemon.ts backend/src/__tests__/heartbeat-daemon.test.ts
git commit -m "feat(daemon): heartbeatDaemon — UPSERTs worker_heartbeats every 30s"
```

---

## Task 3: queueWatcherDaemon

**Files:**
- Create: `backend/src/daemons/queueWatcherDaemon.ts`
- Create: `backend/src/__tests__/queue-watcher-daemon.test.ts`

Cadence: 60s. Resets `state='running'` rows whose `locked_at` is older than 10 minutes back to `state='queued'`. Audits the reset.

- [ ] **Step 1:** Write the daemon

```typescript
// backend/src/daemons/queueWatcherDaemon.ts
//
// Phase 4 Slice 1: queue watcher daemon.
// Periodically resets stuck running rows back to queued. Replaces the
// manual SQL recipe in OPERATIONS.md.

import { pg, isPgEnabled } from '../db';
import { auditLog, AuditAction } from '../auditLog';
import { requestLogger } from '../log';

export const INTERVAL_MS = 60_000;
const STUCK_THRESHOLD_MIN = 10;

export async function tick(): Promise<void> {
  if (!isPgEnabled() || !pg) return;
  const r = await pg.query<{ id: string; kind: string; locked_by: string | null }>(
    `UPDATE background_jobs
        SET state = 'queued',
            locked_at = NULL,
            locked_by = NULL,
            updated_at = NOW()
      WHERE state = 'running'
        AND locked_at < NOW() - ($1::int * INTERVAL '1 minute')
      RETURNING id, kind, locked_by`,
    [STUCK_THRESHOLD_MIN]
  );
  if (r.rowCount === 0) return;
  for (const row of r.rows) {
    requestLogger().warn(
      { event: 'stuck_job_reset', jobId: row.id, kind: row.kind, lockedBy: row.locked_by },
      'reset stuck running job'
    );
    await auditLog.log(AuditAction.ADMIN_ACTION, 'queueWatcherDaemon', {
      event: 'stuck_job_reset',
      job_id: row.id,
      kind: row.kind,
      previously_locked_by: row.locked_by,
    });
  }
}
```

- [ ] **Step 2:** Write the test

```typescript
// backend/src/__tests__/queue-watcher-daemon.test.ts
import { pg, isPgEnabled } from '../db';
import { enqueue } from '../queue/queue';
import { tick as queueWatcherTick } from '../daemons/queueWatcherDaemon';

const describeDb = isPgEnabled() ? describe : describe.skip;

async function cleanQueue() {
  if (!isPgEnabled()) return;
  await pg!.query(`DELETE FROM background_jobs WHERE kind IN ('geocode','test_stuck')`);
}

describeDb('queueWatcherDaemon', () => {
  beforeEach(cleanQueue);
  afterAll(async () => { await pg?.end(); });

  it('resets a row whose locked_at is > 10 minutes old back to queued', async () => {
    const enq = await enqueue({ kind: 'geocode', payload: { job_id: 'j1', address: 'x' } });
    // Move to running with a stale lock.
    await pg!.query(
      `UPDATE background_jobs SET state='running', locked_at = NOW() - INTERVAL '11 minutes', locked_by='dead-worker' WHERE id=$1`,
      [enq.id]
    );

    await queueWatcherTick();

    const r = await pg!.query<{ state: string; locked_at: Date | null; locked_by: string | null }>(
      `SELECT state, locked_at, locked_by FROM background_jobs WHERE id=$1`,
      [enq.id]
    );
    expect(r.rows[0].state).toBe('queued');
    expect(r.rows[0].locked_at).toBeNull();
    expect(r.rows[0].locked_by).toBeNull();
  });

  it('leaves recently-locked running rows alone', async () => {
    const enq = await enqueue({ kind: 'geocode', payload: { job_id: 'j2', address: 'y' } });
    await pg!.query(
      `UPDATE background_jobs SET state='running', locked_at = NOW() - INTERVAL '1 minute', locked_by='live-worker' WHERE id=$1`,
      [enq.id]
    );

    await queueWatcherTick();

    const r = await pg!.query<{ state: string; locked_by: string | null }>(
      `SELECT state, locked_by FROM background_jobs WHERE id=$1`,
      [enq.id]
    );
    expect(r.rows[0].state).toBe('running');
    expect(r.rows[0].locked_by).toBe('live-worker');
  });
});
```

- [ ] **Step 3:** Run

```bash
DATABASE_URL='postgres://fegensprenelon@localhost:5432/smithnet' npx jest --testPathPattern=queue-watcher-daemon.test 2>&1 | tail -10
```
Expected: 2 tests pass.

- [ ] **Step 4:** Commit

```bash
git add backend/src/daemons/queueWatcherDaemon.ts backend/src/__tests__/queue-watcher-daemon.test.ts
git commit -m "feat(daemon): queueWatcherDaemon — unsticks stuck running rows after 10m"
```

---

## Task 4: cleanupDaemon

**Files:**
- Create: `backend/src/daemons/cleanupDaemon.ts`
- Create: `backend/src/__tests__/cleanup-daemon.test.ts`

Cadence: 24h. Two responsibilities:
1. DELETE `background_jobs WHERE state='dead' AND finished_at < NOW() - INTERVAL '30 days'`
2. Call `auditLog.cleanupOldEntries()` (existing function that removes JSONL files >2 years old)

The 24h interval is too slow to test directly — tests call `tick()` directly and verify the DELETE.

- [ ] **Step 1:** Write the daemon

```typescript
// backend/src/daemons/cleanupDaemon.ts
//
// Phase 4 Slice 1: cleanup daemon.
// 24h cadence. Drops dead bg_jobs >30 days old + delegates audit JSONL
// retention to auditLog.cleanupOldEntries.

import { pg, isPgEnabled } from '../db';
import { auditLog } from '../auditLog';
import { requestLogger } from '../log';

export const INTERVAL_MS = 24 * 60 * 60 * 1000;
const DEAD_JOB_RETENTION_DAYS = 30;

export async function tick(): Promise<void> {
  if (!isPgEnabled() || !pg) return;
  const dead = await pg.query<{ id: string; kind: string }>(
    `DELETE FROM background_jobs
      WHERE state = 'dead'
        AND finished_at < NOW() - ($1::int * INTERVAL '1 day')
      RETURNING id, kind`,
    [DEAD_JOB_RETENTION_DAYS]
  );
  if ((dead.rowCount ?? 0) > 0) {
    requestLogger().info(
      { event: 'dead_jobs_purged', count: dead.rowCount },
      'purged dead background_jobs older than retention'
    );
  }

  try {
    const r = await auditLog.cleanupOldEntries();
    if (r.deleted > 0) {
      requestLogger().info(
        { event: 'audit_jsonl_purged', files: r.deleted },
        'purged old audit JSONL files'
      );
    }
  } catch (err) {
    requestLogger().error(
      { event: 'cleanup_daemon_audit_failed', err: (err as Error).message },
      'auditLog.cleanupOldEntries threw'
    );
  }
}
```

- [ ] **Step 2:** Write the test

```typescript
// backend/src/__tests__/cleanup-daemon.test.ts
import { pg, isPgEnabled } from '../db';
import { enqueue } from '../queue/queue';
import { tick as cleanupTick } from '../daemons/cleanupDaemon';

const describeDb = isPgEnabled() ? describe : describe.skip;

async function cleanQueue() {
  if (!isPgEnabled()) return;
  await pg!.query(`DELETE FROM background_jobs WHERE kind='test_cleanup'`);
}

describeDb('cleanupDaemon', () => {
  beforeEach(cleanQueue);
  afterAll(async () => { await pg?.end(); });

  it('deletes dead rows older than 30 days; keeps newer dead rows', async () => {
    const old = await enqueue({ kind: 'test_cleanup', payload: { tag: 'old' } });
    const recent = await enqueue({ kind: 'test_cleanup', payload: { tag: 'recent' } });
    await pg!.query(
      `UPDATE background_jobs SET state='dead', finished_at = NOW() - INTERVAL '31 days' WHERE id=$1`,
      [old.id]
    );
    await pg!.query(
      `UPDATE background_jobs SET state='dead', finished_at = NOW() - INTERVAL '5 days' WHERE id=$1`,
      [recent.id]
    );

    await cleanupTick();

    const r = await pg!.query<{ id: string; tag: string }>(
      `SELECT id, (payload->>'tag') AS tag FROM background_jobs WHERE kind='test_cleanup' ORDER BY id`
    );
    expect(r.rowCount).toBe(1);
    expect(r.rows[0].tag).toBe('recent');
  });

  it('does NOT delete non-dead old rows', async () => {
    const enq = await enqueue({ kind: 'test_cleanup', payload: { tag: 'queued' } });
    // Manually backdate; state is still 'queued'.
    await pg!.query(
      `UPDATE background_jobs SET finished_at = NOW() - INTERVAL '99 days' WHERE id=$1`,
      [enq.id]
    );
    await cleanupTick();
    const r = await pg!.query<{ count: string }>(
      `SELECT COUNT(*)::text AS count FROM background_jobs WHERE id=$1`,
      [enq.id]
    );
    expect(parseInt(r.rows[0].count, 10)).toBe(1);
  });
});
```

- [ ] **Step 3:** Run

```bash
DATABASE_URL='postgres://fegensprenelon@localhost:5432/smithnet' npx jest --testPathPattern=cleanup-daemon.test 2>&1 | tail -10
```
Expected: 2 tests pass.

- [ ] **Step 4:** Commit

```bash
git add backend/src/daemons/cleanupDaemon.ts backend/src/__tests__/cleanup-daemon.test.ts
git commit -m "feat(daemon): cleanupDaemon — purges dead bg_jobs + delegates JSONL retention"
```

---

## Task 5: /api/admin/health endpoint

**Files:**
- Create: `backend/src/healthRoutes.ts`
- Create: `backend/src/__tests__/health-routes.test.ts`

Response shape:
```json
{
  "workers": [{ "workerId": "1234@host", "kinds": ["geocode","email"], "lastBeatAt": "...", "ageSec": 7 }],
  "queue": {
    "byKindState": [{ "kind": "geocode", "state": "queued", "count": 3 }, ...],
    "oldestQueued": { "kind": "geocode", "scheduledAt": "...", "ageSec": 12 },
    "oldestRunning": { "kind": "audit_flush", "lockedAt": "...", "ageSec": 4 }
  }
}
```

Gated to admin role (existing pattern at `requirePermission(Permission.ADMIN)` or role check).

- [ ] **Step 1:** Write the route

```typescript
// backend/src/healthRoutes.ts
//
// Phase 4 Slice 1: /api/admin/health endpoint.
// Aggregates worker heartbeats + queue counters for the operator.

import { Router, Response } from 'express';
import { pg, isPgEnabled } from './db';
import { authenticateToken, AuthenticatedRequest, UserRole } from './auth';

export const healthRouter = Router();

const ADMIN_ROLES: ReadonlySet<UserRole> = new Set<UserRole>([UserRole.ADMIN]);

healthRouter.get('/health', authenticateToken, async (req: AuthenticatedRequest, res: Response) => {
  const role = req.user!.role as UserRole;
  if (!ADMIN_ROLES.has(role)) {
    return res.status(403).json({ error: 'admin role required' });
  }
  if (!isPgEnabled() || !pg) {
    return res.status(503).json({ error: 'pg unavailable' });
  }

  const [workersR, byKindStateR, oldestQR, oldestRR] = await Promise.all([
    pg.query<{ worker_id: string; kinds: string[]; last_beat_at: Date }>(
      `SELECT worker_id, kinds, last_beat_at FROM worker_heartbeats ORDER BY last_beat_at DESC`
    ),
    pg.query<{ kind: string; state: string; count: string }>(
      `SELECT kind, state::text AS state, COUNT(*)::text AS count
         FROM background_jobs
        GROUP BY kind, state
        ORDER BY kind, state`
    ),
    pg.query<{ kind: string; scheduled_at: Date }>(
      `SELECT kind, scheduled_at FROM background_jobs
        WHERE state='queued' ORDER BY scheduled_at ASC LIMIT 1`
    ),
    pg.query<{ kind: string; locked_at: Date }>(
      `SELECT kind, locked_at FROM background_jobs
        WHERE state='running' ORDER BY locked_at ASC LIMIT 1`
    ),
  ]);

  const now = Date.now();
  return res.status(200).json({
    workers: workersR.rows.map((w) => ({
      workerId: w.worker_id,
      kinds: w.kinds,
      lastBeatAt: w.last_beat_at,
      ageSec: Math.floor((now - w.last_beat_at.getTime()) / 1000),
    })),
    queue: {
      byKindState: byKindStateR.rows.map((r) => ({ kind: r.kind, state: r.state, count: parseInt(r.count, 10) })),
      oldestQueued: oldestQR.rows[0]
        ? { kind: oldestQR.rows[0].kind, scheduledAt: oldestQR.rows[0].scheduled_at,
            ageSec: Math.floor((now - oldestQR.rows[0].scheduled_at.getTime()) / 1000) }
        : null,
      oldestRunning: oldestRR.rows[0]
        ? { kind: oldestRR.rows[0].kind, lockedAt: oldestRR.rows[0].locked_at,
            ageSec: Math.floor((now - oldestRR.rows[0].locked_at.getTime()) / 1000) }
        : null,
    },
  });
});
```

- [ ] **Step 2:** Write the test

```typescript
// backend/src/__tests__/health-routes.test.ts
import express from 'express';
import request from 'supertest';
import cookieParser from 'cookie-parser';
import { authRouter } from '../authRoutes';
import { healthRouter } from '../healthRoutes';
import { pg, isPgEnabled } from '../db';
import { createUserAndProfile } from '../jobsService';
import { generateTokens, UserRole } from '../auth';
import { enqueue } from '../queue/queue';
import { tick as heartbeatTick } from '../daemons/heartbeatDaemon';

const describeDb = isPgEnabled() ? describe : describe.skip;

function buildApp() {
  const app = express();
  app.use(express.json());
  app.use(cookieParser());
  app.use('/api/auth', authRouter);
  app.use('/api/admin', healthRouter);
  return app;
}

async function createUserAndLogin(suffix: string, role: UserRole): Promise<{ id: string; token: string }> {
  const email = `health-${suffix}-${Date.now()}@example.com`;
  const user = await createUserAndProfile({ email, password: 'password123', displayName: `Health ${suffix}`, role });
  const tokens = await generateTokens(user);
  return { id: user.id, token: tokens.accessToken };
}

describeDb('GET /api/admin/health', () => {
  let app: express.Express;
  beforeAll(() => { app = buildApp(); });
  beforeEach(async () => {
    if (!isPgEnabled()) return;
    await pg!.query(`DELETE FROM worker_heartbeats WHERE worker_id LIKE 'test-%'`);
    await pg!.query(`DELETE FROM background_jobs WHERE kind='health_test'`);
  });
  afterAll(async () => { await pg?.end(); });

  it('403 for non-admin role', async () => {
    const { token } = await createUserAndLogin('foreman', UserRole.FOREMAN);
    const res = await request(app).get('/api/admin/health').set('Cookie', `smithnet_access=${token}`);
    expect(res.status).toBe(403);
  });

  it('200 for admin role; returns workers + queue rollup', async () => {
    const { token } = await createUserAndLogin('admin', UserRole.ADMIN);

    await heartbeatTick('test-worker-health', ['geocode', 'email']);
    await enqueue({ kind: 'health_test' as any, payload: { x: 1 } });

    const res = await request(app).get('/api/admin/health').set('Cookie', `smithnet_access=${token}`);
    expect(res.status).toBe(200);
    expect(Array.isArray(res.body.workers)).toBe(true);
    const w = res.body.workers.find((w: any) => w.workerId === 'test-worker-health');
    expect(w).toBeDefined();
    expect(w.kinds).toEqual(['geocode', 'email']);
    expect(typeof w.ageSec).toBe('number');
    expect(Array.isArray(res.body.queue.byKindState)).toBe(true);
    const ourRow = res.body.queue.byKindState.find((r: any) => r.kind === 'health_test');
    expect(ourRow).toBeDefined();
    expect(ourRow.count).toBeGreaterThanOrEqual(1);
  });

  it('401 without a token', async () => {
    const res = await request(app).get('/api/admin/health');
    expect(res.status).toBe(401);
  });
});
```

- [ ] **Step 3:** Run

```bash
DATABASE_URL='postgres://fegensprenelon@localhost:5432/smithnet' npx jest --testPathPattern=health-routes.test 2>&1 | tail -15
```
Expected: 3 tests pass.

- [ ] **Step 4:** Commit

```bash
git add backend/src/healthRoutes.ts backend/src/__tests__/health-routes.test.ts
git commit -m "feat(admin): GET /api/admin/health — workers + queue rollup (admin-only)"
```

---

## Task 6: Wire daemons into runner; mount healthRouter; drop legacy setInterval

**Files:**
- Modify: `backend/src/workers/runner.ts`
- Modify: `backend/src/server.ts`

- [ ] **Step 1:** Modify runner.ts

Add daemon imports + a daemonLoop helper. The existing `loop` for workers stays; add a new `daemonLoop`:

```typescript
// existing imports...
import { tick as heartbeatTick, INTERVAL_MS as HEARTBEAT_MS } from '../daemons/heartbeatDaemon';
import { tick as queueWatcherTick, INTERVAL_MS as QUEUE_WATCHER_MS } from '../daemons/queueWatcherDaemon';
import { tick as cleanupTick, INTERVAL_MS as CLEANUP_MS } from '../daemons/cleanupDaemon';
```

After the existing `loop` function, add:

```typescript
async function daemonLoop(name: string, intervalMs: number, fn: () => Promise<void>) {
  while (!SHUTDOWN.stop) {
    const startedAt = Date.now();
    await fn().catch((e) =>
      baseLogger.error({ event: 'daemon_tick_error', name, err: e }, 'daemon tick error')
    );
    const elapsed = Date.now() - startedAt;
    const wait = Math.max(0, intervalMs - elapsed);
    if (wait > 0) await new Promise((r) => setTimeout(r, wait));
  }
  baseLogger.info({ event: 'daemon_loop_stopped', name }, 'daemon loop stopped');
}
```

At the bottom, after the existing worker loops, register the daemons. Pass `WORKER_ID` and the list of registered worker kinds to the heartbeat:

```typescript
const REGISTERED_KINDS = ['geocode', 'audit_flush', 'email'];
void daemonLoop('heartbeat', HEARTBEAT_MS, () => heartbeatTick(WORKER_ID, REGISTERED_KINDS));
void daemonLoop('queue_watcher', QUEUE_WATCHER_MS, queueWatcherTick);
void daemonLoop('cleanup', CLEANUP_MS, cleanupTick);
```

- [ ] **Step 2:** Modify server.ts

Read the current `setInterval(() => auditLog.cleanupOldEntries(), 24 * 60 * 60 * 1000);` line and remove it. Also mount the healthRouter — find the section that mounts other admin/auth routers and add:
```typescript
import { healthRouter } from './healthRoutes';
// ...
app.use('/api/admin', healthRouter);
```
(If `/api/admin` is already in use for another router, mount under a unique path; verify by reading server.ts).

- [ ] **Step 3:** Smoke-run

```bash
cd /Users/fegensprenelon/smith-net/backend
DATABASE_URL='postgres://fegensprenelon@localhost:5432/smithnet' npx tsx -e "import('./src/workers/runner.ts').then(() => setTimeout(() => process.exit(0), 1500))" 2>&1 | tail -10
```
Expected: `worker_starting` + a heartbeat tick log within the first 30s window (or just the worker_starting line if 1.5s < HEARTBEAT_MS). Process exits clean.

- [ ] **Step 4:** Run the full suite to confirm no regression

```bash
DATABASE_URL='postgres://fegensprenelon@localhost:5432/smithnet' npm test 2>&1 | grep -E "Tests:|Test Suites:" | tail -4
```
Expected: ~28 suites, 188+ tests, all green (was 178 + 10 new across Tasks 2,3,4,5).

- [ ] **Step 5:** Commit

```bash
git add backend/src/workers/runner.ts backend/src/server.ts
git commit -m "feat(runner): register heartbeat + queue_watcher + cleanup daemons; mount /api/admin/health; drop legacy setInterval"
```

---

## Task 7: Closeout — tag + OPERATIONS.md

**Files:**
- Modify: `OPERATIONS.md` — the manual stuck-row recipe is now AUTOMATED by queueWatcherDaemon; annotate.

- [ ] **Step 1:** Edit the top of OPERATIONS.md

Replace the `## Stuck running background_jobs (Phase 3 interim)` section heading + intro with something like:

```markdown
## Stuck running background_jobs

**Automated since Phase 4 Slice 1.** `queueWatcherDaemon` runs on a 60s
cadence and resets any `state='running'` row whose `locked_at` is older
than 10 minutes back to `state='queued'`. Operator intervention is no
longer required for the typical worker-crash case.

The manual SQL recipe below stays available for emergencies (e.g. the
daemon itself is down):

```sql
UPDATE background_jobs
   SET state = 'queued',
       locked_at = NULL,
       locked_by = NULL
 WHERE state = 'running'
   AND locked_at < NOW() - INTERVAL '10 minutes';
```
```

(Keep the diagnostic query and the dead-jobs section intact.)

Add a new section:

```markdown
## Operator health view

`GET /api/admin/health` (admin role required) returns:
- `workers[]` — every process with a recent `worker_heartbeats` row
- `queue.byKindState[]` — count of rows per (kind, state)
- `queue.oldestQueued` / `queue.oldestRunning` — oldest pending+running rows

Use this to verify both processes are alive after a deploy and to spot
queue buildup before users notice. Heartbeat cadence is 30s; rows older
than ~2 minutes mean the worker process is down.
```

- [ ] **Step 2:** Final test sweep

```bash
cd /Users/fegensprenelon/smith-net/backend
DATABASE_URL='postgres://fegensprenelon@localhost:5432/smithnet' npm test 2>&1 | grep -E "Tests:|Test Suites:" | tail -4
```

- [ ] **Step 3:** Closeout commit + tag

```bash
cd /Users/fegensprenelon/smith-net
git add OPERATIONS.md
git commit -m "$(cat <<'EOF'
chore(phase-4): close slice 1 — daemons + admin health

Phase 4 Slice 1 — Daemons.

Ships three daemons alongside the Phase 3 workers:
- heartbeatDaemon: UPSERTs worker_heartbeats every 30s
- queueWatcherDaemon: resets stuck running rows after 10m (60s cadence);
  replaces the manual SQL recipe documented in OPERATIONS.md
- cleanupDaemon: 24h cadence; deletes dead bg_jobs >30d, delegates
  JSONL retention to auditLog.cleanupOldEntries

GET /api/admin/health (admin-only) aggregates worker heartbeats +
queue counts for operator visibility.

server.ts setInterval for auditLog.cleanupOldEntries is removed in
favor of cleanupDaemon.

Migrations: 011_worker_heartbeats.sql.

Tests: 10 new across heartbeat/queueWatcher/cleanup/health-routes.

Deferred to subsequent Phase 4 slices:
- presenceWatcherDaemon (semantics needs product call)
- api.ts split into domain routers
- Phase-0 dead-route keep/kill decision
- invoiceDraft / reportRender / cleanup workers
EOF
)"
git tag -a phase-4-slice-1 -m "Phase 4 Slice 1 — daemons + admin health"
```

---

## Done criteria

- 3 daemons registered in `runner.ts` ticking on their cadences
- `/api/admin/health` returns workers + queue rollup; admin-gated
- Migration 011 applied to local pg
- Backend tests go from 178 → 188+ (10 new), all green
- `phase-4-slice-1` tag exists
- OPERATIONS.md reflects daemon automation
- server.ts setInterval for auditLog.cleanupOldEntries is gone

## Self-review checklist

- [ ] Each daemon exports `tick()` and `INTERVAL_MS`
- [ ] runner.ts uses `daemonLoop` (separate from worker `loop`)
- [ ] heartbeat starts immediately on first tick; doesn't wait the full interval
- [ ] queueWatcher only resets `state='running'` rows (not `failed`, `dead`)
- [ ] cleanup retention is 30 days for dead bg_jobs (matches OPERATIONS doc)
- [ ] /admin/health gated on `UserRole.ADMIN`, not just authenticated
- [ ] No emoji anywhere
