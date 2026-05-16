import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest';
import { setupServer } from 'msw/node';
import { http, HttpResponse } from 'msw';
import { crewPositionsClient } from '../crewPositionsClient';

const server = setupServer();
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

describe('crewPositionsClient', () => {
  it('list returns positions on 200', async () => {
    server.use(
      http.get('/api/crew/positions', () =>
        HttpResponse.json({
          positions: [
            {
              userId: 'u-1',
              displayName: 'Alice',
              latitude: 40.7,
              longitude: -74,
              accuracyM: 5,
              recordedAt: '2026-05-16T10:00:00Z',
              source: 'web',
              batteryPct: 80,
            },
          ],
        })
      )
    );
    const r = await crewPositionsClient.list();
    expect(r.ok).toBe(true);
    if (r.ok) {
      expect(r.positions).toHaveLength(1);
      expect(r.positions[0].userId).toBe('u-1');
      expect(r.positions[0].displayName).toBe('Alice');
    }
  });

  it('list surfaces 403 for non-foreman', async () => {
    server.use(
      http.get('/api/crew/positions', () =>
        HttpResponse.json({ error: 'foreman role required' }, { status: 403 })
      )
    );
    const r = await crewPositionsClient.list();
    expect(r.ok).toBe(false);
    if (!r.ok) expect(r.status).toBe(403);
  });
});
