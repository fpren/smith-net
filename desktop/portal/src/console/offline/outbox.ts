// desktop/portal/src/console/offline/outbox.ts
//
// W6 offline-write outbox. Mutations are network-first: if the request reaches
// the server we return its response; if it fails because we're offline we queue
// the op in IndexedDB and replay it on reconnect. Every op carries a stable
// idempotency key (its id), sent as the `Idempotency-Key` header, so a replay of
// a create that already succeeded returns the cached response instead of making
// a duplicate (backend middleware: src/middleware/idempotency.ts).
//
// Ordering: each op is stamped with a per-profile Lamport counter at enqueue
// time; the drain replays oldest-first by (lamportTs, authorId, createdAt) so the
// server applies them in the same causal order regardless of when they drain.

import { db, OUTBOX_STORE } from './db';

export type OutboxStatus = 'pending' | 'in_flight' | 'done' | 'failed';

export interface OutboxOp {
  id: string;            // idempotency key + primary key
  profileId: string;
  authorId: string;
  method: string;        // 'POST' | 'PATCH' | 'DELETE'
  path: string;          // e.g. '/api/jobs'
  body: unknown;
  label: string;         // 'job:create', 'shift:start', 'expense:create' (debug/UI)
  lamportTs: number;
  status: OutboxStatus;
  attempts: number;
  lastError?: string;
  createdAt: number;
}

// ── Lamport clock (per profile, persisted in localStorage) ───────────────────

function lamportTick(profileId: string): number {
  const k = `smithnet:lamport:${profileId}`;
  let v = 0;
  try { v = parseInt(localStorage.getItem(k) ?? '0', 10) || 0; } catch { /* ignore */ }
  v += 1;
  try { localStorage.setItem(k, String(v)); } catch { /* ignore */ }
  return v;
}

const uuid = (): string =>
  (globalThis.crypto?.randomUUID?.() ??
    `op-${Date.now()}-${Math.floor(Math.random() * 1e9).toString(36)}`);

// ── Store helpers ────────────────────────────────────────────────────────────

async function putOp(op: OutboxOp): Promise<void> {
  try { await (await db()).put(OUTBOX_STORE, op); } catch { /* best-effort */ }
}

async function allOps(): Promise<OutboxOp[]> {
  try { return (await (await db()).getAll(OUTBOX_STORE)) as OutboxOp[]; }
  catch { return []; }
}

async function deleteOp(id: string): Promise<void> {
  try { await (await db()).delete(OUTBOX_STORE, id); } catch { /* best-effort */ }
}

/** Queue a mutation for later replay. Returns the op id (idempotency key). */
export async function enqueue(input: {
  profileId: string;
  method: string;
  path: string;
  body: unknown;
  label: string;
}): Promise<string> {
  const id = uuid();
  const op: OutboxOp = {
    id,
    profileId: input.profileId,
    authorId: input.profileId,
    method: input.method,
    path: input.path,
    body: input.body,
    label: input.label,
    lamportTs: lamportTick(input.profileId),
    status: 'pending',
    attempts: 0,
    createdAt: Date.now(),
  };
  await putOp(op);
  return id;
}

/** Count of ops not yet delivered (pending or failed). */
export async function pendingCount(): Promise<number> {
  return (await allOps()).filter((o) => o.status === 'pending' || o.status === 'failed').length;
}

// ── Sending ──────────────────────────────────────────────────────────────────

interface MutateResult<T = any> {
  ok: boolean;
  queued: boolean;        // true => no network, op was queued for replay
  status?: number;
  data?: T;
  error?: string;
}

/** POST/PATCH a mutation directly with its idempotency key. */
async function send(op: Pick<OutboxOp, 'id' | 'method' | 'path' | 'body'>): Promise<Response> {
  return fetch(op.path, {
    method: op.method,
    credentials: 'include',
    headers: { 'Content-Type': 'application/json', 'Idempotency-Key': op.id },
    body: op.body !== undefined ? JSON.stringify(op.body) : undefined,
  });
}

/**
 * Network-first mutation. Tries the request immediately; on a network failure
 * (offline) it queues the op and reports `queued: true` so the caller can update
 * the UI optimistically. On an HTTP error it does NOT queue (the server was
 * reached and rejected it) and returns the error.
 */
export async function mutate<T = any>(input: {
  profileId: string;
  method: string;
  path: string;
  body: unknown;
  label: string;
}): Promise<MutateResult<T>> {
  const id = uuid();
  try {
    const res = await send({ id, method: input.method, path: input.path, body: input.body });
    if (res.ok) {
      const data = res.status === 204 ? undefined : await res.json().catch(() => undefined);
      return { ok: true, queued: false, status: res.status, data };
    }
    const err = await res.json().catch(() => ({ error: res.statusText }));
    return { ok: false, queued: false, status: res.status, error: err?.error ?? 'Request failed' };
  } catch {
    // Network unreachable -> queue with the SAME id so the eventual replay is
    // idempotent with this attempt (if it secretly went through, no dup).
    const op: OutboxOp = {
      id,
      profileId: input.profileId,
      authorId: input.profileId,
      method: input.method,
      path: input.path,
      body: input.body,
      label: input.label,
      lamportTs: lamportTick(input.profileId),
      status: 'pending',
      attempts: 0,
      createdAt: Date.now(),
    };
    await putOp(op);
    return { ok: true, queued: true };
  }
}

// ── Draining ─────────────────────────────────────────────────────────────────

let draining = false;

/** Replay queued ops oldest-first by causal order. Safe to call repeatedly. */
export async function drainOutbox(): Promise<{ drained: number; failed: number }> {
  if (draining) return { drained: 0, failed: 0 };
  draining = true;
  let drained = 0;
  let failed = 0;
  try {
    const ops = (await allOps())
      .filter((o) => o.status === 'pending')
      .sort((a, b) =>
        a.lamportTs - b.lamportTs ||
        a.authorId.localeCompare(b.authorId) ||
        a.createdAt - b.createdAt);

    for (const op of ops) {
      await putOp({ ...op, status: 'in_flight' });
      try {
        const res = await send(op);
        if (res.ok) {
          await deleteOp(op.id);
          drained += 1;
        } else if (res.status === 409) {
          // idempotency_in_progress: another delivery is mid-flight. Leave pending.
          await putOp({ ...op, status: 'pending', attempts: op.attempts + 1 });
        } else if (res.status >= 400 && res.status < 500) {
          // Server reached and rejected (validation/permission). Won't succeed on
          // replay -> mark failed so it stops blocking the queue.
          await putOp({ ...op, status: 'failed', attempts: op.attempts + 1, lastError: `HTTP ${res.status}` });
          failed += 1;
        } else {
          await putOp({ ...op, status: 'pending', attempts: op.attempts + 1, lastError: `HTTP ${res.status}` });
        }
      } catch {
        // Still offline -> back to pending, try again on the next trigger.
        await putOp({ ...op, status: 'pending', attempts: op.attempts + 1, lastError: 'network' });
        break; // no point hammering the rest while offline
      }
    }
  } finally {
    draining = false;
  }
  return { drained, failed };
}

/** Wire drain triggers: on reconnect, on tab focus, and once at startup. */
export function registerOutboxDrain(): () => void {
  const tryDrain = () => { void drainOutbox(); };
  const onVisible = () => { if (document.visibilityState === 'visible') tryDrain(); };
  window.addEventListener('online', tryDrain);
  document.addEventListener('visibilitychange', onVisible);
  // Initial attempt (covers a reload while online with a non-empty queue).
  tryDrain();
  return () => {
    window.removeEventListener('online', tryDrain);
    document.removeEventListener('visibilitychange', onVisible);
  };
}
