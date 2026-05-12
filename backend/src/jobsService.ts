// backend/src/jobsService.ts
//
// Service layer for the Jobs domain. Pure functions on data; no Express types here.
// Routes (jobsRoutes.ts) call into these and map errors / shape responses.
//
// Mutation operations call auditLog.log() before returning — see plan spec.

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
