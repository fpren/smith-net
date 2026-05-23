import { Response, NextFunction } from 'express';
import { AuthenticatedRequest } from '../auth';
import { Tier, CapKey, CAP_LIMITS_BY_TIER } from '../entitlements';
import { emitGateHit } from '../telemetryService';

const TIER_ASC: Tier[] = ['open', 'solo', 'advanced', 'enterprise'];

/** Lowest tier whose cap is unlimited (null) for the given key. */
export function lowestUnlimitedTierFor(capKey: CapKey): Tier {
  for (const t of TIER_ASC) {
    if (CAP_LIMITS_BY_TIER[t][capKey] === null) return t;
  }
  return 'enterprise'; // unreachable: enterprise is unlimited for every cap
}

export interface CapConfig {
  capKey: CapKey;
  gateId: string;                              // 'active_job_cap' | 'pdf_send_cap'
  count: (userId: string) => Promise<number>;  // current usage for this user
}

/**
 * Refuse when the caller's current usage has reached their tier's numeric cap.
 * Unlimited tiers short-circuit before any count query. Fail-closed: a counter
 * that rejects is forwarded to next(err) so the request does not proceed.
 */
export function requireCap(cfg: CapConfig) {
  return async (req: AuthenticatedRequest, res: Response, next: NextFunction) => {
    if (!req.user) return res.status(401).json({ error: 'Authentication required' });
    const limit = CAP_LIMITS_BY_TIER[req.user.tier][cfg.capKey];
    if (limit === null) return next();         // unlimited: no count, no DB hit
    try {
      const current = await cfg.count(req.user.id);
      if (current >= limit) {
        await emitGateHit(req.user.id, `gate_hit.${cfg.gateId}`, req.user.tier, { limit, current });
        return res.status(403).json({
          error: `Tier cap reached: ${cfg.gateId}`,
          code: 'tier_gate_exceeded',
          gate_id: cfg.gateId,
          current_tier: req.user.tier,
          limit,
          current,
          details: { target_tier: lowestUnlimitedTierFor(cfg.capKey) },
        });
      }
      next();
    } catch (err) {
      next(err);                               // fail-closed
    }
  };
}
