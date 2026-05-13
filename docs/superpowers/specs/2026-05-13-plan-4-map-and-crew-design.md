# Plan 4 — Map-First Console + Crew Page — Design

**Date:** 2026-05-13
**Scope:** Frontend Map view as the new `/console` landing route + Crew roster page at `/console/crew` + backend geocoding (Nominatim) + crew-roster API endpoint.
**Target:** `/desktop/portal/src/console/` (frontend) + `/backend/src/` (additions + migration 004)
**Predecessors:** Plan 1 (commit `ba5728c`), Plan 2 (commit `34186e9`), Plan 3 (commit `e255168`)

## Summary

Make the operator console open onto a Map view that pins the foreman's jobs. Right-side panel keeps the existing list-grouped-by-status surface. Click a pin → popup with crew + status + detail link. Add a Crew browse page at `/console/crew` showing the roster derived from `job_crew` assignments (no separate crew CRUD).

Jobs already store `location` as free text. Plan 4 adds asynchronous Nominatim geocoding at create/update time — the user types "47 Maple Ave, Unit B" once, and the row gets `latitude`/`longitude` columns populated in the background. The job appears in the side panel instantly; the pin appears within ~2 seconds (or whenever the next 15s poll runs).

This addresses the user feedback after seeing the Plan 3 UI: "open with the map." The map becomes the dispatch command center.

## Decisions

| Decision | Choice | Why |
|----------|--------|-----|
| Landing route | `/console` → MapRoute (was JobsListRoute) | User-requested. Map is the dispatch command center. |
| Map library | MapLibre GL (npm `maplibre-gl`) | Free, no API key, OSM tiles, MIT license. Same OSM tile source the Android `osmdroid` side uses — visual alignment. |
| Geocoding | Nominatim, async after `INSERT` / `UPDATE`, soft-fail | Free, no API key, OSM-aligned. Job creation stays fast (non-blocking). Privacy-cost flagged (every location text goes to a third-party server). |
| Geocoder rate limit | Token bucket — never exceed 1 req/sec | Nominatim's published policy. Backend enforcement. |
| Crew model | Roster derived from `job_crew` join, NOT a separate CRUD entity | Reuses Plan 2 data. Avoids parallel user model. Plan 5 can add managed crew CRUD if needed. |
| Crew on map | Shown inside job popups, NOT separate pins | Avoids the "crew assigned to 3 jobs — where do they pin?" problem. Crew get a dedicated browse page at `/console/crew`. |
| Crew location feed | Deferred (no live GPS from Android) | Substantial scope + battery/privacy implications. Future plan. |
| Coords storage | New columns `latitude DOUBLE PRECISION`, `longitude DOUBLE PRECISION`, `geocoded_at TIMESTAMPTZ` on `jobs` | Standard pattern. Partial index on coords for bounding-box queries. |
| Real-time | Polling at 15s (same as Plan 3) | No WS. The side panel + map both consume `jobsStore`; one polling source feeds both. |
| Pin colors | Status-driven: planned=grey, in_progress=ok-green, complete=faded-green, cancelled=danger-red | Maps to existing JobStatusBadge tone scheme. |
| Default filter | "Active only" (hide complete + cancelled pins) | Dispatch focus. User can toggle to "all" — persisted in localStorage. |
| Side panel default | Expanded (300px) | Most-common workflow uses the list + map together. |
| Side panel collapse state | Persisted in localStorage | Standard expectation. |
| `+ Create Job` from Map | Same modal as Plan 3 | No duplication. Surfaced as a header button. |
| Create-job from clicking empty map area | NOT in scope | Nice future addition (reverse-geocode → pre-fill location). |
| Stats strip | Counters in the header: `PLANNED · IN PROGRESS · COMPLETE (week) · CANCELLED (week)` | Adds the "dashboard-y" feel the user asked about — week filter on terminal states is client-side from `updatedAt`. |
| MapLibre integration | Imperative effect (markers managed outside React tree) | MapLibre is OOP/imperative. Forcing it into React idioms creates churn. Mark refs in a `useRef` map, diff on jobs change. |
| `MapCanvas` mock in tests | `vi.mock('maplibre-gl', ...)` returns stub class | Vitest jsdom doesn't support WebGL. Visual map verified manually. |
| Crew page scope | Read-only roster (name + email + role badge + availability + active-job indicator) | Plan 4 ships the roster. Managed crew CRUD (invite, deactivate, etc.) — separate future plan. |
| New backend endpoint | `GET /api/profiles/crew` separate from `/api/profiles?q=` search | Different concern (roster vs. lookup); response shape differs. |

## Architecture

### Route reshuffle

```
/                       → marketing/Auth (legacy)
/console/login          → LoginForm
/console/register       → RegisterForm
/console                → MapRoute               (NEW — replaces JobsListRoute as index)
/console/jobs           → JobsListRoute          (UNCHANGED — moved off the index)
/console/jobs/:id       → JobDetailRoute         (UNCHANGED)
/console/crew           → CrewRoute              (NEW)
```

ConsoleShell nav becomes:

```
NAV
  Map   ← active link to /console (NEW)
  Jobs  ← active link to /console/jobs
  Crew  ← active link to /console/crew (was disabled "Coming soon")
```

### File structure

**New frontend files (under `desktop/portal/src/console/`):**

```
api/
  crewClient.ts                          // getRoster() + future crew calls
stores/
  crewStore.ts                           // roster, isLoadingRoster, lastFetched, availability helpers
hooks/
  useCrewRoster.ts                       // 15s polling, visibility-aware
routes/
  MapRoute.tsx                           // landing
  CrewRoute.tsx
components/
  map/
    MapCanvas.tsx                        // MapLibre container + marker manager
    JobMarker.tsx                        // DOM-element factory for markers
    JobPopup.tsx                         // React component for popup contents
    MapSidePanel.tsx                     // right-side job list (collapsible)
    MapFilterChips.tsx                   // [all] / [active only] toggle
    StatsStrip.tsx                       // header counters
  crew/
    CrewCard.tsx
    AvailabilityDot.tsx
```

Plus matching `__tests__/` directories.

**Modified frontend files:**

- `App.tsx` — change nested route index from `JobsListRoute` to `MapRoute`
- `ConsoleShell.tsx` — replace nav placeholder/disabled with three real NavLinks
- `test/msw-handlers.ts` — append handler for `GET /api/profiles/crew`
- `package.json` — add `maplibre-gl` dependency

**New backend files:**

```
src/geocoder.ts                          // Nominatim client + token bucket
src/__tests__/geocoder.test.ts
src/__tests__/profiles-crew-route.test.ts
migrations/004_jobs_coords.sql
```

**Modified backend files:**

- `src/jobsService.ts` — extend `create()` + `update()` to fire async geocode; add `JOB_GEOCODED` audit
- `src/auditLog.ts` — add `JOB_GEOCODED = 'job.geocoded'`
- `src/profilesRoutes.ts` — add `GET /api/profiles/crew` route
- `src/schemas/profiles.ts` — possibly extend (no body schema for GET /crew; no zod for query — empty)

### Stack additions

- npm: `maplibre-gl` (frontend dependency, MIT)
- No new backend dependencies (Nominatim called via `fetch`)

### Scope boundary — explicitly NOT in this plan

- WebSocket / live push (still polling)
- Drag-to-assign on map
- Live GPS feed from Android crew
- Click empty map area to create job pre-filled from reverse-geocode
- Address autocomplete in CreateJobModal
- Crew management CRUD (invite, deactivate, role change) — future plan
- Self-hosted Nominatim
- Multi-region pin clustering (no clustering MVP; OK for ~hundreds of pins)

## Map view layout

```
┌─────────────────────────────────────────────────────────────────────────────┐
│ SMITH NET / CONSOLE     [stats strip]      System Admin  ADMIN  [Logout]    │
├────────┬──────────────────────────────────────────────────────────┬─────────┤
│ NAV    │                                                          │JOBS (4) │
│ Map    │                                                          │         │
│ Jobs   │              ┌────────────┐                              │PLANNED 1│
│ Crew   │              │   Map      │   (full-bleed MapLibre)      │•#ca48..│
│        │              │  tiles     │                              │ Service │
│        │              │  + pins    │                              │ call    │
│        │              └────────────┘                              │IN_PROG 1│
│        │                                                          │•#abcd..│
│        │                                                          │[collapse]│
└────────┴──────────────────────────────────────────────────────────┴─────────┘
```

**Top stats strip:** `PLANNED 3 · IN PROGRESS 1 · COMPLETE 12 (week) · CANCELLED 2 (week)` — monospace, terminal-line style, updates with polling.

**Pin colors by status:**
| Status | Color | ASCII glyph in pin |
|---|---|---|
| planned | `console-text-muted` (neutral grey) | `P` |
| in_progress | `console-ok` (green) | `>` |
| complete | `console-ok` faded | `o` |
| cancelled | `console-danger` (red) | `x` |

Active-only filter (default) hides complete + cancelled pins.

**Pin click popup:**

```
┌─────────────────────────────────┐
│ [IN PROGRESS]                   │
│ Service call — Maple Ave        │
│ 47 Maple Ave, Unit B            │
│ scheduled: tomorrow 9am         │
│                                 │
│ CREW (2)                        │
│ • System Admin (lead)           │
│ • Crew Member B                 │
│                                 │
│ [→ open detail]                 │
└─────────────────────────────────┘
```

Detail link navigates to `/console/jobs/:id` (existing JobDetailRoute, unchanged).

**Side panel:**
- 300px wide; collapse toggle reduces to a thin strip with counts
- Filter chips at top: `[ all ]` `[ active only ]` (default)
- Status sections like Plan 3's JobsListRoute, narrower
- Click a row → fly the map to that pin + open its popup
- Empty side panel state: `No jobs yet. [+ Create Job]`

## Data flow

```
MapRoute mounts
  ├─ useJobsPolling('list')              // existing Plan 3 hook
  ├─ useCrewRoster()                     // NEW — GET /api/profiles/crew at 15s
  ├─ MapCanvas reads jobsStore.jobs
  │    └─ For each job with lat+lng AND matching visibleStatuses, place a Marker
  ├─ MapSidePanel reads jobsStore.jobs (all statuses)
  ├─ StatsStrip reads jobsStore.jobs (counts derived)
  └─ Pin click / side-panel row click → setSelectedJobId → JobPopup opens

CrewRoute mounts
  ├─ useCrewRoster()                     // same hook as Map for cache reuse
  └─ Table of CrewCard rows
```

### Create-job geocoding flow

```
POST /api/jobs { title, location: "47 Maple Ave, Unit B" }
  ├─ jobsService.create() inserts row with status='planned' and coords=null
  ├─ auditLog.log(JOB_CREATED, foremanId, { jobId, title, status, location, ... })
  ├─ Response: 201 { job } — returned immediately; coords still null in body
  └─ Background (fire-and-forget Promise, no await):
       └─ geocoder.geocodeLocation("47 Maple Ave, Unit B")
            ├─ Token-bucket: wait if needed (≤1 req/sec global)
            ├─ fetch nominatim.openstreetmap.org/search?q=...&format=json&limit=1
            │     headers: { User-Agent: 'SmithNet/1.0' }
            ├─ Success: UPDATE jobs SET latitude=$1, longitude=$2, geocoded_at=NOW() WHERE id=$3
            │           + auditLog.log(JOB_GEOCODED, foremanId, { jobId, lat, lng })
            └─ Failure (429 / network / empty): silent. Job stays unpinned. Log non-fatal.
```

User experience: card appears in side panel immediately on POST response. Pin appears on next polling tick (within 15s) once the UPDATE has landed.

### Update-job geocoding

`jobsService.update(jobId, patch)` — if `patch.location` is present AND different from current `location`, also clear coords + fire geocode for the new text. Same async pattern as create.

## Components

### `MapCanvas.tsx`

```ts
interface Props {
  jobs: Job[];                 // jobsStore.jobs
  visibleStatuses: JobStatus[];
  selectedJobId: string | null;
  onSelectJob: (id: string) => void;
}
```

- Initializes MapLibre via `useEffect(() => new maplibregl.Map(...), [])` (once)
- Marker manager: a `useRef<Map<jobId, maplibregl.Marker>>()` diffs jobs each render
- Pins for jobs WITH coords AND status in `visibleStatuses`
- Click a pin → calls `onSelectJob(jobId)`, parent decides to open popup
- Bounds fitting: on first jobs load, `fitBounds()` over all pin coords; subsequent updates don't auto-pan
- Style: OSM raster tiles via `style: { version: 8, sources: { osm: { type: 'raster', tiles: ['https://tile.openstreetmap.org/{z}/{x}/{y}.png'], tileSize: 256, attribution: '© OpenStreetMap' } }, layers: [{ id: 'osm', type: 'raster', source: 'osm' }] }`

### `JobMarker.tsx` (factory, not a component)

```ts
export function createJobMarkerElement(job: Job): HTMLDivElement {
  const el = document.createElement('div');
  el.className = `job-marker job-marker-${job.status}`;  // CSS handles status colors
  el.textContent = STATUS_GLYPH[job.status];              // 'P' | '>' | 'o' | 'x'
  el.setAttribute('data-job-id', job.id);
  return el;
}
```

CSS classes in `console/index.css`:

```css
.job-marker {
  width: 24px; height: 24px;
  display: flex; align-items: center; justify-content: center;
  font-family: monospace; font-size: 14px;
  border: 2px solid theme('colors.console-bg');
  border-radius: 4px; cursor: pointer;
}
.job-marker-planned     { background: theme('colors.console-text-muted'); color: white; }
.job-marker-in_progress { background: theme('colors.console-ok'); color: white; }
.job-marker-complete    { background: theme('colors.console-ok'); color: white; opacity: 0.6; }
.job-marker-cancelled   { background: theme('colors.console-danger'); color: white; }
```

### `JobPopup.tsx`

React component rendered into a detached DOM node via `ReactDOM.createPortal(...)` to a div, which is passed to `popup.setDOMContent(div)`. Renders status badge, title, location, scheduled, crew list (read from current `jobsStore` detail slice if available, else light fetch), `[→ open detail]` link.

### `MapSidePanel.tsx`

- Header: filter chips
- Body: 4 status sections (same shape as Plan 3 JobsListRoute, narrower)
- Footer: collapse toggle
- Collapse state via `localStorage` (`console.map.sidePanel.collapsed`)
- Filter mode via `localStorage` (`console.map.filterMode`)

### `MapFilterChips.tsx`

Two pill buttons: `[active only]` / `[all]`. Active one styled with `bg-console-accent`.

### `StatsStrip.tsx`

```ts
const stats = useMemo(() => {
  const planned = jobs.filter(j => j.status === 'planned').length;
  const inProg  = jobs.filter(j => j.status === 'in_progress').length;
  const weekAgo = Date.now() - 7 * 86400 * 1000;
  const complete = jobs.filter(j => j.status === 'complete' && new Date(j.updatedAt).getTime() > weekAgo).length;
  const cancelled = jobs.filter(j => j.status === 'cancelled' && new Date(j.updatedAt).getTime() > weekAgo).length;
  return { planned, inProg, complete, cancelled };
}, [jobs]);
```

Renders four counters separated by `·` glyphs.

### `MapRoute.tsx`

Composes the above. Reads `visibleStatuses` from `localStorage`-backed filter mode. Renders side panel + map side-by-side. `+ Create Job` button surfaces in the StatsStrip area (or a small header above the side panel — pick during impl).

### `CrewRoute.tsx`

`useCrewRoster()` poll + table of `CrewCard`. Empty state: "No crew yet — assign someone to a job first."

### `CrewCard.tsx`

```ts
interface Props { entry: CrewEntry }
// CrewEntry: { id, displayName, email, role, activeJob: { id, title, status } | null }
```

Renders: `AvailabilityDot` (green if `activeJob === null`, gold if `activeJob`) + name + email + role badge + active-job summary (`"on Service call — Maple Ave"` or `"idle"`).

### `useCrewRoster.ts`

Mirrors `useJobsPolling`:
- Immediate fetch on mount
- 15s interval
- Visibility-aware (clear on hidden, re-fire on visible)
- Writes to `crewStore.setRoster()` on success; `markStale(true)` on failure

### Backend `geocoder.ts`

```ts
import fetch from undici-or-node-builtin;  // node 20+ has global fetch

const RATE_LIMIT_MS = 1100;  // 1.1s gap = ≤1 req/sec safely
let nextAllowedAt = 0;

export async function geocodeLocation(text: string): Promise<{ lat: number; lng: number } | null> {
  // Token-bucket wait
  const now = Date.now();
  if (now < nextAllowedAt) {
    await new Promise(r => setTimeout(r, nextAllowedAt - now));
  }
  nextAllowedAt = Math.max(Date.now(), nextAllowedAt) + RATE_LIMIT_MS;

  try {
    const url = `https://nominatim.openstreetmap.org/search?q=${encodeURIComponent(text)}&format=json&limit=1`;
    const res = await fetch(url, { headers: { 'User-Agent': 'SmithNet/1.0 (operator console)' } });
    if (!res.ok) {
      console.warn('[Geocoder] non-2xx:', res.status, text);
      return null;
    }
    const arr = await res.json() as { lat: string; lon: string }[];
    if (arr.length === 0) {
      console.warn('[Geocoder] no result for:', text);
      return null;
    }
    return { lat: parseFloat(arr[0].lat), lng: parseFloat(arr[0].lon) };
  } catch (e: any) {
    console.warn('[Geocoder] error:', e.message, 'for:', text);
    return null;
  }
}
```

### Backend `profilesRoutes.ts` — `GET /api/profiles/crew`

```ts
profilesRouter.get('/crew', async (req: AuthenticatedRequest, res: Response) => {
  const foremanId = req.user!.id;
  const { rows } = await pg.query(`
    SELECT DISTINCT
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
    ORDER BY p.display_name
  `, [foremanId]);

  res.json({
    crew: rows.map(r => ({
      id: r.id, email: r.email, displayName: r.display_name, role: r.role,
      activeJob: r.active_job_id ? { id: r.active_job_id, title: r.active_job_title, status: r.active_job_status } : null,
    }))
  });
});
```

### Migration 004

```sql
-- 004_jobs_coords.sql
ALTER TABLE jobs
  ADD COLUMN IF NOT EXISTS latitude    DOUBLE PRECISION,
  ADD COLUMN IF NOT EXISTS longitude   DOUBLE PRECISION,
  ADD COLUMN IF NOT EXISTS geocoded_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_jobs_coords ON jobs(latitude, longitude)
  WHERE latitude IS NOT NULL AND longitude IS NOT NULL;
```

## Error handling

| Class | Strategy |
|---|---|
| Nominatim 429 | Token bucket pre-empts; silent fail; job unpinned; log |
| Nominatim empty result | Silent fail; job unpinned; log |
| Nominatim network error | Silent fail; log |
| WebGL unsupported (very old browser) | Catch `new maplibregl.Map(...)` exception; render fallback message + side panel only |
| OSM tile fetch fails | MapLibre default — grey tiles; pins still render; acceptable |
| `/api/profiles/crew` 401 | Plan 3's auth redirect handles it |
| `/api/profiles/crew` 403 tier | RequireAuth's upgrade card handles it |
| Side panel localStorage parse error | Default to expanded + active-only |
| Geocoded coords invalid (NaN, out of range) | Defensive guard in `MapCanvas` filters non-finite coords |

## Privacy flag

Nominatim is hosted by OpenStreetMap. Each geocode sends the location text to a third-party server. For prod beyond MVP, consider:

1. Self-hosted Nominatim (Docker, OSM data dump)
2. Commercial geocoder with usage agreement (Mapbox, Google)

Plan 4 ships with Nominatim. Document the privacy posture in env-setup docs.

## Testing

### Frontend (Vitest + RTL + MSW, no DATABASE_URL)

| File | Cases |
|---|---|
| `crewClient.test.ts` | `getRoster()` happy + 401/403 envelopes |
| `crewStore.test.ts` | setRoster / clear / availabilityOf(id) derives free vs busy |
| `useCrewRoster.test.ts` | Mount fetches; 15s interval; visibility-aware; cleanup |
| `MapSidePanel.test.tsx` | 4 sections render with counts; filter chip toggles; row click fires onSelectJob; collapse state persists |
| `MapFilterChips.test.tsx` | Active-only default; toggle calls onChange |
| `StatsStrip.test.tsx` | 4 counters render; 7d cutoff on terminal states; zero-state without crash |
| `JobPopup.test.tsx` | Renders status badge + title + location + crew list + detail link |
| `CrewCard.test.tsx` | Name + email + role badge + AvailabilityDot reflects free vs busy + active-job summary |
| `CrewRoute.test.tsx` | Empty state; populated state |
| `MapRoute.test.tsx` | Composes children; MapCanvas mocked via `vi.mock('maplibre-gl', ...)` |

**`maplibre-gl` mock:**

```ts
vi.mock('maplibre-gl', () => ({
  default: {
    Map: vi.fn(() => ({
      on: vi.fn(), addControl: vi.fn(), fitBounds: vi.fn(),
      remove: vi.fn(), flyTo: vi.fn(),
    })),
    Marker: vi.fn(() => ({ setLngLat: vi.fn().mockReturnThis(), addTo: vi.fn().mockReturnThis(), remove: vi.fn() })),
    Popup: vi.fn(() => ({ setLngLat: vi.fn().mockReturnThis(), setDOMContent: vi.fn().mockReturnThis(), addTo: vi.fn().mockReturnThis(), remove: vi.fn() })),
  },
}));
```

Estimated new frontend tests: **~35**.

### Backend (Jest + supertest)

| File | Cases |
|---|---|
| `geocoder.test.ts` | Mock global `fetch`: 200 with result → returns `{lat, lng}`; 200 with `[]` → null; 429 → null; network error → null; 1.1s rate-limit gap (fake timers) |
| `profiles-crew-route.test.ts` | 401; 403 Solo; 200 returns roster derived from `job_crew`; `activeJob` populated only for in-progress; cross-foreman isolation |
| `jobs-routes.test.ts` (extend) | POST returns 201 with `latitude=null`; mock `geocodeLocation` returns coords; await microtask; subsequent GET shows populated coords; `JOB_GEOCODED` audit entry written |

Estimated new backend tests: **~12**.

### Manual walkthrough (mandatory)

Pre: `DATABASE_URL` set, migration 004 applied, network access to Nominatim.

1. `psql "$DATABASE_URL" -f backend/migrations/004_jobs_coords.sql`
2. Start backend + portal
3. Login as admin → land on `/console` (Map, not JobsList)
4. OSM tiles render; existing jobs with coords pin; stats strip shows counters
5. Side panel lists jobs grouped by status; filter chip is "active only"
6. Click `+ Create Job` → fill title="MapLibre smoke", location="Empire State Building, NYC" → submit
7. Card appears in side panel immediately; pin appears within 15s after geocode
8. Click pin → popup with status + title + location + crew + detail link
9. Click `[→ open detail]` → /console/jobs/:id
10. Back to `/console` → click `Crew` nav → roster shows assigned crew; admin's status "busy" if assigned to in-progress job
11. Toggle filter to `all` → cancelled/complete pins appear
12. Collapse panel → expand → reload → state persists
13. Login as Solo user → `/console` → "Upgrade Required" card

### Out of scope

- E2E (Playwright)
- Real Nominatim network tests
- Visual regression
- Many-pin perf benchmarks (clustering)
- Cross-browser matrix
- Self-hosted Nominatim setup

## Phasing recommendation

Suggested implementation task order:

1. Backend migration 004 + apply + smoke
2. Backend `geocoder.ts` + tests
3. Backend `AuditAction.JOB_GEOCODED` + `jobsService.create()` extension + `update()` extension + extend `jobs-routes.test.ts`
4. Backend `GET /api/profiles/crew` + tests
5. Frontend `maplibre-gl` install + console base CSS additions for marker classes
6. Frontend `crewClient` + tests
7. Frontend `crewStore` + tests
8. Frontend `useCrewRoster` + tests
9. Frontend `AvailabilityDot` + `CrewCard` + tests
10. Frontend `CrewRoute` + tests
11. Frontend `StatsStrip` + tests
12. Frontend `MapFilterChips` + tests
13. Frontend `MapSidePanel` + tests
14. Frontend `JobMarker` factory + `JobPopup` + tests
15. Frontend `MapCanvas` (the imperative MapLibre bridge) + tests
16. Frontend `MapRoute` (composition) + tests
17. Wire routes in `App.tsx` (Map as index, Crew as new route) + ConsoleShell nav update
18. Manual walkthrough

Phases 1-4 are backend. 5-16 are frontend bottom-up. 17-18 are integration + verification.

## Open questions for implementation

1. **Nominatim User-Agent string** — `SmithNet/1.0 (operator console)`. Adjust if a more specific identifier is preferred.
2. **`+ Create Job` button placement on MapRoute** — header strip area vs. above side panel. Implementer picks; both are visible.
3. **Mocked MapLibre behavior in tests** — `vi.mock` returns stub `Map`/`Marker`/`Popup` classes. Decision: yes — implementer follows the snippet above.
4. **Pin click vs. side-panel row click** — both should select the same job. Implementer wires via shared `selectedJobId` state in MapRoute.
5. **First-load map viewport** — fit bounds of jobs with coords; if zero jobs with coords, default to a continental view centered on 0,0 zoom 2. Better default heuristic could come later.
