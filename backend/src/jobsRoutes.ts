// backend/src/jobsRoutes.ts
import { Router, Response } from 'express';
import { authenticateToken, AuthenticatedRequest } from './auth';
import { requireConsoleTier } from './middleware/requireConsoleTier';
import { requireJobOwner, JobOwnerRequest } from './middleware/requireJobOwner';
import * as jobsService from './jobsService';
import { validateBody } from './middleware/validate';
import { CreateJobBody } from './schemas/jobs';

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

console.log('[Jobs] routes initialized');
