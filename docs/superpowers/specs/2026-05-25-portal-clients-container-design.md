# Portal Clients Container (A0) — Design

**Status:** Approved design, ready for implementation plan
**Date:** 2026-05-25
**Roadmap position:** Slice 1 of the jobs-integration arc. Full order: **Clients (A0) -> Plans -> Expenses -> Price/Invoice**. Price/billing is deferred entirely; it lands in the Price slice once Plans and Expenses feed it real numbers.

---

## Context

The portal is being brought to parity with the Android app's connected flow: Jobs <-> Tasks <-> Plans <-> Expenses <-> Price (the "immersed" invoice). The invoice must bill a real client, but today the portal has **no client entity** — `jobs.client_id UUID` exists as a dangling column with no `clients` table and no FK. The Android app does have clients (`android/.../data/ClientRepository.kt`, `android/.../ui/clients/ClientsScreen.kt`, `ClientDetailScreen.kt`) but they are **name-based and fragile** (no client id; "Jane Smith" vs "jane smith" split into two). The portal is the chance to do it correctly with a real `clients` table and a proper FK.

This slice (A0) builds the lean Clients container and links jobs to it. No pricing/billing anywhere — that follows in later slices. The already-designed GoS invoice template (see memory `reference_gos_invoice_template`, `~/Downloads/Docs/smithnet-preview.html`) is the reference for the later Price slice, not this one.

---

## Goals

- A real `clients` table with a stable `id`, per-org scoped.
- Client CRUD (create / list+search / get / edit / soft-delete).
- A portal Clients **list** (search) and **detail** screen. Detail shows **contact info** (tap to call / email / map) and the client's **linked jobs** only.
- Jobs link to a client via the **real `jobs.client_id` FK**: a client picker on Create Job, and the linked client shown (and changeable) on the job detail.
- A `[Clients]` nav entry (desktop + mobile), foreman-gated like jobs.

## Non-goals (explicitly deferred)

- **Any pricing / billing / invoice linkage** — no billing summary, no `invoices.client_id`, no totals. (Price slice.)
- Full APK ClientDetailScreen aggregations: pending-tasks rollup, recent-closed-jobs, billing breakdown (estimates/deposits/lifetime), activity timeline. (Later, if wanted.)
- Client merge/dedupe, import, hard delete, multi-org sharing beyond existing tenancy.
- Re-tiering jobs/clients down to Solo (jobs are `requireConsoleTier` today; clients follow jobs; changing that is a separate decision).

---

## Architecture

### Backend

**Migration `backend/migrations/030_clients.sql`**
- `CREATE TABLE clients (id UUID PK DEFAULT gen_random_uuid(), organization_id TEXT NOT NULL, created_by TEXT NOT NULL REFERENCES users(id), name TEXT NOT NULL, email TEXT, phone TEXT, address TEXT, company TEXT, notes TEXT, is_deleted BOOLEAN NOT NULL DEFAULT FALSE, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now());`
- Indexes: `(organization_id)` and `(organization_id, lower(name))` for scoped search.
- `ALTER TABLE jobs ADD CONSTRAINT jobs_client_id_fkey FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE SET NULL;` (the `client_id` column already exists from `003_jobs_expansion.sql`).

**Tenancy:** clients are scoped by `organization_id` + `created_by`, mirroring `invoicesService` (the modern model). The implementer resolves the requesting user's org exactly as `invoicesService` does. The jobs<->clients relationship is by `client_id` FK regardless of which ownership column jobs use (`foreman_id`).

**`backend/src/clientsService.ts`** (all functions org-scoped, parameterized queries)
- `createClient(orgId, createdBy, input)` -> client row.
- `listClients(orgId, q?)` -> non-deleted clients, optional case-insensitive name search.
- `getClient(orgId, id)` -> client + its linked jobs (`SELECT ... FROM jobs WHERE client_id = $1`), or null if not found / wrong org.
- `updateClient(orgId, id, patch)` -> updated client (preserve unset fields).
- `softDeleteClient(orgId, id)` -> sets `is_deleted = true`, bumps `updated_at`. Soft delete does not trigger the FK `SET NULL`, so read paths treat a soft-deleted client as "no client": the jobs<->clients join filters `clients.is_deleted = false` and serializes `client: null` for jobs whose client was deleted. The `jobs.client_id` value is left intact for audit/restore.

**`backend/src/schemas/clients.ts`** (zod, `.strict()`)
- `CreateClientBody`: `name` string min 1 max 200 (required); `email` max 200 optional; `phone` max 50 optional; `address` max 500 optional; `company` max 200 optional; `notes` max 5000 optional.
- `UpdateClientBody`: same fields, all optional.

**`backend/src/clientsRoutes.ts`** (mounted at `/api/clients` on the authed apiRouter; add `requireConsoleTier`)
- `GET /api/clients?q=` -> `{ clients: [...] }`
- `POST /api/clients` -> `validateBody(CreateClientBody)` -> 201 `{ client }`
- `GET /api/clients/:id` -> `{ client, jobs: [...] }`; 404 if missing or cross-org
- `PATCH /api/clients/:id` -> `validateBody(UpdateClientBody)` -> `{ client }`
- `DELETE /api/clients/:id` -> soft delete -> 204
- All async handlers wrap `try/catch -> next(err)` (Express 4 does not forward async rejections). PG `23503`/`23505` mapped to 400/409 where relevant.

**Jobs link (modify existing)**
- `backend/src/schemas/jobs.ts`: `CreateJobBody` and `UpdateJobBody` gain `clientId: z.string().uuid().optional()`.
- `backend/src/jobsService.ts`: on create/update with `clientId`, verify the client exists in the caller's org (else reject); job detail + list serialization include `client: { id, name } | null` via a join (soft-deleted client -> `null`).
- `backend/src/jobsRoutes.ts`: serialize the joined `client` in `serializeJob`.

### Portal

**API + state** (mirror the jobs pattern)
- `desktop/portal/src/console/api/clientsClient.ts`: `list(q?)`, `get(id)`, `create(input)`, `update(id, input)`, `remove(id)`. Result types `{ ok: true, ... } | { ok: false, error }`.
- `desktop/portal/src/console/stores/clientsStore.ts` (zustand): `clients: Client[]`, `byId`, `setList`, `upsert`, `remove`, `isStale`, `setStale`.
- `desktop/portal/src/console/hooks/useClientsPolling.ts`: `('list' | { detail: id })`, 15s interval, pause when tab hidden.

**Routes** (`desktop/portal/src/App.tsx`, both behind `RequireForemanTier`)
- `/console/clients` -> `ClientsListRoute`
- `/console/clients/:id` -> `ClientDetailRoute`

**Components**
- `routes/ClientsListRoute.tsx`: search input (filters `clientsStore` by name), list of `ClientCard`, "+ Create client" button -> `CreateClientModal`, offline stale banner (reuse the jobs pattern).
- `routes/ClientDetailRoute.tsx`: `useClientsPolling({ detail: id })`; header (name + `[Edit]`); `ClientContactLines`; a "Jobs" section listing the client's linked jobs, each linking to `/console/jobs/:id`. **No billing.**
- `components/clients/ClientCard.tsx`: name + linked-job count (no money).
- `components/clients/CreateClientModal.tsx`: create + edit; fields name (required) / email / phone / address / company / notes; built on the responsive `Modal` base.
- `components/clients/ClientContactLines.tsx`: phone (`tel:`), email (`mailto:`), address (map link) — tappable, mirroring the APK.

**Job <-> client wiring**
- `components/jobs/CreateJobModal.tsx`: add a client picker — a `<select>` populated from `clientsStore` (label "Client (optional)") plus a "+ new client" affordance that opens `CreateClientModal` and selects the result. Sets `clientId` in the create payload.
- `routes/JobDetailRoute.tsx`: show the linked client (name -> `/console/clients/:id`) with a link/change control.

**Nav**
- `layouts/AppHeader.tsx`: add `[Clients]` `NavButton` inside the `hasForemanTier()` group -> `/console/clients`.
- `layouts/BottomTabBar.tsx`: add `Clients` (foreman-gated). This makes the foreman mobile bar 5 tabs (Home / Clock / Jobs / Clients / Comm) — verify no horizontal overflow at 320 / 375 (the tabs are flex; confirm they still fit and do not regress the overflow fix).

### Security (per smith-net-security)
- `authenticateToken`; identity strictly from `req.user!.id` (never `X-User-Id`).
- `requireConsoleTier` on the clients router.
- `validateBody(...)` zod `.strict()` on every POST/PATCH.
- Parameterized `pg` queries everywhere; per-org isolation enforced in the service (cross-org access -> 404).
- Soft delete (`is_deleted`); no destructive hard delete.
- Pure CRUD: no inline LLM calls, no fire-and-forget (CLAUDE.md Rules 1 & 2 not triggered).

### Error handling
- 400 validation: `{ error, code: 'validation', details }` (zod).
- 403 tier: `requireConsoleTier` for non-foreman.
- 404: missing or cross-org client; job detail with a soft-deleted client serializes `client: null`.
- Creating a job with a `clientId` not in the caller's org -> 400 (validated before insert; FK `23503` also mapped to 400 as a backstop).
- Portal: error toasts on failed mutations; optimistic `upsert` with reconcile on the refetch (mirror jobs).

---

## Files

**Backend**
- Create: `backend/migrations/030_clients.sql`, `backend/src/clientsService.ts`, `backend/src/clientsRoutes.ts`, `backend/src/schemas/clients.ts`
- Modify: `backend/src/schemas/jobs.ts`, `backend/src/jobsService.ts`, `backend/src/jobsRoutes.ts`, and the apiRouter mount point (where other routers are mounted) to add the clients router
- Tests: `backend/src/__tests__/clientsService.test.ts`, `backend/src/__tests__/clientsRoutes.test.ts`, plus job<->client cases added to the jobs tests

**Portal**
- Create: `desktop/portal/src/console/api/clientsClient.ts`, `stores/clientsStore.ts`, `hooks/useClientsPolling.ts`, `routes/ClientsListRoute.tsx`, `routes/ClientDetailRoute.tsx`, `components/clients/ClientCard.tsx`, `components/clients/CreateClientModal.tsx`, `components/clients/ClientContactLines.tsx`
- Modify: `desktop/portal/src/App.tsx`, `components/jobs/CreateJobModal.tsx`, `routes/JobDetailRoute.tsx`, `layouts/AppHeader.tsx`, `layouts/BottomTabBar.tsx`
- Tests: `__tests__` for clientsStore, ClientsListRoute, CreateClientModal, ClientDetailRoute, and the CreateJobModal client picker

---

## Testing

- **Backend (jest, DB-gated via `isPgEnabled()`; run `npx jest <path>` with `DATABASE_URL` exported):**
  - clientsService: create / list / get / update / softDelete; org isolation (user B cannot see user A's client -> not found); name search filter.
  - clientsRoutes: auth required; `requireConsoleTier` -> 403 for non-foreman; zod `.strict()` rejects unknown/invalid fields; 404 cross-org.
  - jobs<->client: create job with a valid `clientId`; reject a foreign `clientId`; soft-deleting a client makes the job serialize `client: null`; job detail/list include `client`.
- **Portal (vitest/jsdom):**
  - clientsStore reducers; ClientsListRoute renders + search filters; CreateClientModal submit (create + edit); ClientDetailRoute shows contact links + linked jobs; CreateJobModal client picker sets `clientId`; `[Clients]` nav shows for foreman / hidden otherwise; no horizontal overflow at 375 with the added tab.

## Verification

- Backend: `npx jest` on the new suites with `DATABASE_URL` set; restart `:3030` (ts-node-dev serves stale code — manual restart needed to verify live).
- Portal: `npm run test:run`, `npm run build`, `tsc --noEmit` all clean.
- Live: clients are foreman-gated, so live verification needs a **foreman-tier login** (the current test user `clocktest-0525` is Solo and will be redirected). Promote a test user to foreman or create one to click through list -> create -> detail -> link-on-job.
