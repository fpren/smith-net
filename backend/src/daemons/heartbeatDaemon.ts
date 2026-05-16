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
