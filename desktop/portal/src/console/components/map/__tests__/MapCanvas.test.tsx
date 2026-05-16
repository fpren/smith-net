import { render, cleanup } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { MapCanvas } from '../MapCanvas';
import type { Job } from '../../../api/jobsClient';
import * as maplibreModule from 'maplibre-gl';

const mapInstance = {
  on: vi.fn(), off: vi.fn(), once: vi.fn(),
  addControl: vi.fn(),
  fitBounds: vi.fn(),
  flyTo: vi.fn(),
  jumpTo: vi.fn(),
  remove: vi.fn(),
  resize: vi.fn(),
  loaded: () => true,
};

const markerInstance = () => ({
  setLngLat: vi.fn().mockReturnThis(),
  setPopup: vi.fn().mockReturnThis(),
  addTo: vi.fn().mockReturnThis(),
  remove: vi.fn(),
  getElement: vi.fn(() => document.createElement('div')),
});

const popupInstance = () => ({
  setLngLat: vi.fn().mockReturnThis(),
  setDOMContent: vi.fn().mockReturnThis(),
  addTo: vi.fn().mockReturnThis(),
  remove: vi.fn(),
});

vi.mock('maplibre-gl', () => ({
  default: {
    Map: vi.fn(() => mapInstance),
    Marker: vi.fn(() => markerInstance()),
    Popup: vi.fn(() => popupInstance()),
    NavigationControl: vi.fn(() => ({})),
    LngLatBounds: vi.fn(() => ({ extend: vi.fn() })),
  },
}));

const j = (id: string, lat: number | null, lng: number | null, status: Job['status'] = 'planned'): Job => ({
  id, foremanId: 'f', clientId: null, engagementId: null,
  title: `Job ${id}`, description: null, status,
  scheduledAt: null, location: null,
  latitude: lat, longitude: lng, geocodedAt: lat !== null ? '2026-05-13T00:00:00Z' : null,
  createdAt: '2026-05-13T00:00:00Z', updatedAt: '2026-05-13T00:00:00Z',
} as any);

describe('MapCanvas', () => {
  beforeEach(() => { vi.clearAllMocks(); });
  afterEach(cleanup);

  it('constructs a Map on mount', () => {
    render(<MapCanvas jobs={[]} visibleStatuses={['planned']} selectedJobId={null} onSelectJob={vi.fn()} />);
    // maplibre-gl is mocked; constructor called once after effect runs
    const maplibre = (maplibreModule as any).default;
    expect(maplibre.Map).toHaveBeenCalled();
  });

  it('creates a marker per job with coords AND matching visibleStatuses', () => {
    const jobs = [
      j('a', 40, -73, 'planned'),
      j('b', null, null, 'planned'),    // missing coords — skipped
      j('c', 41, -74, 'in_progress'),   // status not in visibleStatuses — skipped
    ];
    render(<MapCanvas jobs={jobs} visibleStatuses={['planned']} selectedJobId={null} onSelectJob={vi.fn()} />);
    const maplibre = (maplibreModule as any).default;
    // Only 1 marker should be created (for job 'a')
    expect(maplibre.Marker).toHaveBeenCalledTimes(1);
  });

  it('does NOT crash when jobs is empty', () => {
    expect(() => {
      render(<MapCanvas jobs={[]} visibleStatuses={['planned']} selectedJobId={null} onSelectJob={vi.fn()} />);
    }).not.toThrow();
  });
});
