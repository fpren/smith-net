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
