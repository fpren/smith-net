import { Tier, TIER_CODE, CAPS_BY_TIER, entitlementsHash } from './entitlements';

/** Maps a role to a tier. Used for the 021 migration backfill and the
 *  insert-time default in usersService (new rows). The authoritative tier is the
 *  users.tier column; this is the seed mapping, not a per-request source. */
export function roleToTier(role: string): Tier {
  switch (role) {
    case 'solo': return 'solo';
    case 'team': return 'solo';
    case 'lead': return 'advanced';
    case 'foreman': return 'advanced';
    case 'enterprise': return 'enterprise';
    case 'admin': return 'enterprise';
    default: return 'open';
  }
}

export interface ResolvedEntitlements {
  tier: Tier;
  bitmask: number;
  entitlementsHash: string;
}

export function resolveEntitlements(tier: Tier): ResolvedEntitlements {
  const bitmask = CAPS_BY_TIER[tier];
  return { tier, bitmask, entitlementsHash: entitlementsHash(TIER_CODE[tier], bitmask) };
}
