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
