/**
 * Phase 3.5 Slice 1: shift lifecycle routes.
 *
 * POST   /api/shifts/start    — open a shift
 * POST   /api/shifts/end      — close the open shift
 * GET    /api/shifts/current  — read the open shift (or null)
 *
 * All routes require auth. Audit rows emitted via auditLog.log().
 */

import { Router, Response } from 'express';
import { authenticateToken, AuthenticatedRequest } from './auth';
import { crewPositionService, Shift } from './crewPositionService';
import { auditLog, AuditAction } from './auditLog';
import { requestLogger } from './log';

export const shiftsRouter = Router();

const VALID_SOURCES = new Set(['android', 'web', 'admin']);

function serializeShift(s: Shift) {
  return {
    id: s.id,
    userId: s.user_id,
    startedAt: s.started_at,
    endedAt: s.ended_at,
    source: s.source,
  };
}

shiftsRouter.post('/start', authenticateToken, async (req: AuthenticatedRequest, res: Response) => {
  const userId = req.user!.id;
  const source = (req.body?.source ?? 'web') as Shift['source'];
  if (!VALID_SOURCES.has(source)) {
    return res.status(400).json({ error: 'invalid source' });
  }
  try {
    const shift = await crewPositionService.startShift(userId, source);
    await auditLog.log(AuditAction.SHIFT_STARTED, userId, { shift_id: shift.id, source });
    requestLogger().info({ event: 'shift_started', userId, source, shiftId: shift.id }, 'shift started');
    return res.status(200).json({ shift: serializeShift(shift) });
  } catch (err) {
    if ((err as { code?: string }).code === '23505') {
      return res.status(409).json({ error: 'shift already open' });
    }
    throw err;
  }
});

shiftsRouter.post('/end', authenticateToken, async (req: AuthenticatedRequest, res: Response) => {
  const userId = req.user!.id;
  const shift = await crewPositionService.endShift(userId);
  if (!shift) {
    return res.status(404).json({ error: 'no open shift' });
  }
  await auditLog.log(AuditAction.SHIFT_ENDED, userId, { shift_id: shift.id });
  requestLogger().info({ event: 'shift_ended', userId, shiftId: shift.id }, 'shift ended');
  return res.status(200).json({ shift: serializeShift(shift) });
});

shiftsRouter.get('/current', authenticateToken, async (req: AuthenticatedRequest, res: Response) => {
  const userId = req.user!.id;
  const shift = await crewPositionService.getCurrentShift(userId);
  return res.status(200).json({ shift: shift ? serializeShift(shift) : null });
});
