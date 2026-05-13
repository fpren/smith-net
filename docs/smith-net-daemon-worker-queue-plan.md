# Smith Net Daemon / Worker / Queue Plan

Companion to [smith-net-architecture-audit.md](./smith-net-architecture-audit.md).

---

## Why Now

Three triggers, all already in the tree:

1. **Plan 4's geocode is fire-and-forget.** `jobsService.ts` enqueues nothing; it just kicks off an async Nominatim call after `INSERT`. No retry, no dedup, no observability. If Nominatim is slow during the request handler, the API blocks; if the server restarts mid-call, the job sits unpinned forever.
2. **`auditLog.ts` flushes to disk inline with writes.** A slow disk slows every audited write. There's a `setInterval` cleanup but no flush worker.
3. **`llmInterface.ts` (469 LOC) has zero callers today, but the moment SmithAI Android or server-side synthesis turns AI on, every LLM call will block the request path unless we have a worker now.**

These three drive a single conclusion: introduce a background-job system before another feature lands on top of fire-and-forget patterns.

---

## Design Decisions

| Decision | Choice | Reason |
|---|---|---|
| Queue store | Postgres (`background_jobs` table) | Already deployed; `FOR UPDATE SKIP LOCKED` is enough for tens of thousands of jobs/day; no second runtime |
| Worker process | Separate Node entrypoint (`backend/src/workers/runner.ts`) | Isolates back-pressure from API event loop |
| Scheduler | `scheduled_at` column + watcher daemon | One mechanism for cron and delayed retries |
| Concurrency | 2 workers at launch (geocode, audit-flush) | Match real demand; expand by kind |
| Retry | Exponential backoff in pg row (`attempts`, `next_attempt_at`) | No external retry lib |
| Dead-letter | `state='dead'` after N attempts | Just a state value, queryable like everything else |
| Idempotency | `dedupe_key` unique partial index | Caller sends a stable key (e.g. `geocode:<job_id>`); duplicate enqueue is a no-op |
| Observability | `heartbeat` rows in same table + `/api/admin/health` | One table for everything operators need |

---

## DB Schema

Single migration, `004_background_jobs.sql`.

```sql
-- 004_background_jobs.sql

CREATE TYPE bg_job_state AS ENUM (
  'queued',     -- waiting for a worker
  'running',    -- locked by a worker
  'succeeded',  -- terminal success
  'failed',     -- transient failure; eligible for retry
  'dead'        -- exceeded max attempts
);

CREATE TABLE background_jobs (
  id              BIGSERIAL PRIMARY KEY,
  kind            TEXT NOT NULL,            -- 'geocode' | 'audit_flush' | 'email' | 'invoice_draft' | 'llm_call' | 'cleanup' | 'heartbeat'
  payload         JSONB NOT NULL DEFAULT '{}'::jsonb,
  state           bg_job_state NOT NULL DEFAULT 'queued',
  attempts        INT NOT NULL DEFAULT 0,
  max_attempts    INT NOT NULL DEFAULT 5,
  scheduled_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  locked_at       TIMESTAMPTZ,
  locked_by       TEXT,                     -- worker process id
  last_error      TEXT,
  dedupe_key      TEXT,                     -- optional caller-provided idempotency key
  created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
  finished_at     TIMESTAMPTZ
);

-- Hot path: claim next ready job by kind
CREATE INDEX bg_jobs_claim_idx
  ON background_jobs (kind, scheduled_at)
  WHERE state = 'queued';

-- Dedupe across active states
CREATE UNIQUE INDEX bg_jobs_dedupe_idx
  ON background_jobs (kind, dedupe_key)
  WHERE state IN ('queued', 'running', 'failed');

-- Heartbeats and observability
CREATE INDEX bg_jobs_state_idx ON background_jobs (state, kind);
CREATE INDEX bg_jobs_finished_idx ON background_jobs (finished_at)
  WHERE state IN ('succeeded', 'dead');
```

Notes:
- `bg_jobs_dedupe_idx` is partial so terminal rows can sit around for audit without blocking re-enqueue.
- `dedupe_key` is optional. Callers that don't care leave it null.
- `heartbeat` rows are written by daemons themselves (kind='heartbeat', state='succeeded', payload={component, ts}). Cleanup deletes them after N hours.

Audit table (separate, lives in its own migration):

```sql
-- 005_audit_entries.sql (separate from background_jobs)

CREATE TABLE audit_entries (
  id          BIGSERIAL PRIMARY KEY,
  ts          TIMESTAMPTZ NOT NULL DEFAULT now(),
  actor_id    TEXT,
  action      TEXT NOT NULL,
  target      TEXT,
  payload     JSONB NOT NULL DEFAULT '{}'::jsonb,
  prev_hash   TEXT,
  hash        TEXT NOT NULL UNIQUE
);

CREATE INDEX audit_actor_ts_idx ON audit_entries (actor_id, ts DESC);
CREATE INDEX audit_action_ts_idx ON audit_entries (action, ts DESC);
```

---

## Recommended Daemons / Workers / Queues

### Workers

For each: name / trigger / consumes / produces / failure behavior / AI?

| Worker | Trigger | Consumes (queue kind) | Produces | Failure behavior | AI? |
|---|---|---|---|---|---|
| `geocodeWorker` | New row of kind=`geocode` | `geocode` (payload: `{job_id, address}`) | UPDATE `jobs SET lat, lng, geocoded_at` | Backoff 30s, 2m, 10m, 1h, 6h; `dead` after 5 | No |
| `auditFlushWorker` | Row of kind=`audit_flush` (enqueued by `auditLog.append`) | `audit_flush` (payload: `{entry}`) | INSERT into `audit_entries` + JSONL backup | Retry 3x with 5s gap; on final fail, write to `audit_dead_letter` JSONL | No |
| `emailWorker` | Row of kind=`email` | `email` (payload: `{to, template_id, vars}`) | SMTP send via `emailService.ts` | Retry 4x, backoff 1m/5m/30m/2h | No (template rendering only) |
| `invoiceDraftWorker` | Row of kind=`invoice_draft` | `invoice_draft` (payload: `{job_id, shift_id}`) | Calls `invoiceGenerator.ts`; INSERT draft invoice | Retry 3x; failure is operator-visible | No |
| `reportRenderWorker` | Row of kind=`report_render` | `report_render` (payload: `{report_id}`) | Calls `reportRenderer.ts` (455 LOC); writes file | Retry 3x | No |
| `llmWorker` | Row of kind=`llm_call` (the only worker that talks to LLM) | `llm_call` (payload: `{prompt_id, vars, cache_key, max_tokens}`) | INSERT into `llm_responses` (new table later); also fills `cache_entries` | Retry once on 429/5xx; never retry on 4xx | **Yes** |
| `cleanupWorker` | Row of kind=`cleanup` (enqueued by daemon) | `cleanup` (payload: `{target}`) | Deletes/archives old media, audit JSONL, dead bg_jobs | Soft-fail; log and continue | No |

### Daemons

Long-running watchers that enqueue work or sweep state. All three run inside `workers/runner.ts`.

| Daemon | Cadence | Reads | Writes | Failure behavior |
|---|---|---|---|---|
| `heartbeatDaemon` | every 30s | nothing | INSERT `background_jobs` (kind=`heartbeat`, state=`succeeded`) | Worker process restart on 3 missed ticks |
| `queueWatcherDaemon` | every 5s | `background_jobs` aggregate counts | UPDATE state of stuck `running` rows back to `queued` if `locked_at < now() - 10m` | Logs only |
| `cleanupDaemon` | every 1h | `background_jobs WHERE finished_at < now() - interval '7d'` | Enqueues `cleanup` jobs for media, audit JSONL retention, dead-letter rotation | Logs only |
| `presenceWatcherDaemon` (later) | every 60s | `presence` rows | Marks stale presence after 5m disconnect | Logs only |

### Queues

There is one queue (the table). Different `kind` values partition the work. Workers `WHERE kind = 'geocode'` (etc.) and `FOR UPDATE SKIP LOCKED`.

No Redis. No RabbitMQ. No SQS.

---

## Code Skeleton

The pattern is intentionally small.

### `backend/src/queue/queue.ts` — enqueue + claim API

```ts
import { pool } from '../db';

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

export async function enqueue(opts: EnqueueOptions): Promise<{ id: number; created: boolean }> {
  const r = await pool.query(
    `INSERT INTO background_jobs (kind, payload, scheduled_at, dedupe_key, max_attempts)
     VALUES ($1, $2::jsonb, COALESCE($3, now()), $4, COALESCE($5, 5))
     ON CONFLICT (kind, dedupe_key) WHERE state IN ('queued','running','failed')
     DO NOTHING
     RETURNING id`,
    [opts.kind, JSON.stringify(opts.payload), opts.scheduledAt ?? null, opts.dedupeKey ?? null, opts.maxAttempts ?? null]
  );
  if (r.rowCount === 0) return { id: -1, created: false };
  return { id: r.rows[0].id, created: true };
}

export async function claimNext(kind: BgJobKind, workerId: string) {
  const r = await pool.query(
    `UPDATE background_jobs
       SET state = 'running', locked_at = now(), locked_by = $2, attempts = attempts + 1
     WHERE id = (
       SELECT id FROM background_jobs
        WHERE kind = $1 AND state = 'queued' AND scheduled_at <= now()
        ORDER BY scheduled_at
        FOR UPDATE SKIP LOCKED
        LIMIT 1
     )
     RETURNING id, payload, attempts, max_attempts`,
    [kind, workerId]
  );
  return r.rows[0] ?? null;
}

export async function complete(id: number) {
  await pool.query(
    `UPDATE background_jobs SET state='succeeded', finished_at=now(), updated_at=now() WHERE id=$1`,
    [id]
  );
}

export async function fail(id: number, err: Error, opts: { attempts: number; maxAttempts: number }) {
  const dead = opts.attempts >= opts.maxAttempts;
  const backoffSec = Math.min(60 * Math.pow(3, opts.attempts), 6 * 3600);
  await pool.query(
    `UPDATE background_jobs
       SET state = $2,
           last_error = $3,
           scheduled_at = CASE WHEN $2 = 'failed' THEN now() + ($4::int * interval '1 second') ELSE scheduled_at END,
           finished_at = CASE WHEN $2 = 'dead' THEN now() ELSE NULL END,
           locked_at = NULL,
           locked_by = NULL,
           updated_at = now()
     WHERE id = $1`,
    [id, dead ? 'dead' : 'failed', err.message.slice(0, 1000), backoffSec]
  );
}
```

### `backend/src/workers/geocodeWorker.ts` — one worker file

```ts
import { claimNext, complete, fail } from '../queue/queue';
import { pool } from '../db';

const KIND = 'geocode';

interface GeocodePayload { job_id: string; address: string; }

export async function tick(workerId: string): Promise<boolean> {
  const job = await claimNext(KIND, workerId);
  if (!job) return false;
  const payload = job.payload as GeocodePayload;
  try {
    const res = await fetch(`https://nominatim.openstreetmap.org/search?format=json&q=${encodeURIComponent(payload.address)}`, {
      headers: { 'User-Agent': 'smith-net/1.0' },
    });
    if (!res.ok) throw new Error(`nominatim ${res.status}`);
    const arr = (await res.json()) as Array<{ lat: string; lon: string }>;
    if (!arr.length) throw new Error('no match');
    await pool.query(
      `UPDATE jobs SET lat = $2::numeric, lng = $3::numeric, geocoded_at = now() WHERE id = $1`,
      [payload.job_id, arr[0].lat, arr[0].lon]
    );
    await complete(job.id);
  } catch (err) {
    await fail(job.id, err as Error, { attempts: job.attempts, maxAttempts: job.max_attempts });
  }
  return true;
}
```

### `backend/src/workers/runner.ts` — separate process entrypoint

```ts
import { tick as geocodeTick } from './geocodeWorker';
import { tick as auditTick } from './auditFlushWorker';
import { tick as emailTick } from './emailWorker';
import { runHeartbeat } from '../daemons/heartbeatDaemon';
import { runQueueWatcher } from '../daemons/queueWatcherDaemon';
import { runCleanup } from '../daemons/cleanupDaemon';

const WORKER_ID = `${process.pid}@${process.env.HOSTNAME ?? 'host'}`;
const SHUTDOWN = { stop: false };

process.on('SIGTERM', () => { SHUTDOWN.stop = true; });
process.on('SIGINT', () => { SHUTDOWN.stop = true; });

async function loop(kind: string, fn: (id: string) => Promise<boolean>) {
  while (!SHUTDOWN.stop) {
    const did = await fn(WORKER_ID).catch((e) => { console.error(`[${kind}]`, e); return false; });
    if (!did) await new Promise((r) => setTimeout(r, 1000));
  }
}

void loop('geocode', geocodeTick);
void loop('audit_flush', auditTick);
void loop('email', emailTick);

void runHeartbeat(SHUTDOWN);
void runQueueWatcher(SHUTDOWN);
void runCleanup(SHUTDOWN);
```

Run via `npm run worker` (new `package.json` script: `"worker": "tsx src/workers/runner.ts"`).

### `backend/src/daemons/heartbeatDaemon.ts`

```ts
import { pool } from '../db';

export async function runHeartbeat(state: { stop: boolean }) {
  while (!state.stop) {
    await pool.query(
      `INSERT INTO background_jobs (kind, state, payload, finished_at)
       VALUES ('heartbeat','succeeded', jsonb_build_object('component','runner','pid', $1::text), now())`,
      [process.pid]
    );
    await new Promise((r) => setTimeout(r, 30_000));
  }
}
```

---

## Retry Rules

| Outcome | Action |
|---|---|
| Worker throws | `fail()`; backoff = `min(60 * 3^attempts, 6h)`; retry until `max_attempts` then `dead` |
| 4xx response from external service | Mark `dead` immediately (caller bug, not transient) — handled in each worker's try block |
| 429 / 5xx response | Retry with backoff; respect `Retry-After` header when present |
| Worker process killed mid-job | `locked_at < now() - 10m` AND `state='running'` -> `queueWatcherDaemon` resets to `queued` |
| Job exceeds `max_attempts` | State=`dead`; row stays for audit; cleanup deletes after 30d |

---

## Dead-Letter Queue

There is no separate table. `state='dead'` rows ARE the dead-letter. Operators view via:

```sql
SELECT id, kind, attempts, last_error, scheduled_at
  FROM background_jobs
 WHERE state = 'dead' AND finished_at > now() - interval '7d'
 ORDER BY finished_at DESC;
```

An operator script (`backend/scripts/requeue.ts`) can flip a dead row back to `queued` after a fix.

---

## Idempotency / Dedup

Use `dedupeKey`. Examples:

| Worker kind | Dedupe key pattern |
|---|---|
| `geocode` | `geocode:<job_id>` |
| `invoice_draft` | `invoice_draft:<job_id>:<shift_id>` |
| `email` | `email:<template_id>:<recipient>:<message_id>` |
| `llm_call` | `llm:<prompt_id>:<sha256(input_digest)>` |
| `cleanup` | `cleanup:<target>:<yyyymmdd>` |

Two callers enqueuing the same key get a single row. `enqueue()` returns `{created: false}` for the second caller.

---

## How Existing Modules Plug In

| Existing module | Change |
|---|---|
| `jobsService.ts` (300 LOC) | After `INSERT`, call `enqueue({kind:'geocode', payload:{job_id, address}, dedupeKey:'geocode:'+id})` instead of fire-and-forget |
| `auditLog.ts` (352 LOC) | `append()` writes to a small in-memory buffer + enqueues `audit_flush`; the worker drains buffer to pg `audit_entries` + JSONL backup |
| `invoiceGenerator.ts` (301 LOC) | Called by `invoiceDraftWorker`, not from request handlers |
| `reportRenderer.ts` (455 LOC) | Called by `reportRenderWorker` |
| `emailService.ts` (81 LOC) | Called by `emailWorker` only |
| `llmInterface.ts` (469 LOC) | Called by `llmWorker` only; never from a route handler |
| `mediaHandler.ts` cleanup (currently `setInterval`) | Replaced by `cleanupDaemon` enqueueing `cleanup` jobs |

---

## Operational Surface

- **Process model.** Two long-running processes: `api` (server.ts) and `worker` (workers/runner.ts). Both connect to the same pg.
- **Deploy.** `pm2` or `systemd` for each; or two services in Hetzner Express. Worker is restart-tolerant: any locked job comes back via the `queueWatcherDaemon`.
- **Health.** `/api/admin/health` reads recent heartbeats and oldest pending row per kind. Alarm if oldest pending > 5m for kind=`email` (etc.).
- **Cost.** Zero new infra. One table, one extra Node process.

See `smith-net-implementation-roadmap.md` for the build order.
