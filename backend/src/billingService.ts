// backend/src/billingService.ts
//
// Sub-project 6a: provider-agnostic billing core. Pure tier-derivation helpers
// + (added in Task 2) the transactional applySubscriptionEvent. No provider SDK;
// the Stripe/Play adapters (6b) normalize webhooks into applySubscriptionEvent.

import { Tier, TIER_CODE } from './entitlements';
import { pg, isPgEnabled } from './db';
import { emitGateHit } from './telemetryService';

export type SubscriptionStatus = 'trialing' | 'active' | 'past_due' | 'canceled' | 'expired';
export type SubscriptionProvider = 'stripe' | 'play_billing' | 'manual';

/** A subscription always carries a paid tier ('open' has no subscription).
 *  Matches the migration's CHECK (tier IN ('solo','advanced','enterprise')). */
export type PaidTier = Exclude<Tier, 'open'>;

/** Highest tier among the given access-granting tiers; 'open' if none. */
export function highestTier(tiers: Tier[]): Tier {
  let best: Tier = 'open';
  for (const t of tiers) if (TIER_CODE[t] > TIER_CODE[best]) best = t;
  return best;
}

/** Telemetry event implied by a tier change (null if unchanged). */
export function tierTransitionEvent(
  before: Tier,
  after: Tier,
): 'tier_upgrade.paid_converted' | 'tier_downgrade.canceled' | null {
  if (TIER_CODE[after] > TIER_CODE[before]) return 'tier_upgrade.paid_converted';
  if (TIER_CODE[after] < TIER_CODE[before]) return 'tier_downgrade.canceled';
  return null;
}

export interface SubscriptionEvent {
  userId: string;
  provider: SubscriptionProvider;
  providerSubscriptionId: string;
  tier: PaidTier;
  status: SubscriptionStatus;
  /** Omit to take the DB default ('monthly'); applySubscriptionEvent passes `?? 'monthly'`. */
  cadence?: 'monthly' | 'annual';
  currentPeriodStart?: Date | null;
  currentPeriodEnd?: Date | null;
  cancelAtPeriodEnd?: boolean;
  centsPerPeriod?: number;
  founderSeatId?: string | null;
  founderPriceLocked?: boolean;
}

function requirePg() {
  if (!isPgEnabled() || !pg) throw new Error('[BillingService] Postgres client not initialized');
  return pg;
}

/**
 * Idempotently apply a normalized subscription event: upsert the subscription
 * (ON CONFLICT provider+provider_subscription_id), recompute users.tier as the
 * highest tier across the user's active subscriptions AND active trials (so a
 * running trial is never clobbered), and emit conversion telemetry on a change.
 */
export async function applySubscriptionEvent(
  event: SubscriptionEvent,
): Promise<{ tier: Tier; changed: boolean }> {
  const db = requirePg();
  const client = await db.connect();
  let before: Tier = 'open';
  let after: Tier = 'open';
  try {
    await client.query('BEGIN');
    const u = await client.query<{ tier: string }>(
      `SELECT tier FROM users WHERE id = $1 FOR UPDATE`,
      [event.userId],
    );
    if (u.rowCount === 0) {
      throw new Error(`[BillingService] user not found: ${event.userId}`);
    }
    before = u.rows[0].tier as Tier;

    await client.query(
      `INSERT INTO subscriptions
         (user_id, tier, cadence, provider, provider_subscription_id, status,
          current_period_start, current_period_end, cancel_at_period_end,
          founder_seat_id, founder_price_locked, cents_per_period)
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12)
       ON CONFLICT (provider, provider_subscription_id) DO UPDATE SET
         tier = EXCLUDED.tier,
         cadence = EXCLUDED.cadence,
         status = EXCLUDED.status,
         current_period_start = EXCLUDED.current_period_start,
         current_period_end = EXCLUDED.current_period_end,
         cancel_at_period_end = EXCLUDED.cancel_at_period_end,
         founder_seat_id = COALESCE(EXCLUDED.founder_seat_id, subscriptions.founder_seat_id),
         founder_price_locked = subscriptions.founder_price_locked OR EXCLUDED.founder_price_locked,
         cents_per_period = EXCLUDED.cents_per_period,
         updated_at = now()`,
      [
        event.userId,
        event.tier,
        event.cadence ?? 'monthly',
        event.provider,
        event.providerSubscriptionId,
        event.status,
        event.currentPeriodStart ?? null,
        event.currentPeriodEnd ?? null,
        event.cancelAtPeriodEnd ?? false,
        event.founderSeatId ?? null,
        event.founderPriceLocked ?? false,
        event.centsPerPeriod ?? 0,
      ],
    );

    const tiers = await client.query<{ tier: string }>(
      `SELECT tier FROM subscriptions
         WHERE user_id = $1 AND status IN ('trialing', 'active', 'past_due')
       UNION ALL
       SELECT tier FROM trials
         WHERE user_id = $1 AND status = 'active' AND expires_at > now()`,
      [event.userId],
    );
    after = highestTier(tiers.rows.map((r) => r.tier as Tier));

    await client.query(`UPDATE users SET tier = $1, updated_at = now() WHERE id = $2`, [after, event.userId]);
    await client.query('COMMIT');
  } catch (e) {
    await client.query('ROLLBACK');
    throw e;
  } finally {
    client.release();
  }

  const evt = tierTransitionEvent(before, after);
  if (evt) {
    await emitGateHit(event.userId, evt, after, {
      from_tier: before,
      to_tier: after,
      provider: event.provider,
    });
  }
  return { tier: after, changed: before !== after };
}
