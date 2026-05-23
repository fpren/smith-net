import { Tier, TIER_CODE, CAPS_BY_TIER, entitlementsHash } from './entitlements';

/** Provisional: derive tier from role until a real profiles.tier / billing source
 *  exists. Single source of truth -- no consumer reads tier elsewhere. UserRole
 *  values are strings ('solo','team','lead','foreman','enterprise','admin'). */
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

export function resolveEntitlements(role: string): ResolvedEntitlements {
  const tier = roleToTier(role);
  const bitmask = CAPS_BY_TIER[tier];
  return { tier, bitmask, entitlementsHash: entitlementsHash(TIER_CODE[tier], bitmask) };
}
