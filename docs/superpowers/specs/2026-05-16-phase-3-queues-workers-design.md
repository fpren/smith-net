# Phase 3 — Queues + Workers

**Date:** 2026-05-16
**Roadmap reference:** `docs/smith-net-implementation-roadmap.md` § Phase 3
**Companion doc (pre-existing):** `docs/smith-net-daemon-worker-queue-plan.md`
**Predecessor tag:** `phase-2`
**Target ship tag:** `phase-3` (~3 weeks)
**Status:** approved design, ready for implementation plan

---

## Goal

Move fire-and-forget patterns off the request path onto a Postgres-backed `background_jobs` queue with a separate worker process. Three workloads migrate: geocode (Plan 4), audit-flush (Phase 2 Slice 2), email (auth verification). Closes audit weak point #2.

## Constraints (decided 2026-05-16)

1. **Strict roadmap scope.** Phase 3 ships only the queue infrastructure + 3 workers. Daemons (heartbeat, queueWatcher, cleanup) are deferred to Phase 4. A worker process crash mid-job leaves a row in `state='running'` indefinitely; until Phase 4's `queueWatcherDaemon` ships, the operator runs a manual SQL reset documented in `OPERATIONS.md`. The audit explicitly accepts this gap.
2. **Worker computes the audit chain hash.** `auditLog.log()` enqueues raw entry data and returns `{auditId, queued: true}` immediately. The `auditFlushWorker` drains the queue, computes `prev_hash`+`hash` under `pg_advisory_xact_lock(42)`, and inserts the row into `audit_entries`. The current `Promise<AuditEntry>` return contract from Phase 2 Slice 2 changes; 17 callers get updated.
3. **Sequencing: 3 slices, weak-point priority.** (1) infra + geocode (kills the Plan 4 fire-and-forget), (2) audit-flush (riskiest — changes auditLog contract), (3) email + closeout. Each slice ships independently and is tag-aligned.

## Architecture

Phase 3 introduces a single new table (`background_jobs`), a `queue/` module, a `workers/` directory with a separate Node entrypoint, and modifies 3 existing modules.

### Migration

The migration number picks up from Phase 2's last: `009_background_jobs.sql`. Schema lifted directly from `docs/smith-net-daemon-worker-queue-plan.md`:

```sql
CREATE TYPE bg_job_state AS ENUM (
  'queued', 'running', 'succeeded', 'failed', 'dead'
);

CREATE TABLE background_jobs (
  id              BIGSERIAL PRIMARY KEY,
  kind            TEXT NOT NULL,
  payload         JSONB NOT NULL DEFAULT '{}'::jsonb,
  state           bg_job_state NOT NULL DEFAULT 'queued',
  attempts        INT NOT NULL DEFAULT 0,
  max_attempts    INT NOT NULL DEFAULT 5,
  scheduled_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  locked_at       TIMESTAMPTZ,
  locked_by       TEXT,
  last_error      TEXT,
  dedupe_key      TEXT,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  finished_at     TIMESTAMPTZ
);

CREATE INDEX bg_jobs_claim_idx ON background_jobs (kind, scheduled_at) WHERE state = 'queued';
CREATE UNIQUE INDEX bg_jobs_dedupe_idx ON background_jobs (kind, dedupe_key)
  WHERE state IN ('queued', 'running', 'failed');
CREATE INDEX bg_jobs_state_idx ON background_jobs (state, kind);
```

### New backend layout

```
backend/src/
├── queue/
│   └── queue.ts              NEW — enqueue / claimNext / complete / fail
├── workers/
│   ├── runner.ts             NEW — separate Node entrypoint
│   ├── geocodeWorker.ts      NEW — tick() for kind='geocode'
│   ├── auditFlushWorker.ts   NEW — tick() for kind='audit_flush'
│   └── emailWorker.ts        NEW — tick() for kind='email'
├── jobsService.ts            MODIFIED — geocode becomes enqueue
├── auditLog.ts               MODIFIED — log() enqueues; worker writes
└── (auth/email callers)      MODIFIED — switch to enqueue
```

`backend/package.json` gets two new scripts:

```json
"worker": "tsx src/workers/runner.ts",
"dev:all": "concurrently 'npm run dev' 'npm run worker'"
```

`concurrently` is added as a `devDependency` for the `dev:all` ergonomic. Production deploy uses two services (one for api, one for worker) — pm2 ecosystem or two systemd units.

### Queue API

`backend/src/queue/queue.ts` exports four functions matching the daemon-worker-queue doc:

- `enqueue(opts: EnqueueOptions): Promise<{ id: number; created: boolean }>` — INSERT with `ON CONFLICT (kind, dedupe_key) WHERE state IN ('queued','running','failed') DO NOTHING`. Returns `created: false` on dedupe hit.
- `claimNext(kind, workerId): Promise<JobRow | null>` — UPDATE ... WHERE id IN (SELECT id FROM background_jobs WHERE kind=$1 AND state='queued' AND scheduled_at <= NOW() FOR UPDATE SKIP LOCKED LIMIT 1).
- `complete(id): Promise<void>` — UPDATE state='succeeded', finished_at=NOW().
- `fail(id, err, opts): Promise<void>` — UPDATE state='failed' (with backoff schedule) or 'dead' (if attempts >= max_attempts). Backoff: `min(60 * 3^attempts, 6 * 3600)` seconds.

### Worker pattern

Each worker exports a `tick(workerId): Promise<boolean>` function. The runner loops over registered ticks, sleeps 1s when no work, exits cleanly on SIGTERM/SIGINT:

```typescript
async function loop(kind: string, fn: (id: string) => Promise<boolean>) {
  while (!SHUTDOWN.stop) {
    const did = await fn(WORKER_ID).catch((e) => { console.error(`[${kind}]`, e); return false; });
    if (!did) await new Promise((r) => setTimeout(r, 1000));
  }
}
```

### Slice 1 — Infrastructure + geocode (~1 week)

**Closes the Plan 4 fire-and-forget geocode pattern.**

- Migration 009 + queue.ts (4 functions, full test coverage)
- runner.ts with workerId = `${process.pid}@${hostname}`
- geocodeWorker.ts wraps the existing Nominatim call from `jobsService.geocodeAndUpdate` (the function moves into the worker; jobsService no longer touches Nominatim)
- `jobsService.create()` and `update()`: replace `void geocodeAndUpdate(...)` with `await enqueue({kind:'geocode', dedupeKey:'geocode:'+jobId, payload:{job_id, address}})`
- Tests:
  - `queue.enqueue+claim+complete` happy path
  - Dedupe: two enqueues with same dedupeKey yield one row, second returns `created: false`
  - Geocode 503 retry: mock Nominatim returns 503, 503, 200 → row succeeds on third attempt with backoff
  - Worker crash simulation: manually `UPDATE state='queued' WHERE state='running' AND locked_at < NOW() - INTERVAL '10 minutes'` proves the manual-reset recipe works
- Ship tag: `phase-3-slice-1`

### Slice 2 — audit-flush worker (~1 week)

**Closes the sync pg write from Phase 2 Slice 2.**

The `auditLog.log()` change is non-trivial. The Phase 2 Slice 2 signature:

```typescript
async log(action, actorId, metadata?, options?): Promise<AuditEntry>
```

Becomes:

```typescript
async log(action, actorId, metadata?, options?): Promise<{ auditId: string; queued: true }>
```

The `auditId` is the dedupe-style id (e.g. `audit-<timestamp>-<counter>`) — kept in payload so the worker can correlate. The worker computes the chain hash and writes the actual row.

`auditFlushWorker.tick()`:

```
1. claimNext('audit_flush')
2. BEGIN; SELECT pg_advisory_xact_lock(42);
3. SELECT hash FROM audit_entries ORDER BY id DESC LIMIT 1  -- prev_hash
4. Compute hash = SHA256(prev_hash + body)
5. INSERT INTO audit_entries (...)
6. COMMIT (releases lock)
7. (also append to JSONL buffer — 60s flush stays from Phase 2)
8. complete(jobId)
```

The 17 callers of `auditLog.log()` get the new return type. Most don't read the return value at all — they fire-and-store. A handful (e.g., in admin routes that show the most recent audit row) need updating to query `audit_entries` directly instead of using the return value's `checksum`.

Test pattern:

```typescript
// Helper: poll until all queued audit_flush jobs drain
async function waitForAuditDrain(timeoutMs = 5000) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    const { rows } = await pg!.query(
      `SELECT COUNT(*) FROM background_jobs WHERE kind='audit_flush' AND state IN ('queued','running')`
    );
    if (parseInt(rows[0].count, 10) === 0) return;
    await new Promise(r => setTimeout(r, 50));
  }
  throw new Error('audit_flush jobs did not drain');
}
```

Tests:
- 10 sequential `log()` calls → `waitForAuditDrain()` → recompute chain from `audit_entries` rows, assert match
- Concurrent log() calls (Promise.all of 5) → drain → 5 valid chain entries in some order (advisory lock guarantees serialization)
- Worker failure path: SMTP-style transient error → state='failed' with `last_error`, retries with backoff, eventually succeeds

Ship tag: `phase-3-slice-2`

### Slice 3 — email worker + closeout (~1 week)

**Closes the last fire-and-forget pattern (auth verification email send) and tags Phase 3.**

- `workers/emailWorker.ts` wraps `emailService.sendVerificationEmail` (and any future email types — invoices, notifications)
- Caller updates: `authRoutes.ts` (register flow, resend-verification flow) calls `await enqueue({kind:'email', dedupeKey:'email:verify:'+userId+':'+token, payload:{...}})` instead of `await emailService.sendVerificationEmail(...)`
- `emailService.ts` becomes internal-only (only `emailWorker` imports it)
- Tests:
  - emailWorker retries 4x on simulated SMTP error
  - Dedupe prevents two verification emails when register hits race
  - emailService is no longer called from any route handler (grep-based test)
- **Closeout:**
  - Annotate audit weak point #2 in `docs/smith-net-architecture-audit.md` with `[closed in phase-3, commit <SHA>]`
  - Create `OPERATIONS.md` (or add a section to existing project-root docs) with the stuck-row SQL recipe:
    ```sql
    -- Reset stuck running rows (run after worker crash, until Phase 4's queueWatcherDaemon ships)
    UPDATE background_jobs
       SET state = 'queued', locked_at = NULL, locked_by = NULL
     WHERE state = 'running'
       AND locked_at < NOW() - INTERVAL '10 minutes';
    ```
  - Tag `phase-3-slice-3` and `phase-3`

## Testing

Real Postgres via `DATABASE_URL_TEST` gate (Phase 2 pattern). Each worker's `tick(workerId)` is integration-testable in isolation — it claims work, does the work, completes/fails. Mocks:

- Nominatim: existing `__resetGeocoderState()` + global `fetch` mock (per Plan 4 pattern)
- SMTP: dependency-inject a transport stub via env-controlled email service config

The 60s JSONL flush from Phase 2 Slice 2 stays unchanged — it's downstream of the pg write, not of the queue.

## Dev workflow

Two npm scripts:
- `npm run dev` — API server (existing)
- `npm run worker` — workers/runner.ts (new)
- `npm run dev:all` — both via `concurrently` (new devDep)

For production: two services. Hetzner deploy notes added in Slice 1's closeout (or sooner if practical).

## Risks

1. **Worker process management in production.** Hetzner currently runs one process. Phase 3 needs two. Documented in Slice 1; deploy automation work is out of scope (manual until needed).
2. **Audit chain visibility lag.** Between `log()` enqueue and worker write (typically <100ms), the row isn't in `audit_entries`. Callers reading the chain in the same request (none today) would see stale data. Documented in `OPERATIONS.md`.
3. **17 caller updates for `auditLog.log()`.** Mechanical but voluminous. The few callers that read the return value's `checksum` field need refactoring to query pg directly. tsc will surface them after the signature changes.
4. **No queueWatcherDaemon.** A worker crash mid-job leaves `state='running'` forever. Manual SQL recipe is the interim. Acknowledged in `OPERATIONS.md`.
5. **`bg_job_state` enum vs Postgres versioning.** The enum is a `CREATE TYPE`. If a future phase wants to add a state (e.g., `paused`), it requires `ALTER TYPE bg_job_state ADD VALUE`. Acceptable.

## Done criteria

- 3 slices merged onto `feat/relay-hetzner-postgres`, each with its own slice tag
- Repo tagged `phase-3`
- `smith-net-architecture-audit.md` weak point #2 marked `[closed in phase-3, commit <SHA>]`
- `jobsService.create()/update()` no longer call `geocodeAndUpdate` directly — they enqueue
- `auditLog.log()` returns `{auditId, queued: true}` immediately
- `emailService` is only imported by `emailWorker.ts`
- `npm run worker` runs the worker process
- `OPERATIONS.md` exists with the stuck-row recipe
- 140+ tests passing (135 baseline + ~10 new across all 3 slices)
- No regressions in existing test suite

## What Phase 3 does NOT do

- No daemons. heartbeat / queueWatcher / cleanup all come in Phase 4.
- No `/api/admin/health` endpoint. That's a daemons-readout, which is Phase 4.
- No `invoiceDraft` / `reportRender` / `llmCall` / `cleanup` workers. Those are Phase 4 (operational) or Phase 5 (AI).
- No deploy automation for the two-process model. Manual `pm2` or systemd setup until production demands more.
- No `audit_dead_letter` JSONL fallback for repeatedly-failed audit writes. The pg row in `state='dead'` is the dead-letter; operators query it.

## Cross-reference

| Audit weak point | Closed in slice |
|---|---|
| #2 No background-job system | 1, 2, 3 (all of phase-3) |

Other weak points addressed elsewhere:
- #3 Dead Phase-0 routes — Phase 4
- #7 api.ts size — Phase 4
- #8-10 LLM-related — Phase 5
