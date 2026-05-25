# PWA + Per-Profile Offline Read-Cache (Slice 1) -- Design

> Slice 1 of the offline-first PWA program (SP2 of the portable-artifact line).
> Status: design approved 2026-05-24.

**Goal:** The portal becomes an installable PWA that loads fully offline (cached
app shell + the `smithcore.wasm` ROM), and the office data last synced -- jobs,
invoices, crew roster, tasks, and the comm channel list -- renders offline,
**read-only**, scoped per profile. No offline writes, no sync (Slices 2-3).

---

## 1. Context

The portal (Vite 5 + React 18 + TS + zustand + Vitest) populates zustand stores
from REST polling hooks (`useJobsPolling`, `useInvoicesPolling`, `useTasksPolling`,
`useCrewRoster`) and a WebSocket (`useCommWebSocket`). There is no client
persistence today -- reload with no network shows nothing.

This is the first slice of full offline-first. The later slices (an offline write
outbox; vector-clock reconciliation through the SP1 ROM, mirroring backend
`reconciliationEngine.ts` + Android `ReconciliationEngine`) are OUT of scope here.
Slice 1 delivers two things on its own: **installable** and **read your last-synced
data offline**.

Hard rules in force (do not break):
- **Per-profile isolation.** Cached data on disk is exactly where cross-profile
  leakage happens. Every cache entry is keyed by profile; no path reads another
  profile's cache (the Hetzner per-profile rule).
- **Determinism / ephemeral channels.** Ephemeral channels must never persist --
  this extends to the client cache, so comm message bodies (and ephemeral
  channels entirely) are excluded from Slice 1.
- No emoji anywhere; light/console aesthetic.

---

## 2. Scope

### In scope
- Installable PWA: web manifest + icons + a service worker that precaches the app
  shell and assets (including `smithcore.wasm`), so the app opens and renders with
  no network.
- A per-profile IndexedDB cache that the zustand stores hydrate from on console
  mount and write through to after each successful fetch.
- Cached collections: **jobs (list), invoices (list), crew roster, tasks**.
- Tests: the persistence layer + per-profile isolation (the must-pass).

### Out of scope
- Offline **writes** / optimistic mutations (Slice 2).
- Vector-clock **reconciliation / sync** (Slice 3).
- **Comm offline entirely** (channel list AND messages). The portal `Channel`
  type carries no ephemeral/persistence marker, and ephemeral channels can enter
  the store via the live WebSocket (`addChannel`), so caching comm cannot
  guarantee the determinism rule that ephemeral channels never persist. Deferred
  to a dedicated comm-offline slice (which adds a marker, coordinated with the
  backend, before any comm data is cached).
- Map crew **positions** (realtime; low value cached).
- **Push** notifications.
- The native wrapper (Capacitor/Tauri).
- No backend changes.

---

## 3. Architecture

Two independent mechanisms; neither is a hard dependency of the app (both degrade
to a normal online-only app on failure).

```
vite-plugin-pwa (Workbox)
  -> generates service worker + manifest, auto-registers
  -> precaches app shell + smithcore.wasm + icons
  => app opens & renders offline (no /api caching in the SW)

console/offline/db.ts            idb wrapper; one object store; key `${profileId}:${collection}`
console/offline/persistStore.ts  hydrate a zustand store from cache; subscribe -> debounced write-through
console/offline/useOfflinePersistence.ts
  -> binds the cacheable stores for the CURRENT profile at console mount;
     tears down on profile change; clears the previous profile's cache on logout
```

**Decision: data offline via app-level IndexedDB hydration, NOT service-worker API
caching.** SW caching of `/api` responses would double-cache, complicate
cache-invalidation, and make per-profile isolation hard. The SW handles only the
static app shell; data offline comes from IndexedDB hydration of the stores -- one
source of truth, clean per-profile keys.

**Decision: vite-plugin-pwa (Workbox)** over a hand-rolled service worker -- it is
the standard, generates the manifest + SW, auto-registers, and precaches the build
output (so `smithcore.wasm`, added in SP1, is covered by a glob).

---

## 4. Components / files

### Dependencies (`package.json`)
- `vite-plugin-pwa` (dev) -- SW + manifest generation.
- `idb` (runtime, ~1KB) -- promise wrapper over IndexedDB.
- `fake-indexeddb` (dev) -- IndexedDB shim for Vitest (jsdom has no IndexedDB).

### `vite.config.ts` (modify)
Add the `VitePWA` plugin:
- `registerType: 'autoUpdate'` (Slice 1 keeps update UX simple; a "new version,
  reload" prompt is a trivial later change).
- `workbox.globPatterns: ['**/*.{js,css,html,wasm,svg,png,woff2}']` (precache incl.
  the ROM). Set `navigateFallback: '/index.html'` with an `/api` denylist for SPA
  deep-links offline. Raise `maximumFileSizeToCacheInBytes` only if a build asset
  exceeds the 2 MiB default (the ROM is 7 KB; current main JS is < 2 MiB).
- `manifest`: `name: 'Smith Net'`, `short_name: 'Smith Net'`, `display: 'standalone'`,
  `theme_color` / `background_color` from the console/parchment palette
  (`tailwind.config.js` tokens), and the three icons below.

### PWA assets (`public/`)
- `icon-192.png`, `icon-512.png`, `icon-maskable-512.png` -- a simple "smith net"
  monogram in the console palette (no emoji). Generated as part of the plan.

### `src/console/offline/db.ts` (create)
`idb`-backed cache. DB `smithnet-offline`, one object store `cache` keyed by the
string `${profileId}:${collection}`. Stored value:
`{ schemaVersion: number; savedAt: number; data: unknown }`.
- `loadCache<T>(profileId, collection): Promise<T | null>` -- returns `data` only
  when `schemaVersion === CURRENT_SCHEMA_VERSION`; otherwise deletes the stale key
  and returns `null`.
- `saveCache(profileId, collection, data): Promise<void>`.
- `clearProfile(profileId): Promise<void>` -- delete every key prefixed
  `${profileId}:`.
- `CURRENT_SCHEMA_VERSION` constant (bump to invalidate all cached blobs on a shape
  change).

### `src/console/offline/persistStore.ts` (create)
```
persistStore<S, P>({
  store,          // zustand store (getState + subscribe)
  profileId, collection,
  pick: (s: S) => P,                 // the slice to persist
  hydrate: (api: S, data: P) => void,// apply cached data via the store's own actions
  shouldPersist?: (s: S) => boolean, // gate: only write real fetched data
  debounceMs?: number,               // default 500
}): Promise<() => void>              // returns an unsubscribe
```
- On init: `loadCache` -> if non-null, `hydrate(store.getState(), data)`. Hydration
  applies cached data via existing store actions and marks the store **stale**
  (e.g. `markStale(true)`), so the UI shows cached-then-fresh once polling refreshes.
- Subscribe: on change, if `shouldPersist(state)` is true, debounce-write
  `pick(state)`. `shouldPersist` defaults to always; callers gate on real data
  (e.g. `state.lastFetchedAt != null`) so the empty initial state and the
  post-logout `clear()` never overwrite a good cache.

### `src/console/offline/useOfflinePersistence.ts` (create)
A hook used in `ConsoleShell`. For the current `user.id`, calls `persistStore` for
each cacheable store with the right `pick`/`hydrate`/`shouldPersist`, collecting the
unsubscribes. On `user.id` change it tears down and rebinds; when `user` becomes
null (logout) it tears down and `clearProfile(previousId)`.

Cacheable bindings (Slice 1) -- gates chosen per each store's actual shape so the
empty initial state and the post-logout `clear()` never overwrite a good cache:
- jobs (`jobsStore`): persist `{ jobs }`; hydrate via `setJobs` + `markStale(true)`;
  gate `lastFetchedAt != null`.
- crew roster (`crewStore`): persist `{ roster }`; hydrate via `setRoster` +
  `markStale(true)`; gate `lastFetchedAt != null`.
- invoices (`invoicesStore`): persist `{ invoices }`; hydrate via `setInvoices` +
  `markStale(true)`; gate `invoices.length > 0` (this store has no `lastFetchedAt`).
- tasks (`tasksStore`): persist `{ tasksByJob }` (a `Record<jobId, Task[]>`);
  hydrate by iterating `setTasks(jobId, list)` + `markStale(jobId, true)`; gate
  `Object.keys(tasksByJob).length > 0`.

Comm is intentionally NOT cached in Slice 1 (see Out of scope).

### `src/console/ConsoleShell.tsx` (modify)
Call `useOfflinePersistence()` (alongside the existing `initSmithCore()` effect).

### Tests (`src/console/offline/__tests__/`)
`db.test.ts`, `persistStore.test.ts` -- using `fake-indexeddb` (`import
'fake-indexeddb/auto'`).

---

## 5. Data flow

On console mount, each cacheable store is **hydrated from IndexedDB first** (instant
offline render of last-synced data, marked stale), then the existing polling hook
fetches fresh data and calls its normal setter; the subscription **writes the fresh
slice back** (debounced). Offline, the fetch fails, the store keeps the hydrated
values, and the existing `isStale` / offline UI states apply. The service worker
independently serves the app shell + ROM from precache so the page loads at all
with no network.

---

## 6. Per-profile isolation (critical)

Every cache key is `${profileId}:${collection}` (`profileId = user.id`). Hydration
reads only the current profile's keys. On logout, `clearProfile(previousId)` wipes
that profile's entries; on profile switch, the new profile hydrates only its own
keys. **No code path reads another profile's cache.** This is the property the
isolation test exists to prove.

---

## 7. Error handling

- IndexedDB unavailable / quota exceeded -> log once; run as a normal online-only
  app (the cache is an enhancement, never required).
- Corrupt or schema-mismatched blob -> discard that key (return null), refetch.
- Service worker registration failure -> app still runs online; no crash.

---

## 8. Testing / acceptance criteria

- **db round-trip:** `saveCache` then `loadCache` returns the same data;
  `schemaVersion` mismatch returns `null` and deletes the stale key.
- **persistStore:** hydrate is called with cached data on init and applies it via
  the store's actions; a state change triggers a debounced `saveCache` of
  `pick(state)`; `shouldPersist=false` (empty/initial) does NOT write.
- **Per-profile isolation (must pass):** profile A saves jobs; `loadCache(B, ...)`
  returns null; `clearProfile(A)` removes only A's keys; after A logout A's cache is
  gone, B's intact.
- **Build / type gates:** `vite build` emits `sw.js` + `manifest.webmanifest`, and
  the generated precache manifest includes `smithcore.wasm`; `npx tsc --noEmit` is
  clean; the full `npm run test:run` suite stays green.
- **Manual deferred-verify:** install the PWA, go offline, reload -> the app opens
  and shows last-synced jobs/invoices/crew/tasks; Lighthouse reports "installable".

---

## 9. Open questions

None. Offline mechanism (IndexedDB hydration, not SW API caching), PWA tooling
(vite-plugin-pwa), cached set (jobs/invoices/crew/tasks; comm deferred entirely
for the ephemeral-marker reason above), and isolation keying are all decided above.
