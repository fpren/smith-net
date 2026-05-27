# Portal Materials + Expenses — Slice 3 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add per-job **Materials checklist** (extends the existing `materials` table) and **Job Expenses** (new `job_expenses` table) with full backend write paths, portal UI sections on `JobDetailRoute`, a `JobCostRollup` (Materials/Expenses/Job total), and the APK's non-blocking `! N materials not checked off` warning on the `review` stage.

**Architecture:** Two parallel domains. Both mirror the established tasks-routes pattern: LIST on the jobs router (`GET /api/jobs/:id/{materials,expenses}`), CREATE/PATCH/DELETE on a dedicated router (`POST /api/{materials,expenses}`, `PATCH /api/{materials,expenses}/:id`, `DELETE /api/{materials,expenses}/:id`) with a per-resource ownership middleware. Audit on every mutation. Synthesizer determinism (D1-D5) preserved — new columns are NOT in the canonical hash.

**Tech Stack:** Node/Express + pg + zod + Jest (backend), Vite 5 + React 18 + TS strict + zustand + Tailwind 3 + Vitest + jsdom + MSW (portal).

**Spec:** `docs/superpowers/specs/2026-05-26-portal-materials-expenses-design.md`

**Note on route paths:** the spec section 4.3 said paths under `/api/jobs/:jobId/materials`. The codebase's established convention (tasks-routes) is flatter: LIST on jobs router, write-ops on a dedicated router with `jobId` in the body. This plan aligns with the established convention. Audit/security/data-model decisions from the spec all hold.

---

## File structure

**Create (backend):**
- `backend/migrations/032_materials_extend.sql`
- `backend/migrations/033_job_expenses.sql`
- `backend/src/materialsService.ts`
- `backend/src/expensesService.ts`
- `backend/src/materialsRoutes.ts`
- `backend/src/expensesRoutes.ts`
- `backend/src/middleware/requireMaterialOwner.ts`
- `backend/src/middleware/requireExpenseOwner.ts`
- `backend/src/schemas/materials.ts`
- `backend/src/schemas/expenses.ts`
- `backend/src/__tests__/materials-routes.test.ts`
- `backend/src/__tests__/expenses-routes.test.ts`

**Modify (backend):**
- `backend/src/jobsRoutes.ts` — add `GET /api/jobs/:id/materials` and `GET /api/jobs/:id/expenses` (mirror the existing `GET /api/jobs/:id/tasks`)
- `backend/src/auditLog.ts` — add 6 new `AuditAction` entries
- `backend/src/server.ts` — mount the two new routers

**Create (portal):**
- `desktop/portal/src/console/api/materialsClient.ts`
- `desktop/portal/src/console/api/expensesClient.ts`
- `desktop/portal/src/console/stores/materialsStore.ts`
- `desktop/portal/src/console/stores/expensesStore.ts`
- `desktop/portal/src/console/components/materials/MaterialsList.tsx`
- `desktop/portal/src/console/components/materials/AddMaterialModal.tsx`
- `desktop/portal/src/console/components/expenses/ExpensesTable.tsx`
- `desktop/portal/src/console/components/expenses/AddExpenseModal.tsx`
- `desktop/portal/src/console/components/jobs/JobCostRollup.tsx`
- One `__tests__/*.test.tsx` per component above (5 new test files)

**Modify (portal):**
- `desktop/portal/src/console/routes/JobDetailRoute.tsx` — mount the 3 new sections
- `desktop/portal/src/console/components/jobs/JobStageControls.tsx` — add REVIEW-stage warning
- `desktop/portal/src/console/components/jobs/__tests__/JobStageControls.test.tsx` — extend with warning test
- `desktop/portal/src/console/routes/__tests__/JobDetailRoute.test.tsx` — extend to assert new sections
- `desktop/portal/src/console/test/msw-handlers.ts` — add 8 new handlers

---

### Task 1: Verify materials table is empty + apply migration 032

**Files:**
- Create: `backend/migrations/032_materials_extend.sql`

- [ ] **Step 1: Verify the materials table has no orphan rows**

Run: `psql postgresql://localhost/smithnet -c "SELECT COUNT(*) AS total, COUNT(*) FILTER (WHERE job_id IS NULL) AS orphans FROM materials;"`
Expected: `total = 0, orphans = 0` (the table has had no writers; the synthesizer only reads). If `orphans > 0`, run `DELETE FROM materials WHERE job_id IS NULL;` before proceeding.

- [ ] **Step 2: Write the migration**

`backend/migrations/032_materials_extend.sql`:
```sql
-- Extend the materials table with checklist + cost-capture columns.
-- Synthesizer continues reading name/quantity/unit_cost; canonical hash unchanged.

ALTER TABLE materials
  ADD COLUMN IF NOT EXISTS notes TEXT,
  ADD COLUMN IF NOT EXISTS checked BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS checked_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS vendor TEXT,
  ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- Tighten and link.
ALTER TABLE materials ALTER COLUMN job_id SET NOT NULL;

-- Idempotent FK add: drop-if-exists, then add.
ALTER TABLE materials DROP CONSTRAINT IF EXISTS materials_job_id_fkey;
ALTER TABLE materials ADD CONSTRAINT materials_job_id_fkey
  FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_materials_job ON materials (job_id);
```

- [ ] **Step 3: Apply**

Run: `psql postgresql://localhost/smithnet -f backend/migrations/032_materials_extend.sql`
Expected: `ALTER TABLE`, `ALTER TABLE`, `ALTER TABLE`, `CREATE INDEX`. No errors.

- [ ] **Step 4: Verify with `\d materials`**

Run: `psql postgresql://localhost/smithnet -c "\d materials"`
Expected: all 11 columns (id, job_id, name, quantity, unit, unit_cost, created_at, notes, checked, checked_at, vendor, updated_at), `job_id` is NOT NULL with FK to `jobs(id)`, `idx_materials_job` index.

- [ ] **Step 5: Commit**

```bash
git add backend/migrations/032_materials_extend.sql
git commit -m "feat(materials): migration 032 -- extend materials + FK + index

Adds notes, checked, checked_at, vendor, updated_at. Tightens job_id
to NOT NULL with FK -> jobs(id) ON DELETE CASCADE. Index on (job_id).
Additive change -- synthesizer reads name/quantity/unit_cost; canonical
hash unchanged.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 2: materialsService + AuditAction additions

**Files:**
- Create: `backend/src/materialsService.ts`
- Modify: `backend/src/auditLog.ts`

- [ ] **Step 1: Add `MATERIAL_*` and `EXPENSE_*` audit actions**

In `backend/src/auditLog.ts`, find the `JOB_STAGE_CHANGED = 'job.stage_changed',` line (added in Slice 2). Add the 6 new entries immediately below:

```typescript
MATERIAL_CREATED = 'material.created',
MATERIAL_UPDATED = 'material.updated',
MATERIAL_DELETED = 'material.deleted',
EXPENSE_CREATED = 'expense.created',
EXPENSE_UPDATED = 'expense.updated',
EXPENSE_DELETED = 'expense.deleted',
```

(All 6 land in one edit so we don't need to touch this file again in T7.)

- [ ] **Step 2: Write `materialsService.ts`**

`backend/src/materialsService.ts`:
```typescript
// backend/src/materialsService.ts
//
// Per-job materials checklist with cost capture. Mirrors the APK Material
// data class. Owner check is enforced at route level (requireJobOwner for
// list/create; requireMaterialOwner for patch/delete).

import { pg, isPgEnabled } from './db';
import { auditLog, AuditAction } from './auditLog';
import { v4 as uuidv4 } from 'uuid';

export interface Material {
  id: string;
  jobId: string;
  name: string;
  notes: string | null;
  checked: boolean;
  checkedAt: Date | null;
  quantity: number;
  unit: string;
  unitCost: number;
  vendor: string | null;
  createdAt: Date;
  updatedAt: Date;
}

export class NotFoundError extends Error {
  constructor(message: string = 'Material not found') {
    super(message);
    this.name = 'NotFoundError';
  }
}

function requirePg() {
  if (!isPgEnabled() || !pg) throw new Error('[MaterialsService] Postgres client not initialized');
  return pg;
}

function mapRow(row: any): Material {
  return {
    id: row.id,
    jobId: row.job_id,
    name: row.name,
    notes: row.notes,
    checked: row.checked,
    checkedAt: row.checked_at ? new Date(row.checked_at) : null,
    quantity: Number(row.quantity),
    unit: row.unit,
    unitCost: Number(row.unit_cost),
    vendor: row.vendor,
    createdAt: new Date(row.created_at),
    updatedAt: new Date(row.updated_at),
  };
}

export async function listByJob(jobId: string): Promise<Material[]> {
  const db = requirePg();
  const { rows } = await db.query(
    `SELECT * FROM materials WHERE job_id = $1 ORDER BY created_at ASC`,
    [jobId],
  );
  return rows.map(mapRow);
}

export async function getById(id: string): Promise<Material | null> {
  const db = requirePg();
  const { rows } = await db.query(`SELECT * FROM materials WHERE id = $1`, [id]);
  return rows.length === 0 ? null : mapRow(rows[0]);
}

export interface CreateMaterialInput {
  jobId: string;
  name: string;
  notes?: string;
  quantity?: number;
  unit?: string;
  unitCost?: number;
  vendor?: string;
}

export async function create(input: CreateMaterialInput, actorId: string): Promise<Material> {
  const db = requirePg();
  const id = uuidv4();
  const { rows } = await db.query(
    `INSERT INTO materials
       (id, job_id, name, notes, quantity, unit, unit_cost, vendor)
     VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
     RETURNING *`,
    [
      id, input.jobId, input.name,
      input.notes ?? null,
      input.quantity ?? 1,
      input.unit ?? 'ea',
      input.unitCost ?? 0,
      input.vendor ?? null,
    ],
  );
  const m = mapRow(rows[0]);
  await auditLog.log(AuditAction.MATERIAL_CREATED, actorId, {
    materialId: m.id, jobId: m.jobId, name: m.name,
  });
  return m;
}

export interface UpdateMaterialPatch {
  name?: string;
  notes?: string | null;
  quantity?: number;
  unit?: string;
  unitCost?: number;
  vendor?: string | null;
  checked?: boolean;
}

export async function update(id: string, patch: UpdateMaterialPatch, actorId: string): Promise<Material | null> {
  const db = requirePg();
  const sets: string[] = [];
  const vals: any[] = [];
  let i = 1;
  for (const [col, key] of [
    ['name', 'name'], ['notes', 'notes'], ['quantity', 'quantity'],
    ['unit', 'unit'], ['unit_cost', 'unitCost'], ['vendor', 'vendor'],
  ] as const) {
    if (key in patch) { sets.push(`${col} = $${i++}`); vals.push((patch as any)[key]); }
  }
  if ('checked' in patch) {
    sets.push(`checked = $${i++}`);
    vals.push(patch.checked);
    sets.push(`checked_at = $${i++}`);
    vals.push(patch.checked ? new Date() : null);
  }
  if (sets.length === 0) return getById(id);
  sets.push(`updated_at = NOW()`);
  vals.push(id);
  const { rows } = await db.query(
    `UPDATE materials SET ${sets.join(', ')} WHERE id = $${i} RETURNING *`,
    vals,
  );
  if (rows.length === 0) return null;
  const m = mapRow(rows[0]);
  await auditLog.log(AuditAction.MATERIAL_UPDATED, actorId, {
    materialId: m.id, jobId: m.jobId, fields: Object.keys(patch),
  });
  return m;
}

export async function hardDelete(id: string, actorId: string): Promise<boolean> {
  const db = requirePg();
  const existing = await getById(id);
  if (!existing) return false;
  await db.query(`DELETE FROM materials WHERE id = $1`, [id]);
  await auditLog.log(AuditAction.MATERIAL_DELETED, actorId, {
    materialId: id, jobId: existing.jobId,
  });
  return true;
}
```

- [ ] **Step 3: Type-check**

Run: `cd backend && npx tsc --noEmit`
Expected: clean.

- [ ] **Step 4: Commit**

```bash
git add backend/src/materialsService.ts backend/src/auditLog.ts
git commit -m "feat(materials): materialsService + 6 audit actions

Materials CRUD with toggle-aware update (checked=true sets checked_at;
checked=false clears it). Audit on every mutation. Service is pure
data; ownership enforced at routes. Includes all 6 MATERIAL_* and
EXPENSE_* AuditAction enum entries (expenses service lands in T7).

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 3: requireMaterialOwner middleware

**Files:**
- Create: `backend/src/middleware/requireMaterialOwner.ts`

- [ ] **Step 1: Write the middleware**

`backend/src/middleware/requireMaterialOwner.ts`:
```typescript
import { Response, NextFunction } from 'express';
import { AuthenticatedRequest } from '../auth';
import * as materialsService from '../materialsService';
import * as jobsService from '../jobsService';

export interface MaterialOwnerRequest extends AuthenticatedRequest {
  material?: materialsService.Material;
  job?: jobsService.Job;
}

/**
 * Loads the material by :id, then its job, then asserts the request's user
 * is that job's foreman. Mirrors requireTaskOwner.ts. Used by
 * PATCH /api/materials/:id and DELETE /api/materials/:id.
 */
export async function requireMaterialOwner(
  req: MaterialOwnerRequest,
  res: Response,
  next: NextFunction,
) {
  const id = req.params.id;
  if (!id) return res.status(400).json({ error: 'Missing material id' });
  try {
    const material = await materialsService.getById(id);
    if (!material) return res.status(404).json({ error: 'Material not found' });
    const job = await jobsService.getById(material.jobId);
    if (!job) return res.status(404).json({ error: 'Parent job not found' });
    if (job.foremanId !== req.user!.id) {
      return res.status(403).json({ error: 'Not the job foreman', code: 'not_owner' });
    }
    req.material = material;
    req.job = job;
    next();
  } catch (e: any) {
    return res.status(500).json({ error: 'Failed to load material' });
  }
}
```

- [ ] **Step 2: Type-check**

Run: `cd backend && npx tsc --noEmit`
Expected: clean.

- [ ] **Step 3: Commit**

```bash
git add backend/src/middleware/requireMaterialOwner.ts
git commit -m "feat(materials): requireMaterialOwner middleware

Loads material -> parent job -> verifies foreman. Returns 403 not_owner
on cross-foreman (matches Slice 2 lesson), 404 on missing material or
orphan job. Mirrors requireTaskOwner.ts.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 4: materialsRoutes + zod + backend tests (TDD)

**Files:**
- Create: `backend/src/schemas/materials.ts`
- Create: `backend/src/materialsRoutes.ts`
- Create: `backend/src/__tests__/materials-routes.test.ts`
- Modify: `backend/src/jobsRoutes.ts` (add `GET /api/jobs/:id/materials`)
- Modify: `backend/src/server.ts` (mount the new router)

- [ ] **Step 1: Write the failing test file**

`backend/src/__tests__/materials-routes.test.ts`:
```typescript
import express from 'express';
import request from 'supertest';
import cookieParser from 'cookie-parser';
import { authRouter } from '../authRoutes';
import { jobsRouter } from '../jobsRoutes';
import { materialsRouter } from '../materialsRoutes';
import { generateTokens, UserRole, userStore } from '../auth';
import { createUserAndProfile } from '../jobsService';
import { pg, isPgEnabled } from '../db';

const describeDb = isPgEnabled() ? describe : describe.skip;

function buildApp() {
  const app = express();
  app.use(express.json());
  app.use(cookieParser());
  app.use('/api/auth', authRouter);
  app.use('/api/jobs', jobsRouter);
  app.use('/api', materialsRouter);
  return app;
}

async function foreman(suffix: string): Promise<{ id: string; token: string }> {
  const user = await createUserAndProfile({
    email: `foreman-mat-${suffix}-${Date.now()}@example.com`,
    password: 'password123',
    displayName: `Foreman ${suffix}`,
    role: UserRole.FOREMAN,
  });
  const { accessToken } = await generateTokens(user);
  return { id: user.id, token: accessToken };
}

async function makeJob(token: string): Promise<string> {
  const res = await request(buildApp()).post('/api/jobs').set('Authorization', `Bearer ${token}`)
    .send({ title: 'mat-job' });
  return res.body.job.id;
}

describeDb('materials routes', () => {
  const app = buildApp();

  afterEach(async () => {
    if (!isPgEnabled() || !pg) return;
    await pg.query(`DELETE FROM materials WHERE job_id IN (
      SELECT id FROM jobs WHERE foreman_id IN (
        SELECT id FROM profiles WHERE email LIKE 'foreman-mat-%' OR email LIKE 'solo-mat-%'
      )
    )`);
    await pg.query(`DELETE FROM jobs WHERE foreman_id IN (
      SELECT id FROM profiles WHERE email LIKE 'foreman-mat-%' OR email LIKE 'solo-mat-%'
    )`);
    await pg.query(`DELETE FROM profiles WHERE email LIKE 'foreman-mat-%' OR email LIKE 'solo-mat-%'`);
    await pg.query(`DELETE FROM users WHERE email LIKE 'foreman-mat-%' OR email LIKE 'solo-mat-%'`);
  });
  afterAll(async () => { await pg?.end(); });

  it('401 without auth', async () => {
    const res = await request(app).post('/api/materials').send({ jobId: 'x', name: 'y' });
    expect(res.status).toBe(401);
  });

  it('403 tier_required for Solo', async () => {
    const u = await userStore.createUser(`solo-mat-${Date.now()}@example.com`, 'password123', 'Solo', UserRole.SOLO);
    const { accessToken } = await generateTokens(u);
    const res = await request(app).post('/api/materials').set('Authorization', `Bearer ${accessToken}`)
      .send({ jobId: '00000000-0000-0000-0000-000000000000', name: 'x' });
    expect(res.status).toBe(403);
    expect(res.body.code).toBe('tier_required');
  });

  it('creates, lists, updates, toggles, deletes a material', async () => {
    const f = await foreman('crud');
    const jobId = await makeJob(f.token);
    const create = await request(app).post('/api/materials').set('Authorization', `Bearer ${f.token}`)
      .send({ jobId, name: '10/2 Romex', quantity: 50, unit: 'ft', unitCost: 0.85 });
    expect(create.status).toBe(201);
    const id = create.body.material.id;
    expect(create.body.material.name).toBe('10/2 Romex');
    expect(create.body.material.checked).toBe(false);

    const list = await request(app).get(`/api/jobs/${jobId}/materials`).set('Authorization', `Bearer ${f.token}`);
    expect(list.body.materials).toHaveLength(1);

    const toggleOn = await request(app).patch(`/api/materials/${id}`).set('Authorization', `Bearer ${f.token}`)
      .send({ checked: true });
    expect(toggleOn.body.material.checked).toBe(true);
    expect(toggleOn.body.material.checkedAt).not.toBeNull();

    const toggleOff = await request(app).patch(`/api/materials/${id}`).set('Authorization', `Bearer ${f.token}`)
      .send({ checked: false });
    expect(toggleOff.body.material.checked).toBe(false);
    expect(toggleOff.body.material.checkedAt).toBeNull();

    const del = await request(app).delete(`/api/materials/${id}`).set('Authorization', `Bearer ${f.token}`);
    expect(del.status).toBe(204);
    const after = await request(app).get(`/api/jobs/${jobId}/materials`).set('Authorization', `Bearer ${f.token}`);
    expect(after.body.materials).toHaveLength(0);
  });

  it('isolates across foremen (403 not_owner)', async () => {
    const a = await foreman('iso-a');
    const b = await foreman('iso-b');
    const jobId = await makeJob(a.token);
    const create = await request(app).post('/api/materials').set('Authorization', `Bearer ${a.token}`)
      .send({ jobId, name: 'A only' });
    const id = create.body.material.id;
    const res = await request(app).patch(`/api/materials/${id}`).set('Authorization', `Bearer ${b.token}`)
      .send({ name: 'hack' });
    expect(res.status).toBe(403);
    expect(res.body.code).toBe('not_owner');
  });

  it('rejects unknown fields (zod strict)', async () => {
    const f = await foreman('strict');
    const jobId = await makeJob(f.token);
    const res = await request(app).post('/api/materials').set('Authorization', `Bearer ${f.token}`)
      .send({ jobId, name: 'X', bogus: 1 });
    expect(res.status).toBe(400);
    expect(res.body.code).toBe('validation');
  });

  it('rejects negative quantity', async () => {
    const f = await foreman('neg');
    const jobId = await makeJob(f.token);
    const res = await request(app).post('/api/materials').set('Authorization', `Bearer ${f.token}`)
      .send({ jobId, name: 'X', quantity: -1 });
    expect(res.status).toBe(400);
    expect(res.body.code).toBe('validation');
  });

  it('cascades on job delete', async () => {
    const f = await foreman('casc');
    const jobId = await makeJob(f.token);
    await request(app).post('/api/materials').set('Authorization', `Bearer ${f.token}`)
      .send({ jobId, name: 'm1' });
    expect((await pg!.query(`SELECT COUNT(*) FROM materials WHERE job_id = $1`, [jobId])).rows[0].count).toBe('1');
    await pg!.query(`DELETE FROM jobs WHERE id = $1`, [jobId]);
    expect((await pg!.query(`SELECT COUNT(*) FROM materials WHERE job_id = $1`, [jobId])).rows[0].count).toBe('0');
  });
});
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `cd backend && DATABASE_URL=postgresql://localhost/smithnet npx jest src/__tests__/materials-routes.test.ts`
Expected: tests fail (module imports for `materialsRouter` not found).

- [ ] **Step 3: Add the zod schemas**

`backend/src/schemas/materials.ts`:
```typescript
import { z } from 'zod';

export const CreateMaterialBody = z.object({
  jobId: z.string().uuid(),
  name: z.string().min(1).max(200),
  notes: z.string().max(2000).optional(),
  quantity: z.number().nonnegative().optional(),
  unit: z.string().min(1).max(20).optional(),
  unitCost: z.number().nonnegative().optional(),
  vendor: z.string().max(200).optional(),
}).strict();
export type CreateMaterialBody = z.infer<typeof CreateMaterialBody>;

export const UpdateMaterialBody = z.object({
  name: z.string().min(1).max(200).optional(),
  notes: z.string().max(2000).nullable().optional(),
  quantity: z.number().nonnegative().optional(),
  unit: z.string().min(1).max(20).optional(),
  unitCost: z.number().nonnegative().optional(),
  vendor: z.string().max(200).nullable().optional(),
  checked: z.boolean().optional(),
}).strict();
export type UpdateMaterialBody = z.infer<typeof UpdateMaterialBody>;
```

- [ ] **Step 4: Add the LIST route to jobsRoutes.ts**

Find the existing `GET /api/jobs/:id/tasks` block (around line 47 in `backend/src/jobsRoutes.ts`). Add the materials LIST route immediately after it:

```typescript
// GET /api/jobs/:id/materials — per-job materials list (foreman of the job only)
jobsRouter.get('/:id/materials', requireJobOwner, async (req: JobOwnerRequest, res: Response) => {
  try {
    const materials = await materialsService.listByJob(req.job!.id);
    res.json({ materials });
  } catch (e) {
    requestLogger().error({ event: 'jobs_list_materials_error', err: e }, 'jobs list materials error');
    res.status(500).json({ error: 'Failed to load materials' });
  }
});
```

Add the import at top of `jobsRoutes.ts`:
```typescript
import * as materialsService from './materialsService';
```

- [ ] **Step 5: Write `materialsRoutes.ts`**

`backend/src/materialsRoutes.ts`:
```typescript
// backend/src/materialsRoutes.ts
//
// Owner-scoped write operations on materials. LIST lives on jobsRouter
// (GET /api/jobs/:id/materials) — that route uses requireJobOwner.
// Here: POST /api/materials, PATCH/DELETE /api/materials/:id.

import { Router, Response } from 'express';
import { authenticateToken, AuthenticatedRequest } from './auth';
import { validateBody } from './middleware/validate';
import { requireConsoleTier } from './middleware/requireConsoleTier';
import { requireJobOwner, JobOwnerRequest } from './middleware/requireJobOwner';
import { requireMaterialOwner, MaterialOwnerRequest } from './middleware/requireMaterialOwner';
import { CreateMaterialBody, UpdateMaterialBody } from './schemas/materials';
import * as materialsService from './materialsService';
import { requestLogger } from './logging';

export const materialsRouter = Router();

materialsRouter.use(authenticateToken, requireConsoleTier);

// POST /api/materials — body carries jobId. requireJobOwner reads it from
// the body (not the URL), which is how tasks does it.
materialsRouter.post('/materials', validateBody(CreateMaterialBody),
  async (req: AuthenticatedRequest, res: Response) => {
    try {
      const body = req.body as CreateMaterialBody;
      // Verify foreman of the target job. We can't use the requireJobOwner
      // middleware directly (it reads from params); use the service guard
      // via getById + foremanId check.
      const job = await (await import('./jobsService')).getById(body.jobId);
      if (!job) return res.status(404).json({ error: 'Job not found' });
      if (job.foremanId !== req.user!.id) {
        return res.status(403).json({ error: 'Not the job foreman', code: 'not_owner' });
      }
      const material = await materialsService.create(body, req.user!.id);
      res.status(201).json({ material });
    } catch (e) {
      requestLogger().error({ event: 'materials_create_error', err: e }, 'materials create error');
      res.status(500).json({ error: 'Failed to create material' });
    }
  });

materialsRouter.patch('/materials/:id', requireMaterialOwner, validateBody(UpdateMaterialBody),
  async (req: MaterialOwnerRequest, res: Response) => {
    try {
      const body = req.body as UpdateMaterialBody;
      const material = await materialsService.update(req.material!.id, body, req.user!.id);
      if (!material) return res.status(404).json({ error: 'Material not found' });
      res.json({ material });
    } catch (e) {
      requestLogger().error({ event: 'materials_update_error', err: e }, 'materials update error');
      res.status(500).json({ error: 'Failed to update material' });
    }
  });

materialsRouter.delete('/materials/:id', requireMaterialOwner,
  async (req: MaterialOwnerRequest, res: Response) => {
    try {
      await materialsService.hardDelete(req.material!.id, req.user!.id);
      res.status(204).send();
    } catch (e) {
      requestLogger().error({ event: 'materials_delete_error', err: e }, 'materials delete error');
      res.status(500).json({ error: 'Failed to delete material' });
    }
  });
```

- [ ] **Step 6: Mount the router in `server.ts`**

Find the existing `app.use('/api/jobs', jobsRouter);` line (around line 152). Add immediately after:

```typescript
import { materialsRouter } from './materialsRoutes';
// ...
app.use('/api', materialsRouter);
```

(Place the `import` near the other route imports at the top; the `app.use` near line 152.)

- [ ] **Step 7: Re-run the test**

Run: `cd backend && DATABASE_URL=postgresql://localhost/smithnet npx jest src/__tests__/materials-routes.test.ts`
Expected: all 7 tests PASS.

- [ ] **Step 8: Full backend suite**

Run: `cd backend && DATABASE_URL=postgresql://localhost/smithnet npx jest`
Expected: all suites pass.

- [ ] **Step 9: Commit**

```bash
git add backend/src/schemas/materials.ts backend/src/materialsRoutes.ts \
        backend/src/jobsRoutes.ts backend/src/server.ts \
        backend/src/__tests__/materials-routes.test.ts
git commit -m "feat(materials): routes + zod + TDD tests

POST /api/materials (jobId in body), PATCH /api/materials/:id,
DELETE /api/materials/:id all guarded; GET /api/jobs/:id/materials
on jobsRouter (mirrors tasks pattern). 7 tests: 401, 403 tier,
full CRUD + toggle round-trip, cross-foreman 403, zod strict, negative
quantity rejected, ON DELETE CASCADE verified.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 5: Migration 033 + expensesService

**Files:**
- Create: `backend/migrations/033_job_expenses.sql`
- Create: `backend/src/expensesService.ts`

- [ ] **Step 1: Write the migration**

`backend/migrations/033_job_expenses.sql`:
```sql
-- Per-job BOL-style expenses. Category is free text; UI suggests slugs.
-- amount is flat (not unit-priced). expense_date is a calendar date.

CREATE TABLE IF NOT EXISTS job_expenses (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  job_id UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
  category TEXT NOT NULL,
  description TEXT NOT NULL,
  amount NUMERIC(10,2) NOT NULL DEFAULT 0,
  vendor TEXT,
  notes TEXT,
  expense_date DATE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_job_expenses_job ON job_expenses (job_id);
```

- [ ] **Step 2: Apply**

Run: `psql postgresql://localhost/smithnet -f backend/migrations/033_job_expenses.sql`
Expected: `CREATE TABLE`, `CREATE INDEX`. No errors.

- [ ] **Step 3: Verify**

Run: `psql postgresql://localhost/smithnet -c "\d job_expenses"`
Expected: all 10 columns, `job_id` NOT NULL with FK to `jobs(id)`, `idx_job_expenses_job` index.

- [ ] **Step 4: Write `expensesService.ts`**

`backend/src/expensesService.ts`:
```typescript
// backend/src/expensesService.ts
//
// Per-job expense lines. Mirrors materialsService.ts shape. Ownership at
// routes (requireJobOwner for create; requireExpenseOwner for patch/delete).

import { pg, isPgEnabled } from './db';
import { auditLog, AuditAction } from './auditLog';
import { v4 as uuidv4 } from 'uuid';

export interface Expense {
  id: string;
  jobId: string;
  category: string;
  description: string;
  amount: number;
  vendor: string | null;
  notes: string | null;
  expenseDate: string | null;  // ISO YYYY-MM-DD or null
  createdAt: Date;
  updatedAt: Date;
}

export class NotFoundError extends Error {
  constructor(message: string = 'Expense not found') {
    super(message);
    this.name = 'NotFoundError';
  }
}

function requirePg() {
  if (!isPgEnabled() || !pg) throw new Error('[ExpensesService] Postgres client not initialized');
  return pg;
}

function mapRow(row: any): Expense {
  return {
    id: row.id,
    jobId: row.job_id,
    category: row.category,
    description: row.description,
    amount: Number(row.amount),
    vendor: row.vendor,
    notes: row.notes,
    expenseDate: row.expense_date
      ? (row.expense_date instanceof Date
          ? row.expense_date.toISOString().slice(0, 10)
          : String(row.expense_date).slice(0, 10))
      : null,
    createdAt: new Date(row.created_at),
    updatedAt: new Date(row.updated_at),
  };
}

export async function listByJob(jobId: string): Promise<Expense[]> {
  const db = requirePg();
  const { rows } = await db.query(
    `SELECT * FROM job_expenses WHERE job_id = $1 ORDER BY created_at ASC`,
    [jobId],
  );
  return rows.map(mapRow);
}

export async function getById(id: string): Promise<Expense | null> {
  const db = requirePg();
  const { rows } = await db.query(`SELECT * FROM job_expenses WHERE id = $1`, [id]);
  return rows.length === 0 ? null : mapRow(rows[0]);
}

export interface CreateExpenseInput {
  jobId: string;
  category: string;
  description: string;
  amount: number;
  vendor?: string;
  notes?: string;
  expenseDate?: string;
}

export async function create(input: CreateExpenseInput, actorId: string): Promise<Expense> {
  const db = requirePg();
  const id = uuidv4();
  const { rows } = await db.query(
    `INSERT INTO job_expenses
       (id, job_id, category, description, amount, vendor, notes, expense_date)
     VALUES ($1, $2, $3, $4, $5, $6, $7, $8)
     RETURNING *`,
    [
      id, input.jobId, input.category, input.description, input.amount,
      input.vendor ?? null, input.notes ?? null, input.expenseDate ?? null,
    ],
  );
  const e = mapRow(rows[0]);
  await auditLog.log(AuditAction.EXPENSE_CREATED, actorId, {
    expenseId: e.id, jobId: e.jobId, category: e.category, amount: e.amount,
  });
  return e;
}

export interface UpdateExpensePatch {
  category?: string;
  description?: string;
  amount?: number;
  vendor?: string | null;
  notes?: string | null;
  expenseDate?: string | null;
}

export async function update(id: string, patch: UpdateExpensePatch, actorId: string): Promise<Expense | null> {
  const db = requirePg();
  const sets: string[] = [];
  const vals: any[] = [];
  let i = 1;
  for (const [col, key] of [
    ['category', 'category'], ['description', 'description'], ['amount', 'amount'],
    ['vendor', 'vendor'], ['notes', 'notes'], ['expense_date', 'expenseDate'],
  ] as const) {
    if (key in patch) { sets.push(`${col} = $${i++}`); vals.push((patch as any)[key]); }
  }
  if (sets.length === 0) return getById(id);
  sets.push(`updated_at = NOW()`);
  vals.push(id);
  const { rows } = await db.query(
    `UPDATE job_expenses SET ${sets.join(', ')} WHERE id = $${i} RETURNING *`,
    vals,
  );
  if (rows.length === 0) return null;
  const e = mapRow(rows[0]);
  await auditLog.log(AuditAction.EXPENSE_UPDATED, actorId, {
    expenseId: e.id, jobId: e.jobId, fields: Object.keys(patch),
  });
  return e;
}

export async function hardDelete(id: string, actorId: string): Promise<boolean> {
  const db = requirePg();
  const existing = await getById(id);
  if (!existing) return false;
  await db.query(`DELETE FROM job_expenses WHERE id = $1`, [id]);
  await auditLog.log(AuditAction.EXPENSE_DELETED, actorId, {
    expenseId: id, jobId: existing.jobId,
  });
  return true;
}
```

- [ ] **Step 5: Type-check**

Run: `cd backend && npx tsc --noEmit`
Expected: clean.

- [ ] **Step 6: Commit**

```bash
git add backend/migrations/033_job_expenses.sql backend/src/expensesService.ts
git commit -m "feat(expenses): migration 033 + expensesService

job_expenses table (category TEXT, description TEXT, amount NUMERIC,
vendor, notes, expense_date DATE) with ON DELETE CASCADE -> jobs.
Service mirrors materialsService shape (listByJob, getById, create,
update, hardDelete) with EXPENSE_* audit on every mutation.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 6: requireExpenseOwner + expensesRoutes + tests (TDD)

**Files:**
- Create: `backend/src/middleware/requireExpenseOwner.ts`
- Create: `backend/src/schemas/expenses.ts`
- Create: `backend/src/expensesRoutes.ts`
- Create: `backend/src/__tests__/expenses-routes.test.ts`
- Modify: `backend/src/jobsRoutes.ts` (add `GET /api/jobs/:id/expenses`)
- Modify: `backend/src/server.ts` (mount expensesRouter)

This combines structure-mirror tasks. The middleware + schemas are short and isomorphic to materials; the test file is also nearly identical with an `expenseDate` round-trip case added.

- [ ] **Step 1: Write `requireExpenseOwner.ts`**

`backend/src/middleware/requireExpenseOwner.ts`:
```typescript
import { Response, NextFunction } from 'express';
import { AuthenticatedRequest } from '../auth';
import * as expensesService from '../expensesService';
import * as jobsService from '../jobsService';

export interface ExpenseOwnerRequest extends AuthenticatedRequest {
  expense?: expensesService.Expense;
  job?: jobsService.Job;
}

export async function requireExpenseOwner(
  req: ExpenseOwnerRequest,
  res: Response,
  next: NextFunction,
) {
  const id = req.params.id;
  if (!id) return res.status(400).json({ error: 'Missing expense id' });
  try {
    const expense = await expensesService.getById(id);
    if (!expense) return res.status(404).json({ error: 'Expense not found' });
    const job = await jobsService.getById(expense.jobId);
    if (!job) return res.status(404).json({ error: 'Parent job not found' });
    if (job.foremanId !== req.user!.id) {
      return res.status(403).json({ error: 'Not the job foreman', code: 'not_owner' });
    }
    req.expense = expense;
    req.job = job;
    next();
  } catch (e: any) {
    return res.status(500).json({ error: 'Failed to load expense' });
  }
}
```

- [ ] **Step 2: Write `schemas/expenses.ts`**

```typescript
import { z } from 'zod';

const ISO_DATE = z.string().regex(/^\d{4}-\d{2}-\d{2}$/, 'expected YYYY-MM-DD');

export const CreateExpenseBody = z.object({
  jobId: z.string().uuid(),
  category: z.string().min(1).max(60),
  description: z.string().min(1).max(500),
  amount: z.number().nonnegative(),
  vendor: z.string().max(200).optional(),
  notes: z.string().max(2000).optional(),
  expenseDate: ISO_DATE.optional(),
}).strict();
export type CreateExpenseBody = z.infer<typeof CreateExpenseBody>;

export const UpdateExpenseBody = z.object({
  category: z.string().min(1).max(60).optional(),
  description: z.string().min(1).max(500).optional(),
  amount: z.number().nonnegative().optional(),
  vendor: z.string().max(200).nullable().optional(),
  notes: z.string().max(2000).nullable().optional(),
  expenseDate: ISO_DATE.nullable().optional(),
}).strict();
export type UpdateExpenseBody = z.infer<typeof UpdateExpenseBody>;
```

- [ ] **Step 3: Write the failing test file**

`backend/src/__tests__/expenses-routes.test.ts`:
```typescript
import express from 'express';
import request from 'supertest';
import cookieParser from 'cookie-parser';
import { authRouter } from '../authRoutes';
import { jobsRouter } from '../jobsRoutes';
import { expensesRouter } from '../expensesRoutes';
import { generateTokens, UserRole, userStore } from '../auth';
import { createUserAndProfile } from '../jobsService';
import { pg, isPgEnabled } from '../db';

const describeDb = isPgEnabled() ? describe : describe.skip;

function buildApp() {
  const app = express();
  app.use(express.json());
  app.use(cookieParser());
  app.use('/api/auth', authRouter);
  app.use('/api/jobs', jobsRouter);
  app.use('/api', expensesRouter);
  return app;
}

async function foreman(suffix: string): Promise<{ id: string; token: string }> {
  const user = await createUserAndProfile({
    email: `foreman-exp-${suffix}-${Date.now()}@example.com`,
    password: 'password123',
    displayName: `Foreman ${suffix}`,
    role: UserRole.FOREMAN,
  });
  const { accessToken } = await generateTokens(user);
  return { id: user.id, token: accessToken };
}

async function makeJob(token: string): Promise<string> {
  const res = await request(buildApp()).post('/api/jobs').set('Authorization', `Bearer ${token}`)
    .send({ title: 'exp-job' });
  return res.body.job.id;
}

describeDb('expenses routes', () => {
  const app = buildApp();

  afterEach(async () => {
    if (!isPgEnabled() || !pg) return;
    await pg.query(`DELETE FROM job_expenses WHERE job_id IN (
      SELECT id FROM jobs WHERE foreman_id IN (
        SELECT id FROM profiles WHERE email LIKE 'foreman-exp-%' OR email LIKE 'solo-exp-%'
      )
    )`);
    await pg.query(`DELETE FROM jobs WHERE foreman_id IN (
      SELECT id FROM profiles WHERE email LIKE 'foreman-exp-%' OR email LIKE 'solo-exp-%'
    )`);
    await pg.query(`DELETE FROM profiles WHERE email LIKE 'foreman-exp-%' OR email LIKE 'solo-exp-%'`);
    await pg.query(`DELETE FROM users WHERE email LIKE 'foreman-exp-%' OR email LIKE 'solo-exp-%'`);
  });
  afterAll(async () => { await pg?.end(); });

  it('401 without auth', async () => {
    const res = await request(app).post('/api/expenses').send({ jobId: 'x', category: 'fuel', description: 'gas', amount: 0 });
    expect(res.status).toBe(401);
  });

  it('403 tier_required for Solo', async () => {
    const u = await userStore.createUser(`solo-exp-${Date.now()}@example.com`, 'password123', 'Solo', UserRole.SOLO);
    const { accessToken } = await generateTokens(u);
    const res = await request(app).post('/api/expenses').set('Authorization', `Bearer ${accessToken}`)
      .send({ jobId: '00000000-0000-0000-0000-000000000000', category: 'fuel', description: 'x', amount: 0 });
    expect(res.status).toBe(403);
    expect(res.body.code).toBe('tier_required');
  });

  it('creates, lists, updates, deletes an expense with expense_date round-trip', async () => {
    const f = await foreman('crud');
    const jobId = await makeJob(f.token);
    const create = await request(app).post('/api/expenses').set('Authorization', `Bearer ${f.token}`)
      .send({ jobId, category: 'permit_fee', description: 'Electrical permit', amount: 175.50, expenseDate: '2026-05-20' });
    expect(create.status).toBe(201);
    expect(create.body.expense.expenseDate).toBe('2026-05-20');
    const id = create.body.expense.id;

    const list = await request(app).get(`/api/jobs/${jobId}/expenses`).set('Authorization', `Bearer ${f.token}`);
    expect(list.body.expenses).toHaveLength(1);

    const patch = await request(app).patch(`/api/expenses/${id}`).set('Authorization', `Bearer ${f.token}`)
      .send({ amount: 200.00 });
    expect(Number(patch.body.expense.amount)).toBe(200);

    const del = await request(app).delete(`/api/expenses/${id}`).set('Authorization', `Bearer ${f.token}`);
    expect(del.status).toBe(204);
  });

  it('isolates across foremen (403 not_owner)', async () => {
    const a = await foreman('iso-a');
    const b = await foreman('iso-b');
    const jobId = await makeJob(a.token);
    const create = await request(app).post('/api/expenses').set('Authorization', `Bearer ${a.token}`)
      .send({ jobId, category: 'fuel', description: 'gas', amount: 40 });
    const id = create.body.expense.id;
    const res = await request(app).patch(`/api/expenses/${id}`).set('Authorization', `Bearer ${b.token}`)
      .send({ amount: 1 });
    expect(res.status).toBe(403);
    expect(res.body.code).toBe('not_owner');
  });

  it('rejects unknown fields (zod strict)', async () => {
    const f = await foreman('strict');
    const jobId = await makeJob(f.token);
    const res = await request(app).post('/api/expenses').set('Authorization', `Bearer ${f.token}`)
      .send({ jobId, category: 'fuel', description: 'x', amount: 1, bogus: 1 });
    expect(res.status).toBe(400);
    expect(res.body.code).toBe('validation');
  });

  it('rejects bad expense_date format', async () => {
    const f = await foreman('date');
    const jobId = await makeJob(f.token);
    const res = await request(app).post('/api/expenses').set('Authorization', `Bearer ${f.token}`)
      .send({ jobId, category: 'fuel', description: 'x', amount: 1, expenseDate: 'May 20, 2026' });
    expect(res.status).toBe(400);
    expect(res.body.code).toBe('validation');
  });

  it('cascades on job delete', async () => {
    const f = await foreman('casc');
    const jobId = await makeJob(f.token);
    await request(app).post('/api/expenses').set('Authorization', `Bearer ${f.token}`)
      .send({ jobId, category: 'fuel', description: 'gas', amount: 40 });
    expect((await pg!.query(`SELECT COUNT(*) FROM job_expenses WHERE job_id = $1`, [jobId])).rows[0].count).toBe('1');
    await pg!.query(`DELETE FROM jobs WHERE id = $1`, [jobId]);
    expect((await pg!.query(`SELECT COUNT(*) FROM job_expenses WHERE job_id = $1`, [jobId])).rows[0].count).toBe('0');
  });
});
```

- [ ] **Step 4: Run the tests to verify they fail**

Run: `cd backend && DATABASE_URL=postgresql://localhost/smithnet npx jest src/__tests__/expenses-routes.test.ts`
Expected: tests fail (expensesRouter not found).

- [ ] **Step 5: Add LIST route to jobsRoutes.ts**

Immediately after the `GET /:id/materials` route added in T4:

```typescript
// GET /api/jobs/:id/expenses — per-job expense list (foreman of the job only)
jobsRouter.get('/:id/expenses', requireJobOwner, async (req: JobOwnerRequest, res: Response) => {
  try {
    const expenses = await expensesService.listByJob(req.job!.id);
    res.json({ expenses });
  } catch (e) {
    requestLogger().error({ event: 'jobs_list_expenses_error', err: e }, 'jobs list expenses error');
    res.status(500).json({ error: 'Failed to load expenses' });
  }
});
```

Extend the import at top:
```typescript
import * as expensesService from './expensesService';
```

- [ ] **Step 6: Write `expensesRoutes.ts`**

`backend/src/expensesRoutes.ts`:
```typescript
import { Router, Response } from 'express';
import { authenticateToken, AuthenticatedRequest } from './auth';
import { validateBody } from './middleware/validate';
import { requireConsoleTier } from './middleware/requireConsoleTier';
import { requireExpenseOwner, ExpenseOwnerRequest } from './middleware/requireExpenseOwner';
import { CreateExpenseBody, UpdateExpenseBody } from './schemas/expenses';
import * as expensesService from './expensesService';
import { requestLogger } from './logging';

export const expensesRouter = Router();

expensesRouter.use(authenticateToken, requireConsoleTier);

expensesRouter.post('/expenses', validateBody(CreateExpenseBody),
  async (req: AuthenticatedRequest, res: Response) => {
    try {
      const body = req.body as CreateExpenseBody;
      const job = await (await import('./jobsService')).getById(body.jobId);
      if (!job) return res.status(404).json({ error: 'Job not found' });
      if (job.foremanId !== req.user!.id) {
        return res.status(403).json({ error: 'Not the job foreman', code: 'not_owner' });
      }
      const expense = await expensesService.create(body, req.user!.id);
      res.status(201).json({ expense });
    } catch (e) {
      requestLogger().error({ event: 'expenses_create_error', err: e }, 'expenses create error');
      res.status(500).json({ error: 'Failed to create expense' });
    }
  });

expensesRouter.patch('/expenses/:id', requireExpenseOwner, validateBody(UpdateExpenseBody),
  async (req: ExpenseOwnerRequest, res: Response) => {
    try {
      const body = req.body as UpdateExpenseBody;
      const expense = await expensesService.update(req.expense!.id, body, req.user!.id);
      if (!expense) return res.status(404).json({ error: 'Expense not found' });
      res.json({ expense });
    } catch (e) {
      requestLogger().error({ event: 'expenses_update_error', err: e }, 'expenses update error');
      res.status(500).json({ error: 'Failed to update expense' });
    }
  });

expensesRouter.delete('/expenses/:id', requireExpenseOwner,
  async (req: ExpenseOwnerRequest, res: Response) => {
    try {
      await expensesService.hardDelete(req.expense!.id, req.user!.id);
      res.status(204).send();
    } catch (e) {
      requestLogger().error({ event: 'expenses_delete_error', err: e }, 'expenses delete error');
      res.status(500).json({ error: 'Failed to delete expense' });
    }
  });
```

- [ ] **Step 7: Mount in server.ts**

After the materialsRouter mount:
```typescript
import { expensesRouter } from './expensesRoutes';
// ...
app.use('/api', expensesRouter);
```

- [ ] **Step 8: Re-run tests + full backend suite**

Run: `cd backend && DATABASE_URL=postgresql://localhost/smithnet npx jest`
Expected: all suites pass, including 7 new expenses tests.

- [ ] **Step 9: Commit**

```bash
git add backend/src/middleware/requireExpenseOwner.ts \
        backend/src/schemas/expenses.ts \
        backend/src/expensesRoutes.ts \
        backend/src/jobsRoutes.ts backend/src/server.ts \
        backend/src/__tests__/expenses-routes.test.ts
git commit -m "feat(expenses): requireExpenseOwner + routes + zod + TDD tests

POST /api/expenses (jobId in body), PATCH/DELETE /api/expenses/:id;
GET /api/jobs/:id/expenses on jobsRouter. 7 tests: 401, 403 tier,
CRUD with expense_date YYYY-MM-DD round-trip, cross-foreman 403,
zod strict, bad-date format rejected, ON DELETE CASCADE.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 7: Portal materialsClient + materialsStore + MSW handlers

**Files:**
- Create: `desktop/portal/src/console/api/materialsClient.ts`
- Create: `desktop/portal/src/console/stores/materialsStore.ts`
- Modify: `desktop/portal/src/console/test/msw-handlers.ts`

- [ ] **Step 1: Write `materialsClient.ts`**

```typescript
// desktop/portal/src/console/api/materialsClient.ts
// Mirrors tasksClient shape: typed result + thin wrapper around fetch.

export interface Material {
  id: string;
  jobId: string;
  name: string;
  notes: string | null;
  checked: boolean;
  checkedAt: string | null;
  quantity: number;
  unit: string;
  unitCost: number;
  vendor: string | null;
  createdAt: string;
  updatedAt: string;
}

export type MaterialsResult<T> =
  | ({ ok: true } & T)
  | { ok: false; status: number; error: string; details?: any; code?: string };

async function call<T>(path: string, opts: { method?: string; body?: any } = {}): Promise<MaterialsResult<T>> {
  const res = await fetch(path, {
    method: opts.method ?? 'GET',
    headers: { 'Content-Type': 'application/json' },
    credentials: 'include',
    body: opts.body !== undefined ? JSON.stringify(opts.body) : undefined,
  });
  if (res.status === 204) return { ok: true } as MaterialsResult<T>;
  if (!res.ok) {
    const errBody = await res.json().catch(() => ({ error: res.statusText }));
    return { ok: false, status: res.status, error: errBody.error ?? res.statusText, details: errBody.details, code: errBody.code };
  }
  const data = (await res.json()) as T;
  return { ok: true, ...data } as MaterialsResult<T>;
}

export interface CreateMaterialInput {
  jobId: string;
  name: string;
  notes?: string;
  quantity?: number;
  unit?: string;
  unitCost?: number;
  vendor?: string;
}

export interface UpdateMaterialInput {
  name?: string;
  notes?: string | null;
  quantity?: number;
  unit?: string;
  unitCost?: number;
  vendor?: string | null;
  checked?: boolean;
}

export const materialsClient = {
  listForJob: (jobId: string) =>
    call<{ materials: Material[] }>(`/api/jobs/${encodeURIComponent(jobId)}/materials`),
  create: (input: CreateMaterialInput) =>
    call<{ material: Material }>(`/api/materials`, { method: 'POST', body: input }),
  update: (id: string, patch: UpdateMaterialInput) =>
    call<{ material: Material }>(`/api/materials/${encodeURIComponent(id)}`, { method: 'PATCH', body: patch }),
  delete: (id: string) =>
    call<Record<string, never>>(`/api/materials/${encodeURIComponent(id)}`, { method: 'DELETE' }),
};
```

- [ ] **Step 2: Write `materialsStore.ts`**

```typescript
// desktop/portal/src/console/stores/materialsStore.ts
import { create } from 'zustand';
import type { Material } from '../api/materialsClient';

interface MaterialsStore {
  byJob: Record<string, Material[]>;
  setForJob: (jobId: string, items: Material[]) => void;
  upsert: (jobId: string, item: Material) => void;
  remove: (jobId: string, id: string) => void;
  clear: () => void;
}

export const useMaterialsStore = create<MaterialsStore>((set) => ({
  byJob: {},
  setForJob: (jobId, items) => set((s) => ({ byJob: { ...s.byJob, [jobId]: items } })),
  upsert: (jobId, item) => set((s) => {
    const list = s.byJob[jobId] ?? [];
    const idx = list.findIndex((m) => m.id === item.id);
    const next = idx === -1 ? [...list, item] : list.map((m, i) => (i === idx ? item : m));
    return { byJob: { ...s.byJob, [jobId]: next } };
  }),
  remove: (jobId, id) => set((s) => ({
    byJob: { ...s.byJob, [jobId]: (s.byJob[jobId] ?? []).filter((m) => m.id !== id) },
  })),
  clear: () => set({ byJob: {} }),
}));
```

- [ ] **Step 3: Add MSW handlers**

In `desktop/portal/src/console/test/msw-handlers.ts`, near the existing job handlers, add:

```typescript
http.get('/api/jobs/:jobId/materials', () => HttpResponse.json({ materials: [] })),

http.post('/api/materials', async ({ request }) => {
  const body = (await request.json()) as any;
  return HttpResponse.json({
    material: {
      id: 'm-mock', jobId: body.jobId, name: body.name,
      notes: body.notes ?? null, checked: false, checkedAt: null,
      quantity: body.quantity ?? 1, unit: body.unit ?? 'ea',
      unitCost: body.unitCost ?? 0, vendor: body.vendor ?? null,
      createdAt: '2026-05-26T10:00:00Z', updatedAt: '2026-05-26T10:00:00Z',
    },
  }, { status: 201 });
}),

http.patch('/api/materials/:id', async ({ params, request }) => {
  const body = (await request.json()) as any;
  return HttpResponse.json({
    material: {
      id: params.id, jobId: 'j-mock', name: body.name ?? 'Mock material',
      notes: body.notes ?? null,
      checked: body.checked ?? false,
      checkedAt: body.checked ? '2026-05-26T11:00:00Z' : null,
      quantity: body.quantity ?? 1, unit: body.unit ?? 'ea',
      unitCost: body.unitCost ?? 0, vendor: body.vendor ?? null,
      createdAt: '2026-05-26T10:00:00Z', updatedAt: '2026-05-26T11:00:00Z',
    },
  });
}),

http.delete('/api/materials/:id', () => new HttpResponse(null, { status: 204 })),
```

- [ ] **Step 4: tsc + portal tests**

Run:
```bash
cd desktop/portal
npx tsc --noEmit
npm run test:run
```
Expected: clean.

- [ ] **Step 5: Commit**

```bash
git add desktop/portal/src/console/api/materialsClient.ts \
        desktop/portal/src/console/stores/materialsStore.ts \
        desktop/portal/src/console/test/msw-handlers.ts
git commit -m "feat(portal): materialsClient + materialsStore + MSW handlers

materialsClient: listForJob/create/update/delete, MaterialsResult
discriminated union mirroring tasksClient. materialsStore: zustand
byJob map with setForJob/upsert/remove/clear. 4 MSW handlers for
the test suite.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 8: MaterialsList component + test

**Files:**
- Create: `desktop/portal/src/console/components/materials/MaterialsList.tsx`
- Create: `desktop/portal/src/console/components/materials/__tests__/MaterialsList.test.tsx`

- [ ] **Step 1: Write the failing test**

`desktop/portal/src/console/components/materials/__tests__/MaterialsList.test.tsx`:
```typescript
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, beforeEach } from 'vitest';
import { MaterialsList } from '../MaterialsList';
import { useMaterialsStore } from '../../../stores/materialsStore';
import type { Material } from '../../../api/materialsClient';

function mat(id: string, name: string, qty = 1, unitCost = 0, checked = false): Material {
  return {
    id, jobId: 'j1', name, notes: null,
    checked, checkedAt: checked ? '2026-05-26T11:00:00Z' : null,
    quantity: qty, unit: 'ea', unitCost, vendor: null,
    createdAt: '2026-05-26T10:00:00Z', updatedAt: '2026-05-26T10:00:00Z',
  };
}

describe('MaterialsList', () => {
  beforeEach(() => useMaterialsStore.getState().clear());

  it('renders empty state when no materials', () => {
    render(<MaterialsList jobId="j1" />);
    expect(screen.getByText(/no materials yet/i)).toBeInTheDocument();
  });

  it('renders materials with line costs and subtotal', () => {
    useMaterialsStore.getState().setForJob('j1', [
      mat('a', '10/2 Romex', 50, 0.85),
      mat('b', 'Box of staples', 1, 4.50),
    ]);
    render(<MaterialsList jobId="j1" />);
    expect(screen.getByText('10/2 Romex')).toBeInTheDocument();
    expect(screen.getByText('Box of staples')).toBeInTheDocument();
    // Subtotal: 50 * 0.85 + 1 * 4.50 = 47.00
    expect(screen.getByText(/Materials: \$47\.00/)).toBeInTheDocument();
  });

  it('toggling the checkbox calls update and reflects in store', async () => {
    useMaterialsStore.getState().setForJob('j1', [mat('a', 'X', 1, 0)]);
    render(<MaterialsList jobId="j1" />);
    const cb = screen.getByRole('checkbox');
    fireEvent.click(cb);
    await waitFor(() => {
      const m = useMaterialsStore.getState().byJob['j1']?.[0];
      expect(m?.checked).toBe(true);
    });
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd desktop/portal && npm run test:run -- MaterialsList`
Expected: module-not-found failure.

- [ ] **Step 3: Implement `MaterialsList.tsx`**

```typescript
// desktop/portal/src/console/components/materials/MaterialsList.tsx
import { useEffect, useState } from 'react';
import { materialsClient } from '../../api/materialsClient';
import type { Material } from '../../api/materialsClient';
import { useMaterialsStore } from '../../stores/materialsStore';
import { useToast } from '../../hooks/useToast';
import { Button } from '../ui/Button';
import { AddMaterialModal } from './AddMaterialModal';

const USD = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });

export function MaterialsList({ jobId }: { jobId: string }) {
  const items = useMaterialsStore((s) => s.byJob[jobId] ?? []);
  const setForJob = useMaterialsStore((s) => s.setForJob);
  const upsert = useMaterialsStore((s) => s.upsert);
  const remove = useMaterialsStore((s) => s.remove);
  const toast = useToast();
  const [showAdd, setShowAdd] = useState(false);
  const [editing, setEditing] = useState<Material | null>(null);

  useEffect(() => {
    materialsClient.listForJob(jobId).then((r) => {
      if (r.ok) setForJob(jobId, r.materials);
    });
  }, [jobId, setForJob]);

  async function toggle(m: Material) {
    const prev = m.checked;
    upsert(jobId, { ...m, checked: !prev });
    const r = await materialsClient.update(m.id, { checked: !prev });
    if (r.ok) {
      upsert(jobId, r.material);
    } else {
      upsert(jobId, m); // rollback
      toast.error(r.error || 'Failed to update');
    }
  }

  async function del(m: Material) {
    if (!window.confirm(`Delete "${m.name}"?`)) return;
    const r = await materialsClient.delete(m.id);
    if (r.ok) { remove(jobId, m.id); toast.info('Material deleted'); }
    else toast.error(r.error || 'Failed to delete');
  }

  const subtotal = items.reduce((s, m) => s + m.quantity * m.unitCost, 0);

  return (
    <section className="font-mono mb-4">
      <header className="flex items-center justify-between mb-2">
        <h2 className="text-console-text text-sm uppercase tracking-wider">Materials ({items.filter((m) => m.checked).length}/{items.length})</h2>
        <Button variant="secondary" onClick={() => { setEditing(null); setShowAdd(true); }}>+ Add material</Button>
      </header>
      {items.length === 0 ? (
        <div className="text-console-text-muted text-sm py-2">No materials yet.</div>
      ) : (
        <div className="border border-console-border">
          {items.map((m) => (
            <div key={m.id} className="flex items-center gap-2 px-3 py-2 border-b border-console-border last:border-b-0 text-sm">
              <input
                type="checkbox"
                checked={m.checked}
                onChange={() => toggle(m)}
                className="cursor-pointer"
                aria-label={`Toggle ${m.name}`}
              />
              <div className="flex-1 min-w-0">
                <div className={m.checked ? 'line-through text-console-text-muted' : 'text-console-text'}>{m.name}</div>
                <div className="text-xs text-console-text-muted">
                  {m.quantity} {m.unit} @ {USD.format(m.unitCost)}
                  {m.vendor ? ` - ${m.vendor}` : ''}
                </div>
              </div>
              <div className="text-console-text tabular-nums">{USD.format(m.quantity * m.unitCost)}</div>
              <button onClick={() => { setEditing(m); setShowAdd(true); }} className="text-xs text-console-text-muted hover:text-console-text">[edit]</button>
              <button onClick={() => del(m)} className="text-xs text-console-text-muted hover:text-console-warn">[delete]</button>
            </div>
          ))}
          <div className="px-3 py-2 text-right text-console-text font-bold border-t-2 border-console-border">
            Materials: {USD.format(subtotal)}
          </div>
        </div>
      )}
      <AddMaterialModal
        open={showAdd}
        onClose={() => setShowAdd(false)}
        jobId={jobId}
        editing={editing}
      />
    </section>
  );
}
```

- [ ] **Step 4: Re-run the tests**

Note: the test will require `AddMaterialModal` to at least exist (component imports it). Stub it temporarily as an empty default-export component so the test passes; T9 replaces the stub with the real implementation.

Create `desktop/portal/src/console/components/materials/AddMaterialModal.tsx` with a temporary stub:
```typescript
export function AddMaterialModal(_: any) { return null; }
```

Run: `cd desktop/portal && npm run test:run -- MaterialsList`
Expected: 3 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add desktop/portal/src/console/components/materials/MaterialsList.tsx \
        desktop/portal/src/console/components/materials/AddMaterialModal.tsx \
        desktop/portal/src/console/components/materials/__tests__/MaterialsList.test.tsx
git commit -m "feat(portal): MaterialsList + AddMaterialModal stub

MaterialsList: per-job checklist with toggle (optimistic + rollback
on error), line costs, subtotal, edit/delete actions. Empty state.
AddMaterialModal stub returned null so the import resolves; real
modal lands in next commit.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 9: AddMaterialModal (real implementation) + test

**Files:**
- Modify: `desktop/portal/src/console/components/materials/AddMaterialModal.tsx`
- Create: `desktop/portal/src/console/components/materials/__tests__/AddMaterialModal.test.tsx`

- [ ] **Step 1: Write the failing test**

```typescript
// desktop/portal/src/console/components/materials/__tests__/AddMaterialModal.test.tsx
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, beforeEach } from 'vitest';
import { AddMaterialModal } from '../AddMaterialModal';
import { useMaterialsStore } from '../../../stores/materialsStore';

describe('AddMaterialModal', () => {
  beforeEach(() => useMaterialsStore.getState().clear());

  it('submits create with defaults', async () => {
    render(<AddMaterialModal open jobId="j1" editing={null} onClose={() => {}} />);
    fireEvent.change(screen.getByPlaceholderText(/material name/i), { target: { value: '12-gauge wire' } });
    fireEvent.click(screen.getByRole('button', { name: /save/i }));
    await waitFor(() => {
      const m = useMaterialsStore.getState().byJob['j1']?.[0];
      expect(m?.name).toBe('12-gauge wire');
    });
  });

  it('prefills when editing', () => {
    const editing = {
      id: 'm1', jobId: 'j1', name: 'Pre', notes: 'n',
      checked: false, checkedAt: null,
      quantity: 5, unit: 'ft', unitCost: 1.25, vendor: 'V',
      createdAt: '', updatedAt: '',
    };
    render(<AddMaterialModal open jobId="j1" editing={editing} onClose={() => {}} />);
    expect(screen.getByDisplayValue('Pre')).toBeInTheDocument();
    expect(screen.getByDisplayValue('5')).toBeInTheDocument();
    expect(screen.getByDisplayValue('ft')).toBeInTheDocument();
    expect(screen.getByDisplayValue('1.25')).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd desktop/portal && npm run test:run -- AddMaterialModal`
Expected: tests fail (stub doesn't render anything).

- [ ] **Step 3: Replace the stub with the real implementation**

Overwrite `desktop/portal/src/console/components/materials/AddMaterialModal.tsx`:

```typescript
import { useState, useEffect, FormEvent } from 'react';
import { Modal } from '../ui/Modal';
import { Button } from '../ui/Button';
import { materialsClient } from '../../api/materialsClient';
import type { Material } from '../../api/materialsClient';
import { useMaterialsStore } from '../../stores/materialsStore';
import { useToast } from '../../hooks/useToast';

const UNIT_SUGGESTIONS = ['ea', 'ft', 'lot', 'hr', 'gal', 'bag', 'box'];

interface Props {
  open: boolean;
  jobId: string;
  editing: Material | null;
  onClose: () => void;
}

export function AddMaterialModal({ open, jobId, editing, onClose }: Props) {
  const upsert = useMaterialsStore((s) => s.upsert);
  const toast = useToast();
  const [name, setName] = useState('');
  const [quantity, setQuantity] = useState('1');
  const [unit, setUnit] = useState('ea');
  const [unitCost, setUnitCost] = useState('0');
  const [vendor, setVendor] = useState('');
  const [notes, setNotes] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (editing) {
      setName(editing.name);
      setQuantity(String(editing.quantity));
      setUnit(editing.unit);
      setUnitCost(String(editing.unitCost));
      setVendor(editing.vendor ?? '');
      setNotes(editing.notes ?? '');
    } else {
      setName(''); setQuantity('1'); setUnit('ea');
      setUnitCost('0'); setVendor(''); setNotes('');
    }
  }, [editing, open]);

  async function handleSubmit(e: FormEvent) {
    e.preventDefault();
    if (!name.trim() || busy) return;
    setBusy(true);
    const payload = {
      name: name.trim(),
      quantity: Number(quantity) || 0,
      unit: unit.trim() || 'ea',
      unitCost: Number(unitCost) || 0,
      vendor: vendor.trim() || undefined,
      notes: notes.trim() || undefined,
    };
    const r = editing
      ? await materialsClient.update(editing.id, payload)
      : await materialsClient.create({ jobId, ...payload });
    setBusy(false);
    if (r.ok) {
      upsert(jobId, r.material);
      toast.info(editing ? 'Material updated' : 'Material added');
      onClose();
    } else {
      toast.error(r.error || 'Failed to save material');
    }
  }

  return (
    <Modal open={open} onClose={onClose} title={editing ? 'Edit material' : 'Add material'}>
      <form onSubmit={handleSubmit} className="w-full sm:w-[420px] max-w-full flex flex-col gap-2 font-mono text-sm">
        <input value={name} onChange={(e) => setName(e.target.value)}
          placeholder="Material name" autoFocus required
          className="bg-console-bg border border-console-border rounded px-2 py-1 text-console-text focus:border-console-accent outline-none" />
        <div className="flex gap-2">
          <input value={quantity} onChange={(e) => setQuantity(e.target.value)}
            type="number" min="0" step="0.01" placeholder="qty"
            className="w-24 bg-console-bg border border-console-border rounded px-2 py-1 text-console-text focus:border-console-accent outline-none" />
          <input value={unit} onChange={(e) => setUnit(e.target.value)}
            list="unit-suggestions" placeholder="unit"
            className="w-24 bg-console-bg border border-console-border rounded px-2 py-1 text-console-text focus:border-console-accent outline-none" />
          <datalist id="unit-suggestions">
            {UNIT_SUGGESTIONS.map((u) => <option key={u} value={u} />)}
          </datalist>
          <input value={unitCost} onChange={(e) => setUnitCost(e.target.value)}
            type="number" min="0" step="0.01" placeholder="unit cost"
            className="flex-1 bg-console-bg border border-console-border rounded px-2 py-1 text-console-text focus:border-console-accent outline-none" />
        </div>
        <input value={vendor} onChange={(e) => setVendor(e.target.value)}
          placeholder="Vendor (optional)"
          className="bg-console-bg border border-console-border rounded px-2 py-1 text-console-text focus:border-console-accent outline-none" />
        <textarea value={notes} onChange={(e) => setNotes(e.target.value)}
          placeholder="Notes (optional)" rows={2}
          className="bg-console-bg border border-console-border rounded px-2 py-1 text-console-text focus:border-console-accent outline-none" />
        <div className="flex gap-2 justify-end pt-2">
          <Button type="button" variant="secondary" onClick={onClose}>Cancel</Button>
          <Button type="submit" disabled={busy}>Save</Button>
        </div>
      </form>
    </Modal>
  );
}
```

- [ ] **Step 4: Re-run tests**

Run: `cd desktop/portal && npm run test:run -- AddMaterialModal`
Expected: both tests PASS.

- [ ] **Step 5: Commit**

```bash
git add desktop/portal/src/console/components/materials/AddMaterialModal.tsx \
        desktop/portal/src/console/components/materials/__tests__/AddMaterialModal.test.tsx
git commit -m "feat(portal): AddMaterialModal -- create/edit with unit suggestions

Form modal mirroring CreateClientModal pattern. Pre-fills from
editing prop; datalist suggestions for unit (ea/ft/lot/hr/gal/bag/box).
On submit calls create or update -> upsertJob -> toast.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 10: Portal expensesClient + expensesStore + MSW handlers

**Files:**
- Create: `desktop/portal/src/console/api/expensesClient.ts`
- Create: `desktop/portal/src/console/stores/expensesStore.ts`
- Modify: `desktop/portal/src/console/test/msw-handlers.ts`

Identical shape to T7. The implementer mirrors materialsClient/Store; the only differences are the `Expense` shape (category/description/amount/vendor/notes/expenseDate vs material's fields) and the URL paths.

- [ ] **Step 1: Write `expensesClient.ts`**

(Mirror `materialsClient.ts`. The `Expense` interface fields: `id, jobId, category, description, amount, vendor, notes, expenseDate, createdAt, updatedAt` — all `string` except `amount: number`. `CreateExpenseInput`: `jobId, category, description, amount, vendor?, notes?, expenseDate?`. `UpdateExpenseInput`: all of the above optional, with `vendor/notes/expenseDate` nullable. Method names: `listForJob, create, update, delete`. Paths: `/api/jobs/:jobId/expenses` for list; `/api/expenses` for create; `/api/expenses/:id` for patch/delete.)

- [ ] **Step 2: Write `expensesStore.ts`**

(Mirror `materialsStore.ts` — `byJob: Record<string, Expense[]>`, same actions.)

- [ ] **Step 3: Add MSW handlers**

3 handlers mirroring the materials ones, but on the expenses paths. Mock expense payload: `{ id, jobId, category, description, amount, vendor, notes, expenseDate, createdAt, updatedAt }`.

- [ ] **Step 4: tsc + tests**

```bash
cd desktop/portal && npx tsc --noEmit && npm run test:run
```
Expected: clean.

- [ ] **Step 5: Commit**

```bash
git add desktop/portal/src/console/api/expensesClient.ts \
        desktop/portal/src/console/stores/expensesStore.ts \
        desktop/portal/src/console/test/msw-handlers.ts
git commit -m "feat(portal): expensesClient + expensesStore + MSW handlers

Mirrors materialsClient/Store shape. Expense fields: category,
description, amount, vendor, notes, expenseDate. 4 MSW handlers.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 11: ExpensesTable + test

**Files:**
- Create: `desktop/portal/src/console/components/expenses/ExpensesTable.tsx`
- Create: `desktop/portal/src/console/components/expenses/AddExpenseModal.tsx` (stub)
- Create: `desktop/portal/src/console/components/expenses/__tests__/ExpensesTable.test.tsx`

Mirrors T8 (MaterialsList) structure.

- [ ] **Step 1: Write the failing test**

Mirror `MaterialsList.test.tsx`:
- Empty state
- Renders category/description/amount/vendor/date cells
- Subtotal correct (`Expenses: $X.XX`)
- Edit/delete buttons

- [ ] **Step 2: Run the test, verify it fails**

- [ ] **Step 3: Implement `ExpensesTable.tsx`**

Same structure as MaterialsList, but rendered as a `<table>` with columns: Category | Description | Amount | Vendor | Date | Actions. Each row links to edit (opens AddExpenseModal with prefill) and delete (window.confirm). Subtotal in the table footer. Empty state.

Use the same `USD = new Intl.NumberFormat(...)` helper.

- [ ] **Step 4: Create the AddExpenseModal stub** (T12 replaces)

```typescript
export function AddExpenseModal(_: any) { return null; }
```

- [ ] **Step 5: Run the test**

Run: `cd desktop/portal && npm run test:run -- ExpensesTable`
Expected: tests PASS.

- [ ] **Step 6: Commit**

```bash
git add desktop/portal/src/console/components/expenses/ExpensesTable.tsx \
        desktop/portal/src/console/components/expenses/AddExpenseModal.tsx \
        desktop/portal/src/console/components/expenses/__tests__/ExpensesTable.test.tsx
git commit -m "feat(portal): ExpensesTable + AddExpenseModal stub

Per-job expense table with category/description/amount/vendor/date
columns, subtotal, edit/delete actions. AddExpenseModal stub for
import resolution; real impl in next commit.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 12: AddExpenseModal (real) + test

**Files:**
- Modify: `desktop/portal/src/console/components/expenses/AddExpenseModal.tsx`
- Create: `desktop/portal/src/console/components/expenses/__tests__/AddExpenseModal.test.tsx`

Mirrors T9. Categories datalist: `material, permit_fee, fuel, subcontractor, equipment_rental, other`.

- [ ] **Step 1: Write the failing test**

Same structure as `AddMaterialModal.test.tsx`:
- Submits create with defaults
- Prefills when editing
- Datalist `<option>` values present in the rendered DOM

- [ ] **Step 2: Run the test, verify it fails**

- [ ] **Step 3: Replace the stub with real implementation**

Same structure as `AddMaterialModal.tsx`. Fields: category (with `<datalist>` of `EXPENSE_CATEGORY_SUGGESTIONS`), description, amount, vendor (optional), expense date (`type="date"`), notes (optional). The form submits via `expensesClient.create` (or `update` if editing) and calls `useExpensesStore.upsert`.

```typescript
const EXPENSE_CATEGORY_SUGGESTIONS = ['material', 'permit_fee', 'fuel', 'subcontractor', 'equipment_rental', 'other'];
```

- [ ] **Step 4: Re-run tests**

- [ ] **Step 5: Commit**

```bash
git add desktop/portal/src/console/components/expenses/AddExpenseModal.tsx \
        desktop/portal/src/console/components/expenses/__tests__/AddExpenseModal.test.tsx
git commit -m "feat(portal): AddExpenseModal -- create/edit with category suggestions

Form modal with datalist for category (material/permit_fee/fuel/
subcontractor/equipment_rental/other). expense_date uses native
type='date' input. Submits via expensesClient + upsertExpense + toast.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 13: JobCostRollup component + test

**Files:**
- Create: `desktop/portal/src/console/components/jobs/JobCostRollup.tsx`
- Create: `desktop/portal/src/console/components/jobs/__tests__/JobCostRollup.test.tsx`

- [ ] **Step 1: Write the failing test**

```typescript
import { render, screen } from '@testing-library/react';
import { describe, it, expect, beforeEach } from 'vitest';
import { JobCostRollup } from '../JobCostRollup';
import { useMaterialsStore } from '../../../stores/materialsStore';
import { useExpensesStore } from '../../../stores/expensesStore';

describe('JobCostRollup', () => {
  beforeEach(() => {
    useMaterialsStore.getState().clear();
    useExpensesStore.getState().clear();
  });

  it('sums materials and expenses into a job total', () => {
    useMaterialsStore.getState().setForJob('j1', [
      { id: 'a', jobId: 'j1', name: 'X', notes: null, checked: false, checkedAt: null,
        quantity: 2, unit: 'ea', unitCost: 10, vendor: null,
        createdAt: '', updatedAt: '' },
    ]);
    useExpensesStore.getState().setForJob('j1', [
      { id: 'e1', jobId: 'j1', category: 'fuel', description: 'gas', amount: 30,
        vendor: null, notes: null, expenseDate: null, createdAt: '', updatedAt: '' },
    ]);
    render(<JobCostRollup jobId="j1" />);
    expect(screen.getByText(/Materials: \$20\.00/)).toBeInTheDocument();
    expect(screen.getByText(/Expenses: \$30\.00/)).toBeInTheDocument();
    expect(screen.getByText(/Job total: \$50\.00/)).toBeInTheDocument();
  });

  it('renders zeros when stores are empty', () => {
    render(<JobCostRollup jobId="j1" />);
    expect(screen.getByText(/Job total: \$0\.00/)).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

- [ ] **Step 3: Implement `JobCostRollup.tsx`**

```typescript
// desktop/portal/src/console/components/jobs/JobCostRollup.tsx
import { useMaterialsStore } from '../../stores/materialsStore';
import { useExpensesStore } from '../../stores/expensesStore';

const USD = new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' });

export function JobCostRollup({ jobId }: { jobId: string }) {
  const materials = useMaterialsStore((s) => s.byJob[jobId] ?? []);
  const expenses = useExpensesStore((s) => s.byJob[jobId] ?? []);
  const mTotal = materials.reduce((s, m) => s + m.quantity * m.unitCost, 0);
  const eTotal = expenses.reduce((s, e) => s + e.amount, 0);
  const total = mTotal + eTotal;
  return (
    <section className="font-mono mb-4 border border-console-border bg-console-surface p-3">
      <div className="text-sm flex justify-between">
        <span className="text-console-text-muted">Materials:</span>
        <span className="text-console-text tabular-nums">{USD.format(mTotal)}</span>
      </div>
      <div className="text-sm flex justify-between">
        <span className="text-console-text-muted">Expenses:</span>
        <span className="text-console-text tabular-nums">{USD.format(eTotal)}</span>
      </div>
      <div className="mt-2 pt-2 border-t border-console-border text-sm flex justify-between font-bold">
        <span className="text-console-text">Job total:</span>
        <span className="text-console-accent tabular-nums">{USD.format(total)}</span>
      </div>
    </section>
  );
}
```

- [ ] **Step 4: Re-run tests**

Expected: both tests PASS.

- [ ] **Step 5: Commit**

```bash
git add desktop/portal/src/console/components/jobs/JobCostRollup.tsx \
        desktop/portal/src/console/components/jobs/__tests__/JobCostRollup.test.tsx
git commit -m "feat(portal): JobCostRollup -- Materials/Expenses/Job total

Subscribes to materialsStore + expensesStore by job. Recomputes on
change. USD formatting via Intl.NumberFormat. Tabular-nums for
right-aligned amounts.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 14: JobStageControls REVIEW warning + test

**Files:**
- Modify: `desktop/portal/src/console/components/jobs/JobStageControls.tsx`
- Modify: `desktop/portal/src/console/components/jobs/__tests__/JobStageControls.test.tsx`

- [ ] **Step 1: Extend the existing test file**

Add to the existing `describe('JobStageControls', ...)` block:

```typescript
it('shows the unchecked-materials warning when stage is review', () => {
  useMaterialsStore.getState().setForJob('j1', [
    { id: 'a', jobId: 'j1', name: 'X', notes: null, checked: false, checkedAt: null,
      quantity: 1, unit: 'ea', unitCost: 0, vendor: null, createdAt: '', updatedAt: '' },
    { id: 'b', jobId: 'j1', name: 'Y', notes: null, checked: false, checkedAt: null,
      quantity: 1, unit: 'ea', unitCost: 0, vendor: null, createdAt: '', updatedAt: '' },
    { id: 'c', jobId: 'j1', name: 'Z', notes: null, checked: true, checkedAt: '2026-05-26T11:00:00Z',
      quantity: 1, unit: 'ea', unitCost: 0, vendor: null, createdAt: '', updatedAt: '' },
  ]);
  render(<JobStageControls job={mockJob('review')} />);
  expect(screen.getByText(/2 materials not checked off/)).toBeInTheDocument();
  // Warning is non-blocking: button remains enabled
  expect(screen.getByRole('button', { name: /generate invoice/i })).toBeEnabled();
});

it('does not show the warning when stage is not review', () => {
  useMaterialsStore.getState().setForJob('j1', [
    { id: 'a', jobId: 'j1', name: 'X', notes: null, checked: false, checkedAt: null,
      quantity: 1, unit: 'ea', unitCost: 0, vendor: null, createdAt: '', updatedAt: '' },
  ]);
  render(<JobStageControls job={mockJob('in_progress')} />);
  expect(screen.queryByText(/not checked off/)).not.toBeInTheDocument();
});
```

Also import the store at the top of the test file:
```typescript
import { useMaterialsStore } from '../../../stores/materialsStore';
```

And reset it in `beforeEach`:
```typescript
beforeEach(() => {
  useJobsStore.getState().clear();
  useMaterialsStore.getState().clear();
});
```

- [ ] **Step 2: Run the test to verify the new cases fail**

Run: `cd desktop/portal && npm run test:run -- JobStageControls`
Expected: 2 new tests fail.

- [ ] **Step 3: Modify `JobStageControls.tsx`**

At the top, import the store:
```typescript
import { useMaterialsStore } from '../../stores/materialsStore';
```

Inside the component, before the `transitions` const:
```typescript
const materials = useMaterialsStore((s) => s.byJob[job.id] ?? []);
const uncheckedCount = materials.filter((m) => !m.checked).length;
const showReviewWarning = job.stage === 'review' && uncheckedCount > 0;
```

In the returned JSX, add a warning line above the buttons map:
```tsx
{showReviewWarning && (
  <div className="text-console-warn text-xs mb-2" role="alert">
    ! {uncheckedCount} materials not checked off
  </div>
)}
```

- [ ] **Step 4: Re-run tests**

Expected: all 10 JobStageControls tests pass (8 original + 2 new).

- [ ] **Step 5: Commit**

```bash
git add desktop/portal/src/console/components/jobs/JobStageControls.tsx \
        desktop/portal/src/console/components/jobs/__tests__/JobStageControls.test.tsx
git commit -m "feat(portal): JobStageControls -- non-blocking review-stage warning

When stage is 'review' and >=1 unchecked material exists, render
'! N materials not checked off' above the button row. Non-blocking
(transition button stays enabled). Wording mirrors APK
JobPipelineScreen.kt:446.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 15: JobDetailRoute mount + route test extension

**Files:**
- Modify: `desktop/portal/src/console/routes/JobDetailRoute.tsx`
- Modify: `desktop/portal/src/console/routes/__tests__/JobDetailRoute.test.tsx`

- [ ] **Step 1: Extend the route test**

Add a new test case to the existing describe block:

```typescript
it('renders MaterialsList, ExpensesTable, and JobCostRollup', async () => {
  // Use the existing renderAt + MSW setup. The default MSW handlers return
  // empty arrays for materials/expenses, so we expect the section headers
  // + the JobCostRollup's "Job total: $0.00" line.
  useJobsStore.getState().setJobs([/* same shape as Slice 2 test seed */]);
  renderAt('jX');
  expect(await screen.findByRole('heading', { name: /materials/i })).toBeInTheDocument();
  expect(await screen.findByText(/Job total: \$0\.00/)).toBeInTheDocument();
});
```

(Use the same seed pattern as the existing JobDetailRoute tests; ensure `stage: 'lead'` on the job.)

- [ ] **Step 2: Run the test to verify it fails**

- [ ] **Step 3: Mount the three new sections in `JobDetailRoute.tsx`**

Add imports near the top:
```typescript
import { MaterialsList } from '../components/materials/MaterialsList';
import { ExpensesTable } from '../components/expenses/ExpensesTable';
import { JobCostRollup } from '../components/jobs/JobCostRollup';
```

Mount, directly below the existing `<JobStageBar /> + <JobStageControls />` from Slice 2:
```tsx
<JobStageBar stage={job.stage} />
<JobStageControls job={job} />
<MaterialsList jobId={job.id} />
<ExpensesTable jobId={job.id} />
<JobCostRollup jobId={job.id} />
```

- [ ] **Step 4: Re-run the test**

Run: `cd desktop/portal && npm run test:run -- JobDetailRoute`
Expected: all tests PASS.

- [ ] **Step 5: Full portal suite + tsc + build**

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
git commit -m "feat(portal): mount Materials + Expenses + JobCostRollup on JobDetailRoute

Three new sections below JobStageControls (Slice 2): MaterialsList,
ExpensesTable, JobCostRollup. Existing tasks/description sections
unchanged.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>"
```

---

### Task 16: End-to-end verification

**Files:** none — verification only.

- [ ] **Step 1: Full backend suite**

```bash
cd backend
DATABASE_URL=postgresql://localhost/smithnet npx jest 2>&1 | grep -E "^(Tests:|Test Suites:)"
```
Expected: all suites pass (was 56 after Slice 2; this adds 2 new suites = 58).

- [ ] **Step 2: Full portal suite + tsc + build**

```bash
cd desktop/portal
npm run test:run
npx tsc --noEmit
npm run build
```
Expected: all green. New components add tests; expect ~85 files / ~390 tests.

- [ ] **Step 3: ts-node-dev pickup verification**

ts-node-dev on `:3030` should have picked up the new files automatically. Probe the live endpoints:
```bash
curl -s -o /dev/null -w "POST /api/materials (expect 401): %{http_code}\n" -X POST http://localhost:3030/api/materials -H "Content-Type: application/json" -d '{}'
curl -s -o /dev/null -w "POST /api/expenses (expect 401): %{http_code}\n" -X POST http://localhost:3030/api/expenses -H "Content-Type: application/json" -d '{}'
```
Expected: both `401`. If `404`, the dev server didn't pick up the changes; restart `:3030`.

- [ ] **Step 4: Live smoke (optional but recommended)**

Log in as `foreman-demo@example.com`, open a job, add 2 materials + 1 expense, check one material, see the rollup. Advance the job to `review` and confirm the `! 1 materials not checked off` warning. Click `[GENERATE INVOICE]` to confirm non-blocking. Delete the job from the DB (`DELETE FROM jobs WHERE id = '...'`) and verify materials + expenses cascade.

- [ ] **Step 5: No final commit needed**

If everything is green, move to finishing-a-development-branch. Nothing changed in this task.

---

## Reuse references

- **Audit pattern:** `auditLog.log(AuditAction.XXX, actorId, payload)` mirrors existing `JOB_*`, `TASK_*`, `CLIENT_*` calls.
- **`requireTaskOwner.ts`** at `backend/src/middleware/` is the template for the two new ownership middlewares.
- **`tasksRoutes.ts`** is the template for the routes (`POST /api/<resource>` with `jobId` in body, `PATCH/DELETE /api/<resource>/:id` guarded).
- **`clientsService.ts`** is the template for the dynamic-update SET-builder pattern in `update()`.
- **`tasksClient.ts`** is the template for `materialsClient.ts` / `expensesClient.ts` (discriminated `*Result<T>` union, private `call<T>` helper).
- **`CreateClientModal.tsx`** / `AddTaskModal.tsx` (if present) is the template for the two new modals.
- **MSW handler pattern:** existing `http.patch('/api/jobs/:id', ...)` style — params in path, JSON body, return JSON payload.
- **APK reference (do not modify):** `JobBoardTypes.kt` (Material + JobExpense data classes), `JobPipelineScreen.kt:252-275, 443-449` (materials section + REVIEW warning).
- **Slice 2 stage-transition lessons learned (apply here):**
  - Cross-foreman returns `403 not_owner` (not 404). Tests must match.
  - Portal `useToast()` exposes `info()` and `error()` (not `show()`).
  - Portal `*Result<T>` spreads payload directly (`result.material`, not `result.value.material`).

---

## Self-review

- [x] **Spec coverage**: every section of the spec maps to a task — migration 032 (T1), migration 033 (T5), materialsService (T2-T3), materialsRoutes + tests (T4), expensesService (T5), expensesRoutes + tests (T6), portal materials wiring (T7-T9), portal expenses wiring (T10-T12), JobCostRollup (T13), REVIEW warning (T14), JobDetailRoute mount (T15), end-to-end gates (T16).
- [x] **Placeholder scan**: every code block is real. T10 and T11 use "mirror T7/T8" prose for the structurally-identical expenses wiring; this is intentional (DRY across tasks) and the implementer has the materials code in front of them as a verbatim template. No TBD/TODO.
- [x] **Type consistency**: `Material` / `Expense`; `materialsClient` / `expensesClient`; `useMaterialsStore` / `useExpensesStore`; `MATERIAL_*` / `EXPENSE_*` audit actions; `requireMaterialOwner` / `requireExpenseOwner` — consistent naming throughout.
- [x] **Step count per task**: each task is 4-9 steps of 2-5 minutes each, ending in a commit. Bite-sized.
- [x] **No `git add -A`**: every commit names files explicitly.
- [x] **Commit trailer**: every commit message ends with the required `Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>`.
- [x] **No emoji**: ASCII tokens only across all code, comments, and commit messages.
- [x] **Tasks-router convention**: the plan deviates from the spec's path-nested form (§4.3) and aligns with the tasks-routes convention (LIST on jobs router, write-ops on dedicated router with jobId in body). This is a small documented adjustment; the spec's audit/security/data-model decisions are unchanged.
