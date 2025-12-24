/**
 * C-11: Job Store
 * In-memory storage for jobs and tasks (replace with DB in production)
 */

import { v4 as uuidv4 } from 'uuid';
import {
  Job,
  Task,
  JobStatus,
  Priority,
  ChecklistItem,
  CreateJobRequest,
  UpdateJobRequest,
  CreateTaskRequest,
} from './types';

class JobStore {
  private jobs: Map<string, Job> = new Map();
  private tasks: Map<string, Task> = new Map();
  private tasksByJob: Map<string, string[]> = new Map();

  // ════════════════════════════════════════════════════════════════════
  // JOBS
  // ════════════════════════════════════════════════════════════════════

  createJob(request: CreateJobRequest, createdBy: string): Job {
    const job: Job = {
      id: uuidv4(),
      title: request.title,
      description: request.description || '',
      projectId: request.projectId,
      clientName: request.clientName,
      location: request.location,
      status: 'backlog',
      priority: request.priority || 'medium',
      createdBy,
      assignedTo: request.assignedTo || [],
      createdAt: Date.now(),
      updatedAt: Date.now(),
      dueDate: request.dueDate,
      tags: request.tags || [],
      metadata: {},
    };

    this.jobs.set(job.id, job);
    this.tasksByJob.set(job.id, []);

    console.log(`[JobStore] Created job: ${job.title} (${job.id})`);
    return job;
  }

  getJob(id: string): Job | undefined {
    return this.jobs.get(id);
  }

  updateJob(id: string, updates: UpdateJobRequest): Job | undefined {
    const job = this.jobs.get(id);
    if (!job) return undefined;

    const updated: Job = {
      ...job,
      ...updates,
      updatedAt: Date.now(),
    };

    // Track completion
    if (updates.status === 'done' && job.status !== 'done') {
      updated.completedAt = Date.now();
    }

    this.jobs.set(id, updated);
    console.log(`[JobStore] Updated job: ${updated.title}`);
    return updated;
  }

  deleteJob(id: string): boolean {
    const job = this.jobs.get(id);
    if (!job) return false;

    // Delete associated tasks
    const taskIds = this.tasksByJob.get(id) || [];
    for (const taskId of taskIds) {
      this.tasks.delete(taskId);
    }
    this.tasksByJob.delete(id);

    this.jobs.delete(id);
    console.log(`[JobStore] Deleted job: ${job.title}`);
    return true;
  }

  moveJob(id: string, newStatus: JobStatus): Job | undefined {
    return this.updateJob(id, { status: newStatus });
  }

  // ════════════════════════════════════════════════════════════════════
  // QUERIES
  // ════════════════════════════════════════════════════════════════════

  getAllJobs(): Job[] {
    return Array.from(this.jobs.values());
  }

  getJobsByStatus(status: JobStatus): Job[] {
    return this.getAllJobs().filter(j => j.status === status);
  }

  getJobsByUser(userId: string): Job[] {
    return this.getAllJobs().filter(
      j => j.createdBy === userId || j.assignedTo.includes(userId)
    );
  }

  getJobsByAssignee(userId: string): Job[] {
    return this.getAllJobs().filter(j => j.assignedTo.includes(userId));
  }

  getJobsByProject(projectId: string): Job[] {
    return this.getAllJobs().filter(j => j.projectId === projectId);
  }

  searchJobs(query: string): Job[] {
    const q = query.toLowerCase();
    return this.getAllJobs().filter(
      j =>
        j.title.toLowerCase().includes(q) ||
        j.description.toLowerCase().includes(q) ||
        j.tags.some(t => t.toLowerCase().includes(q))
    );
  }

  // ════════════════════════════════════════════════════════════════════
  // TASKS
  // ════════════════════════════════════════════════════════════════════

  createTask(request: CreateTaskRequest, createdBy: string): Task | undefined {
    const job = this.jobs.get(request.jobId);
    if (!job) return undefined;

    const existingTasks = this.tasksByJob.get(request.jobId) || [];

    const task: Task = {
      id: uuidv4(),
      jobId: request.jobId,
      title: request.title,
      description: request.description,
      status: 'pending',
      assignedTo: request.assignedTo,
      createdBy,
      createdAt: Date.now(),
      updatedAt: Date.now(),
      order: existingTasks.length,
      checklist: [],
    };

    this.tasks.set(task.id, task);
    existingTasks.push(task.id);
    this.tasksByJob.set(request.jobId, existingTasks);

    console.log(`[JobStore] Created task: ${task.title} for job ${job.title}`);
    return task;
  }

  getTask(id: string): Task | undefined {
    return this.tasks.get(id);
  }

  getTasksForJob(jobId: string): Task[] {
    const taskIds = this.tasksByJob.get(jobId) || [];
    return taskIds
      .map(id => this.tasks.get(id))
      .filter((t): t is Task => t !== undefined)
      .sort((a, b) => a.order - b.order);
  }

  updateTask(id: string, updates: Partial<Task>): Task | undefined {
    const task = this.tasks.get(id);
    if (!task) return undefined;

    const updated: Task = {
      ...task,
      ...updates,
      updatedAt: Date.now(),
    };

    if (updates.status === 'done' && task.status !== 'done') {
      updated.completedAt = Date.now();
    }

    this.tasks.set(id, updated);
    return updated;
  }

  deleteTask(id: string): boolean {
    const task = this.tasks.get(id);
    if (!task) return false;

    // Remove from job's task list
    const taskIds = this.tasksByJob.get(task.jobId) || [];
    const filtered = taskIds.filter(tid => tid !== id);
    this.tasksByJob.set(task.jobId, filtered);

    this.tasks.delete(id);
    return true;
  }

  addChecklistItem(taskId: string, text: string): Task | undefined {
    const task = this.tasks.get(taskId);
    if (!task) return undefined;

    const item: ChecklistItem = {
      id: uuidv4(),
      text,
      checked: false,
    };

    task.checklist.push(item);
    task.updatedAt = Date.now();
    this.tasks.set(taskId, task);
    return task;
  }

  toggleChecklistItem(taskId: string, itemId: string, userId: string): Task | undefined {
    const task = this.tasks.get(taskId);
    if (!task) return undefined;

    const item = task.checklist.find(i => i.id === itemId);
    if (!item) return undefined;

    item.checked = !item.checked;
    item.checkedAt = item.checked ? Date.now() : undefined;
    item.checkedBy = item.checked ? userId : undefined;

    task.updatedAt = Date.now();
    this.tasks.set(taskId, task);
    return task;
  }

  // ════════════════════════════════════════════════════════════════════
  // STATS
  // ════════════════════════════════════════════════════════════════════

  getStats(): {
    totalJobs: number;
    byStatus: Record<JobStatus, number>;
    byPriority: Record<Priority, number>;
    completedThisWeek: number;
  } {
    const jobs = this.getAllJobs();
    const weekAgo = Date.now() - 7 * 24 * 60 * 60 * 1000;

    const byStatus: Record<JobStatus, number> = {
      backlog: 0,
      todo: 0,
      in_progress: 0,
      review: 0,
      done: 0,
      archived: 0,
    };

    const byPriority: Record<Priority, number> = {
      low: 0,
      medium: 0,
      high: 0,
      urgent: 0,
    };

    let completedThisWeek = 0;

    for (const job of jobs) {
      byStatus[job.status]++;
      byPriority[job.priority]++;
      if (job.completedAt && job.completedAt >= weekAgo) {
        completedThisWeek++;
      }
    }

    return {
      totalJobs: jobs.length,
      byStatus,
      byPriority,
      completedThisWeek,
    };
  }
}

export const jobStore = new JobStore();
