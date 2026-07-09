import { render, screen, waitFor, fireEvent, act } from '@testing-library/react';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { http, HttpResponse } from 'msw';
import { MapRoute } from '../MapRoute';
import { useJobsStore } from '../../stores/jobsStore';
import { server } from '../../test/msw-server';

// MapLibre is mocked the same way as in MapCanvas.test.tsx
vi.mock('maplibre-gl', () => ({
  default: {
    Map: vi.fn(() => ({ on: vi.fn(), off: vi.fn(), once: vi.fn(), addControl: vi.fn(), fitBounds: vi.fn(), flyTo: vi.fn(), jumpTo: vi.fn(), remove: vi.fn(), resize: vi.fn(), loaded: () => true })),
    Marker: vi.fn(() => ({ setLngLat: vi.fn().mockReturnThis(), addTo: vi.fn().mockReturnThis(), remove: vi.fn(), getElement: vi.fn(() => document.createElement('div')) })),
    Popup: vi.fn(() => ({ setLngLat: vi.fn().mockReturnThis(), setDOMContent: vi.fn().mockReturnThis(), addTo: vi.fn().mockReturnThis(), remove: vi.fn() })),
    NavigationControl: vi.fn(() => ({})),
    LngLatBounds: vi.fn(() => ({ extend: vi.fn() })),
  },
}));

describe('MapRoute', () => {
  beforeEach(() => { useJobsStore.getState().clear(); localStorage.clear(); });

  it('renders the stats strip + side panel + map canvas containers', async () => {
    render(<MemoryRouter><MapRoute /></MemoryRouter>);
    // The MSW handler returns 1 job ("Test Job") on /api/jobs after polling fires
    // Both StatsStrip and MapSidePanel render "PLANNED", so use getAllByText
    await waitFor(() => expect(screen.getAllByText(/PLANNED/i).length).toBeGreaterThan(0));
    expect(screen.getByTestId('map-canvas')).toBeInTheDocument();
  });

  it('shows the create-job button', async () => {
    render(<MemoryRouter><MapRoute /></MemoryRouter>);
    await waitFor(() => expect(screen.getByRole('button', { name: /create job/i })).toBeInTheDocument());
  });

  it('initial jobs fetch failure (no cached jobs) shows ErrorState with retry, not the map', async () => {
    server.use(http.get('/api/jobs', () => HttpResponse.json({ error: 'boom' }, { status: 500 })));
    render(<MemoryRouter><MapRoute /></MemoryRouter>);
    await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument());
    expect(screen.queryByTestId('map-canvas')).not.toBeInTheDocument();

    server.use(http.get('/api/jobs', () => HttpResponse.json({ jobs: [] })));
    fireEvent.click(screen.getByRole('button', { name: /retry/i }));
    await waitFor(() => expect(screen.getByTestId('map-canvas')).toBeInTheDocument());
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('a stale poll with cached jobs shows an inline banner while still rendering the map', async () => {
    render(<MemoryRouter><MapRoute /></MemoryRouter>);
    await waitFor(() => expect(screen.getByTestId('map-canvas')).toBeInTheDocument());
    act(() => { useJobsStore.getState().markListStale(true); });
    await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument());
    expect(screen.getByTestId('map-canvas')).toBeInTheDocument();
  });
});
