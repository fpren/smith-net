// backend/src/jobsRoutes.ts
import { Router, Response } from 'express';
import { authenticateToken, AuthenticatedRequest } from './auth';
import { requireConsoleTier } from './middleware/requireConsoleTier';
import { requireJobOwner, JobOwnerRequest } from './middleware/requireJobOwner';
import * as jobsService from './jobsService';
import { validateBody } from './middleware/validate';
import { CreateJobBody, UpdateJobBody, StatusChangeBody, AssignCrewBody } from './schemas/jobs';

export const jobsRouter = Router();

// All jobs routes require auth + console tier
jobsRouter.use(authenticateToken, requireConsoleTier);

// ════════════════════════════════════════════════════════════════════
// GET /api/jobs — list jobs for the calling foreman
// ════════════════════════════════════════════════════════════════════

jobsRouter.get('/', async (req: AuthenticatedRequest, res: Response) => {
  try {
    const jobs = await jobsService.listByForeman(req.user!.id);
    res.json({ jobs });
  } catch (e: any) {
    console.error('[Jobs] list error:', e.message);
    res.status(500).json({ error: 'Failed to list jobs' });
  }
});

// ════════════════════════════════════════════════════════════════════
// GET /api/jobs/:id — single job + assigned crew
// ════════════════════════════════════════════════════════════════════

jobsRouter.get('/:id', requireJobOwner, async (req: JobOwnerRequest, res: Response) => {
  try {
    const crew = await jobsService.listCrew(req.job!.id);
    res.json({ job: req.job, crew });
  } catch (e: any) {
    console.error('[Jobs] getOne error:', e.message);
    res.status(500).json({ error: 'Failed to load job' });
  }
});

// ════════════════════════════════════════════════════════════════════
// POST /api/jobs — create
// ════════════════════════════════════════════════════════════════════

jobsRouter.post('/', validateBody(CreateJobBody), async (req: AuthenticatedRequest, res: Response) => {
  try {
    const body = req.body as CreateJobBody;
    const job = await jobsService.create({
      foremanId: req.user!.id,
      title: body.title,
      description: body.description,
      scheduledAt: body.scheduledAt ? new Date(body.scheduledAt) : undefined,
      location: body.location,
      clientId: body.clientId,
      engagementId: body.engagementId,
    });
    res.status(201).json({ job });
  } catch (e: any) {
    console.error('[Jobs] create error:', e.message);
    res.status(500).json({ error: 'Failed to create job' });
  }
});

// ════════════════════════════════════════════════════════════════════
// PATCH /api/jobs/:id/status — status transitions
// ════════════════════════════════════════════════════════════════════

jobsRouter.patch('/:id/status', requireJobOwner, validateBody(StatusChangeBody), async (req: JobOwnerRequest, res: Response) => {
  try {
    const body = req.body as StatusChangeBody;
    const job = await jobsService.changeStatus(req.job!.id, body.status);
    res.json({ job });
  } catch (e: any) {
    if (e instanceof jobsService.InvalidTransitionError) {
      return res.status(400).json({
        error: e.message,
        code: 'invalid_status_transition',
        from: e.from,
        to: e.to,
      });
    }
    if (e instanceof jobsService.NotFoundError) {
      return res.status(404).json({ error: 'Job not found' });
    }
    console.error('[Jobs] status error:', e.message);
    res.status(500).json({ error: 'Failed to change status' });
  }
});

// ════════════════════════════════════════════════════════════════════
// PATCH /api/jobs/:id — partial update (NOT status — see /:id/status)
// ════════════════════════════════════════════════════════════════════

jobsRouter.patch('/:id', requireJobOwner, validateBody(UpdateJobBody), async (req: JobOwnerRequest, res: Response) => {
  try {
    const body = req.body as UpdateJobBody;
    const job = await jobsService.update(req.job!.id, {
      title: body.title,
      description: body.description === null ? null as any : body.description,
      scheduledAt: body.scheduledAt === null ? null as any : (body.scheduledAt ? new Date(body.scheduledAt) : undefined),
      location: body.location === null ? null as any : body.location,
    });
    res.json({ job });
  } catch (e: any) {
    if (e instanceof jobsService.NotFoundError) {
      return res.status(404).json({ error: 'Job not found' });
    }
    console.error('[Jobs] update error:', e.message);
    res.status(500).json({ error: 'Failed to update job' });
  }
});

// ════════════════════════════════════════════════════════════════════
// POST /api/jobs/:id/assign — add crew member
// ════════════════════════════════════════════════════════════════════

jobsRouter.post('/:id/assign', requireJobOwner, validateBody(AssignCrewBody), async (req: JobOwnerRequest, res: Response) => {
  try {
    const body = req.body as AssignCrewBody;
    const assignment = await jobsService.assignCrew(req.job!.id, body.profileId, body.roleOnJob);
    res.status(201).json({ assignment });
  } catch (e: any) {
    if (e.code === 'duplicate_assignment') {
      return res.status(409).json({ error: e.message, code: 'duplicate_assignment' });
    }
    if (e instanceof jobsService.NotFoundError) {
      return res.status(404).json({ error: 'Job not found' });
    }
    if (e.code === '23503') {
      return res.status(400).json({ error: 'Unknown profile', code: 'unknown_profile' });
    }
    console.error('[Jobs] assign error:', e.message);
    res.status(500).json({ error: 'Failed to assign crew' });
  }
});

// ════════════════════════════════════════════════════════════════════
// DELETE /api/jobs/:id/assign/:profileId — remove crew member
// ════════════════════════════════════════════════════════════════════

jobsRouter.delete('/:id/assign/:profileId', requireJobOwner, async (req: JobOwnerRequest, res: Response) => {
  try {
    await jobsService.unassignCrew(req.job!.id, req.params.profileId);
    res.status(204).send();
  } catch (e: any) {
    if (e instanceof jobsService.NotFoundError) {
      return res.status(404).json({ error: e.message });
    }
    console.error('[Jobs] unassign error:', e.message);
    res.status(500).json({ error: 'Failed to unassign crew' });
  }
});

console.log('[Jobs] routes initialized');
