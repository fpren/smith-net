/**
 * Phase 3.5 Slice 1: shift lifecycle routes.
 *
 * POST   /api/shifts/start    — open a shift
 * POST   /api/shifts/end      — close the open shift
 * GET    /api/shifts/current  — read the open shift (or null)
 *
 * All routes require auth. Audit rows emitted via auditLog.log().
 */

import { Router, Response, NextFunction } from 'express';
import { authenticateToken, AuthenticatedRequest } from './auth';
import { crewPositionService, Shift } from './crewPositionService';
import { auditLog, AuditAction } from './auditLog';
import { requestLogger } from './log';
import { validateBody } from './middleware/validate';
import { StartShiftBody, EndShiftBody } from './schemas/shifts';
import * as jobsService from './jobsService';
import * as tasksService from './tasksService';

export const shiftsRouter = Router();

function serializeShift(s: Shift) {
  return {
    id: s.id,
    userId: s.user_id,
    startedAt: s.started_at,
    endedAt: s.ended_at,
    source: s.source,
    entryType: s.entry_type,
    jobId: s.job_id,
    jobTitle: s.job_title,
    taskId: s.task_id,
    taskTitle: s.task_title,
    clockOutReason: s.clock_out_reason,
  };
}

shiftsRouter.post('/start', authenticateToken, validateBody(StartShiftBody), async (req: AuthenticatedRequest, res: Response, next: NextFunction) => {
  const userId = req.user!.id;
  const { source = 'web', entryType, jobId, jobTitle, taskId, taskTitle } = req.body as StartShiftBody;
  try {
    const shift = await crewPositionService.startShift(userId, source, { entryType, jobId, jobTitle, taskId, taskTitle });
    await auditLog.log(AuditAction.SHIFT_STARTED, userId, { shift_id: shift.id, source, entry_type: shift.entry_type });
    requestLogger().info({ event: 'shift_started', userId, source, shiftId: shift.id }, 'shift started');
    return res.status(200).json({ shift: serializeShift(shift) });
  } catch (err) {
    const code = (err as { code?: string }).code;
    if (code === '23505') return res.status(409).json({ error: 'shift already open' });
    if (code === '23503') return res.status(400).json({ error: 'invalid jobId' });
    // Express 4 does not forward a rejected async handler to the error
    // middleware -- pass it on explicitly instead of throwing (which would
    // hang the request as an unhandled rejection).
    return next(err);
  }
});

shiftsRouter.post('/end', authenticateToken, validateBody(EndShiftBody), async (req: AuthenticatedRequest, res: Response, next: NextFunction) => {
  const userId = req.user!.id;
  const { reason } = req.body as EndShiftBody;
  // try/catch -> next(err): endShift / auditLog.log can throw on a transient DB
  // error, and Express 4 will not catch an async rejection (it would hang the
  // request) -- forward it to the error middleware for a clean 500.
  try {
    const shift = await crewPositionService.endShift(userId, reason);
    if (!shift) {
      return res.status(404).json({ error: 'no open shift' });
    }
    await auditLog.log(AuditAction.SHIFT_ENDED, userId, { shift_id: shift.id });
    requestLogger().info({ event: 'shift_ended', userId, shiftId: shift.id }, 'shift ended');
    return res.status(200).json({ shift: serializeShift(shift) });
  } catch (err) {
    return next(err);
  }
});

shiftsRouter.get('/current', authenticateToken, async (req: AuthenticatedRequest, res: Response) => {
  const userId = req.user!.id;
  const shift = await crewPositionService.getCurrentShift(userId);
  return res.status(200).json({ shift: shift ? serializeShift(shift) : null });
});

shiftsRouter.get('/today', authenticateToken, async (req: AuthenticatedRequest, res: Response) => {
  const userId = req.user!.id;
  const since = Number(req.query.since);
  if (!Number.isFinite(since) || since < 0) {
    return res.status(400).json({ error: 'invalid since' });
  }
  const shifts = await crewPositionService.getShiftsSince(userId, since);
  return res.status(200).json({ shifts: shifts.map(serializeShift) });
});

// Clock-scoped, all-tier (authenticateToken only — NOT requireConsoleTier).
// A solo worker must reach their own jobs/tasks to connect time at clock-in.
shiftsRouter.get('/jobs', authenticateToken, async (req: AuthenticatedRequest, res: Response) => {
  const jobs = await jobsService.listForUser(req.user!.id);
  return res.status(200).json({ jobs: jobs.map((j) => ({ id: j.id, title: j.title, status: j.status })) });
});

shiftsRouter.get('/jobs/:jobId/tasks', authenticateToken, async (req: AuthenticatedRequest, res: Response) => {
  const ok = await jobsService.canUserAccessJob(req.params.jobId, req.user!.id);
  if (!ok) return res.status(404).json({ error: 'job not found' });
  const tasks = await tasksService.listByJob(req.params.jobId);
  return res.status(200).json({ tasks: tasks.map((t) => ({ id: t.id, title: t.title, status: t.status })) });
});
