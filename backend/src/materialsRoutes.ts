// backend/src/materialsRoutes.ts
//
// Owner-scoped write operations on materials. LIST lives on jobsRouter
// (GET /api/jobs/:id/materials) — that route uses requireJobOwner.
// Here: POST /api/materials, PATCH/DELETE /api/materials/:id.

import { Router, Response } from 'express';
import { authenticateToken, AuthenticatedRequest } from './auth';
import { validateBody } from './middleware/validate';
import { requireConsoleTier } from './middleware/requireConsoleTier';
import { requireMaterialOwner, MaterialOwnerRequest } from './middleware/requireMaterialOwner';
import { CreateMaterialBody, UpdateMaterialBody } from './schemas/materials';
import * as materialsService from './materialsService';
import * as jobsService from './jobsService';
import { requestLogger } from './log';

export const materialsRouter = Router();

materialsRouter.use(authenticateToken, requireConsoleTier);

// POST /api/materials — body carries jobId. Verifies foreman of target job.
materialsRouter.post('/materials', validateBody(CreateMaterialBody),
  async (req: AuthenticatedRequest, res: Response) => {
    try {
      const body = req.body as CreateMaterialBody;
      const job = await jobsService.getById(body.jobId);
      if (!job) return res.status(404).json({ error: 'Job not found' });
      if (job.foremanId !== req.user!.id) {
        return res.status(403).json({ error: 'Not the job foreman', code: 'not_owner' });
      }
      const material = await materialsService.create(body, req.user!.id);
      res.status(201).json({ material });
    } catch (e) {
      requestLogger().error({ event: 'materials_create_error', err: e }, 'materials create error');
      res.status(500).json({ error: 'Failed to create material' });
    }
  });

materialsRouter.patch('/materials/:id', requireMaterialOwner, validateBody(UpdateMaterialBody),
  async (req: MaterialOwnerRequest, res: Response) => {
    try {
      const body = req.body as UpdateMaterialBody;
      const material = await materialsService.update(req.material!.id, body, req.user!.id);
      if (!material) return res.status(404).json({ error: 'Material not found' });
      res.json({ material });
    } catch (e) {
      requestLogger().error({ event: 'materials_update_error', err: e }, 'materials update error');
      res.status(500).json({ error: 'Failed to update material' });
    }
  });

materialsRouter.delete('/materials/:id', requireMaterialOwner,
  async (req: MaterialOwnerRequest, res: Response) => {
    try {
      await materialsService.hardDelete(req.material!.id, req.user!.id);
      res.status(204).send();
    } catch (e) {
      requestLogger().error({ event: 'materials_delete_error', err: e }, 'materials delete error');
      res.status(500).json({ error: 'Failed to delete material' });
    }
  });
