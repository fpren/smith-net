import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest';
import { setupServer } from 'msw/node';
import { http, HttpResponse } from 'msw';
import { presenceClient } from '../presenceClient';

// Each test file spins up its own isolated MSW server so it does not conflict
// with the global setup in src/console/test/setup.ts (which also calls listen).
// The global server is started in beforeAll of setup.ts; this per-file server
// is started here. They coexist because MSW node intercepts at the Node
// http-module level and each server gets its own scope via listen/close.
const server = setupServer();
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

// Backend returns raw DB rows (snake_case, no wrapper object).
// Verified against: backend/src/shiftsRoutes.ts and backend/src/crewPositionService.ts

describe('presenceClient', () => {
  it('startShift returns { ok, shiftId } on 200', async () => {
    server.use(
      http.post('/api/shifts/start', () =>
        HttpResponse.json({
          id: 'shift-1',
          user_id: 'u-1',
          started_at: '2026-05-16T10:00:00Z',
          ended_at: null,
          source: 'web',
        })
      )
    );
    const r = await presenceClient.startShift('web');
    expect(r.ok).toBe(true);
    if (r.ok) expect(r.shiftId).toBe('shift-1');
  });

  it('startShift returns { ok: false, status } on 409 already-open', async () => {
    server.use(
      http.post('/api/shifts/start', () =>
        HttpResponse.json({ error: 'shift already open' }, { status: 409 })
      )
    );
    const r = await presenceClient.startShift('web');
    expect(r.ok).toBe(false);
    if (!r.ok) expect(r.status).toBe(409);
  });

  it('endShift returns ok on 200', async () => {
    server.use(
      http.post('/api/shifts/end', () =>
        HttpResponse.json({
          id: 's',
          user_id: 'u-1',
          started_at: '2026-05-16T10:00:00Z',
          ended_at: '2026-05-16T18:00:00Z',
          source: 'web',
        })
      )
    );
    const r = await presenceClient.endShift();
    expect(r.ok).toBe(true);
  });

  it('endShift treats 404 as success (no open shift = already ended)', async () => {
    server.use(
      http.post('/api/shifts/end', () =>
        HttpResponse.json({ error: 'no open shift' }, { status: 404 })
      )
    );
    const r = await presenceClient.endShift();
    expect(r.ok).toBe(true);
  });

  it('postLocation returns ok on 200 with body', async () => {
    server.use(
      http.post('/api/presence/location', () =>
        HttpResponse.json({
          user_id: 'u-1',
          latitude: 1,
          longitude: 2,
          accuracy_m: 5,
          recorded_at: '2026-05-16T10:05:00Z',
          source: 'web',
          battery_pct: null,
        })
      )
    );
    const r = await presenceClient.postLocation({ lat: 1, lng: 2, accuracyM: 5 });
    expect(r.ok).toBe(true);
  });

  it('postLocation surfaces 403 (no open shift)', async () => {
    server.use(
      http.post('/api/presence/location', () =>
        HttpResponse.json({ error: 'no open shift' }, { status: 403 })
      )
    );
    const r = await presenceClient.postLocation({ lat: 1, lng: 2 });
    expect(r.ok).toBe(false);
    if (!r.ok) expect(r.status).toBe(403);
  });

  it('getCurrentShift returns null when server returns null', async () => {
    server.use(
      http.get('/api/shifts/current', () => HttpResponse.json(null))
    );
    const r = await presenceClient.getCurrentShift();
    expect(r.ok).toBe(true);
    if (r.ok) expect(r.shiftId).toBeNull();
  });
});
