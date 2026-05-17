// backend/src/tasksService.ts
//
// Per-job task list. A task belongs to exactly one job; the job's foreman_id
// pins the tenant via jobs.organization_id → users.organization_id. There is
// no per-task ACL beyond "did the job's foreman make this request" (enforced
// in tasksRoutes + requireTaskOwner middleware).

import { pg, isPgEnabled } from './db';

export type TaskStatus = 'pending' | 'done';

export interface Task {
  id: string;
  jobId: string;
  title: string;
  status: TaskStatus;
  sortOrder: number;
  createdBy: string | null;
  createdAt: Date;
  updatedAt: Date;
  completedAt: Date | null;
}

function requirePg() {
  if (!isPgEnabled() || !pg) throw new Error('[TasksService] Postgres client not initialized');
  return pg;
}

function mapRow(row: any): Task {
  return {
    id: row.id,
    jobId: row.job_id,
    title: row.title,
    status: row.status as TaskStatus,
    sortOrder: row.sort_order,
    createdBy: row.created_by,
    createdAt: new Date(row.created_at),
    updatedAt: new Date(row.updated_at),
    completedAt: row.completed_at ? new Date(row.completed_at) : null,
  };
}

export async function listByJob(jobId: string): Promise<Task[]> {
  const db = requirePg();
  const { rows } = await db.query(
    `SELECT * FROM tasks WHERE job_id = $1 ORDER BY sort_order ASC, created_at ASC`,
    [jobId],
  );
  return rows.map(mapRow);
}

export async function getById(taskId: string): Promise<Task | null> {
  const db = requirePg();
  const { rows } = await db.query(`SELECT * FROM tasks WHERE id = $1`, [taskId]);
  return rows.length ? mapRow(rows[0]) : null;
}

export async function create(input: {
  jobId: string;
  title: string;
  createdBy: string;
}): Promise<Task> {
  const db = requirePg();
  // Append: next sort_order is one past the max for the job.
  const { rows } = await db.query(
    `INSERT INTO tasks (job_id, title, status, sort_order, created_by, updated_at)
       VALUES (
         $1, $2, 'pending',
         COALESCE((SELECT MAX(sort_order) + 1 FROM tasks WHERE job_id = $1), 0),
         $3, NOW()
       )
       RETURNING *`,
    [input.jobId, input.title, input.createdBy],
  );
  return mapRow(rows[0]);
}

export async function update(
  taskId: string,
  patch: Partial<{ title: string; status: TaskStatus; sortOrder: number }>,
): Promise<Task | null> {
  const db = requirePg();
  const sets: string[] = ['updated_at = NOW()'];
  const params: unknown[] = [];

  if (patch.title !== undefined) {
    params.push(patch.title);
    sets.push(`title = $${params.length}`);
  }
  if (patch.status !== undefined) {
    params.push(patch.status);
    sets.push(`status = $${params.length}`);
    // Stamp / clear completed_at as a side-effect of the status flip.
    sets.push(patch.status === 'done' ? `completed_at = NOW()` : `completed_at = NULL`);
  }
  if (patch.sortOrder !== undefined) {
    params.push(patch.sortOrder);
    sets.push(`sort_order = $${params.length}`);
  }

  params.push(taskId);
  const { rows } = await db.query(
    `UPDATE tasks SET ${sets.join(', ')} WHERE id = $${params.length} RETURNING *`,
    params,
  );
  return rows.length ? mapRow(rows[0]) : null;
}

export async function deleteTask(taskId: string): Promise<boolean> {
  const db = requirePg();
  const r = await db.query(`DELETE FROM tasks WHERE id = $1`, [taskId]);
  return (r.rowCount ?? 0) > 0;
}
