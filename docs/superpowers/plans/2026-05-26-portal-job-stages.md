# Portal Job Stages — Slice 2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a server-authoritative `jobs.stage` pipeline (7 stages, forward + 3 reverses) with a `JobStageBar` + context-aware action buttons on the portal job detail.

**Architecture:** Mirrors the APK's `JobStage` enum (`lead/proposal/approved/in_progress/review/invoice/closed`). Backend exposes `PATCH /api/jobs/:id/stage` mirroring the existing `/:id/status` route. `stage` is additive — the existing `jobs.status` field is untouched. Portal renders a monospace dots-and-lines bar and per-stage action buttons that call `jobsClient.changeStage(...)` + `useJobsStore.upsertJob(...)`. Audit log captures every transition.

**Tech Stack:** Node/Express + pg + zod + Jest (backend), Vite 5 + React 18 + TS strict + zustand + Tailwind 3 + Vitest + jsdom + MSW (portal).

**Spec:** `docs/superpowers/specs/2026-05-26-portal-job-stages-design.md`

---

## File structure

**Create:**
- `backend/migrations/031_jobs_stage.sql`
- `backend/src/__tests__/jobs-stage-routes.test.ts`
- `desktop/portal/src/console/components/jobs/JobStageBar.tsx`
- `desktop/portal/src/console/components/jobs/JobStageControls.tsx`
- `desktop/portal/src/console/components/jobs/__tests__/JobStageBar.test.tsx`
- `desktop/portal/src/console/components/jobs/__tests__/JobStageControls.test.tsx`

**Modify:**
- `backend/src/jobsService.ts` — `JobStage` type, `VALID_STAGE_TRANSITIONS`, `assertValidStageTransition`, `InvalidStageTransitionError`, `changeStage`, `Job.stage`, `mapJobRow` reads `row.stage`, `create()` ignores stage (defaults `lead`)
- `backend/src/jobsRoutes.ts` — `PATCH /:id/stage` route
- `backend/src/schemas/jobs.ts` — `StageChangeBody` zod
- `backend/src/auditLog.ts` — add `JOB_STAGE_CHANGED` enum
- `desktop/portal/src/console/api/jobsClient.ts` — `JobStage` type, `Job.stage`, `changeStage(...)`
- `desktop/portal/src/console/test/msw-handlers.ts` — `PATCH /api/jobs/:id/stage` handler
- `desktop/portal/src/console/routes/JobDetailRoute.tsx` — mount `JobStageBar` + `JobStageControls`
- `desktop/portal/src/console/routes/__tests__/JobDetailRoute.test.tsx` — assert bar + controls render
- Existing test fixtures that build `Job` literals — add `stage: 'lead'` to keep tsc happy (discovered at task 6)

---

### Task 1: Database migration

**Files:**
- Create: `backend/migrations/031_jobs_stage.sql`

- [ ] **Step 1: Write the migration**

`backend/migrations/031_jobs_stage.sql`:
```sql
-- Add the pipeline stage column to jobs.
-- Stage is additive to status; both fields coexist.

ALTER TABLE jobs ADD COLUMN IF NOT EXISTS stage TEXT NOT NULL DEFAULT 'lead'
  CHECK (stage IN ('lead','proposal','approved','in_progress','review','invoice','closed'));

-- Backfill from existing status (lossy by design — see spec section 9).
UPDATE jobs SET stage = CASE
  WHEN status = 'planned'     THEN 'lead'
  WHEN status = 'in_progress' THEN 'in_progress'
  WHEN status = 'complete'    THEN 'closed'
  WHEN status = 'cancelled'   THEN 'closed'
  ELSE 'lead'
END
WHERE stage = 'lead';

CREATE INDEX IF NOT EXISTS idx_jobs_foreman_stage ON jobs (foreman_id, stage);
```

- [ ] **Step 2: Apply the migration**

Run: `psql postgresql://localhost/smithnet -f backend/migrations/031_jobs_stage.sql`
Expected: `ALTER TABLE`, `UPDATE N`, `CREATE INDEX`. No errors.

- [ ] **Step 3: Verify the column exists with the CHECK constraint**

Run: `psql postgresql://localhost/smithnet -c "\d jobs"`
Expected: a row `stage | text | not null default 'lead'::text` AND a check constraint listing the 7 stages.

- [ ] **Step 4: Commit**

```bash
git add backend/migrations/031_jobs_stage.sql
git commit -m "feat(jobs): migration 031 -- jobs.stage column + CHECK + backfill

Adds 7-stage pipeline (lead/proposal/approved/in_progress/review/
invoice/closed) with default 'lead', heuristic backfill from existing
status, and (foreman_id, stage) index for filtering.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: `jobsService` — JobStage type + state machine

**Files:**
- Modify: `backend/src/jobsService.ts`
- Test: `backend/src/__tests__/jobs-stage-routes.test.ts` (created later — for now we exercise the state machine via the route tests in Task 5)

This task is type-only + pure-function logic; tests come in Task 5 where the route exercises every transition. We verify here via `tsc`.

- [ ] **Step 1: Add `JobStage` type and `InvalidStageTransitionError`**

Insert after the existing `JobStatus` type at `backend/src/jobsService.ts:16`:

```typescript
export type JobStage =
  | 'lead'
  | 'proposal'
  | 'approved'
  | 'in_progress'
  | 'review'
  | 'invoice'
  | 'closed';

export const JOB_STAGES: readonly JobStage[] = [
  'lead', 'proposal', 'approved', 'in_progress', 'review', 'invoice', 'closed',
] as const;
```

Insert after the existing `InvalidTransitionError` class (currently at `jobsService.ts:54-59`):

```typescript
export class InvalidStageTransitionError extends Error {
  constructor(public from: JobStage, public to: JobStage) {
    super(`Invalid stage transition: ${from} -> ${to}`);
    this.name = 'InvalidStageTransitionError';
  }
}
```

- [ ] **Step 2: Add `VALID_STAGE_TRANSITIONS` and `assertValidStageTransition`**

Insert after the existing `VALID_TRANSITIONS` map (currently at `jobsService.ts:65-70`):

```typescript
// Forward spine + 3 targeted reverses (proposal->lead, invoice->review,
// closed->invoice). See spec section 4.
const VALID_STAGE_TRANSITIONS: Record<JobStage, JobStage[]> = {
  lead:        ['proposal'],
  proposal:    ['approved', 'lead'],         // reverse: rejected
  approved:    ['in_progress'],
  in_progress: ['review'],
  review:      ['invoice'],
  invoice:     ['closed', 'review'],         // reverse: invoice wrong
  closed:      ['invoice'],                  // reverse: reopened
};

export function assertValidStageTransition(from: JobStage, to: JobStage): void {
  if (from === to) return; // self-loop is a no-op; caller may short-circuit
  const allowed = VALID_STAGE_TRANSITIONS[from] ?? [];
  if (!allowed.includes(to)) {
    throw new InvalidStageTransitionError(from, to);
  }
}
```

- [ ] **Step 3: Add `stage` to `Job` interface + `mapJobRow`**

Modify the `Job` interface (`jobsService.ts:18-34`) — add `stage: JobStage` after `status`:

```typescript
export interface Job {
  id: string;
  foremanId: string;
  clientId: string | null;
  engagementId: string | null;
  title: string;
  description: string | null;
  status: JobStatus;
  stage: JobStage;
  scheduledAt: Date | null;
  location: string | null;
  latitude: number | null;
  longitude: number | null;
  geocodedAt: Date | null;
  createdAt: Date;
  updatedAt: Date;
  client: { id: string; name: string } | null;
}
```

In `mapJobRow` (`jobsService.ts:87+`), add `stage: row.stage as JobStage` next to `status:`:

```typescript
status: row.status as JobStatus,
stage: row.stage as JobStage,
```

- [ ] **Step 4: Verify the backend type-checks**

Run: `cd backend && npx tsc --noEmit`
Expected: clean — no new errors. (If `Job` is used as a literal anywhere with missing `stage`, fix it; the existing service code reads `mapJobRow` results from pg, so no fixtures break here. Test fixtures live in `__tests__/` and `desktop/portal/`; those get updated in their own tasks.)

- [ ] **Step 5: Commit**

```bash
git add backend/src/jobsService.ts
git commit -m "feat(jobs): JobStage type, transition map, assertValidStageTransition

Adds the 7-stage type, the VALID_STAGE_TRANSITIONS map (forward spine
+ 3 reverses), assertValidStageTransition, InvalidStageTransitionError,
and surfaces stage on the Job interface + mapJobRow. No behavior wired
yet -- consumed by changeStage in the next commit.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: `jobsService.changeStage` + audit enum

**Files:**
- Modify: `backend/src/jobsService.ts`
- Modify: `backend/src/auditLog.ts`

- [ ] **Step 1: Add `JOB_STAGE_CHANGED` to `AuditAction`**

In `backend/src/auditLog.ts`, find the line `JOB_STATUS_CHANGED = 'job.status_changed'` (currently line 51) and add immediately below it:

```typescript
JOB_STAGE_CHANGED = 'job.stage_changed',
```

- [ ] **Step 2: Add `changeStage` to `jobsService`**

Insert immediately after the existing `changeStatus` function (`backend/src/jobsService.ts:268-289`):

```typescript
export async function changeStage(jobId: string, newStage: JobStage): Promise<Job> {
  const db = requirePg();
  const existing = await getById(jobId);
  if (!existing) throw new NotFoundError();

  // Self-loop is a no-op: return the existing job, no audit entry written.
  if (existing.stage === newStage) return existing;

  assertValidStageTransition(existing.stage, newStage);

  const { rows } = await db.query(
    `UPDATE jobs SET stage = $1, updated_at = NOW() WHERE id = $2 RETURNING *`,
    [newStage, jobId]
  );

  const job = mapJobRow(rows[0]);

  await auditLog.log(AuditAction.JOB_STAGE_CHANGED, job.foremanId, {
    jobId: job.id,
    from: existing.stage,
    to: newStage,
  });

  return job;
}
```

- [ ] **Step 3: Type-check**

Run: `cd backend && npx tsc --noEmit`
Expected: clean.

- [ ] **Step 4: Commit**

```bash
git add backend/src/jobsService.ts backend/src/auditLog.ts
git commit -m "feat(jobs): changeStage service + JOB_STAGE_CHANGED audit

Mirrors changeStatus exactly: load, self-loop short-circuit, assert
transition, UPDATE, audit, return. Self-loop returns existing job
without writing an audit entry (idempotent PATCH).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: zod schema + route + backend tests

**Files:**
- Modify: `backend/src/schemas/jobs.ts`
- Modify: `backend/src/jobsRoutes.ts`
- Create: `backend/src/__tests__/jobs-stage-routes.test.ts`

This task is TDD on the route — tests first, then route + schema.

- [ ] **Step 1: Write failing route test file**

Create `backend/src/__tests__/jobs-stage-routes.test.ts`:

```typescript
// backend/src/__tests__/jobs-stage-routes.test.ts
import express from 'express';
import request from 'supertest';
import cookieParser from 'cookie-parser';
import { authRouter } from '../authRoutes';
import { jobsRouter } from '../jobsRoutes';
import { generateTokens, UserRole } from '../auth';
import { createUserAndProfile } from '../jobsService';
import * as jobsService from '../jobsService';
import { pg, isPgEnabled } from '../db';

const describeDb = isPgEnabled() ? describe : describe.skip;

function buildApp() {
  const app = express();
  app.use(express.json());
  app.use(cookieParser());
  app.use('/api/auth', authRouter);
  app.use('/api/jobs', jobsRouter);
  return app;
}

async function foreman(suffix: string): Promise<{ id: string; token: string }> {
  const user = await createUserAndProfile({
    email: `foreman-stage-${suffix}-${Date.now()}@example.com`,
    password: 'password123',
    displayName: `Foreman ${suffix}`,
    role: UserRole.FOREMAN,
  });
  const { accessToken } = await generateTokens(user);
  return { id: user.id, token: accessToken };
}

async function createJobAt(token: string, stage: jobsService.JobStage): Promise<string> {
  const res = await request(buildApp()).post('/api/jobs')
    .set('Authorization', `Bearer ${token}`)
    .send({ title: `job-${stage}` });
  const id = res.body.job.id;
  // Walk the job forward to the target stage using the route under test.
  // For 'lead' (default) nothing to do.
  if (stage === 'lead') return id;
  const path: jobsService.JobStage[] =
    ['lead','proposal','approved','in_progress','review','invoice','closed'];
  const targetIdx = path.indexOf(stage);
  for (let i = 1; i <= targetIdx; i++) {
    await request(buildApp()).patch(`/api/jobs/${id}/stage`)
      .set('Authorization', `Bearer ${token}`)
      .send({ stage: path[i] });
  }
  return id;
}

describeDb('jobs stage routes', () => {
  const app = buildApp();

  afterEach(async () => {
    if (!isPgEnabled() || !pg) return;
    await pg.query(`DELETE FROM jobs WHERE foreman_id IN (SELECT id FROM profiles WHERE email LIKE 'foreman-stage-%')`);
    await pg.query(`DELETE FROM profiles WHERE email LIKE 'foreman-stage-%'`);
    await pg.query(`DELETE FROM users WHERE email LIKE 'foreman-stage-%'`);
  });
  afterAll(async () => { await pg?.end(); });

  it('401 without auth', async () => {
    const res = await request(app).patch('/api/jobs/00000000-0000-0000-0000-000000000000/stage')
      .send({ stage: 'proposal' });
    expect(res.status).toBe(401);
  });

  it('404 cross-foreman', async () => {
    const a = await foreman('iso-a');
    const b = await foreman('iso-b');
    const id = await createJobAt(a.token, 'lead');
    const res = await request(app).patch(`/api/jobs/${id}/stage`)
      .set('Authorization', `Bearer ${b.token}`).send({ stage: 'proposal' });
    expect(res.status).toBe(404);
  });

  it('rejects unknown stage value (zod strict)', async () => {
    const f = await foreman('zod');
    const id = await createJobAt(f.token, 'lead');
    const res = await request(app).patch(`/api/jobs/${id}/stage`)
      .set('Authorization', `Bearer ${f.token}`).send({ stage: 'bogus' });
    expect(res.status).toBe(400);
    expect(res.body.code).toBe('validation');
  });

  it('valid forward transitions (full spine)', async () => {
    const f = await foreman('fwd');
    const id = await createJobAt(f.token, 'lead');
    const path: jobsService.JobStage[] =
      ['proposal','approved','in_progress','review','invoice','closed'];
    for (const stage of path) {
      const res = await request(app).patch(`/api/jobs/${id}/stage`)
        .set('Authorization', `Bearer ${f.token}`).send({ stage });
      expect(res.status).toBe(200);
      expect(res.body.job.stage).toBe(stage);
    }
  });

  it('valid reverse: proposal -> lead', async () => {
    const f = await foreman('rev1');
    const id = await createJobAt(f.token, 'proposal');
    const res = await request(app).patch(`/api/jobs/${id}/stage`)
      .set('Authorization', `Bearer ${f.token}`).send({ stage: 'lead' });
    expect(res.status).toBe(200);
    expect(res.body.job.stage).toBe('lead');
  });

  it('valid reverse: invoice -> review', async () => {
    const f = await foreman('rev2');
    const id = await createJobAt(f.token, 'invoice');
    const res = await request(app).patch(`/api/jobs/${id}/stage`)
      .set('Authorization', `Bearer ${f.token}`).send({ stage: 'review' });
    expect(res.status).toBe(200);
    expect(res.body.job.stage).toBe('review');
  });

  it('valid reverse: closed -> invoice', async () => {
    const f = await foreman('rev3');
    const id = await createJobAt(f.token, 'closed');
    const res = await request(app).patch(`/api/jobs/${id}/stage`)
      .set('Authorization', `Bearer ${f.token}`).send({ stage: 'invoice' });
    expect(res.status).toBe(200);
    expect(res.body.job.stage).toBe('invoice');
  });

  it('refuses invalid: lead -> in_progress', async () => {
    const f = await foreman('inv1');
    const id = await createJobAt(f.token, 'lead');
    const res = await request(app).patch(`/api/jobs/${id}/stage`)
      .set('Authorization', `Bearer ${f.token}`).send({ stage: 'in_progress' });
    expect(res.status).toBe(400);
    expect(res.body.code).toBe('invalid_stage_transition');
    expect(res.body.from).toBe('lead');
    expect(res.body.to).toBe('in_progress');
  });

  it('refuses invalid: closed -> lead', async () => {
    const f = await foreman('inv2');
    const id = await createJobAt(f.token, 'closed');
    const res = await request(app).patch(`/api/jobs/${id}/stage`)
      .set('Authorization', `Bearer ${f.token}`).send({ stage: 'lead' });
    expect(res.status).toBe(400);
    expect(res.body.code).toBe('invalid_stage_transition');
  });

  it('self-loop returns 200 (idempotent)', async () => {
    const f = await foreman('self');
    const id = await createJobAt(f.token, 'lead');
    const res = await request(app).patch(`/api/jobs/${id}/stage`)
      .set('Authorization', `Bearer ${f.token}`).send({ stage: 'lead' });
    expect(res.status).toBe(200);
    expect(res.body.job.stage).toBe('lead');
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd backend && DATABASE_URL=postgresql://localhost/smithnet npx jest src/__tests__/jobs-stage-routes.test.ts`
Expected: ALL tests fail with errors like "Cannot find module" or "404 (route not mounted)" — the route + schema don't exist yet.

- [ ] **Step 3: Add the zod schema**

Append to `backend/src/schemas/jobs.ts`:

```typescript
export const StageChangeBody = z.object({
  stage: z.enum(['lead','proposal','approved','in_progress','review','invoice','closed']),
}).strict();
export type StageChangeBody = z.infer<typeof StageChangeBody>;
```

- [ ] **Step 4: Add the route**

In `backend/src/jobsRoutes.ts`, find the `StatusChangeBody` import — extend it to also import `StageChangeBody`:

```typescript
import { StatusChangeBody, StageChangeBody } from './schemas/jobs';
```

Insert immediately after the `PATCH /:id/status` route block (currently `jobsRoutes.ts:91-114`):

```typescript
// ════════════════════════════════════════════════════════════════════
// PATCH /api/jobs/:id/stage — pipeline stage transitions
// ════════════════════════════════════════════════════════════════════

jobsRouter.patch('/:id/stage', requireJobOwner, validateBody(StageChangeBody), async (req: JobOwnerRequest, res: Response) => {
  try {
    const body = req.body as StageChangeBody;
    const job = await jobsService.changeStage(req.job!.id, body.stage);
    res.json({ job });
  } catch (e: any) {
    if (e instanceof jobsService.InvalidStageTransitionError) {
      return res.status(400).json({
        error: e.message,
        code: 'invalid_stage_transition',
        from: e.from,
        to: e.to,
      });
    }
    if (e instanceof jobsService.NotFoundError) {
      return res.status(404).json({ error: 'Job not found' });
    }
    requestLogger().error({ event: 'jobs_stage_error', err: e }, 'jobs stage error');
    res.status(500).json({ error: 'Failed to change stage' });
  }
});
```

- [ ] **Step 5: Re-run the tests to verify they pass**

Run: `cd backend && DATABASE_URL=postgresql://localhost/smithnet npx jest src/__tests__/jobs-stage-routes.test.ts`
Expected: all 10 tests PASS.

- [ ] **Step 6: Run the full backend suite to confirm no regression**

Run: `cd backend && DATABASE_URL=postgresql://localhost/smithnet npx jest`
Expected: all suites pass. (Foreman-demo user may need recreation if the cleanup script wipes it — that's a known recurring issue, not blocking.)

- [ ] **Step 7: Commit**

```bash
git add backend/src/schemas/jobs.ts backend/src/jobsRoutes.ts backend/src/__tests__/jobs-stage-routes.test.ts
git commit -m "feat(jobs): PATCH /api/jobs/:id/stage route + tests

Mirrors PATCH /:id/status (zod-validated body, requireJobOwner, audit
on transition). 400 invalid_stage_transition on refused transitions,
404 on cross-foreman, 200 + idempotent on self-loop. Covers full
forward spine (6) + all 3 reverses + 2 refused cases + zod + auth.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: Portal jobsClient + Job type + MSW handler + fixture fix

**Files:**
- Modify: `desktop/portal/src/console/api/jobsClient.ts`
- Modify: `desktop/portal/src/console/test/msw-handlers.ts`
- Modify: every test file that builds a `Job` literal (discovered via tsc — likely `routes/__tests__/JobDetailRoute.test.tsx`, `routes/__tests__/JobsListRoute.test.tsx`, `routes/__tests__/ClientDetailRoute.test.tsx`, `components/jobs/__tests__/EditJobModal.test.tsx`, and any others — fix all at once)

- [ ] **Step 1: Extend `jobsClient.ts` with `JobStage`, `Job.stage`, and `changeStage`**

In `desktop/portal/src/console/api/jobsClient.ts`:

Add after the existing `JobStatus` type:

```typescript
export type JobStage =
  | 'lead'
  | 'proposal'
  | 'approved'
  | 'in_progress'
  | 'review'
  | 'invoice'
  | 'closed';
```

Add `stage: JobStage` to the `Job` interface, immediately below `status`:

```typescript
export interface Job {
  id: string;
  // ...existing fields...
  status: JobStatus;
  stage: JobStage;     // <- ADD
  // ...remaining fields...
}
```

Add to the union of error types (the existing line shows `from?: JobStatus; to?: JobStatus`) — keep those AND add stage-typed equivalents are unnecessary since the union already accepts the strings; no change needed.

Add `changeStage` method to the exported `jobsClient` object, immediately below `changeStatus`:

```typescript
changeStage: (id: string, stage: JobStage) =>
  call<MutateResp>(`/api/jobs/${encodeURIComponent(id)}/stage`, { method: 'PATCH', body: { stage } }),
```

- [ ] **Step 2: Add MSW handler for `PATCH /api/jobs/:id/stage`**

In `desktop/portal/src/console/test/msw-handlers.ts`, add (next to the existing PATCH `/api/jobs/:id` handler):

```typescript
http.patch('/api/jobs/:id/stage', async ({ params, request }) => {
  const body = (await request.json()) as { stage: string };
  return HttpResponse.json({
    job: {
      id: params.id, foremanId: 'f-1', clientId: null, engagementId: null,
      title: 'Mock Job', description: null, status: 'planned', stage: body.stage,
      scheduledAt: null, location: null, latitude: null, longitude: null,
      geocodedAt: null, createdAt: '2026-05-11T10:00:00Z',
      updatedAt: '2026-05-11T11:00:00Z', client: null,
    },
  });
}),
```

- [ ] **Step 3: Find every Job literal in test fixtures and add `stage: 'lead'`**

Run: `cd desktop/portal && grep -RIn "foremanId:" src --include='*.test.tsx' --include='*.test.ts'`

For each result that's building a `Job` literal (look for surrounding `title:`, `status:`, `createdAt:` fields), add `stage: 'lead'` next to the `status:` line. Common locations expected:
- `src/console/routes/__tests__/JobDetailRoute.test.tsx`
- `src/console/routes/__tests__/JobsListRoute.test.tsx`
- `src/console/routes/__tests__/ClientDetailRoute.test.tsx`
- `src/console/components/jobs/__tests__/EditJobModal.test.tsx`

Also update `msw-handlers.ts` mock jobs (the existing GET handlers) to include `stage: 'lead'`.

- [ ] **Step 4: tsc + full portal test suite**

Run:
```bash
cd desktop/portal
npx tsc --noEmit
npm run test:run
```
Expected: both clean. If `tsc` flags a missing `stage` somewhere, add it; re-run.

- [ ] **Step 5: Commit**

```bash
git add desktop/portal/src/console/api/jobsClient.ts \
        desktop/portal/src/console/test/msw-handlers.ts \
        $(git diff --name-only desktop/portal/src/console | grep test)
git commit -m "feat(portal): jobsClient.changeStage + Job.stage + MSW + fixtures

Adds JobStage type, stage field to Job, changeStage(id, stage) client
method (mirrors changeStatus), MSW PATCH /:id/stage echo handler, and
'stage: \"lead\"' on every Job literal in test fixtures.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

(If `git diff` substitution feels fragile, list the actual paths explicitly. Never `git add -A`.)

---

### Task 6: `JobStageBar` component + test

**Files:**
- Create: `desktop/portal/src/console/components/jobs/JobStageBar.tsx`
- Create: `desktop/portal/src/console/components/jobs/__tests__/JobStageBar.test.tsx`

- [ ] **Step 1: Write the failing test**

`desktop/portal/src/console/components/jobs/__tests__/JobStageBar.test.tsx`:

```typescript
import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { JobStageBar } from '../JobStageBar';

describe('JobStageBar', () => {
  it('renders 7 stage dots', () => {
    const { container } = render(<JobStageBar stage="lead" />);
    expect(container.querySelectorAll('[data-stage-dot]')).toHaveLength(7);
  });

  it('shows the current stage label in uppercase', () => {
    render(<JobStageBar stage="in_progress" />);
    expect(screen.getByText('IN PROGRESS')).toBeInTheDocument();
  });

  it('marks the current dot as active', () => {
    const { container } = render(<JobStageBar stage="review" />);
    const active = container.querySelector('[data-stage-dot-active="true"]');
    expect(active).toHaveAttribute('data-stage', 'review');
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd desktop/portal && npm run test:run -- JobStageBar`
Expected: fails — module not found.

- [ ] **Step 3: Implement `JobStageBar`**

`desktop/portal/src/console/components/jobs/JobStageBar.tsx`:

```typescript
// desktop/portal/src/console/components/jobs/JobStageBar.tsx
import type { JobStage } from '../../api/jobsClient';

const STAGES: JobStage[] = [
  'lead', 'proposal', 'approved', 'in_progress', 'review', 'invoice', 'closed',
];

const LABELS: Record<JobStage, string> = {
  lead: 'LEAD',
  proposal: 'PROPOSAL',
  approved: 'APPROVED',
  in_progress: 'IN PROGRESS',
  review: 'REVIEW',
  invoice: 'INVOICE',
  closed: 'CLOSED',
};

export function JobStageBar({ stage }: { stage: JobStage }) {
  const currentIdx = STAGES.indexOf(stage);
  return (
    <div className="font-mono bg-console-surface border border-console-border px-4 py-3 mb-3">
      <div className="flex items-center">
        {STAGES.map((s, i) => {
          const filled = i <= currentIdx;
          const active = i === currentIdx;
          return (
            <div key={s} className="flex items-center flex-1 last:flex-none">
              <div className="flex items-center">
                <span className={filled ? 'text-console-accent' : 'text-console-text-muted/40'}>(</span>
                <span
                  data-stage-dot
                  data-stage={s}
                  data-stage-dot-active={active ? 'true' : 'false'}
                  className={[
                    'mx-1 inline-block rounded-full',
                    active ? 'h-2.5 w-2.5' : 'h-2 w-2',
                    filled
                      ? 'bg-console-accent'
                      : 'border border-console-text-muted/40',
                  ].join(' ')}
                />
                <span className={filled ? 'text-console-accent' : 'text-console-text-muted/40'}>)</span>
              </div>
              {i < STAGES.length - 1 && (
                <div
                  className={[
                    'flex-1 h-px mx-1',
                    i < currentIdx ? 'bg-console-accent' : 'bg-console-text-muted/15',
                  ].join(' ')}
                />
              )}
            </div>
          );
        })}
      </div>
      <div className="text-center text-xs tracking-wider text-console-accent mt-2">
        {LABELS[stage]}
      </div>
    </div>
  );
}
```

- [ ] **Step 4: Re-run the test**

Run: `cd desktop/portal && npm run test:run -- JobStageBar`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add desktop/portal/src/console/components/jobs/JobStageBar.tsx \
        desktop/portal/src/console/components/jobs/__tests__/JobStageBar.test.tsx
git commit -m "feat(portal): JobStageBar -- monospace 7-stage pipeline indicator

Dots-and-lines bar mirroring APK JobStageBar in spirit (Tailwind /
console palette). Filled dots before+including current stage; active
dot enlarged; current label below in uppercase.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 7: `JobStageControls` component + test

**Files:**
- Create: `desktop/portal/src/console/components/jobs/JobStageControls.tsx`
- Create: `desktop/portal/src/console/components/jobs/__tests__/JobStageControls.test.tsx`

- [ ] **Step 1: Write the failing test**

`desktop/portal/src/console/components/jobs/__tests__/JobStageControls.test.tsx`:

```typescript
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, beforeEach } from 'vitest';
import { JobStageControls } from '../JobStageControls';
import { useJobsStore } from '../../../stores/jobsStore';
import type { Job, JobStage } from '../../../api/jobsClient';

function mockJob(stage: JobStage): Job {
  return {
    id: 'j1', foremanId: 'f-1', clientId: null, engagementId: null,
    title: 't', description: null, status: 'planned', stage,
    scheduledAt: null, location: null, latitude: null, longitude: null,
    geocodedAt: null, createdAt: '2026-05-11T10:00:00Z',
    updatedAt: '2026-05-11T11:00:00Z', client: null,
  };
}

describe('JobStageControls', () => {
  beforeEach(() => useJobsStore.getState().clear());

  it('lead -> shows CREATE PROPOSAL', () => {
    render(<JobStageControls job={mockJob('lead')} />);
    expect(screen.getByRole('button', { name: /create proposal/i })).toBeInTheDocument();
  });

  it('proposal -> shows MARK APPROVED + MARK REJECTED', () => {
    render(<JobStageControls job={mockJob('proposal')} />);
    expect(screen.getByRole('button', { name: /mark approved/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /mark rejected/i })).toBeInTheDocument();
  });

  it('approved -> shows START WORK', () => {
    render(<JobStageControls job={mockJob('approved')} />);
    expect(screen.getByRole('button', { name: /start work/i })).toBeInTheDocument();
  });

  it('in_progress -> shows MARK WORK COMPLETE', () => {
    render(<JobStageControls job={mockJob('in_progress')} />);
    expect(screen.getByRole('button', { name: /mark work complete/i })).toBeInTheDocument();
  });

  it('review -> shows GENERATE INVOICE', () => {
    render(<JobStageControls job={mockJob('review')} />);
    expect(screen.getByRole('button', { name: /generate invoice/i })).toBeInTheDocument();
  });

  it('invoice -> shows MARK PAID - CLOSE + REOPEN INVOICE', () => {
    render(<JobStageControls job={mockJob('invoice')} />);
    expect(screen.getByRole('button', { name: /mark paid - close/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /reopen invoice/i })).toBeInTheDocument();
  });

  it('closed -> shows REOPEN JOB only', () => {
    render(<JobStageControls job={mockJob('closed')} />);
    expect(screen.getByRole('button', { name: /reopen job/i })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /create proposal/i })).not.toBeInTheDocument();
  });

  it('clicking a forward button calls changeStage and upserts the job', async () => {
    useJobsStore.getState().setJobs([mockJob('lead')]);
    render(<JobStageControls job={mockJob('lead')} />);
    fireEvent.click(screen.getByRole('button', { name: /create proposal/i }));
    await waitFor(() => {
      const j = useJobsStore.getState().jobs.find((x) => x.id === 'j1');
      expect(j?.stage).toBe('proposal');
    });
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd desktop/portal && npm run test:run -- JobStageControls`
Expected: fails — module not found.

- [ ] **Step 3: Implement `JobStageControls`**

`desktop/portal/src/console/components/jobs/JobStageControls.tsx`:

```typescript
// desktop/portal/src/console/components/jobs/JobStageControls.tsx
import { useState } from 'react';
import { jobsClient } from '../../api/jobsClient';
import type { Job, JobStage } from '../../api/jobsClient';
import { useJobsStore } from '../../stores/jobsStore';
import { useToast } from '../../hooks/useToast';
import { Button } from '../ui/Button';

interface Transition {
  label: string;
  to: JobStage;
  variant?: 'primary' | 'ghost';
}

const TRANSITIONS: Record<JobStage, Transition[]> = {
  lead:        [{ label: 'CREATE PROPOSAL',    to: 'proposal' }],
  proposal:    [{ label: 'MARK APPROVED',      to: 'approved' },
                { label: 'MARK REJECTED',      to: 'lead',     variant: 'ghost' }],
  approved:    [{ label: 'START WORK',         to: 'in_progress' }],
  in_progress: [{ label: 'MARK WORK COMPLETE', to: 'review' }],
  review:      [{ label: 'GENERATE INVOICE',   to: 'invoice' }],
  invoice:     [{ label: 'MARK PAID - CLOSE',  to: 'closed' },
                { label: 'REOPEN INVOICE',     to: 'review',   variant: 'ghost' }],
  closed:      [{ label: 'REOPEN JOB',         to: 'invoice',  variant: 'ghost' }],
};

export function JobStageControls({ job }: { job: Job }) {
  const transitions = TRANSITIONS[job.stage] ?? [];
  const upsertJob = useJobsStore((s) => s.upsertJob);
  const toast = useToast();
  const [busy, setBusy] = useState(false);

  async function handleClick(to: JobStage) {
    if (busy) return;
    setBusy(true);
    const result = await jobsClient.changeStage(job.id, to);
    setBusy(false);
    if (result.ok) {
      upsertJob(result.value.job);
      toast.show(`Stage: ${to.replace('_', ' ').toUpperCase()}`);
    } else {
      toast.show(result.error || 'Failed to change stage');
    }
  }

  if (transitions.length === 0) {
    return <div className="text-console-text-muted text-xs mb-3">Job closed.</div>;
  }

  return (
    <div className="flex flex-wrap gap-2 mb-4">
      {transitions.map((t) => (
        <Button
          key={t.to}
          variant={t.variant === 'ghost' ? 'secondary' : 'primary'}
          onClick={() => handleClick(t.to)}
          disabled={busy}
        >
          {t.label}
        </Button>
      ))}
    </div>
  );
}
```

(Confirm `useJobsStore` exposes `upsertJob` and `setJobs` — both used in Slice 1; if a method name differs, adjust accordingly.)

- [ ] **Step 4: Re-run the test**

Run: `cd desktop/portal && npm run test:run -- JobStageControls`
Expected: PASS for all 8 tests.

- [ ] **Step 5: Commit**

```bash
git add desktop/portal/src/console/components/jobs/JobStageControls.tsx \
        desktop/portal/src/console/components/jobs/__tests__/JobStageControls.test.tsx
git commit -m "feat(portal): JobStageControls -- per-stage transition buttons

Context-aware buttons per current stage (APK labels preserved).
Forward as primary, targeted reverses as ghost. Click calls
jobsClient.changeStage + useJobsStore.upsertJob + toast.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 8: `JobDetailRoute` integration

**Files:**
- Modify: `desktop/portal/src/console/routes/JobDetailRoute.tsx`
- Modify: `desktop/portal/src/console/routes/__tests__/JobDetailRoute.test.tsx`

- [ ] **Step 1: Extend the JobDetailRoute test to assert bar + controls render**

In `desktop/portal/src/console/routes/__tests__/JobDetailRoute.test.tsx`, add a new test case (do NOT modify existing passing tests):

```typescript
it('renders the JobStageBar and stage controls for the current stage', async () => {
  // Seed a job at 'approved' so the bar shows the right marker and
  // the controls render the APPROVED -> START WORK button.
  // Use whatever store-seeding pattern the existing tests use; below mirrors them:
  useJobsStore.getState().setJobs([{
    id: 'jX', foremanId: 'f-1', clientId: null, engagementId: null,
    title: 'Stage test', description: null, status: 'planned', stage: 'approved',
    scheduledAt: null, location: null, latitude: null, longitude: null,
    geocodedAt: null, createdAt: '2026-05-11T10:00:00Z',
    updatedAt: '2026-05-11T11:00:00Z', client: null,
  }]);
  renderAt('jX'); // use the existing helper in this file
  expect(await screen.findByText('APPROVED')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: /start work/i })).toBeInTheDocument();
});
```

(If the file's existing tests use MSW for `GET /api/jobs/:id` rather than store-seeding, add an MSW override here that returns the same job shape with `stage: 'approved'`.)

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd desktop/portal && npm run test:run -- JobDetailRoute`
Expected: the new test fails (bar/controls not mounted).

- [ ] **Step 3: Mount the components in `JobDetailRoute.tsx`**

In `desktop/portal/src/console/routes/JobDetailRoute.tsx`:

Add imports near the top:

```typescript
import { JobStageBar } from '../components/jobs/JobStageBar';
import { JobStageControls } from '../components/jobs/JobStageControls';
```

Mount, directly under the title row (immediately after the `<h1>...</h1>` + `[Edit]` row, before the existing tasks/description section):

```tsx
<JobStageBar stage={job.stage} />
<JobStageControls job={job} />
```

- [ ] **Step 4: Re-run the test**

Run: `cd desktop/portal && npm run test:run -- JobDetailRoute`
Expected: PASS.

- [ ] **Step 5: Run full portal test + tsc + build**

```bash
cd desktop/portal
npm run test:run
npx tsc --noEmit
npm run build
```
Expected: all green.

- [ ] **Step 6: Commit**

```bash
git add desktop/portal/src/console/routes/JobDetailRoute.tsx \
        desktop/portal/src/console/routes/__tests__/JobDetailRoute.test.tsx
git commit -m "feat(portal): mount JobStageBar + JobStageControls on JobDetailRoute

Bar appears directly under the title row; controls below the bar
(above the tasks section). The existing [Edit] button and the rest
of the route are unchanged.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 9: End-to-end verification

**Files:** none modified — verification only.

- [ ] **Step 1: Full backend suite**

```bash
cd backend
DATABASE_URL=postgresql://localhost/smithnet npx jest
```
Expected: all suites pass. Recreate `foreman-demo@example.com` if needed (known recurring issue per project memory) via register + `UPDATE users SET role='foreman'`.

- [ ] **Step 2: Full portal suite + tsc + build**

```bash
cd desktop/portal
npm run test:run
npx tsc --noEmit
npm run build
```
Expected: all green.

- [ ] **Step 3: Restart `:3030` so backend route is live**

The dev backend uses `ts-node-dev` which doesn't always pick up new routes — restart to be safe (per project memory `project_portal_dev_backend_port`):

```bash
# Find the existing backend process
lsof -i :3030 -P | grep LISTEN
# Kill the PID, then restart from /Users/fegensprenelon/smith-net/backend
cd backend && npm run dev   # or whatever the dev script is in package.json
```

- [ ] **Step 4: Live smoke test (optional but recommended)**

In a browser at `http://localhost:5174/console/jobs/<job-id>` logged in as foreman:
- Bar shows current stage (default `lead`)
- Click `[CREATE PROPOSAL]` -> bar advances to `PROPOSAL`, toast appears
- Reload -> still `PROPOSAL` (persisted)
- Try the reverse `[MARK REJECTED]` -> back to `LEAD`

- [ ] **Step 5: Final wrap commit (if anything updated, e.g. a CHANGELOG)**

Nothing to commit if all tasks landed cleanly. Move to finishing-a-development-branch.

---

## Reuse references

- **Backend convention to mirror:** `backend/src/jobsRoutes.ts:91-114` (status route) and `backend/src/jobsService.ts:268-289` (`changeStatus`).
- **Audit pattern:** `auditLog.log(AuditAction.JOB_STATUS_CHANGED, ...)` at `jobsService.ts:282`.
- **Portal `MutateResp` + `call<T>` helper:** `desktop/portal/src/console/api/jobsClient.ts` (Slice 1 added `update`; same pattern).
- **`useJobsStore.upsertJob`:** used by `CreateJobModal` and `EditJobModal` (Slice 1).
- **`useToast`:** `desktop/portal/src/console/hooks/useToast.ts`.
- **MSW handler pattern:** existing `http.patch('/api/jobs/:id', ...)` (Slice 1).
- **APK reference (do not modify):** `JobStage.kt`, `JobStageBar.kt`, `JobPipelineScreen.kt`.

---

## Self-review

- [x] **Spec coverage**: every section of the spec maps to a task — schema/CHECK (T1), state machine (T2), service (T3), zod + route (T4), portal client (T5), bar (T6), controls (T7), route mount (T8), end-to-end verify (T9).
- [x] **Placeholder scan**: no TBD/TODO, every code block is real, every command has expected output.
- [x] **Type consistency**: `JobStage` lowercase snake_case throughout; `changeStage` (not `setStage` / `transitionStage`); `InvalidStageTransitionError` (not `StageTransitionError`); `assertValidStageTransition` (not `validateStageTransition`).
- [x] **Step count per task**: each task is 4-6 steps of 2-5 minutes each, ending in a commit. Bite-sized.
- [x] **No `git add -A`**: every commit names files explicitly (one `$(git diff --name-only ...)` substitution in T5; the plan flags it as adjustable to literal paths).
- [x] **Commit trailer**: every commit message ends with the required `Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>`.
- [x] **No emoji**: ASCII tokens only.
