import { Response, NextFunction } from 'express';
import { AuthenticatedRequest } from '../auth';
import * as tasksService from '../tasksService';
import * as jobsService from '../jobsService';

export interface TaskOwnerRequest extends AuthenticatedRequest {
  task?: tasksService.Task;
  job?: jobsService.Job;
}

/**
 * Loads the task by :id, then its job, then asserts the request's user is
 * that job's foreman. Mirrors requireJobOwner.ts but for the task tier.
 * Used by PATCH /api/tasks/:id and DELETE /api/tasks/:id.
 */
export async function requireTaskOwner(
  req: TaskOwnerRequest,
  res: Response,
  next: NextFunction,
) {
  const taskId = req.params.id;
  if (!taskId) {
    return res.status(400).json({ error: 'Missing task id' });
  }
  try {
    const task = await tasksService.getById(taskId);
    if (!task) {
      return res.status(404).json({ error: 'Task not found' });
    }
    const job = await jobsService.getById(task.jobId);
    if (!job) {
      // Orphan task — should be impossible thanks to the FK ON DELETE
      // CASCADE, but guard anyway.
      return res.status(404).json({ error: 'Parent job not found' });
    }
    if (job.foremanId !== req.user!.id) {
      return res.status(403).json({ error: 'Not the job foreman', code: 'not_owner' });
    }
    req.task = task;
    req.job = job;
    next();
  } catch (err) {
    next(err);
  }
}
