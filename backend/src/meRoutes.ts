// backend/src/meRoutes.ts
//
// POST /api/me/start-trial -- start a Solo or Advanced trial (no credit card).
// Raises users.tier; the trialExpirer daemon reverts it at expiry. Enterprise
// trials require billing (deferred). Email-verified + per-user 5/min rate limit.

import { Router, Response } from 'express';
import rateLimit from 'express-rate-limit';
import { AuthenticatedRequest, requireVerifiedEmail } from './auth';
import { validateBody } from './middleware/validate';
import { StartTrialBody } from './schemas/me';
import * as trialService from './trialService';
import { emitGateHit } from './telemetryService';
import { requestLogger } from './log';

export const meRouter = Router();

const startTrialLimiter = rateLimit({
  windowMs: 60_000,
  max: 5,
  standardHeaders: true,
  legacyHeaders: false,
  keyGenerator: (req) => (req as AuthenticatedRequest).user?.id ?? req.ip ?? 'unknown',
  skip: (req) => !(req as AuthenticatedRequest).user,
});

meRouter.post(
  '/me/start-trial',
  requireVerifiedEmail,
  startTrialLimiter,
  validateBody(StartTrialBody),
  async (req: AuthenticatedRequest, res: Response) => {
    try {
      const current = req.user!.tier;
      const target = (req.body as StartTrialBody).tier;
      if (!trialService.isTrialUpgrade(current, target)) {
        return res.status(400).json({
          error: `Cannot trial ${target} from ${current}`,
          code: 'already_at_or_above_tier',
          current_tier: current,
        });
      }
      const r = await trialService.startTrial(req.user!.id, current, target);
      if (!r.ok) {
        return res.status(400).json({ error: `Trial not started: ${r.code}`, code: r.code });
      }
      await emitGateHit(req.user!.id, 'tier_upgrade.trial_started', target, {
        from_tier: current,
        has_cc: false,
      });
      res.setHeader('X-Tier-Changed', 'true');
      res.status(200).json({ tier: r.tier, trial_ends_at: r.trialEndsAt });
    } catch (e: any) {
      requestLogger().error({ event: 'start_trial_error', err: e }, 'start trial error');
      res.status(500).json({ error: 'Failed to start trial' });
    }
  },
);
