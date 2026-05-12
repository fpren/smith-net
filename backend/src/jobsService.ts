// backend/src/jobsService.ts
//
// Service layer for the Jobs domain. Pure functions on data; no Express types here.
// Routes (jobsRoutes.ts) call into these and map errors / shape responses.
//
// Mutation operations call auditLog.log() before returning — see plan spec.

import { pg, isPgEnabled } from './db';

export type JobStatus = 'planned' | 'in_progress' | 'complete' | 'cancelled';

export interface Job {
  id: string;
  foremanId: string;
  clientId: string | null;
  engagementId: string | null;
  title: string;
  description: string | null;
  status: JobStatus;
  scheduledAt: Date | null;
  location: string | null;
  createdAt: Date;
  updatedAt: Date;
}

export interface CrewAssignment {
  jobId: string;
  profileId: string;
  roleOnJob: 'crew' | 'lead';
  assignedAt: Date;
}

// ════════════════════════════════════════════════════════════════════
// Errors
// ════════════════════════════════════════════════════════════════════

export class NotFoundError extends Error {
  constructor(message: string = 'Job not found') {
    super(message);
    this.name = 'NotFoundError';
  }
}

export class InvalidTransitionError extends Error {
  constructor(public from: JobStatus, public to: JobStatus) {
    super(`Invalid status transition: ${from} -> ${to}`);
    this.name = 'InvalidTransitionError';
  }
}

// ════════════════════════════════════════════════════════════════════
// State machine
// ════════════════════════════════════════════════════════════════════

const VALID_TRANSITIONS: Record<JobStatus, JobStatus[]> = {
  planned:     ['in_progress', 'cancelled'],
  in_progress: ['complete', 'cancelled'],
  complete:    [],
  cancelled:   [],
};

export function assertValidTransition(from: JobStatus, to: JobStatus): void {
  if (!VALID_TRANSITIONS[from].includes(to)) {
    throw new InvalidTransitionError(from, to);
  }
}

// ════════════════════════════════════════════════════════════════════
// Internal helpers
// ════════════════════════════════════════════════════════════════════

function requirePg() {
  if (!isPgEnabled() || !pg) throw new Error('[JobsService] Postgres client not initialized');
  return pg;
}

function mapJobRow(row: any): Job {
  return {
    id: row.id,
    foremanId: row.foreman_id,
    clientId: row.client_id,
    engagementId: row.engagement_id,
    title: row.title,
    description: row.description,
    status: row.status as JobStatus,
    scheduledAt: row.scheduled_at ? new Date(row.scheduled_at) : null,
    location: row.location,
    createdAt: new Date(row.created_at),
    updatedAt: new Date(row.updated_at),
  };
}

function mapCrewRow(row: any): CrewAssignment {
  return {
    jobId: row.job_id,
    profileId: row.profile_id,
    roleOnJob: row.role_on_job as 'crew' | 'lead',
    assignedAt: new Date(row.assigned_at),
  };
}

// ════════════════════════════════════════════════════════════════════
// Read
// ════════════════════════════════════════════════════════════════════

export async function listByForeman(foremanId: string): Promise<Job[]> {
  const db = requirePg();
  const { rows } = await db.query(
    `SELECT * FROM jobs WHERE foreman_id = $1 ORDER BY created_at DESC`,
    [foremanId]
  );
  return rows.map(mapJobRow);
}

export async function getById(jobId: string): Promise<Job | null> {
  const db = requirePg();
  const { rows } = await db.query(`SELECT * FROM jobs WHERE id = $1`, [jobId]);
  return rows.length === 0 ? null : mapJobRow(rows[0]);
}

export async function listCrew(jobId: string): Promise<CrewAssignment[]> {
  const db = requirePg();
  const { rows } = await db.query(
    `SELECT * FROM job_crew WHERE job_id = $1 ORDER BY assigned_at ASC`,
    [jobId]
  );
  return rows.map(mapCrewRow);
}
