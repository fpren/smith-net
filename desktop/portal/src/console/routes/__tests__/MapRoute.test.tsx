import { render, screen, waitFor } from '@testing-library/react';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { MapRoute } from '../MapRoute';
import { useJobsStore } from '../../stores/jobsStore';

// MapLibre is mocked the same way as in MapCanvas.test.tsx
vi.mock('maplibre-gl', () => ({
  default: {
    Map: vi.fn(() => ({ on: vi.fn(), off: vi.fn(), addControl: vi.fn(), fitBounds: vi.fn(), flyTo: vi.fn(), remove: vi.fn() })),
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
});
