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

function requirePg() {
  if (!isPgEnabled() || !pg) {
    throw new Error('[crewPositionService] DATABASE_URL is required; pg pool is not initialized');
  }
  return pg;
}

class CrewPositionService {
  async startShift(userId: string, source: Shift['source']): Promise<Shift> {
    const db = requirePg();
    // The partial unique index does the heavy lifting: a duplicate INSERT
    // when an open shift exists raises 23505 (unique_violation).
    const r = await db.query<Shift>(
      `INSERT INTO shifts (user_id, source)
       VALUES ($1, $2)
       RETURNING id, user_id, started_at, ended_at, source`,
      [userId, source]
    );
    return r.rows[0];
  }

  async endShift(userId: string): Promise<Shift | null> {
    const db = requirePg();
    const r = await db.query<Shift>(
      `UPDATE shifts
          SET ended_at = NOW()
        WHERE user_id = $1 AND ended_at IS NULL
        RETURNING id, user_id, started_at, ended_at, source`,
      [userId]
    );
    return r.rows[0] ?? null;
  }

  async getCurrentShift(userId: string): Promise<Shift | null> {
    const db = requirePg();
    const r = await db.query<Shift>(
      `SELECT id, user_id, started_at, ended_at, source
         FROM shifts
        WHERE user_id = $1 AND ended_at IS NULL
        LIMIT 1`,
      [userId]
    );
    return r.rows[0] ?? null;
  }
}

export const crewPositionService = new CrewPositionService();
