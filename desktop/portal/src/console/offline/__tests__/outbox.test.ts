import 'fake-indexeddb/auto';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { mutate, drainOutbox, pendingCount } from '../outbox';
import { db, OUTBOX_STORE } from '../db';

// Minimal Response-like stub (avoids depending on a global fetch Response).
const mockRes = (status: number, body: any) => ({
  ok: status >= 200 && status < 300,
  status,
  json: async () => body,
  text: async () => JSON.stringify(body),
});

const offline = () => vi.fn().mockRejectedValue(new TypeError('Failed to fetch'));

beforeEach(async () => {
  await (await db()).clear(OUTBOX_STORE);
  vi.restoreAllMocks();
});

describe('offline outbox', () => {
  it('mutate is network-first: returns server data and does not queue on success', async () => {
    const fetchMock = vi.fn().mockResolvedValue(mockRes(201, { job: { id: 'j1' } }));
    vi.stubGlobal('fetch', fetchMock);

    const r = await mutate({ profileId: 'A', method: 'POST', path: '/api/jobs', body: { title: 'x' }, label: 'job:create' });

    expect(r.ok).toBe(true);
    expect(r.queued).toBe(false);
    expect(r.data).toEqual({ job: { id: 'j1' } });
    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(fetchMock.mock.calls[0][1].headers['Idempotency-Key']).toBeTruthy();
    expect(await pendingCount()).toBe(0);
  });

  it('mutate queues the op when offline', async () => {
    vi.stubGlobal('fetch', offline());
    const r = await mutate({ profileId: 'A', method: 'POST', path: '/api/jobs', body: { title: 'x' }, label: 'job:create' });
    expect(r.queued).toBe(true);
    expect(await pendingCount()).toBe(1);
  });

  it('drains a queued op once reconnected, sending its idempotency key', async () => {
    vi.stubGlobal('fetch', offline());
    await mutate({ profileId: 'A', method: 'POST', path: '/api/jobs', body: { title: 'x' }, label: 'job:create' });
    expect(await pendingCount()).toBe(1);

    const ok = vi.fn().mockResolvedValue(mockRes(201, {}));
    vi.stubGlobal('fetch', ok);
    const res = await drainOutbox();

    expect(res.drained).toBe(1);
    expect(await pendingCount()).toBe(0);
    expect(ok.mock.calls[0][1].headers['Idempotency-Key']).toBeTruthy();
  });

  it('a 4xx on replay marks the op failed instead of retrying forever', async () => {
    vi.stubGlobal('fetch', offline());
    await mutate({ profileId: 'A', method: 'POST', path: '/api/expenses', body: {}, label: 'expense:create' });

    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(mockRes(400, { error: 'bad' })));
    const res = await drainOutbox();

    expect(res.failed).toBe(1);
    expect(res.drained).toBe(0);
  });

  it('replays multiple ops in causal (lamport, createdAt) order', async () => {
    vi.stubGlobal('fetch', offline());
    await mutate({ profileId: 'A', method: 'POST', path: '/api/shifts/start', body: { n: 1 }, label: 'shift:start' });
    await mutate({ profileId: 'A', method: 'POST', path: '/api/jobs', body: { n: 2 }, label: 'job:create' });

    const seen: any[] = [];
    const ok = vi.fn().mockImplementation((_path: string, init: any) => {
      seen.push(JSON.parse(init.body).n);
      return Promise.resolve(mockRes(201, {}));
    });
    vi.stubGlobal('fetch', ok);
    await drainOutbox();

    expect(seen).toEqual([1, 2]);
    expect(await pendingCount()).toBe(0);
  });
});
