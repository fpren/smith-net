# Plan 4 — Map-First Console + Crew Roster Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `/console` open onto an interactive Map with status-colored job pins (geocoded via Nominatim) + Crew roster page derived from `job_crew` assignments. Polling at 15s.

**Architecture:** Backend adds Nominatim async-geocoder + `GET /api/profiles/crew` + migration 004 for lat/lng columns. Frontend adds `maplibre-gl` + `MapRoute` (new landing) + `CrewRoute` + supporting components (StatsStrip, MapSidePanel, JobMarker, JobPopup, etc.). Geocoding is fire-and-forget after `jobsService.create()`/`update()` — job creation stays fast, pins appear on next polling tick.

**Tech Stack:** Backend — Express + `pg.Pool` + `zod` + Jest + supertest + global `fetch` (Node 20+). Frontend — React 18 + Vite + Vitest + RTL + MSW + Zustand + Tailwind + **`maplibre-gl` (new dep)**.

**Spec:** `docs/superpowers/specs/2026-05-13-plan-4-map-and-crew-design.md`

**Scope boundary (NOT in this plan):**
- WebSocket / live push (still polling)
- Drag-to-assign on map
- Live GPS feed from Android crew
- Click-empty-map-to-create-job
- Self-hosted Nominatim
- Multi-pin clustering
- Crew CRUD (invite, deactivate)

**DB requirement:** Migration 004 needs `DATABASE_URL` set. Frontend tests need no DB. Backend `geocoder.test.ts` mocks global `fetch`. `profiles-crew-route.test.ts` is gated by `DATABASE_URL` (skipped if unset, like Plan 2's `jobs-routes.test.ts`).

---

## File Structure

**New backend files:**
- `backend/migrations/004_jobs_coords.sql`
- `backend/src/geocoder.ts`
- `backend/src/__tests__/geocoder.test.ts`
- `backend/src/__tests__/profiles-crew-route.test.ts`

**Modified backend files:**
- `backend/src/auditLog.ts` (add `JOB_GEOCODED`)
- `backend/src/jobsService.ts` (async geocode in `create()` + `update()`)
- `backend/src/profilesRoutes.ts` (add `GET /crew`)
- `backend/src/__tests__/jobs-routes.test.ts` (extend with geocode verification)

**New frontend files (under `desktop/portal/src/console/`):**
- `api/crewClient.ts`
- `api/__tests__/crewClient.test.ts`
- `stores/crewStore.ts`
- `stores/__tests__/crewStore.test.ts`
- `hooks/useCrewRoster.ts`
- `hooks/__tests__/useCrewRoster.test.ts`
- `components/crew/AvailabilityDot.tsx`
- `components/crew/CrewCard.tsx`
- `components/crew/__tests__/CrewCard.test.tsx`
- `components/map/MapFilterChips.tsx`
- `components/map/MapSidePanel.tsx`
- `components/map/StatsStrip.tsx`
- `components/map/JobMarker.tsx`
- `components/map/JobPopup.tsx`
- `components/map/MapCanvas.tsx`
- `components/map/__tests__/MapFilterChips.test.tsx`
- `components/map/__tests__/MapSidePanel.test.tsx`
- `components/map/__tests__/StatsStrip.test.tsx`
- `components/map/__tests__/JobPopup.test.tsx`
- `components/map/__tests__/MapCanvas.test.tsx`
- `routes/CrewRoute.tsx`
- `routes/MapRoute.tsx`
- `routes/__tests__/CrewRoute.test.tsx`
- `routes/__tests__/MapRoute.test.tsx`

**Modified frontend files:**
- `desktop/portal/package.json` (add `maplibre-gl`)
- `desktop/portal/src/console/index.css` (job-marker classes)
- `desktop/portal/src/console/test/msw-handlers.ts` (extend with `/api/profiles/crew`)
- `desktop/portal/src/console/ConsoleShell.tsx` (Map + Jobs + Crew nav links)
- `desktop/portal/src/App.tsx` (Map as `/console` index, add `/console/crew`)

---

## Task 1: Migration 004 — coords on jobs

**Files:**
- Create: `backend/migrations/004_jobs_coords.sql`

- [ ] **Step 1: Create the SQL**

```sql
-- 004_jobs_coords.sql
-- Plan 4: add latitude/longitude/geocoded_at columns to jobs for map pins.

ALTER TABLE jobs
  ADD COLUMN IF NOT EXISTS latitude    DOUBLE PRECISION,
  ADD COLUMN IF NOT EXISTS longitude   DOUBLE PRECISION,
  ADD COLUMN IF NOT EXISTS geocoded_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_jobs_coords ON jobs(latitude, longitude)
  WHERE latitude IS NOT NULL AND longitude IS NOT NULL;
```

- [ ] **Step 2: Apply (if DATABASE_URL is set)**

```bash
psql "$DATABASE_URL" -f /Users/fegensprenelon/smith-net/backend/migrations/004_jobs_coords.sql
```

Expected: `ALTER TABLE` + `CREATE INDEX` messages, no errors. If `DATABASE_URL` is unset, skip and report DONE_WITH_CONCERNS.

- [ ] **Step 3: Verify the new columns exist (if applied)**

```bash
psql "$DATABASE_URL" -c "\d jobs" | head -20
```

Expected: jobs columns include `latitude`, `longitude`, `geocoded_at`.

- [ ] **Step 4: Commit**

```bash
git -C /Users/fegensprenelon/smith-net add backend/migrations/004_jobs_coords.sql
git -C /Users/fegensprenelon/smith-net commit -m "feat(jobs): migration 004 — lat/lng/geocoded_at on jobs"
```

---

## Task 2: `geocoder.ts` + tests (TDD)

**Files:**
- Create: `backend/src/geocoder.ts`
- Create: `backend/src/__tests__/geocoder.test.ts`

- [ ] **Step 1: Write the failing test**

```ts
// backend/src/__tests__/geocoder.test.ts
import { geocodeLocation, __resetGeocoderState } from '../geocoder';

describe('geocodeLocation', () => {
  beforeEach(() => {
    __resetGeocoderState();
    (global as any).fetch = jest.fn();
  });
  afterEach(() => {
    jest.restoreAllMocks();
  });

  it('returns lat/lng on 200 with a result', async () => {
    (global as any).fetch = jest.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => [{ lat: '40.748817', lon: '-73.985428' }],
    });
    const result = await geocodeLocation('Empire State Building, NYC');
    expect(result).toEqual({ lat: 40.748817, lng: -73.985428 });
    const call = (global.fetch as jest.Mock).mock.calls[0];
    expect(call[0]).toContain('https://nominatim.openstreetmap.org/search');
    expect(call[0]).toContain(encodeURIComponent('Empire State Building, NYC'));
    expect(call[1].headers['User-Agent']).toMatch(/SmithNet/);
  });

  it('returns null when 200 with empty array', async () => {
    (global as any).fetch = jest.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => [],
    });
    const result = await geocodeLocation('asdfasdfasdfasdf');
    expect(result).toBeNull();
  });

  it('returns null on 429 rate-limit', async () => {
    (global as any).fetch = jest.fn().mockResolvedValue({
      ok: false,
      status: 429,
      json: async () => ({}),
    });
    const result = await geocodeLocation('x');
    expect(result).toBeNull();
  });

  it('returns null on network error', async () => {
    (global as any).fetch = jest.fn().mockRejectedValue(new Error('network down'));
    const result = await geocodeLocation('x');
    expect(result).toBeNull();
  });

  it('respects the 1 req/sec rate limit between calls', async () => {
    (global as any).fetch = jest.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => [{ lat: '1', lon: '2' }],
    });

    const t0 = Date.now();
    await geocodeLocation('a');
    await geocodeLocation('b');
    const elapsed = Date.now() - t0;
    // Two calls back-to-back must be ≥ ~1.1s apart.
    expect(elapsed).toBeGreaterThanOrEqual(1000);
  }, 5000);
});
```

- [ ] **Step 2: Run — confirm FAIL**

```bash
cd /Users/fegensprenelon/smith-net/backend && JWT_SECRET=test-secret-at-least-32-chars-long-please-thanks npx jest geocoder 2>&1 | tail -10
```

Expected: FAIL — `Cannot find module '../geocoder'`.

- [ ] **Step 3: Implement `geocoder.ts`**

```ts
// backend/src/geocoder.ts
//
// Thin Nominatim client. Best-effort async geocoding called from jobsService.
// Soft-fails on rate-limit, network error, or empty results — the caller proceeds
// without a pin if no coords come back.

const NOMINATIM_BASE = 'https://nominatim.openstreetmap.org/search';
const USER_AGENT = 'SmithNet/1.0 (operator console)';
const RATE_LIMIT_MS = 1100; // 1.1s gap → ≤1 req/sec safely

let nextAllowedAt = 0;

/**
 * Test-only — resets the rate-limit clock so tests start from a clean state.
 */
export function __resetGeocoderState(): void {
  nextAllowedAt = 0;
}

export async function geocodeLocation(text: string): Promise<{ lat: number; lng: number } | null> {
  // Token-bucket wait
  const now = Date.now();
  if (now < nextAllowedAt) {
    await new Promise((r) => setTimeout(r, nextAllowedAt - now));
  }
  nextAllowedAt = Math.max(Date.now(), nextAllowedAt) + RATE_LIMIT_MS;

  try {
    const url = `${NOMINATIM_BASE}?q=${encodeURIComponent(text)}&format=json&limit=1`;
    const res = await fetch(url, { headers: { 'User-Agent': USER_AGENT } });
    if (!res.ok) {
      console.warn(`[Geocoder] non-2xx ${res.status} for: ${text}`);
      return null;
    }
    const arr = (await res.json()) as Array<{ lat: string; lon: string }>;
    if (!Array.isArray(arr) || arr.length === 0) {
      console.warn(`[Geocoder] no result for: ${text}`);
      return null;
    }
    const lat = parseFloat(arr[0].lat);
    const lng = parseFloat(arr[0].lon);
    if (!Number.isFinite(lat) || !Number.isFinite(lng)) {
      console.warn(`[Geocoder] non-finite coords for: ${text}`);
      return null;
    }
    return { lat, lng };
  } catch (e: any) {
    console.warn(`[Geocoder] error for "${text}": ${e.message}`);
    return null;
  }
}
```

- [ ] **Step 4: Run — confirm 5 tests PASS**

```bash
cd /Users/fegensprenelon/smith-net/backend && JWT_SECRET=test-secret-at-least-32-chars-long-please-thanks npx jest geocoder 2>&1 | tail -10
```

Expected: 5 PASS.

- [ ] **Step 5: Commit**

```bash
git -C /Users/fegensprenelon/smith-net add backend/src/geocoder.ts backend/src/__tests__/geocoder.test.ts
git -C /Users/fegensprenelon/smith-net commit -m "feat(geocoder): Nominatim client with 1 req/sec rate-limit + soft-fail"
```

---

## Task 3: `JOB_GEOCODED` audit code + `jobsService` async geocode

**Files:**
- Modify: `backend/src/auditLog.ts` (add one enum value)
- Modify: `backend/src/jobsService.ts` (extend `create()` + `update()`)
- Modify: `backend/src/__tests__/jobs-routes.test.ts` (extend POST test with geocode verification)

- [ ] **Step 1: Add `JOB_GEOCODED` enum value to `auditLog.ts`**

In the `Jobs` section of the `AuditAction` enum (added in Plan 2 Task 2), add:

```ts
  JOB_GEOCODED = 'job.geocoded',
```

Final Jobs section in auditLog.ts:

```ts
  // Jobs
  JOB_CREATED = 'job.created',
  JOB_UPDATED = 'job.updated',
  JOB_STATUS_CHANGED = 'job.status_changed',
  JOB_CREW_ASSIGNED = 'job.crew_assigned',
  JOB_CREW_UNASSIGNED = 'job.crew_unassigned',
  JOB_GEOCODED = 'job.geocoded',
```

- [ ] **Step 2: Extend `jobsService.ts` — add geocoder import + `latitude`/`longitude` fields on Job type + async geocode helper**

In `backend/src/jobsService.ts`:

Add to the imports near the top (alongside existing `pg` and `auditLog`):

```ts
import { geocodeLocation } from './geocoder';
```

Extend the `Job` interface to include the new columns:

```ts
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
  latitude: number | null;        // NEW
  longitude: number | null;       // NEW
  geocodedAt: Date | null;        // NEW
  createdAt: Date;
  updatedAt: Date;
}
```

Update `mapJobRow` to read the new columns:

```ts
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
    latitude: row.latitude !== null ? Number(row.latitude) : null,
    longitude: row.longitude !== null ? Number(row.longitude) : null,
    geocodedAt: row.geocoded_at ? new Date(row.geocoded_at) : null,
    createdAt: new Date(row.created_at),
    updatedAt: new Date(row.updated_at),
  };
}
```

Add a fire-and-forget helper at the bottom of the file:

```ts
// ════════════════════════════════════════════════════════════════════
// Geocoding (best-effort async; called from create + update)
// ════════════════════════════════════════════════════════════════════

async function geocodeAndUpdate(jobId: string, foremanId: string, locationText: string): Promise<void> {
  try {
    const coords = await geocodeLocation(locationText);
    if (!coords) return;
    const db = requirePg();
    await db.query(
      `UPDATE jobs SET latitude = $1, longitude = $2, geocoded_at = NOW(), updated_at = NOW() WHERE id = $3`,
      [coords.lat, coords.lng, jobId]
    );
    auditLog.log(AuditAction.JOB_GEOCODED, foremanId, {
      jobId,
      lat: coords.lat,
      lng: coords.lng,
    });
  } catch (e: any) {
    // Don't crash the process — geocoding is best-effort enrichment.
    console.warn(`[JobsService] async geocode failed for ${jobId}: ${e.message}`);
  }
}
```

In `create()`, after the existing `auditLog.log(AuditAction.JOB_CREATED, ...)` call, fire the geocode without await:

```ts
  auditLog.log(AuditAction.JOB_CREATED, input.foremanId, {
    jobId: job.id,
    title: job.title,
    status: job.status,
    scheduledAt: job.scheduledAt,
    location: job.location,
    clientId: job.clientId,
    engagementId: job.engagementId,
  });

  // Fire-and-forget geocode. Job is already returned with coords=null;
  // a subsequent fetch picks up the populated row.
  if (job.location) {
    geocodeAndUpdate(job.id, input.foremanId, job.location);
  }

  return job;
```

In `update()`, after the existing `auditLog.log(AuditAction.JOB_UPDATED, ...)`, re-geocode IF the `location` field was in the patch:

```ts
  auditLog.log(AuditAction.JOB_UPDATED, job.foremanId, {
    jobId: job.id,
    changedFields,
    after: { title: job.title, description: job.description, scheduledAt: job.scheduledAt, location: job.location },
  });

  // If location changed, re-geocode. Best-effort.
  if (changedFields.includes('location') && job.location) {
    geocodeAndUpdate(job.id, job.foremanId, job.location);
  }

  return job;
```

- [ ] **Step 3: Extend the POST test in `jobs-routes.test.ts`** to verify the geocode lands on the job row

Append a new test inside the existing `describeDb('POST /api/jobs', ...)` block:

```ts
  it('latitude/longitude get populated by background geocode', async () => {
    // Mock fetch globally so the geocoder returns coords without hitting Nominatim.
    const originalFetch = global.fetch;
    (global as any).fetch = jest.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => [{ lat: '40.748817', lon: '-73.985428' }],
    });

    try {
      const f = await createForemanAndLogin('geocode-create');
      const res = await request(app)
        .post('/api/jobs')
        .set('Authorization', `Bearer ${f.token}`)
        .send({ title: 'Empire State smoke', location: 'Empire State Building, NYC' });
      expect(res.status).toBe(201);
      // Coords are null in the immediate response (async).
      expect(res.body.job.latitude).toBeNull();

      // Wait for the async geocode + UPDATE to complete.
      await new Promise((r) => setTimeout(r, 200));

      // Re-fetch — coords should be populated now.
      const got = await request(app)
        .get(`/api/jobs/${res.body.job.id}`)
        .set('Authorization', `Bearer ${f.token}`);
      expect(got.status).toBe(200);
      expect(got.body.job.latitude).toBeCloseTo(40.748817, 4);
      expect(got.body.job.longitude).toBeCloseTo(-73.985428, 4);
    } finally {
      global.fetch = originalFetch;
    }
  });
```

- [ ] **Step 4: Verify all tests** (Plan 2 tests still pass; new geocoded test passes if DATABASE_URL set)

```bash
cd /Users/fegensprenelon/smith-net/backend && JWT_SECRET=test-secret-at-least-32-chars-long-please-thanks npm test 2>&1 | tail -5
```

Expected without DATABASE_URL: 5 new geocoder tests + 1 new POST test skipped (in `describeDb` block) → total goes up by 5. Pre-existing 4 failures still there. If DATABASE_URL is set, the geocode-on-POST test additionally passes (+1).

- [ ] **Step 5: Commit**

```bash
git -C /Users/fegensprenelon/smith-net add backend/src/auditLog.ts backend/src/jobsService.ts backend/src/__tests__/jobs-routes.test.ts
git -C /Users/fegensprenelon/smith-net commit -m "feat(jobs): async geocode on create/update + JOB_GEOCODED audit"
```

---

## Task 4: `GET /api/profiles/crew` + tests (TDD)

**Files:**
- Modify: `backend/src/profilesRoutes.ts` (append new route)
- Create: `backend/src/__tests__/profiles-crew-route.test.ts`

- [ ] **Step 1: Write the failing test (DATABASE_URL-gated like Plan 2 integration tests)**

Create `backend/src/__tests__/profiles-crew-route.test.ts`:

```ts
import express from 'express';
import request from 'supertest';
import cookieParser from 'cookie-parser';
import { authRouter } from '../authRoutes';
import { profilesRouter } from '../profilesRoutes';
import { jobsRouter } from '../jobsRoutes';
import { userStore, generateTokens, UserRole } from '../auth';
import { pg, isPgEnabled } from '../db';

const describeDb = isPgEnabled() ? describe : describe.skip;

function buildApp() {
  const app = express();
  app.use(express.json());
  app.use(cookieParser());
  app.use('/api/auth', authRouter);
  app.use('/api/profiles', profilesRouter);
  app.use('/api/jobs', jobsRouter);
  return app;
}

async function createForeman(suffix: string) {
  const email = `foreman-crew-${suffix}-${Date.now()}@example.com`;
  const user = await userStore.createUser(email, 'password123', `Foreman ${suffix}`, UserRole.FOREMAN);
  if (isPgEnabled() && pg) {
    await pg.query(
      `INSERT INTO profiles (id, email, display_name, role) VALUES ($1, $2, $3, $4) ON CONFLICT (id) DO NOTHING`,
      [user.id, user.email, user.displayName, 'foreman']
    );
  }
  return { id: user.id, token: generateTokens(user).accessToken };
}

async function createCrew(suffix: string) {
  const email = `crewroster-${suffix}-${Date.now()}@example.com`;
  const user = await userStore.createUser(email, 'password123', `Crew ${suffix}`, UserRole.TEAM_MEMBER);
  if (isPgEnabled() && pg) {
    await pg.query(
      `INSERT INTO profiles (id, email, display_name, role) VALUES ($1, $2, $3, $4) ON CONFLICT (id) DO NOTHING`,
      [user.id, user.email, user.displayName, 'team']
    );
  }
  return { id: user.id, email };
}

afterEach(async () => {
  if (!isPgEnabled() || !pg) return;
  await pg.query(`DELETE FROM job_crew`);
  await pg.query(`DELETE FROM jobs WHERE title LIKE 'crew-roster%'`);
  await pg.query(`DELETE FROM profiles WHERE email LIKE 'foreman-crew-%' OR email LIKE 'crewroster-%'`);
});

describe('GET /api/profiles/crew — auth gates', () => {
  const app = buildApp();

  it('returns 401 with no auth', async () => {
    const res = await request(app).get('/api/profiles/crew');
    expect(res.status).toBe(401);
  });

  it('returns 403 tier_required for Solo user', async () => {
    const u = await userStore.createUser(`solo-roster-${Date.now()}@example.com`, 'password123', 'S', UserRole.SOLO);
    const { accessToken } = generateTokens(u);
    const res = await request(app).get('/api/profiles/crew').set('Authorization', `Bearer ${accessToken}`);
    expect(res.status).toBe(403);
    expect(res.body.code).toBe('tier_required');
  });
});

describeDb('GET /api/profiles/crew — roster derivation', () => {
  const app = buildApp();

  it('returns empty roster when the foreman has no crew assignments', async () => {
    const f = await createForeman('empty');
    const res = await request(app).get('/api/profiles/crew').set('Authorization', `Bearer ${f.token}`);
    expect(res.status).toBe(200);
    expect(res.body.crew).toEqual([]);
  });

  it('returns assigned crew with activeJob=null when no in-progress job', async () => {
    const f = await createForeman('idle');
    const c = await createCrew('idle');
    // Create a planned job, assign crew, leave it planned.
    const created = await request(app).post('/api/jobs').set('Authorization', `Bearer ${f.token}`).send({ title: 'crew-roster-idle' });
    await request(app).post(`/api/jobs/${created.body.job.id}/assign`).set('Authorization', `Bearer ${f.token}`).send({ profileId: c.id });

    const res = await request(app).get('/api/profiles/crew').set('Authorization', `Bearer ${f.token}`);
    expect(res.status).toBe(200);
    expect(res.body.crew.length).toBe(1);
    expect(res.body.crew[0].id).toBe(c.id);
    expect(res.body.crew[0].activeJob).toBeNull();
  });

  it('returns assigned crew with activeJob populated when assigned to in_progress job', async () => {
    const f = await createForeman('busy');
    const c = await createCrew('busy');
    const created = await request(app).post('/api/jobs').set('Authorization', `Bearer ${f.token}`).send({ title: 'crew-roster-busy' });
    const jobId = created.body.job.id;
    await request(app).post(`/api/jobs/${jobId}/assign`).set('Authorization', `Bearer ${f.token}`).send({ profileId: c.id });
    await request(app).patch(`/api/jobs/${jobId}/status`).set('Authorization', `Bearer ${f.token}`).send({ status: 'in_progress' });

    const res = await request(app).get('/api/profiles/crew').set('Authorization', `Bearer ${f.token}`);
    expect(res.status).toBe(200);
    expect(res.body.crew.length).toBe(1);
    expect(res.body.crew[0].activeJob).not.toBeNull();
    expect(res.body.crew[0].activeJob.id).toBe(jobId);
    expect(res.body.crew[0].activeJob.status).toBe('in_progress');
  });

  it('does NOT leak crew from another foreman', async () => {
    const fA = await createForeman('iso-A');
    const fB = await createForeman('iso-B');
    const c = await createCrew('iso-shared');
    const createdA = await request(app).post('/api/jobs').set('Authorization', `Bearer ${fA.token}`).send({ title: 'crew-roster-iso-A' });
    await request(app).post(`/api/jobs/${createdA.body.job.id}/assign`).set('Authorization', `Bearer ${fA.token}`).send({ profileId: c.id });

    const res = await request(app).get('/api/profiles/crew').set('Authorization', `Bearer ${fB.token}`);
    expect(res.status).toBe(200);
    expect(res.body.crew).toEqual([]);
  });
});
```

- [ ] **Step 2: Run — confirm FAIL on the body tests (auth-gate tests may pass if the route returns 404 first; both still fail)**

```bash
cd /Users/fegensprenelon/smith-net/backend && JWT_SECRET=test-secret-at-least-32-chars-long-please-thanks npx jest profiles-crew-route 2>&1 | tail -15
```

Expected: tests fail (no route registered yet → 404 instead of 200/403).

- [ ] **Step 3: Implement the route in `profilesRoutes.ts`**

In `backend/src/profilesRoutes.ts`, add the new route AFTER the existing `GET /` (search) route. Add the `pg` import at the top if not already present:

```ts
import { pg, isPgEnabled } from './db';
```

Add the route handler:

```ts
profilesRouter.get('/crew', async (req: AuthenticatedRequest, res: Response) => {
  const foremanId = req.user!.id;
  if (!isPgEnabled() || !pg) {
    return res.json({ crew: [] });
  }
  try {
    const { rows } = await pg.query(
      `SELECT DISTINCT
         p.id, p.email, p.display_name, p.role,
         ij.id AS active_job_id, ij.title AS active_job_title, ij.status AS active_job_status
       FROM profiles p
       INNER JOIN job_crew jc ON jc.profile_id = p.id
       INNER JOIN jobs j ON j.id = jc.job_id AND j.foreman_id = $1
       LEFT JOIN LATERAL (
         SELECT j2.id, j2.title, j2.status
         FROM jobs j2
         INNER JOIN job_crew jc2 ON jc2.job_id = j2.id AND jc2.profile_id = p.id
         WHERE j2.foreman_id = $1 AND j2.status = 'in_progress'
         ORDER BY j2.updated_at DESC
         LIMIT 1
       ) ij ON true
       ORDER BY p.display_name`,
      [foremanId]
    );
    res.json({
      crew: rows.map((r) => ({
        id: r.id,
        email: r.email,
        displayName: r.display_name,
        role: r.role,
        activeJob: r.active_job_id
          ? { id: r.active_job_id, title: r.active_job_title, status: r.active_job_status }
          : null,
      })),
    });
  } catch (e: any) {
    console.error('[Profiles] crew roster error:', e.message);
    res.status(500).json({ error: 'Failed to load crew roster' });
  }
});
```

- [ ] **Step 4: Run — confirm tests PASS**

```bash
cd /Users/fegensprenelon/smith-net/backend && JWT_SECRET=test-secret-at-least-32-chars-long-please-thanks npx jest profiles-crew-route 2>&1 | tail -10
```

Expected: auth-gate tests pass (2). Roster tests pass (4) if `DATABASE_URL` is set; skipped otherwise.

- [ ] **Step 5: Commit**

```bash
git -C /Users/fegensprenelon/smith-net add backend/src/profilesRoutes.ts backend/src/__tests__/profiles-crew-route.test.ts
git -C /Users/fegensprenelon/smith-net commit -m "feat(profiles): GET /api/profiles/crew — foreman's roster + active job"
```

---

## Task 5: Frontend — install `maplibre-gl` + marker CSS

**Files:**
- Modify: `desktop/portal/package.json` (npm)
- Modify: `desktop/portal/src/console/index.css` (append marker classes)

- [ ] **Step 1: Install**

```bash
cd /Users/fegensprenelon/smith-net/desktop/portal && npm install maplibre-gl@^4.7.0
```

- [ ] **Step 2: Append marker CSS to `desktop/portal/src/console/index.css`**

The current file has `@tailwind` directives + base styles. Append at the end:

```css
/* Plan 4: map marker styles. Used by createJobMarkerElement(). */
.job-marker {
  width: 24px;
  height: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: monospace;
  font-size: 14px;
  font-weight: bold;
  border: 2px solid theme('colors.console-bg');
  border-radius: 4px;
  cursor: pointer;
  color: white;
}
.job-marker-planned     { background: theme('colors.console-text-muted'); }
.job-marker-in_progress { background: theme('colors.console-ok'); }
.job-marker-complete    { background: theme('colors.console-ok'); opacity: 0.6; }
.job-marker-cancelled   { background: theme('colors.console-danger'); }

/* MapLibre default popup needs minor reset to match console aesthetic. */
.maplibregl-popup-content {
  background: theme('colors.console-surface');
  color: theme('colors.console-text');
  border: 1px solid theme('colors.console-border');
  border-radius: 0;
  font-family: monospace;
  padding: 0;
}
.maplibregl-popup-tip {
  border-top-color: theme('colors.console-surface');
}
```

- [ ] **Step 3: Verify build succeeds**

```bash
cd /Users/fegensprenelon/smith-net/desktop/portal && npm run build 2>&1 | tail -10
```

Expected: build succeeds (MapLibre is bundled).

- [ ] **Step 4: Commit**

```bash
git -C /Users/fegensprenelon/smith-net add desktop/portal/package.json desktop/portal/package-lock.json desktop/portal/src/console/index.css
git -C /Users/fegensprenelon/smith-net commit -m "feat(console): install maplibre-gl + marker/popup CSS"
```

---

## Task 6: Frontend — `crewClient.ts` + tests (TDD)

**Files:**
- Create: `desktop/portal/src/console/api/crewClient.ts`
- Create: `desktop/portal/src/console/api/__tests__/crewClient.test.ts`
- Modify: `desktop/portal/src/console/test/msw-handlers.ts` (append handler)

- [ ] **Step 1: Extend MSW handlers**

Append to `desktop/portal/src/console/test/msw-handlers.ts` (inside the `handlers` array):

```ts
  http.get('/api/profiles/crew', () => {
    return HttpResponse.json({
      crew: [
        {
          id: 'p-1',
          email: 'alice@example.com',
          displayName: 'Alice',
          role: 'team',
          activeJob: { id: 'j-1', title: 'Maple Ave', status: 'in_progress' },
        },
        {
          id: 'p-2',
          email: 'bob@example.com',
          displayName: 'Bob',
          role: 'lead',
          activeJob: null,
        },
      ],
    });
  }),
```

- [ ] **Step 2: Write failing test**

```ts
// desktop/portal/src/console/api/__tests__/crewClient.test.ts
import { describe, it, expect } from 'vitest';
import { crewClient } from '../crewClient';

describe('crewClient', () => {
  it('getRoster returns crew array', async () => {
    const result = await crewClient.getRoster();
    expect(result.ok).toBe(true);
    if (result.ok) {
      expect(result.crew).toHaveLength(2);
      expect(result.crew[0].displayName).toBe('Alice');
      expect(result.crew[0].activeJob).not.toBeNull();
      expect(result.crew[1].activeJob).toBeNull();
    }
  });
});
```

- [ ] **Step 3: Run — confirm FAIL**

```bash
cd /Users/fegensprenelon/smith-net/desktop/portal && npm run test:run -- crewClient 2>&1 | tail -10
```

- [ ] **Step 4: Implement `crewClient.ts`**

```ts
// desktop/portal/src/console/api/crewClient.ts
export type CrewRole = 'solo' | 'team' | 'lead' | 'foreman' | 'enterprise' | 'admin';
export type CrewActiveJobStatus = 'planned' | 'in_progress' | 'complete' | 'cancelled';

export interface CrewActiveJob {
  id: string;
  title: string;
  status: CrewActiveJobStatus;
}

export interface CrewEntry {
  id: string;
  email: string;
  displayName: string;
  role: CrewRole;
  activeJob: CrewActiveJob | null;
}

export type CrewResult<T> =
  | ({ ok: true } & T)
  | { ok: false; status: number; error: string; code?: string };

interface RosterResp { crew: CrewEntry[] }

export const crewClient = {
  getRoster: async (): Promise<CrewResult<RosterResp>> => {
    const res = await fetch('/api/profiles/crew', { credentials: 'include' });
    if (!res.ok) {
      const errBody = await res.json().catch(() => ({ error: res.statusText }));
      return { ok: false, status: res.status, error: errBody.error || 'Failed', code: errBody.code };
    }
    const data = (await res.json()) as RosterResp;
    return { ok: true, ...data };
  },
};
```

- [ ] **Step 5: Run — confirm PASS**

```bash
cd /Users/fegensprenelon/smith-net/desktop/portal && npm run test:run -- crewClient 2>&1 | tail -5
```

- [ ] **Step 6: Commit**

```bash
git -C /Users/fegensprenelon/smith-net add desktop/portal/src/console/api/crewClient.ts desktop/portal/src/console/api/__tests__/crewClient.test.ts desktop/portal/src/console/test/msw-handlers.ts
git -C /Users/fegensprenelon/smith-net commit -m "feat(console): crewClient.getRoster + MSW handler"
```

---

## Task 7: Frontend — `crewStore.ts` (TDD)

**Files:**
- Create: `desktop/portal/src/console/stores/crewStore.ts`
- Create: `desktop/portal/src/console/stores/__tests__/crewStore.test.ts`

- [ ] **Step 1: Write failing test**

```ts
import { describe, it, expect, beforeEach } from 'vitest';
import { useCrewStore } from '../crewStore';
import type { CrewEntry } from '../../api/crewClient';

const sample: CrewEntry[] = [
  { id: 'a', email: 'a@x.com', displayName: 'Alice', role: 'team',
    activeJob: { id: 'j1', title: 'X', status: 'in_progress' } },
  { id: 'b', email: 'b@x.com', displayName: 'Bob',   role: 'lead', activeJob: null },
];

describe('crewStore', () => {
  beforeEach(() => useCrewStore.getState().clear());

  it('starts empty', () => {
    expect(useCrewStore.getState().roster).toEqual([]);
    expect(useCrewStore.getState().isStale).toBe(false);
  });

  it('setRoster updates the list and lastFetched', () => {
    const before = useCrewStore.getState().lastFetchedAt;
    useCrewStore.getState().setRoster(sample);
    const s = useCrewStore.getState();
    expect(s.roster).toEqual(sample);
    expect(s.lastFetchedAt).not.toBe(before);
  });

  it('availabilityOf returns "busy" when crew has activeJob', () => {
    useCrewStore.getState().setRoster(sample);
    expect(useCrewStore.getState().availabilityOf('a')).toBe('busy');
  });

  it('availabilityOf returns "free" when no activeJob', () => {
    useCrewStore.getState().setRoster(sample);
    expect(useCrewStore.getState().availabilityOf('b')).toBe('free');
  });

  it('availabilityOf returns "free" for unknown profile id', () => {
    expect(useCrewStore.getState().availabilityOf('nope')).toBe('free');
  });

  it('markStale toggles', () => {
    useCrewStore.getState().markStale(true);
    expect(useCrewStore.getState().isStale).toBe(true);
  });

  it('clear resets everything', () => {
    useCrewStore.getState().setRoster(sample);
    useCrewStore.getState().markStale(true);
    useCrewStore.getState().clear();
    const s = useCrewStore.getState();
    expect(s.roster).toEqual([]);
    expect(s.isStale).toBe(false);
    expect(s.lastFetchedAt).toBeNull();
  });
});
```

- [ ] **Step 2: Run — confirm FAIL**

```bash
cd /Users/fegensprenelon/smith-net/desktop/portal && npm run test:run -- crewStore 2>&1 | tail -10
```

- [ ] **Step 3: Implement `crewStore.ts`**

```ts
// desktop/portal/src/console/stores/crewStore.ts
import { create } from 'zustand';
import type { CrewEntry } from '../api/crewClient';

export type Availability = 'free' | 'busy';

interface CrewState {
  roster: CrewEntry[];
  isLoadingRoster: boolean;
  lastFetchedAt: number | null;
  isStale: boolean;

  setRoster: (roster: CrewEntry[]) => void;
  markLoading: (b: boolean) => void;
  markStale: (b: boolean) => void;
  availabilityOf: (id: string) => Availability;
  clear: () => void;
}

export const useCrewStore = create<CrewState>((set, get) => ({
  roster: [],
  isLoadingRoster: false,
  lastFetchedAt: null,
  isStale: false,

  setRoster: (roster) => set({ roster, lastFetchedAt: Date.now(), isStale: false }),
  markLoading: (isLoadingRoster) => set({ isLoadingRoster }),
  markStale: (isStale) => set({ isStale }),
  availabilityOf: (id) => {
    const entry = get().roster.find((e) => e.id === id);
    return entry && entry.activeJob !== null ? 'busy' : 'free';
  },
  clear: () => set({ roster: [], isLoadingRoster: false, lastFetchedAt: null, isStale: false }),
}));
```

- [ ] **Step 4: Run — confirm 7 PASS**

```bash
cd /Users/fegensprenelon/smith-net/desktop/portal && npm run test:run -- crewStore 2>&1 | tail -5
```

- [ ] **Step 5: Commit**

```bash
git -C /Users/fegensprenelon/smith-net add desktop/portal/src/console/stores/crewStore.ts desktop/portal/src/console/stores/__tests__/crewStore.test.ts
git -C /Users/fegensprenelon/smith-net commit -m "feat(console): crewStore with availabilityOf derivation"
```

---

## Task 8: Frontend — `useCrewRoster.ts` (TDD)

**Files:**
- Create: `desktop/portal/src/console/hooks/useCrewRoster.ts`
- Create: `desktop/portal/src/console/hooks/__tests__/useCrewRoster.test.ts`

- [ ] **Step 1: Write failing test (mirrors `useJobsPolling` pattern)**

```ts
import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useCrewRoster } from '../useCrewRoster';
import { useCrewStore } from '../../stores/crewStore';
import * as crewClient from '../../api/crewClient';

describe('useCrewRoster', () => {
  beforeEach(() => {
    useCrewStore.getState().clear();
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('fires an immediate fetch on mount', async () => {
    const spy = vi.spyOn(crewClient.crewClient, 'getRoster').mockResolvedValue({ ok: true, crew: [] });
    renderHook(() => useCrewRoster(15000));
    await act(async () => { await Promise.resolve(); });
    expect(spy).toHaveBeenCalledTimes(1);
  });

  it('fires another fetch after the interval', async () => {
    const spy = vi.spyOn(crewClient.crewClient, 'getRoster').mockResolvedValue({ ok: true, crew: [] });
    renderHook(() => useCrewRoster(15000));
    await act(async () => { await Promise.resolve(); });
    await act(async () => { vi.advanceTimersByTime(15001); await Promise.resolve(); });
    expect(spy).toHaveBeenCalledTimes(2);
  });

  it('sets isStale on fetch failure', async () => {
    vi.spyOn(crewClient.crewClient, 'getRoster').mockResolvedValue({ ok: false, status: 500, error: 'oops' });
    renderHook(() => useCrewRoster(15000));
    await act(async () => { await Promise.resolve(); });
    expect(useCrewStore.getState().isStale).toBe(true);
  });

  it('cleans up interval on unmount', async () => {
    const spy = vi.spyOn(crewClient.crewClient, 'getRoster').mockResolvedValue({ ok: true, crew: [] });
    const { unmount } = renderHook(() => useCrewRoster(15000));
    await act(async () => { await Promise.resolve(); });
    unmount();
    await act(async () => { vi.advanceTimersByTime(60000); await Promise.resolve(); });
    expect(spy).toHaveBeenCalledTimes(1);
  });
});
```

- [ ] **Step 2: Run — confirm FAIL**

```bash
cd /Users/fegensprenelon/smith-net/desktop/portal && npm run test:run -- useCrewRoster 2>&1 | tail -10
```

- [ ] **Step 3: Implement `useCrewRoster.ts`**

```ts
// desktop/portal/src/console/hooks/useCrewRoster.ts
import { useEffect, useRef } from 'react';
import { crewClient } from '../api/crewClient';
import { useCrewStore } from '../stores/crewStore';

export function useCrewRoster(intervalMs: number = 15_000): void {
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    const fetchOnce = async () => {
      useCrewStore.getState().markLoading(true);
      const result = await crewClient.getRoster();
      useCrewStore.getState().markLoading(false);
      if (result.ok) {
        useCrewStore.getState().setRoster(result.crew);
      } else {
        useCrewStore.getState().markStale(true);
      }
    };

    const start = () => {
      if (intervalRef.current !== null) return;
      intervalRef.current = setInterval(fetchOnce, intervalMs);
    };

    const stop = () => {
      if (intervalRef.current !== null) {
        clearInterval(intervalRef.current);
        intervalRef.current = null;
      }
    };

    const onVisibility = () => {
      if (document.visibilityState === 'visible') {
        fetchOnce();
        start();
      } else {
        stop();
      }
    };

    fetchOnce();
    start();
    document.addEventListener('visibilitychange', onVisibility);

    return () => {
      stop();
      document.removeEventListener('visibilitychange', onVisibility);
    };
  }, [intervalMs]);
}
```

- [ ] **Step 4: Run — confirm 4 PASS**

```bash
cd /Users/fegensprenelon/smith-net/desktop/portal && npm run test:run -- useCrewRoster 2>&1 | tail -5
```

- [ ] **Step 5: Commit**

```bash
git -C /Users/fegensprenelon/smith-net add desktop/portal/src/console/hooks/useCrewRoster.ts desktop/portal/src/console/hooks/__tests__/useCrewRoster.test.ts
git -C /Users/fegensprenelon/smith-net commit -m "feat(console): useCrewRoster — 15s polling, visibility-aware"
```

---

## Task 9: Frontend — `AvailabilityDot` + `CrewCard` (TDD)

**Files:**
- Create: `desktop/portal/src/console/components/crew/AvailabilityDot.tsx`
- Create: `desktop/portal/src/console/components/crew/CrewCard.tsx`
- Create: `desktop/portal/src/console/components/crew/__tests__/CrewCard.test.tsx`

- [ ] **Step 1: Implement `AvailabilityDot.tsx`** (trivial; no separate test — covered via CrewCard tests)

```tsx
// desktop/portal/src/console/components/crew/AvailabilityDot.tsx
import type { Availability } from '../../stores/crewStore';

const COLOR: Record<Availability, string> = {
  free: 'bg-console-ok',
  busy: 'bg-console-accent',
};

export function AvailabilityDot({ availability }: { availability: Availability }) {
  return (
    <span
      className={`inline-block w-2 h-2 rounded-full ${COLOR[availability]}`}
      aria-label={availability}
    />
  );
}
```

- [ ] **Step 2: Write `CrewCard.test.tsx`**

```tsx
import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { CrewCard } from '../CrewCard';
import type { CrewEntry } from '../../../api/crewClient';

const busy: CrewEntry = {
  id: 'a', email: 'a@x.com', displayName: 'Alice', role: 'team',
  activeJob: { id: 'j1', title: 'Maple Ave', status: 'in_progress' },
};

const free: CrewEntry = {
  id: 'b', email: 'b@x.com', displayName: 'Bob', role: 'lead', activeJob: null,
};

describe('CrewCard', () => {
  it('renders display name and email', () => {
    render(<CrewCard entry={free} />);
    expect(screen.getByText('Bob')).toBeInTheDocument();
    expect(screen.getByText('b@x.com')).toBeInTheDocument();
  });

  it('renders role badge', () => {
    render(<CrewCard entry={free} />);
    expect(screen.getByText('lead')).toBeInTheDocument();
  });

  it('renders "on <title>" when busy', () => {
    render(<CrewCard entry={busy} />);
    expect(screen.getByText(/on Maple Ave/i)).toBeInTheDocument();
  });

  it('renders "idle" when free', () => {
    render(<CrewCard entry={free} />);
    expect(screen.getByText(/idle/i)).toBeInTheDocument();
  });

  it('availability dot has correct aria-label for busy', () => {
    render(<CrewCard entry={busy} />);
    expect(screen.getByLabelText('busy')).toBeInTheDocument();
  });

  it('availability dot has correct aria-label for free', () => {
    render(<CrewCard entry={free} />);
    expect(screen.getByLabelText('free')).toBeInTheDocument();
  });
});
```

- [ ] **Step 3: Implement `CrewCard.tsx`**

```tsx
// desktop/portal/src/console/components/crew/CrewCard.tsx
import { Badge } from '../ui/Badge';
import { AvailabilityDot } from './AvailabilityDot';
import type { CrewEntry } from '../../api/crewClient';

export function CrewCard({ entry }: { entry: CrewEntry }) {
  const availability = entry.activeJob !== null ? 'busy' : 'free';
  return (
    <div className="grid grid-cols-[1.5ch_1fr_20ch_8ch_1fr] gap-3 items-center px-3 py-2 border-b border-console-border text-sm font-mono">
      <AvailabilityDot availability={availability} />
      <span className="text-console-text">{entry.displayName}</span>
      <span className="text-console-text-muted truncate">{entry.email}</span>
      <Badge tone="default">{entry.role}</Badge>
      <span className="text-console-text-muted truncate">
        {entry.activeJob ? `on ${entry.activeJob.title}` : 'idle'}
      </span>
    </div>
  );
}
```

- [ ] **Step 4: Run — confirm 6 PASS**

```bash
cd /Users/fegensprenelon/smith-net/desktop/portal && npm run test:run -- CrewCard 2>&1 | tail -5
```

- [ ] **Step 5: Commit**

```bash
git -C /Users/fegensprenelon/smith-net add desktop/portal/src/console/components/crew/
git -C /Users/fegensprenelon/smith-net commit -m "feat(console): AvailabilityDot + CrewCard"
```

---

## Task 10: Frontend — `CrewRoute.tsx` (TDD)

**Files:**
- Create: `desktop/portal/src/console/routes/CrewRoute.tsx`
- Create: `desktop/portal/src/console/routes/__tests__/CrewRoute.test.tsx`

- [ ] **Step 1: Write failing test**

```tsx
import { render, screen, waitFor } from '@testing-library/react';
import { describe, it, expect, beforeEach } from 'vitest';
import { CrewRoute } from '../CrewRoute';
import { useCrewStore } from '../../stores/crewStore';

describe('CrewRoute', () => {
  beforeEach(() => useCrewStore.getState().clear());

  it('renders the empty state when roster is empty', async () => {
    render(<CrewRoute />);
    await waitFor(() => {
      // MSW returns 2 entries, so empty state should NOT show after first fetch.
      expect(screen.getByText('Alice')).toBeInTheDocument();
    });
  });

  it('renders Alice + Bob from the MSW handler', async () => {
    render(<CrewRoute />);
    await waitFor(() => expect(screen.getByText('Alice')).toBeInTheDocument());
    expect(screen.getByText('Bob')).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run — confirm FAIL**

```bash
cd /Users/fegensprenelon/smith-net/desktop/portal && npm run test:run -- CrewRoute 2>&1 | tail -10
```

- [ ] **Step 3: Implement `CrewRoute.tsx`**

```tsx
// desktop/portal/src/console/routes/CrewRoute.tsx
import { CrewCard } from '../components/crew/CrewCard';
import { useCrewRoster } from '../hooks/useCrewRoster';
import { useCrewStore } from '../stores/crewStore';

export function CrewRoute() {
  useCrewRoster();
  const roster = useCrewStore((s) => s.roster);
  const isStale = useCrewStore((s) => s.isStale);

  return (
    <div className="font-mono">
      <div className="flex items-center justify-between mb-4">
        <h1 className="text-console-text text-lg">Crew</h1>
        <span className="text-console-text-muted text-xs">{roster.length} member{roster.length === 1 ? '' : 's'}</span>
      </div>
      {isStale && (
        <div className="bg-console-surface border border-console-warn text-console-warn px-3 py-1 text-xs mb-3">
          [OFFLINE] Couldn't refresh roster
        </div>
      )}
      {roster.length === 0 ? (
        <div className="text-console-text-muted text-sm">No crew yet — assign someone to a job first.</div>
      ) : (
        <div className="border border-console-border">
          {roster.map((entry) => <CrewCard key={entry.id} entry={entry} />)}
        </div>
      )}
    </div>
  );
}
```

- [ ] **Step 4: Run — confirm PASS**

```bash
cd /Users/fegensprenelon/smith-net/desktop/portal && npm run test:run -- CrewRoute 2>&1 | tail -5
```

- [ ] **Step 5: Commit**

```bash
git -C /Users/fegensprenelon/smith-net add desktop/portal/src/console/routes/CrewRoute.tsx desktop/portal/src/console/routes/__tests__/CrewRoute.test.tsx
git -C /Users/fegensprenelon/smith-net commit -m "feat(console): CrewRoute — /console/crew roster page"
```

---

## Task 11: Frontend — `StatsStrip` (TDD)

**Files:**
- Create: `desktop/portal/src/console/components/map/StatsStrip.tsx`
- Create: `desktop/portal/src/console/components/map/__tests__/StatsStrip.test.tsx`

- [ ] **Step 1: Write failing test**

```tsx
import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { StatsStrip } from '../StatsStrip';
import type { Job } from '../../../api/jobsClient';

const j = (id: string, status: Job['status'], daysAgo: number = 0): Job => {
  const d = new Date(Date.now() - daysAgo * 86400 * 1000).toISOString();
  return {
    id, foremanId: 'f', clientId: null, engagementId: null,
    title: id, description: null, status,
    scheduledAt: null, location: null,
    latitude: null, longitude: null, geocodedAt: null,
    createdAt: d, updatedAt: d,
  } as any;
};

describe('StatsStrip', () => {
  it('counts planned and in_progress jobs', () => {
    const jobs = [j('a', 'planned'), j('b', 'planned'), j('c', 'in_progress')];
    render(<StatsStrip jobs={jobs} />);
    expect(screen.getByText(/PLANNED 2/)).toBeInTheDocument();
    expect(screen.getByText(/IN PROGRESS 1/)).toBeInTheDocument();
  });

  it('only counts complete/cancelled within 7 days', () => {
    const jobs = [
      j('recent-c', 'complete', 3),
      j('old-c',    'complete', 10),
      j('recent-x', 'cancelled', 1),
    ];
    render(<StatsStrip jobs={jobs} />);
    // recent complete counted; old not counted
    expect(screen.getByText(/COMPLETE 1/)).toBeInTheDocument();
    expect(screen.getByText(/CANCELLED 1/)).toBeInTheDocument();
  });

  it('renders zeros for empty jobs', () => {
    render(<StatsStrip jobs={[]} />);
    expect(screen.getByText(/PLANNED 0/)).toBeInTheDocument();
    expect(screen.getByText(/IN PROGRESS 0/)).toBeInTheDocument();
    expect(screen.getByText(/COMPLETE 0/)).toBeInTheDocument();
    expect(screen.getByText(/CANCELLED 0/)).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run — confirm FAIL**

```bash
cd /Users/fegensprenelon/smith-net/desktop/portal && npm run test:run -- StatsStrip 2>&1 | tail -5
```

- [ ] **Step 3: Implement `StatsStrip.tsx`**

```tsx
// desktop/portal/src/console/components/map/StatsStrip.tsx
import { useMemo } from 'react';
import type { Job } from '../../api/jobsClient';

const WEEK_MS = 7 * 86400 * 1000;

export function StatsStrip({ jobs }: { jobs: Job[] }) {
  const stats = useMemo(() => {
    const weekAgo = Date.now() - WEEK_MS;
    let planned = 0, inProg = 0, complete = 0, cancelled = 0;
    for (const j of jobs) {
      if (j.status === 'planned') planned++;
      else if (j.status === 'in_progress') inProg++;
      else if (j.status === 'complete' && new Date(j.updatedAt).getTime() > weekAgo) complete++;
      else if (j.status === 'cancelled' && new Date(j.updatedAt).getTime() > weekAgo) cancelled++;
    }
    return { planned, inProg, complete, cancelled };
  }, [jobs]);

  return (
    <div className="font-mono text-xs text-console-text-muted flex gap-3">
      <span>PLANNED {stats.planned}</span><span>·</span>
      <span>IN PROGRESS {stats.inProg}</span><span>·</span>
      <span>COMPLETE {stats.complete} (week)</span><span>·</span>
      <span>CANCELLED {stats.cancelled} (week)</span>
    </div>
  );
}
```

- [ ] **Step 4: Run — confirm 3 PASS**

```bash
cd /Users/fegensprenelon/smith-net/desktop/portal && npm run test:run -- StatsStrip 2>&1 | tail -5
```

- [ ] **Step 5: Commit**

```bash
git -C /Users/fegensprenelon/smith-net add desktop/portal/src/console/components/map/StatsStrip.tsx desktop/portal/src/console/components/map/__tests__/StatsStrip.test.tsx
git -C /Users/fegensprenelon/smith-net commit -m "feat(console): StatsStrip with week-filtered terminal counters"
```

---

## Task 12: Frontend — `MapFilterChips` (TDD)

**Files:**
- Create: `desktop/portal/src/console/components/map/MapFilterChips.tsx`
- Create: `desktop/portal/src/console/components/map/__tests__/MapFilterChips.test.tsx`

- [ ] **Step 1: Write failing test**

```tsx
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import { MapFilterChips } from '../MapFilterChips';

describe('MapFilterChips', () => {
  it('renders both options', () => {
    render(<MapFilterChips mode="active" onChange={vi.fn()} />);
    expect(screen.getByRole('button', { name: /active only/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /all/i })).toBeInTheDocument();
  });

  it('clicking "all" calls onChange("all")', async () => {
    const onChange = vi.fn();
    render(<MapFilterChips mode="active" onChange={onChange} />);
    await userEvent.click(screen.getByRole('button', { name: /^all$/i }));
    expect(onChange).toHaveBeenCalledWith('all');
  });

  it('clicking "active only" calls onChange("active")', async () => {
    const onChange = vi.fn();
    render(<MapFilterChips mode="all" onChange={onChange} />);
    await userEvent.click(screen.getByRole('button', { name: /active only/i }));
    expect(onChange).toHaveBeenCalledWith('active');
  });
});
```

- [ ] **Step 2: Run — confirm FAIL**

```bash
cd /Users/fegensprenelon/smith-net/desktop/portal && npm run test:run -- MapFilterChips 2>&1 | tail -5
```

- [ ] **Step 3: Implement `MapFilterChips.tsx`**

```tsx
// desktop/portal/src/console/components/map/MapFilterChips.tsx
import { twMerge } from 'tailwind-merge';
import { clsx } from 'clsx';

export type FilterMode = 'active' | 'all';

interface Props {
  mode: FilterMode;
  onChange: (mode: FilterMode) => void;
}

const BASE = 'px-3 py-1 text-xs font-mono border';
const ACTIVE = 'bg-console-accent text-white border-console-accent';
const INACTIVE = 'bg-console-surface text-console-text border-console-border hover:bg-console-bg';

export function MapFilterChips({ mode, onChange }: Props) {
  return (
    <div className="flex gap-2">
      <button
        type="button"
        onClick={() => onChange('active')}
        className={twMerge(clsx(BASE, mode === 'active' ? ACTIVE : INACTIVE))}
      >
        active only
      </button>
      <button
        type="button"
        onClick={() => onChange('all')}
        className={twMerge(clsx(BASE, mode === 'all' ? ACTIVE : INACTIVE))}
      >
        all
      </button>
    </div>
  );
}
```

- [ ] **Step 4: Run — confirm 3 PASS**

```bash
cd /Users/fegensprenelon/smith-net/desktop/portal && npm run test:run -- MapFilterChips 2>&1 | tail -5
```

- [ ] **Step 5: Commit**

```bash
git -C /Users/fegensprenelon/smith-net add desktop/portal/src/console/components/map/MapFilterChips.tsx desktop/portal/src/console/components/map/__tests__/MapFilterChips.test.tsx
git -C /Users/fegensprenelon/smith-net commit -m "feat(console): MapFilterChips toggle (active vs all)"
```

---

## Task 13: Frontend — `MapSidePanel` (TDD)

**Files:**
- Create: `desktop/portal/src/console/components/map/MapSidePanel.tsx`
- Create: `desktop/portal/src/console/components/map/__tests__/MapSidePanel.test.tsx`

- [ ] **Step 1: Write failing test**

```tsx
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { MapSidePanel } from '../MapSidePanel';
import type { Job } from '../../../api/jobsClient';

const j = (id: string, status: Job['status']): Job => ({
  id, foremanId: 'f', clientId: null, engagementId: null,
  title: `Job ${id}`, description: null, status,
  scheduledAt: null, location: null,
  latitude: null, longitude: null, geocodedAt: null,
  createdAt: '2026-05-13T00:00:00Z', updatedAt: '2026-05-13T00:00:00Z',
} as any);

describe('MapSidePanel', () => {
  beforeEach(() => localStorage.clear());

  it('shows counts in each section header', () => {
    const jobs = [j('a', 'planned'), j('b', 'planned'), j('c', 'in_progress')];
    render(<MemoryRouter><MapSidePanel jobs={jobs} mode="active" onModeChange={vi.fn()} onSelectJob={vi.fn()} /></MemoryRouter>);
    expect(screen.getByText(/PLANNED \(2\)/)).toBeInTheDocument();
    expect(screen.getByText(/IN PROGRESS \(1\)/)).toBeInTheDocument();
  });

  it('clicking a job row calls onSelectJob with its id', async () => {
    const jobs = [j('a', 'planned')];
    const onSelectJob = vi.fn();
    render(<MemoryRouter><MapSidePanel jobs={jobs} mode="active" onModeChange={vi.fn()} onSelectJob={onSelectJob} /></MemoryRouter>);
    await userEvent.click(screen.getByText('Job a'));
    expect(onSelectJob).toHaveBeenCalledWith('a');
  });

  it('renders cancelled/complete sections when mode=all', () => {
    const jobs = [j('a', 'complete'), j('b', 'cancelled')];
    render(<MemoryRouter><MapSidePanel jobs={jobs} mode="all" onModeChange={vi.fn()} onSelectJob={vi.fn()} /></MemoryRouter>);
    expect(screen.getByText(/COMPLETE \(1\)/)).toBeInTheDocument();
    expect(screen.getByText(/CANCELLED \(1\)/)).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: Run — confirm FAIL**

```bash
cd /Users/fegensprenelon/smith-net/desktop/portal && npm run test:run -- MapSidePanel 2>&1 | tail -5
```

- [ ] **Step 3: Implement `MapSidePanel.tsx`**

```tsx
// desktop/portal/src/console/components/map/MapSidePanel.tsx
import { useState } from 'react';
import { MapFilterChips, FilterMode } from './MapFilterChips';
import type { Job, JobStatus } from '../../api/jobsClient';

const STATUSES: { status: JobStatus; label: string; defaultOpen: boolean }[] = [
  { status: 'planned',     label: 'PLANNED',     defaultOpen: true  },
  { status: 'in_progress', label: 'IN PROGRESS', defaultOpen: true  },
  { status: 'complete',    label: 'COMPLETE',    defaultOpen: false },
  { status: 'cancelled',   label: 'CANCELLED',   defaultOpen: false },
];

interface Props {
  jobs: Job[];
  mode: FilterMode;
  onModeChange: (m: FilterMode) => void;
  onSelectJob: (jobId: string) => void;
}

function SidePanelRow({ job, onClick }: { job: Job; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="w-full text-left px-3 py-2 text-sm font-mono border-b border-console-border hover:bg-console-bg"
    >
      <div className="text-console-accent text-xs">#{job.id.slice(0, 8)}</div>
      <div className="text-console-text truncate">{job.title}</div>
      {job.location && <div className="text-console-text-muted text-xs truncate">{job.location}</div>}
    </button>
  );
}

function Section({ label, jobs, defaultOpen, onSelectJob }: { label: string; jobs: Job[]; defaultOpen: boolean; onSelectJob: (id: string) => void }) {
  const [open, setOpen] = useState(defaultOpen);
  return (
    <div className="border-b border-console-border">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        className="w-full flex items-center justify-between px-3 py-2 bg-console-surface text-console-text-muted text-xs uppercase tracking-wide font-mono"
      >
        <span>{label} ({jobs.length})</span>
        <span>{open ? '[-]' : '[+]'}</span>
      </button>
      {open && jobs.map((j) => <SidePanelRow key={j.id} job={j} onClick={() => onSelectJob(j.id)} />)}
    </div>
  );
}

export function MapSidePanel({ jobs, mode, onModeChange, onSelectJob }: Props) {
  const visible = STATUSES.filter((s) =>
    mode === 'all' ? true : s.status === 'planned' || s.status === 'in_progress'
  );

  return (
    <aside className="w-[300px] border-l border-console-border bg-console-surface flex flex-col font-mono">
      <div className="p-3 border-b border-console-border">
        <MapFilterChips mode={mode} onChange={onModeChange} />
      </div>
      <div className="flex-1 overflow-y-auto">
        {visible.map((s) => (
          <Section
            key={s.status}
            label={s.label}
            jobs={jobs.filter((j) => j.status === s.status)}
            defaultOpen={s.defaultOpen}
            onSelectJob={onSelectJob}
          />
        ))}
      </div>
    </aside>
  );
}
```

- [ ] **Step 4: Run — confirm 3 PASS**

```bash
cd /Users/fegensprenelon/smith-net/desktop/portal && npm run test:run -- MapSidePanel 2>&1 | tail -5
```

- [ ] **Step 5: Commit**

```bash
git -C /Users/fegensprenelon/smith-net add desktop/portal/src/console/components/map/MapSidePanel.tsx desktop/portal/src/console/components/map/__tests__/MapSidePanel.test.tsx
git -C /Users/fegensprenelon/smith-net commit -m "feat(console): MapSidePanel — filter chips + collapsible status sections"
```

---

## Task 14: Frontend — `JobMarker` factory + `JobPopup` (TDD)

**Files:**
- Create: `desktop/portal/src/console/components/map/JobMarker.tsx`
- Create: `desktop/portal/src/console/components/map/JobPopup.tsx`
- Create: `desktop/portal/src/console/components/map/__tests__/JobPopup.test.tsx`

- [ ] **Step 1: Implement `JobMarker.tsx`** (factory function, no test — covered indirectly via MapCanvas)

```tsx
// desktop/portal/src/console/components/map/JobMarker.tsx
import type { Job, JobStatus } from '../../api/jobsClient';

const GLYPH: Record<JobStatus, string> = {
  planned: 'P',
  in_progress: '>',
  complete: 'o',
  cancelled: 'x',
};

export function createJobMarkerElement(job: Job): HTMLDivElement {
  const el = document.createElement('div');
  el.className = `job-marker job-marker-${job.status}`;
  el.textContent = GLYPH[job.status];
  el.setAttribute('data-job-id', job.id);
  el.setAttribute('aria-label', `${job.status} job ${job.title}`);
  return el;
}
```

- [ ] **Step 2: Write failing test for `JobPopup`**

```tsx
import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { JobPopup } from '../JobPopup';
import type { Job, CrewAssignment } from '../../../api/jobsClient';

const job: Job = {
  id: 'j-1', foremanId: 'f', clientId: null, engagementId: null,
  title: 'Service call', description: null, status: 'in_progress',
  scheduledAt: null, location: '47 Maple Ave',
  latitude: 40.7, longitude: -73.9, geocodedAt: '2026-05-13T00:00:00Z',
  createdAt: '2026-05-13T00:00:00Z', updatedAt: '2026-05-13T00:00:00Z',
} as any;

const crew: CrewAssignment[] = [
  { jobId: 'j-1', profileId: 'p-1', roleOnJob: 'lead', assignedAt: '2026-05-13T00:00:00Z' },
];

describe('JobPopup', () => {
  it('renders status badge + title + location', () => {
    render(<MemoryRouter><JobPopup job={job} crew={[]} /></MemoryRouter>);
    expect(screen.getByText(/IN PROGRESS/i)).toBeInTheDocument();
    expect(screen.getByText('Service call')).toBeInTheDocument();
    expect(screen.getByText('47 Maple Ave')).toBeInTheDocument();
  });

  it('renders crew list when present', () => {
    render(<MemoryRouter><JobPopup job={job} crew={crew} /></MemoryRouter>);
    expect(screen.getByText(/CREW \(1\)/)).toBeInTheDocument();
    expect(screen.getByText(/p-1/)).toBeInTheDocument();
    expect(screen.getByText(/\(lead\)/)).toBeInTheDocument();
  });

  it('renders "No crew assigned." when crew is empty', () => {
    render(<MemoryRouter><JobPopup job={job} crew={[]} /></MemoryRouter>);
    expect(screen.getByText(/No crew assigned/i)).toBeInTheDocument();
  });

  it('detail link points at /console/jobs/:id', () => {
    render(<MemoryRouter><JobPopup job={job} crew={[]} /></MemoryRouter>);
    expect(screen.getByRole('link', { name: /open detail/i })).toHaveAttribute('href', '/console/jobs/j-1');
  });
});
```

- [ ] **Step 3: Run — confirm FAIL**

```bash
cd /Users/fegensprenelon/smith-net/desktop/portal && npm run test:run -- JobPopup 2>&1 | tail -5
```

- [ ] **Step 4: Implement `JobPopup.tsx`**

```tsx
// desktop/portal/src/console/components/map/JobPopup.tsx
import { Link } from 'react-router-dom';
import { JobStatusBadge } from '../jobs/JobStatusBadge';
import type { Job, CrewAssignment } from '../../api/jobsClient';

interface Props {
  job: Job;
  crew: CrewAssignment[];
}

export function JobPopup({ job, crew }: Props) {
  return (
    <div className="p-3 min-w-[260px] max-w-[360px] font-mono text-sm">
      <div className="mb-2"><JobStatusBadge status={job.status} /></div>
      <div className="text-console-text mb-1">{job.title}</div>
      {job.location && <div className="text-console-text-muted text-xs mb-2">{job.location}</div>}
      {job.scheduledAt && <div className="text-console-text-muted text-xs mb-2">scheduled: {new Date(job.scheduledAt).toLocaleString()}</div>}

      <div className="border-t border-console-border mt-2 pt-2">
        <div className="text-console-text-muted text-xs uppercase tracking-wide mb-1">CREW ({crew.length})</div>
        {crew.length === 0 ? (
          <div className="text-console-text-muted text-xs">No crew assigned.</div>
        ) : (
          <ul className="text-xs">
            {crew.map((c) => (
              <li key={c.profileId}>• {c.profileId} <span className="text-console-text-muted">({c.roleOnJob})</span></li>
            ))}
          </ul>
        )}
      </div>

      <Link to={`/console/jobs/${job.id}`} className="text-console-accent text-xs block mt-3 hover:underline">
        [-&gt; open detail]
      </Link>
    </div>
  );
}
```

- [ ] **Step 5: Run — confirm 4 PASS**

```bash
cd /Users/fegensprenelon/smith-net/desktop/portal && npm run test:run -- JobPopup 2>&1 | tail -5
```

- [ ] **Step 6: Commit**

```bash
git -C /Users/fegensprenelon/smith-net add desktop/portal/src/console/components/map/JobMarker.tsx desktop/portal/src/console/components/map/JobPopup.tsx desktop/portal/src/console/components/map/__tests__/JobPopup.test.tsx
git -C /Users/fegensprenelon/smith-net commit -m "feat(console): JobMarker factory + JobPopup component"
```

---

## Task 15: Frontend — `MapCanvas.tsx` (TDD with mocked maplibre-gl)

**Files:**
- Create: `desktop/portal/src/console/components/map/MapCanvas.tsx`
- Create: `desktop/portal/src/console/components/map/__tests__/MapCanvas.test.tsx`

- [ ] **Step 1: Write the failing test (with vi.mock for maplibre-gl)**

```tsx
import { render, cleanup } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { MapCanvas } from '../MapCanvas';
import type { Job } from '../../../api/jobsClient';

const mapInstance = {
  on: vi.fn(), off: vi.fn(),
  addControl: vi.fn(),
  fitBounds: vi.fn(),
  flyTo: vi.fn(),
  remove: vi.fn(),
};

const markerInstance = () => ({
  setLngLat: vi.fn().mockReturnThis(),
  setPopup: vi.fn().mockReturnThis(),
  addTo: vi.fn().mockReturnThis(),
  remove: vi.fn(),
  getElement: vi.fn(() => document.createElement('div')),
});

const popupInstance = () => ({
  setLngLat: vi.fn().mockReturnThis(),
  setDOMContent: vi.fn().mockReturnThis(),
  addTo: vi.fn().mockReturnThis(),
  remove: vi.fn(),
});

vi.mock('maplibre-gl', () => ({
  default: {
    Map: vi.fn(() => mapInstance),
    Marker: vi.fn(() => markerInstance()),
    Popup: vi.fn(() => popupInstance()),
    NavigationControl: vi.fn(() => ({})),
  },
}));

const j = (id: string, lat: number | null, lng: number | null, status: Job['status'] = 'planned'): Job => ({
  id, foremanId: 'f', clientId: null, engagementId: null,
  title: `Job ${id}`, description: null, status,
  scheduledAt: null, location: null,
  latitude: lat, longitude: lng, geocodedAt: lat !== null ? '2026-05-13T00:00:00Z' : null,
  createdAt: '2026-05-13T00:00:00Z', updatedAt: '2026-05-13T00:00:00Z',
} as any);

describe('MapCanvas', () => {
  beforeEach(() => { vi.clearAllMocks(); });
  afterEach(cleanup);

  it('constructs a Map on mount', () => {
    render(<MapCanvas jobs={[]} visibleStatuses={['planned']} selectedJobId={null} onSelectJob={vi.fn()} />);
    // maplibre-gl is dynamically imported; constructor called once after effect runs
    // (vi.mock above provides a stub class)
    const maplibre = require('maplibre-gl').default;
    expect(maplibre.Map).toHaveBeenCalled();
  });

  it('creates a marker per job with coords AND matching visibleStatuses', () => {
    const jobs = [
      j('a', 40, -73, 'planned'),
      j('b', null, null, 'planned'),    // missing coords — skipped
      j('c', 41, -74, 'in_progress'),   // status not in visibleStatuses — skipped
    ];
    render(<MapCanvas jobs={jobs} visibleStatuses={['planned']} selectedJobId={null} onSelectJob={vi.fn()} />);
    const maplibre = require('maplibre-gl').default;
    // Only 1 marker should be created (for job 'a')
    expect(maplibre.Marker).toHaveBeenCalledTimes(1);
  });

  it('does NOT crash when jobs is empty', () => {
    expect(() => {
      render(<MapCanvas jobs={[]} visibleStatuses={['planned']} selectedJobId={null} onSelectJob={vi.fn()} />);
    }).not.toThrow();
  });
});
```

- [ ] **Step 2: Run — confirm FAIL**

```bash
cd /Users/fegensprenelon/smith-net/desktop/portal && npm run test:run -- MapCanvas 2>&1 | tail -10
```

- [ ] **Step 3: Implement `MapCanvas.tsx`**

```tsx
// desktop/portal/src/console/components/map/MapCanvas.tsx
import { useEffect, useRef } from 'react';
import maplibregl from 'maplibre-gl';
import type { Job, JobStatus } from '../../api/jobsClient';
import { createJobMarkerElement } from './JobMarker';

interface Props {
  jobs: Job[];
  visibleStatuses: JobStatus[];
  selectedJobId: string | null;
  onSelectJob: (jobId: string) => void;
}

const TILE_STYLE: maplibregl.StyleSpecification = {
  version: 8,
  sources: {
    osm: {
      type: 'raster',
      tiles: ['https://tile.openstreetmap.org/{z}/{x}/{y}.png'],
      tileSize: 256,
      attribution: '© OpenStreetMap contributors',
    },
  },
  layers: [{ id: 'osm', type: 'raster', source: 'osm' }],
};

export function MapCanvas({ jobs, visibleStatuses, onSelectJob }: Props) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<maplibregl.Map | null>(null);
  const markersRef = useRef<Map<string, maplibregl.Marker>>(new Map());

  // Init map once
  useEffect(() => {
    if (!containerRef.current) return;
    try {
      const map = new maplibregl.Map({
        container: containerRef.current,
        style: TILE_STYLE,
        center: [-74.0, 40.7],
        zoom: 2,
      });
      map.addControl(new maplibregl.NavigationControl(), 'top-right');
      mapRef.current = map;
    } catch (e: any) {
      console.warn('[MapCanvas] init failed:', e.message);
    }
    return () => {
      mapRef.current?.remove();
      mapRef.current = null;
      markersRef.current.clear();
    };
  }, []);

  // Diff markers when jobs / visibleStatuses change
  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;

    const showable = jobs.filter(
      (j) =>
        j.latitude !== null &&
        j.longitude !== null &&
        Number.isFinite(j.latitude) &&
        Number.isFinite(j.longitude) &&
        visibleStatuses.includes(j.status)
    );

    const wantIds = new Set(showable.map((j) => j.id));

    // Remove markers no longer needed
    for (const [id, marker] of markersRef.current.entries()) {
      if (!wantIds.has(id)) {
        marker.remove();
        markersRef.current.delete(id);
      }
    }

    // Add or update markers
    for (const j of showable) {
      const existing = markersRef.current.get(j.id);
      if (existing) {
        existing.setLngLat([j.longitude!, j.latitude!]);
        continue;
      }
      const el = createJobMarkerElement(j);
      el.addEventListener('click', () => onSelectJob(j.id));
      const marker = new maplibregl.Marker({ element: el })
        .setLngLat([j.longitude!, j.latitude!])
        .addTo(map);
      markersRef.current.set(j.id, marker);
    }

    // Fit bounds the first time we have pins
    if (showable.length > 0 && markersRef.current.size === showable.length) {
      const bounds = new maplibregl.LngLatBounds();
      for (const j of showable) bounds.extend([j.longitude!, j.latitude!]);
      map.fitBounds(bounds, { padding: 60, maxZoom: 15, duration: 0 });
    }
  }, [jobs, visibleStatuses, onSelectJob]);

  return <div ref={containerRef} className="w-full h-full" data-testid="map-canvas" />;
}
```

- [ ] **Step 4: Run — confirm 3 PASS**

```bash
cd /Users/fegensprenelon/smith-net/desktop/portal && npm run test:run -- MapCanvas 2>&1 | tail -5
```

- [ ] **Step 5: Commit**

```bash
git -C /Users/fegensprenelon/smith-net add desktop/portal/src/console/components/map/MapCanvas.tsx desktop/portal/src/console/components/map/__tests__/MapCanvas.test.tsx
git -C /Users/fegensprenelon/smith-net commit -m "feat(console): MapCanvas — MapLibre bridge with marker diffing"
```

---

## Task 16: Frontend — `MapRoute.tsx` (TDD)

**Files:**
- Create: `desktop/portal/src/console/routes/MapRoute.tsx`
- Create: `desktop/portal/src/console/routes/__tests__/MapRoute.test.tsx`

- [ ] **Step 1: Write failing test**

```tsx
import { render, screen, waitFor } from '@testing-library/react';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { MapRoute } from '../MapRoute';
import { useJobsStore } from '../../stores/jobsStore';

// MapLibre is mocked the same way as in MapCanvas.test.tsx
vi.mock('maplibre-gl', () => ({
  default: {
    Map: vi.fn(() => ({ on: vi.fn(), off: vi.fn(), addControl: vi.fn(), fitBounds: vi.fn(), flyTo: vi.fn(), remove: vi.fn() })),
    Marker: vi.fn(() => ({ setLngLat: vi.fn().mockReturnThis(), addTo: vi.fn().mockReturnThis(), remove: vi.fn(), getElement: vi.fn(() => document.createElement('div')) })),
    Popup: vi.fn(() => ({ setLngLat: vi.fn().mockReturnThis(), setDOMContent: vi.fn().mockReturnThis(), addTo: vi.fn().mockReturnThis(), remove: vi.fn() })),
    NavigationControl: vi.fn(() => ({})),
    LngLatBounds: vi.fn(() => ({ extend: vi.fn() })),
  },
}));

describe('MapRoute', () => {
  beforeEach(() => { useJobsStore.getState().clear(); localStorage.clear(); });

  it('renders the stats strip + side panel + map canvas containers', async () => {
    render(<MemoryRouter><MapRoute /></MemoryRouter>);
    // The MSW handler returns 1 job ("Test Job") on /api/jobs after polling fires
    await waitFor(() => expect(screen.getByText(/PLANNED/i)).toBeInTheDocument());
    expect(screen.getByTestId('map-canvas')).toBeInTheDocument();
  });

  it('shows the create-job button', async () => {
    render(<MemoryRouter><MapRoute /></MemoryRouter>);
    await waitFor(() => expect(screen.getByRole('button', { name: /create job/i })).toBeInTheDocument());
  });
});
```

- [ ] **Step 2: Run — confirm FAIL**

```bash
cd /Users/fegensprenelon/smith-net/desktop/portal && npm run test:run -- MapRoute 2>&1 | tail -5
```

- [ ] **Step 3: Implement `MapRoute.tsx`**

```tsx
// desktop/portal/src/console/routes/MapRoute.tsx
import { useEffect, useState } from 'react';
import { Button } from '../components/ui/Button';
import { CreateJobModal } from '../components/jobs/CreateJobModal';
import { StatsStrip } from '../components/map/StatsStrip';
import { MapSidePanel } from '../components/map/MapSidePanel';
import { MapCanvas } from '../components/map/MapCanvas';
import type { FilterMode } from '../components/map/MapFilterChips';
import { useJobsPolling } from '../hooks/useJobsPolling';
import { useJobsStore } from '../stores/jobsStore';
import type { JobStatus } from '../api/jobsClient';

const FILTER_KEY = 'console.map.filterMode';

function readFilterMode(): FilterMode {
  try {
    const v = localStorage.getItem(FILTER_KEY);
    return v === 'all' ? 'all' : 'active';
  } catch {
    return 'active';
  }
}

const ACTIVE_STATUSES: JobStatus[] = ['planned', 'in_progress'];
const ALL_STATUSES: JobStatus[] = ['planned', 'in_progress', 'complete', 'cancelled'];

export function MapRoute() {
  useJobsPolling('list');
  const jobs = useJobsStore((s) => s.jobs);
  const [mode, setMode] = useState<FilterMode>(readFilterMode);
  const [showCreate, setShowCreate] = useState(false);

  useEffect(() => {
    try { localStorage.setItem(FILTER_KEY, mode); } catch { /* ignore */ }
  }, [mode]);

  const visibleStatuses = mode === 'all' ? ALL_STATUSES : ACTIVE_STATUSES;

  return (
    <div className="flex flex-col h-full font-mono">
      <div className="flex items-center justify-between p-3 border-b border-console-border">
        <StatsStrip jobs={jobs} />
        <Button onClick={() => setShowCreate(true)}>+ Create Job</Button>
      </div>
      <div className="flex flex-1 min-h-0">
        <div className="flex-1 relative">
          <MapCanvas
            jobs={jobs}
            visibleStatuses={visibleStatuses}
            selectedJobId={null}
            onSelectJob={(_id) => { /* future: open popup */ }}
          />
        </div>
        <MapSidePanel
          jobs={jobs}
          mode={mode}
          onModeChange={setMode}
          onSelectJob={(_id) => { /* future: fly to + open popup */ }}
        />
      </div>
      <CreateJobModal open={showCreate} onClose={() => setShowCreate(false)} onCreated={() => setShowCreate(false)} />
    </div>
  );
}
```

- [ ] **Step 4: Run — confirm 2 PASS**

```bash
cd /Users/fegensprenelon/smith-net/desktop/portal && npm run test:run -- MapRoute 2>&1 | tail -5
```

- [ ] **Step 5: Commit**

```bash
git -C /Users/fegensprenelon/smith-net add desktop/portal/src/console/routes/MapRoute.tsx desktop/portal/src/console/routes/__tests__/MapRoute.test.tsx
git -C /Users/fegensprenelon/smith-net commit -m "feat(console): MapRoute composes StatsStrip + MapCanvas + MapSidePanel"
```

---

## Task 17: Frontend — wire routes + ConsoleShell nav update

**Files:**
- Modify: `desktop/portal/src/App.tsx`
- Modify: `desktop/portal/src/console/ConsoleShell.tsx`

- [ ] **Step 1: Modify App.tsx**

Open `desktop/portal/src/App.tsx`. Replace the nested `/console` route's index from `JobsListRoute` to `MapRoute`, and ADD `/console/crew`.

Add to imports:

```tsx
import { MapRoute } from './console/routes/MapRoute';
import { CrewRoute } from './console/routes/CrewRoute';
```

The current nested route block:

```tsx
<Route
  path="/console"
  element={
    <RequireAuth>
      <ConsoleShell><Outlet /></ConsoleShell>
    </RequireAuth>
  }
>
  <Route index element={<JobsListRoute />} />
  <Route path="jobs" element={<JobsListRoute />} />
  <Route path="jobs/:id" element={<JobDetailRoute />} />
</Route>
```

Becomes:

```tsx
<Route
  path="/console"
  element={
    <RequireAuth>
      <ConsoleShell><Outlet /></ConsoleShell>
    </RequireAuth>
  }
>
  <Route index element={<MapRoute />} />
  <Route path="jobs" element={<JobsListRoute />} />
  <Route path="jobs/:id" element={<JobDetailRoute />} />
  <Route path="crew" element={<CrewRoute />} />
</Route>
```

- [ ] **Step 2: Modify ConsoleShell nav**

In `desktop/portal/src/console/ConsoleShell.tsx`, replace the current `<nav>` body. Current (from Plan 3):

```tsx
<NavLink to="/console/jobs" className={...}>Jobs</NavLink>
<div className="block px-2 py-1 text-console-text-muted/60 cursor-not-allowed" title="Coming soon">Map</div>
```

Replace with three NavLinks (Map first, Jobs second, Crew third):

```tsx
<NavLink
  to="/console"
  end
  className={({ isActive }) =>
    `block px-2 py-1 ${isActive ? 'text-console-accent' : 'text-console-text hover:text-console-accent'}`
  }
>
  Map
</NavLink>
<NavLink
  to="/console/jobs"
  className={({ isActive }) =>
    `block px-2 py-1 ${isActive ? 'text-console-accent' : 'text-console-text hover:text-console-accent'}`
  }
>
  Jobs
</NavLink>
<NavLink
  to="/console/crew"
  className={({ isActive }) =>
    `block px-2 py-1 ${isActive ? 'text-console-accent' : 'text-console-text hover:text-console-accent'}`
  }
>
  Crew
</NavLink>
```

Note: the `end` prop on the Map NavLink prevents it from matching `/console/jobs` (it would otherwise match because `/console/jobs` starts with `/console`).

- [ ] **Step 3: Verify the full frontend suite — no new failures**

```bash
cd /Users/fegensprenelon/smith-net/desktop/portal && npm run test:run 2>&1 | tail -5
```

Expected: tests still pass. The ConsoleShell test from Plan 1 may need updating if it specifically looks for "(routes coming soon)" text — verify and if so, update.

- [ ] **Step 4: Run tsc check**

```bash
cd /Users/fegensprenelon/smith-net/desktop/portal && npx tsc --noEmit 2>&1 | grep -E "console/" | head -10 || echo "no console errors"
```

Expected: "no console errors".

- [ ] **Step 5: Run frontend build**

```bash
cd /Users/fegensprenelon/smith-net/desktop/portal && npm run build 2>&1 | tail -10
```

Expected: build succeeds.

- [ ] **Step 6: Commit**

```bash
git -C /Users/fegensprenelon/smith-net add desktop/portal/src/App.tsx desktop/portal/src/console/ConsoleShell.tsx
git -C /Users/fegensprenelon/smith-net commit -m "feat(console): /console index → Map; add /console/crew; nav with 3 real links"
```

---

## Task 18: Manual browser walkthrough

No code changes. Pre-conditions: `DATABASE_URL` set, migration 004 applied, network access to Nominatim.

- [ ] **Step 1: Apply migration 004 (if not yet applied)**

```bash
psql "$DATABASE_URL" -f /Users/fegensprenelon/smith-net/backend/migrations/004_jobs_coords.sql
```

- [ ] **Step 2: Start backend**

```bash
cd /Users/fegensprenelon/smith-net/backend && JWT_SECRET=verification-secret-at-least-32-chars-long-please-thanks DATABASE_URL="$DATABASE_URL" npm run dev
```

- [ ] **Step 3: Start frontend (separate terminal)**

```bash
cd /Users/fegensprenelon/smith-net/desktop/portal && npm run dev
```

- [ ] **Step 4: Walk the flow at http://localhost:5173/**

1. Open `/console` — RequireAuth redirects to `/console/login` if not authenticated
2. Login as `admin@smithnet.local` — lands on `/console` (MapRoute, NOT JobsListRoute)
3. Stats strip visible at top with counters
4. Side panel on the right with status sections; filter chips at top show "active only" selected
5. Map renders OSM tiles in the center pane
6. Click `+ Create Job` — modal opens, type title="Empire State smoke", location="Empire State Building, NYC" — submit
7. Within ~2 seconds, a pin appears on the map at the Empire State Building's coords (geocode completed asynchronously)
8. Click the pin (or row in side panel) — popup shows status badge + title + location + "No crew assigned." + `[-> open detail]` link
9. Click `[-> open detail]` — navigates to `/console/jobs/<id>` (existing JobDetailRoute, unchanged)
10. Back to `/console` — pin still there
11. Click `Crew` in nav — `/console/crew` shows roster (admin appears once they're assigned to a job)
12. Back to `/console` — toggle filter chip to "all" — terminal-status pins appear
13. Reload page — filter mode persists (still "all")
14. Login as a Solo user (or register one) — `/console` shows "Upgrade Required" card (RequireAuth tier gate intact)

If all 14 steps pass, Plan 4 is shippable.

- [ ] **Step 5: Stop dev servers + print commit summary**

```bash
git -C /Users/fegensprenelon/smith-net log --oneline 56e91ca..HEAD
```

Expected: ~17 commits from this plan (Task 1 through Task 17), plus the spec commit (`56e91ca`).

---

## Self-Review

**Spec coverage:**
- Migration 004 (lat/lng cols) — Task 1
- `geocoder.ts` (Nominatim client + token bucket) — Task 2
- `JOB_GEOCODED` audit + async geocode in `create()`/`update()` — Task 3
- `GET /api/profiles/crew` — Task 4
- `maplibre-gl` install + marker CSS — Task 5
- `crewClient` — Task 6
- `crewStore` — Task 7
- `useCrewRoster` — Task 8
- `AvailabilityDot` + `CrewCard` — Task 9
- `CrewRoute` — Task 10
- `StatsStrip` — Task 11
- `MapFilterChips` — Task 12
- `MapSidePanel` — Task 13
- `JobMarker` factory + `JobPopup` — Task 14
- `MapCanvas` — Task 15
- `MapRoute` (composition) — Task 16
- App.tsx route wire + ConsoleShell nav — Task 17
- Manual walkthrough — Task 18

Every spec section maps to a task.

**Placeholder scan:** None. Every step has real code or real commands.

**Type consistency:**
- `Job` interface extended with `latitude` / `longitude` / `geocodedAt` consistently across backend and frontend
- `CrewEntry`, `CrewActiveJob`, `Availability`, `FilterMode` defined once, consumed downstream
- `JobStatus` values consistent (`planned` / `in_progress` / `complete` / `cancelled`)
- MapLibre mock returns stub Map/Marker/Popup classes; consistent across MapCanvas and MapRoute tests

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-05-13-plan-4-map-and-crew.md`. Two execution options:**

1. **Subagent-Driven (recommended)** — fresh subagent per task, review between tasks
2. **Inline Execution** — execute tasks in this session with checkpoints

**Which approach?**
