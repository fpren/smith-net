import { Response, NextFunction } from 'express';
import { AuthenticatedRequest } from '../auth';
import { Tier, ENTITLEMENT_BITS, CAPS_BY_TIER } from '../entitlements';

// Canonical tier gates. requireConsoleTier.ts is the legacy role-based gate.

const TIER_ORDER: Record<Tier, number> = { open: 0, solo: 1, advanced: 2, enterprise: 3 };
const TIER_ASC: Tier[] = ['open', 'solo', 'advanced', 'enterprise'];

/** Lowest tier whose CAPS_BY_TIER includes the given bit. */
export function lowestTierFor(bit: number): Tier {
  for (const t of TIER_ASC) {
    if ((CAPS_BY_TIER[t] & (1 << bit)) !== 0) return t;
  }
  return 'enterprise'; // unreachable for a registered entitlement bit
}

/** Refuse unless the caller's tier is >= minTier. Structured 403 on refusal. */
export function requireTier(minTier: Tier, gateId: string) {
  return (req: AuthenticatedRequest, res: Response, next: NextFunction) => {
    if (!req.user) return res.status(401).json({ error: 'Authentication required' });
    const cur = TIER_ORDER[req.user.tier];
    if (cur === undefined || cur < TIER_ORDER[minTier]) {
      return res.status(403).json({
        error: `Tier gate: ${gateId}`,
        code: 'tier_gate_exceeded',
        gate_id: gateId,
        current_tier: req.user.tier,
        details: { target_tier: minTier },
      });
    }
    next();
  };
}

/** Refuse unless the caller's tier includes the named entitlement bit. */
export function requireEntitlement(entitlement: keyof typeof ENTITLEMENT_BITS, gateId: string) {
  const bit = ENTITLEMENT_BITS[entitlement];
  const target = lowestTierFor(bit);
  return (req: AuthenticatedRequest, res: Response, next: NextFunction) => {
    if (!req.user) return res.status(401).json({ error: 'Authentication required' });
    if ((CAPS_BY_TIER[req.user.tier] & (1 << bit)) === 0) {
      return res.status(403).json({
        error: `Tier gate: ${gateId}`,
        code: 'tier_gate_exceeded',
        gate_id: gateId,
        current_tier: req.user.tier,
        details: { target_tier: target },
      });
    }
    next();
  };
}
