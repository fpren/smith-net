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
    let observer: ResizeObserver | null = null;
    try {
      const map = new maplibregl.Map({
        container: containerRef.current,
        style: TILE_STYLE as maplibregl.StyleSpecification,
        center: [-74.0, 40.7],
        zoom: 2,
      });
      map.addControl(new maplibregl.NavigationControl(), 'top-right');
      mapRef.current = map;
      // MapLibre measures the container at construction; if the flex parent
      // has wrong/zero size at that moment, all coordinate math is off (pins
      // render off-screen). Force one resize after the layout pass via rAF,
      // then keep a ResizeObserver for future size changes.
      requestAnimationFrame(() => mapRef.current?.resize());
      if (typeof ResizeObserver !== 'undefined' && containerRef.current) {
        observer = new ResizeObserver(() => {
          mapRef.current?.resize();
        });
        observer.observe(containerRef.current);
      }
    } catch (e: any) {
      console.warn('[MapCanvas] init failed:', e.message);
    }
    return () => {
      observer?.disconnect();
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
      (j) =>
        j.latitude !== null &&
        j.longitude !== null &&
        Number.isFinite(j.latitude) &&
        Number.isFinite(j.longitude) &&
        visibleStatuses.includes(j.status)
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
      const existing = markersRef.current.get(j.id);
      if (existing) {
        existing.setLngLat([j.longitude!, j.latitude!]);
        continue;
      }
      const el = createJobMarkerElement(j);
      el.addEventListener('click', () => onSelectJob(j.id));
      const marker = new maplibregl.Marker({ element: el })
        .setLngLat([j.longitude!, j.latitude!])
        .addTo(map);
      markersRef.current.set(j.id, marker);
    }

    // Position the camera. jumpTo for a single pin (deterministic), fitBounds
    // for multiple (deferred to map 'idle' so the viewport is settled — fitBounds
    // before the map has measured its container is unreliable).
    if (showable.length === 1) {
      map.jumpTo({ center: [showable[0].longitude!, showable[0].latitude!], zoom: 14 });
    } else if (showable.length > 1) {
      const bounds = new maplibregl.LngLatBounds();
      for (const j of showable) bounds.extend([j.longitude!, j.latitude!]);
      const run = () => map.fitBounds(bounds, { padding: 60, maxZoom: 15, duration: 0 });
      if (map.loaded()) run();
      else map.once('idle', run);
    }
  }, [jobs, visibleStatuses, onSelectJob]);

  return <div ref={containerRef} className="w-full h-full" data-testid="map-canvas" />;
}
