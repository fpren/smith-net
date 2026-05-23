// backend/src/telemetryRoutes.ts
//
// POST /api/telemetry/gate-hit -- client-emitted gate hits + upgrade CTAs.
// Identity (user_id_hash) and current_tier are derived server-side from
// req.user; client-supplied identity is never trusted.

import { Router, Response } from 'express';
import { AuthenticatedRequest } from './auth';
import { validateBody } from './middleware/validate';
import { GateHitBody } from './schemas/telemetry';
import { emitGateHit } from './telemetryService';
import { requestLogger } from './log';

export const telemetryRouter = Router();

telemetryRouter.post('/telemetry/gate-hit', validateBody(GateHitBody), async (req: AuthenticatedRequest, res: Response) => {
  try {
    const body = req.body as GateHitBody;
    await emitGateHit(req.user!.id, body.event, req.user!.tier, body.metadata ?? {});
    res.status(204).send();
  } catch (e: any) {
    requestLogger().error({ event: 'telemetry_gate_hit_error', err: e }, 'telemetry gate-hit error');
    res.status(500).json({ error: 'Failed to record gate hit' });
  }
});
