// backend/src/trialService.ts
//
// Sub-project 4: trials against users.tier (the single tier source). Pure
// helpers (durations, upgrade direction) + two transactional ops. startTrial
// raises users.tier and records the trial atomically; expireDueTrials reverts
// the tier to previous_tier with a "still on trial tier" guard.

import { pg, isPgEnabled } from './db';
import { Tier, TIER_CODE } from './entitlements';

function requirePg() {
  if (!isPgEnabled() || !pg) throw new Error('[TrialService] Postgres client not initialized');
  return pg;
}

export type TrialTier = 'solo' | 'advanced';
export const TRIAL_DAYS: Record<TrialTier, number> = { solo: 14, advanced: 30 };

export function trialExpiry(tier: TrialTier, now: Date = new Date()): Date {
  return new Date(now.getTime() + TRIAL_DAYS[tier] * 24 * 60 * 60 * 1000);
}

/** A trial is allowed only for a tier strictly above the user's current tier. */
export function isTrialUpgrade(currentTier: Tier, targetTier: TrialTier): boolean {
  return TIER_CODE[targetTier] > TIER_CODE[currentTier];
}

export type StartTrialResult =
  | { ok: true; tier: TrialTier; trialEndsAt: Date }
  | { ok: false; code: 'trial_already_used' | 'trial_already_active' };

/**
 * Atomically: reject if an active trial exists; insert the trial row (the
 * UNIQUE(user_id, tier) constraint makes same-tier reuse a 23505 -> reject);
 * raise users.tier. Either everything lands or nothing does. The caller has
 * already checked isTrialUpgrade, so previous_tier (= currentTier) is below the
 * target.
 */
export async function startTrial(
  userId: string,
  currentTier: Tier,
  targetTier: TrialTier,
): Promise<StartTrialResult> {
  const db = requirePg();
  const client = await db.connect();
  try {
    await client.query('BEGIN');
    const active = await client.query(
      `SELECT 1 FROM trials WHERE user_id = $1 AND status = 'active' LIMIT 1`,
      [userId],
    );
    if ((active.rowCount ?? 0) > 0) {
      await client.query('ROLLBACK');
      return { ok: false, code: 'trial_already_active' };
    }
    const expiresAt = trialExpiry(targetTier);
    try {
      await client.query(
        `INSERT INTO trials (user_id, tier, previous_tier, expires_at)
           VALUES ($1, $2, $3, $4)`,
        [userId, targetTier, currentTier, expiresAt],
      );
    } catch (e: any) {
      if (e.code === '23505') {
        await client.query('ROLLBACK');
        return { ok: false, code: 'trial_already_used' };
      }
      throw e;
    }
    await client.query(
      `UPDATE users SET tier = $1, updated_at = now() WHERE id = $2`,
      [targetTier, userId],
    );
    await client.query('COMMIT');
    return { ok: true, tier: targetTier, trialEndsAt: expiresAt };
  } catch (e) {
    await client.query('ROLLBACK');
    throw e;
  } finally {
    client.release();
  }
}

export interface ExpiredTrial {
  userId: string;
  tier: TrialTier;
  previousTier: Tier;
}

/**
 * Revert each due trial: set users.tier back to previous_tier ONLY if the user
 * is still on the trial tier (guard against a later upgrade), mark the row
 * expired. Each row is its own transaction. Returns the processed rows for
 * telemetry.
 */
export async function expireDueTrials(limit = 200): Promise<ExpiredTrial[]> {
  const db = requirePg();
  const due = await db.query<{ id: string; user_id: string; tier: string; previous_tier: string }>(
    `SELECT id, user_id, tier, previous_tier FROM trials
       WHERE status = 'active' AND expires_at <= now()
       ORDER BY expires_at ASC
       LIMIT $1`,
    [limit],
  );
  const processed: ExpiredTrial[] = [];
  for (const row of due.rows) {
    const client = await db.connect();
    try {
      await client.query('BEGIN');
      await client.query(
        `UPDATE users SET tier = $1, updated_at = now() WHERE id = $2 AND tier = $3`,
        [row.previous_tier, row.user_id, row.tier],
      );
      await client.query(`UPDATE trials SET status = 'expired' WHERE id = $1`, [row.id]);
      await client.query('COMMIT');
      processed.push({
        userId: row.user_id,
        tier: row.tier as TrialTier,
        previousTier: row.previous_tier as Tier,
      });
    } catch (e) {
      await client.query('ROLLBACK');
      throw e;
    } finally {
      client.release();
    }
  }
  return processed;
}
