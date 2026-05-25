# PWA + Per-Profile Offline Read-Cache (Slice 1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the portal an installable PWA that loads offline (cached app shell + the `smithcore.wasm` ROM) and renders the last-synced jobs / invoices / crew / tasks offline, read-only, scoped per profile.

**Architecture:** Two independent mechanisms. (1) `vite-plugin-pwa` (Workbox) generates a service worker that precaches the build output (incl. the wasm) + a web manifest -> the app opens with no network. (2) A per-profile IndexedDB cache (`idb`) that the zustand stores hydrate from on console mount and write through to after each fetch. The SW does NOT cache `/api`; data offline comes only from IndexedDB hydration. Neither mechanism is a hard dependency -- both degrade to a normal online app on failure.

**Tech Stack:** Vite 5 + React 18 + TS (strict) + zustand + Vitest (jsdom). New deps: `vite-plugin-pwa`, `idb`, `fake-indexeddb` (dev). Icons generated one-off via `npx @vite-pwa/assets-generator`.

**Spec:** `docs/superpowers/specs/2026-05-24-pwa-offline-slice1-design.md`

---

## File structure (locked before tasks)

| File | Responsibility |
|---|---|
| `desktop/portal/package.json` (+lock) (modify) | Add `vite-plugin-pwa` (dev), `idb` (prod), `fake-indexeddb` (dev). |
| `desktop/portal/public/smithnet-icon.svg` (create) | Source monogram (console palette) for icon generation. |
| `desktop/portal/public/pwa-192x192.png`, `pwa-512x512.png`, `maskable-icon-512x512.png`, `apple-touch-icon-180x180.png`, `favicon.ico` (create) | Generated PWA icons (committed). |
| `desktop/portal/vite.config.ts` (modify) | Add the `VitePWA` plugin (manifest + workbox precache incl. wasm). |
| `desktop/portal/src/console/offline/db.ts` (create) | `idb` cache: `loadCache` / `saveCache` / `clearProfile`, per-profile keys, schema-versioned. |
| `desktop/portal/src/console/offline/persistStore.ts` (create) | Hydrate a zustand store from cache + debounced write-through. |
| `desktop/portal/src/console/offline/useOfflinePersistence.ts` (create) | Bind jobs/invoices/crew/tasks for the current profile; clear prior profile on logout. |
| `desktop/portal/src/console/ConsoleShell.tsx` (modify) | Call `useOfflinePersistence()`. |
| `desktop/portal/src/console/offline/__tests__/db.test.ts`, `persistStore.test.ts` (create) | Unit tests via `fake-indexeddb`. |

**Conventions:** commands run from `desktop/portal/`. Stage ONLY the files each task names -- never `git add -A`/`.`. Every commit ends with the trailer `Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>`. No emoji anywhere. `smithCore.ts`/offline code is browser code (no Node APIs); Node APIs (`fake-indexeddb`) only in `.test.ts`.

---

### Task 1: PWA shell -- manifest, service worker, icons

This task is verified by build output, not a unit test (a service worker is not meaningfully unit-testable). It also installs the deps the later tasks need.

**Files:**
- Modify: `desktop/portal/package.json` (+ `package-lock.json`)
- Create: `desktop/portal/public/smithnet-icon.svg` + 5 generated icon files
- Modify: `desktop/portal/vite.config.ts`

- [ ] **Step 1: Install dependencies**

```bash
cd desktop/portal
npm install idb
npm install -D vite-plugin-pwa fake-indexeddb
```
If `vite-plugin-pwa` reports a peer-dep conflict with Vite 5, install the latest `0.21.x` (`npm install -D vite-plugin-pwa@^0.21.0`). Expected: installs cleanly, `package.json` gains the three deps.

- [ ] **Step 2: Create the source icon**

Create `desktop/portal/public/smithnet-icon.svg`:

```svg
<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 512 512">
  <rect width="512" height="512" fill="#2A2520"/>
  <text x="256" y="272" font-family="ui-monospace, SFMono-Regular, Menlo, monospace"
        font-size="220" font-weight="700" fill="#9A6F2E"
        text-anchor="middle" dominant-baseline="central">SN</text>
</svg>
```

- [ ] **Step 3: Generate the icon set**

```bash
cd desktop/portal
npx @vite-pwa/assets-generator@latest --preset minimal-2023 public/smithnet-icon.svg
ls public/*.png public/*.ico
```
Expected outputs in `public/`: `pwa-64x64.png`, `pwa-192x192.png`, `pwa-512x512.png`, `maskable-icon-512x512.png`, `apple-touch-icon-180x180.png`, `favicon.ico`. If the generated filenames differ from these, note the actual names and use them in the manifest in Step 4.

- [ ] **Step 4: Add the VitePWA plugin**

Replace `desktop/portal/vite.config.ts` with (preserves the existing resolve/server/test blocks, adds the plugin):

```ts
/// <reference types="vitest" />
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import { VitePWA } from 'vite-plugin-pwa'
import path from 'path'

export default defineConfig({
  plugins: [
    react(),
    VitePWA({
      registerType: 'autoUpdate',
      manifest: {
        name: 'Smith Net',
        short_name: 'Smith Net',
        description: 'Smith Net console -- jobs, invoices, crew, and comm.',
        display: 'standalone',
        start_url: '/',
        scope: '/',
        background_color: '#F4F2EE',
        theme_color: '#9A6F2E',
        icons: [
          { src: 'pwa-192x192.png', sizes: '192x192', type: 'image/png' },
          { src: 'pwa-512x512.png', sizes: '512x512', type: 'image/png' },
          { src: 'maskable-icon-512x512.png', sizes: '512x512', type: 'image/png', purpose: 'maskable' },
        ],
      },
      workbox: {
        globPatterns: ['**/*.{js,css,html,wasm,svg,png,ico,woff2}'],
        navigateFallback: '/index.html',
        navigateFallbackDenylist: [/^\/api/],
      },
      devOptions: { enabled: false },
    }),
  ],
  resolve: {
    alias: {
      '@console': path.resolve(__dirname, './src/console'),
    },
  },
  server: {
    port: 5173,
    proxy: {
      // ws:true so the WS upgrade at /api/ws (used by wsClient) is forwarded
      // to the backend. Same-origin from the browser's view keeps the
      // smithnet_access cookie attached on the upgrade request -- the cookie
      // is scoped to Path=/api, so connecting via /api/ws is required.
      '/api': {
        target: 'http://localhost:3030',
        ws: true,
        changeOrigin: true,
      },
    },
  },
  test: {
    globals: true,
    environment: 'jsdom',
    setupFiles: './src/console/test/setup.ts',
    include: ['src/**/*.test.{ts,tsx}'],
  },
})
```

- [ ] **Step 5: Build and verify the SW + manifest + wasm precache**

```bash
cd desktop/portal
npm run build
ls dist/manifest.webmanifest dist/sw.js
grep -o "smithcore[^\"]*\.wasm" dist/sw.js dist/workbox-*.js 2>/dev/null | head
npx tsc --noEmit
npm run test:run
```
Expected: `dist/manifest.webmanifest` and `dist/sw.js` exist; the `grep` finds a `smithcore...wasm` entry in the precache (sw.js or a workbox-*.js chunk); `tsc` 0 errors; full test suite still green (no test changes this task).

- [ ] **Step 6: Commit**

```bash
git add desktop/portal/package.json desktop/portal/package-lock.json desktop/portal/vite.config.ts desktop/portal/public/smithnet-icon.svg desktop/portal/public/pwa-192x192.png desktop/portal/public/pwa-512x512.png desktop/portal/public/maskable-icon-512x512.png desktop/portal/public/apple-touch-icon-180x180.png desktop/portal/public/favicon.ico
git commit -m "$(cat <<'EOF'
feat(portal): installable PWA shell -- manifest + service worker (SP2 slice 1)

vite-plugin-pwa precaches the app shell + smithcore.wasm so the portal
loads offline. No /api caching in the SW; offline data comes from the
IndexedDB layer in the following tasks.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```
(If Step 3 generated a `pwa-64x64.png` too, add it to the `git add` line.)

---

### Task 2: Per-profile IndexedDB cache (`db.ts`)

**Files:**
- Create: `desktop/portal/src/console/offline/db.ts`
- Test: `desktop/portal/src/console/offline/__tests__/db.test.ts`

- [ ] **Step 1: Write the failing test**

Create `desktop/portal/src/console/offline/__tests__/db.test.ts`:

```ts
import 'fake-indexeddb/auto';
import { describe, it, expect, beforeEach } from 'vitest';
import { openDB } from 'idb';
import { loadCache, saveCache, clearProfile, CURRENT_SCHEMA_VERSION } from '../db';

beforeEach(async () => {
  // Fresh DB per test: delete and let the next call recreate it.
  await clearProfile('A');
  await clearProfile('B');
});

describe('offline db', () => {
  it('round-trips a saved collection', async () => {
    await saveCache('A', 'jobs', { jobs: ['j1', 'j2'] });
    expect(await loadCache('A', 'jobs')).toEqual({ jobs: ['j1', 'j2'] });
  });

  it('returns null for a missing collection', async () => {
    expect(await loadCache('A', 'nope')).toBeNull();
  });

  it('is isolated per profile', async () => {
    await saveCache('A', 'jobs', { jobs: ['secret'] });
    expect(await loadCache('B', 'jobs')).toBeNull();
  });

  it('clearProfile removes only that profile', async () => {
    await saveCache('A', 'jobs', { jobs: ['a'] });
    await saveCache('B', 'jobs', { jobs: ['b'] });
    await clearProfile('A');
    expect(await loadCache('A', 'jobs')).toBeNull();
    expect(await loadCache('B', 'jobs')).toEqual({ jobs: ['b'] });
  });

  it('discards a schema-mismatched blob and deletes the stale key', async () => {
    // Write an envelope with a wrong schemaVersion directly.
    const d = await openDB('smithnet-offline', 1, {
      upgrade(db) {
        if (!db.objectStoreNames.contains('cache')) db.createObjectStore('cache');
      },
    });
    await d.put(
      'cache',
      { schemaVersion: CURRENT_SCHEMA_VERSION + 999, savedAt: 0, data: { jobs: ['stale'] } },
      'A:jobs',
    );
    expect(await loadCache('A', 'jobs')).toBeNull();
    // stale key was deleted
    expect(await d.get('cache', 'A:jobs')).toBeUndefined();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm run test:run -- offline/db`
Expected: FAIL -- cannot resolve `../db`.

- [ ] **Step 3: Implement `db.ts`**

Create `desktop/portal/src/console/offline/db.ts`:

```ts
// desktop/portal/src/console/offline/db.ts
//
// Per-profile IndexedDB cache for offline read. One object store keyed by
// `${profileId}:${collection}`. Values are schema-version-stamped so a stored-
// shape change invalidates old blobs. The cache is an enhancement, never a hard
// dependency: every call swallows errors and degrades to "no cache" (e.g. when
// IndexedDB is unavailable or quota is exceeded).
import { openDB, type IDBPDatabase } from 'idb';

const DB_NAME = 'smithnet-offline';
const DB_VERSION = 1;
const STORE = 'cache';

/** Bump to invalidate every cached blob after a stored-shape change. */
export const CURRENT_SCHEMA_VERSION = 1;

interface CacheEnvelope<T> {
  schemaVersion: number;
  savedAt: number;
  data: T;
}

let _dbPromise: Promise<IDBPDatabase> | null = null;

function db(): Promise<IDBPDatabase> {
  if (!_dbPromise) {
    _dbPromise = openDB(DB_NAME, DB_VERSION, {
      upgrade(d) {
        if (!d.objectStoreNames.contains(STORE)) d.createObjectStore(STORE);
      },
    });
  }
  return _dbPromise;
}

const keyOf = (profileId: string, collection: string) => `${profileId}:${collection}`;

/** Load a profile's cached collection, or null if absent / stale / unavailable.
 *  A schemaVersion mismatch deletes the stale key. */
export async function loadCache<T>(profileId: string, collection: string): Promise<T | null> {
  try {
    const d = await db();
    const env = (await d.get(STORE, keyOf(profileId, collection))) as CacheEnvelope<T> | undefined;
    if (!env) return null;
    if (env.schemaVersion !== CURRENT_SCHEMA_VERSION) {
      await d.delete(STORE, keyOf(profileId, collection));
      return null;
    }
    return env.data;
  } catch {
    return null;
  }
}

/** Persist a profile's collection. Best-effort: swallows errors. */
export async function saveCache<T>(profileId: string, collection: string, data: T): Promise<void> {
  try {
    const env: CacheEnvelope<T> = { schemaVersion: CURRENT_SCHEMA_VERSION, savedAt: Date.now(), data };
    await (await db()).put(STORE, env, keyOf(profileId, collection));
  } catch {
    // best-effort; ignore
  }
}

/** Delete every cached entry for one profile (logout / profile-switch hygiene). */
export async function clearProfile(profileId: string): Promise<void> {
  try {
    const d = await db();
    const prefix = `${profileId}:`;
    const keys = (await d.getAllKeys(STORE)) as string[];
    await Promise.all(keys.filter((k) => k.startsWith(prefix)).map((k) => d.delete(STORE, k)));
  } catch {
    // best-effort; ignore
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm run test:run -- offline/db`
Expected: PASS (5 tests). Then `npx tsc --noEmit` -> 0 errors.

- [ ] **Step 5: Commit**

```bash
git add desktop/portal/src/console/offline/db.ts desktop/portal/src/console/offline/__tests__/db.test.ts
git commit -m "$(cat <<'EOF'
feat(portal): per-profile IndexedDB cache for offline read (SP2 slice 1)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: Store persistence binding (`persistStore.ts`)

**Files:**
- Create: `desktop/portal/src/console/offline/persistStore.ts`
- Test: `desktop/portal/src/console/offline/__tests__/persistStore.test.ts`

- [ ] **Step 1: Write the failing test**

Create `desktop/portal/src/console/offline/__tests__/persistStore.test.ts`:

```ts
import 'fake-indexeddb/auto';
import { describe, it, expect, beforeEach } from 'vitest';
import { create } from 'zustand';
import { persistStore } from '../persistStore';
import { saveCache, clearProfile, loadCache } from '../db';

interface S {
  items: string[];
  fetched: boolean;
  setItems: (x: string[]) => void;      // marks fetched (a real fetch)
  setItemsRaw: (x: string[]) => void;   // does NOT mark fetched
}
const makeStore = () =>
  create<S>((set) => ({
    items: [],
    fetched: false,
    setItems: (items) => set({ items, fetched: true }),
    setItemsRaw: (items) => set({ items }),
  }));

const delay = (ms: number) => new Promise((r) => setTimeout(r, ms));

beforeEach(async () => {
  await clearProfile('p');
});

describe('persistStore', () => {
  it('hydrates the store from cache on init', async () => {
    await saveCache('p', 'things', { items: ['a', 'b'] });
    const store = makeStore();
    await persistStore<S, { items: string[] }>({
      store,
      profileId: 'p',
      collection: 'things',
      pick: (s) => ({ items: s.items }),
      hydrate: (api, d) => api.setItems(d.items),
    });
    expect(store.getState().items).toEqual(['a', 'b']);
  });

  it('writes through (debounced) on change when shouldPersist passes', async () => {
    const store = makeStore();
    await persistStore<S, { items: string[] }>({
      store,
      profileId: 'p',
      collection: 'w',
      pick: (s) => ({ items: s.items }),
      hydrate: (api, d) => api.setItems(d.items),
      shouldPersist: (s) => s.fetched,
      debounceMs: 5,
    });
    store.getState().setItems(['x']);
    await delay(30);
    expect(await loadCache('p', 'w')).toEqual({ items: ['x'] });
  });

  it('does not persist when shouldPersist fails', async () => {
    const store = makeStore();
    await persistStore<S, { items: string[] }>({
      store,
      profileId: 'p',
      collection: 'g',
      pick: (s) => ({ items: s.items }),
      hydrate: (api, d) => api.setItems(d.items),
      shouldPersist: (s) => s.fetched,
      debounceMs: 5,
    });
    store.getState().setItemsRaw(['y']); // fetched stays false
    await delay(30);
    expect(await loadCache('p', 'g')).toBeNull();
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `npm run test:run -- offline/persistStore`
Expected: FAIL -- cannot resolve `../persistStore`.

- [ ] **Step 3: Implement `persistStore.ts`**

Create `desktop/portal/src/console/offline/persistStore.ts`:

```ts
// desktop/portal/src/console/offline/persistStore.ts
//
// Wire one zustand store to the per-profile cache: hydrate from IndexedDB on
// init, then write-through (debounced) on change. Hydration applies cached data
// via the store's own actions and is expected to mark the store stale, so the UI
// shows cached-then-fresh once normal polling refreshes it.
import { loadCache, saveCache } from './db';

interface ZStore<S> {
  getState: () => S;
  subscribe: (listener: (state: S, prev: S) => void) => () => void;
}

export interface PersistOptions<S, P> {
  store: ZStore<S>;
  profileId: string;
  collection: string;
  /** The slice to persist. */
  pick: (s: S) => P;
  /** Apply cached data via the store's own actions (and mark it stale). */
  hydrate: (api: S, data: P) => void;
  /** Gate: only write real fetched data, never the empty initial / cleared state. */
  shouldPersist?: (s: S) => boolean;
  debounceMs?: number;
}

/** Hydrate the store from cache (if present) and persist future changes.
 *  Returns an unsubscribe that cancels any pending write and detaches. */
export async function persistStore<S, P>(opts: PersistOptions<S, P>): Promise<() => void> {
  const { store, profileId, collection, pick, hydrate, shouldPersist, debounceMs = 500 } = opts;

  const cached = await loadCache<P>(profileId, collection);
  if (cached !== null) hydrate(store.getState(), cached);

  let timer: ReturnType<typeof setTimeout> | null = null;
  const unsub = store.subscribe((state) => {
    if (shouldPersist && !shouldPersist(state)) return;
    if (timer) clearTimeout(timer);
    timer = setTimeout(() => {
      void saveCache(profileId, collection, pick(state));
    }, debounceMs);
  });

  return () => {
    if (timer) clearTimeout(timer);
    unsub();
  };
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `npm run test:run -- offline/persistStore`
Expected: PASS (3 tests). Then `npx tsc --noEmit` -> 0 errors.

- [ ] **Step 5: Commit**

```bash
git add desktop/portal/src/console/offline/persistStore.ts desktop/portal/src/console/offline/__tests__/persistStore.test.ts
git commit -m "$(cat <<'EOF'
feat(portal): persistStore -- hydrate + debounced write-through for offline (SP2 slice 1)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: Wire the office stores + clear-on-logout

**Files:**
- Create: `desktop/portal/src/console/offline/useOfflinePersistence.ts`
- Modify: `desktop/portal/src/console/ConsoleShell.tsx`

This task is React/store glue verified by the full suite + `tsc` + build (the cache correctness is proven at the db/persistStore layer in Tasks 2-3; the per-profile keys live there). No new unit test.

- [ ] **Step 1: Create the persistence hook**

Create `desktop/portal/src/console/offline/useOfflinePersistence.ts`:

```ts
// desktop/portal/src/console/offline/useOfflinePersistence.ts
//
// At console mount, hydrate the office-data stores (jobs / crew / invoices /
// tasks) from the per-profile IndexedDB cache and write changes through. On
// logout or profile switch, clear the previous profile's cache. Comm is
// intentionally not cached (the portal Channel type has no ephemeral marker;
// see the SP2 slice 1 spec).
import { useEffect, useRef } from 'react';
import { useAuthStore } from '../auth/authStore';
import { useJobsStore } from '../stores/jobsStore';
import { useCrewStore } from '../stores/crewStore';
import { useInvoicesStore } from '../stores/invoicesStore';
import { useTasksStore } from '../stores/tasksStore';
import { persistStore } from './persistStore';
import { clearProfile } from './db';

export function useOfflinePersistence(): void {
  const userId = useAuthStore((s) => s.user?.id ?? null);
  const prevIdRef = useRef<string | null>(null);

  useEffect(() => {
    // Clear the profile we were caching when it changes (logout / switch).
    const prev = prevIdRef.current;
    if (prev && prev !== userId) void clearProfile(prev);
    prevIdRef.current = userId;

    if (!userId) return;

    let cancelled = false;
    const unsubs: Array<() => void> = [];
    const bind = (p: Promise<() => void>) =>
      void p.then((u) => {
        if (cancelled) u();
        else unsubs.push(u);
      });

    bind(
      persistStore({
        store: useJobsStore,
        profileId: userId,
        collection: 'jobs',
        pick: (s) => ({ jobs: s.jobs }),
        hydrate: (api, d) => {
          api.setJobs(d.jobs);
          api.markStale(true);
        },
        shouldPersist: (s) => s.lastFetchedAt != null,
      }),
    );
    bind(
      persistStore({
        store: useCrewStore,
        profileId: userId,
        collection: 'crew',
        pick: (s) => ({ roster: s.roster }),
        hydrate: (api, d) => {
          api.setRoster(d.roster);
          api.markStale(true);
        },
        shouldPersist: (s) => s.lastFetchedAt != null,
      }),
    );
    bind(
      persistStore({
        store: useInvoicesStore,
        profileId: userId,
        collection: 'invoices',
        pick: (s) => ({ invoices: s.invoices }),
        hydrate: (api, d) => {
          api.setInvoices(d.invoices);
          api.markStale(true);
        },
        shouldPersist: (s) => s.invoices.length > 0,
      }),
    );
    bind(
      persistStore({
        store: useTasksStore,
        profileId: userId,
        collection: 'tasks',
        pick: (s) => ({ tasksByJob: s.tasksByJob }),
        hydrate: (api, d) => {
          for (const [jobId, list] of Object.entries(d.tasksByJob)) {
            api.setTasks(jobId, list);
            api.markStale(jobId, true);
          }
        },
        shouldPersist: (s) => Object.keys(s.tasksByJob).length > 0,
      }),
    );

    return () => {
      cancelled = true;
      unsubs.forEach((u) => u());
    };
  }, [userId]);
}
```

Notes for the implementer:
- The zustand store hooks (`useJobsStore` etc.) double as the store API (`getState`/`subscribe`), so passing them as `store` satisfies `persistStore`'s `ZStore`. Type params should infer from the store; if `tsc` cannot infer, annotate explicitly, e.g. `persistStore<ReturnType<typeof useJobsStore.getState>, { jobs: ReturnType<typeof useJobsStore.getState>['jobs'] }>({...})`.
- `tasksStore.setTasks(jobId, list)` and `markStale(jobId, b)` are per-job (the store is keyed by job) -- the loop is correct; confirm the action names against `src/console/stores/tasksStore.ts`.

- [ ] **Step 2: Call the hook from ConsoleShell**

In `desktop/portal/src/console/ConsoleShell.tsx`, add the import after the existing `core/smithCore` import:

```ts
import { useOfflinePersistence } from './offline/useOfflinePersistence';
```

Inside `ConsoleShell`, immediately after `const user = useAuthStore((s) => s.user);`, add:

```ts
  useOfflinePersistence();
```

(Leave the existing `initSmithCore` effect and the JSX unchanged.)

- [ ] **Step 3: Verify the full suite, types, and build**

```bash
cd desktop/portal
npm run test:run
npx tsc --noEmit
npm run build
```
Expected: full suite passes incl. the existing `ConsoleShell.test.tsx` (the offline code degrades when jsdom has no IndexedDB -- `loadCache` catches and returns null, so the shell still renders); `tsc` 0 errors; build succeeds. If `ConsoleShell.test.tsx` throws an unhandled rejection from the offline code, wrap the hook's bind in the existing try/catch-friendly path (it already swallows db errors) and re-run; do not weaken the production code to satisfy the test.

- [ ] **Step 4: Manual deferred-verify (note in report, do not run the dev server here)**

Human check: `npm run build && npm run preview`, install the PWA, load it online (so the stores fetch + cache), then go offline and reload -> the app opens and shows last-synced jobs/invoices/crew/tasks; Lighthouse reports "installable".

- [ ] **Step 5: Commit**

```bash
git add desktop/portal/src/console/offline/useOfflinePersistence.ts desktop/portal/src/console/ConsoleShell.tsx
git commit -m "$(cat <<'EOF'
feat(portal): hydrate office stores from per-profile offline cache (SP2 slice 1)

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Self-review (against the spec)

**Spec coverage:**
- Installable PWA + SW precache incl. wasm (spec §2/§3/§4 vite.config) -> Task 1.
- `db.ts` per-profile cache, schema-versioned, clearProfile (spec §4) -> Task 2.
- `persistStore` hydrate + debounced write-through + shouldPersist gate (spec §4) -> Task 3.
- `useOfflinePersistence` binding jobs/crew/invoices/tasks with the per-store gates + clear-on-logout, wired in ConsoleShell (spec §4/§6) -> Task 4.
- Per-profile isolation (spec §6) -> proven by the db tests (Task 2: isolation + clearProfile cases); keys live in `db.ts`.
- Tests: db + persistStore via fake-indexeddb (spec §8) -> Tasks 2-3. Build assertions (sw/manifest/wasm precache) -> Task 1 Step 5. Manual deferred-verify -> Task 4 Step 4.
- Out-of-scope (writes, sync, comm, push) -> none of the tasks add them; comm deliberately absent from Task 4's bindings.

**Placeholder scan:** No TBD/TODO. Task 1 is build-verified (a SW is not unit-testable) and says so. The icon-filename "confirm and match" note gives the exact command + expected names -- not a placeholder.

**Type consistency:** `loadCache`/`saveCache`/`clearProfile`/`CURRENT_SCHEMA_VERSION` defined in Task 2 and used in Tasks 3-4. `persistStore` / `PersistOptions` shape defined in Task 3 and called in Task 4 with matching fields (`store`/`profileId`/`collection`/`pick`/`hydrate`/`shouldPersist`). Store action names (`setJobs`/`markStale`, `setRoster`, `setInvoices`, `setTasks`/`markStale(jobId,b)`) match the stores inspected during design; the implementer is told to confirm `tasksStore` action names. Gates match each store's real shape (jobs/crew have `lastFetchedAt`; invoices gates on length; tasks gates on key count).

**Determinism / isolation:** comm is excluded entirely (no ephemeral marker on the portal Channel); cache keys are per-profile and the isolation test enforces it; the cache is best-effort and never a hard dependency.
