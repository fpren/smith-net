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
      byKindState: byKindStateR.rows.map((r) => ({
        kind: r.kind,
        state: r.state,
        count: parseInt(r.count, 10),
      })),
      oldestQueued: oldestQR.rows[0]
        ? {
            kind: oldestQR.rows[0].kind,
            scheduledAt: oldestQR.rows[0].scheduled_at,
            ageSec: Math.floor((now - oldestQR.rows[0].scheduled_at.getTime()) / 1000),
          }
        : null,
      oldestRunning: oldestRR.rows[0]
        ? {
            kind: oldestRR.rows[0].kind,
            lockedAt: oldestRR.rows[0].locked_at,
            ageSec: Math.floor((now - oldestRR.rows[0].locked_at.getTime()) / 1000),
          }
        : null,
    },
  });
});
