import { render, screen, waitFor } from '@testing-library/react';
import { describe, it, expect, beforeEach } from 'vitest';
import { http, HttpResponse } from 'msw';
import { CrewRoute } from '../CrewRoute';
import { useCrewStore } from '../../stores/crewStore';
import { useCrewPositionsStore } from '../../stores/crewPositionsStore';
import { server } from '../../test/msw-server';

describe('CrewRoute', () => {
  beforeEach(() => {
    useCrewStore.getState().clear();
    useCrewPositionsStore.getState().clear();
  });

  it('renders Alice + Bob from the MSW handler', async () => {
    render(<CrewRoute />);
    await waitFor(() => expect(screen.getByText('Alice')).toBeInTheDocument());
    expect(screen.getByText('Bob')).toBeInTheDocument();
  });

  it('shows on-shift indicator for users whose userId appears in /api/crew/positions', async () => {
    // Override the global MSW handler for /api/crew/positions: Alice (p-1)
    // is on-shift, Bob (p-2) is not. server.use is reset by setup.ts's
    // afterEach hook so the override doesn't leak.
    server.use(
      http.get('/api/crew/positions', () =>
        HttpResponse.json({
          positions: [
            {
              userId: 'p-1',
              displayName: 'Alice',
              latitude: 40.7,
              longitude: -74,
              accuracyM: 5,
              recordedAt: new Date(Date.now() - 30_000).toISOString(),
              source: 'android',
              batteryPct: 80,
            },
          ],
        })
      )
    );

    render(<CrewRoute />);
    await waitFor(() => expect(screen.getByText('Alice')).toBeInTheDocument());
    await waitFor(() => expect(screen.getByText(/on shift/i)).toBeInTheDocument());

    // [ON] indicator near Alice; not for Bob.
    const onIndicators = screen.getAllByText(/\[ON\]/);
    expect(onIndicators.length).toBe(1);
  });
});
