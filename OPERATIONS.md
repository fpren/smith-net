# Smith Net Operations Runbook

## Operator health view

`GET /api/admin/health` (admin role required) returns:
- `workers[]` — every process with a recent `worker_heartbeats` row
- `queue.byKindState[]` — count of rows per (kind, state)
- `queue.oldestQueued` / `queue.oldestRunning` — oldest pending+running rows

Use this to verify both processes are alive after a deploy and to spot
queue buildup before users notice. Heartbeat cadence is 30s; rows older
than ~2 minutes mean the worker process is down.

## Stuck running background_jobs

**Automated since Phase 4 Slice 1.** `queueWatcherDaemon` runs on a 60s
cadence inside the worker process and resets any `state='running'` row
whose `locked_at` is older than 10 minutes back to `state='queued'`.
Operator intervention is no longer required for the typical worker-crash
case; each reset is audit-logged (`ADMIN_ACTION` / `stuck_job_reset`).

The manual SQL recipe below stays as a break-glass for emergencies (e.g.
the worker process itself is down):

```sql
UPDATE background_jobs
   SET state = 'queued',
       locked_at = NULL,
       locked_by = NULL
 WHERE state = 'running'
   AND locked_at < NOW() - INTERVAL '10 minutes';
```

Diagnostic query — show stuck rows:

```sql
SELECT id, kind, locked_by, locked_at, attempts
  FROM background_jobs
 WHERE state = 'running'
   AND locked_at < NOW() - INTERVAL '10 minutes'
 ORDER BY locked_at;
```

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

## Presence watcher (Phase 4 Slice 2)

`presenceWatcherDaemon` runs on a 60s cadence and emits two audit signals
without taking any automated action:

- `stale_presence` — open shift whose latest GPS report is >30 minutes old
  (or has never reported). Suggests a dead device, app killed, or user
  underground.
- `ultra_long_shift` — open shift duration >16 hours. Suggests a forgotten
  clock-out.

Both emit `ADMIN_ACTION` audit rows with `actor_id='presenceWatcherDaemon'`.
The daemon dedupes per-process: a given shift emits each event at most once
per worker-process lifetime, so a forgotten shift produces one signal, not
hundreds. Worker restart resets the dedupe set (acceptable — restarts are
rare and audit duplicates are not destructive).

Operator inspection query:

```sql
SELECT id, audit_id, metadata->>'event' AS event,
       metadata->>'user_id' AS user_id,
       metadata->>'shift_id' AS shift_id,
       created_at
  FROM audit_entries
 WHERE actor_id = 'presenceWatcherDaemon'
   AND created_at > NOW() - INTERVAL '24 hours'
 ORDER BY created_at DESC;
```

The daemon does NOT auto-end shifts (intentional — too easy to surprise
users who briefly lost GPS). To force-end a shift after operator review,
use the manual SQL in the crew tracking section above.

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

## Email worker (Phase 3 Slice 3)

`authRoutes.ts` no longer calls SMTP directly. Register and resend-verification
enqueue a `kind='email'` job with `subkind='verification'`; the emailWorker
dispatches and calls `emailService.sendEmail`.

Dedupe key: `email:verify:<userId>:<token>`. If register races a resend the
second enqueue returns `created: false` and only one send reaches SMTP.

If SMTP env (`SMTP_USER` + `SMTP_APP_PASSWORD`) is unset, `sendEmail` runs
in dry-run mode and logs the body to the worker's stdout — useful for
grabbing the verification link in dev. The route's `/resend-verification`
response still includes `dryRun: !isEmailLive()` so the client knows
whether real mail was attempted.

Retry: a `sendEmail` failure marks the row `state='failed'` with
exponential backoff (`60 * 3^attempts` seconds, capped at 6h). After
`max_attempts=5`, the row goes to `state='dead'` and stays for operator
review. Same stuck-row recipe applies.

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

