# Plan 2 — Jobs Backend End-to-End — Design

**Date:** 2026-05-11
**Scope:** Backend Jobs domain (schema + service + routes + audit logging + tests). One vertical slice.
**Target:** `/backend/`
**Predecessor:** Plan 1 (operator console foundation, 22 commits, ships `efc9aa7..ba5728c`)

## Summary

Add the backend Jobs domain so the operator console (Plan 1 foundation) can list, create, update, dispatch, and assign crew to jobs scoped to the calling Foreman. Hybrid persistence model: standalone `jobs` + `job_crew` tables for fast queries, with every state-changing operation writing an entry to the existing `auditLog` (the same mechanism used by `authRoutes.ts` for `USER_REGISTER`, `USER_LOGIN`, etc.) so "what happened" is durably recorded.

Plan 1 stubbed out which endpoints the Job Board UX needed and explicitly deferred the audit. Plan 2 fills that gap with concrete routes, a service layer, schemas, tier-gating middleware, ownership middleware, a migration, and a test suite — matching the patterns established by `authRoutes.ts`, `auth-cookie.test.ts`, and the F1.5 zod validation work that just landed.

## Decisions

| Decision | Choice | Why |
|----------|--------|-----|
| Audit pattern | Hybrid (standalone table + `auditLog.log()` on every state change) | Fast queries; `auditLog` is the canonical audit mechanism (used by authRoutes, messageBus). `intentService` is for scope-negotiation, NOT state-change audit — wrong tool for this job. |
| Plan scope | Jobs vertical only — no Clients API, no Crew CRUD, no WS push | Plan was originally one mega-plan; split for shippability |
| Clients table | NOT in this plan (deferred to Plan 5) | `client_id` column on `jobs` is nullable — Plan 5 adds the FK |
| Crew model | Profiles (role='team'\|'lead') joined via `job_crew` | Reuses auth/profiles, no parallel user model |
| Engagements | Stay stub — `engagement_id` column nullable, no API | Not needed for dispatch MVP |
| Live updates (WebSocket) | NOT in this plan (deferred to Plan 3) | Plan 3 builds the dispatcher + frontend stores together |
| DB integration | Use existing `pg` Pool from `db.ts` | Pattern established by `intentService`, `ledger`, etc. |
| Status state machine | 4 states, 5 transitions, terminal `complete`/`cancelled` | Matches dispatch UX; no CHECK constraint in DB for evolution |
| Status validation | Service layer (`assertValidTransition`) | Easier to evolve than DB CHECK; routes get a structured 400 |
| Routing file | New `jobsRoutes.ts` mounted at `/api/jobs` | Cleaner than appending to the 1393-line `api.ts` |
| Service file | New `jobsService.ts` following `intentService.ts` shape | Pure functions on data, no Express types |
| Schemas | New `schemas/jobs.ts` (zod, F1.5 pattern) | Matches `schemas/auth.ts` |
| Tier gate | New `middleware/requireConsoleTier.ts` checking role ∈ {foreman, enterprise, admin} | Same role gate as `RequireAuth.tsx`'s `hasConsoleAccess` |
| Ownership gate | New `middleware/requireJobOwner.ts` attaching `req.job` | Avoids double-fetching in handlers |
| Audit call site | Inside service (not routes) so audit can't be bypassed | Bypassable audit defeats the point. Pattern matches existing `auditLog.log(USER_REGISTER, ...)` calls inside authRoutes handlers. |
| Test pattern | Inline app built from `jobsRouter` via supertest | Matches existing `auth-cookie.test.ts` |
| Test isolation | Unique-foreman-per-test (matching the unique-email pattern) | Shared in-memory userStore behavior is established |

## Architecture

### File structure

```
backend/src/
|-- jobsRoutes.ts                  // new — Express router, mounted at /api/jobs in server.ts
|-- jobsService.ts                 // new — pg queries + auditLog.log() calls; pure data layer
|-- schemas/
|   `-- jobs.ts                    // new — zod CreateJobBody, UpdateJobBody, StatusChangeBody, AssignCrewBody
|-- middleware/
|   |-- validate.ts                // existing (F1.5)
|   |-- requireConsoleTier.ts      // new — 403 tier_required if role not in CONSOLE_ROLES
|   `-- requireJobOwner.ts         // new — 404/403; attaches req.job
|-- __tests__/
|   `-- jobs-routes.test.ts        // new — supertest coverage of all routes + auditLog entries
|-- migrations/
|   `-- 003_jobs_expansion.sql     // new — ALTER jobs + DROP/recreate job_crew
|-- server.ts                      // modify — one new line: app.use('/api/jobs', jobsRouter)
```

### Stack

- `pg.Pool` (already wired in `db.ts`)
- `zod` (added by F1.5, commit `00bc6f3`)
- `jest` + `supertest` (added by Plan 1 Task 2.5, commit `6ffcad5`)
- `auditLog` (existing — re-used for state-change audit)

No new npm dependencies.

### Scope boundary — explicitly NOT in Plan 2

| Out of scope | Where it lands |
|---|---|
| WebSocket events / live JobUpdated push | Plan 3 |
| `/api/clients` CRUD + clients table | Plan 5 |
| Separate `/api/crew` CRUD (browseable crew list) | Plan 4 |
| Engagement entity logic | Future plan (not yet scheduled) |
| Frontend Job Board UI | Plan 3 |
| MapLibre / map view | Plan 4 |

## Schema migration

`backend/migrations/003_jobs_expansion.sql`:

```sql
-- ────────────── Expand jobs ──────────────
ALTER TABLE jobs
  ADD COLUMN IF NOT EXISTS foreman_id     TEXT REFERENCES profiles(id),
  ADD COLUMN IF NOT EXISTS client_id      UUID,         -- nullable; clients table lands in Plan 5
  ADD COLUMN IF NOT EXISTS engagement_id  UUID,         -- nullable
  ADD COLUMN IF NOT EXISTS scheduled_at   TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS location       TEXT,
  ADD COLUMN IF NOT EXISTS updated_at     TIMESTAMPTZ DEFAULT NOW();

ALTER TABLE jobs ALTER COLUMN status SET DEFAULT 'planned';

CREATE INDEX IF NOT EXISTS idx_jobs_foreman ON jobs(foreman_id);
CREATE INDEX IF NOT EXISTS idx_jobs_status  ON jobs(status);

-- ────────────── Recreate job_crew ──────────────
-- Current job_crew is a stub (id + created_at, no real schema, no data). Recreate.
DROP TABLE IF EXISTS job_crew;
CREATE TABLE job_crew (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  job_id       UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
  profile_id   TEXT NOT NULL REFERENCES profiles(id),
  role_on_job  TEXT NOT NULL DEFAULT 'crew',  -- 'crew' | 'lead'
  assigned_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE(job_id, profile_id)
);
CREATE INDEX idx_job_crew_job     ON job_crew(job_id);
CREATE INDEX idx_job_crew_profile ON job_crew(profile_id);
```

**Notes:**
- `foreman_id TEXT` matches `profiles.id` (TEXT in the existing schema)
- `client_id` / `engagement_id` are nullable + no FK constraints — added later by Plan 5
- `status` values are convention only; no CHECK constraint so we can evolve states cheaply. Service layer enforces.
- `DROP TABLE job_crew` is safe — current table is a stub with no real data; this is pre-prod.

## Status state machine

```
        planned ──start──> in_progress ──finish──> complete
            │                   │
            └───cancel───┐ ┌────┘  cancel
                         ▼ ▼
                      cancelled
```

| Valid transitions | |
|---|---|
| `planned` | → `in_progress` | `cancelled` |
| `in_progress` | → `complete` | `cancelled` |
| `complete` | terminal — no transitions |
| `cancelled` | terminal — no transitions |

`jobsService.changeStatus()` calls `assertValidTransition(from, to)`. Invalid transition throws `InvalidTransitionError` → route returns:

```json
HTTP/1.1 400 Bad Request
{ "error": "Invalid status transition", "code": "invalid_status_transition", "from": "complete", "to": "planned" }
```

## Service layer (`jobsService.ts`)

Follows `intentService.ts` shape — exports pure functions on data, throws typed errors, never touches Express types.

### Types

```ts
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

export class NotFoundError extends Error {}
export class InvalidTransitionError extends Error {
  constructor(public from: JobStatus, public to: JobStatus) {
    super(`Invalid status transition: ${from} -> ${to}`);
  }
}
```

### Surface

```ts
// Read
listByForeman(foremanId: string): Promise<Job[]>
getById(jobId: string): Promise<Job | null>
listCrew(jobId: string): Promise<CrewAssignment[]>

// Mutate — each calls auditLog.log() on success
create(input: {
  foremanId: string;
  title: string;
  description?: string;
  scheduledAt?: Date;
  location?: string;
  clientId?: string;
  engagementId?: string;
}): Promise<Job>

update(jobId: string, patch: Partial<Pick<Job, 'title'|'description'|'scheduledAt'|'location'>>): Promise<Job>

changeStatus(jobId: string, newStatus: JobStatus): Promise<Job>   // throws InvalidTransitionError

assignCrew(jobId: string, profileId: string, roleOnJob?: 'crew'|'lead'): Promise<CrewAssignment>

unassignCrew(jobId: string, profileId: string): Promise<void>
```

### Conventions

- DB columns are `snake_case`; service surface is `camelCase`. Mapping happens once per function in a helper `mapRow(row): Job`.
- `pg` import from `./db`; throw on `null` pool with `'Postgres not configured'`.
- Each mutation runs the DB write first, then calls `auditLog.log(...)`. `auditLog.log()` is fire-and-forget by design (it buffers internally), so no transaction wrapping is needed. The trade-off: if the audit manager's flush fails the DB write still committed — this is the same behavior already accepted across `authRoutes.ts`.

## Audit logging

Every state-changing service operation calls `auditLog.log(action, actorId, details)` before returning. The `auditLog` module already exists in `backend/src/auditLog.ts` and is the canonical audit mechanism (used by `authRoutes.ts` for `USER_REGISTER`, `USER_LOGIN`, etc.). Plan 2 extends the `AuditAction` enum with 5 new values.

| Service op | New AuditAction value | Details payload |
|---|---|---|
| `create()` | `JOB_CREATED = 'job.created'` | `{ jobId, title, status: 'planned', scheduledAt, location, clientId, engagementId }` |
| `update()` | `JOB_UPDATED = 'job.updated'` | `{ jobId, changedFields: string[], after: Partial<Job> }` |
| `changeStatus()` | `JOB_STATUS_CHANGED = 'job.status_changed'` | `{ jobId, from: JobStatus, to: JobStatus }` |
| `assignCrew()` | `JOB_CREW_ASSIGNED = 'job.crew_assigned'` | `{ jobId, profileId, roleOnJob }` |
| `unassignCrew()` | `JOB_CREW_UNASSIGNED = 'job.crew_unassigned'` | `{ jobId, profileId }` |

Call shape:

```ts
auditLog.log(AuditAction.JOB_CREATED, foremanId, {
  jobId: job.id,
  title: job.title,
  status: job.status,
  // ...
});
```

`auditLog.log()` is synchronous-fire-and-forget inside the manager (the existing pattern). It does not throw or block the response. The audit log buffers internally and flushes on its own cadence; we trust that mechanism the same way authRoutes does.

**Note on `intentService`**: an earlier draft of this spec proposed emitting Intents on state change. That was wrong — `intentService.createIntent()` is for negotiating *scope of work* between parties (it has fields like `scopeStatement`, `parties[]`, `intendedJobIds[]`), not for operational state-change audit. Jobs MAY participate in Intents downstream (an Intent can reference a `jobId`), but that linkage is not Plan 2's concern.

## Middleware

### `middleware/requireConsoleTier.ts`

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

### `middleware/requireJobOwner.ts`

```ts
import { Response, NextFunction } from 'express';
import { AuthenticatedRequest } from '../auth';
import * as jobsService from '../jobsService';

export interface JobOwnerRequest extends AuthenticatedRequest {
  job?: jobsService.Job;
}

export async function requireJobOwner(req: JobOwnerRequest, res: Response, next: NextFunction) {
  const jobId = req.params.id;
  if (!jobId) return res.status(400).json({ error: 'Missing job id' });
  try {
    const job = await jobsService.getById(jobId);
    if (!job) return res.status(404).json({ error: 'Job not found' });
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

## Route chain

```
authenticateToken → requireConsoleTier → [validateBody(...)] → [requireJobOwner →] handler
```

| Method | Path | Middleware | Handler | Response |
|---|---|---|---|---|
| GET | `/` | auth → tier | `listMine` | `200 { jobs }` — scoped to `foreman_id = req.user.id` |
| GET | `/:id` | auth → tier → owner | `getOne` | `200 { job, crew }` |
| POST | `/` | auth → tier → validate(CreateJobBody) | `create` | `201 { job }` — `foremanId` injected; status defaults `'planned'` |
| PATCH | `/:id` | auth → tier → owner → validate(UpdateJobBody) | `update` | `200 { job }` |
| PATCH | `/:id/status` | auth → tier → owner → validate(StatusChangeBody) | `changeStatus` | `200 { job }` or `400 invalid_status_transition` |
| POST | `/:id/assign` | auth → tier → owner → validate(AssignCrewBody) | `assignCrew` | `201 { assignment }` or `409 duplicate_assignment` |
| DELETE | `/:id/assign/:profileId` | auth → tier → owner | `unassignCrew` | `204` |

## Schemas (`schemas/jobs.ts`)

```ts
import { z } from 'zod';

export const CreateJobBody = z.object({
  title:        z.string().trim().min(1).max(200),
  description:  z.string().trim().max(5000).optional(),
  scheduledAt:  z.string().datetime().optional(),  // ISO 8601; service parses to Date
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

## Error handling

| Service throws | Route maps to | Body |
|---|---|---|
| `NotFoundError` | 404 | `{ error: 'Job not found' }` |
| `InvalidTransitionError` | 400 | `{ error, code: 'invalid_status_transition', from, to }` |
| Unique constraint on `job_crew` | 409 | `{ error: 'Crew member already assigned', code: 'duplicate_assignment' }` |
| FK violation (bad profileId) | 400 | `{ error: 'Unknown profile', code: 'unknown_profile' }` |
| Anything else | 500 | `{ error: 'Internal error' }` + log |

Pg error codes consulted:
- `23505` — unique violation → `duplicate_assignment`
- `23503` — FK violation → `unknown_profile`

## Tests (`__tests__/jobs-routes.test.ts`)

Build inline app from `jobsRouter` + `cookieParser()` + `authRouter` (auth needed for cookies). Pattern matches existing `auth-cookie.test.ts`.

| Group | Cases |
|---|---|
| **Auth gate** | 401 when no cookie/header; 200 when authenticated foreman |
| **Tier gate** | 403 `tier_required` when role='solo'; 403 also for 'team', 'lead' |
| **Ownership** | 403 `not_owner` when foreman A reads foreman B's job; 200 on own |
| **Create** | 201 + Job; `foreman_id` set from req.user; status defaults 'planned'; zod rejects empty title; zod rejects extra fields (strict) |
| **List** | Returns only own jobs; cross-foreman isolation |
| **Update** | 200 patches title; rejects `status` in body (handled by PATCH `/:id/status` not PATCH `/:id`) |
| **Status transitions** | All 5 valid → 200; `complete → planned` → 400; `cancelled → in_progress` → 400 |
| **Assign / Unassign** | 201 happy path; 409 duplicate; 204 unassign; 404 unassign-nonexistent |
| **Audit logging** | After create/update/status/assign/unassign, the right `AuditAction.JOB_*` entry exists. Use `jest.spyOn(auditLog, 'log')` and assert call args (auditLog is a singleton — buffered, not directly queryable). |

### Test isolation
Each test creates its own foreman via `userStore.createUser(\`foreman-jobs-\${randomId}@example.com\`, ...)`. Same isolation pattern as `auth-cookie.test.ts`.

### Test DB
Tests run against the same Postgres the dev backend uses (configured via `DATABASE_URL`). If `DATABASE_URL` is unset (CI / fresh dev), tests must `skip` gracefully — flag during implementation if Jest needs a `describe.skipIf` helper.

## Out-of-scope / open questions for implementation

1. **`DATABASE_URL`-not-set test behavior** — graceful skip vs hard fail. Flag if skip helper is missing.
2. **`api.ts` route prefix collision** — confirm there's no existing `/jobs` route in `api.ts` that would shadow the new router. Pre-flight grep.
3. **Pre-existing failing tests** — `api-auth-integration.test.ts` and `auth-middleware.test.ts` have 4 failures unrelated to this work. Plan 2 must not make them worse; not in scope to fix them.

## Manual verification (mandatory before merge)

Pre-conditions: `DATABASE_URL` set, backend running on `:3030`, migration 003 applied, dev admin user exists.

```bash
# Login + get cookies
curl -sS -c /tmp/jobs.cookies -X POST http://localhost:3030/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@smithnet.local","password":"admin123"}' >/dev/null

# Create
curl -sS -b /tmp/jobs.cookies -X POST http://localhost:3030/api/jobs \
  -H "Content-Type: application/json" \
  -d '{"title":"Test panel install","location":"123 Main St"}'

# List
curl -sS -b /tmp/jobs.cookies http://localhost:3030/api/jobs

# Status change (valid)
curl -sS -b /tmp/jobs.cookies -X PATCH http://localhost:3030/api/jobs/$ID/status \
  -H "Content-Type: application/json" -d '{"status":"in_progress"}'

# Status change (invalid)
curl -sS -b /tmp/jobs.cookies -X PATCH http://localhost:3030/api/jobs/$ID/status \
  -H "Content-Type: application/json" -d '{"status":"planned"}'
# expect 400 invalid_status_transition

# Tier gate — login as Solo user, expect 403
curl -sS -c /tmp/solo.cookies -X POST http://localhost:3030/api/auth/login \
  -H "Content-Type: application/json" -d '{"email":"solo-user@example.com","password":"...",..}' >/dev/null
curl -sS -b /tmp/solo.cookies http://localhost:3030/api/jobs
# expect 403 tier_required
```

After all manual checks pass + automated tests pass: Plan 2 is shippable.

## Phasing recommendation

Inside Plan 2, suggested task order for the implementation plan:

1. Migration `003_jobs_expansion.sql` + apply + smoke
2. `jobsService.ts` types + `listByForeman` + `getById` (read-only first)
3. `middleware/requireConsoleTier.ts`
4. `middleware/requireJobOwner.ts`
5. `schemas/jobs.ts`
6. `jobsRoutes.ts` skeleton + GET `/` + GET `/:id` (read routes)
7. Mount in `server.ts`
8. Extend `AuditAction` enum with 5 new `JOB_*` values
9. `jobsService.create()` + POST route + `auditLog.log(JOB_CREATED, ...)`
10. `jobsService.update()` + PATCH route + `auditLog.log(JOB_UPDATED, ...)`
11. `jobsService.changeStatus()` + status state machine + PATCH `/:id/status` + `auditLog.log(JOB_STATUS_CHANGED, ...)`
12. `jobsService.assignCrew()` / `unassignCrew()` + POST `/:id/assign` / DELETE + `auditLog.log(JOB_CREW_*, ...)`
12. Full test file (or incrementally added per route)
13. Manual curl walkthrough

Plan-writer can subdivide further. Phase 1 (migration) and Phase 2 (read-only service) are independently mergeable if needed.
