// backend/src/tasksRoutes.ts
//
// Per-job task list. Mounted under the parent apiRouter (which applies
// authenticateToken). Auth model:
//   - POST /tasks: handler asserts req.user is the parent job's foreman.
//   - PATCH/DELETE /tasks/:id: requireTaskOwner does the same via task → job.
// GET /api/jobs/:id/tasks lives on jobsRouter because the foreman check
// there already exists (requireJobOwner).

import { Router, Response } from 'express';
import { AuthenticatedRequest } from './auth';
import { requestLogger } from './log';
import { validateBody } from './middleware/validate';
import { requireTaskOwner, TaskOwnerRequest } from './middleware/requireTaskOwner';
import { CreateTaskBody, UpdateTaskBody } from './schemas/tasks';
import * as tasksService from './tasksService';
import * as jobsService from './jobsService';

export const tasksRouter = Router();

tasksRouter.post('/tasks', validateBody(CreateTaskBody), async (req: AuthenticatedRequest, res: Response) => {
  try {
    const body = req.body as CreateTaskBody;
    const job = await jobsService.getById(body.jobId);
    if (!job) return res.status(404).json({ error: 'Job not found' });
    if (job.foremanId !== req.user!.id) {
      return res.status(403).json({ error: 'Not the job foreman', code: 'not_owner' });
    }
    const task = await tasksService.create({
      jobId: job.id,
      title: body.title,
      createdBy: req.user!.id,
    });
    res.status(201).json({ task });
  } catch (e: any) {
    requestLogger().error({ event: 'task_create_error', err: e }, 'task create error');
    res.status(500).json({ error: 'Failed to create task' });
  }
});

tasksRouter.patch('/tasks/:id', requireTaskOwner, validateBody(UpdateTaskBody), async (req: TaskOwnerRequest, res: Response) => {
  try {
    const body = req.body as UpdateTaskBody;
    const task = await tasksService.update(req.task!.id, body);
    if (!task) return res.status(404).json({ error: 'Task not found' });
    res.json({ task });
  } catch (e: any) {
    requestLogger().error({ event: 'task_update_error', err: e }, 'task update error');
    res.status(500).json({ error: 'Failed to update task' });
  }
});

tasksRouter.delete('/tasks/:id', requireTaskOwner, async (req: TaskOwnerRequest, res: Response) => {
  try {
    await tasksService.deleteTask(req.task!.id);
    res.status(204).send();
  } catch (e: any) {
    requestLogger().error({ event: 'task_delete_error', err: e }, 'task delete error');
    res.status(500).json({ error: 'Failed to delete task' });
  }
});
