/**
 * Phase 3.5 Slice 1: crew tracking service layer.
 *
 * Responsible for shift lifecycle and crew_positions UPSERT. All endpoints
 * in shiftsRoutes / presenceLocationRoutes route through this module.
 */

import { pg, isPgEnabled } from './db';

export interface Shift {
  id: string;
  user_id: string;
  started_at: Date;
  ended_at: Date | null;
  source: 'android' | 'web' | 'admin';
  entry_type: string;
  job_id: string | null;
  job_title: string | null;
  task_id: string | null;
  task_title: string | null;
  clock_out_reason: string | null;
}

export interface CrewPosition {
  user_id: string;
  latitude: number;
  longitude: number;
  accuracy_m: number | null;
  recorded_at: Date;
  source: 'android' | 'web' | 'admin';
  battery_pct: number | null;
}

export interface CrewPositionWithProfile extends CrewPosition {
  display_name: string;
}

function requirePg() {
  if (!isPgEnabled() || !pg) {
    throw new Error('[crewPositionService] DATABASE_URL is required; pg pool is not initialized');
  }
  return pg;
}

class CrewPositionService {
  async startShift(
    userId: string,
    source: Shift['source'],
    opts: { entryType?: string; jobId?: string; jobTitle?: string; taskId?: string; taskTitle?: string } = {}
  ): Promise<Shift> {
    const db = requirePg();
    // The partial unique index does the heavy lifting: a duplicate INSERT
    // when an open shift exists raises 23505 (unique_violation).
    const r = await db.query<Shift>(
      `INSERT INTO shifts (user_id, source, entry_type, job_id, job_title, task_id, task_title)
       VALUES ($1, $2, COALESCE($3, 'regular'), $4, $5, $6, $7)
       RETURNING id, user_id, started_at, ended_at, source, entry_type, job_id, job_title, task_id, task_title, clock_out_reason`,
      [userId, source, opts.entryType ?? null, opts.jobId ?? null, opts.jobTitle ?? null, opts.taskId ?? null, opts.taskTitle ?? null]
    );
    return r.rows[0];
  }

  async endShift(userId: string, reason?: string): Promise<Shift | null> {
    const db = requirePg();
    const r = await db.query<Shift>(
      `UPDATE shifts
          SET ended_at = NOW(), clock_out_reason = $2
        WHERE user_id = $1 AND ended_at IS NULL
        RETURNING id, user_id, started_at, ended_at, source, entry_type, job_id, job_title, task_id, task_title, clock_out_reason`,
      [userId, reason ?? null]
    );
    return r.rows[0] ?? null;
  }

  async getCurrentShift(userId: string): Promise<Shift | null> {
    const db = requirePg();
    const r = await db.query<Shift>(
      `SELECT id, user_id, started_at, ended_at, source, entry_type, job_id, job_title, task_id, task_title, clock_out_reason
         FROM shifts
        WHERE user_id = $1 AND ended_at IS NULL
        LIMIT 1`,
      [userId]
    );
    return r.rows[0] ?? null;
  }

  async getShiftsSince(userId: string, sinceMs: number): Promise<Shift[]> {
    const res = await requirePg().query<Shift>(
      `SELECT id, user_id, started_at, ended_at, source, entry_type, job_id, job_title, task_id, task_title, clock_out_reason
         FROM shifts
        WHERE user_id = $1 AND (ended_at IS NULL OR ended_at >= to_timestamp($2 / 1000.0))
        ORDER BY started_at ASC`,
      [userId, sinceMs],
    );
    return res.rows;
  }

  async upsertPosition(
    userId: string,
    input: { lat: number; lng: number; accuracy_m?: number; battery_pct?: number },
    source: Shift['source']
  ): Promise<CrewPosition> {
    const db = requirePg();
    const open = await this.getCurrentShift(userId);
    if (!open) {
      throw new Error('no open shift for user');
    }
    // organization_id is denormalized onto crew_positions so the foreman
    // crew-list query can filter on a single column without a JOIN. The
    // owning user's org is the source of truth (013 migration ensures NOT NULL).
    const orgRow = await db.query<{ organization_id: string }>(
      `SELECT organization_id FROM users WHERE id = $1`,
      [userId]
    );
    const organizationId = orgRow.rows[0]?.organization_id;
    if (!organizationId) {
      throw new Error(`user ${userId} has no organization_id`);
    }
    const r = await db.query<CrewPosition>(
      `INSERT INTO crew_positions (user_id, latitude, longitude, accuracy_m, source, battery_pct, organization_id, recorded_at)
       VALUES ($1, $2, $3, $4, $5, $6, $7, NOW())
       ON CONFLICT (user_id) DO UPDATE SET
         latitude    = EXCLUDED.latitude,
         longitude   = EXCLUDED.longitude,
         accuracy_m  = EXCLUDED.accuracy_m,
         source      = EXCLUDED.source,
         battery_pct = EXCLUDED.battery_pct,
         recorded_at = NOW()
       RETURNING user_id, latitude, longitude, accuracy_m, recorded_at, source, battery_pct`,
      [userId, input.lat, input.lng, input.accuracy_m ?? null, source, input.battery_pct ?? null, organizationId]
    );
    return r.rows[0];
  }

  async listOpenPositions(organizationId: string): Promise<CrewPositionWithProfile[]> {
    const db = requirePg();
    // Two filters stack here:
    //   1. organization_id — tenant isolation (013 migration); a foreman only
    //      ever sees crew in their own org.
    //   2. u.role <> 'solo' — solo workers operate independently, so their dot
    //      should not surface on any supervisor's console even within the org.
    const r = await db.query<CrewPositionWithProfile>(
      `SELECT p.user_id, p.latitude, p.longitude, p.accuracy_m, p.recorded_at, p.source, p.battery_pct,
              pr.display_name
         FROM crew_positions p
         INNER JOIN shifts   s  ON s.user_id  = p.user_id AND s.ended_at IS NULL
         INNER JOIN profiles pr ON pr.id      = p.user_id
         INNER JOIN users    u  ON u.id       = p.user_id
        WHERE p.organization_id = $1
          AND u.role <> 'solo'`,
      [organizationId]
    );
    return r.rows;
  }
}

export const crewPositionService = new CrewPositionService();
