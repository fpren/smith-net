import { describe, it, expect, beforeAll, afterAll, afterEach } from 'vitest';
import { setupServer } from 'msw/node';
import { http, HttpResponse } from 'msw';
import { crewPositionsClient } from '../crewPositionsClient';

// Each test file spins up its own isolated MSW server.
const server = setupServer();
beforeAll(() => server.listen({ onUnhandledRequest: 'error' }));
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

// Backend returns the raw array directly (no { positions: [...] } wrapper).
// Fields are snake_case per crewPositionService.listOpenPositions.
// There is no displayName field — that is not stored in crew_positions.
// Verified against: backend/src/presenceLocationRoutes.ts and
//                   backend/src/crewPositionService.ts

describe('crewPositionsClient', () => {
  it('list returns positions on 200', async () => {
    server.use(
      http.get('/api/crew/positions', () =>
        HttpResponse.json([
          {
            user_id: 'u-1',
            latitude: 40.7,
            longitude: -74,
            accuracy_m: 5,
            recorded_at: '2026-05-16T10:00:00Z',
            source: 'web',
            battery_pct: 80,
          },
        ])
      )
    );
    const r = await crewPositionsClient.list();
    expect(r.ok).toBe(true);
    if (r.ok) {
      expect(r.positions).toHaveLength(1);
      expect(r.positions[0].user_id).toBe('u-1');
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
