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
    // Open at the current schema version (v2 adds 'sync_outbox') so this manual
    // handle doesn't collide with the version db.ts opens.
    const d = await openDB('smithnet-offline', 2, {
      upgrade(db) {
        if (!db.objectStoreNames.contains('cache')) db.createObjectStore('cache');
        if (!db.objectStoreNames.contains('sync_outbox')) db.createObjectStore('sync_outbox', { keyPath: 'id' });
      },
    });
    await d.put(
      'cache',
      { schemaVersion: CURRENT_SCHEMA_VERSION + 999, savedAt: 0, data: { jobs: ['stale'] } },
      'A:jobs',
    );
    expect(await loadCache('A', 'jobs')).toBeNull();
    expect(await d.get('cache', 'A:jobs')).toBeUndefined();
  });
});
