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
    // Always cancel a pending write first: a change that fails the gate (e.g.
    // the store's clear() on logout) must not let an older scheduled write fire.
    if (timer) clearTimeout(timer);
    timer = null;
    if (shouldPersist && !shouldPersist(state)) return;
    timer = setTimeout(() => {
      void saveCache(profileId, collection, pick(state));
    }, debounceMs);
  });

  return () => {
    if (timer) clearTimeout(timer);
    unsub();
  };
}
