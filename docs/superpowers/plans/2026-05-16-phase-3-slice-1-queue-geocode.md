# Phase 3 Slice 1 — Queue infrastructure + geocode worker

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up the `background_jobs` Postgres queue, the `queue.ts` API (`enqueue`/`claimNext`/`complete`/`fail`), a separate `workers/runner.ts` Node entrypoint, and migrate Plan 4's fire-and-forget geocode into a `geocodeWorker`. Closes the most visible Phase 2 Rule 2 violation: `jobsService.create()` no longer kicks off a non-awaited Nominatim call.

**Architecture:** Migration 009 creates `background_jobs` (per the schema in `docs/smith-net-daemon-worker-queue-plan.md`). `backend/src/queue/queue.ts` exports four functions used by every worker. `backend/src/workers/runner.ts` is a new Node entrypoint started via `npm run worker` — it loops over each kind's `tick(workerId)` with 1s idle sleep and graceful SIGTERM/SIGINT. `geocodeWorker.tick()` claims one job, calls Nominatim, UPDATEs the jobs row with lat/lng, completes (or fails with backoff on transient errors). `jobsService.create()` and `update()` stop calling `geocodeAndUpdate` directly — they enqueue a `kind='geocode'` row with `dedupeKey='geocode:'+jobId`.

**Tech Stack:** Node + Express + TypeScript + `pg.Pool` + Jest + `tsx` (already a dev dep for the existing `dev` script). New devDep: `concurrently`.

**Prerequisites:**
- Phase 2 complete (tag `phase-2`). 135/135 backend tests passing.
- `DATABASE_URL` is set and `psql` is on PATH.
- Working branch is `feat/relay-hetzner-postgres`.

**Reference docs:**
- Spec: `docs/superpowers/specs/2026-05-16-phase-3-queues-workers-design.md`
- Companion: `docs/smith-net-daemon-worker-queue-plan.md` (the original audit doc — schema + code skeletons are lifted from here)
- Audit: `docs/smith-net-architecture-audit.md` (weak point #2 — annotated `[closed in phase-3, ...]` at Slice 3 closeout, NOT this slice)
- CLAUDE.md: no fire-and-forget — every enqueue must be awaited

---

## File Map

| File | Action | Purpose |
|---|---|---|
| `backend/migrations/009_background_jobs.sql` | Create | `bg_job_state` enum + `background_jobs` table + 3 indexes |
| `backend/src/queue/queue.ts` | Create | `enqueue` / `claimNext` / `complete` / `fail` API |
| `backend/src/workers/runner.ts` | Create | Separate Node entrypoint; loops over registered ticks |
| `backend/src/workers/geocodeWorker.ts` | Create | `tick(workerId)` for kind='geocode' |
| `backend/src/geocoder.ts` | Modify | Export the pure `geocode(address)` function for reuse by the worker (currently inline in jobsService) |
| `backend/src/jobsService.ts` | Modify | `create()` / `update()` enqueue instead of fire-and-forget; delete `geocodeAndUpdate` helper |
| `backend/src/__tests__/queue.test.ts` | Create | enqueue/claim/complete + dedup |
| `backend/src/__tests__/queue-backoff.test.ts` | Create | fail with exponential backoff + dead transition |
| `backend/src/__tests__/geocodeWorker.test.ts` | Create | tick happy path + 503 retry + dead-letter |
| `backend/package.json` | Modify | Add `worker` and `dev:all` scripts; add `concurrently` devDep |

---

## Task 1 — Migration 009: background_jobs schema

**Files:**
- Create: `backend/migrations/009_background_jobs.sql`

- [ ] **Step 1: Write the migration**

Create `backend/migrations/009_background_jobs.sql`:

```sql
-- 009_background_jobs.sql
-- Phase 3 Slice 1: Postgres-backed background-job queue.
-- One table per the daemon-worker-queue plan. Different `kind` values
-- partition the work; workers claim WHERE kind=$1 FOR UPDATE SKIP LOCKED.

CREATE TYPE bg_job_state AS ENUM (
  'queued', 'running', 'succeeded', 'failed', 'dead'
);

CREATE TABLE IF NOT EXISTS background_jobs (
  id              BIGSERIAL PRIMARY KEY,
  kind            TEXT NOT NULL,
  payload         JSONB NOT NULL DEFAULT '{}'::jsonb,
  state           bg_job_state NOT NULL DEFAULT 'queued',
  attempts        INTEGER NOT NULL DEFAULT 0,
  max_attempts    INTEGER NOT NULL DEFAULT 5,
  scheduled_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  locked_at       TIMESTAMPTZ,
  locked_by       TEXT,
  last_error      TEXT,
  dedupe_key      TEXT,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  finished_at     TIMESTAMPTZ
);

-- Hot path: claim next ready job by kind
CREATE INDEX IF NOT EXISTS bg_jobs_claim_idx
  ON background_jobs (kind, scheduled_at)
  WHERE state = 'queued';

-- Dedupe across active states. Two callers with same (kind, dedupe_key)
-- get a single row; terminal rows don't block re-enqueue.
CREATE UNIQUE INDEX IF NOT EXISTS bg_jobs_dedupe_idx
  ON background_jobs (kind, dedupe_key)
  WHERE state IN ('queued', 'running', 'failed') AND dedupe_key IS NOT NULL;

-- Observability: state counts by kind
CREATE INDEX IF NOT EXISTS bg_jobs_state_idx ON background_jobs (state, kind);
```

- [ ] **Step 2: Apply the migration**

```bash
export PATH=/opt/homebrew/opt/postgresql@16/bin:$PATH
export DATABASE_URL=postgres://fegensprenelon@localhost:5432/smithnet
cd backend && psql "$DATABASE_URL" -f migrations/009_background_jobs.sql
```

Expected: `CREATE TYPE`, `CREATE TABLE`, `CREATE INDEX` (×3). No errors.

- [ ] **Step 3: Verify schema**

```bash
psql "$DATABASE_URL" -c "\d background_jobs"
psql "$DATABASE_URL" -c "\dT bg_job_state"
```

Expected: 14 columns; 4 indexes (PK + 3 explicit); enum has 5 values.

- [ ] **Step 4: Commit**

```bash
git add backend/migrations/009_background_jobs.sql
git commit -m "feat(db): migration 009 — background_jobs queue table (Phase 3 Slice 1)"
```

---

## Task 2 — queue.ts: enqueue (TDD entry point)

**Files:**
- Create: `backend/src/queue/queue.ts`
- Create: `backend/src/__tests__/queue.test.ts`

- [ ] **Step 1: Write the failing test**

Create `backend/src/__tests__/queue.test.ts`:

```typescript
import { pg, isPgEnabled } from '../db';
import { enqueue } from '../queue/queue';

const describeDb = isPgEnabled() ? describe : describe.skip;

async function cleanJobs() {
  if (!isPgEnabled()) return;
  await pg!.query('TRUNCATE background_jobs RESTART IDENTITY');
}

describeDb('queue.enqueue', () => {
  beforeEach(cleanJobs);
  afterAll(async () => { await pg?.end(); });

  it('inserts a row with state=queued, attempts=0', async () => {
    const r = await enqueue({ kind: 'geocode', payload: { job_id: 'j-1', address: 'NYC' } });
    expect(r.created).toBe(true);
    expect(r.id).toBeGreaterThan(0);

    const rows = await pg!.query('SELECT kind, state, attempts, payload, max_attempts FROM background_jobs WHERE id = $1', [r.id]);
    expect(rows.rowCount).toBe(1);
    expect(rows.rows[0].kind).toBe('geocode');
    expect(rows.rows[0].state).toBe('queued');
    expect(rows.rows[0].attempts).toBe(0);
    expect(rows.rows[0].max_attempts).toBe(5);
    expect(rows.rows[0].payload).toEqual({ job_id: 'j-1', address: 'NYC' });
  });

  it('respects dedupeKey: same key twice yields one row, second returns created=false', async () => {
    const a = await enqueue({ kind: 'geocode', payload: { x: 1 }, dedupeKey: 'geocode:abc' });
    const b = await enqueue({ kind: 'geocode', payload: { x: 2 }, dedupeKey: 'geocode:abc' });
    expect(a.created).toBe(true);
    expect(b.created).toBe(false);
    expect(b.id).toBe(-1);

    const count = await pg!.query("SELECT COUNT(*) FROM background_jobs WHERE dedupe_key = 'geocode:abc'");
    expect(parseInt(count.rows[0].count, 10)).toBe(1);
  });

  it('respects scheduledAt for delayed jobs', async () => {
    const future = new Date(Date.now() + 60_000);
    const r = await enqueue({ kind: 'geocode', payload: {}, scheduledAt: future });
    const row = await pg!.query('SELECT scheduled_at FROM background_jobs WHERE id = $1', [r.id]);
    const got = (row.rows[0].scheduled_at as Date).getTime();
    expect(Math.abs(got - future.getTime())).toBeLessThan(1000);
  });

  it('honors maxAttempts override', async () => {
    const r = await enqueue({ kind: 'geocode', payload: {}, maxAttempts: 10 });
    const row = await pg!.query('SELECT max_attempts FROM background_jobs WHERE id = $1', [r.id]);
    expect(row.rows[0].max_attempts).toBe(10);
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd backend && npx jest src/__tests__/queue.test.ts
```

Expected: FAIL with "Cannot find module '../queue/queue'".

- [ ] **Step 3: Implement queue/queue.ts (enqueue only)**

Create `backend/src/queue/queue.ts`:

```typescript
/**
 * Phase 3 Slice 1: Postgres-backed background-job queue.
 *
 * Public API:
 *   enqueue()    — INSERT a job row; dedupe via partial unique index
 *   claimNext()  — pop one queued job via FOR UPDATE SKIP LOCKED (Task 3)
 *   complete()   — mark terminal success (Task 3)
 *   fail()       — backoff retry or mark dead (Task 4)
 */

import { pg, isPgEnabled } from '../db';

export type BgJobKind =
  | 'geocode'
  | 'audit_flush'
  | 'email'
  | 'invoice_draft'
  | 'report_render'
  | 'llm_call'
  | 'cleanup'
  | 'heartbeat';

export interface EnqueueOptions {
  kind: BgJobKind;
  payload: Record<string, unknown>;
  scheduledAt?: Date;
  dedupeKey?: string;
  maxAttempts?: number;
}

function requirePg() {
  if (!isPgEnabled() || !pg) {
    throw new Error('[queue] DATABASE_URL is required; pg pool is not initialized');
  }
  return pg;
}

export async function enqueue(opts: EnqueueOptions): Promise<{ id: number; created: boolean }> {
  const db = requirePg();
  const r = await db.query<{ id: number }>(
    `INSERT INTO background_jobs (kind, payload, scheduled_at, dedupe_key, max_attempts)
     VALUES ($1, $2::jsonb, COALESCE($3, NOW()), $4, COALESCE($5, 5))
     ON CONFLICT (kind, dedupe_key)
       WHERE state IN ('queued','running','failed') AND dedupe_key IS NOT NULL
       DO NOTHING
     RETURNING id`,
    [opts.kind, JSON.stringify(opts.payload), opts.scheduledAt ?? null, opts.dedupeKey ?? null, opts.maxAttempts ?? null]
  );
  if (r.rowCount === 0) return { id: -1, created: false };
  return { id: r.rows[0].id, created: true };
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
cd backend && npx jest src/__tests__/queue.test.ts
```

Expected: PASS — 4 tests pass.

- [ ] **Step 5: Commit**

```bash
git add backend/src/queue/queue.ts backend/src/__tests__/queue.test.ts
git commit -m "feat(queue): enqueue with ON CONFLICT dedupe"
```

---

## Task 3 — queue.ts: claimNext + complete

**Files:**
- Modify: `backend/src/queue/queue.ts`
- Modify: `backend/src/__tests__/queue.test.ts`

- [ ] **Step 1: Write the failing test**

Append to `backend/src/__tests__/queue.test.ts`:

```typescript
import { claimNext, complete } from '../queue/queue';

describeDb('queue.claimNext + complete', () => {
  beforeEach(cleanJobs);

  it('claims oldest queued row matching kind; sets state=running, locked_by, attempts+1', async () => {
    const a = await enqueue({ kind: 'geocode', payload: { x: 1 } });
    await new Promise(r => setTimeout(r, 10));
    await enqueue({ kind: 'geocode', payload: { x: 2 } });

    const claimed = await claimNext('geocode', 'worker-A');
    expect(claimed?.id).toBe(a.id);
    expect(claimed?.attempts).toBe(1);
    expect(claimed?.payload).toEqual({ x: 1 });

    const row = await pg!.query('SELECT state, locked_by, locked_at FROM background_jobs WHERE id = $1', [a.id]);
    expect(row.rows[0].state).toBe('running');
    expect(row.rows[0].locked_by).toBe('worker-A');
    expect(row.rows[0].locked_at).toBeTruthy();
  });

  it('returns null when no queued rows for that kind', async () => {
    const claimed = await claimNext('email', 'worker-A');
    expect(claimed).toBeNull();
  });

  it('skips jobs with scheduled_at in the future', async () => {
    await enqueue({ kind: 'geocode', payload: {}, scheduledAt: new Date(Date.now() + 60_000) });
    const claimed = await claimNext('geocode', 'worker-A');
    expect(claimed).toBeNull();
  });

  it('two concurrent claims return different jobs (SKIP LOCKED)', async () => {
    await enqueue({ kind: 'geocode', payload: { x: 1 } });
    await new Promise(r => setTimeout(r, 10));
    await enqueue({ kind: 'geocode', payload: { x: 2 } });

    const [a, b] = await Promise.all([
      claimNext('geocode', 'worker-A'),
      claimNext('geocode', 'worker-B'),
    ]);
    expect(a?.id).not.toBe(b?.id);
    expect(a?.id).toBeTruthy();
    expect(b?.id).toBeTruthy();
  });

  it('complete() moves running -> succeeded with finished_at set', async () => {
    const e = await enqueue({ kind: 'geocode', payload: {} });
    const c = await claimNext('geocode', 'worker-A');
    await complete(c!.id);
    const row = await pg!.query('SELECT state, finished_at FROM background_jobs WHERE id = $1', [e.id]);
    expect(row.rows[0].state).toBe('succeeded');
    expect(row.rows[0].finished_at).toBeTruthy();
  });
});
```

- [ ] **Step 2: Verify the test fails**

```bash
cd backend && npx jest src/__tests__/queue.test.ts -t 'claimNext|complete'
```

Expected: FAIL — functions not exported.

- [ ] **Step 3: Implement claimNext and complete**

Add to `backend/src/queue/queue.ts`:

```typescript
export interface ClaimedJob {
  id: number;
  payload: Record<string, unknown>;
  attempts: number;
  max_attempts: number;
}

export async function claimNext(kind: BgJobKind, workerId: string): Promise<ClaimedJob | null> {
  const db = requirePg();
  const r = await db.query<ClaimedJob>(
    `UPDATE background_jobs
        SET state = 'running',
            locked_at = NOW(),
            locked_by = $2,
            attempts = attempts + 1,
            updated_at = NOW()
      WHERE id = (
        SELECT id FROM background_jobs
         WHERE kind = $1 AND state = 'queued' AND scheduled_at <= NOW()
         ORDER BY scheduled_at, id
         FOR UPDATE SKIP LOCKED
         LIMIT 1
      )
      RETURNING id, payload, attempts, max_attempts`,
    [kind, workerId]
  );
  return r.rows[0] ?? null;
}

export async function complete(id: number): Promise<void> {
  const db = requirePg();
  await db.query(
    `UPDATE background_jobs
        SET state = 'succeeded', finished_at = NOW(), updated_at = NOW()
      WHERE id = $1`,
    [id]
  );
}
```

- [ ] **Step 4: Verify the test passes**

```bash
cd backend && npx jest src/__tests__/queue.test.ts
```

Expected: PASS — 9 tests total (4 enqueue + 5 claim/complete).

- [ ] **Step 5: Commit**

```bash
git add backend/src/queue/queue.ts backend/src/__tests__/queue.test.ts
git commit -m "feat(queue): claimNext (FOR UPDATE SKIP LOCKED) + complete"
```

---

## Task 4 — queue.ts: fail with backoff + dead state

**Files:**
- Modify: `backend/src/queue/queue.ts`
- Create: `backend/src/__tests__/queue-backoff.test.ts`

- [ ] **Step 1: Write the failing test**

Create `backend/src/__tests__/queue-backoff.test.ts`:

```typescript
import { pg, isPgEnabled } from '../db';
import { enqueue, claimNext, fail } from '../queue/queue';

const describeDb = isPgEnabled() ? describe : describe.skip;

async function cleanJobs() {
  if (!isPgEnabled()) return;
  await pg!.query('TRUNCATE background_jobs RESTART IDENTITY');
}

describeDb('queue.fail + backoff', () => {
  beforeEach(cleanJobs);
  afterAll(async () => { await pg?.end(); });

  it('fail with attempts < max moves to state=failed and schedules retry', async () => {
    const e = await enqueue({ kind: 'geocode', payload: {} });
    const c = await claimNext('geocode', 'w');
    await fail(c!.id, new Error('boom'), { attempts: c!.attempts, maxAttempts: c!.max_attempts });

    const row = await pg!.query(
      `SELECT state, last_error, scheduled_at, locked_at, locked_by FROM background_jobs WHERE id = $1`,
      [e.id]
    );
    expect(row.rows[0].state).toBe('failed');
    expect(row.rows[0].last_error).toBe('boom');
    expect(row.rows[0].locked_at).toBeNull();
    expect(row.rows[0].locked_by).toBeNull();
    // First-attempt backoff: 60 * 3^1 = 180s. Allow 30s clock slop.
    const scheduledIn = (row.rows[0].scheduled_at as Date).getTime() - Date.now();
    expect(scheduledIn).toBeGreaterThan(150_000);
    expect(scheduledIn).toBeLessThan(210_000);
  });

  it('fail with attempts >= max moves to state=dead with finished_at', async () => {
    const e = await enqueue({ kind: 'geocode', payload: {}, maxAttempts: 2 });
    // Burn 2 attempts
    for (let i = 0; i < 2; i++) {
      const c = await claimNext('geocode', 'w');
      // Force the row back to queued so we can re-claim — bypass the natural
      // backoff by setting scheduled_at to now.
      await fail(c!.id, new Error('e' + i), { attempts: c!.attempts, maxAttempts: c!.max_attempts });
      if (i < 1) {
        await pg!.query(`UPDATE background_jobs SET state='queued', scheduled_at=NOW() WHERE id=$1`, [e.id]);
      }
    }

    const row = await pg!.query('SELECT state, finished_at FROM background_jobs WHERE id = $1', [e.id]);
    expect(row.rows[0].state).toBe('dead');
    expect(row.rows[0].finished_at).toBeTruthy();
  });

  it('backoff formula: min(60 * 3^attempts, 6h)', async () => {
    // Synthetic row with attempts already high — verify backoff caps at 6h.
    await pg!.query(`
      INSERT INTO background_jobs (kind, payload, state, attempts, max_attempts, locked_at, locked_by)
      VALUES ('geocode', '{}'::jsonb, 'running', 10, 99, NOW(), 'w')
    `);
    const { rows } = await pg!.query<{ id: number }>(`SELECT id FROM background_jobs ORDER BY id DESC LIMIT 1`);
    await fail(rows[0].id, new Error('cap'), { attempts: 10, maxAttempts: 99 });

    const row = await pg!.query(`SELECT scheduled_at FROM background_jobs WHERE id = $1`, [rows[0].id]);
    const scheduledIn = (row.rows[0].scheduled_at as Date).getTime() - Date.now();
    expect(scheduledIn).toBeLessThanOrEqual(6 * 3600 * 1000 + 5_000); // 6h + tolerance
    expect(scheduledIn).toBeGreaterThan(6 * 3600 * 1000 - 5_000);
  });

  it('truncates last_error to 1000 chars', async () => {
    const e = await enqueue({ kind: 'geocode', payload: {} });
    const c = await claimNext('geocode', 'w');
    const huge = 'x'.repeat(2000);
    await fail(c!.id, new Error(huge), { attempts: c!.attempts, maxAttempts: c!.max_attempts });
    const row = await pg!.query('SELECT last_error FROM background_jobs WHERE id = $1', [e.id]);
    expect(row.rows[0].last_error.length).toBe(1000);
  });
});
```

- [ ] **Step 2: Verify failure**

```bash
cd backend && npx jest src/__tests__/queue-backoff.test.ts
```

Expected: FAIL — `fail` is not exported.

- [ ] **Step 3: Implement fail**

Add to `backend/src/queue/queue.ts`:

```typescript
export async function fail(id: number, err: Error, opts: { attempts: number; maxAttempts: number }): Promise<void> {
  const db = requirePg();
  const dead = opts.attempts >= opts.maxAttempts;
  const backoffSec = Math.min(60 * Math.pow(3, opts.attempts), 6 * 3600);
  await db.query(
    `UPDATE background_jobs
        SET state = $2::bg_job_state,
            last_error = $3,
            scheduled_at = CASE WHEN $2 = 'failed' THEN NOW() + ($4::int * INTERVAL '1 second') ELSE scheduled_at END,
            finished_at = CASE WHEN $2 = 'dead' THEN NOW() ELSE NULL END,
            locked_at = NULL,
            locked_by = NULL,
            updated_at = NOW()
      WHERE id = $1`,
    [id, dead ? 'dead' : 'failed', err.message.slice(0, 1000), backoffSec]
  );
}
```

- [ ] **Step 4: Verify tests pass**

```bash
cd backend && npx jest src/__tests__/queue-backoff.test.ts
```

Expected: PASS — 4 tests.

- [ ] **Step 5: Run the full backend test suite**

```bash
cd backend && npx jest
```

Expected: 144+ passing (135 baseline + 9 queue + 4 backoff). 0 failures.

- [ ] **Step 6: Commit**

```bash
git add backend/src/queue/queue.ts backend/src/__tests__/queue-backoff.test.ts
git commit -m "feat(queue): fail() with exponential backoff + dead transition"
```

---

## Task 5 — Refactor geocoder.ts to export a pure function

**Files:**
- Modify: `backend/src/geocoder.ts`
- Modify: `backend/src/jobsService.ts` (will be edited again in Task 7; here just rename the helper)

Read `backend/src/geocoder.ts` first — Plan 4 created it. It likely already exports `geocodeLocation` or similar. If so, this task is a tiny rename/no-op.

- [ ] **Step 1: Confirm geocoder.ts shape**

```bash
grep -n "^export" backend/src/geocoder.ts
```

Expected exports include something like `geocodeLocation(location: string): Promise<{lat,lng} | null>` and `__resetGeocoderState`.

If a pure `geocode(address): Promise<{lat,lng}>` already exists, Task 5 is just verification — proceed to Step 4.

If `jobsService.geocodeAndUpdate` inlines the Nominatim fetch (not delegating to geocoder.ts), refactor: move the fetch into a new exported function in `geocoder.ts` and have `geocodeAndUpdate` (still in jobsService for now — Task 7 deletes it) call the exported function.

- [ ] **Step 2: Ensure the exported function throws on transient errors**

The Nominatim function should:
- Return `{lat, lng}` on 200 + array with at least one result
- Throw `new Error('nominatim 503')` (or similar) on 5xx — the worker's `fail()` path uses the Error message
- Throw `new Error('nominatim no_match')` on 200 + empty array — caller decides whether to retry or `dead`
- Respect the existing 1100ms token bucket (Plan 4)

If the existing `geocodeLocation` returns `null` instead of throwing on transient errors, wrap it in the worker's tick (Task 6) rather than refactor here. The plan can adapt.

- [ ] **Step 3: Run existing geocoder tests**

```bash
cd backend && npx jest src/__tests__/geocoder.test.ts
```

Expected: still passing.

- [ ] **Step 4: Commit (only if changes were made)**

```bash
git add backend/src/geocoder.ts
git commit -m "chore(geocoder): expose pure function for reuse by worker"
```

If no changes were needed, skip the commit.

---

## Task 6 — workers/geocodeWorker.ts + tick test

**Files:**
- Create: `backend/src/workers/geocodeWorker.ts`
- Create: `backend/src/__tests__/geocodeWorker.test.ts`

- [ ] **Step 1: Write the failing test**

Create `backend/src/__tests__/geocodeWorker.test.ts`:

```typescript
import { pg, isPgEnabled } from '../db';
import { enqueue } from '../queue/queue';
import { tick } from '../workers/geocodeWorker';
import { __resetGeocoderState } from '../geocoder';

const describeDb = isPgEnabled() ? describe : describe.skip;

async function cleanJobs() {
  if (!isPgEnabled()) return;
  await pg!.query('TRUNCATE background_jobs RESTART IDENTITY');
  await pg!.query("DELETE FROM jobs WHERE title LIKE 'geocode-worker-test-%'");
  __resetGeocoderState();
}

async function insertJob(): Promise<string> {
  // Minimal jobs row so the worker has a target to UPDATE.
  // Read jobs schema first to know required columns. Plan 4's jobs row needs:
  // id, foreman_id, title, status, created_at, updated_at, latitude, longitude, geocoded_at.
  const { v4: uuidv4 } = require('uuid');
  const id = uuidv4();
  const foremanId = 'admin-001'; // bootstrapped by usersService
  await pg!.query(
    `INSERT INTO jobs (id, foreman_id, title, status, created_at, updated_at)
     VALUES ($1, $2, 'geocode-worker-test-' || $1, 'planned', NOW(), NOW())`,
    [id, foremanId]
  );
  return id;
}

describeDb('geocodeWorker.tick', () => {
  let origFetch: typeof fetch;
  beforeEach(async () => {
    await cleanJobs();
    origFetch = global.fetch;
  });
  afterEach(() => { global.fetch = origFetch; });
  afterAll(async () => { await pg?.end(); });

  it('happy path: claims, geocodes, UPDATEs jobs row, completes', async () => {
    global.fetch = (async () => ({
      ok: true,
      status: 200,
      json: async () => [{ lat: '40.7484', lon: '-73.9856' }],
    })) as any;

    const jobId = await insertJob();
    await enqueue({
      kind: 'geocode',
      payload: { job_id: jobId, address: 'Empire State Building' },
      dedupeKey: `geocode:${jobId}`,
    });

    const did = await tick('w-test');
    expect(did).toBe(true);

    const updated = await pg!.query('SELECT latitude, longitude, geocoded_at FROM jobs WHERE id = $1', [jobId]);
    expect(parseFloat(updated.rows[0].latitude)).toBeCloseTo(40.7484, 3);
    expect(parseFloat(updated.rows[0].longitude)).toBeCloseTo(-73.9856, 3);
    expect(updated.rows[0].geocoded_at).toBeTruthy();

    const jobRow = await pg!.query("SELECT state FROM background_jobs WHERE kind='geocode'");
    expect(jobRow.rows[0].state).toBe('succeeded');
  });

  it('returns false when no jobs to claim', async () => {
    const did = await tick('w-test');
    expect(did).toBe(false);
  });

  it('503 from Nominatim: marks failed with retry scheduled', async () => {
    global.fetch = (async () => ({
      ok: false, status: 503, json: async () => null,
    })) as any;

    const jobId = await insertJob();
    await enqueue({ kind: 'geocode', payload: { job_id: jobId, address: 'x' } });

    await tick('w-test');
    const row = await pg!.query("SELECT state, last_error FROM background_jobs WHERE kind='geocode'");
    expect(row.rows[0].state).toBe('failed');
    expect(row.rows[0].last_error).toMatch(/503/);
  });

  it('empty Nominatim result: marks dead immediately (no point retrying)', async () => {
    global.fetch = (async () => ({
      ok: true, status: 200, json: async () => [],
    })) as any;

    const jobId = await insertJob();
    await enqueue({ kind: 'geocode', payload: { job_id: jobId, address: 'fakeville' }, maxAttempts: 1 });

    await tick('w-test');
    const row = await pg!.query("SELECT state, last_error FROM background_jobs WHERE kind='geocode'");
    // With maxAttempts=1 and attempts becoming 1 on claim, fail() sets dead.
    expect(row.rows[0].state).toBe('dead');
    expect(row.rows[0].last_error).toMatch(/no_match|no result|empty/i);
  });
});
```

- [ ] **Step 2: Verify failure**

```bash
cd backend && npx jest src/__tests__/geocodeWorker.test.ts
```

Expected: FAIL — `tick` not exported.

- [ ] **Step 3: Implement geocodeWorker.ts**

Create `backend/src/workers/geocodeWorker.ts`:

```typescript
/**
 * Phase 3 Slice 1: geocode worker.
 *
 * Claims kind='geocode' jobs, calls Nominatim, UPDATEs the jobs row.
 * Closes the Plan 4 fire-and-forget pattern.
 */

import { pg, isPgEnabled } from '../db';
import { claimNext, complete, fail } from '../queue/queue';
import { requestLogger } from '../log';

const KIND = 'geocode';
const NOMINATIM_URL = 'https://nominatim.openstreetmap.org/search';

interface GeocodePayload { job_id: string; address: string; }

export async function tick(workerId: string): Promise<boolean> {
  if (!isPgEnabled() || !pg) return false;
  const job = await claimNext(KIND, workerId);
  if (!job) return false;

  const payload = job.payload as GeocodePayload;
  try {
    const url = `${NOMINATIM_URL}?format=json&q=${encodeURIComponent(payload.address)}`;
    const res = await fetch(url, { headers: { 'User-Agent': 'smith-net/1.0' } });
    if (!res.ok) throw new Error(`nominatim ${res.status}`);
    const arr = (await res.json()) as Array<{ lat: string; lon: string }>;
    if (!arr.length) throw new Error('nominatim no_match');

    await pg.query(
      `UPDATE jobs
          SET latitude = $2::double precision,
              longitude = $3::double precision,
              geocoded_at = NOW(),
              updated_at = NOW()
        WHERE id = $1`,
      [payload.job_id, arr[0].lat, arr[0].lon]
    );

    await complete(job.id);
    requestLogger().info({ event: 'geocode_succeeded', jobId: job.id, lat: arr[0].lat, lon: arr[0].lon }, 'geocode succeeded');
    return true;
  } catch (err) {
    await fail(job.id, err as Error, { attempts: job.attempts, maxAttempts: job.max_attempts });
    requestLogger().warn({ event: 'geocode_failed', jobId: job.id, attempts: job.attempts, err: (err as Error).message }, 'geocode failed');
    return true; // we DID do work (failed work counts) — runner doesn't sleep
  }
}
```

- [ ] **Step 4: Verify tests pass**

```bash
cd backend && npx jest src/__tests__/geocodeWorker.test.ts
```

Expected: PASS — 4 tests.

- [ ] **Step 5: Commit**

```bash
git add backend/src/workers/geocodeWorker.ts backend/src/__tests__/geocodeWorker.test.ts
git commit -m "feat(worker): geocodeWorker.tick — calls Nominatim, UPDATEs jobs row"
```

---

## Task 7 — jobsService: enqueue instead of fire-and-forget

**Files:**
- Modify: `backend/src/jobsService.ts`

- [ ] **Step 1: Find the current geocodeAndUpdate call sites**

```bash
grep -n "geocodeAndUpdate\|geocodeLocation\|fire-and-forget" backend/src/jobsService.ts
```

Expected: 2 calls in `create()` and `update()`, plus the `geocodeAndUpdate` helper function.

- [ ] **Step 2: Replace fire-and-forget with enqueue**

In `backend/src/jobsService.ts`:

**a)** Add import at top:

```typescript
import { enqueue } from './queue/queue';
```

**b)** In `create()`, find the line that calls `geocodeAndUpdate(...)` (it's a non-awaited `void` call per Plan 4). Replace with:

```typescript
// Phase 3 Slice 1: enqueue geocode instead of fire-and-forget.
if (input.location) {
  await enqueue({
    kind: 'geocode',
    payload: { job_id: created.id, address: input.location },
    dedupeKey: `geocode:${created.id}`,
  });
}
```

**c)** Same change in `update()` — when `patch.location` is set, enqueue.

**d)** Delete the `geocodeAndUpdate` helper function entirely. It's now in `geocodeWorker.ts`.

- [ ] **Step 3: Run the full test suite**

```bash
cd backend && npx jest
```

Expected: 148+ passing (144 from prior + 4 geocodeWorker). 0 failures.

If the existing `jobs-routes.test.ts` had a test that exercised the post-create geocode side effect via `__resetGeocoderState()` directly, it now needs adapting:
- The geocode side effect happens via the worker, not directly inside `create()`
- The test should:
  1. Create the job via POST
  2. Assert a `background_jobs` row with `kind='geocode'` exists for that job_id
  3. OPTIONALLY run `tick()` once and assert the jobs row now has lat/lng
- If the existing test was a quick assertion that lat/lng got populated synchronously, change it to either run `tick()` after create, or to only assert the queue row.

- [ ] **Step 4: Run tsc**

```bash
cd backend && npx tsc --noEmit
```

Expected: clean.

- [ ] **Step 5: Commit**

```bash
git add backend/src/jobsService.ts backend/src/
git commit -m "feat(jobs): enqueue geocode instead of fire-and-forget (Phase 3 weak point #2)"
```

---

## Task 8 — workers/runner.ts + npm scripts

**Files:**
- Create: `backend/src/workers/runner.ts`
- Modify: `backend/package.json`

- [ ] **Step 1: Install concurrently**

```bash
cd backend && npm install --save-dev concurrently
```

Verify it lands in `devDependencies`.

- [ ] **Step 2: Create runner.ts**

Create `backend/src/workers/runner.ts`:

```typescript
/**
 * Phase 3 Slice 1: worker process entrypoint.
 *
 * Run via `npm run worker`. Connects to the same Postgres as the api
 * process and loops over each registered worker's tick() function.
 * Sleeps 1s when no work; exits cleanly on SIGTERM / SIGINT.
 *
 * Phase 3 ships 3 workers; Phase 4 adds daemons inside the same runner.
 */

import { tick as geocodeTick } from './geocodeWorker';
import { baseLogger } from '../log';

const WORKER_ID = `${process.pid}@${process.env.HOSTNAME ?? 'host'}`;
const SHUTDOWN = { stop: false };

process.on('SIGTERM', () => { baseLogger.info({ event: 'worker_sigterm' }, 'worker received SIGTERM'); SHUTDOWN.stop = true; });
process.on('SIGINT',  () => { baseLogger.info({ event: 'worker_sigint' },  'worker received SIGINT');  SHUTDOWN.stop = true; });

async function loop(kind: string, fn: (id: string) => Promise<boolean>) {
  while (!SHUTDOWN.stop) {
    const did = await fn(WORKER_ID).catch((e) => {
      baseLogger.error({ event: 'worker_tick_error', kind, err: e }, 'worker tick error');
      return false;
    });
    if (!did) await new Promise((r) => setTimeout(r, 1000));
  }
  baseLogger.info({ event: 'worker_loop_stopped', kind }, 'worker loop stopped');
}

baseLogger.info({ event: 'worker_starting', workerId: WORKER_ID }, 'worker starting');
void loop('geocode', geocodeTick);
// Audit-flush worker registers in Slice 2; email worker in Slice 3.
```

- [ ] **Step 3: Add npm scripts**

In `backend/package.json`, add to `scripts`:

```json
"worker": "tsx src/workers/runner.ts",
"dev:all": "concurrently --kill-others-on-fail 'npm:dev' 'npm:worker'"
```

The `--kill-others-on-fail` means a worker crash also kills the api (and vice versa) — both restart together in dev.

- [ ] **Step 4: Smoke test the runner**

```bash
export PATH=/opt/homebrew/opt/postgresql@16/bin:$PATH
export DATABASE_URL=postgres://fegensprenelon@localhost:5432/smithnet
cd backend && DATABASE_URL=$DATABASE_URL npm run worker > /tmp/p3s1-runner.log 2>&1 &
sleep 4
# Verify it's logging
grep -E "worker_starting|worker_tick_error" /tmp/p3s1-runner.log | head -5

# Stop it
kill $(pgrep -f "tsx src/workers/runner.ts") 2>/dev/null
sleep 2
tail -3 /tmp/p3s1-runner.log
```

Expected:
- `worker_starting` log line with workerId
- No `worker_tick_error` lines (no work to do; geocodeTick returns false then sleeps)
- On kill: `worker_sigterm` and `worker_loop_stopped` lines

- [ ] **Step 5: Commit**

```bash
git add backend/src/workers/runner.ts backend/package.json backend/package-lock.json
git commit -m "feat(worker): runner.ts entrypoint + npm run worker / dev:all"
```

---

## Task 9 — Slice 1 closeout: end-to-end smoke + tag

**Files:** none (this is integration + tag)

- [ ] **Step 1: Run the FULL backend test suite one final time**

```bash
cd backend && npx jest 2>&1 | tail -5
```

Expected: 148+ tests passing.

### Step 2: End-to-end smoke (api + worker + real geocode)

```bash
export PATH=/opt/homebrew/opt/postgresql@16/bin:$PATH
export DATABASE_URL=postgres://fegensprenelon@localhost:5432/smithnet

# Boot api
cd backend && DATABASE_URL=$DATABASE_URL npm run dev > /tmp/p3s1-api.log 2>&1 &
sleep 6
# Boot worker
DATABASE_URL=$DATABASE_URL npm run worker > /tmp/p3s1-worker.log 2>&1 &
sleep 3

# Register + login + create a job with a location
SMOKE_EMAIL="p3s1-smoke-$(date +%s)@example.com"
curl -s -X POST http://localhost:3030/api/auth/register \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$SMOKE_EMAIL\",\"password\":\"password123\",\"displayName\":\"P3S1\"}" > /dev/null

LOGIN=$(curl -s -i -X POST http://localhost:3030/api/auth/login \
  -H 'Content-Type: application/json' \
  -d "{\"email\":\"$SMOKE_EMAIL\",\"password\":\"password123\"}")
TOKEN=$(echo "$LOGIN" | grep -oE 'smithnet_access=[^;]+' | head -1 | cut -d= -f2)

# Create a job that needs geocoding
CREATE=$(curl -s -X POST http://localhost:3030/api/jobs \
  -H 'Content-Type: application/json' \
  -H "Cookie: smithnet_access=$TOKEN" \
  -d '{"title":"P3S1 smoke","location":"Empire State Building"}')
JOB_ID=$(echo "$CREATE" | grep -oE '"id":"[^"]+"' | head -1 | cut -d'"' -f4)
echo "Created job: $JOB_ID"

# Wait for the worker to pick it up (geocode token bucket = 1.1s)
sleep 3

# Verify the queue row succeeded
psql "$DATABASE_URL" -c "SELECT state, attempts FROM background_jobs WHERE kind='geocode' AND payload->>'job_id' = '$JOB_ID';"

# Verify the jobs row has lat/lng
psql "$DATABASE_URL" -c "SELECT latitude, longitude FROM jobs WHERE id = '$JOB_ID';"

# Inspect worker log for geocode_succeeded event
grep "geocode_succeeded" /tmp/p3s1-worker.log | head -1

# Stop both processes
kill $(pgrep -f "tsx watch src/server.ts") 2>/dev/null
kill $(pgrep -f "tsx src/workers/runner.ts") 2>/dev/null
sleep 2
```

Expected:
- queue row: state='succeeded', attempts=1
- jobs row: latitude ≈ 40.7484, longitude ≈ -73.9856
- worker log contains `geocode_succeeded` with the job id

If all three signals are present, the end-to-end pipeline works.

- [ ] **Step 3: Document the manual stuck-row reset**

Create `OPERATIONS.md` at project root (or, if it already exists, add a section to it):

```markdown
# Smith Net Operations Runbook

## Stuck running background_jobs (Phase 3 interim)

Until Phase 4's `queueWatcherDaemon` ships, a worker that crashes mid-job
leaves its row in `state='running'` indefinitely. Operator recipe:

```sql
UPDATE background_jobs
   SET state = 'queued',
       locked_at = NULL,
       locked_by = NULL
 WHERE state = 'running'
   AND locked_at < NOW() - INTERVAL '10 minutes';
```

Run via `psql "$DATABASE_URL" -c "<sql>"` whenever the queue shows running
rows older than 10 minutes. Frequency: as needed; the queueWatcherDaemon
will automate this in Phase 4.
```

- [ ] **Step 4: Commit + tag**

```bash
git add OPERATIONS.md
git commit -m "chore(phase-3): close slice 1 — queue infra + geocode worker

- background_jobs table + queue.ts (enqueue/claimNext/complete/fail)
- workers/runner.ts entrypoint + npm run worker / dev:all
- geocodeWorker replaces Plan 4 fire-and-forget
- OPERATIONS.md documents the stuck-row interim recipe
- audit weak point #2 partially closed; tag at Slice 3 closes it fully"
git tag -a phase-3-slice-1 -m "Phase 3 Slice 1 — queue infra + geocode worker"
```

- [ ] **Step 5: Verify**

```bash
git log --oneline phase-2..phase-3-slice-1
git tag --list 'phase-3*'
```

Expected: Slice 1's ~9 commits visible; tag `phase-3-slice-1` exists.

---

## What slice 1 did NOT do

- Did **not** migrate `auditLog.log()` — that's Slice 2.
- Did **not** migrate email sends — that's Slice 3.
- Did **not** ship the queueWatcherDaemon — operator runs SQL manually until Phase 4.
- Did **not** annotate audit weak point #2 as fully closed — that happens at Slice 3 closeout (all 3 fire-and-forget patterns gone).

Slice 2 plan to follow when slice 1 is in main.
