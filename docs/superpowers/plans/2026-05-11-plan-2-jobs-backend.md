# Plan 2 — Jobs Backend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the backend Jobs vertical so the Plan 1 operator console can list, create, update, dispatch, and assign-crew on Foreman-scoped jobs. State changes write to `auditLog` for audit trail.

**Architecture:** Standalone `jobs` + `job_crew` tables. `jobsService.ts` exposes pure data functions; `jobsRoutes.ts` is the Express router mounted at `/api/jobs`. Two new middlewares: `requireConsoleTier` (Advanced+ role gate) and `requireJobOwner` (foreman ownership of `:id` routes). Each state-changing service mutation calls `auditLog.log(AuditAction.JOB_*, foremanId, details)`.

**Tech Stack:** Express + `pg.Pool` (from `db.ts`) + `zod` (F1.5) + `jest` + `supertest` (Plan 1 Task 2.5). No new npm deps.

**Spec:** `docs/superpowers/specs/2026-05-11-plan-2-jobs-backend-design.md`

**Scope boundary (NOT in this plan):**
- WebSocket events / live push (Plan 3)
- `/api/clients` (Plan 5)
- Standalone `/api/crew` CRUD (Plan 4 — Plan 2 assignment uses `profile_id` directly)
- Frontend Job Board UI (Plan 3)

**Pre-flight (verified before plan writing):**
- `auditLog` is the canonical audit mechanism — used by `authRoutes.ts` for `USER_REGISTER`, `USER_LOGIN`, etc.
- `intentService` is NOT used for state-change audit (it's for scope negotiation)
- No `/jobs` route prefix collision in existing `api.ts`
- No migration runner exists — migrations are applied via `psql` manually

---

## File Structure

**New files:**
- `backend/migrations/003_jobs_expansion.sql`
- `backend/src/schemas/jobs.ts`
- `backend/src/middleware/requireConsoleTier.ts`
- `backend/src/middleware/requireJobOwner.ts`
- `backend/src/jobsService.ts`
- `backend/src/jobsRoutes.ts`
- `backend/src/__tests__/jobs-status-machine.test.ts` (pure unit tests for transitions)
- `backend/src/__tests__/jobs-middleware.test.ts` (requireConsoleTier + requireJobOwner)
- `backend/src/__tests__/jobs-routes.test.ts` (integration tests against pg — gated by DATABASE_URL)

**Modified files:**
- `backend/src/auditLog.ts` (add 5 `AuditAction.JOB_*` enum values)
- `backend/src/server.ts` (one new line: mount `jobsRouter` at `/api/jobs`)

---

## Task 1: Migration `003_jobs_expansion.sql`

**Files:**
- Create: `backend/migrations/003_jobs_expansion.sql`

- [ ] **Step 1: Create the SQL file**

Write to `backend/migrations/003_jobs_expansion.sql`:

```sql
-- 003_jobs_expansion.sql
-- Plan 2: expand jobs + recreate job_crew

-- ────────────── Expand jobs ──────────────
ALTER TABLE jobs
  ADD COLUMN IF NOT EXISTS foreman_id     TEXT REFERENCES profiles(id),
  ADD COLUMN IF NOT EXISTS client_id      UUID,
  ADD COLUMN IF NOT EXISTS engagement_id  UUID,
  ADD COLUMN IF NOT EXISTS scheduled_at   TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS location       TEXT,
  ADD COLUMN IF NOT EXISTS updated_at     TIMESTAMPTZ DEFAULT NOW();

ALTER TABLE jobs ALTER COLUMN status SET DEFAULT 'planned';

CREATE INDEX IF NOT EXISTS idx_jobs_foreman ON jobs(foreman_id);
CREATE INDEX IF NOT EXISTS idx_jobs_status  ON jobs(status);

-- ────────────── Recreate job_crew ──────────────
DROP TABLE IF EXISTS job_crew;
CREATE TABLE job_crew (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  job_id       UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
  profile_id   TEXT NOT NULL REFERENCES profiles(id),
  role_on_job  TEXT NOT NULL DEFAULT 'crew',
  assigned_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE(job_id, profile_id)
);
CREATE INDEX idx_job_crew_job     ON job_crew(job_id);
CREATE INDEX idx_job_crew_profile ON job_crew(profile_id);
```

- [ ] **Step 2: Apply the migration to the local dev DB**

If `DATABASE_URL` is set in the local env, apply it. If you're unsure of the URL, ask the user.

```bash
psql "$DATABASE_URL" -f /Users/fegensprenelon/smith-net/backend/migrations/003_jobs_expansion.sql
```

Expected: `ALTER TABLE` / `CREATE INDEX` / `DROP TABLE` / `CREATE TABLE` / `CREATE INDEX` messages, no errors.

If `DATABASE_URL` is NOT set: skip Step 2 and Step 3, note as `DONE_WITH_CONCERNS` so the user knows the migration hasn't been applied locally.

- [ ] **Step 3: Verify schema**

```bash
psql "$DATABASE_URL" -c "\d jobs" | head -20
psql "$DATABASE_URL" -c "\d job_crew" | head -15
```

Expected: `jobs` has the new columns (foreman_id, client_id, engagement_id, scheduled_at, location, updated_at). `job_crew` has the new shape (job_id, profile_id, role_on_job, assigned_at) with a unique constraint.

- [ ] **Step 4: Commit**

```bash
git -C /Users/fegensprenelon/smith-net add backend/migrations/003_jobs_expansion.sql
git -C /Users/fegensprenelon/smith-net commit -m "feat(jobs): migration 003 — expand jobs + recreate job_crew"
```

---

## Task 2: Extend `AuditAction` enum

**Files:**
- Modify: `backend/src/auditLog.ts`

- [ ] **Step 1: Add 5 new enum values**

Open `backend/src/auditLog.ts`. Find the `export enum AuditAction { ... }` block (starts around line 15). Add these values in the section that makes sense (alongside other action groups — there's likely a "Channels" or "Messages" group; add a new "Jobs" group right after):

```ts
  // Jobs
  JOB_CREATED = 'job.created',
  JOB_UPDATED = 'job.updated',
  JOB_STATUS_CHANGED = 'job.status_changed',
  JOB_CREW_ASSIGNED = 'job.crew_assigned',
  JOB_CREW_UNASSIGNED = 'job.crew_unassigned',
```

- [ ] **Step 2: Verify tsc clean**

```bash
cd /Users/fegensprenelon/smith-net/backend && npx tsc --noEmit 2>&1 | grep -E "auditLog\.ts" || echo "no auditLog errors"
```

Expected: "no auditLog errors" (any pre-existing test-file errors are fine — those existed before this plan).

- [ ] **Step 3: Commit**

```bash
git -C /Users/fegensprenelon/smith-net add backend/src/auditLog.ts
git -C /Users/fegensprenelon/smith-net commit -m "feat(audit): add JOB_* action codes"
```

---

## Task 3: Zod schemas `schemas/jobs.ts`

**Files:**
- Create: `backend/src/schemas/jobs.ts`
- Modify: `backend/src/schemas/index.ts` (re-export the new schemas — match the existing pattern)

- [ ] **Step 1: Create the schemas file**

```ts
// backend/src/schemas/jobs.ts
import { z } from 'zod';

export const CreateJobBody = z.object({
  title:        z.string().trim().min(1).max(200),
  description:  z.string().trim().max(5000).optional(),
  scheduledAt:  z.string().datetime().optional(),
  location:     z.string().trim().max(500).optional(),
  clientId:     z.string().uuid().optional(),
  engagementId: z.string().uuid().optional(),
}).strict();
export type CreateJobBody = z.infer<typeof CreateJobBody>;

export const UpdateJobBody = z.object({
  title:       z.string().trim().min(1).max(200).optional(),
  description: z.string().trim().max(5000).optional().nullable(),
  scheduledAt: z.string().datetime().optional().nullable(),
  location:    z.string().trim().max(500).optional().nullable(),
}).strict();
export type UpdateJobBody = z.infer<typeof UpdateJobBody>;

export const StatusChangeBody = z.object({
  status: z.enum(['planned', 'in_progress', 'complete', 'cancelled']),
}).strict();
export type StatusChangeBody = z.infer<typeof StatusChangeBody>;

export const AssignCrewBody = z.object({
  profileId: z.string().min(1).max(100),
  roleOnJob: z.enum(['crew', 'lead']).optional(),
}).strict();
export type AssignCrewBody = z.infer<typeof AssignCrewBody>;
```

- [ ] **Step 2: Re-export from `schemas/index.ts`**

Read the current `backend/src/schemas/index.ts` to see the export pattern (it currently re-exports auth schemas). Add a parallel re-export for jobs:

```ts
export * from './jobs';
```

If `index.ts` uses `export { X, Y } from './auth';` (named re-exports), use the same pattern for the 4 named exports from jobs.

- [ ] **Step 3: Verify tsc clean**

```bash
cd /Users/fegensprenelon/smith-net/backend && npx tsc --noEmit 2>&1 | grep -E "schemas/" || echo "no schema errors"
```

Expected: "no schema errors".

- [ ] **Step 4: Commit**

```bash
git -C /Users/fegensprenelon/smith-net add backend/src/schemas/jobs.ts backend/src/schemas/index.ts
git -C /Users/fegensprenelon/smith-net commit -m "feat(jobs): zod schemas for /api/jobs/* request bodies"
```

---

## Task 4: `middleware/requireConsoleTier.ts` (TDD)

**Files:**
- Create: `backend/src/middleware/requireConsoleTier.ts`
- Create: `backend/src/__tests__/jobs-middleware.test.ts`

- [ ] **Step 1: Write the failing test**

Create `backend/src/__tests__/jobs-middleware.test.ts`:

```ts
import express, { Request, Response, NextFunction } from 'express';
import request from 'supertest';
import cookieParser from 'cookie-parser';
import { authRouter } from '../authRoutes';
import { userStore, generateTokens, UserRole, authenticateToken, AuthenticatedRequest } from '../auth';
import { requireConsoleTier } from '../middleware/requireConsoleTier';

function buildApp() {
  const app = express();
  app.use(express.json());
  app.use(cookieParser());
  app.use('/api/auth', authRouter);
  app.get('/api/protected', authenticateToken, requireConsoleTier, (req: AuthenticatedRequest, res: Response) => {
    res.json({ ok: true, role: req.user!.role });
  });
  return app;
}

describe('requireConsoleTier', () => {
  const app = buildApp();

  it('returns 401 when no token', async () => {
    const res = await request(app).get('/api/protected');
    expect(res.status).toBe(401);
  });

  it('returns 403 tier_required for SOLO role', async () => {
    const u = await userStore.createUser('tier-solo@example.com', 'password123', 'S', UserRole.SOLO);
    const { accessToken } = generateTokens(u);
    const res = await request(app).get('/api/protected').set('Authorization', `Bearer ${accessToken}`);
    expect(res.status).toBe(403);
    expect(res.body.code).toBe('tier_required');
    expect(res.body.currentRole).toBe('solo');
  });

  it('returns 200 for FOREMAN role', async () => {
    const u = await userStore.createUser('tier-foreman@example.com', 'password123', 'F', UserRole.FOREMAN);
    const { accessToken } = generateTokens(u);
    const res = await request(app).get('/api/protected').set('Authorization', `Bearer ${accessToken}`);
    expect(res.status).toBe(200);
    expect(res.body.role).toBe('foreman');
  });

  it('returns 200 for ENTERPRISE role', async () => {
    const u = await userStore.createUser('tier-ent@example.com', 'password123', 'E', UserRole.ENTERPRISE);
    const { accessToken } = generateTokens(u);
    const res = await request(app).get('/api/protected').set('Authorization', `Bearer ${accessToken}`);
    expect(res.status).toBe(200);
  });

  it('returns 200 for ADMIN role', async () => {
    const u = await userStore.createUser('tier-admin@example.com', 'password123', 'A', UserRole.ADMIN);
    const { accessToken } = generateTokens(u);
    const res = await request(app).get('/api/protected').set('Authorization', `Bearer ${accessToken}`);
    expect(res.status).toBe(200);
  });

  it('returns 403 tier_required for TEAM_LEAD role', async () => {
    const u = await userStore.createUser('tier-lead@example.com', 'password123', 'L', UserRole.TEAM_LEAD);
    const { accessToken } = generateTokens(u);
    const res = await request(app).get('/api/protected').set('Authorization', `Bearer ${accessToken}`);
    expect(res.status).toBe(403);
    expect(res.body.code).toBe('tier_required');
  });
});
```

- [ ] **Step 2: Run test — confirm FAIL**

```bash
cd /Users/fegensprenelon/smith-net/backend && JWT_SECRET=test-secret-at-least-32-chars-long-please-thanks npx jest jobs-middleware 2>&1 | tail -15
```

Expected: FAIL — `Cannot find module '../middleware/requireConsoleTier'`.

- [ ] **Step 3: Implement the middleware**

Create `backend/src/middleware/requireConsoleTier.ts`:

```ts
import { Response, NextFunction } from 'express';
import { AuthenticatedRequest, UserRole } from '../auth';

export const CONSOLE_ROLES: UserRole[] = [UserRole.FOREMAN, UserRole.ENTERPRISE, UserRole.ADMIN];

export function requireConsoleTier(req: AuthenticatedRequest, res: Response, next: NextFunction) {
  if (!req.user) {
    return res.status(401).json({ error: 'Authentication required' });
  }
  if (!CONSOLE_ROLES.includes(req.user.role)) {
    return res.status(403).json({
      error: 'Console access requires Advanced tier',
      code: 'tier_required',
      currentRole: req.user.role,
    });
  }
  next();
}
```

- [ ] **Step 4: Run test — confirm PASS**

```bash
cd /Users/fegensprenelon/smith-net/backend && JWT_SECRET=test-secret-at-least-32-chars-long-please-thanks npx jest jobs-middleware 2>&1 | tail -10
```

Expected: 6 tests PASS.

- [ ] **Step 5: Commit**

```bash
git -C /Users/fegensprenelon/smith-net add backend/src/middleware/requireConsoleTier.ts backend/src/__tests__/jobs-middleware.test.ts
git -C /Users/fegensprenelon/smith-net commit -m "feat(jobs): requireConsoleTier middleware (Foreman+ gate)"
```

---

## Task 5: `jobsService` types + pure state-machine helper (TDD)

This task adds **only the pure parts** of `jobsService` — types and `assertValidTransition`. No pg dependency yet.

**Files:**
- Create: `backend/src/jobsService.ts`
- Create: `backend/src/__tests__/jobs-status-machine.test.ts`

- [ ] **Step 1: Write the failing test**

Create `backend/src/__tests__/jobs-status-machine.test.ts`:

```ts
import { assertValidTransition, InvalidTransitionError, JobStatus } from '../jobsService';

describe('assertValidTransition', () => {
  const validTransitions: [JobStatus, JobStatus][] = [
    ['planned', 'in_progress'],
    ['planned', 'cancelled'],
    ['in_progress', 'complete'],
    ['in_progress', 'cancelled'],
  ];

  const invalidTransitions: [JobStatus, JobStatus][] = [
    ['planned', 'complete'],
    ['planned', 'planned'],
    ['in_progress', 'planned'],
    ['in_progress', 'in_progress'],
    ['complete', 'planned'],
    ['complete', 'in_progress'],
    ['complete', 'cancelled'],
    ['cancelled', 'planned'],
    ['cancelled', 'in_progress'],
    ['cancelled', 'complete'],
  ];

  it.each(validTransitions)('allows %s -> %s', (from, to) => {
    expect(() => assertValidTransition(from, to)).not.toThrow();
  });

  it.each(invalidTransitions)('rejects %s -> %s with InvalidTransitionError', (from, to) => {
    expect(() => assertValidTransition(from, to)).toThrow(InvalidTransitionError);
  });

  it('error carries from + to fields', () => {
    try {
      assertValidTransition('complete', 'planned');
      fail('should have thrown');
    } catch (e) {
      expect(e).toBeInstanceOf(InvalidTransitionError);
      expect((e as InvalidTransitionError).from).toBe('complete');
      expect((e as InvalidTransitionError).to).toBe('planned');
    }
  });
});
```

- [ ] **Step 2: Run test — confirm FAIL**

```bash
cd /Users/fegensprenelon/smith-net/backend && JWT_SECRET=test-secret-at-least-32-chars-long-please-thanks npx jest jobs-status-machine 2>&1 | tail -10
```

Expected: FAIL — `Cannot find module '../jobsService'`.

- [ ] **Step 3: Create the initial `jobsService.ts` with types + state machine helper**

```ts
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
```

- [ ] **Step 4: Run test — confirm PASS (14 tests)**

```bash
cd /Users/fegensprenelon/smith-net/backend && JWT_SECRET=test-secret-at-least-32-chars-long-please-thanks npx jest jobs-status-machine 2>&1 | tail -10
```

Expected: 14 PASS (4 valid + 10 invalid + 1 error-shape).

- [ ] **Step 5: Commit**

```bash
git -C /Users/fegensprenelon/smith-net add backend/src/jobsService.ts backend/src/__tests__/jobs-status-machine.test.ts
git -C /Users/fegensprenelon/smith-net commit -m "feat(jobs): jobsService types + assertValidTransition state machine"
```

---

## Task 6: `jobsService` read operations (`listByForeman`, `getById`, `listCrew`)

**Files:**
- Modify: `backend/src/jobsService.ts` (append read functions)

This task adds pg-touching code. We deliberately do not add a service-level unit test here — the read functions are simple SQL passthrough. Coverage comes in Task 10 (integration tests via supertest).

- [ ] **Step 1: Append read functions + pg setup to `jobsService.ts`**

Add this near the top of the file (after the imports — there are none yet, so before the type exports):

```ts
import { pg, isPgEnabled } from './db';

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
```

Then append at the bottom of the file:

```ts
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
```

- [ ] **Step 2: Verify status-machine test still passes (no regression)**

```bash
cd /Users/fegensprenelon/smith-net/backend && JWT_SECRET=test-secret-at-least-32-chars-long-please-thanks npx jest jobs-status-machine 2>&1 | tail -5
```

Expected: 14 pass.

- [ ] **Step 3: Verify tsc clean**

```bash
cd /Users/fegensprenelon/smith-net/backend && npx tsc --noEmit 2>&1 | grep -E "jobsService" || echo "no jobsService errors"
```

Expected: "no jobsService errors".

- [ ] **Step 4: Commit**

```bash
git -C /Users/fegensprenelon/smith-net add backend/src/jobsService.ts
git -C /Users/fegensprenelon/smith-net commit -m "feat(jobs): jobsService read ops — listByForeman, getById, listCrew"
```

---

## Task 7: `middleware/requireJobOwner.ts` (TDD)

**Files:**
- Create: `backend/src/middleware/requireJobOwner.ts`
- Modify: `backend/src/__tests__/jobs-middleware.test.ts` (append a new describe block)

- [ ] **Step 1: Append the failing test**

Append to `backend/src/__tests__/jobs-middleware.test.ts` after the existing `describe` block:

```ts
import { requireJobOwner } from '../middleware/requireJobOwner';
import * as jobsService from '../jobsService';

describe('requireJobOwner', () => {
  it('returns 404 when job does not exist', async () => {
    jest.spyOn(jobsService, 'getById').mockResolvedValueOnce(null);

    const app = express();
    app.use(express.json());
    const fakeAuth = (req: any, _res: any, next: any) => {
      req.user = { id: 'foreman-1', role: 'foreman' };
      next();
    };
    app.get('/api/jobs/:id/test', fakeAuth, requireJobOwner, (_req, res) => res.json({ ok: true }));

    const res = await request(app).get('/api/jobs/nonexistent/test');
    expect(res.status).toBe(404);
  });

  it('returns 403 not_owner when foreman_id mismatches req.user.id', async () => {
    jest.spyOn(jobsService, 'getById').mockResolvedValueOnce({
      id: 'job-1',
      foremanId: 'OTHER_FOREMAN',
      clientId: null,
      engagementId: null,
      title: 'X',
      description: null,
      status: 'planned',
      scheduledAt: null,
      location: null,
      createdAt: new Date(),
      updatedAt: new Date(),
    });

    const app = express();
    app.use(express.json());
    const fakeAuth = (req: any, _res: any, next: any) => {
      req.user = { id: 'foreman-1', role: 'foreman' };
      next();
    };
    app.get('/api/jobs/:id/test', fakeAuth, requireJobOwner, (_req, res) => res.json({ ok: true }));

    const res = await request(app).get('/api/jobs/job-1/test');
    expect(res.status).toBe(403);
    expect(res.body.code).toBe('not_owner');
  });

  it('attaches job to req and calls next when owner matches', async () => {
    const job = {
      id: 'job-2',
      foremanId: 'foreman-1',
      clientId: null,
      engagementId: null,
      title: 'mine',
      description: null,
      status: 'planned' as const,
      scheduledAt: null,
      location: null,
      createdAt: new Date(),
      updatedAt: new Date(),
    };
    jest.spyOn(jobsService, 'getById').mockResolvedValueOnce(job);

    const app = express();
    app.use(express.json());
    const fakeAuth = (req: any, _res: any, next: any) => {
      req.user = { id: 'foreman-1', role: 'foreman' };
      next();
    };
    app.get('/api/jobs/:id/test', fakeAuth, requireJobOwner, (req: any, res) => res.json({ jobTitle: req.job.title }));

    const res = await request(app).get('/api/jobs/job-2/test');
    expect(res.status).toBe(200);
    expect(res.body.jobTitle).toBe('mine');
  });
});
```

- [ ] **Step 2: Run test — confirm FAIL**

```bash
cd /Users/fegensprenelon/smith-net/backend && JWT_SECRET=test-secret-at-least-32-chars-long-please-thanks npx jest jobs-middleware 2>&1 | tail -15
```

Expected: 3 new tests FAIL — `Cannot find module '../middleware/requireJobOwner'`.

- [ ] **Step 3: Implement the middleware**

Create `backend/src/middleware/requireJobOwner.ts`:

```ts
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
```

- [ ] **Step 4: Run test — confirm 9 tests PASS in this file (6 tier + 3 owner)**

```bash
cd /Users/fegensprenelon/smith-net/backend && JWT_SECRET=test-secret-at-least-32-chars-long-please-thanks npx jest jobs-middleware 2>&1 | tail -10
```

Expected: 9 PASS.

- [ ] **Step 5: Commit**

```bash
git -C /Users/fegensprenelon/smith-net add backend/src/middleware/requireJobOwner.ts backend/src/__tests__/jobs-middleware.test.ts
git -C /Users/fegensprenelon/smith-net commit -m "feat(jobs): requireJobOwner middleware with attach-to-req pattern"
```

---

## Task 8: `jobsRoutes.ts` skeleton + GET `/` and GET `/:id`

**Files:**
- Create: `backend/src/jobsRoutes.ts`
- Modify: `backend/src/server.ts` (mount the router)

- [ ] **Step 1: Create `jobsRoutes.ts`**

```ts
// backend/src/jobsRoutes.ts
import { Router, Response } from 'express';
import { authenticateToken, AuthenticatedRequest } from './auth';
import { requireConsoleTier } from './middleware/requireConsoleTier';
import { requireJobOwner, JobOwnerRequest } from './middleware/requireJobOwner';
import * as jobsService from './jobsService';

export const jobsRouter = Router();

// All jobs routes require auth + console tier
jobsRouter.use(authenticateToken, requireConsoleTier);

// ════════════════════════════════════════════════════════════════════
// GET /api/jobs — list jobs for the calling foreman
// ════════════════════════════════════════════════════════════════════

jobsRouter.get('/', async (req: AuthenticatedRequest, res: Response) => {
  try {
    const jobs = await jobsService.listByForeman(req.user!.id);
    res.json({ jobs });
  } catch (e: any) {
    console.error('[Jobs] list error:', e.message);
    res.status(500).json({ error: 'Failed to list jobs' });
  }
});

// ════════════════════════════════════════════════════════════════════
// GET /api/jobs/:id — single job + assigned crew
// ════════════════════════════════════════════════════════════════════

jobsRouter.get('/:id', requireJobOwner, async (req: JobOwnerRequest, res: Response) => {
  try {
    const crew = await jobsService.listCrew(req.job!.id);
    res.json({ job: req.job, crew });
  } catch (e: any) {
    console.error('[Jobs] getOne error:', e.message);
    res.status(500).json({ error: 'Failed to load job' });
  }
});

console.log('[Jobs] routes initialized');
```

- [ ] **Step 2: Mount the router in `server.ts`**

Open `backend/src/server.ts`. Find the section where other routers are mounted (look for `app.use('/api/auth', ...)`). Add the import near the top with other router imports:

```ts
import { jobsRouter } from './jobsRoutes';
```

Add the mount right after the existing `app.use('/api', apiRouter)` line:

```ts
app.use('/api/jobs', authLimiter, jobsRouter);
```

Wait — there's no `authLimiter` for jobs; use `apiLimiter` (the general API rate limiter). Look at how existing routers are wrapped; mirror that. The line you want is approximately:

```ts
app.use('/api/jobs', jobsRouter);
```

(Rate limiting is already applied at the `/api` level via `app.use('/api', apiLimiter)` so the inner mount inherits it.)

- [ ] **Step 3: Verify tsc clean and full test suite at baseline**

```bash
cd /Users/fegensprenelon/smith-net/backend && npx tsc --noEmit 2>&1 | grep -E "jobsRoutes|server\.ts" || echo "no errors"
JWT_SECRET=test-secret-at-least-32-chars-long-please-thanks npm test 2>&1 | tail -5
```

Expected: "no errors" + test summary showing same baseline (4 pre-existing failures unchanged) + status machine + middleware tests still passing.

- [ ] **Step 4: Commit**

```bash
git -C /Users/fegensprenelon/smith-net add backend/src/jobsRoutes.ts backend/src/server.ts
git -C /Users/fegensprenelon/smith-net commit -m "feat(jobs): jobsRouter mounted at /api/jobs with GET / and GET /:id"
```

---

## Task 9: Integration test scaffolding (`jobs-routes.test.ts`)

This task creates the integration test file with a small helper that detects whether a usable test DB exists. If `DATABASE_URL` is unset, tests in this file skip cleanly.

**Files:**
- Create: `backend/src/__tests__/jobs-routes.test.ts`

- [ ] **Step 1: Create the file with one smoke test**

```ts
import express from 'express';
import request from 'supertest';
import cookieParser from 'cookie-parser';
import { authRouter } from '../authRoutes';
import { jobsRouter } from '../jobsRoutes';
import { userStore, generateTokens, UserRole } from '../auth';
import { pg, isPgEnabled } from '../db';

// Skip the entire suite when no Postgres test DB is configured.
// To run these tests, set DATABASE_URL pointing at a dev/test Postgres
// with migration 003_jobs_expansion.sql applied.
const describeDb = isPgEnabled() ? describe : describe.skip;

function buildApp() {
  const app = express();
  app.use(express.json());
  app.use(cookieParser());
  app.use('/api/auth', authRouter);
  app.use('/api/jobs', jobsRouter);
  return app;
}

// Each test creates its own foreman with a unique email so the in-memory
// userStore doesn't collide. Returns the access token.
async function createForemanAndLogin(suffix: string): Promise<{ id: string; token: string }> {
  const user = await userStore.createUser(
    `foreman-jobs-${suffix}-${Date.now()}@example.com`,
    'password123',
    `Foreman ${suffix}`,
    UserRole.FOREMAN
  );
  const { accessToken } = generateTokens(user);
  return { id: user.id, token: accessToken };
}

// Truncate the test data this suite produces so reruns are clean.
afterEach(async () => {
  if (!isPgEnabled() || !pg) return;
  await pg.query(`DELETE FROM job_crew`);
  await pg.query(`DELETE FROM jobs WHERE foreman_id LIKE 'foreman-jobs-%' OR foreman_id IN (SELECT id FROM profiles WHERE email LIKE 'foreman-jobs-%')`);
});

describeDb('GET /api/jobs', () => {
  const app = buildApp();

  it('returns empty list for a new foreman', async () => {
    const f = await createForemanAndLogin('list-empty');
    const res = await request(app)
      .get('/api/jobs')
      .set('Authorization', `Bearer ${f.token}`);
    expect(res.status).toBe(200);
    expect(res.body.jobs).toEqual([]);
  });

  it('returns 401 with no auth', async () => {
    const res = await request(app).get('/api/jobs');
    expect(res.status).toBe(401);
  });

  it('returns 403 tier_required for Solo user', async () => {
    const user = await userStore.createUser(
      `solo-jobs-${Date.now()}@example.com`,
      'password123',
      'Solo',
      UserRole.SOLO
    );
    const { accessToken } = generateTokens(user);
    const res = await request(app)
      .get('/api/jobs')
      .set('Authorization', `Bearer ${accessToken}`);
    expect(res.status).toBe(403);
    expect(res.body.code).toBe('tier_required');
  });
});

describeDb('GET /api/jobs/:id', () => {
  const app = buildApp();

  it('returns 404 when job does not exist', async () => {
    const f = await createForemanAndLogin('getone-404');
    const res = await request(app)
      .get('/api/jobs/00000000-0000-0000-0000-000000000000')
      .set('Authorization', `Bearer ${f.token}`);
    expect(res.status).toBe(404);
  });
});
```

- [ ] **Step 2: Run the test**

If `DATABASE_URL` is set + migration applied:

```bash
cd /Users/fegensprenelon/smith-net/backend && JWT_SECRET=test-secret-at-least-32-chars-long-please-thanks npm test -- jobs-routes 2>&1 | tail -10
```

Expected: 4 tests PASS.

If `DATABASE_URL` is unset:

Expected: tests skipped (suite reports as PASS with 0 tests). Verify by reading the output line `Tests: 0 passed` or similar. Report as `DONE_WITH_CONCERNS` noting the user needs `DATABASE_URL` set to actually exercise these tests.

- [ ] **Step 3: Confirm no regressions in other suites**

```bash
cd /Users/fegensprenelon/smith-net/backend && JWT_SECRET=test-secret-at-least-32-chars-long-please-thanks npm test 2>&1 | tail -5
```

Expected: baseline 4 pre-existing failures unchanged.

- [ ] **Step 4: Commit**

```bash
git -C /Users/fegensprenelon/smith-net add backend/src/__tests__/jobs-routes.test.ts
git -C /Users/fegensprenelon/smith-net commit -m "test(jobs): integration scaffolding for /api/jobs read routes"
```

---

## Task 10: `jobsService.create()` + POST `/api/jobs` (TDD)

**Files:**
- Modify: `backend/src/jobsService.ts` (append `create` function)
- Modify: `backend/src/jobsRoutes.ts` (add POST handler)
- Modify: `backend/src/__tests__/jobs-routes.test.ts` (append POST tests)

- [ ] **Step 1: Append failing tests for POST**

Append to `backend/src/__tests__/jobs-routes.test.ts`:

```ts
describeDb('POST /api/jobs', () => {
  const app = buildApp();

  it('creates a job with status="planned" and foreman_id from req.user', async () => {
    const f = await createForemanAndLogin('create');
    const res = await request(app)
      .post('/api/jobs')
      .set('Authorization', `Bearer ${f.token}`)
      .send({ title: 'Install panel', location: '123 Main St' });
    expect(res.status).toBe(201);
    expect(res.body.job.title).toBe('Install panel');
    expect(res.body.job.location).toBe('123 Main St');
    expect(res.body.job.status).toBe('planned');
    expect(res.body.job.foremanId).toBe(f.id);
    expect(res.body.job.id).toBeDefined();
  });

  it('rejects empty title with 400', async () => {
    const f = await createForemanAndLogin('create-bad');
    const res = await request(app)
      .post('/api/jobs')
      .set('Authorization', `Bearer ${f.token}`)
      .send({ title: '' });
    expect(res.status).toBe(400);
  });

  it('rejects extra fields (strict schema)', async () => {
    const f = await createForemanAndLogin('create-strict');
    const res = await request(app)
      .post('/api/jobs')
      .set('Authorization', `Bearer ${f.token}`)
      .send({ title: 'x', foremanId: 'spoofed', status: 'complete' });
    expect(res.status).toBe(400);
  });
});
```

- [ ] **Step 2: Run — confirm FAIL**

```bash
cd /Users/fegensprenelon/smith-net/backend && JWT_SECRET=test-secret-at-least-32-chars-long-please-thanks npm test -- jobs-routes 2>&1 | tail -15
```

Expected: 3 new tests FAIL (404 or 500 — route doesn't exist yet).

- [ ] **Step 3: Add `create()` to `jobsService.ts`**

Append at the bottom of `backend/src/jobsService.ts`:

```ts
import { auditLog, AuditAction } from './auditLog';
import { v4 as uuidv4 } from 'uuid';

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

  auditLog.log(AuditAction.JOB_CREATED, input.foremanId, {
    jobId: job.id,
    title: job.title,
    status: job.status,
    scheduledAt: job.scheduledAt,
    location: job.location,
    clientId: job.clientId,
    engagementId: job.engagementId,
  });

  return job;
}
```

The `import { auditLog, AuditAction } from './auditLog';` import goes at the top with the other imports (currently just `import { pg, isPgEnabled } from './db';`). The `uuid` import too.

- [ ] **Step 4: Add POST handler to `jobsRoutes.ts`**

Add to the imports at the top:

```ts
import { validateBody } from './middleware/validate';
import { CreateJobBody } from './schemas/jobs';
```

Add the route after the GET `/:id` route:

```ts
// ════════════════════════════════════════════════════════════════════
// POST /api/jobs — create
// ════════════════════════════════════════════════════════════════════

jobsRouter.post('/', validateBody(CreateJobBody), async (req: AuthenticatedRequest, res: Response) => {
  try {
    const body = req.body as CreateJobBody;
    const job = await jobsService.create({
      foremanId: req.user!.id,
      title: body.title,
      description: body.description,
      scheduledAt: body.scheduledAt ? new Date(body.scheduledAt) : undefined,
      location: body.location,
      clientId: body.clientId,
      engagementId: body.engagementId,
    });
    res.status(201).json({ job });
  } catch (e: any) {
    console.error('[Jobs] create error:', e.message);
    res.status(500).json({ error: 'Failed to create job' });
  }
});
```

- [ ] **Step 5: Run — confirm 3 new tests PASS**

```bash
cd /Users/fegensprenelon/smith-net/backend && JWT_SECRET=test-secret-at-least-32-chars-long-please-thanks npm test -- jobs-routes 2>&1 | tail -10
```

Expected: 7 total in jobs-routes.test.ts PASS (4 from Task 9 + 3 new).

- [ ] **Step 6: Commit**

```bash
git -C /Users/fegensprenelon/smith-net add backend/src/jobsService.ts backend/src/jobsRoutes.ts backend/src/__tests__/jobs-routes.test.ts
git -C /Users/fegensprenelon/smith-net commit -m "feat(jobs): POST /api/jobs creates job + emits JOB_CREATED audit"
```

---

## Task 11: `jobsService.update()` + PATCH `/api/jobs/:id` (TDD)

**Files:**
- Modify: `backend/src/jobsService.ts`
- Modify: `backend/src/jobsRoutes.ts`
- Modify: `backend/src/__tests__/jobs-routes.test.ts`

- [ ] **Step 1: Append failing tests**

Append to `jobs-routes.test.ts`:

```ts
describeDb('PATCH /api/jobs/:id', () => {
  const app = buildApp();

  it('updates title + location, leaves other fields untouched', async () => {
    const f = await createForemanAndLogin('update');
    const created = await request(app)
      .post('/api/jobs')
      .set('Authorization', `Bearer ${f.token}`)
      .send({ title: 'orig', location: 'A' });
    const jobId = created.body.job.id;

    const res = await request(app)
      .patch(`/api/jobs/${jobId}`)
      .set('Authorization', `Bearer ${f.token}`)
      .send({ title: 'updated', location: 'B' });

    expect(res.status).toBe(200);
    expect(res.body.job.title).toBe('updated');
    expect(res.body.job.location).toBe('B');
    expect(res.body.job.status).toBe('planned');  // untouched
  });

  it('returns 403 not_owner when another foreman patches', async () => {
    const a = await createForemanAndLogin('update-a');
    const b = await createForemanAndLogin('update-b');
    const created = await request(app)
      .post('/api/jobs')
      .set('Authorization', `Bearer ${a.token}`)
      .send({ title: 'a-job' });
    const jobId = created.body.job.id;

    const res = await request(app)
      .patch(`/api/jobs/${jobId}`)
      .set('Authorization', `Bearer ${b.token}`)
      .send({ title: 'hijacked' });

    expect(res.status).toBe(403);
    expect(res.body.code).toBe('not_owner');
  });

  it('rejects `status` in patch body (status has its own endpoint)', async () => {
    const f = await createForemanAndLogin('update-no-status');
    const created = await request(app)
      .post('/api/jobs')
      .set('Authorization', `Bearer ${f.token}`)
      .send({ title: 'x' });
    const jobId = created.body.job.id;

    const res = await request(app)
      .patch(`/api/jobs/${jobId}`)
      .set('Authorization', `Bearer ${f.token}`)
      .send({ status: 'complete' });

    expect(res.status).toBe(400);
  });
});
```

- [ ] **Step 2: Run — confirm 3 new tests FAIL**

```bash
cd /Users/fegensprenelon/smith-net/backend && JWT_SECRET=test-secret-at-least-32-chars-long-please-thanks npm test -- jobs-routes 2>&1 | tail -15
```

Expected: 3 FAIL (route doesn't exist).

- [ ] **Step 3: Add `update()` to `jobsService.ts`**

Append at the bottom (after `create`):

```ts
export type UpdatePatch = Partial<Pick<Job, 'title' | 'description' | 'scheduledAt' | 'location'>>;

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

  auditLog.log(AuditAction.JOB_UPDATED, job.foremanId, {
    jobId: job.id,
    changedFields,
    after: { title: job.title, description: job.description, scheduledAt: job.scheduledAt, location: job.location },
  });

  return job;
}
```

- [ ] **Step 4: Add PATCH handler to `jobsRoutes.ts`**

Add to imports:

```ts
import { UpdateJobBody } from './schemas/jobs';
```

Add the route after POST `/`:

```ts
// ════════════════════════════════════════════════════════════════════
// PATCH /api/jobs/:id — partial update (NOT status — see /:id/status)
// ════════════════════════════════════════════════════════════════════

jobsRouter.patch('/:id', requireJobOwner, validateBody(UpdateJobBody), async (req: JobOwnerRequest, res: Response) => {
  try {
    const body = req.body as UpdateJobBody;
    const job = await jobsService.update(req.job!.id, {
      title: body.title,
      description: body.description === null ? null as any : body.description,
      scheduledAt: body.scheduledAt === null ? null as any : (body.scheduledAt ? new Date(body.scheduledAt) : undefined),
      location: body.location === null ? null as any : body.location,
    });
    res.json({ job });
  } catch (e: any) {
    if (e instanceof jobsService.NotFoundError) {
      return res.status(404).json({ error: 'Job not found' });
    }
    console.error('[Jobs] update error:', e.message);
    res.status(500).json({ error: 'Failed to update job' });
  }
});
```

- [ ] **Step 5: Run — confirm 3 new tests PASS**

```bash
cd /Users/fegensprenelon/smith-net/backend && JWT_SECRET=test-secret-at-least-32-chars-long-please-thanks npm test -- jobs-routes 2>&1 | tail -10
```

Expected: 10 total in jobs-routes.test.ts PASS.

- [ ] **Step 6: Commit**

```bash
git -C /Users/fegensprenelon/smith-net add backend/src/jobsService.ts backend/src/jobsRoutes.ts backend/src/__tests__/jobs-routes.test.ts
git -C /Users/fegensprenelon/smith-net commit -m "feat(jobs): PATCH /api/jobs/:id partial update + JOB_UPDATED audit"
```

---

## Task 12: `jobsService.changeStatus()` + PATCH `/:id/status` (TDD)

**Files:**
- Modify: `backend/src/jobsService.ts`
- Modify: `backend/src/jobsRoutes.ts`
- Modify: `backend/src/__tests__/jobs-routes.test.ts`

- [ ] **Step 1: Append failing tests**

```ts
describeDb('PATCH /api/jobs/:id/status', () => {
  const app = buildApp();

  it('allows planned -> in_progress', async () => {
    const f = await createForemanAndLogin('status-start');
    const created = await request(app)
      .post('/api/jobs')
      .set('Authorization', `Bearer ${f.token}`)
      .send({ title: 'x' });
    const jobId = created.body.job.id;

    const res = await request(app)
      .patch(`/api/jobs/${jobId}/status`)
      .set('Authorization', `Bearer ${f.token}`)
      .send({ status: 'in_progress' });

    expect(res.status).toBe(200);
    expect(res.body.job.status).toBe('in_progress');
  });

  it('rejects complete -> planned with invalid_status_transition', async () => {
    const f = await createForemanAndLogin('status-bad');
    const created = await request(app)
      .post('/api/jobs')
      .set('Authorization', `Bearer ${f.token}`)
      .send({ title: 'x' });
    const jobId = created.body.job.id;
    // walk to complete first
    await request(app).patch(`/api/jobs/${jobId}/status`).set('Authorization', `Bearer ${f.token}`).send({ status: 'in_progress' });
    await request(app).patch(`/api/jobs/${jobId}/status`).set('Authorization', `Bearer ${f.token}`).send({ status: 'complete' });

    const res = await request(app)
      .patch(`/api/jobs/${jobId}/status`)
      .set('Authorization', `Bearer ${f.token}`)
      .send({ status: 'planned' });

    expect(res.status).toBe(400);
    expect(res.body.code).toBe('invalid_status_transition');
    expect(res.body.from).toBe('complete');
    expect(res.body.to).toBe('planned');
  });

  it('rejects unknown status value with zod 400', async () => {
    const f = await createForemanAndLogin('status-unknown');
    const created = await request(app)
      .post('/api/jobs')
      .set('Authorization', `Bearer ${f.token}`)
      .send({ title: 'x' });

    const res = await request(app)
      .patch(`/api/jobs/${created.body.job.id}/status`)
      .set('Authorization', `Bearer ${f.token}`)
      .send({ status: 'in-orbit' });

    expect(res.status).toBe(400);
    // zod error envelope from F1.5 validateBody middleware
    expect(res.body.code).toBe('validation');
  });
});
```

- [ ] **Step 2: Run — confirm 3 new tests FAIL**

```bash
cd /Users/fegensprenelon/smith-net/backend && JWT_SECRET=test-secret-at-least-32-chars-long-please-thanks npm test -- jobs-routes 2>&1 | tail -15
```

Expected: 3 FAIL.

- [ ] **Step 3: Add `changeStatus()` to `jobsService.ts`**

Append:

```ts
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

  auditLog.log(AuditAction.JOB_STATUS_CHANGED, job.foremanId, {
    jobId: job.id,
    from: existing.status,
    to: newStatus,
  });

  return job;
}
```

- [ ] **Step 4: Add PATCH `/:id/status` handler**

Imports — add:

```ts
import { StatusChangeBody } from './schemas/jobs';
```

Route — add after PATCH `/:id`:

```ts
// ════════════════════════════════════════════════════════════════════
// PATCH /api/jobs/:id/status — status transitions
// ════════════════════════════════════════════════════════════════════

jobsRouter.patch('/:id/status', requireJobOwner, validateBody(StatusChangeBody), async (req: JobOwnerRequest, res: Response) => {
  try {
    const body = req.body as StatusChangeBody;
    const job = await jobsService.changeStatus(req.job!.id, body.status);
    res.json({ job });
  } catch (e: any) {
    if (e instanceof jobsService.InvalidTransitionError) {
      return res.status(400).json({
        error: e.message,
        code: 'invalid_status_transition',
        from: e.from,
        to: e.to,
      });
    }
    if (e instanceof jobsService.NotFoundError) {
      return res.status(404).json({ error: 'Job not found' });
    }
    console.error('[Jobs] status error:', e.message);
    res.status(500).json({ error: 'Failed to change status' });
  }
});
```

- [ ] **Step 5: Run — confirm 3 new tests PASS**

```bash
cd /Users/fegensprenelon/smith-net/backend && JWT_SECRET=test-secret-at-least-32-chars-long-please-thanks npm test -- jobs-routes 2>&1 | tail -10
```

Expected: 13 total in jobs-routes.test.ts PASS.

- [ ] **Step 6: Commit**

```bash
git -C /Users/fegensprenelon/smith-net add backend/src/jobsService.ts backend/src/jobsRoutes.ts backend/src/__tests__/jobs-routes.test.ts
git -C /Users/fegensprenelon/smith-net commit -m "feat(jobs): PATCH /api/jobs/:id/status with state machine + audit"
```

---

## Task 13: `assignCrew` + `unassignCrew` + routes (TDD)

**Files:**
- Modify: `backend/src/jobsService.ts`
- Modify: `backend/src/jobsRoutes.ts`
- Modify: `backend/src/__tests__/jobs-routes.test.ts`

- [ ] **Step 1: Append failing tests**

```ts
describeDb('POST /api/jobs/:id/assign + DELETE /api/jobs/:id/assign/:profileId', () => {
  const app = buildApp();

  it('assigns crew member then lists them on GET', async () => {
    const f = await createForemanAndLogin('assign-1');
    const crew = await userStore.createUser(`crew-${Date.now()}@example.com`, 'password123', 'C', UserRole.TEAM_MEMBER);
    const created = await request(app).post('/api/jobs').set('Authorization', `Bearer ${f.token}`).send({ title: 'x' });
    const jobId = created.body.job.id;

    const assign = await request(app)
      .post(`/api/jobs/${jobId}/assign`)
      .set('Authorization', `Bearer ${f.token}`)
      .send({ profileId: crew.id, roleOnJob: 'lead' });
    expect(assign.status).toBe(201);
    expect(assign.body.assignment.profileId).toBe(crew.id);
    expect(assign.body.assignment.roleOnJob).toBe('lead');

    const fetched = await request(app).get(`/api/jobs/${jobId}`).set('Authorization', `Bearer ${f.token}`);
    expect(fetched.status).toBe(200);
    expect(fetched.body.crew).toHaveLength(1);
    expect(fetched.body.crew[0].profileId).toBe(crew.id);
  });

  it('rejects duplicate assignment with 409 duplicate_assignment', async () => {
    const f = await createForemanAndLogin('assign-dup');
    const crew = await userStore.createUser(`crew-dup-${Date.now()}@example.com`, 'password123', 'C', UserRole.TEAM_MEMBER);
    const created = await request(app).post('/api/jobs').set('Authorization', `Bearer ${f.token}`).send({ title: 'x' });
    const jobId = created.body.job.id;

    await request(app).post(`/api/jobs/${jobId}/assign`).set('Authorization', `Bearer ${f.token}`).send({ profileId: crew.id });
    const dup = await request(app).post(`/api/jobs/${jobId}/assign`).set('Authorization', `Bearer ${f.token}`).send({ profileId: crew.id });

    expect(dup.status).toBe(409);
    expect(dup.body.code).toBe('duplicate_assignment');
  });

  it('unassigns with 204', async () => {
    const f = await createForemanAndLogin('unassign');
    const crew = await userStore.createUser(`crew-unassign-${Date.now()}@example.com`, 'password123', 'C', UserRole.TEAM_MEMBER);
    const created = await request(app).post('/api/jobs').set('Authorization', `Bearer ${f.token}`).send({ title: 'x' });
    const jobId = created.body.job.id;

    await request(app).post(`/api/jobs/${jobId}/assign`).set('Authorization', `Bearer ${f.token}`).send({ profileId: crew.id });

    const del = await request(app)
      .delete(`/api/jobs/${jobId}/assign/${crew.id}`)
      .set('Authorization', `Bearer ${f.token}`);
    expect(del.status).toBe(204);

    const fetched = await request(app).get(`/api/jobs/${jobId}`).set('Authorization', `Bearer ${f.token}`);
    expect(fetched.body.crew).toHaveLength(0);
  });
});
```

- [ ] **Step 2: Run — confirm 3 new tests FAIL**

```bash
cd /Users/fegensprenelon/smith-net/backend && JWT_SECRET=test-secret-at-least-32-chars-long-please-thanks npm test -- jobs-routes 2>&1 | tail -15
```

Expected: 3 FAIL.

- [ ] **Step 3: Add service functions to `jobsService.ts`**

Append:

```ts
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

    auditLog.log(AuditAction.JOB_CREW_ASSIGNED, job.foremanId, {
      jobId,
      profileId,
      roleOnJob,
    });

    return assignment;
  } catch (e: any) {
    // pg unique_violation = '23505'
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

  auditLog.log(AuditAction.JOB_CREW_UNASSIGNED, job.foremanId, {
    jobId,
    profileId,
  });
}
```

- [ ] **Step 4: Add routes to `jobsRoutes.ts`**

Imports — add:

```ts
import { AssignCrewBody } from './schemas/jobs';
```

Routes — add at the end of the file (before the `console.log` initialization line):

```ts
// ════════════════════════════════════════════════════════════════════
// POST /api/jobs/:id/assign — add crew member
// ════════════════════════════════════════════════════════════════════

jobsRouter.post('/:id/assign', requireJobOwner, validateBody(AssignCrewBody), async (req: JobOwnerRequest, res: Response) => {
  try {
    const body = req.body as AssignCrewBody;
    const assignment = await jobsService.assignCrew(req.job!.id, body.profileId, body.roleOnJob);
    res.status(201).json({ assignment });
  } catch (e: any) {
    if (e.code === 'duplicate_assignment') {
      return res.status(409).json({ error: e.message, code: 'duplicate_assignment' });
    }
    if (e instanceof jobsService.NotFoundError) {
      return res.status(404).json({ error: 'Job not found' });
    }
    // pg FK violation = '23503' — bad profileId
    if (e.code === '23503') {
      return res.status(400).json({ error: 'Unknown profile', code: 'unknown_profile' });
    }
    console.error('[Jobs] assign error:', e.message);
    res.status(500).json({ error: 'Failed to assign crew' });
  }
});

// ════════════════════════════════════════════════════════════════════
// DELETE /api/jobs/:id/assign/:profileId — remove crew member
// ════════════════════════════════════════════════════════════════════

jobsRouter.delete('/:id/assign/:profileId', requireJobOwner, async (req: JobOwnerRequest, res: Response) => {
  try {
    await jobsService.unassignCrew(req.job!.id, req.params.profileId);
    res.status(204).send();
  } catch (e: any) {
    if (e instanceof jobsService.NotFoundError) {
      return res.status(404).json({ error: e.message });
    }
    console.error('[Jobs] unassign error:', e.message);
    res.status(500).json({ error: 'Failed to unassign crew' });
  }
});
```

- [ ] **Step 5: Run — confirm 3 new tests PASS**

```bash
cd /Users/fegensprenelon/smith-net/backend && JWT_SECRET=test-secret-at-least-32-chars-long-please-thanks npm test -- jobs-routes 2>&1 | tail -10
```

Expected: 16 total in jobs-routes.test.ts PASS.

- [ ] **Step 6: Commit**

```bash
git -C /Users/fegensprenelon/smith-net add backend/src/jobsService.ts backend/src/jobsRoutes.ts backend/src/__tests__/jobs-routes.test.ts
git -C /Users/fegensprenelon/smith-net commit -m "feat(jobs): POST/DELETE assign endpoints + JOB_CREW_* audit"
```

---

## Task 14: Manual verification

**No code changes.** End-to-end smoke check before declaring Plan 2 done.

- [ ] **Step 1: Confirm full backend test suite at expected baseline**

```bash
cd /Users/fegensprenelon/smith-net/backend && JWT_SECRET=test-secret-at-least-32-chars-long-please-thanks npm test 2>&1 | tail -10
```

Expected: pre-existing 4 failures unchanged. New tests (status-machine + middleware + routes) all passing. Total roughly: 14 status-machine + 9 middleware + 16 routes + 33 from Plan 1 = 72 pass, 4 fail (if `DATABASE_URL` set). If `DATABASE_URL` unset, jobs-routes 16 tests skip — adjust expectation.

- [ ] **Step 2: Boot backend + curl walkthrough (requires `DATABASE_URL` set + migration applied)**

Start backend:

```bash
cd /Users/fegensprenelon/smith-net/backend && JWT_SECRET=verification-secret-at-least-32-chars-long-please-thanks npm run dev &
sleep 3
```

Login + capture cookies:

```bash
curl -sS -c /tmp/p2.cookies -X POST http://localhost:3030/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@smithnet.local","password":"admin123"}' >/dev/null
```

Create job:

```bash
curl -sS -b /tmp/p2.cookies -X POST http://localhost:3030/api/jobs \
  -H "Content-Type: application/json" \
  -d '{"title":"Plan 2 smoke","location":"123 Test St"}' | tee /tmp/p2.job.json
```

Save the job id from the response, e.g. `JID=$(jq -r .job.id < /tmp/p2.job.json)`. Then:

```bash
# List
curl -sS -b /tmp/p2.cookies http://localhost:3030/api/jobs

# Get
curl -sS -b /tmp/p2.cookies http://localhost:3030/api/jobs/$JID

# Valid status change
curl -sS -b /tmp/p2.cookies -X PATCH http://localhost:3030/api/jobs/$JID/status \
  -H "Content-Type: application/json" -d '{"status":"in_progress"}'

# Invalid status change → expect 400 invalid_status_transition
curl -sS -b /tmp/p2.cookies -X PATCH http://localhost:3030/api/jobs/$JID/status \
  -H "Content-Type: application/json" -d '{"status":"planned"}' | grep invalid_status_transition

# Assign crew (use admin's own id as the crew member — quick sanity)
ADMINID=admin-001
curl -sS -b /tmp/p2.cookies -X POST http://localhost:3030/api/jobs/$JID/assign \
  -H "Content-Type: application/json" -d "{\"profileId\":\"$ADMINID\"}"

# Re-fetch — crew should be present
curl -sS -b /tmp/p2.cookies http://localhost:3030/api/jobs/$JID | jq .crew

# Unassign
curl -sS -i -b /tmp/p2.cookies -X DELETE http://localhost:3030/api/jobs/$JID/assign/$ADMINID | head -1
# expect 204 No Content

# Tier gate — register a Solo user, expect 403 on /api/jobs
SOLO_EMAIL="solo-p2-$(date +%s)@example.com"
curl -sS -c /tmp/p2.solo.cookies -X POST http://localhost:3030/api/auth/register \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$SOLO_EMAIL\",\"password\":\"password123\",\"displayName\":\"Solo\"}" >/dev/null

curl -sS -b /tmp/p2.solo.cookies http://localhost:3030/api/jobs | grep tier_required
```

Stop backend (`kill %1` or via `TaskStop` if dispatched via Bash run_in_background).

- [ ] **Step 3: Confirm clean working tree**

```bash
git -C /Users/fegensprenelon/smith-net status --short backend/
```

Expected: no uncommitted backend files from this plan. (Pre-existing untracked files outside `backend/` are fine.)

- [ ] **Step 4: Print commit summary**

```bash
git -C /Users/fegensprenelon/smith-net log --oneline afe3a36..HEAD
```

Should show ~13 commits from this plan plus the spec commit (`afe3a36`).

---

## Self-Review Notes

**Spec coverage:**
- Schema migration — Task 1
- AuditAction extension — Task 2
- Zod schemas — Task 3
- requireConsoleTier — Task 4
- jobsService types + state machine — Task 5
- jobsService read ops — Task 6
- requireJobOwner — Task 7
- jobsRouter + read routes + server.ts mount — Task 8
- Integration test scaffolding — Task 9
- POST + JOB_CREATED — Task 10
- PATCH + JOB_UPDATED — Task 11
- PATCH /:id/status + JOB_STATUS_CHANGED — Task 12
- assign/unassign + JOB_CREW_* — Task 13
- Manual verification — Task 14

Every spec section maps to a task. No gaps.

**Placeholder scan:** None. Every step has actual code or actual commands.

**Type consistency:**
- `JobStatus = 'planned' | 'in_progress' | 'complete' | 'cancelled'` — consistent in service, schema, tests
- `roleOnJob = 'crew' | 'lead'` — consistent
- `Job` interface fields (camelCase) match SQL columns (snake_case) via the mapping in `mapJobRow`
- `NotFoundError` and `InvalidTransitionError` used consistently
- `auditLog.log(AuditAction.JOB_*, foremanId, details)` shape consistent across all 5 call sites
- Middleware request types (`AuthenticatedRequest`, `JobOwnerRequest`) consistent

**Pre-existing concerns (out of scope):**
- 4 pre-existing failing tests in `api-auth-integration.test.ts` / `auth-middleware.test.ts` — Plan 2 must not make them worse, but is NOT responsible for fixing.
- `intentService` deeper integration (sealing JobIntents via Ledger) is genuinely out of scope — could be a future plan once the relationship between operational state and scope-sealing is fleshed out.

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-05-11-plan-2-jobs-backend.md`. Two execution options:**

1. **Subagent-Driven (recommended)** — fresh subagent per task, review between tasks
2. **Inline Execution** — execute tasks in this session with checkpoints

**Which approach?**
