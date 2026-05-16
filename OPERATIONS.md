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

Run via:

```bash
psql "$DATABASE_URL" -c "<sql>"
```

whenever the queue shows running rows older than 10 minutes.

Diagnostic query — show stuck rows:

```sql
SELECT id, kind, locked_by, locked_at, attempts
  FROM background_jobs
 WHERE state = 'running'
   AND locked_at < NOW() - INTERVAL '10 minutes'
 ORDER BY locked_at;
```

Frequency: as needed. The queueWatcherDaemon (Phase 4) will automate this on a 5s cadence.

## Dead jobs (terminal failure)

Jobs that exceed `max_attempts` go to `state='dead'`. Inspect:

```sql
SELECT id, kind, attempts, last_error, scheduled_at
  FROM background_jobs
 WHERE state = 'dead' AND finished_at > NOW() - INTERVAL '7d'
 ORDER BY finished_at DESC;
```

To re-queue a dead row after fixing the root cause:

```sql
UPDATE background_jobs
   SET state = 'queued',
       attempts = 0,
       last_error = NULL,
       scheduled_at = NOW(),
       finished_at = NULL
 WHERE id = <id>;
```

## Process layout

Production runs two long-lived Node processes:

- `npm run dev` (or `node dist/server.js` in prod) — Express API on port 3030
- `npm run worker` — background workers (`backend/src/workers/runner.ts`)

Both connect to the same Postgres. Use `pm2` or two `systemd` units.

Local development: `npm run dev:all` runs both via `concurrently`.

## Audit chain visibility lag (Phase 3 Slice 2)

`auditLog.log()` no longer writes synchronously. It enqueues a `kind='audit_flush'`
row and returns `{ auditId, queued: true }` immediately; the auditFlushWorker
drains under `pg_advisory_xact_lock(42)` and INSERTs the row into
`audit_entries`. Typical lag: <100ms.

Implications:
- A request handler that emits an audit cannot read the resulting row in the
  same handler — query `background_jobs` first if it must.
- The stuck-row recipe at the top of this file applies to `audit_flush` jobs
  too. The advisory lock is transaction-scoped, so a crashed worker releases
  it automatically; the manual reset puts the row back to `queued` for the
  next worker.
- `INSERT ... ON CONFLICT (audit_id) DO NOTHING` in the worker means a retry
  after a crash mid-INSERT won't double-write; the unique index on
  `audit_id` is the safety net.

## Crew tracking (Phase 3.5)

Two tables: `shifts` and `crew_positions`.

- `shifts` is append-only; at most one open shift per user is enforced by the
  partial unique index `shifts_one_open_per_user_uidx` (where `ended_at IS NULL`).
  Retention: keep indefinitely — needed for hours-worked reporting in Phase 3.6.
- `crew_positions` is latest-only: one row per user, UPSERTed on every
  `POST /api/presence/location`. No history table. Stale rows (user has no open
  shift) are harmless because `GET /api/crew/positions` INNER JOINs to
  `shifts WHERE ended_at IS NULL`, so the operator never sees them.
- No PII beyond `user_id`, `lat`, `lng`, `accuracy_m`, `battery_pct`. No
  addresses, no reverse-geocoded street names. Re-evaluate when history
  ships (Phase 3.6).
- API surface: `POST /api/shifts/{start,end}`, `GET /api/shifts/current`,
  `POST /api/presence/location`, `GET /api/crew/positions` (foreman+).
- Operator query — currently-clocked-in users:

```sql
SELECT s.user_id, pr.display_name, s.source, s.started_at,
       p.latitude, p.longitude, p.recorded_at
  FROM shifts s
  INNER JOIN profiles pr      ON pr.id     = s.user_id
  LEFT  JOIN crew_positions p ON p.user_id = s.user_id
 WHERE s.ended_at IS NULL
 ORDER BY s.started_at;
```

- Operator action — force-end a stuck shift (e.g. user closed app without
  clocking out):

```sql
UPDATE shifts SET ended_at = NOW()
 WHERE user_id = '<id>' AND ended_at IS NULL;
```

