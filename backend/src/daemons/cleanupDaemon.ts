// backend/src/daemons/cleanupDaemon.ts
//
// Phase 4 Slice 1: cleanup daemon.
// 24h cadence. Purges dead bg_jobs older than retention + delegates audit
// JSONL retention to auditLog.cleanupOldEntries.

import { pg, isPgEnabled } from '../db';
import { auditLog } from '../auditLog';
import { requestLogger } from '../log';

export const INTERVAL_MS = 24 * 60 * 60 * 1000;
const DEAD_JOB_RETENTION_DAYS = 30;
const STALE_HEARTBEAT_RETENTION_HOURS = 24;

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

  // Stale heartbeats. A worker_heartbeats row outlives the worker process
  // (we never DELETE on shutdown — the parent crashes might not run
  // cleanup). Purge anything not refreshed in 24h so the admin/health
  // table doesn't accumulate noise from dev/test sessions.
  const heartbeats = await pg.query<{ worker_id: string }>(
    `DELETE FROM worker_heartbeats
      WHERE last_beat_at < NOW() - ($1::int * INTERVAL '1 hour')
      RETURNING worker_id`,
    [STALE_HEARTBEAT_RETENTION_HOURS]
  );
  if ((heartbeats.rowCount ?? 0) > 0) {
    requestLogger().info(
      { event: 'stale_heartbeats_purged', count: heartbeats.rowCount },
      'purged worker_heartbeats older than retention'
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
