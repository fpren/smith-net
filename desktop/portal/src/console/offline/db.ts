// desktop/portal/src/console/offline/db.ts
//
// Per-profile IndexedDB cache for offline read. One object store keyed by
// `${profileId}:${collection}`. Values are schema-version-stamped so a stored-
// shape change invalidates old blobs. The cache is an enhancement, never a hard
// dependency: every call swallows errors and degrades to "no cache" (e.g. when
// IndexedDB is unavailable or quota is exceeded).
import { openDB, type IDBPDatabase } from 'idb';

const DB_NAME = 'smithnet-offline';
// v2 adds the 'sync_outbox' store (W6 offline-write outbox). The read 'cache'
// store is untouched.
const DB_VERSION = 2;
const STORE = 'cache';
/** Object store holding queued offline writes. Keyed by op id (idempotency key). */
export const OUTBOX_STORE = 'sync_outbox';

/** Bump to invalidate every cached blob after a stored-shape change. */
export const CURRENT_SCHEMA_VERSION = 1;

interface CacheEnvelope<T> {
  schemaVersion: number;
  savedAt: number;
  data: T;
}

let _dbPromise: Promise<IDBPDatabase> | null = null;

/** Shared handle to the offline DB (used by the read cache and the outbox). */
export function db(): Promise<IDBPDatabase> {
  if (!_dbPromise) {
    _dbPromise = openDB(DB_NAME, DB_VERSION, {
      upgrade(d) {
        if (!d.objectStoreNames.contains(STORE)) d.createObjectStore(STORE);
        if (!d.objectStoreNames.contains(OUTBOX_STORE)) d.createObjectStore(OUTBOX_STORE, { keyPath: 'id' });
      },
    }).catch((e) => {
      _dbPromise = null;
      throw e;
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
