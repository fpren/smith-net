import { Response, NextFunction } from 'express';
import { AuthenticatedRequest } from '../auth';
import * as jobsService from '../jobsService';

export interface JobOwnerRequest extends AuthenticatedRequest {
  job?: jobsService.Job;
}

export async function requireJobOwner(req: JobOwnerRequest, res: Response, next: NextFunction) {
  const jobId = req.params.id;
  if (!jobId) {
    return res.status(400).json({ error: 'Missing job id' });
  }
  try {
    const job = await jobsService.getById(jobId);
    if (!job) {
      return res.status(404).json({ error: 'Job not found' });
    }
    if (job.foremanId !== req.user!.id) {
      return res.status(403).json({ error: 'Not the job foreman', code: 'not_owner' });
    }
    req.job = job;
    next();
  } catch (err) {
    next(err);
  }
}
