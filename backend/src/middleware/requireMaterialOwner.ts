import { Response, NextFunction } from 'express';
import { AuthenticatedRequest } from '../auth';
import * as materialsService from '../materialsService';
import * as jobsService from '../jobsService';

export interface MaterialOwnerRequest extends AuthenticatedRequest {
  material?: materialsService.Material;
  job?: jobsService.Job;
}

/**
 * Loads the material by :id, then its job, then asserts the request's user
 * is that job's foreman. Mirrors requireTaskOwner.ts. Used by
 * PATCH /api/materials/:id and DELETE /api/materials/:id.
 */
export async function requireMaterialOwner(
  req: MaterialOwnerRequest,
  res: Response,
  next: NextFunction,
) {
  const id = req.params.id;
  if (!id) {
    return res.status(400).json({ error: 'Missing material id' });
  }
  try {
    const material = await materialsService.getById(id);
    if (!material) {
      return res.status(404).json({ error: 'Material not found' });
    }
    const job = await jobsService.getById(material.jobId);
    if (!job) {
      return res.status(404).json({ error: 'Parent job not found' });
    }
    if (job.foremanId !== req.user!.id) {
      return res.status(403).json({ error: 'Not the job foreman', code: 'not_owner' });
    }
    req.material = material;
    req.job = job;
    next();
  } catch (err) {
    next(err);
  }
}
