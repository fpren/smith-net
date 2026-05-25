// backend/src/jobsRoutes.ts
import { Router, Response } from 'express';
import { authenticateToken, AuthenticatedRequest } from './auth';
import { requireConsoleTier } from './middleware/requireConsoleTier';
import { requireJobOwner, JobOwnerRequest } from './middleware/requireJobOwner';
import * as jobsService from './jobsService';
import * as tasksService from './tasksService';
import { requestLogger } from './log';
import { validateBody } from './middleware/validate';
import { requireCap } from './middleware/requireCap';
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
    requestLogger().error({ event: 'jobs_list_error', err: e }, 'jobs list error');
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
    requestLogger().error({ event: 'jobs_get_error', err: e }, 'jobs get error');
    res.status(500).json({ error: 'Failed to load job' });
  }
});

// ════════════════════════════════════════════════════════════════════
// GET /api/jobs/:id/tasks — per-job task list (foreman of the job only)
// ════════════════════════════════════════════════════════════════════

jobsRouter.get('/:id/tasks', requireJobOwner, async (req: JobOwnerRequest, res: Response) => {
  try {
    const tasks = await tasksService.listByJob(req.job!.id);
    res.json({ tasks });
  } catch (e: any) {
    requestLogger().error({ event: 'tasks_list_error', err: e }, 'tasks list error');
    res.status(500).json({ error: 'Failed to load tasks' });
  }
});

// ════════════════════════════════════════════════════════════════════
// POST /api/jobs — create
// ════════════════════════════════════════════════════════════════════

jobsRouter.post(
  '/',
  validateBody(CreateJobBody),
  requireCap({ capKey: 'active_jobs', gateId: 'active_job_cap', count: jobsService.countActive }),
  async (req: AuthenticatedRequest, res: Response) => {
  try {
    const body = req.body as CreateJobBody;
    if (body.clientId && !(await jobsService.clientBelongsToOwner(body.clientId, req.user!.id))) {
      return res.status(400).json({ error: 'Unknown client', code: 'validation' });
    }
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
    requestLogger().error({ event: 'jobs_create_error', err: e }, 'jobs create error');
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
    requestLogger().error({ event: 'jobs_status_error', err: e }, 'jobs status error');
    res.status(500).json({ error: 'Failed to change status' });
  }
});

// ════════════════════════════════════════════════════════════════════
// PATCH /api/jobs/:id — partial update (NOT status — see /:id/status)
// ════════════════════════════════════════════════════════════════════

jobsRouter.patch('/:id', requireJobOwner, validateBody(UpdateJobBody), async (req: JobOwnerRequest, res: Response) => {
  try {
    const body = req.body as UpdateJobBody;
    if (body.clientId && !(await jobsService.clientBelongsToOwner(body.clientId, req.user!.id))) {
      return res.status(400).json({ error: 'Unknown client', code: 'validation' });
    }
    const patch: Parameters<typeof jobsService.update>[1] = {
      title: body.title,
      description: body.description === null ? null as any : body.description,
      scheduledAt: body.scheduledAt === null ? null as any : (body.scheduledAt ? new Date(body.scheduledAt) : undefined),
      location: body.location === null ? null as any : body.location,
    };
    if ('clientId' in body) { patch.clientId = body.clientId ?? null; }
    const job = await jobsService.update(req.job!.id, patch);
    res.json({ job });
  } catch (e: any) {
    if (e instanceof jobsService.NotFoundError) {
      return res.status(404).json({ error: 'Job not found' });
    }
    requestLogger().error({ event: 'jobs_update_error', err: e }, 'jobs update error');
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
    requestLogger().error({ event: 'jobs_assign_error', err: e }, 'jobs assign error');
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
    requestLogger().error({ event: 'jobs_unassign_error', err: e }, 'jobs unassign error');
    res.status(500).json({ error: 'Failed to unassign crew' });
  }
});

requestLogger().info({ event: 'jobs_routes_initialized' }, 'jobs routes initialized');
