# Plan 3 — Job Board Frontend — Design

**Date:** 2026-05-12
**Scope:** Frontend Job Board surface on the console + one small backend endpoint (`GET /api/profiles?q=`) to feed the crew-search modal.
**Target:** `/desktop/portal/src/console/` (frontend) + minor add in `/backend/src/` (backend)
**Predecessors:** Plan 1 (console foundation, commit `ba5728c`), Plan 2 (jobs backend, commit `34186e9`)

## Summary

Surface the Foreman's jobs in the operator console with a list view (`/console/jobs`) grouped by status, a detail view (`/console/jobs/:id`) with crew + status workflow buttons, a create modal, and an assign-crew modal that searches profiles by name/email. **Polling at 15s, paused when the tab is hidden.** No WebSocket, no drag-and-drop — both deferred to a later plan once the basic workflow is in user hands.

The Plan 1 spec originally pitched a kanban-with-drag-and-drop driven by WebSocket events. That's ~4× the work of what users actually need to dispatch jobs. Plan 3 ships the minimum that's actually useful: list, detail, create, status transitions, crew assignment. Live updates come from polling — invisible to the user, trivial to implement, and good enough at this scale.

One small backend addition fits naturally inside this plan: `GET /api/profiles?q=<query>` — needed by the assign-crew modal. It's a 30-line route + zod schema + test file, no schema change.

## Decisions

| Decision | Choice | Why |
|----------|--------|-----|
| Live-update mechanism | Polling at 15s, paused on `document.hidden` | WebSocket adds substantial backend complexity (extend `wsHandler.ts` auth + add 5 new message types + frontend WS module + reconnect logic) for a barely perceptible UX win at this scale. Defer to a follow-up plan. |
| List layout | Status-grouped collapsible sections | Familiar; communicates pipeline state; works on narrow viewports; no DnD machinery needed |
| Default section state | `planned` + `in_progress` expanded; `complete` + `cancelled` collapsed | Active states are action items; terminal states are history |
| Status changes | Buttons that respect the state machine | UI mirrors the backend `assertValidTransition` table from Plan 2; server still enforces |
| Crew assignment | Modal with name/email search | Familiar; ships a small backend endpoint as a natural side effect |
| Profile-search endpoint | Part of Plan 3, not Plan 4 | The endpoint exists ONLY to feed the assign modal; deferring it would block the assign UX |
| Optimistic updates | NONE | 15s polling hides server-driven changes; optimistic-with-rollback adds 2× code paths per mutation for negligible win |
| Toast primitive | New `Toast.tsx` UI primitive in Plan 3 | One small file; matches the Plan 1 primitive pattern; needed for network/status-transition feedback |
| Console nav | Add real `Jobs` link; `Map` disabled placeholder (Plan 4) | Replaces the "(routes coming soon)" placeholder |
| Mutations | Just await + refetch | Polling-15s smooths over the brief delay; no optimistic UI code paths |
| Backend route file | New `backend/src/profilesRoutes.ts` | Mirrors the `jobsRoutes.ts` separation from Plan 2; cleaner than appending to `api.ts` |
| Backend search backend | In-memory `userStore.getAllUsers()` filtered + capped at 20 | Auth is still in-memory; swaps to SQL when userStore migrates without changing the contract |
| Tests | Vitest + RTL + MSW + fake timers | All HTTP mocked; no `DATABASE_URL` dependency on the frontend test suite |

## Architecture

### Frontend file structure (new under `desktop/portal/src/console/`)

```
api/
  jobsClient.ts             // fetch wrappers for /api/jobs/* — mirrors authClient.ts
  profilesClient.ts         // GET /api/profiles?q=<query>
stores/
  jobsStore.ts              // zustand — jobs, detailJob, detailCrew, isStale, lastFetchedAt
hooks/
  useJobsPolling.ts         // 15s interval, visibility-aware, scope = 'list' | { detail: id }
routes/
  JobsListRoute.tsx
  JobDetailRoute.tsx
components/
  jobs/
    JobCard.tsx
    JobStatusBadge.tsx
    StatusButtons.tsx
    CreateJobModal.tsx
    AssignCrewModal.tsx
  ui/
    Toast.tsx               // new primitive
```

Plus tests in `__tests__/` subfolders mirroring the layout, and the existing `ConsoleShell.tsx` gets a small nav update (replace "(routes coming soon)" with real items).

### Backend additions

```
backend/src/
  profilesRoutes.ts         // new — GET /api/profiles?q=
  schemas/profiles.ts       // new — ProfileQuery zod schema
  schemas/index.ts          // modify — add `export * as profiles from './profiles';`
  server.ts                 // modify — one line: app.use('/api/profiles', profilesRouter)
  __tests__/
    profiles-routes.test.ts // new — 401, 403, 400, 200 cases
```

### Stack

Already installed: `react`, `react-router-dom`, `zustand`, `clsx`, `tailwind-merge`, `tailwindcss`, `vitest`, `@testing-library/react`, `msw`, `zod`. **No new dependencies.**

### Scope boundary — explicitly NOT in this plan

- WebSocket / live-push (separate plan)
- Drag-and-drop kanban (separate plan)
- `/console/map` route + MapLibre (Plan 4)
- `/console/crew` browse page (Plan 4 — Plan 3 only has the search modal)
- `/console/clients/*` (Plan 5)
- Optimistic updates + rollback
- Toast library (we build a minimal primitive)

## List layout + data flow

### `/console/jobs` layout (status-grouped sections, collapsible)

```
[Header: "Jobs"]                                        [+ Create Job]

┌─ PLANNED (3) ──────────────────────────────────────────── ▼ ┐
│  [#] Install panel — 123 Main St      tomorrow 9am  [→detail]│
│  [#] Service call — 47 Oak Lane       Mar 12 2pm    [→detail]│
│  [#] Estimate — 88 Pine Rd            unsch         [→detail]│
└──────────────────────────────────────────────────────────────┘

┌─ IN PROGRESS (1) ──────────────────────────────────────── ▼ ┐
│  [#] Rough-in — 200 Elm Ave           started 11:14 [→detail]│
└──────────────────────────────────────────────────────────────┘

┌─ COMPLETE (12) ────────────────────────────────────────── ▶ ┐ (collapsed)
└──────────────────────────────────────────────────────────────┘

┌─ CANCELLED (2) ────────────────────────────────────────── ▶ ┐ (collapsed)
└──────────────────────────────────────────────────────────────┘
```

Row contents: `[#]` glyph + first 8 chars of job id, title, location, relative scheduled time ("tomorrow 9am", "in 2 days", "unsch" when null), right-aligned `[→ detail]` link to `/console/jobs/:id`.

### Polling flow

```
JobsListRoute mounts
  ├─ render jobsStore.jobs immediately (may be empty/stale)
  ├─ useJobsPolling('list') hook
  │    ├─ kick immediate fetch
  │    └─ setInterval(15_000)
  └─ on tick (only when document.visibilityState === 'visible'):
       └─ jobsClient.list() → jobsStore.setJobs(jobs) → re-render

JobDetailRoute mounts (params.id)
  ├─ render jobsStore.detailJob if id matches; else show loading
  ├─ jobsClient.getById(id) → jobsStore.setDetail(job, crew)
  └─ useJobsPolling({ detail: params.id }) — refetches THIS job, not list
```

### Mutation flow (status / create / assign)

```
User action (e.g., click [▶ Start])
  ├─ component disables relevant control
  ├─ jobsClient.changeStatus(jobId, 'in_progress')
  ├─ on 2xx:
  │    ├─ jobsStore.upsertJob(updatedJob)        // updates both list + detail slices
  │    ├─ close any modal
  │    └─ re-enable controls
  ├─ on 400 invalid_status_transition:
  │    └─ toast "Server rejected this transition (was X, tried Y). Refreshing..." + force refetch
  ├─ on 400 validation (zod errors):
  │    └─ inline field errors from response.details
  ├─ on 401:
  │    └─ toast "Session expired" + redirect to /console/login after 2s
  ├─ on 403 not_owner:
  │    └─ inline card "Not your job" with back link (detail route only)
  ├─ on 404 (detail route):
  │    └─ inline card "Job not found" with back link
  ├─ on 409 duplicate_assignment (assign modal):
  │    └─ inline error "Already assigned to this job"
  └─ on 5xx / network:
       └─ toast "Network error — retry?" + leave UI as-is + jobsStore.isStale = true
```

### `jobsStore` interface

```ts
interface JobsStore {
  jobs: Job[];                         // list-route data
  detailJob: Job | null;               // detail-route current job
  detailCrew: CrewAssignment[];        // detail-route crew
  isLoadingList: boolean;
  isLoadingDetail: boolean;
  lastFetchedAt: number | null;        // for "Last refreshed Xs ago"
  isStale: boolean;                    // true if last fetch errored

  setJobs(jobs: Job[]): void;
  setDetail(job: Job, crew: CrewAssignment[]): void;
  upsertJob(job: Job): void;
  markListLoading(b: boolean): void;
  markDetailLoading(b: boolean): void;
  markStale(b: boolean): void;
  clear(): void;
}
```

Mirrors `Job` and `CrewAssignment` types from Plan 2's backend service surface — types defined locally in `console/api/jobsClient.ts` to avoid coupling.

## Components

### `JobCard.tsx`
Pure presentation. Props: `{ job: Job }`. Renders id-prefix + title + location + relative scheduled time + detail link.

### `JobStatusBadge.tsx`
Wraps the Plan 1 `Badge` primitive. Status → tone mapping:

| status | tone | label |
|---|---|---|
| `planned` | default | `PLANNED` |
| `in_progress` | ok | `IN PROGRESS` |
| `complete` | ok | `COMPLETE` |
| `cancelled` | danger | `CANCELLED` |

### `StatusButtons.tsx`
Renders buttons valid for the current state per the state machine:

| current status | visible buttons |
|---|---|
| `planned` | `[▶ Start]` `[✕ Cancel]` |
| `in_progress` | `[✓ Complete]` `[✕ Cancel]` |
| `complete` | (none — terminal) |
| `cancelled` | (none — terminal) |

Each button disables during its pending API call. Server-rejected transition (rare — only possible if polling missed a state change) triggers a refetch + toast.

### `CreateJobModal.tsx`
Wraps Plan 1's `Modal`. Form: `title` (required), `location`, `scheduledAt` (datetime-local → ISO 8601 on submit), `description` (textarea). Client validates against a mirror of `CreateJobBody`. Submit POSTs → on 2xx closes + refetches list + navigates to new detail.

### `AssignCrewModal.tsx`
Two states:

1. **Search:** debounced input (300ms) → `GET /api/profiles?q=<value>` → result rows (displayName + email + role badge). Min 2 chars triggers fetch. Empty input shows "Type to search profiles." Already-assigned profiles in results are visually marked + non-selectable.
2. **Confirm:** click a row → role-selector radio (`crew` | `lead`) → submit → `POST /api/jobs/:id/assign`.

### `useJobsPolling.ts`

```ts
useJobsPolling(scope: 'list' | { detail: string }, intervalMs?: number): void
```

- Mount: immediate fetch.
- `setInterval(intervalMs ?? 15_000)` for periodic refresh.
- Listen to `document.visibilitychange`:
  - on `hidden`: clear interval (do not fetch in background).
  - on `visible`: immediately fetch + restart interval.
- Cleanup on unmount.
- Errors set `jobsStore.markStale(true)`. Success clears the flag.

### `JobsListRoute.tsx`
Composition: header with `[+ Create Job]` button → `useJobsPolling('list')` → 4 status sections (each collapsible) → each section iterates `jobsStore.jobs.filter(j => j.status === status)` rendering `JobCard`.

States:
- Empty store, loading first time → "Loading jobs..." centered
- Zero jobs total → "No jobs yet. [Create your first one]" centered with prominent button
- Zero in section, others have jobs → section header still visible with `(0)`, body says "—"
- `isStale=true` → top strip: `[OFFLINE] Couldn't refresh — showing data from <relative>`

### `JobDetailRoute.tsx`
Composition: back link → `JobStatusBadge` + title + metadata grid → `StatusButtons` row → crew section (list of `CrewAssignment` with unassign-x) → `[+ Assign crew]` button. `useJobsPolling({ detail: params.id })` keeps it fresh.

States:
- Direct nav (no store entry yet) → "Loading..."
- 404 → "This job no longer exists or was never created." + back link
- 403 not_owner → "You're not the foreman for this job." + back link

### `Toast.tsx` (new UI primitive)
~60 lines. Fixed-position bottom-right stack. Each toast:
- `id` (auto), `message`, `tone` (`info` | `error`), `duration` (default 4000ms).
- Auto-dismiss after `duration`; manual dismiss via `[x]`.
- Multiple toasts stack vertically; newest at top of stack.

Exposes a `useToast()` hook: `const toast = useToast(); toast.error('...'); toast.info('...');`. Backing zustand store `toastStore` holds active toasts.

### `ConsoleShell` nav update
Replace the current placeholder block:

```
NAV
  Jobs           ← active link to /console/jobs (NavLink with active styling)
  Map            ← disabled, tooltip "Coming soon"
  (more coming)
```

## Error handling

| Class | Where | Strategy |
|---|---|---|
| `401` on any jobs call | api wrapper | Returns `{ ok: false, status: 401 }`. UI toasts "Session expired. Re-login." + after 2s redirects to `/console/login`. |
| `403 tier_required` | api wrapper | Defensive (Plan 1's `RequireAuth` already gates). Renders the "Upgrade Required" card style. |
| `403 not_owner` | detail route | Renders "Not your job" inline card + back link. No toast. |
| `404` | detail route | Renders "Job not found" inline card + back link. |
| `400 validation` | modals | Pull field errors from `response.details` (zod flatten shape) → render inline under each field. |
| `400 invalid_status_transition` | StatusButtons | Toast "Server rejected this transition (was %from%, tried %to%). Refreshing..." + force refetch. Most likely cause: stale polled data. |
| `409 duplicate_assignment` | AssignCrewModal | Inline error: "Already assigned to this job." |
| `5xx / network` | any | Toast "Network error — retry?" with retry button. Polling continues silently. `jobsStore.isStale = true` triggers header strip. |
| Polling tick fails | useJobsPolling | Silent except `isStale` flag. Don't toast — polling errors are expected. UI shows the stale strip. |

## Testing

### Frontend (Vitest + RTL + MSW; no `DATABASE_URL` needed)

| File | Cases |
|---|---|
| `jobsClient.test.ts` | list/getById/create/update/changeStatus/assignCrew/unassignCrew happy paths; 401/403/400 envelopes; `credentials: 'include'` on every call |
| `profilesClient.test.ts` | search returns profiles; 401 / 400-too-short |
| `jobsStore.test.ts` | setJobs / upsertJob (update existing + insert new) / setDetail / markStale / lastFetchedAt updates / clear |
| `useJobsPolling.test.ts` | mount → immediate fetch; advance 15s → another fetch; `document.hidden=true` clears; `visible` re-fires + restarts; unmount clears |
| `JobsListRoute.test.tsx` | 4 sections render; collapsed defaults correct; counts correct; empty state; 401 redirect |
| `JobDetailRoute.test.tsx` | metadata render; correct StatusButtons per status; click Start → PATCH + refetch; 404 and 403 not_owner render correct cards |
| `CreateJobModal.test.tsx` | valid form → POST + close + navigate; empty title → inline error; mocked 403 → upgrade prompt |
| `AssignCrewModal.test.tsx` | <2 chars → "type to search"; 2+ chars + 300ms debounce → search fires; click result → role selector; submit → POST; 409 → inline error |
| `StatusButtons.test.tsx` | each status → correct buttons; disable during pending; invalid transition response → refetch |
| `JobStatusBadge.test.tsx` | each status → correct tone class + label |
| `Toast.test.tsx` | show + auto-dismiss; manual dismiss; multiple stack |
| `JobCard.test.tsx` | all fields render; "unsch" when scheduledAt null; detail link href correct |

Total new frontend tests: ~50.

### Backend (Jest + supertest; no DATABASE_URL needed — uses in-memory userStore)

| File | Cases |
|---|---|
| `profiles-routes.test.ts` | 401 unauth; 403 tier_required for Solo; 400 validation (q missing / too short); 200 happy path returning matching profiles; result cap at 20 |

### Manual browser verification (mandatory before merge)

`DATABASE_URL` required + migration 003 applied. Walkthrough:

1. Apply migration if not done: `psql "$DATABASE_URL" -f backend/migrations/003_jobs_expansion.sql`
2. Start backend + portal dev servers
3. Login as admin → `/console`
4. Click `Jobs` nav → empty state with `[+ Create Job]`
5. Create one → land on detail → status PLANNED with `[▶ Start]` + `[✕ Cancel]`
6. Click `Start` → IN PROGRESS with `[✓ Complete]` + `[✕ Cancel]`
7. Click `+ Assign crew` → search "admin" → click result → role selector → submit → crew appears
8. Click assignment `[x]` to unassign → list shrinks
9. Click `Complete` → status flips, status buttons disappear (terminal)
10. Back to list → COMPLETE `(1)` count appears, expand to verify
11. Hide tab 30s, return → polling fires immediately, refresh indicator updates
12. Disable network → wait 15s → top strip: `[OFFLINE] Couldn't refresh — showing cached data`
13. Re-enable network → strip clears on next tick
14. Login as Solo user → `/console/jobs` → tier-gate "Upgrade Required" card

### Out of scope (explicit non-goals)

- Automated E2E (Playwright)
- Visual regression
- Cross-browser matrix (Chrome only)
- Load testing
- Tests against real pg (Plan 2's integration tests already cover this; they activate when DATABASE_URL is set)

## Open questions for implementation

1. **Status-section default expanded/collapsed** — code uses literal defaults (planned + in_progress expanded; complete + cancelled collapsed). Persist user's choice across sessions? Decision: NO for MVP. Add later if requested.
2. **Toast `useToast()` hook location** — co-locate with `Toast.tsx` primitive or split into `useToast.ts`? Decision: co-locate; small surface.
3. **`AssignCrewModal` profile filtering on already-assigned** — needs `useJobsStore` access to current `detailCrew` to mark assigned profiles. Verify the modal can read from the same store the detail route writes to.
4. **`useJobsPolling` and React Strict Mode** — Strict Mode double-invokes effects in dev; ensure the immediate-fetch path is idempotent (zustand setter is idempotent; double-fire just costs one extra request). Verify in dev.
5. **Toast stacking limit** — cap at 3? More? Decision: cap at 5 to prevent runaway error storms.

## Phasing recommendation

Single spec; the work is one cohesive vertical. Suggested task order in the implementation plan:

1. Backend: `schemas/profiles.ts` + `profilesRoutes.ts` + mount + tests
2. Frontend: `Toast.tsx` primitive + `useToast` + tests
3. Frontend: `jobsClient.ts` + tests
4. Frontend: `profilesClient.ts` + tests
5. Frontend: `jobsStore.ts` + tests
6. Frontend: `useJobsPolling.ts` + tests
7. Frontend: `JobStatusBadge.tsx` + tests
8. Frontend: `JobCard.tsx` + tests
9. Frontend: `StatusButtons.tsx` + tests
10. Frontend: `CreateJobModal.tsx` + tests
11. Frontend: `AssignCrewModal.tsx` + tests
12. Frontend: `JobsListRoute.tsx` + tests
13. Frontend: `JobDetailRoute.tsx` + tests
14. Frontend: `ConsoleShell.tsx` nav update
15. Frontend: wire routes in `App.tsx`
16. Manual browser walkthrough

Phases 1 + 2 are foundation. Phases 3-6 are data plumbing. Phases 7-13 are UI bottom-up. 14-16 are integration + verification.
