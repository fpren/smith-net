// backend/src/jobsService.ts
//
// Service layer for the Jobs domain. Pure functions on data; no Express types here.
// Routes (jobsRoutes.ts) call into these and map errors / shape responses.
//
// Mutation operations call auditLog.log() before returning — see plan spec.

import bcrypt from 'bcryptjs';
import { pg, isPgEnabled } from './db';
import { auditLog, AuditAction } from './auditLog';
import { notificationService } from './notificationService';
import { v4 as uuidv4 } from 'uuid';
import { enqueue } from './queue/queue';
import { StoredUser, UserRole, validatePassword } from './auth';

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
  latitude: number | null;        // NEW
  longitude: number | null;       // NEW
  geocodedAt: Date | null;        // NEW
  createdAt: Date;
  updatedAt: Date;
  client: { id: string; name: string } | null;
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
    latitude: row.latitude !== null ? Number(row.latitude) : null,
    longitude: row.longitude !== null ? Number(row.longitude) : null,
    geocodedAt: row.geocoded_at ? new Date(row.geocoded_at) : null,
    createdAt: new Date(row.created_at),
    updatedAt: new Date(row.updated_at),
    client: row.client_name ? { id: row.client_id, name: row.client_name } : null,
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

// Jobs a user can connect time to: owned (foreman) OR assigned (job_crew). All-tier.
export async function listForUser(userId: string): Promise<Job[]> {
  const db = requirePg();
  const { rows } = await db.query(
    `SELECT DISTINCT j.* FROM jobs j
       LEFT JOIN job_crew jc ON jc.job_id = j.id
      WHERE j.foreman_id = $1 OR jc.profile_id = $1
      ORDER BY j.created_at DESC`,
    [userId]
  );
  return rows.map(mapJobRow);
}

// Does this user own (foreman) or get assigned to (job_crew) the given job?
export async function canUserAccessJob(jobId: string, userId: string): Promise<boolean> {
  const db = requirePg();
  const { rows } = await db.query(
    `SELECT 1 FROM jobs j
       LEFT JOIN job_crew jc ON jc.job_id = j.id
      WHERE j.id = $1 AND (j.foreman_id = $2 OR jc.profile_id = $2)
      LIMIT 1`,
    [jobId, userId]
  );
  return rows.length > 0;
}

/** Count active (non-terminal) jobs for a foreman. Used by the active_jobs cap. */
export async function countActive(foremanId: string): Promise<number> {
  const db = requirePg();
  const { rows } = await db.query(
    `SELECT COUNT(*)::int AS c FROM jobs
       WHERE foreman_id = $1 AND status NOT IN ('complete', 'cancelled')`,
    [foremanId]
  );
  return rows[0].c;
}

export async function getById(jobId: string): Promise<Job | null> {
  const db = requirePg();
  const { rows } = await db.query(
    `SELECT j.*, c.name AS client_name
       FROM jobs j
       LEFT JOIN clients c ON c.id = j.client_id AND c.is_deleted = FALSE
      WHERE j.id = $1`,
    [jobId]
  );
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

export async function listByClient(clientId: string, foremanId: string): Promise<Job[]> {
  const db = requirePg();
  const { rows } = await db.query(
    `SELECT * FROM jobs WHERE client_id = $1 AND foreman_id = $2 ORDER BY created_at DESC`,
    [clientId, foremanId]
  );
  return rows.map(mapJobRow);
}

export async function clientBelongsToOwner(clientId: string, ownerId: string): Promise<boolean> {
  const db = requirePg();
  const { rowCount } = await db.query(
    `SELECT 1 FROM clients WHERE id = $1 AND owner_id = $2 AND is_deleted = FALSE`,
    [clientId, ownerId]
  );
  return (rowCount ?? 0) > 0;
}

// ════════════════════════════════════════════════════════════════════
// Mutate
// ════════════════════════════════════════════════════════════════════

export interface CreateJobInput {
  foremanId: string;
  title: string;
  description?: string;
  scheduledAt?: Date;
  location?: string;
  clientId?: string;
  engagementId?: string;
}

export async function create(input: CreateJobInput): Promise<Job> {
  const db = requirePg();
  const id = uuidv4();
  const now = new Date();

  const { rows } = await db.query(
    `INSERT INTO jobs
       (id, foreman_id, client_id, engagement_id, title, description,
        status, scheduled_at, location, created_at, updated_at)
     VALUES ($1, $2, $3, $4, $5, $6, 'planned', $7, $8, $9, $9)
     RETURNING *`,
    [
      id,
      input.foremanId,
      input.clientId ?? null,
      input.engagementId ?? null,
      input.title,
      input.description ?? null,
      input.scheduledAt ?? null,
      input.location ?? null,
      now,
    ]
  );

  const job = mapJobRow(rows[0]);

  await auditLog.log(AuditAction.JOB_CREATED, input.foremanId, {
    jobId: job.id,
    title: job.title,
    status: job.status,
    scheduledAt: job.scheduledAt,
    location: job.location,
    clientId: job.clientId,
    engagementId: job.engagementId,
  });

  // Phase 3 Slice 1: enqueue geocode instead of fire-and-forget.
  // Worker picks it up off background_jobs and UPDATEs lat/lng asynchronously.
  if (job.location) {
    await enqueue({
      kind: 'geocode',
      payload: { job_id: job.id, address: job.location },
      dedupeKey: `geocode:${job.id}`,
    });
  }

  return job;
}

export async function changeStatus(jobId: string, newStatus: JobStatus): Promise<Job> {
  const db = requirePg();
  const existing = await getById(jobId);
  if (!existing) throw new NotFoundError();

  assertValidTransition(existing.status, newStatus);

  const { rows } = await db.query(
    `UPDATE jobs SET status = $1, updated_at = NOW() WHERE id = $2 RETURNING *`,
    [newStatus, jobId]
  );

  const job = mapJobRow(rows[0]);

  await auditLog.log(AuditAction.JOB_STATUS_CHANGED, job.foremanId, {
    jobId: job.id,
    from: existing.status,
    to: newStatus,
  });

  return job;
}

export type UpdatePatch = Partial<Pick<Job, 'title' | 'description' | 'scheduledAt' | 'location'>> & { clientId?: string | null };

export async function update(jobId: string, patch: UpdatePatch): Promise<Job> {
  const db = requirePg();
  const changedFields: string[] = [];
  const sets: string[] = [];
  const params: any[] = [];
  let paramIdx = 1;

  if (patch.title !== undefined) { sets.push(`title = $${paramIdx++}`); params.push(patch.title); changedFields.push('title'); }
  if (patch.description !== undefined) { sets.push(`description = $${paramIdx++}`); params.push(patch.description); changedFields.push('description'); }
  if (patch.scheduledAt !== undefined) { sets.push(`scheduled_at = $${paramIdx++}`); params.push(patch.scheduledAt); changedFields.push('scheduledAt'); }
  if (patch.location !== undefined) { sets.push(`location = $${paramIdx++}`); params.push(patch.location); changedFields.push('location'); }
  if ('clientId' in patch) { sets.push(`client_id = $${paramIdx++}`); params.push(patch.clientId ?? null); changedFields.push('clientId'); }

  if (sets.length === 0) {
    const existing = await getById(jobId);
    if (!existing) throw new NotFoundError();
    return existing;
  }

  sets.push(`updated_at = NOW()`);
  params.push(jobId);

  const { rows } = await db.query(
    `UPDATE jobs SET ${sets.join(', ')} WHERE id = $${paramIdx} RETURNING *`,
    params
  );

  if (rows.length === 0) throw new NotFoundError();
  const job = mapJobRow(rows[0]);

  await auditLog.log(AuditAction.JOB_UPDATED, job.foremanId, {
    jobId: job.id,
    changedFields,
    after: { title: job.title, description: job.description, scheduledAt: job.scheduledAt, location: job.location },
  });

  // Phase 3 Slice 1: enqueue geocode instead of fire-and-forget.
  // dedupeKey is per-job so multiple location edits collapse to one queued row
  // until the worker drains it; subsequent location changes after that re-enqueue.
  if (changedFields.includes('location') && job.location) {
    await enqueue({
      kind: 'geocode',
      payload: { job_id: job.id, address: job.location },
      dedupeKey: `geocode:${job.id}`,
    });
  }

  return job;
}

export async function assignCrew(
  jobId: string,
  profileId: string,
  roleOnJob: 'crew' | 'lead' = 'crew'
): Promise<CrewAssignment> {
  const db = requirePg();
  const job = await getById(jobId);
  if (!job) throw new NotFoundError();

  try {
    const { rows } = await db.query(
      `INSERT INTO job_crew (job_id, profile_id, role_on_job)
       VALUES ($1, $2, $3) RETURNING *`,
      [jobId, profileId, roleOnJob]
    );
    const assignment = mapCrewRow(rows[0]);

    await auditLog.log(AuditAction.JOB_CREW_ASSIGNED, job.foremanId, {
      jobId,
      profileId,
      roleOnJob,
    });

    // N-1 producer: notify the assignee (best-effort -- a failed notification
    // must not fail the assignment). Awaited in-request, not fire-and-forget.
    try {
      await notificationService.create({
        userId: profileId,
        type: 'job_assigned',
        title: `You were assigned ${job.title}`,
        link: `/console/jobs/${jobId}`,
        actorId: job.foremanId,
      });
    } catch (err) {
      console.warn('[assignCrew] notification producer failed:', (err as Error).message);
    }

    return assignment;
  } catch (e: any) {
    if (e.code === '23505') {
      const err: any = new Error('Crew member already assigned');
      err.code = 'duplicate_assignment';
      throw err;
    }
    throw e;
  }
}

export async function unassignCrew(jobId: string, profileId: string): Promise<void> {
  const db = requirePg();
  const job = await getById(jobId);
  if (!job) throw new NotFoundError();

  const { rowCount } = await db.query(
    `DELETE FROM job_crew WHERE job_id = $1 AND profile_id = $2`,
    [jobId, profileId]
  );

  if (rowCount === 0) {
    throw new NotFoundError('Assignment not found');
  }

  await auditLog.log(AuditAction.JOB_CREW_UNASSIGNED, job.foremanId, {
    jobId,
    profileId,
  });
}

// ════════════════════════════════════════════════════════════════════
// Transactional user+profile create (Phase 2 Slice 1)
// ════════════════════════════════════════════════════════════════════

interface CreateUserAndProfileInput {
  email: string;
  password: string;
  displayName: string;
  role: UserRole;
  /** Internal — only used by tests to force a collision. */
  forcedId?: string;
}

/**
 * Phase 2 Slice 1: create a user row AND its matching profile row inside a
 * single transaction. Either both land or neither does. Closes audit weak
 * point #1 (userStore <-> profiles FK drift).
 */
export async function createUserAndProfile(input: CreateUserAndProfileInput): Promise<StoredUser> {
  const db = requirePg();
  const validation = validatePassword(input.password);
  if (!validation.valid) throw new Error(validation.reason);

  const id = input.forcedId ?? uuidv4();
  const SALT_ROUNDS = 10;
  const EMAIL_VERIFICATION_TTL_MS = 24 * 60 * 60 * 1000;
  const crypto = require('crypto');
  const passwordHash = await bcrypt.hash(input.password, SALT_ROUNDS);
  const verificationToken = crypto.randomBytes(32).toString('hex');
  const verificationExpires = new Date(Date.now() + EMAIL_VERIFICATION_TTL_MS);

  const client = await db.connect();
  try {
    await client.query('BEGIN');
    await client.query(
      `INSERT INTO users (
         id, email, password_hash, display_name, role, organization_id,
         is_active, mfa_enabled, failed_login_count,
         email_verification_token, email_verification_expires_at
       ) VALUES ($1, $2, $3, $4, $5, $1, TRUE, FALSE, 0, $6, $7)`,
      [id, input.email.toLowerCase(), passwordHash, input.displayName, input.role, verificationToken, verificationExpires]
    );
    await client.query(
      `INSERT INTO profiles (id, email, display_name, role)
       VALUES ($1, $2, $3, $4)`,
      [id, input.email.toLowerCase(), input.displayName, input.role]
    );
    await client.query('COMMIT');
  } catch (err) {
    await client.query('ROLLBACK');
    throw err;
  } finally {
    client.release();
  }

  // Re-fetch via usersService to apply the row->StoredUser mapper.
  // Dynamic import avoids circularity: auth.ts -> usersService.ts -> auth.ts.
  const { usersService } = await import('./usersService');
  const fresh = await usersService.getUserById(id);
  if (!fresh) throw new Error('[createUserAndProfile] post-insert lookup failed');
  return fresh;
}
