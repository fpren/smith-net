# Smith Net Operator Console — Design

**Date:** 2026-05-10
**Scope:** Foreman-tier desktop operator console MVP — multi-job dispatch surface
**Target:** `/desktop/portal/` (extend existing React + Vite + TS app)
**Backend:** Canonical Hetzner Express (`/backend/`)

## Summary

Extend the existing `desktop/portal/` React app with a `/console/*` route tree that gives Advanced-tier Foremen a desktop-class multi-job dispatch surface. Live updates via WebSocket. Replaces nothing — runs alongside the legacy `/portal` and `/dashboard` routes during transition. Switches console-only auth from Supabase (legacy) to Hetzner JWT (canonical) using httpOnly cookies.

## Decisions

| Decision | Choice | Why |
|----------|--------|-----|
| Persona at launch | Foreman only | Smallest MVP; Enterprise admin views deferred to a follow-up phase |
| Tier gate | Advanced+ | Per the tier-gating skill; Solo does not get the console |
| Killer use case | Multi-job dispatch + scheduling | Drives the MVP feature set |
| MVP screens | Job Board, Map, Crew, light Client lookup, light Chat to crew | Bounded around dispatch |
| Real-time | Live push via WebSocket | Reuses existing `websocket.ts` scaffold; dispatch UX is broken without it |
| Architecture | Extend existing `desktop/portal/` (Option A) | Lowest cost; forces overdue Supabase removal; single deploy |
| URL | `console.smithnet.app` subdomain | Same Vite build, just routed first to console |
| Auth | Hetzner JWT, httpOnly cookies | Canonical auth path; cookies immune to XSS token theft |
| Auth coexistence | New console = Hetzner; legacy `/portal` = Supabase, untouched | No big-bang; Supabase deleted in a follow-up PR |
| State management | Zustand (already installed), one store per domain | No god-store; clear boundaries |
| Map library | MapLibre GL (default) | Free, no API key; will align with Android `MapScreen.kt` if Android uses something specific |
| Drag library | `@dnd-kit/core` | Modern, accessible, React-native |
| Frontend test stack | Vitest + @testing-library/react + MSW | Vite-native, jest-compatible API |
| Observability | Console logs only for MVP | Real telemetry deferred — explicit non-goal, not oversight |
| Migration assumption | No real Supabase users to migrate | Active user base already on Hetzner JWT via Android |

## Architecture

### URL & deploy
- `console.smithnet.app` subdomain. Same Vite static bundle as the marketing/legacy site; subdomain just lands on a different default route.
- Single `npm run build` in `desktop/portal/`. Code-split per top-level console route.
- No new build pipeline.

### Routing
Add to existing React Router tree. Legacy routes stay alive.

```
/                       → marketing / Auth (legacy)
/portal                 → legacy chat portal (Supabase, untouched)
/dashboard              → legacy dashboard (Supabase, untouched)

/console/login          → new login (Hetzner JWT)
/console/register       → new register
/console                → ConsoleShell, lands on JobBoardRoute
/console/jobs/:id       → JobDetailRoute
/console/map            → MapRoute
/console/crew           → CrewRoute
/console/clients        → ClientLookupRoute
/console/clients/:id    → ClientDetailRoute (light)
/console/chat           → CrewChatRoute (thread list)
/console/chat/:threadId → CrewChatRoute (thread view)
```

### Backend
All console traffic hits canonical Hetzner Express. Zero Supabase calls from console code.

### Brand
Light-mode only. Monospace font. Console aesthetic per smith-net-design-system. ASCII glyphs as icons (no Material icons). Tailwind utilities OK with a strict palette tied to `consoleTheme` tokens. **Caveat:** `desktop/portal/package.json` only includes `tailwind-merge`, not Tailwind itself — Tailwind setup must be verified or added as part of implementation.

### Tier gate
Server-side check on every console API call. Caller tier `< Advanced` returns structured 403 per the tier-gating skill. Console UI shows an upgrade prompt on 403; no client-side route hiding.

## Components

```
desktop/portal/src/console/
|-- ConsoleShell.tsx          // left nav + header (user + tier badge) + main pane
|-- routes/
|   |-- JobBoardRoute.tsx     // landing — multi-job dispatch view
|   |-- JobDetailRoute.tsx
|   |-- MapRoute.tsx
|   |-- CrewRoute.tsx
|   |-- ClientLookupRoute.tsx
|   |-- ClientDetailRoute.tsx
|   `-- CrewChatRoute.tsx
|-- components/
|   |-- JobBoard/             // board, columns by status, JobCard, drag-to-assign
|   |-- Map/                  // canvas, JobPin, CrewPin, popups
|   |-- Crew/                 // CrewCard, availability indicator
|   |-- Chat/                 // ThreadList, MessageList, Composer
|   `-- ui/                   // Console-style primitives: Button, Card, Input, Badge, Modal
|-- hooks/                    // useJobs, useCrew, useChat, useClient, useTierGate
|-- api/                      // typed fetch wrappers — JWT cookie auto-attached
|-- ws/                       // WS dispatcher — routes events into stores
|-- stores/                   // zustand: jobsStore, crewStore, chatStore, clientStore, authStore
|-- auth/                     // LoginForm, RegisterForm, authClient, authStore
`-- theme/                    // consoleTheme tokens mirroring Android ConsoleTheme
```

### Boundaries
- `api/` only knows HTTP.
- `ws/` only knows the socket lifecycle + event routing.
- `stores/` are the single source of truth. Both `api/` (after fetch) and `ws/` (on event) write into them.
- `hooks/` read from stores and expose intent-named selectors (`useJobsByStatus()`, `useActiveCrew()`).
- Components never call `api/` directly.

### State
Zustand. One store per domain. No global god-store.

## Data flow

### Initial load
```
Route mounts
  -> hook called
  -> hook calls api/ wrapper
  -> fetch(Hetzner) with JWT cookie auto-attached
  -> api/ writes result into store
  -> hook re-selects from store
  -> component renders
```

### Real-time update
```
Hetzner emits event
  -> WS frame arrives
  -> ws/ dispatcher parses (zod schema check)
  -> dispatcher calls store.applyEvent(event)
  -> React re-renders subscribed components
```

Dispatcher is one switch on `event.type`. Each store exposes one `applyEvent` per event type it cares about. No business logic in the dispatcher.

### Mutation (e.g., assign job)
```
User drags
  -> component calls hook.assignJob(jobId, crewId)
  -> hook does optimistic store update
  -> hook calls api/jobs.assign
  -> on 2xx: server WS broadcasts JobUpdated, store reconciles
  -> on error: rollback optimistic update + toast with retry
```

Optimistic by default for dispatch actions. Non-optimistic for destructive ops (delete client, end job).

### Backend endpoints (audit-first)
MVP needs the following. **Some likely already exist inside `backend/src/api.ts`; gaps will be filled as part of implementation, not snuck in.**

| Method | Endpoint | Purpose |
|--------|----------|---------|
| GET | `/api/jobs?foreman=me` | Job board listing |
| GET | `/api/jobs/:id` | Job detail |
| POST | `/api/jobs/:id/assign` | Assign job to crew member |
| PATCH | `/api/jobs/:id/status` | Update job status |
| GET | `/api/crew?foreman=me` | Crew listing |
| GET | `/api/crew/:id` | Crew member detail |
| GET | `/api/clients?q=` | Client search |
| GET | `/api/clients/:id` | Client detail |
| GET | `/api/chat/threads` | Thread list |
| GET | `/api/chat/threads/:id/messages` | Thread messages |
| POST | `/api/chat/threads/:id/messages` | Send message |

### WS channels
Subscribe-on-connect:
- `foreman:{userId}:jobs` — `JobUpdated`
- `foreman:{userId}:crew` — `CrewLocationPing`, `CrewAvailabilityChanged`
- `foreman:{userId}:chat` — `ChatMessageReceived`

Server filters events by foreman scope before broadcasting. Never trust client filters.

## Auth migration

### Coexistence model
- `/portal` and `/dashboard` (legacy) keep Supabase auth, untouched
- `/console/*` (new) uses Hetzner JWT, custom login form
- Independent — no shared session, no fights
- When `/portal` is retired (separate PR), all Supabase code (`supabaseClient.ts`, `supabaseAuth.ts`, `Auth.tsx`, `AuthCallback.tsx`, npm deps) gets deleted in one commit

### New console auth module
Lives under `console/auth/`:
- `LoginForm.tsx` — email + password, POSTs `/api/auth/login`
- `RegisterForm.tsx` — POSTs `/api/auth/register` (already wired to email verification per F1.4)
- `authStore.ts` — holds `{ user, tier }` (NOT raw tokens — those live in httpOnly cookies)
- `authClient.ts` — `login()`, `register()`, `refresh()`, `logout()`, `me()`

### Token storage: httpOnly cookies
Set by the backend on login/register/refresh responses.

- `Set-Cookie: smithnet_access=...; HttpOnly; Secure; SameSite=Strict; Path=/api; Max-Age=604800`
- `Set-Cookie: smithnet_refresh=...; HttpOnly; Secure; SameSite=Strict; Path=/api/auth/refresh; Max-Age=2592000`

Reasons:
1. Immune to XSS token theft (real risk for a paid operator console handling client/financial data)
2. Backend already controls the auth response shape
3. CSRF mitigated by `SameSite=Strict` + standard double-submit pattern if cross-site ever happens

**Backend change required.** `authRoutes.ts` must set cookies on login/register/refresh. The Android app's existing JWT-in-body flow stays unchanged; cookies are additive for browser callers (User-Agent or `X-Client: console` header to disambiguate).

### Refresh flow
Access token 7d, refresh 30d (per security skill). On any 401 from a console API call, the `api/` wrapper transparently calls `/api/auth/refresh`, retries the original request once, and only bubbles the error if refresh also fails. On refresh failure: clear authStore, redirect to `/console/login`.

### Tier check on session
`me()` returns `{ user, tier }`. `ConsoleShell` mounts and checks tier. If `< Advanced`, render an upgrade screen instead of the console. Server still enforces; client check is UX, not security.

## Error handling

| Class | Where | Strategy |
|---|---|---|
| Auth expired (401) | Any `api/` call | Transparent refresh + retry once. If refresh fails, clear store, redirect to `/console/login` with banner. |
| Tier denied (403) | Any `api/` call | Hook returns 403 to component. Component renders inline upgrade prompt, not toast. |
| Network / 5xx | `api/` call | Wrapper returns `{ ok: false, error }`. Stores hold last-good data + `isStale` flag. UI shows non-blocking inline strip "couldn't refresh — showing cached data." Retry on user action. |
| WS drop | `ws/` dispatcher | Exponential backoff reconnect (1s, 2s, 4s, 8s, capped at 30s). Header shows `[OFFLINE]` glyph. On reconnect, per-store `refetch()`. |
| Mutation failure (optimistic) | Component -> hook -> api/ | Rollback optimistic update. Toast: "Couldn't assign — retry?" with one-click retry. |

### WS message parse failure
If a frame doesn't validate against the expected zod schema (matching backend `schemas/`), drop the frame, log to console, do **not** crash the dispatcher. Store stays consistent because nothing was applied.

### Fatal errors
Three things qualify:
1. WS reconnect fails 5 times in a row
2. JWT refresh returns a structurally bad response
3. `me()` 5xx for >30s on app boot

UI shows a full-screen `[ERROR]` panel with "reload" + "log out." Never a blank screen.

### Observability (MVP)
Console logs only. All error paths log with stable prefix (`[console:auth]`, `[console:ws]`, `[console:api]`) so they're greppable. Real telemetry (Sentry, etc.) deferred — explicit non-goal.

## Testing

### Frontend stack to add (new — none exists today)
- **Vitest** for unit + integration
- **@testing-library/react** for component tests
- **MSW** for mocking Hetzner API + WS responses

### What gets tested where

| Layer | Tool | Coverage |
|---|---|---|
| Stores | Vitest | `applyEvent` for every WS event type. Optimistic update + rollback. Stale-data flag transitions. |
| API wrappers | Vitest + MSW | JWT cookie attached. 401 -> refresh-and-retry. 403 -> structured tier error. 5xx -> `{ ok: false }`. |
| WS dispatcher | Vitest + MSW WebSocket mock | Bad frame dropped without crash. Backoff schedule fires. Reconnect triggers refetch. |
| Hooks | Vitest + RTL `renderHook` | Selector returns expected shape. Re-renders on store change. |
| Components | Vitest + RTL | Smoke render per route. Critical interactions only — drag-to-assign, send-message, search-and-pick. **No** snapshot tests. |
| Backend new endpoints | Existing jest-style suite in `backend/src/__tests__/` | One integration test per new endpoint matching `api-auth-integration.test.ts` pattern. Tier-403 + happy-path + auth-required cases. |

### E2E
None for MVP. Manual browser walkthrough mandatory before merge:
1. Login -> load Job Board
2. Drag a job onto a crew member -> confirm WS push reflects in a second tab
3. Open `/console/map` -> confirm crew pin updates
4. Send a chat message to crew -> confirm round-trip
5. Tier-gated user sees upgrade prompt
6. Expired JWT triggers transparent refresh

### Out of scope (explicit non-goals)
- Visual regression (Percy / Chromatic)
- Load / WS-fanout testing (single-foreman MVP, not multi-tenant scale)
- Cross-browser matrix (Chrome only; flag known issues if others come up)
- E2E automation

## Phasing recommendation

Single spec, but implementation should be phased into reviewable chunks. Suggested order for the implementation plan:

1. **Foundation** — Tailwind setup verification, `consoleTheme`, `ui/` primitives, `ConsoleShell`, test stack (Vitest + RTL + MSW). No routes yet, just the chassis.
2. **Auth migration** — Backend cookie auth changes + console `auth/` module + `LoginForm` / `RegisterForm` + `me()` + tier gate. Lands `/console/login`, `/console/register`, and a placeholder `/console` that just shows "you are logged in as X (tier Y)."
3. **Backend endpoint audit + gap-fill** — Inventory `api.ts`, file PRs for missing job/crew/client/chat endpoints with tests.
4. **WS infrastructure** — `ws/` dispatcher + reconnect logic + first store (`jobsStore`) consuming `JobUpdated`. Proves the pipeline end-to-end on one domain.
5. **Job Board route** — JobBoardRoute + JobDetailRoute + drag-to-assign + JobPin on a placeholder map. The killer use case lands.
6. **Map + Crew routes** — MapRoute (MapLibre integration) + CrewRoute. Dispatch surface complete.
7. **Client lookup + Chat** — ClientLookupRoute, ClientDetailRoute, CrewChatRoute. Supporting screens.
8. **Subdomain + deploy** — `console.smithnet.app` DNS, host config, CORS allowlist update.
9. **(Follow-up PR)** Retire `/portal`, delete Supabase code.

Phases 1-2 are unblocking foundations. Phase 3 is the only one with backend uncertainty. Phases 4-7 are largely independent within themselves but sequential against the foundation.

## Open questions for implementation

These don't block the spec but need resolution during planning:

1. **Tailwind**: present or not in `desktop/portal/`? (Only `tailwind-merge` in package.json today.)
2. **`api.ts` inventory**: which of the MVP endpoints exist; what's the tier-gating pattern there?
3. **Map library on Android**: does `android/.../map/MapScreen.kt` use Mapbox/Google? If yes, web should align.
4. **WS auth handshake**: does `wsHandler.ts` accept token via query param, `Sec-WebSocket-Protocol`, or cookie? Determines console connect code.
5. **CORS**: is `console.smithnet.app` already in the allowlist (recently added per F1.2)? Will need to add if not.
6. **Static hosting**: where does `desktop/portal/` deploy today? Same place serves the subdomain or new host?

## Non-goals (out of scope for this spec)

- Enterprise admin / multi-foreman roll-up views
- Solo-tier web access
- Marketing-facing public pages
- PWA / installable / offline support
- Mobile-web responsive layout (desktop-first; mobile users use the Android app)
- SSR / Next.js
- Visual regression / cross-browser test matrix
- Removing Supabase code (separate follow-up PR)
- Migrating existing Supabase users to Hetzner (assumed not needed; flag if wrong)
