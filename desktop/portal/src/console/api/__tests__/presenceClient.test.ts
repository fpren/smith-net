import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest';
import { setupServer } from 'msw/node';
import { http, HttpResponse } from 'msw';
import { presenceClient } from '../presenceClient';

// Per-file MSW server. Backend wire format is camelCase, wrapped in
// `{ shift: ... }` / `{ position: ... }` — verified against
// backend/src/shiftsRoutes.ts and backend/src/presenceLocationRoutes.ts.
const server = setupServer();
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

describe('presenceClient', () => {
  it('startShift returns { ok, shiftId } on 200', async () => {
    server.use(
      http.post('/api/shifts/start', () =>
        HttpResponse.json({
          shift: { id: 'shift-1', userId: 'u-1', startedAt: '2026-05-16T10:00:00Z', endedAt: null, source: 'web' },
        })
      )
    );
    const r = await presenceClient.startShift('web');
    expect(r.ok).toBe(true);
    if (r.ok) expect(r.shiftId).toBe('shift-1');
  });

  it('startShift returns { ok: false, status } on 409 already-open', async () => {
    server.use(
      http.post('/api/shifts/start', () => HttpResponse.json({ error: 'shift already open' }, { status: 409 }))
    );
    const r = await presenceClient.startShift('web');
    expect(r.ok).toBe(false);
    if (!r.ok) expect(r.status).toBe(409);
  });

  it('endShift returns ok on 200', async () => {
    server.use(
      http.post('/api/shifts/end', () =>
        HttpResponse.json({ shift: { id: 's', userId: 'u-1', startedAt: 't', endedAt: 'u', source: 'web' } })
      )
    );
    const r = await presenceClient.endShift();
    expect(r.ok).toBe(true);
  });

  it('endShift treats 404 as success (no open shift = already ended)', async () => {
    server.use(
      http.post('/api/shifts/end', () => HttpResponse.json({ error: 'no open shift' }, { status: 404 }))
    );
    const r = await presenceClient.endShift();
    expect(r.ok).toBe(true);
  });

  it('postLocation returns ok on 200 with body', async () => {
    server.use(
      http.post('/api/presence/location', () =>
        HttpResponse.json({ position: { userId: 'u-1', latitude: 1, longitude: 2, accuracyM: 5, recordedAt: 't', source: 'web', batteryPct: null, displayName: 'A' } })
      )
    );
    const r = await presenceClient.postLocation({ lat: 1, lng: 2, accuracyM: 5 });
    expect(r.ok).toBe(true);
  });

  it('postLocation surfaces 403 (no open shift)', async () => {
    server.use(
      http.post('/api/presence/location', () => HttpResponse.json({ error: 'no open shift' }, { status: 403 }))
    );
    const r = await presenceClient.postLocation({ lat: 1, lng: 2 });
    expect(r.ok).toBe(false);
    if (!r.ok) expect(r.status).toBe(403);
  });

  it('getCurrentShift returns null when server returns { shift: null }', async () => {
    server.use(
      http.get('/api/shifts/current', () => HttpResponse.json({ shift: null }))
    );
    const r = await presenceClient.getCurrentShift();
    expect(r.ok).toBe(true);
    if (r.ok) expect(r.shiftId).toBeNull();
  });
});
