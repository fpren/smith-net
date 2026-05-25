# Portal Clients Container (A0) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a first-class Clients container to the portal (clients table + CRUD + list/detail screens) and link jobs to a real client, mirroring the existing Jobs feature. No pricing/billing (deferred to the Price slice).

**Architecture:** Backend mirrors `jobsRoutes`/`jobsService` exactly — a `clients` table owner-scoped by `owner_id = req.user!.id`, a `clientsService` with a `mapClientRow` mapper, a `clientsRouter` that self-applies `authenticateToken` + `requireConsoleTier`, and a `requireClientOwner` middleware mirroring `requireJobOwner`. Portal mirrors the jobs store/api-client/polling-hook/routes patterns (zustand + a per-client `call<T>` helper + a visibility-aware polling hook). Jobs already accept `clientId` on create; we add owner-validation, a `client { id, name }` join on job detail, `clientId` on job update, and the portal client picker + nav.

**Tech Stack:** Backend: Node/Express + pg + zod + Jest (DB-gated via `isPgEnabled()`). Portal: Vite + React 18 + TS (strict) + zustand + Tailwind + Vitest/jsdom + @testing-library/react + MSW.

**Spec:** `docs/superpowers/specs/2026-05-25-portal-clients-container-design.md`

**Conventions (must follow):** No emoji anywhere (UI/code/commits). Identity from `req.user!.id` only (never `X-User-Id`). `validateBody` zod `.strict()` on every POST/PATCH. Parameterized pg queries. Owner isolation (cross-owner -> 404). Backend routes use inline `try/catch -> res.status(5xx)` (NOT `next(err)`), mirroring `jobsRoutes`. Commit trailer: `Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>`. Stage only named files (never `git add -A`).

---

## File Structure

**Backend (create)**
- `backend/migrations/030_clients.sql` — `clients` table + `jobs.client_id` FK
- `backend/src/schemas/clients.ts` — `CreateClientBody`, `UpdateClientBody` (zod `.strict()`)
- `backend/src/clientsService.ts` — `Client` type, `mapClientRow`, CRUD (owner-scoped)
- `backend/src/middleware/requireClientOwner.ts` — owner guard (mirrors `requireJobOwner`)
- `backend/src/clientsRoutes.ts` — `clientsRouter`
- `backend/src/__tests__/clients-routes.test.ts` — routes + isolation tests

**Backend (modify)**
- `backend/src/server.ts` — mount `clientsRouter` at `/api/clients`
- `backend/src/schemas/jobs.ts` — add `clientId` to `UpdateJobBody`
- `backend/src/jobsService.ts` — `listByClient`, `validateClientOwnership` helper, `getById` client join, `update` handles `client_id`, `Job` type gains `client`
- `backend/src/jobsRoutes.ts` — validate `clientId` ownership on create/update
- `backend/src/__tests__/jobs-routes.test.ts` — add job<->client cases

**Portal (create)**
- `desktop/portal/src/console/api/clientsClient.ts` — `Client` type + `clientsClient`
- `desktop/portal/src/console/stores/clientsStore.ts` — zustand store
- `desktop/portal/src/console/hooks/useClientsPolling.ts` — polling hook
- `desktop/portal/src/console/components/clients/ClientContactLines.tsx`
- `desktop/portal/src/console/components/clients/ClientCard.tsx`
- `desktop/portal/src/console/components/clients/CreateClientModal.tsx`
- `desktop/portal/src/console/routes/ClientsListRoute.tsx`
- `desktop/portal/src/console/routes/ClientDetailRoute.tsx`
- tests under the matching `__tests__/` dirs

**Portal (modify)**
- `desktop/portal/src/App.tsx` — register `/console/clients` + `/console/clients/:id`
- `desktop/portal/src/console/layouts/AppHeader.tsx` — `[Clients]` desktop nav
- `desktop/portal/src/console/layouts/BottomTabBar.tsx` — `[Clients]` mobile tab
- `desktop/portal/src/console/components/jobs/CreateJobModal.tsx` — client picker
- `desktop/portal/src/console/routes/JobDetailRoute.tsx` — show linked client
- `desktop/portal/src/console/test/msw-handlers.ts` — `/api/clients` handlers

---

## Backend setup (run once before backend tasks)

Backend tests are DB-gated. Export the dev DB URL in the shell you run jest from:
```bash
export DATABASE_URL=postgresql://localhost/smithnet
```
Run a single suite with: `npx jest src/__tests__/clients-routes.test.ts --runInBand`. After backend route changes, restart `:3030` to verify live (`ts-node-dev` serves stale code).

---

### Task 1: Migration — clients table + jobs.client_id FK

**Files:**
- Create: `backend/migrations/030_clients.sql`

- [ ] **Step 1: Write the migration**

```sql
-- backend/migrations/030_clients.sql
-- Clients container (A0). Owner-scoped by owner_id (mirrors jobs.foreman_id).
-- Also gives the pre-existing jobs.client_id column a real FK home.

CREATE TABLE IF NOT EXISTS clients (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id    TEXT NOT NULL REFERENCES profiles(id),
  name        TEXT NOT NULL,
  email       TEXT,
  phone       TEXT,
  address     TEXT,
  company     TEXT,
  notes       TEXT,
  is_deleted  BOOLEAN NOT NULL DEFAULT FALSE,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_clients_owner ON clients (owner_id) WHERE is_deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_clients_owner_name ON clients (owner_id, lower(name));

-- jobs.client_id already exists (003_jobs_expansion.sql) but has no FK. Add it now.
ALTER TABLE jobs
  ADD CONSTRAINT jobs_client_id_fkey
  FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE SET NULL;
```

- [ ] **Step 2: Apply the migration**

Run the project's migration runner (check `backend/package.json` scripts for the migrate command, e.g. `npm run migrate`). Expected: applies `030_clients.sql` with no error.

Verify the table exists:
```bash
/opt/homebrew/Cellar/postgresql@17/17.7_1/bin/psql -d smithnet -c "\d clients"
```
Expected: shows the `clients` columns + indexes.

- [ ] **Step 3: Commit**

```bash
git add backend/migrations/030_clients.sql
git commit -m "feat(clients): migration for clients table + jobs.client_id FK"
```

---

### Task 2: Zod schemas for clients

**Files:**
- Create: `backend/src/schemas/clients.ts`

- [ ] **Step 1: Write the schemas** (mirrors `schemas/jobs.ts` doubled-export idiom + `.strict()`)

```ts
// backend/src/schemas/clients.ts
import { z } from 'zod';

export const CreateClientBody = z.object({
  name:    z.string().trim().min(1).max(200),
  email:   z.string().trim().max(200).optional(),
  phone:   z.string().trim().max(50).optional(),
  address: z.string().trim().max(500).optional(),
  company: z.string().trim().max(200).optional(),
  notes:   z.string().trim().max(5000).optional(),
}).strict();
export type CreateClientBody = z.infer<typeof CreateClientBody>;

export const UpdateClientBody = z.object({
  name:    z.string().trim().min(1).max(200).optional(),
  email:   z.string().trim().max(200).optional().nullable(),
  phone:   z.string().trim().max(50).optional().nullable(),
  address: z.string().trim().max(500).optional().nullable(),
  company: z.string().trim().max(200).optional().nullable(),
  notes:   z.string().trim().max(5000).optional().nullable(),
}).strict();
export type UpdateClientBody = z.infer<typeof UpdateClientBody>;
```

- [ ] **Step 2: Typecheck**

Run: `cd backend && npx tsc --noEmit`
Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add backend/src/schemas/clients.ts
git commit -m "feat(clients): zod schemas for client create/update"
```

---

### Task 3: clientsService (owner-scoped CRUD)

**Files:**
- Create: `backend/src/clientsService.ts`

- [ ] **Step 1: Write the service** (mirrors `jobsService` — `requirePg`, `mapClientRow`, `uuidv4` ids, owner-scoped queries)

```ts
// backend/src/clientsService.ts
import { pg, isPgEnabled } from './db';
import { v4 as uuidv4 } from 'uuid';

export interface Client {
  id: string;
  ownerId: string;
  name: string;
  email: string | null;
  phone: string | null;
  address: string | null;
  company: string | null;
  notes: string | null;
  createdAt: Date;
  updatedAt: Date;
}

function requirePg() {
  if (!isPgEnabled() || !pg) throw new Error('[ClientsService] Postgres client not initialized');
  return pg;
}

function mapClientRow(row: any): Client {
  return {
    id: row.id,
    ownerId: row.owner_id,
    name: row.name,
    email: row.email,
    phone: row.phone,
    address: row.address,
    company: row.company,
    notes: row.notes,
    createdAt: new Date(row.created_at),
    updatedAt: new Date(row.updated_at),
  };
}

export interface CreateClientInput {
  ownerId: string;
  name: string;
  email?: string;
  phone?: string;
  address?: string;
  company?: string;
  notes?: string;
}

export async function create(input: CreateClientInput): Promise<Client> {
  const db = requirePg();
  const id = uuidv4();
  const now = new Date();
  const { rows } = await db.query(
    `INSERT INTO clients (id, owner_id, name, email, phone, address, company, notes, created_at, updated_at)
     VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $9)
     RETURNING *`,
    [id, input.ownerId, input.name, input.email ?? null, input.phone ?? null,
     input.address ?? null, input.company ?? null, input.notes ?? null, now]
  );
  return mapClientRow(rows[0]);
}

export async function listByOwner(ownerId: string, q?: string): Promise<Client[]> {
  const db = requirePg();
  if (q && q.trim()) {
    const { rows } = await db.query(
      `SELECT * FROM clients
        WHERE owner_id = $1 AND is_deleted = FALSE AND name ILIKE $2
        ORDER BY lower(name) ASC`,
      [ownerId, `%${q.trim()}%`]
    );
    return rows.map(mapClientRow);
  }
  const { rows } = await db.query(
    `SELECT * FROM clients WHERE owner_id = $1 AND is_deleted = FALSE ORDER BY lower(name) ASC`,
    [ownerId]
  );
  return rows.map(mapClientRow);
}

// Owner-scoped get; returns null for missing OR cross-owner OR soft-deleted.
export async function getById(ownerId: string, id: string): Promise<Client | null> {
  const db = requirePg();
  const { rows } = await db.query(
    `SELECT * FROM clients WHERE id = $1 AND owner_id = $2 AND is_deleted = FALSE`,
    [id, ownerId]
  );
  return rows.length === 0 ? null : mapClientRow(rows[0]);
}

export interface UpdateClientPatch {
  name?: string;
  email?: string | null;
  phone?: string | null;
  address?: string | null;
  company?: string | null;
  notes?: string | null;
}

export async function update(ownerId: string, id: string, patch: UpdateClientPatch): Promise<Client | null> {
  const db = requirePg();
  const sets: string[] = [];
  const vals: any[] = [];
  let i = 1;
  for (const [col, key] of [['name','name'],['email','email'],['phone','phone'],
       ['address','address'],['company','company'],['notes','notes']] as const) {
    if (key in patch) { sets.push(`${col} = $${i++}`); vals.push((patch as any)[key]); }
  }
  if (sets.length === 0) return getById(ownerId, id);
  sets.push(`updated_at = now()`);
  vals.push(id, ownerId);
  const { rows } = await db.query(
    `UPDATE clients SET ${sets.join(', ')}
      WHERE id = $${i++} AND owner_id = $${i++} AND is_deleted = FALSE
      RETURNING *`,
    vals
  );
  return rows.length === 0 ? null : mapClientRow(rows[0]);
}

export async function softDelete(ownerId: string, id: string): Promise<boolean> {
  const db = requirePg();
  const { rowCount } = await db.query(
    `UPDATE clients SET is_deleted = TRUE, updated_at = now()
      WHERE id = $1 AND owner_id = $2 AND is_deleted = FALSE`,
    [id, ownerId]
  );
  return (rowCount ?? 0) > 0;
}
```

- [ ] **Step 2: Typecheck**

Run: `cd backend && npx tsc --noEmit`
Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add backend/src/clientsService.ts
git commit -m "feat(clients): owner-scoped clientsService CRUD"
```

---

### Task 4: requireClientOwner middleware

**Files:**
- Create: `backend/src/middleware/requireClientOwner.ts`

- [ ] **Step 1: Write the middleware** (mirrors `requireJobOwner` exactly; this is the ONLY place `next(err)` is used)

```ts
// backend/src/middleware/requireClientOwner.ts
import { Response, NextFunction } from 'express';
import { AuthenticatedRequest } from '../auth';
import * as clientsService from '../clientsService';

export interface ClientOwnerRequest extends AuthenticatedRequest {
  client?: clientsService.Client;
}

export async function requireClientOwner(req: ClientOwnerRequest, res: Response, next: NextFunction) {
  const id = req.params.id;
  if (!id) return res.status(400).json({ error: 'Missing client id' });
  try {
    const client = await clientsService.getById(req.user!.id, id);
    if (!client) return res.status(404).json({ error: 'Client not found' });
    req.client = client;
    next();
  } catch (err) {
    next(err);
  }
}
```

Note: `getById` is already owner-scoped, so a cross-owner id returns `null` -> 404 (no separate owner check needed).

- [ ] **Step 2: Typecheck**

Run: `cd backend && npx tsc --noEmit`
Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add backend/src/middleware/requireClientOwner.ts
git commit -m "feat(clients): requireClientOwner middleware"
```

---

### Task 5: clientsRoutes + mount + routes tests

**Files:**
- Create: `backend/src/clientsRoutes.ts`, `backend/src/__tests__/clients-routes.test.ts`
- Modify: `backend/src/server.ts`

- [ ] **Step 1: Write the failing routes test** (mirrors `jobs-routes.test.ts` harness: `describeDb`, `createUserAndProfile`, `generateTokens`, `afterEach` cleanup, top-level `afterAll` with `pg?.end()`)

```ts
// backend/src/__tests__/clients-routes.test.ts
import express from 'express';
import request from 'supertest';
import cookieParser from 'cookie-parser';
import { authRouter } from '../authRoutes';
import { clientsRouter } from '../clientsRoutes';
import { generateTokens, UserRole, userStore } from '../auth';
import { createUserAndProfile } from '../jobsService';
import { pg, isPgEnabled } from '../db';

const describeDb = isPgEnabled() ? describe : describe.skip;

function buildApp() {
  const app = express();
  app.use(express.json());
  app.use(cookieParser());
  app.use('/api/auth', authRouter);
  app.use('/api/clients', clientsRouter);
  return app;
}

async function foreman(suffix: string): Promise<{ id: string; token: string }> {
  const user = await createUserAndProfile({
    email: `foreman-clients-${suffix}-${Date.now()}@example.com`,
    password: 'password123',
    displayName: `Foreman ${suffix}`,
    role: UserRole.FOREMAN,
  });
  const { accessToken } = await generateTokens(user);
  return { id: user.id, token: accessToken };
}

describeDb('clients routes', () => {
  const app = buildApp();

  afterEach(async () => {
    if (!isPgEnabled() || !pg) return;
    await pg.query(`DELETE FROM clients`);
    await pg.query(`DELETE FROM profiles WHERE email LIKE 'foreman-clients-%' OR email LIKE 'solo-clients-%'`);
    await pg.query(`DELETE FROM users WHERE email LIKE 'foreman-clients-%' OR email LIKE 'solo-clients-%'`);
  });
  afterAll(async () => { await pg?.end(); });

  it('401 without auth', async () => {
    const res = await request(app).get('/api/clients');
    expect(res.status).toBe(401);
  });

  it('403 tier_required for Solo', async () => {
    const u = await userStore.createUser(`solo-clients-${Date.now()}@example.com`, 'password123', 'Solo', UserRole.SOLO);
    const { accessToken } = await generateTokens(u);
    const res = await request(app).get('/api/clients').set('Authorization', `Bearer ${accessToken}`);
    expect(res.status).toBe(403);
    expect(res.body.code).toBe('tier_required');
  });

  it('creates, lists, gets, updates, soft-deletes a client', async () => {
    const f = await foreman('crud');
    const create = await request(app).post('/api/clients')
      .set('Authorization', `Bearer ${f.token}`)
      .send({ name: 'Acme Co', email: 'a@acme.test' });
    expect(create.status).toBe(201);
    const id = create.body.client.id;
    expect(create.body.client.name).toBe('Acme Co');

    const list = await request(app).get('/api/clients').set('Authorization', `Bearer ${f.token}`);
    expect(list.body.clients).toHaveLength(1);

    const get = await request(app).get(`/api/clients/${id}`).set('Authorization', `Bearer ${f.token}`);
    expect(get.body.client.id).toBe(id);
    expect(get.body.jobs).toEqual([]);

    const patch = await request(app).patch(`/api/clients/${id}`)
      .set('Authorization', `Bearer ${f.token}`).send({ phone: '555-1234' });
    expect(patch.body.client.phone).toBe('555-1234');

    const del = await request(app).delete(`/api/clients/${id}`).set('Authorization', `Bearer ${f.token}`);
    expect(del.status).toBe(204);
    const after = await request(app).get('/api/clients').set('Authorization', `Bearer ${f.token}`);
    expect(after.body.clients).toHaveLength(0);
  });

  it('isolates clients across owners (404 cross-owner)', async () => {
    const a = await foreman('iso-a');
    const b = await foreman('iso-b');
    const created = await request(app).post('/api/clients')
      .set('Authorization', `Bearer ${a.token}`).send({ name: 'A only' });
    const id = created.body.client.id;
    const res = await request(app).get(`/api/clients/${id}`).set('Authorization', `Bearer ${b.token}`);
    expect(res.status).toBe(404);
  });

  it('rejects unknown fields (zod strict)', async () => {
    const f = await foreman('strict');
    const res = await request(app).post('/api/clients')
      .set('Authorization', `Bearer ${f.token}`).send({ name: 'X', bogus: 1 });
    expect(res.status).toBe(400);
    expect(res.body.code).toBe('validation');
  });
});
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd backend && npx jest src/__tests__/clients-routes.test.ts --runInBand`
Expected: FAIL — `Cannot find module '../clientsRoutes'`.

- [ ] **Step 3: Write the router** (mirrors `jobsRoutes`: router self-applies auth+tier, inline `try/catch -> res.status`, init log)

```ts
// backend/src/clientsRoutes.ts
import { Router, Response } from 'express';
import { authenticateToken, AuthenticatedRequest } from './auth';
import { requireConsoleTier } from './middleware/requireConsoleTier';
import { requireClientOwner, ClientOwnerRequest } from './middleware/requireClientOwner';
import { validateBody } from './middleware/validate';
import * as clientsService from './clientsService';
import * as jobsService from './jobsService';
import { requestLogger } from './log';
import { CreateClientBody, UpdateClientBody } from './schemas/clients';

export const clientsRouter = Router();
clientsRouter.use(authenticateToken, requireConsoleTier);

clientsRouter.get('/', async (req: AuthenticatedRequest, res: Response) => {
  try {
    const q = typeof req.query.q === 'string' ? req.query.q : undefined;
    const clients = await clientsService.listByOwner(req.user!.id, q);
    res.json({ clients });
  } catch (e: any) {
    requestLogger().error({ event: 'clients_list_error', err: e }, 'clients list error');
    res.status(500).json({ error: 'Failed to list clients' });
  }
});

clientsRouter.post('/', validateBody(CreateClientBody), async (req: AuthenticatedRequest, res: Response) => {
  try {
    const body = req.body as CreateClientBody;
    const client = await clientsService.create({ ownerId: req.user!.id, ...body });
    res.status(201).json({ client });
  } catch (e: any) {
    requestLogger().error({ event: 'clients_create_error', err: e }, 'clients create error');
    res.status(500).json({ error: 'Failed to create client' });
  }
});

clientsRouter.get('/:id', requireClientOwner, async (req: ClientOwnerRequest, res: Response) => {
  try {
    const jobs = await jobsService.listByClient(req.params.id, req.user!.id);
    res.json({ client: req.client, jobs });
  } catch (e: any) {
    requestLogger().error({ event: 'clients_get_error', err: e }, 'clients get error');
    res.status(500).json({ error: 'Failed to get client' });
  }
});

clientsRouter.patch('/:id', requireClientOwner, validateBody(UpdateClientBody), async (req: ClientOwnerRequest, res: Response) => {
  try {
    const client = await clientsService.update(req.user!.id, req.params.id, req.body as UpdateClientBody);
    if (!client) return res.status(404).json({ error: 'Client not found' });
    res.json({ client });
  } catch (e: any) {
    requestLogger().error({ event: 'clients_update_error', err: e }, 'clients update error');
    res.status(500).json({ error: 'Failed to update client' });
  }
});

clientsRouter.delete('/:id', requireClientOwner, async (req: ClientOwnerRequest, res: Response) => {
  try {
    await clientsService.softDelete(req.user!.id, req.params.id);
    res.status(204).end();
  } catch (e: any) {
    requestLogger().error({ event: 'clients_delete_error', err: e }, 'clients delete error');
    res.status(500).json({ error: 'Failed to delete client' });
  }
});

requestLogger().info({ event: 'clients_routes_initialized' }, 'clients routes initialized');
```

This references `jobsService.listByClient` — defined in Task 6. If implementing strictly in order, temporarily stub `const jobs: any[] = [];` in the GET `/:id` handler and replace with `jobsService.listByClient(...)` in Task 6. (Recommended: do Task 6's `listByClient` first, then this handler is complete.)

- [ ] **Step 4: Mount the router in server.ts**

In `backend/src/server.ts`, add the import near the other route imports (~line 25):
```ts
import { clientsRouter } from './clientsRoutes';
```
And mount it near the jobs mount (~line 151), right after `app.use('/api/jobs', jobsRouter);`:
```ts
// Mount Clients API
app.use('/api/clients', clientsRouter);
```

- [ ] **Step 5: Run the test to verify it passes**

Run: `cd backend && npx jest src/__tests__/clients-routes.test.ts --runInBand`
Expected: PASS (all cases). If `listByClient` is not yet defined, the `jobs: []` assertion still passes with the temporary stub.

- [ ] **Step 6: Commit**

```bash
git add backend/src/clientsRoutes.ts backend/src/__tests__/clients-routes.test.ts backend/src/server.ts
git commit -m "feat(clients): clients routes + mount + isolation tests"
```

---

### Task 6: Job <-> client wiring (listByClient, ownership validation, client join, update)

**Files:**
- Modify: `backend/src/jobsService.ts`, `backend/src/schemas/jobs.ts`, `backend/src/jobsRoutes.ts`
- Test: `backend/src/__tests__/jobs-routes.test.ts`

- [ ] **Step 1: Write the failing test** (append to `jobs-routes.test.ts`; the harness/`buildApp` there must also mount `clientsRouter` and `authRouter` — add `app.use('/api/clients', clientsRouter)` to its `buildApp` and import `clientsRouter`)

```ts
describeDb('jobs <-> client link', () => {
  const app = buildApp(); // ensure buildApp mounts /api/clients too

  it('creates a job linked to a client and returns client on detail', async () => {
    const f = await createForemanAndLogin('link');
    const c = await request(app).post('/api/clients')
      .set('Authorization', `Bearer ${f.token}`).send({ name: 'Linked Co' });
    const clientId = c.body.client.id;

    const job = await request(app).post('/api/jobs')
      .set('Authorization', `Bearer ${f.token}`).send({ title: 'Wired job', clientId });
    expect(job.status).toBe(201);

    const detail = await request(app).get(`/api/jobs/${job.body.job.id}`)
      .set('Authorization', `Bearer ${f.token}`);
    expect(detail.body.job.client).toEqual({ id: clientId, name: 'Linked Co' });
  });

  it('rejects a job create with a foreign clientId (400)', async () => {
    const a = await createForemanAndLogin('own');
    const b = await createForemanAndLogin('other');
    const c = await request(app).post('/api/clients')
      .set('Authorization', `Bearer ${a.token}`).send({ name: 'A client' });
    const res = await request(app).post('/api/jobs')
      .set('Authorization', `Bearer ${b.token}`).send({ title: 'X', clientId: c.body.client.id });
    expect(res.status).toBe(400);
  });
});
```

Add to the `afterEach` cleanup in that file: `await pg.query('DELETE FROM clients');` (before the profiles/users deletes, since jobs.client_id FK references clients).

- [ ] **Step 2: Run to verify it fails**

Run: `cd backend && npx jest src/__tests__/jobs-routes.test.ts --runInBand`
Expected: FAIL — `detail.body.job.client` is undefined; foreign-client create returns 201 not 400.

- [ ] **Step 3a: Add `listByClient` + `client` join to jobsService**

In `backend/src/jobsService.ts`, add `client` to the `Job` interface:
```ts
  // add to interface Job:
  client: { id: string; name: string } | null;
```
Make `mapJobRow` tolerant of an optional joined `client_name` (add this line to the returned object):
```ts
    client: row.client_name ? { id: row.client_id, name: row.client_name } : null,
```
Change `getById` to LEFT JOIN clients:
```ts
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
```
Add `listByClient` and an ownership-validation helper:
```ts
export async function listByClient(clientId: string, foremanId: string): Promise<Job[]> {
  const db = requirePg();
  const { rows } = await db.query(
    `SELECT * FROM jobs WHERE client_id = $1 AND foreman_id = $2 ORDER BY created_at DESC`,
    [clientId, foremanId]
  );
  return rows.map(mapJobRow);
}

// Returns true if the client exists and is owned by this foreman.
export async function clientBelongsToOwner(clientId: string, ownerId: string): Promise<boolean> {
  const db = requirePg();
  const { rowCount } = await db.query(
    `SELECT 1 FROM clients WHERE id = $1 AND owner_id = $2 AND is_deleted = FALSE`,
    [clientId, ownerId]
  );
  return (rowCount ?? 0) > 0;
}
```
(`listByForeman` and the create `RETURNING *` stay unchanged — they have no `client_name`, so `mapJobRow` yields `client: null` there, which is fine; the list view does not show the client name.)

- [ ] **Step 3b: Validate clientId ownership in the create route**

In `backend/src/jobsRoutes.ts` POST `/` handler, before calling `jobsService.create`, add:
```ts
    if (body.clientId && !(await jobsService.clientBelongsToOwner(body.clientId, req.user!.id))) {
      return res.status(400).json({ error: 'Unknown client', code: 'validation' });
    }
```

- [ ] **Step 3c: Add clientId to UpdateJobBody + handle in update route + service**

In `backend/src/schemas/jobs.ts`, add to `UpdateJobBody`:
```ts
  clientId: z.string().uuid().optional().nullable(),
```
In the PATCH `/:id` route handler (`jobsRoutes.ts`), when `clientId` is present and non-null, validate ownership the same way:
```ts
    if (body.clientId && !(await jobsService.clientBelongsToOwner(body.clientId, req.user!.id))) {
      return res.status(400).json({ error: 'Unknown client', code: 'validation' });
    }
```
Ensure `jobsService.update` writes `client_id` when present (add `client_id` to its dynamic SET, mirroring how it handles other patch fields; if `clientId === null`, set it to NULL to unlink).

- [ ] **Step 4: Run to verify it passes**

Run: `cd backend && npx jest src/__tests__/jobs-routes.test.ts src/__tests__/clients-routes.test.ts --runInBand`
Expected: PASS (both suites). Then full backend run: `npx jest --runInBand` — expected: all green.

- [ ] **Step 5: Commit**

```bash
git add backend/src/jobsService.ts backend/src/schemas/jobs.ts backend/src/jobsRoutes.ts backend/src/__tests__/jobs-routes.test.ts
git commit -m "feat(clients): wire jobs<->client (validate ownership, client join on detail, link/unlink on update)"
```

---

## Portal setup (run from `desktop/portal/`)

Tests: `npm run test:run`. Build: `npm run build`. Typecheck: `npx tsc --noEmit`. Tests use MSW with `onUnhandledRequest: 'error'`, so any new `/api/clients` call needs a handler in `src/console/test/msw-handlers.ts`.

---

### Task 7: clientsClient (API) + MSW handlers

**Files:**
- Create: `desktop/portal/src/console/api/clientsClient.ts`, `desktop/portal/src/console/api/__tests__/clientsClient.test.ts`
- Modify: `desktop/portal/src/console/test/msw-handlers.ts`

- [ ] **Step 1: Add MSW handlers** (append to the `handlers` array in `msw-handlers.ts`)

```ts
  http.get('/api/clients', () =>
    HttpResponse.json({ clients: [
      { id: 'client-1', ownerId: 'f-1', name: 'Test Client', email: 't@c.test',
        phone: null, address: null, company: null, notes: null,
        createdAt: '2026-05-11T10:00:00Z', updatedAt: '2026-05-11T10:00:00Z' },
    ] })),
  http.post('/api/clients', async ({ request }) => {
    const body = (await request.json()) as { name: string };
    return HttpResponse.json({ client: {
      id: 'new-client-id', ownerId: 'f-1', name: body.name, email: null,
      phone: null, address: null, company: null, notes: null,
      createdAt: '2026-05-11T10:00:00Z', updatedAt: '2026-05-11T10:00:00Z' },
    }, { status: 201 });
  }),
  http.get('/api/clients/:id', ({ params }) =>
    HttpResponse.json({
      client: { id: params.id, ownerId: 'f-1', name: 'Test Client', email: 't@c.test',
        phone: null, address: null, company: null, notes: null,
        createdAt: '2026-05-11T10:00:00Z', updatedAt: '2026-05-11T10:00:00Z' },
      jobs: [],
    })),
```

- [ ] **Step 2: Write the failing client test**

```tsx
// desktop/portal/src/console/api/__tests__/clientsClient.test.ts
import { describe, it, expect } from 'vitest';
import { clientsClient } from '../clientsClient';

describe('clientsClient', () => {
  it('list returns clients', async () => {
    const r = await clientsClient.list();
    expect(r.ok).toBe(true);
    if (r.ok) expect(r.clients[0].name).toBe('Test Client');
  });
  it('create returns the new client on 201', async () => {
    const r = await clientsClient.create({ name: 'Brand new' });
    expect(r.ok).toBe(true);
    if (r.ok) expect(r.client.id).toBe('new-client-id');
  });
});
```

- [ ] **Step 3: Run to verify it fails**

Run: `npm run test:run -- clientsClient`
Expected: FAIL — cannot find `../clientsClient`.

- [ ] **Step 4: Write the API client** (copy the private `call<T>` helper from `jobsClient.ts`)

```ts
// desktop/portal/src/console/api/clientsClient.ts
export interface Client {
  id: string;
  ownerId: string;
  name: string;
  email: string | null;
  phone: string | null;
  address: string | null;
  company: string | null;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
}

export type ClientsResult<T> =
  | ({ ok: true } & T)
  | { ok: false; status: number; error: string; details?: any; code?: string };

interface JsonInit { method?: string; body?: unknown }

async function call<T>(path: string, init: JsonInit = {}): Promise<ClientsResult<T>> {
  const res = await fetch(path, {
    method: init.method ?? 'GET',
    credentials: 'include',
    headers: init.body !== undefined ? { 'Content-Type': 'application/json' } : undefined,
    body: init.body !== undefined ? JSON.stringify(init.body) : undefined,
  });
  if (res.status === 204) return { ok: true } as ClientsResult<T>;
  if (!res.ok) {
    const e = await res.json().catch(() => ({ error: res.statusText }));
    return { ok: false, status: res.status, error: e.error || 'Request failed', details: e.details, code: e.code };
  }
  return { ok: true, ...(await res.json() as T) } as ClientsResult<T>;
}

// `jobs` reuses the Job type from jobsClient at the call site; typed loosely here to avoid a cycle.
interface ListResp { clients: Client[] }
interface OneResp { client: Client; jobs: any[] }
interface MutateResp { client: Client }

export interface CreateClientInput {
  name: string; email?: string; phone?: string; address?: string; company?: string; notes?: string;
}
export interface UpdateClientInput {
  name?: string; email?: string | null; phone?: string | null;
  address?: string | null; company?: string | null; notes?: string | null;
}

export const clientsClient = {
  list: (q?: string) => call<ListResp>(`/api/clients${q ? `?q=${encodeURIComponent(q)}` : ''}`),
  getById: (id: string) => call<OneResp>(`/api/clients/${encodeURIComponent(id)}`),
  create: (input: CreateClientInput) => call<MutateResp>('/api/clients', { method: 'POST', body: input }),
  update: (id: string, patch: UpdateClientInput) =>
    call<MutateResp>(`/api/clients/${encodeURIComponent(id)}`, { method: 'PATCH', body: patch }),
  remove: (id: string) => call<{}>(`/api/clients/${encodeURIComponent(id)}`, { method: 'DELETE' }),
};
```

- [ ] **Step 5: Run to verify it passes**

Run: `npm run test:run -- clientsClient`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add desktop/portal/src/console/api/clientsClient.ts desktop/portal/src/console/api/__tests__/clientsClient.test.ts desktop/portal/src/console/test/msw-handlers.ts
git commit -m "feat(clients): portal clientsClient + MSW handlers"
```

---

### Task 8: clientsStore (zustand)

**Files:**
- Create: `desktop/portal/src/console/stores/clientsStore.ts`, `desktop/portal/src/console/stores/__tests__/clientsStore.test.ts`

- [ ] **Step 1: Write the failing store test**

```ts
// desktop/portal/src/console/stores/__tests__/clientsStore.test.ts
import { describe, it, expect, beforeEach } from 'vitest';
import { useClientsStore } from '../clientsStore';
import type { Client } from '../../api/clientsClient';

const c = (id: string, name: string): Client => ({
  id, ownerId: 'f-1', name, email: null, phone: null, address: null,
  company: null, notes: null, createdAt: '2026-05-11T10:00:00Z', updatedAt: '2026-05-11T10:00:00Z',
});

describe('clientsStore', () => {
  beforeEach(() => useClientsStore.getState().clear());

  it('setClients clears stale + stamps fetch', () => {
    useClientsStore.getState().markStale(true);
    useClientsStore.getState().setClients([c('a', 'A')]);
    expect(useClientsStore.getState().clients).toHaveLength(1);
    expect(useClientsStore.getState().isStale).toBe(false);
  });

  it('upsertClient replaces by id or prepends', () => {
    useClientsStore.getState().setClients([c('a', 'A')]);
    useClientsStore.getState().upsertClient(c('a', 'A2'));
    expect(useClientsStore.getState().clients[0].name).toBe('A2');
    useClientsStore.getState().upsertClient(c('b', 'B'));
    expect(useClientsStore.getState().clients).toHaveLength(2);
  });

  it('removeClient drops by id', () => {
    useClientsStore.getState().setClients([c('a', 'A'), c('b', 'B')]);
    useClientsStore.getState().removeClient('a');
    expect(useClientsStore.getState().clients.map((x) => x.id)).toEqual(['b']);
  });
});
```

- [ ] **Step 2: Run to verify it fails**

Run: `npm run test:run -- clientsStore`
Expected: FAIL — cannot find `../clientsStore`.

- [ ] **Step 3: Write the store** (mirrors `jobsStore`)

```ts
// desktop/portal/src/console/stores/clientsStore.ts
import { create } from 'zustand';
import type { Client } from '../api/clientsClient';

interface ClientsState {
  clients: Client[];
  detailClient: Client | null;
  detailJobs: any[];
  lastFetchedAt: number | null;
  isStale: boolean;
  setClients: (clients: Client[]) => void;
  setDetail: (client: Client, jobs: any[]) => void;
  upsertClient: (client: Client) => void;
  removeClient: (id: string) => void;
  markStale: (b: boolean) => void;
  clear: () => void;
}

export const useClientsStore = create<ClientsState>((set) => ({
  clients: [],
  detailClient: null,
  detailJobs: [],
  lastFetchedAt: null,
  isStale: false,
  setClients: (clients) => set({ clients, lastFetchedAt: Date.now(), isStale: false }),
  setDetail: (detailClient, detailJobs) => set({ detailClient, detailJobs }),
  upsertClient: (client) => set((s) => {
    const idx = s.clients.findIndex((c) => c.id === client.id);
    const clients = idx === -1 ? [client, ...s.clients] : s.clients.map((c, i) => (i === idx ? client : c));
    const detailClient = s.detailClient && s.detailClient.id === client.id ? client : s.detailClient;
    return { clients, detailClient };
  }),
  removeClient: (id) => set((s) => ({ clients: s.clients.filter((c) => c.id !== id) })),
  markStale: (isStale) => set({ isStale }),
  clear: () => set({ clients: [], detailClient: null, detailJobs: [], lastFetchedAt: null, isStale: false }),
}));
```

- [ ] **Step 4: Run to verify it passes**

Run: `npm run test:run -- clientsStore`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add desktop/portal/src/console/stores/clientsStore.ts desktop/portal/src/console/stores/__tests__/clientsStore.test.ts
git commit -m "feat(clients): clientsStore"
```

---

### Task 9: useClientsPolling hook

**Files:**
- Create: `desktop/portal/src/console/hooks/useClientsPolling.ts`

- [ ] **Step 1: Write the hook** (copy `useJobsPolling.ts`, swap client/store calls; list passes the search `q` separately so the hook key stays stable)

```ts
// desktop/portal/src/console/hooks/useClientsPolling.ts
import { useEffect, useRef } from 'react';
import { clientsClient } from '../api/clientsClient';
import { useClientsStore } from '../stores/clientsStore';

type Scope = 'list' | { detail: string };

export function useClientsPolling(scope: Scope, intervalMs: number = 15_000): void {
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  useEffect(() => {
    const fetchOnce = async () => {
      if (scope === 'list') {
        const r = await clientsClient.list();
        if (r.ok) useClientsStore.getState().setClients(r.clients);
        else useClientsStore.getState().markStale(true);
      } else {
        const r = await clientsClient.getById(scope.detail);
        if (r.ok) { useClientsStore.getState().setDetail(r.client, r.jobs); useClientsStore.getState().markStale(false); }
        else useClientsStore.getState().markStale(true);
      }
    };
    const start = () => { if (intervalRef.current === null) intervalRef.current = setInterval(fetchOnce, intervalMs); };
    const stop = () => { if (intervalRef.current !== null) { clearInterval(intervalRef.current); intervalRef.current = null; } };
    const onVis = () => { if (document.visibilityState === 'visible') { fetchOnce(); start(); } else stop(); };
    fetchOnce(); start();
    document.addEventListener('visibilitychange', onVis);
    return () => { stop(); document.removeEventListener('visibilitychange', onVis); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [intervalMs, scope === 'list' ? 'list' : scope.detail]);
}
```

- [ ] **Step 2: Typecheck**

Run: `npx tsc --noEmit`
Expected: no errors.

- [ ] **Step 3: Commit**

```bash
git add desktop/portal/src/console/hooks/useClientsPolling.ts
git commit -m "feat(clients): useClientsPolling hook"
```

---

### Task 10: Client components (ContactLines, Card, CreateClientModal)

**Files:**
- Create: `desktop/portal/src/console/components/clients/ClientContactLines.tsx`, `ClientCard.tsx`, `CreateClientModal.tsx`
- Test: `desktop/portal/src/console/components/clients/__tests__/CreateClientModal.test.tsx`

- [ ] **Step 1: Write ClientContactLines** (tappable tel/mailto/map; no emoji)

```tsx
// desktop/portal/src/console/components/clients/ClientContactLines.tsx
import type { Client } from '../../api/clientsClient';

export function ClientContactLines({ client }: { client: Client }) {
  return (
    <dl className="text-sm grid grid-cols-[10ch_1fr] gap-y-1 font-mono">
      <dt className="text-console-text-muted">phone</dt>
      <dd>{client.phone ? <a className="text-console-accent" href={`tel:${client.phone}`}>{client.phone}</a> : '—'}</dd>
      <dt className="text-console-text-muted">email</dt>
      <dd>{client.email ? <a className="text-console-accent" href={`mailto:${client.email}`}>{client.email}</a> : '—'}</dd>
      <dt className="text-console-text-muted">address</dt>
      <dd>{client.address
        ? <a className="text-console-accent" href={`https://maps.google.com/?q=${encodeURIComponent(client.address)}`} target="_blank" rel="noreferrer">{client.address}</a>
        : '—'}</dd>
      <dt className="text-console-text-muted">company</dt>
      <dd>{client.company ?? '—'}</dd>
    </dl>
  );
}
```

- [ ] **Step 2: Write ClientCard** (links to detail; no money)

```tsx
// desktop/portal/src/console/components/clients/ClientCard.tsx
import { Link } from 'react-router-dom';
import type { Client } from '../../api/clientsClient';

export function ClientCard({ client }: { client: Client }) {
  return (
    <Link to={`/console/clients/${client.id}`}
      className="flex items-center justify-between px-3 py-2 border-b border-console-border hover:bg-console-surface font-mono">
      <span className="text-console-text truncate">{client.name}</span>
      <span className="text-console-accent text-xs shrink-0">[-&gt; open]</span>
    </Link>
  );
}
```

- [ ] **Step 3: Write the failing modal test**

```tsx
// desktop/portal/src/console/components/clients/__tests__/CreateClientModal.test.tsx
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { CreateClientModal } from '../CreateClientModal';
import { useClientsStore } from '../../../stores/clientsStore';

describe('CreateClientModal', () => {
  beforeEach(() => useClientsStore.getState().clear());

  it('creates a client and calls onCreated with it', async () => {
    const onCreated = vi.fn();
    render(<CreateClientModal open onClose={() => {}} onCreated={onCreated} />);
    fireEvent.change(screen.getByLabelText(/name/i), { target: { value: 'Brand new' } });
    fireEvent.click(screen.getByRole('button', { name: /create/i }));
    await waitFor(() => expect(onCreated).toHaveBeenCalled());
    expect(onCreated.mock.calls[0][0].id).toBe('new-client-id');
    expect(useClientsStore.getState().clients[0].id).toBe('new-client-id');
  });
});
```

- [ ] **Step 4: Run to verify it fails**

Run: `npm run test:run -- CreateClientModal`
Expected: FAIL — cannot find `../CreateClientModal`.

- [ ] **Step 5: Write CreateClientModal** (uses the responsive `Modal` base + `Input`/`Button`; supports create and edit; upserts into the store; calls `onCreated`)

```tsx
// desktop/portal/src/console/components/clients/CreateClientModal.tsx
import { useState, useEffect } from 'react';
import { Modal } from '../ui/Modal';
import { Input } from '../ui/Input';
import { Button } from '../ui/Button';
import { useToast } from '../ui/ToastProvider';
import { clientsClient } from '../../api/clientsClient';
import { useClientsStore } from '../../stores/clientsStore';
import type { Client } from '../../api/clientsClient';

interface Props {
  open: boolean;
  onClose: () => void;
  onCreated?: (c: Client) => void;
  editing?: Client | null;
}

export function CreateClientModal({ open, onClose, onCreated, editing }: Props) {
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [phone, setPhone] = useState('');
  const [address, setAddress] = useState('');
  const [busy, setBusy] = useState(false);
  const [nameError, setNameError] = useState<string | null>(null);
  const toast = useToast();

  useEffect(() => {
    if (open) {
      setName(editing?.name ?? ''); setEmail(editing?.email ?? '');
      setPhone(editing?.phone ?? ''); setAddress(editing?.address ?? '');
      setNameError(null);
    }
  }, [open, editing]);

  async function onSubmit(e: React.FormEvent) {
    e.preventDefault();
    if (!name.trim()) { setNameError('Name is required'); return; }
    setBusy(true);
    const payload = { name: name.trim(), email: email.trim() || undefined,
      phone: phone.trim() || undefined, address: address.trim() || undefined };
    const r = editing
      ? await clientsClient.update(editing.id, payload)
      : await clientsClient.create(payload);
    setBusy(false);
    if (!r.ok) { toast.error(r.error || 'Failed to save client'); return; }
    useClientsStore.getState().upsertClient(r.client);
    onCreated?.(r.client);
    onClose();
  }

  return (
    <Modal open={open} onClose={onClose} title={editing ? 'Edit Client' : 'Create Client'}>
      <form onSubmit={onSubmit} className="flex flex-col gap-3 w-full sm:w-[420px] max-w-full">
        <Input label="Name" value={name} onChange={(e) => setName(e.target.value)} error={nameError ?? undefined} />
        <Input label="Email" value={email} onChange={(e) => setEmail(e.target.value)} />
        <Input label="Phone" value={phone} onChange={(e) => setPhone(e.target.value)} />
        <Input label="Address" value={address} onChange={(e) => setAddress(e.target.value)} />
        <Button type="submit" disabled={busy}>{editing ? 'Save' : 'Create'}</Button>
      </form>
    </Modal>
  );
}
```

Note: confirm the toast hook import path/name from an existing modal (e.g. `CreateJobModal.tsx`) — use whatever it uses (`useToast` from `../ui/ToastProvider` or similar). Match `Input`'s `label`/`error` props to the existing `Input` component.

- [ ] **Step 6: Run to verify it passes**

Run: `npm run test:run -- CreateClientModal`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add desktop/portal/src/console/components/clients/
git commit -m "feat(clients): client contact lines, card, create/edit modal"
```

---

### Task 11: Clients routes (list + detail) + App.tsx registration

**Files:**
- Create: `desktop/portal/src/console/routes/ClientsListRoute.tsx`, `ClientDetailRoute.tsx`, `desktop/portal/src/console/routes/__tests__/ClientsListRoute.test.tsx`
- Modify: `desktop/portal/src/App.tsx`

- [ ] **Step 1: Write the failing list-route test** (seed the store directly; mirrors `JobsListRoute.test.tsx`)

```tsx
// desktop/portal/src/console/routes/__tests__/ClientsListRoute.test.tsx
import { render, screen } from '@testing-library/react';
import { describe, it, expect, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { ClientsListRoute } from '../ClientsListRoute';
import { useClientsStore } from '../../stores/clientsStore';
import type { Client } from '../../api/clientsClient';

const c = (id: string, name: string): Client => ({
  id, ownerId: 'f-1', name, email: null, phone: null, address: null,
  company: null, notes: null, createdAt: '2026-05-11T10:00:00Z', updatedAt: '2026-05-11T10:00:00Z',
});

describe('ClientsListRoute', () => {
  beforeEach(() => useClientsStore.getState().clear());
  it('renders clients from the store', () => {
    useClientsStore.getState().setClients([c('a', 'Acme'), c('b', 'Globex')]);
    render(<MemoryRouter><ClientsListRoute /></MemoryRouter>);
    expect(screen.getByText('Acme')).toBeInTheDocument();
    expect(screen.getByText('Globex')).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run to verify it fails**

Run: `npm run test:run -- ClientsListRoute`
Expected: FAIL — cannot find `../ClientsListRoute`.

- [ ] **Step 3: Write ClientsListRoute**

```tsx
// desktop/portal/src/console/routes/ClientsListRoute.tsx
import { useState } from 'react';
import { Button } from '../components/ui/Button';
import { ClientCard } from '../components/clients/ClientCard';
import { CreateClientModal } from '../components/clients/CreateClientModal';
import { useClientsPolling } from '../hooks/useClientsPolling';
import { useClientsStore } from '../stores/clientsStore';

export function ClientsListRoute() {
  useClientsPolling('list');
  const clients = useClientsStore((s) => s.clients);
  const isStale = useClientsStore((s) => s.isStale);
  const [showCreate, setShowCreate] = useState(false);
  const [query, setQuery] = useState('');
  const shown = query.trim()
    ? clients.filter((c) => c.name.toLowerCase().includes(query.trim().toLowerCase()))
    : clients;

  return (
    <div className="font-mono">
      <div className="flex flex-col items-start gap-2 md:flex-row md:items-center md:justify-between mb-4">
        <h1 className="text-console-text text-lg">Clients</h1>
        <Button onClick={() => setShowCreate(true)}>+ Create client</Button>
      </div>
      <input
        value={query}
        onChange={(e) => setQuery(e.target.value)}
        placeholder="Search by name"
        className="w-full mb-3 bg-console-bg border border-console-border rounded px-2 py-1 text-sm font-mono text-console-text focus:border-console-accent outline-none"
      />
      {isStale && (
        <div className="bg-console-surface border border-console-warn text-console-warn px-3 py-1 text-xs mb-3">
          [OFFLINE] Couldn't refresh — showing cached data
        </div>
      )}
      {shown.length === 0
        ? <div className="text-console-text-muted text-sm">No clients.</div>
        : <div className="border border-console-border">{shown.map((c) => <ClientCard key={c.id} client={c} />)}</div>}
      <CreateClientModal open={showCreate} onClose={() => setShowCreate(false)} onCreated={() => setShowCreate(false)} />
    </div>
  );
}
```

- [ ] **Step 4: Write ClientDetailRoute** (contact + linked jobs; edit modal)

```tsx
// desktop/portal/src/console/routes/ClientDetailRoute.tsx
import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Button } from '../components/ui/Button';
import { ClientContactLines } from '../components/clients/ClientContactLines';
import { CreateClientModal } from '../components/clients/CreateClientModal';
import { useClientsPolling } from '../hooks/useClientsPolling';
import { useClientsStore } from '../stores/clientsStore';

export function ClientDetailRoute() {
  const { id } = useParams<{ id: string }>();
  useClientsPolling({ detail: id ?? '' });
  const client = useClientsStore((s) => s.detailClient);
  const jobs = useClientsStore((s) => s.detailJobs);
  const [showEdit, setShowEdit] = useState(false);

  if (!client || client.id !== id) {
    return <div className="text-console-text-muted">Loading...</div>;
  }

  return (
    <div className="font-mono">
      <Link to="/console/clients" className="text-console-accent text-sm">back to clients</Link>
      <div className="flex items-center justify-between mt-2 mb-4">
        <h1 className="text-console-text text-lg">{client.name}</h1>
        <Button onClick={() => setShowEdit(true)}>Edit</Button>
      </div>
      <ClientContactLines client={client} />
      <div className="mt-6">
        <div className="text-xs uppercase tracking-wide text-console-text-muted mb-2">Jobs ({jobs.length})</div>
        {jobs.length === 0
          ? <div className="text-console-text-muted text-sm">No jobs for this client.</div>
          : <div className="border border-console-border">
              {jobs.map((j: any) => (
                <Link key={j.id} to={`/console/jobs/${j.id}`}
                  className="flex items-center justify-between px-3 py-2 border-b border-console-border hover:bg-console-surface">
                  <span className="truncate">{j.title}</span>
                  <span className="text-console-text-muted text-xs shrink-0">{j.status}</span>
                </Link>
              ))}
            </div>}
      </div>
      <CreateClientModal open={showEdit} onClose={() => setShowEdit(false)} editing={client} />
    </div>
  );
}
```

- [ ] **Step 5: Register routes in App.tsx** (mirror the jobs block, inside the `/console` parent)

Add imports near the jobs route imports:
```tsx
import { ClientsListRoute } from './console/routes/ClientsListRoute';
import { ClientDetailRoute } from './console/routes/ClientDetailRoute';
```
Add routes next to the jobs routes:
```tsx
        <Route path="clients" element={<RequireForemanTier><ClientsListRoute /></RequireForemanTier>} />
        <Route path="clients/:id" element={<RequireForemanTier><ClientDetailRoute /></RequireForemanTier>} />
```

- [ ] **Step 6: Run to verify it passes + typecheck**

Run: `npm run test:run -- ClientsListRoute` then `npx tsc --noEmit`
Expected: PASS + no type errors.

- [ ] **Step 7: Commit**

```bash
git add desktop/portal/src/console/routes/ClientsListRoute.tsx desktop/portal/src/console/routes/ClientDetailRoute.tsx desktop/portal/src/console/routes/__tests__/ClientsListRoute.test.tsx desktop/portal/src/App.tsx
git commit -m "feat(clients): clients list + detail routes"
```

---

### Task 12: Nav + job client picker + job detail client display

**Files:**
- Modify: `desktop/portal/src/console/layouts/AppHeader.tsx`, `desktop/portal/src/console/layouts/BottomTabBar.tsx`, `desktop/portal/src/console/components/jobs/CreateJobModal.tsx`, `desktop/portal/src/console/routes/JobDetailRoute.tsx`

- [ ] **Step 1: Add `[Clients]` to AppHeader** (inside the `hasForemanTier()` nav group, after the Jobs entry)

```tsx
        {hasForemanTier() && <NavButton to="/console/clients" label="Clients" />}
```

- [ ] **Step 2: Add `[Clients]` to BottomTabBar** (after the Jobs tab, foreman-gated)

```tsx
      {hasForemanTier() && <TabLink to="/console/clients" label="Clients" />}
```

- [ ] **Step 3: Add the client picker to CreateJobModal**

In `CreateJobModal.tsx`: add `useClientsPolling('list')` so the list is available, read `clients` from `useClientsStore`, add a `clientId` state, render a `<select>` (default empty = "No client"), and include `clientId` in the create payload. Sketch:
```tsx
  // imports
  import { useClientsPolling } from '../../hooks/useClientsPolling';
  import { useClientsStore } from '../../stores/clientsStore';
  // in component:
  useClientsPolling('list');
  const clients = useClientsStore((s) => s.clients);
  const [clientId, setClientId] = useState('');
  // in the form, before submit button:
  <label className="flex flex-col gap-1 font-mono text-sm">
    <span className="text-console-text-muted">Client (optional)</span>
    <select value={clientId} onChange={(e) => setClientId(e.target.value)}
      className="bg-console-bg border border-console-border rounded px-2 py-1 text-sm text-console-text focus:border-console-accent outline-none">
      <option value="">No client</option>
      {clients.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
    </select>
  </label>
  // include in jobsClient.create payload: clientId: clientId || undefined
```

- [ ] **Step 4: Show linked client on JobDetailRoute**

Add a row to the metadata `<dl>` using the `job.client` field (now serialized by the backend):
```tsx
        <dt className="text-console-text-muted">client</dt>
        <dd>{job.client
          ? <Link className="text-console-accent" to={`/console/clients/${job.client.id}`}>{job.client.name}</Link>
          : '—'}</dd>
```
(`Link` is already imported in `JobDetailRoute`. Also add `client: { id: string; name: string } | null` to the portal `Job` type in `jobsClient.ts` so this typechecks.)

- [ ] **Step 5: Verify nav + no overflow at phone widths**

Run: `npm run test:run` (full suite) and `npx tsc --noEmit` and `npm run build` — all clean.
Then verify mobile overflow with a foreman session (manual or a quick measurement): at 320px and 375px on `/console/jobs`, `document.documentElement.scrollWidth === window.innerWidth` (the bar now has 5 tabs for foreman: Home/Clock/Jobs/Clients/Comm). If it overflows, shorten labels or reduce tab padding; do not regress the existing overflow fix.

- [ ] **Step 6: Commit**

```bash
git add desktop/portal/src/console/layouts/AppHeader.tsx desktop/portal/src/console/layouts/BottomTabBar.tsx desktop/portal/src/console/components/jobs/CreateJobModal.tsx desktop/portal/src/console/routes/JobDetailRoute.tsx desktop/portal/src/console/api/jobsClient.ts
git commit -m "feat(clients): nav entries + job client picker + linked client on job detail"
```

---

## Final verification (after all tasks)

- Backend: `cd backend && DATABASE_URL=postgresql://localhost/smithnet npx jest --runInBand` — all green.
- Portal: `cd desktop/portal && npm run test:run && npx tsc --noEmit && npm run build` — all clean.
- Live: restart `:3030`; log in as a **foreman-tier** user (the Solo `clocktest-0525` will be redirected away from `/console/clients`). Click through: Clients list -> create a client -> open detail (contact + empty jobs) -> Create Job with that client selected -> open the job -> see the linked client -> open it from the job. Resize to 375px and confirm the bottom bar and screens have no horizontal overflow.

---

## Self-Review (against the spec)

- **Spec coverage:** clients table + FK (Task 1); CRUD (Tasks 3, 5); list/detail screens with contact + linked jobs, no billing (Tasks 10-11); jobs<->client FK + picker + display (Tasks 6, 12); foreman-gated nav (Task 12). All spec goals mapped.
- **Non-goals respected:** no billing/price, no `invoices.client_id`, no tasks/timeline aggregations. Confirmed absent.
- **Type consistency:** `Client` shape identical in `clientsService` (backend, Date) and `clientsClient` (portal, ISO string); store/components/routes all import `Client` from `clientsClient`. `Job.client` added consistently in both backend (`jobsService`) and portal (`jobsClient`). `clientBelongsToOwner` / `listByClient` defined in Task 6 and referenced by Task 5's router (note the ordering caveat called out in Task 5 Step 3).
- **Placeholder scan:** none — every code step has complete code. The two soft references ("match the existing Input/Toast props", "ensure jobsService.update writes client_id") point at existing code the implementer must read, not undefined work.
