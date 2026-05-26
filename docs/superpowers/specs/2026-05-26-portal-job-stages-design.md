# Portal — Job stages / lifecycle (Slice 2) — Design

**Date:** 2026-05-26
**Status:** Approved (forward + targeted-reverses policy confirmed)
**Parent plan:** `~/.claude/plans/quizzical-napping-balloon.md` (Slices 2-5 outlined; each gets its own spec/plan/build cycle)
**Companion APK reference:**
- `android/app/src/main/java/com/guildofsmiths/trademesh/ui/jobboard/JobStage.kt` (enum, 7 values)
- `android/app/src/main/java/com/guildofsmiths/trademesh/ui/jobpipeline/JobStageBar.kt` (visual)
- `android/app/src/main/java/com/guildofsmiths/trademesh/ui/jobpipeline/JobPipelineScreen.kt:420-472` (per-stage actions)

---

## 1. Goal

Give every job a server-authoritative pipeline `stage` that mirrors the APK's 7-step lifecycle, refuse invalid transitions, and surface a stage bar + context-aware action buttons on the portal job detail. `stage` is additive — the existing `jobs.status` (`planned/in_progress/complete/cancelled`) stays exactly as-is.

## 2. Why now

The portal currently shows a job's `status` but no notion of where it sits in the lead -> proposal -> approved -> work -> review -> invoice -> closed pipeline. Slices 3-5 (Materials, Plans, Price) all hinge on stage as their gate (e.g. invoice generation is only meaningful in `review`/`invoice`). Landing stages first makes those slices straightforward.

## 3. Canonical stages

From `JobStage.kt`, lowercase snake_case in SQL/JSON:

| Stage | Meaning |
|---|---|
| `lead` | New opportunity, no proposal yet |
| `proposal` | Proposal sent to client |
| `approved` | Client approved; ready to schedule |
| `in_progress` | Work happening |
| `review` | Work done; reviewing materials/logs before invoicing |
| `invoice` | Invoice generated; awaiting payment |
| `closed` | Paid + done (terminal) |

## 4. Transition policy

**Forward spine:**
```
lead -> proposal -> approved -> in_progress -> review -> invoice -> closed
```

**Targeted reverses (3 only):**
- `proposal -> lead` — client rejected proposal
- `invoice -> review` — invoice wrong; rebuild before resending
- `closed -> invoice` — job reopened (rare; warranty/dispute)

**Everything else is refused** with `400 { error: 'Invalid stage transition: <from> -> <to>', code: 'invalid_stage_transition', from, to }` (mirrors the existing `invalid_status_transition` shape at `backend/src/jobsRoutes.ts:101-104`).

A self-loop (`from === to`) is treated as a no-op (200, no audit entry) so retrying the same PATCH is idempotent.

## 5. Backend surface

### 5.1 Migration — `migrations/031_jobs_stage.sql`

```sql
ALTER TABLE jobs ADD COLUMN stage TEXT NOT NULL DEFAULT 'lead'
  CHECK (stage IN ('lead','proposal','approved','in_progress','review','invoice','closed'));

-- Backfill from existing status (lossy but reasonable):
UPDATE jobs SET stage = CASE
  WHEN status = 'planned'     THEN 'lead'
  WHEN status = 'in_progress' THEN 'in_progress'
  WHEN status = 'complete'    THEN 'closed'
  WHEN status = 'cancelled'   THEN 'closed'
  ELSE 'lead'
END;

CREATE INDEX IF NOT EXISTS idx_jobs_foreman_stage ON jobs (foreman_id, stage);
```

### 5.2 `backend/src/jobsService.ts`

- Export `JobStage` type union of the 7 stages.
- `Job` interface gains `stage: JobStage`.
- `mapJobRow` reads `row.stage`.
- `INSERT` in `create()` adds `stage` column (defaults to `lead`, no need to override).
- New `VALID_STAGE_TRANSITIONS: Record<JobStage, JobStage[]>` mapping (forward + 3 reverses).
- New `assertValidStageTransition(from, to)` throwing `InvalidStageTransitionError` (mirrors the existing `InvalidTransitionError` for status at `jobsService.ts:54-59`).
- New `changeStage(jobId, newStage): Promise<Job>` mirroring `changeStatus` exactly: load existing, assert, UPDATE, audit, return.
  - Self-loop (`existing.stage === newStage`) returns existing job without writing audit.

### 5.3 `backend/src/jobsRoutes.ts`

- New `PATCH /api/jobs/:id/stage` route mirroring `PATCH /api/jobs/:id/status` (`jobsRoutes.ts:91-114`):
  - `requireJobOwner` + `validateBody(StageChangeBody)`
  - Calls `jobsService.changeStage(...)`
  - 400 on `InvalidStageTransitionError` with `code: 'invalid_stage_transition'`
  - 404 on `NotFoundError`
  - 500 otherwise

### 5.4 `backend/src/schemas/jobs.ts`

```ts
export const StageChangeBody = z.object({
  stage: z.enum(['lead','proposal','approved','in_progress','review','invoice','closed']),
}).strict();
```

### 5.5 `backend/src/auditLog.ts`

Add `JOB_STAGE_CHANGED = 'job.stage_changed'` to the `AuditAction` enum (sits next to the existing `JOB_STATUS_CHANGED` at line 51).

## 6. Portal surface

### 6.1 New component — `components/jobs/JobStageBar.tsx`

Monospace pipeline indicator (mirrors `JobStageBar.kt` in spirit, web-native in execution). Renders 7 dots connected by lines; dots before-and-including current stage are filled; current stage is brighter/larger; the active stage label appears below in uppercase.

```
( • )───( • )───( • )───( o )───( o )───( o )───( o )
                   IN PROGRESS
```

- Filled dot: solid `bg-console-accent`
- Empty dot: hollow `border border-console-text-muted/40`
- Connecting line: `bg-console-accent` to the left of current, `bg-console-text-muted/15` to the right
- Label: `text-console-accent text-xs tracking-wider uppercase`

### 6.2 New component — `components/jobs/JobStageControls.tsx`

Context-aware action buttons per current stage. Labels mirror APK (`JobPipelineScreen.kt:420-472`). All buttons call `jobsClient.changeStage(jobId, targetStage)` then `useJobsStore.getState().upsertJob(result.job)` + toast. Errors surface as toasts with the server `error` message.

| Current stage | Forward button(s) | Reverse button (if any) |
|---|---|---|
| `lead` | `[CREATE PROPOSAL]` -> proposal | - |
| `proposal` | `[MARK APPROVED]` -> approved | `[MARK REJECTED]` -> lead |
| `approved` | `[START WORK]` -> in_progress | - |
| `in_progress` | `[MARK WORK COMPLETE]` -> review | - |
| `review` | `[GENERATE INVOICE]` -> invoice | - |
| `invoice` | `[MARK PAID - CLOSE]` -> closed | `[REOPEN INVOICE]` -> review |
| `closed` | (none — terminal) | `[REOPEN JOB]` -> invoice |

Forward buttons render as the primary variant; reverse buttons as `variant="ghost"` so they don't pull focus from the natural flow.

**Note on "forward" buttons in `lead`, `review`, `invoice` stages**: these transition the stage only. The proposal/invoice generation flows themselves land in later slices (Plans, Price). Until then, transitioning to `proposal` just marks the stage — no artifact is created. The button label still says `[CREATE PROPOSAL]` because that's what it means in product terms; the user simply won't see a generated proposal yet. This is intentional: stages can land before the artifacts they gate.

### 6.3 `routes/JobDetailRoute.tsx`

- Mount `<JobStageBar stage={job.stage} />` directly under the page title row.
- Mount `<JobStageControls job={job} />` below the bar, above the tasks section.
- No layout change to the existing `[Edit]` button (Slice 1).

### 6.4 `api/jobsClient.ts`

- `Job` interface gains `stage: JobStage` (type imported from a new shared module or duplicated as a string-literal union — see Decisions below).
- New `changeStage(id: string, stage: JobStage): Promise<JobsResult<{ job: Job }>>` method, identical shape to existing CRUD methods (uses the private `call<T>` helper).

### 6.5 `stores/jobsStore.ts`

No interface change. The existing `upsertJob(job)` already replaces a job in the list; the new `stage` field rides along on the existing Job type.

## 7. Tests

### 7.1 Backend — `backend/src/__tests__/jobs-stage-routes.test.ts` (new)

Mirrors `jobs-routes.test.ts` setup pattern (`describeDb`, `createUserAndProfile`, top-level `pg?.end()`):

- 401 without auth
- 404 cross-foreman
- 200 for each valid forward transition (6 cases) — asserts the job's `stage` is updated AND an `JOB_STAGE_CHANGED` audit entry is written with the right `from`/`to`
- 200 for each valid reverse transition (3 cases)
- 400 `invalid_stage_transition` for representative refused cases: `lead -> in_progress`, `closed -> lead`, `approved -> closed`
- Self-loop `lead -> lead` returns 200 and writes NO audit entry
- 400 zod for unknown stage value (`{ stage: 'bogus' }`)

### 7.2 Portal — three new test files

- `components/jobs/__tests__/JobStageBar.test.tsx` — renders 7 dots, the current-stage label uppercase, no a11y violations.
- `components/jobs/__tests__/JobStageControls.test.tsx` — for each of the 7 stages, asserts which buttons appear. Click `[CREATE PROPOSAL]` -> MSW PATCH returns job with `stage: 'proposal'` -> store's `upsertJob` is called with the new stage.
- `routes/__tests__/JobDetailRoute.test.tsx` — extend the existing test (or add a focused new one) to assert the stage bar renders and the appropriate forward button click invokes the stage endpoint.

### 7.3 MSW handler — `test/msw-handlers.ts`

Add `http.patch('/api/jobs/:id/stage', ...)` that echoes the requested stage back on the job.

## 8. Tier / security / determinism

- **Tier.** Already enforced: `jobsRouter.use(authenticateToken, requireConsoleTier)` gates the whole router. Stage transitions require foreman+ (same as status changes).
- **Ownership.** `requireJobOwner` middleware on the route enforces single-tenant access.
- **Identity.** `req.user!.id` only — no `X-User-Id`.
- **Validation.** `validateBody(StageChangeBody)` with `.strict()` rejects unknown fields.
- **Audit.** `JOB_STAGE_CHANGED` entry on every real transition (per `audit on every mutation` convention).
- **Determinism.** `stage` is regular mutable state on `jobs` — NOT a ledger entry, NOT supersession-chained. The stage column lives at the same level as `status`; nothing about D1-D5 changes. The `synthesizer.synthesize()` pipeline doesn't read `stage`.
- **No inline LLM / no fire-and-forget.** This slice does neither (CLAUDE.md Rule 1, Rule 2).

## 9. Decisions (called out)

- **Backfill heuristic** (`complete -> closed`): lossy because a `complete` job hasn't necessarily been paid. Accepted because (a) the dataset is small (dev DB + foreman-demo), (b) the foreman can manually walk it back to `review` if needed, (c) the alternative — `complete -> review` — leaves the foreman to manually advance closed work, which is more friction than the rare reopen case.
- **`JobStage` type location.** Define once in `api/jobsClient.ts` as a string-literal union and re-export. Don't introduce a shared `types/` module just for this — the project's existing convention keeps types local to their client file.
- **No drag-and-drop kanban / no per-org custom stages.** Out of scope for v1. The 7-stage list is the SmithNet canon.

## 10. Out of scope (explicit)

- Proposal generation/preview surface (later slice — Plans or its own)
- Invoice generation (Price slice)
- Material check-off blocking review->invoice (Slice 3 — Materials may add a warning, not a block)
- Stage-derived dashboard summaries (could land as a quick follow-up if compelling, but not part of Slice 2 acceptance)
- Bulk stage transition / multi-job pipeline view

## 11. Acceptance

- Backend: `cd backend && DATABASE_URL=postgresql://localhost/smithnet npx jest src/__tests__/jobs-stage-routes.test.ts` green; full backend suite still green.
- Portal: `cd desktop/portal && npm run test:run && npx tsc --noEmit && npm run build` green.
- Live: log in as `foreman-demo@example.com`, open a job, see the bar at `LEAD`, click `[CREATE PROPOSAL]` -> bar advances to `PROPOSAL` -> reload page -> still `PROPOSAL`. Try `closed -> lead` (manually via DevTools or curl) -> backend returns 400 `invalid_stage_transition`.

---

## Self-review

- [x] **Spec coverage** vs. the parent plan's Slice 2 bullet: column + state machine + serialize ✓, JobStageBar ✓, stage controls ✓, distinct from `status` ✓.
- [x] **No placeholders**: every section has concrete content; no TBD/TODO.
- [x] **Type consistency**: `JobStage` referenced consistently (lowercase snake_case strings); `changeStage` not `setStage` or `transitionStage` throughout.
- [x] **Internal consistency**: section 4's transition policy matches the test cases in 7.1; section 6.2's buttons match the transitions in section 4.
- [x] **Scope check**: single subsystem (job stages), no cross-cutting concerns slipped in.
