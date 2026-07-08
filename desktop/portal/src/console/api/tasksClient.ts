// desktop/portal/src/console/api/tasksClient.ts
//
// Per-job task list. Mirrors jobsClient.ts in shape — uses the same
// credentials:'include' cookie-auth pattern. Backend lives at:
//   GET    /api/jobs/:jobId/tasks
//   POST   /api/tasks         body { jobId, title }
//   PATCH  /api/tasks/:id     body { title?, status?, sortOrder? }
//   DELETE /api/tasks/:id

import { httpCall } from './httpCall';

export type TaskStatus = 'pending' | 'done';

export interface Task {
  id: string;
  jobId: string;
  title: string;
  status: TaskStatus;
  sortOrder: number;
  createdBy: string | null;
  createdAt: string;
  updatedAt: string;
  completedAt: string | null;
}

export type TasksResult<T> =
  | ({ ok: true } & T)
  | { ok: false; status: number; error: string };

interface JsonInit { method?: string; body?: unknown }

async function call<T>(path: string, init: JsonInit = {}): Promise<TasksResult<T>> {
  const r = await httpCall<T>(path, {
    method: init.method ?? 'GET',
    headers: init.body !== undefined ? { 'Content-Type': 'application/json' } : undefined,
    body: init.body !== undefined ? JSON.stringify(init.body) : undefined,
  });
  if (!r.ok) {
    return { ok: false, status: r.status, error: r.error };
  }
  return { ok: true, ...((r.data ?? {}) as T) } as TasksResult<T>;
}

export const tasksClient = {
  listForJob: (jobId: string) =>
    call<{ tasks: Task[] }>(`/api/jobs/${encodeURIComponent(jobId)}/tasks`),

  create: (jobId: string, title: string) =>
    call<{ task: Task }>('/api/tasks', { method: 'POST', body: { jobId, title } }),

  update: (taskId: string, patch: Partial<{ title: string; status: TaskStatus; sortOrder: number }>) =>
    call<{ task: Task }>(`/api/tasks/${encodeURIComponent(taskId)}`, { method: 'PATCH', body: patch }),

  delete: (taskId: string) =>
    call<{}>(`/api/tasks/${encodeURIComponent(taskId)}`, { method: 'DELETE' }),
};
