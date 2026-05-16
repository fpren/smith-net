import { render } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { MapCanvas } from '../MapCanvas';

const addToMock = vi.fn().mockReturnThis();
const setLngLatMock = vi.fn().mockReturnThis();
const removeMock = vi.fn();
const markerInstance = { setLngLat: setLngLatMock, addTo: addToMock, remove: removeMock };

vi.mock('maplibre-gl', () => ({
  default: {
    Map: vi.fn(() => ({
      on: vi.fn(), off: vi.fn(), once: vi.fn(), addControl: vi.fn(),
      fitBounds: vi.fn(), jumpTo: vi.fn(), remove: vi.fn(),
      loaded: () => true, resize: vi.fn(),
    })),
    Marker: vi.fn(() => markerInstance),
    NavigationControl: vi.fn(() => ({})),
    LngLatBounds: vi.fn(() => ({ extend: vi.fn() })),
  },
}));

describe('MapCanvas crew layer', () => {
  it('adds a marker per crew position', () => {
    render(
      <MapCanvas
        jobs={[]}
        visibleStatuses={['planned']}
        selectedJobId={null}
        onSelectJob={() => {}}
        crewPositions={[
          { userId: 'u-1', displayName: 'Alice', latitude: 40.7, longitude: -74,
            accuracyM: 5, recordedAt: '2026-05-16T10:00:00Z', source: 'web', batteryPct: 80 },
        ]}
      />
    );
    expect(setLngLatMock).toHaveBeenCalledWith([-74, 40.7]);
    expect(addToMock).toHaveBeenCalled();
  });
});
