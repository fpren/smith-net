// backend/src/founderSeatsRoutes.ts
//
// Founder-pricing scarcity pools. GET counts (drives the "X OF Y SPOTS"
// counter) + POST reserve (10-min hold). Identity from req.user, never body.

import { Router, Response } from 'express';
import { AuthenticatedRequest } from './auth';
import { validateBody } from './middleware/validate';
import { ReserveFounderSeatBody } from './schemas/founderSeats';
import * as founderSeatService from './founderSeatService';
import type { FounderBonusId } from './founderSeatService';
import { requestLogger } from './log';

export const founderSeatsRouter = Router();

founderSeatsRouter.get('/founder-seats', async (_req: AuthenticatedRequest, res: Response) => {
  try {
    res.json({ counts: await founderSeatService.getAllCounts() });
  } catch (e: any) {
    requestLogger().error({ event: 'founder_seats_counts_error', err: e }, 'founder seats counts error');
    res.status(500).json({ error: 'Failed to load founder seats' });
  }
});

founderSeatsRouter.post(
  '/founder-seats/reserve',
  validateBody(ReserveFounderSeatBody),
  async (req: AuthenticatedRequest, res: Response) => {
    try {
      const bonusId = (req.body as ReserveFounderSeatBody).bonusId as FounderBonusId;
      const r = await founderSeatService.reserve(bonusId, req.user!.id);
      if (!r) {
        return res.status(409).json({
          error: 'Founder seats exhausted',
          code: 'founder_seats_exhausted',
          bonus_id: bonusId,
        });
      }
      res.status(200).json({ seat_id: r.seatId, bonus_id: r.bonusId, held_until: r.heldUntil });
    } catch (e: any) {
      requestLogger().error({ event: 'founder_seats_reserve_error', err: e }, 'founder seats reserve error');
      res.status(500).json({ error: 'Failed to reserve founder seat' });
    }
  },
);
