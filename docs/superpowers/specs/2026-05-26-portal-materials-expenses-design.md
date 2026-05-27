# Portal — Materials checklist + Job expenses (Slice 3) — Design

**Date:** 2026-05-26
**Status:** Approved (Material + JobExpense both; free-text categories with suggestions; rollup shown now; APK warning wording preserved)
**Parent plan:** `~/.claude/plans/quizzical-napping-balloon.md` (Slice 3 of 5)
**Companion APK reference:**
- `android/app/src/main/java/com/guildofsmiths/trademesh/ui/jobboard/JobBoardTypes.kt:84-96` — `data class Material`
- `android/app/src/main/java/com/guildofsmiths/trademesh/ui/jobboard/JobBoardTypes.kt:106-...` — `data class JobExpense`
- `android/app/src/main/java/com/guildofsmiths/trademesh/ui/jobpipeline/JobPipelineScreen.kt:252-275` — materials section render
- `android/app/src/main/java/com/guildofsmiths/trademesh/ui/jobpipeline/JobPipelineScreen.kt:443-449` — REVIEW-stage unchecked warning

---

## 1. Goal

Give every job a server-authoritative **Materials checklist** (cost-capturing per-job items with check-off) AND a parallel **Expenses** table (BOL-style itemized lines by category). Both are foreman-owned, per-job, and surface as sections on `JobDetailRoute` under the stage controls landed in Slice 2. The stage controls grow a non-blocking warning at `review` when materials are unchecked.

## 2. Why now

The `materials` table already exists in the schema (`migrations/002_full_schema.sql:152`) and `synthesizer.ts:69` already reads it to compose `materialsUsed` for the canonical SummaryArtifact hash — but there's no write path or UI today, so the column is dead in practice. Slice 5 (Billing/Price) cannot generate a useful invoice without real cost data on the job. Landing both surfaces now means: (a) the synthesizer starts seeing real material rows, (b) the invoice generator in Slice 5 has both labor and materials/expenses to work with, (c) the `review` stage's "unchecked materials" warning gets a real signal to fire on.

## 3. Domain model

### 3.1 Material (per-job checklist with cost)

Mirrors `JobBoardTypes.kt:84-96` minus the receipt-photo field (deferred). Persisted in the existing `materials` table, extended.

| Field | Type | Purpose |
|---|---|---|
| `id` | UUID | PK (existing) |
| `job_id` | UUID | FK -> `jobs(id) ON DELETE CASCADE` (added; was nullable) |
| `name` | TEXT | What it is (e.g. "10/2 Romex") |
| `notes` | TEXT NULL | Free notes (added) |
| `checked` | BOOLEAN | Acquired/installed flag, default `FALSE` (added) |
| `checked_at` | TIMESTAMPTZ NULL | When the toggle fired (added) |
| `quantity` | NUMERIC(10,2) | Existing |
| `unit` | TEXT | Existing — defaults `'ea'` |
| `unit_cost` | NUMERIC(10,2) | Existing |
| `vendor` | TEXT NULL | Where bought (added) |
| `created_at` | TIMESTAMPTZ | Existing |
| `updated_at` | TIMESTAMPTZ | Added |

`total_cost` (APK has it) is **NOT** persisted — derived client-side as `quantity * unit_cost`. The APK already ignores `totalCost` when summing (`JobPipelineScreen.kt:373` does `sumOf { quantity * unitCost }`).

Receipt photo (APK has it) is **NOT** persisted — deferred until the portal has an upload story.

### 3.2 JobExpense (per-job BOL-style line)

Mirrors `JobBoardTypes.kt:106+` (simplified). Persisted in a new `job_expenses` table.

| Field | Type | Purpose |
|---|---|---|
| `id` | UUID | PK |
| `job_id` | UUID NOT NULL | FK -> `jobs(id) ON DELETE CASCADE` |
| `category` | TEXT NOT NULL | Free text. UI suggests `material, permit_fee, fuel, subcontractor, equipment_rental, other` via `<datalist>` but the server does not enforce. |
| `description` | TEXT NOT NULL | What it was for |
| `amount` | NUMERIC(10,2) NOT NULL DEFAULT 0 | Flat amount (not unit-priced) |
| `vendor` | TEXT NULL | Where paid |
| `notes` | TEXT NULL | Free notes |
| `expense_date` | DATE NULL | When it occurred (NULL = unknown) |
| `created_at`, `updated_at` | TIMESTAMPTZ | Standard |

## 4. Backend surface

### 4.1 Migrations

**`migrations/032_materials_extend.sql`:**
```sql
ALTER TABLE materials
  ADD COLUMN IF NOT EXISTS notes TEXT,
  ADD COLUMN IF NOT EXISTS checked BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS checked_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS vendor TEXT,
  ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- Tighten and link. Skip if any row would orphan; should be zero in practice
-- (the table has had no writers).
ALTER TABLE materials ALTER COLUMN job_id SET NOT NULL;
ALTER TABLE materials ADD CONSTRAINT materials_job_id_fkey
  FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_materials_job ON materials (job_id);
```

**`migrations/033_job_expenses.sql`:**
```sql
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

### 4.2 Services

**`backend/src/materialsService.ts`** — pure functions:
- `Material` interface (camelCase: `id, jobId, name, notes, checked, checkedAt, quantity, unit, unitCost, vendor, createdAt, updatedAt`)
- `listByJob(jobId): Promise<Material[]>` ordered by `created_at ASC`
- `create(input): Promise<Material>` — input takes `jobId, name, quantity?, unit?, unitCost?, vendor?, notes?`
- `update(id, patch): Promise<Material | null>` — dynamic SET builder over the 8 mutable fields. When patch includes `checked: true`, set `checked_at = NOW()`; when `checked: false`, set `checked_at = NULL`.
- `softDelete` not used — **hard delete** via `delete(id)` returning boolean. Matches tasks pattern.
- `getById(id): Promise<Material | null>` for ownership verification.

**`backend/src/expensesService.ts`** — same shape, with the JobExpense field set.

### 4.3 Routes (mirror `tasks-routes.ts`)

**`backend/src/materialsRoutes.ts`:**
- `GET /api/jobs/:jobId/materials` -> uses existing `requireJobOwner` (loads job + verifies foreman) -> `materialsService.listByJob`
- `POST /api/jobs/:jobId/materials` -> `requireJobOwner` + `validateBody(CreateMaterialBody)` -> `materialsService.create`
- `PATCH /api/materials/:id` -> a new `requireMaterialOwner` middleware that loads the material, then verifies the parent job's foreman matches `req.user!.id`. Then `materialsService.update`.
- `DELETE /api/materials/:id` -> same ownership middleware -> `materialsService.delete` -> 204.

**`backend/src/expensesRoutes.ts`** — identical pattern.

Both routers `.use(authenticateToken, requireConsoleTier)` at the top (same as `clientsRouter`/`jobsRouter`).

### 4.4 Zod schemas

**`backend/src/schemas/materials.ts`:**
```typescript
export const CreateMaterialBody = z.object({
  name: z.string().min(1).max(200),
  notes: z.string().max(2000).optional(),
  quantity: z.number().nonnegative().optional(),
  unit: z.string().min(1).max(20).optional(),
  unitCost: z.number().nonnegative().optional(),
  vendor: z.string().max(200).optional(),
}).strict();

export const UpdateMaterialBody = z.object({
  name: z.string().min(1).max(200).optional(),
  notes: z.string().max(2000).nullable().optional(),
  quantity: z.number().nonnegative().optional(),
  unit: z.string().min(1).max(20).optional(),
  unitCost: z.number().nonnegative().optional(),
  vendor: z.string().max(200).nullable().optional(),
  checked: z.boolean().optional(),
}).strict();
```

**`backend/src/schemas/expenses.ts`:**
```typescript
const ISO_DATE = z.string().regex(/^\d{4}-\d{2}-\d{2}$/);

export const CreateExpenseBody = z.object({
  category: z.string().min(1).max(60),
  description: z.string().min(1).max(500),
  amount: z.number().nonnegative(),
  vendor: z.string().max(200).optional(),
  notes: z.string().max(2000).optional(),
  expenseDate: ISO_DATE.optional(),
}).strict();

export const UpdateExpenseBody = z.object({
  category: z.string().min(1).max(60).optional(),
  description: z.string().min(1).max(500).optional(),
  amount: z.number().nonnegative().optional(),
  vendor: z.string().max(200).nullable().optional(),
  notes: z.string().max(2000).nullable().optional(),
  expenseDate: ISO_DATE.nullable().optional(),
}).strict();
```

### 4.5 Audit log

Add to `AuditAction` enum:
```typescript
MATERIAL_CREATED = 'material.created',
MATERIAL_UPDATED = 'material.updated',
MATERIAL_DELETED = 'material.deleted',
EXPENSE_CREATED = 'expense.created',
EXPENSE_UPDATED = 'expense.updated',
EXPENSE_DELETED = 'expense.deleted',
```
Each mutation in the service writes its own audit entry with `jobId`, the resource `id`, and the changed fields.

### 4.6 Server wiring

`backend/src/server.ts` mounts both routers next to the existing ones:
```typescript
app.use('/api', materialsRouter);  // routes are /jobs/:jobId/materials and /materials/:id
app.use('/api', expensesRouter);
```

## 5. Portal surface

### 5.1 API clients

**`desktop/portal/src/console/api/materialsClient.ts`** — mirrors `tasksClient.ts`:
- `Material` interface
- `MaterialsResult<T>` discriminated union (same shape as `JobsResult`, `TasksResult`)
- `listForJob(jobId)`, `create(jobId, input)`, `update(id, patch)`, `delete(id)`

**`desktop/portal/src/console/api/expensesClient.ts`** — same shape with `Expense`.

### 5.2 Stores

**`desktop/portal/src/console/stores/materialsStore.ts`** — zustand store keyed by jobId:
```typescript
interface MaterialsStore {
  byJob: Record<string, Material[]>;
  setForJob(jobId: string, items: Material[]): void;
  upsert(jobId: string, item: Material): void;
  remove(jobId: string, id: string): void;
  clear(): void;
}
```

**`expensesStore.ts`** — same shape.

### 5.3 Components

**`components/materials/MaterialsList.tsx`** — given `jobId`, fetches via the store on mount, renders a list of materials. Each row:
- Checkbox (left) -> toggles `checked` via `materialsClient.update(id, { checked })`. Optimistic upsert.
- Name + qty/unit + vendor (middle)
- Computed line cost (`quantity * unit_cost`) on the right
- Edit (opens `AddMaterialModal` with prefill) + Delete buttons
- Bottom: subtotal `Materials: $X.XX` (sum of `quantity * unit_cost`)
- Empty state: "No materials yet. [+ Add material]"

**`components/materials/AddMaterialModal.tsx`** — create/edit modal:
- Fields: name (required), quantity (default 1), unit (default 'ea'), unit cost (default 0), vendor, notes
- On submit: `materialsClient.create` (or `update` if editing) -> store upsert -> toast.

**`components/expenses/ExpensesTable.tsx`** — given `jobId`, renders a table:
- Columns: category | description | amount | vendor | date | actions
- Empty state: "No expenses yet. [+ Add expense]"
- Bottom: subtotal `Expenses: $Y.YY`

**`components/expenses/AddExpenseModal.tsx`** — create/edit modal:
- Fields: category (with `<datalist>` of suggestions: `material, permit_fee, fuel, subcontractor, equipment_rental, other`), description (required), amount (required), vendor, expense date, notes
- On submit: client create/update -> store upsert -> toast.

**`components/jobs/JobCostRollup.tsx`** — small read-only block to render under both sections:
```
Materials: $X.XX
Expenses:  $Y.YY
─────────────
Job total: $Z.ZZ
```
Subscribes to `materialsStore.byJob[jobId]` and `expensesStore.byJob[jobId]` and recomputes on change.

### 5.4 `JobDetailRoute.tsx`

Mount, directly under the `JobStageControls` block (which sits under `JobStageBar` from Slice 2):
```tsx
<JobStageBar stage={job.stage} />
<JobStageControls job={job} />
<MaterialsList jobId={job.id} />
<ExpensesTable jobId={job.id} />
<JobCostRollup jobId={job.id} />
```
The existing tasks section (open tasks) and description block remain below.

### 5.5 `JobStageControls.tsx` — REVIEW warning

When `job.stage === 'review'` AND `materialsStore.byJob[job.id]` has at least one row with `checked === false`, render a small warning line above the transition button (`[GENERATE INVOICE]`):
```
! <N> materials not checked off
```
Color: `text-console-warn`. Non-blocking — does not disable the button. Wording exactly matches APK (`JobPipelineScreen.kt:446`).

The component will need to subscribe to `materialsStore.byJob[job.id]`. To avoid a stale read race, the materials list mount already fetches on its own; the warning may be momentarily absent on first paint before fetch completes — acceptable for v1.

## 6. Tests

### 6.1 Backend

`materials-routes.test.ts`:
- 401 without auth
- 403 tier_required for Solo
- Create + list + get + update + delete (CRUD round-trip)
- `checked = true` sets `checked_at`; `checked = false` clears it
- Cross-foreman isolation: 403 (or 404 per the `requireMaterialOwner` middleware decision — see section 8)
- Strict zod (rejects unknown fields, rejects negative quantity)
- Cascade: deleting the job cascades materials (`ON DELETE CASCADE`)

`expenses-routes.test.ts` — mirror, with `expenseDate` round-trip and category as free text.

### 6.2 Portal

- `MaterialsList.test.tsx` — renders rows, toggle checkbox optimistically updates store + calls client, subtotal correct, empty state, edit/delete buttons.
- `AddMaterialModal.test.tsx` — submits create, prefills on edit, validation error path.
- `ExpensesTable.test.tsx` — table renders with category/desc/amount/vendor/date, subtotal correct.
- `AddExpenseModal.test.tsx` — datalist visible, submits, prefills on edit.
- `JobCostRollup.test.tsx` — sums materials and expenses correctly; reactive when stores change.
- `JobStageControls.test.tsx` — extend with a `review`-stage test that seeds 2 unchecked materials and asserts the `! 2 materials not checked off` line appears.
- `JobDetailRoute.test.tsx` — extend to assert both sections render and the rollup.

### 6.3 MSW

Add handlers for: `GET /api/jobs/:jobId/materials`, `POST /api/jobs/:jobId/materials`, `PATCH /api/materials/:id`, `DELETE /api/materials/:id`, and the four expense equivalents.

## 7. Tier / security / determinism

- **Tier.** Both routers gate at `requireConsoleTier` (foreman+). Solo gets 403 `tier_required`.
- **Identity.** `req.user!.id` only. Ownership via `requireJobOwner` (for the list/create endpoints under `/jobs/:jobId/...`) or a new `requireMaterialOwner` / `requireExpenseOwner` middleware (for the resource-scoped PATCH/DELETE endpoints under `/materials/:id` and `/expenses/:id`). The new middlewares load the resource, then verify the parent job's foreman matches.
- **Validation.** Every POST/PATCH passes through `validateBody(strict zod)`.
- **Audit.** Every mutation writes an `AuditAction.MATERIAL_*` or `EXPENSE_*` entry.
- **Determinism.**
  - `synthesizer.ts:69` continues to `SELECT name, quantity, unit_cost FROM materials WHERE job_id = ANY(...)`. The new columns (`checked, vendor, notes, checked_at, updated_at`) are NOT in the canonical hash. D1-D5 invariants preserved.
  - Adding rows to `materials` will start producing non-empty `materialsUsed` arrays in artifacts — which is the intended downstream effect. Existing artifacts already sealed are not affected (canonicalization unchanged).
  - `job_expenses` is **NOT** read by the synthesizer in this slice. Expenses participate in the invoice (Slice 5), not the SummaryArtifact canonical hash. This is intentional: invoices are public-facing financial documents; SummaryArtifacts are the operational record. They can diverge.
- **No inline LLM. No fire-and-forget.** Audit goes through the existing `auditLog.log` (which already enqueues via `background_jobs`).

## 8. Decisions called out

- **Materials extends the existing table, expenses is a new table.** Two distinct data shapes; squeezing both into one table would dilute both.
- **Hard delete on both** (matches tasks). End user has no reason to keep deleted material/expense rows.
- **No `sort_order` on either.** `created_at ASC`. Drag-reorder deferred.
- **Resource-scoped middleware** (`requireMaterialOwner`) returns **403 `not_owner`** on cross-foreman, matching the `requireJobOwner` precedent caught in Slice 2 (NOT 404).
- **`total_cost` is derived, not persisted.** APK sums `quantity * unit_cost`; we do the same on the portal. Storing both invites drift.
- **`expense_date` is `DATE`, not `TIMESTAMPTZ`.** Expenses occur on a day, not at a moment. No timezone concerns.
- **Synthesizer is NOT modified in this slice.** It already reads materials. We're only adding writers + UI.

## 9. Known issues / deferred

- **Concurrent toggle race.** Same shape as the Slice 2 stage race (LOW): two PATCH `/materials/:id` with `{ checked: true }` racing each other will both write `checked_at`. Acceptable — second write wins, no audit corruption (each PATCH writes its own audit), result is deterministic from the DB's perspective.
- **Receipt photo upload.** Deferred. The schema doesn't have the column.
- **Bulk paste from a vendor receipt** (e.g. "5 lines from a Home Depot receipt"). Manual entry only for v1.
- **Cross-job cost totals on the client detail page.** Slice 5 (Billing) will do the per-client rollup. This slice rolls up per-job only.
- **Audit assertion tests.** Same caveat as Slice 2 (§9b): no existing backend tests assert audit entries. Adding here would establish a new pattern; deferred to a coverage pass.

## 10. Out of scope (explicit)

- Proposal generation (deferred — Slice 4 or its own slice)
- Invoice generation from materials/expenses (Slice 5 / Price)
- Categorized expense reports / spreadsheets export (later)
- Approval workflow on expenses (e.g. "manager must approve > $X") — not a v1 concern
- Multi-currency

## 11. Acceptance

- Backend: full suite green; new suites `materials-routes.test.ts` + `expenses-routes.test.ts` pass.
- Portal: full suite green; tsc + build clean.
- Live (foreman demo): open a job -> add 2 materials, check one -> add 1 expense -> see `Materials: $X / Expenses: $Y / Job total: $Z` rollup. Advance the job to `review` -> see `! 1 materials not checked off` warning above `[GENERATE INVOICE]` button. Click `[GENERATE INVOICE]` anyway (warning is non-blocking) -> stage advances to `invoice`. Delete the job -> materials and expenses cascade.

---

## Self-review

- [x] **Spec coverage** vs the parent plan's Slice 3 bullet: materials write path + columns (`checked`, `unit` (already there), `total_cost` (derived), `vendor`) ✓; routes/service per job ✓; portal materials section with check-off + cost capture ✓; feeds invoice material lines later ✓. Expenses surface added per user direction.
- [x] **Placeholder scan**: no TBD/TODO. Every column, route, endpoint, and test is specified concretely.
- [x] **Type consistency**: `Material` / `JobExpense` (Kotlin parity); `materialsClient` / `expensesClient`; `MATERIAL_*` / `EXPENSE_*` audit actions; same casing throughout.
- [x] **Internal consistency**: §3 model -> §4 migrations -> §4.4 zod -> §6.1 tests all match field-for-field. The REVIEW warning in §5.5 matches §6.2's `JobStageControls` extension test.
- [x] **Scope check**: two parallel domains, one slice. Tightly coupled (shared parent job, shared rollup, shared REVIEW gate). Big but coherent. Synthesizer not modified — important for keeping the slice contained.
