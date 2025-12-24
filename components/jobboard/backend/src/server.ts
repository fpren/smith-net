/**
 * C-11: Job Board Backend Server
 * Role-adaptive task management for Smith Net
 */

import express from 'express';
import cors from 'cors';
import jwt from 'jsonwebtoken';
import { jobStore } from './jobStore';
import {
  UserContext,
  CreateJobRequest,
  UpdateJobRequest,
  CreateTaskRequest,
  MoveJobRequest,
  JobStatus,
} from './types';

const PORT = process.env.PORT || 3001;
const JWT_SECRET = process.env.JWT_SECRET || 'smith-net-dev-secret-change-in-production';

const app = express();
app.use(cors());
app.use(express.json());

// ════════════════════════════════════════════════════════════════════
// AUTH MIDDLEWARE (validates SmithNet JWT)
// ════════════════════════════════════════════════════════════════════

interface AuthRequest extends express.Request {
  user?: UserContext;
}

function authenticateToken(
  req: AuthRequest,
  res: express.Response,
  next: express.NextFunction
) {
  const authHeader = req.headers.authorization;
  const token = authHeader?.startsWith('Bearer ') ? authHeader.slice(7) : null;

  if (!token) {
    return res.status(401).json({ error: 'No token provided' });
  }

  try {
    const payload = jwt.verify(token, JWT_SECRET) as any;
    req.user = {
      userId: payload.userId,
      email: payload.email,
      displayName: payload.displayName || payload.email,
      role: payload.role,
      organizationId: payload.organizationId,
    };
    next();
  } catch (e) {
    return res.status(401).json({ error: 'Invalid token' });
  }
}

// ════════════════════════════════════════════════════════════════════
// ROOT
// ════════════════════════════════════════════════════════════════════

app.get('/', (_req, res) => {
  res.json({
    name: 'Smith Net Job Board',
    version: '1.0.0',
    component: 'C-11',
    description: 'Role-adaptive task management',
    endpoints: {
      jobs: '/api/jobs',
      tasks: '/api/tasks',
      stats: '/api/stats',
    },
  });
});

// ════════════════════════════════════════════════════════════════════
// JOBS API
// ════════════════════════════════════════════════════════════════════

// Get all jobs (filtered by role)
app.get('/api/jobs', authenticateToken, (req: AuthRequest, res) => {
  const user = req.user!;
  const { status, assignee, project, search } = req.query;

  let jobs = jobStore.getAllJobs();

  // Role-based filtering
  if (user.role === 'solo') {
    // Solo users only see their own jobs
    jobs = jobs.filter(j => j.createdBy === user.userId || j.assignedTo.includes(user.userId));
  } else if (user.role === 'team') {
    // Team members see assigned jobs
    jobs = jobs.filter(j => j.assignedTo.includes(user.userId) || j.createdBy === user.userId);
  }
  // Foreman, Enterprise, Admin see all jobs

  // Apply filters
  if (status) {
    jobs = jobs.filter(j => j.status === status);
  }
  if (assignee) {
    jobs = jobs.filter(j => j.assignedTo.includes(assignee as string));
  }
  if (project) {
    jobs = jobs.filter(j => j.projectId === project);
  }
  if (search) {
    const q = (search as string).toLowerCase();
    jobs = jobs.filter(
      j =>
        j.title.toLowerCase().includes(q) ||
        j.description.toLowerCase().includes(q)
    );
  }

  res.json({ jobs });
});

// Get single job
app.get('/api/jobs/:id', authenticateToken, (req: AuthRequest, res) => {
  const job = jobStore.getJob(req.params.id);
  if (!job) {
    return res.status(404).json({ error: 'Job not found' });
  }

  const tasks = jobStore.getTasksForJob(job.id);
  res.json({ job, tasks });
});

// Create job
app.post('/api/jobs', authenticateToken, (req: AuthRequest, res) => {
  const user = req.user!;
  const request: CreateJobRequest = req.body;

  if (!request.title) {
    return res.status(400).json({ error: 'Title is required' });
  }

  const job = jobStore.createJob(request, user.userId);
  res.status(201).json({ job });
});

// Update job
app.patch('/api/jobs/:id', authenticateToken, (req: AuthRequest, res) => {
  const updates: UpdateJobRequest = req.body;
  const job = jobStore.updateJob(req.params.id, updates);

  if (!job) {
    return res.status(404).json({ error: 'Job not found' });
  }

  res.json({ job });
});

// Move job (drag-drop status change)
app.post('/api/jobs/:id/move', authenticateToken, (req: AuthRequest, res) => {
  const { newStatus }: MoveJobRequest = req.body;

  if (!newStatus) {
    return res.status(400).json({ error: 'newStatus is required' });
  }

  const job = jobStore.moveJob(req.params.id, newStatus as JobStatus);
  if (!job) {
    return res.status(404).json({ error: 'Job not found' });
  }

  res.json({ job });
});

// Delete job
app.delete('/api/jobs/:id', authenticateToken, (req: AuthRequest, res) => {
  const success = jobStore.deleteJob(req.params.id);
  if (!success) {
    return res.status(404).json({ error: 'Job not found' });
  }

  res.json({ success: true });
});

// ════════════════════════════════════════════════════════════════════
// TASKS API
// ════════════════════════════════════════════════════════════════════

// Get tasks for a job
app.get('/api/jobs/:jobId/tasks', authenticateToken, (req: AuthRequest, res) => {
  const tasks = jobStore.getTasksForJob(req.params.jobId);
  res.json({ tasks });
});

// Create task
app.post('/api/tasks', authenticateToken, (req: AuthRequest, res) => {
  const user = req.user!;
  const request: CreateTaskRequest = req.body;

  if (!request.jobId || !request.title) {
    return res.status(400).json({ error: 'jobId and title are required' });
  }

  const task = jobStore.createTask(request, user.userId);
  if (!task) {
    return res.status(404).json({ error: 'Job not found' });
  }

  res.status(201).json({ task });
});

// Update task
app.patch('/api/tasks/:id', authenticateToken, (req: AuthRequest, res) => {
  const task = jobStore.updateTask(req.params.id, req.body);
  if (!task) {
    return res.status(404).json({ error: 'Task not found' });
  }

  res.json({ task });
});

// Delete task
app.delete('/api/tasks/:id', authenticateToken, (req: AuthRequest, res) => {
  const success = jobStore.deleteTask(req.params.id);
  if (!success) {
    return res.status(404).json({ error: 'Task not found' });
  }

  res.json({ success: true });
});

// Add checklist item
app.post('/api/tasks/:id/checklist', authenticateToken, (req: AuthRequest, res) => {
  const { text } = req.body;
  if (!text) {
    return res.status(400).json({ error: 'text is required' });
  }

  const task = jobStore.addChecklistItem(req.params.id, text);
  if (!task) {
    return res.status(404).json({ error: 'Task not found' });
  }

  res.json({ task });
});

// Toggle checklist item
app.post('/api/tasks/:taskId/checklist/:itemId/toggle', authenticateToken, (req: AuthRequest, res) => {
  const user = req.user!;
  const task = jobStore.toggleChecklistItem(req.params.taskId, req.params.itemId, user.userId);
  if (!task) {
    return res.status(404).json({ error: 'Task or item not found' });
  }

  res.json({ task });
});

// ════════════════════════════════════════════════════════════════════
// STATS API
// ════════════════════════════════════════════════════════════════════

app.get('/api/stats', authenticateToken, (_req, res) => {
  const stats = jobStore.getStats();
  res.json({ stats });
});

// ════════════════════════════════════════════════════════════════════
// START SERVER
// ════════════════════════════════════════════════════════════════════

app.listen(PORT, () => {
  console.log('════════════════════════════════════════');
  console.log('📋 JOB BOARD (C-11) STARTED');
  console.log(`   HTTP: http://localhost:${PORT}`);
  console.log(`   API:  http://localhost:${PORT}/api/jobs`);
  console.log('════════════════════════════════════════');
});
