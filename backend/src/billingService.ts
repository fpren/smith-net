// backend/src/billingService.ts
//
// Sub-project 6a: provider-agnostic billing core. Pure tier-derivation helpers
// + (added in Task 2) the transactional applySubscriptionEvent. No provider SDK;
// the Stripe/Play adapters (6b) normalize webhooks into applySubscriptionEvent.

import { Tier, TIER_CODE } from './entitlements';

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
