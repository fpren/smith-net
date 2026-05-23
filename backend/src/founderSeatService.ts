// backend/src/founderSeatService.ts
//
// Sub-project 5: founder-pricing scarcity pools (F5.1). Pre-minted rows; reserve
// grabs one under FOR UPDATE SKIP LOCKED for a 10-min hold. Holds are
// self-healing: reserve and getAllCounts both treat an expired hold as
// available, so the release daemon is housekeeping, not a correctness need.

import { pg, isPgEnabled } from './db';

function requirePg() {
  if (!isPgEnabled() || !pg) throw new Error('[FounderSeatService] Postgres client not initialized');
  return pg;
}

export const FOUNDER_BONUS_IDS = [
  'solo_founder_pricing_lock',
  'advanced_lifetime_template_library',
  'enterprise_founder_annual_pricing',
] as const;
export type FounderBonusId = typeof FOUNDER_BONUS_IDS[number];

export const FOUNDER_POOLS: Record<FounderBonusId, number> = {
  solo_founder_pricing_lock: 1000,
  advanced_lifetime_template_library: 100,
  enterprise_founder_annual_pricing: 10,
};

export const HOLD_MINUTES = 10;

export interface Reservation {
  seatId: string;
  bonusId: FounderBonusId;
  heldUntil: Date;
}

export interface SeatCount {
  remaining: number;
  total: number;
}

/**
 * Atomically hold one seat for 10 minutes. Returns the caller's existing
 * un-expired hold for this pool if any (a sequential re-tap reuses it), else
 * grabs one available-or-expired-held seat via FOR UPDATE SKIP LOCKED. null when
 * the pool is exhausted. Note: two CONCURRENT reserves by the same user can each
 * pass the existing-hold check before either commits and transiently hold two
 * seats; both expire within HOLD_MINUTES (no claimed-seat over-allocation). If
 * that ever matters, add a partial unique index on (bonus_id, held_by) WHERE
 * status='held'.
 */
export async function reserve(bonusId: FounderBonusId, userId: string): Promise<Reservation | null> {
  const db = requirePg();
  const client = await db.connect();
  try {
    await client.query('BEGIN');
    const existing = await client.query<{ id: string; held_until: string }>(
      `SELECT id, held_until FROM founder_seats
         WHERE bonus_id = $1 AND status = 'held' AND held_by = $2 AND held_until > now()
         LIMIT 1`,
      [bonusId, userId],
    );
    if (existing.rows.length > 0) {
      await client.query('COMMIT');
      return { seatId: existing.rows[0].id, bonusId, heldUntil: new Date(existing.rows[0].held_until) };
    }
    const pick = await client.query<{ id: string }>(
      `SELECT id FROM founder_seats
         WHERE bonus_id = $1 AND (status = 'available' OR (status = 'held' AND held_until <= now()))
         ORDER BY seat_number
         LIMIT 1
         FOR UPDATE SKIP LOCKED`,
      [bonusId],
    );
    if (pick.rows.length === 0) {
      await client.query('COMMIT');
      return null;
    }
    const seatId = pick.rows[0].id;
    const heldUntil = new Date(Date.now() + HOLD_MINUTES * 60 * 1000);
    await client.query(
      `UPDATE founder_seats SET status = 'held', held_by = $2, held_until = $3 WHERE id = $1`,
      [seatId, userId, heldUntil],
    );
    await client.query('COMMIT');
    return { seatId, bonusId, heldUntil };
  } catch (e) {
    await client.query('ROLLBACK');
    throw e;
  } finally {
    client.release();
  }
}

/** Claim a held seat after payment. True if the seat was held by this user. */
export async function claim(seatId: string, userId: string): Promise<boolean> {
  const db = requirePg();
  const r = await db.query(
    `UPDATE founder_seats SET status = 'claimed', claimed_by = $2, claimed_at = now()
       WHERE id = $1 AND status = 'held' AND held_by = $2`,
    [seatId, userId],
  );
  return (r.rowCount ?? 0) > 0;
}

/** Flip expired holds back to available. Returns the number released. */
export async function releaseExpiredHolds(): Promise<number> {
  const db = requirePg();
  const r = await db.query(
    `UPDATE founder_seats SET status = 'available', held_by = NULL, held_until = NULL
       WHERE status = 'held' AND held_until <= now()`,
  );
  return r.rowCount ?? 0;
}

/** Remaining (available + expired holds) and total per pool. */
export async function getAllCounts(): Promise<Record<FounderBonusId, SeatCount>> {
  const db = requirePg();
  const r = await db.query<{ bonus_id: string; remaining: number; total: number }>(
    `SELECT bonus_id,
            COUNT(*) FILTER (WHERE status = 'available' OR (status = 'held' AND held_until <= now()))::int AS remaining,
            COUNT(*)::int AS total
       FROM founder_seats
      GROUP BY bonus_id`,
  );
  const out = {} as Record<FounderBonusId, SeatCount>;
  for (const id of FOUNDER_BONUS_IDS) {
    out[id] = { remaining: 0, total: FOUNDER_POOLS[id] };
  }
  for (const row of r.rows) {
    if ((FOUNDER_BONUS_IDS as readonly string[]).includes(row.bonus_id)) {
      out[row.bonus_id as FounderBonusId] = { remaining: row.remaining, total: row.total };
    }
  }
  return out;
}
