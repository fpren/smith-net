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

  it('cancels a pending write when a later change fails shouldPersist', async () => {
    const store = makeStore();
    await persistStore<S, { items: string[] }>({
      store,
      profileId: 'p',
      collection: 'cancel',
      pick: (s) => ({ items: s.items }),
      hydrate: (api, d) => api.setItems(d.items),
      shouldPersist: (s) => s.fetched,
      debounceMs: 20,
    });
    store.getState().setItems(['stale']);   // gate true -> schedules a write
    store.setState({ fetched: false });      // gate false -> must cancel the pending write
    await delay(50);
    expect(await loadCache('p', 'cancel')).toBeNull();
  });

  it('unsubscribe cancels a pending write', async () => {
    const store = makeStore();
    const stop = await persistStore<S, { items: string[] }>({
      store,
      profileId: 'p',
      collection: 'unsub',
      pick: (s) => ({ items: s.items }),
      hydrate: (api, d) => api.setItems(d.items),
      shouldPersist: (s) => s.fetched,
      debounceMs: 20,
    });
    store.getState().setItems(['x']);   // schedules a write
    stop();                              // unsubscribe before the debounce fires
    await delay(50);
    expect(await loadCache('p', 'unsub')).toBeNull();
  });
});
