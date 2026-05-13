// desktop/portal/src/console/components/map/MapCanvas.tsx
import { useEffect, useRef } from 'react';
import maplibregl from 'maplibre-gl';
import type { Job, JobStatus } from '../../api/jobsClient';
import { createJobMarkerElement } from './JobMarker';

interface Props {
  jobs: Job[];
  visibleStatuses: JobStatus[];
  selectedJobId: string | null;
  onSelectJob: (jobId: string) => void;
}

const TILE_STYLE = {
  version: 8 as const,
  sources: {
    osm: {
      type: 'raster' as const,
      tiles: ['https://tile.openstreetmap.org/{z}/{x}/{y}.png'],
      tileSize: 256,
      attribution: '© OpenStreetMap contributors',
    },
  },
  layers: [{ id: 'osm', type: 'raster' as const, source: 'osm' }],
};

export function MapCanvas({ jobs, visibleStatuses, onSelectJob }: Props) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const mapRef = useRef<maplibregl.Map | null>(null);
  const markersRef = useRef<Map<string, maplibregl.Marker>>(new Map());

  // Init map once
  useEffect(() => {
    if (!containerRef.current) return;
    try {
      const map = new maplibregl.Map({
        container: containerRef.current,
        style: TILE_STYLE as maplibregl.StyleSpecification,
        center: [-74.0, 40.7],
        zoom: 2,
      });
      map.addControl(new maplibregl.NavigationControl(), 'top-right');
      mapRef.current = map;
    } catch (e: any) {
      console.warn('[MapCanvas] init failed:', e.message);
    }
    return () => {
      mapRef.current?.remove();
      mapRef.current = null;
      markersRef.current.clear();
    };
  }, []);

  // Diff markers when jobs / visibleStatuses change
  useEffect(() => {
    const map = mapRef.current;
    if (!map) return;

    const showable = jobs.filter(
      (j) => {
        const lat = (j as any).latitude;
        const lng = (j as any).longitude;
        return (
          lat !== null &&
          lat !== undefined &&
          lng !== null &&
          lng !== undefined &&
          Number.isFinite(lat) &&
          Number.isFinite(lng) &&
          visibleStatuses.includes(j.status)
        );
      }
    );

    const wantIds = new Set(showable.map((j) => j.id));

    // Remove markers no longer needed
    for (const [id, marker] of markersRef.current.entries()) {
      if (!wantIds.has(id)) {
        marker.remove();
        markersRef.current.delete(id);
      }
    }

    // Add or update markers
    for (const j of showable) {
      const lat = (j as any).latitude as number;
      const lng = (j as any).longitude as number;
      const existing = markersRef.current.get(j.id);
      if (existing) {
        existing.setLngLat([lng, lat]);
        continue;
      }
      const el = createJobMarkerElement(j);
      el.addEventListener('click', () => onSelectJob(j.id));
      const marker = new maplibregl.Marker({ element: el })
        .setLngLat([lng, lat])
        .addTo(map);
      markersRef.current.set(j.id, marker);
    }

    // Fit bounds when we have pins
    if (showable.length > 0 && markersRef.current.size === showable.length) {
      const bounds = new maplibregl.LngLatBounds();
      for (const j of showable) {
        const lat = (j as any).latitude as number;
        const lng = (j as any).longitude as number;
        bounds.extend([lng, lat]);
      }
      map.fitBounds(bounds, { padding: 60, maxZoom: 15, duration: 0 });
    }
  }, [jobs, visibleStatuses, onSelectJob]);

  return <div ref={containerRef} className="w-full h-full" data-testid="map-canvas" />;
}
