// desktop/portal/src/console/hooks/useShareLocation.ts
import { useCallback, useEffect, useRef } from 'react';
import { presenceClient } from '../api/presenceClient';
import { useShareLocationStore } from '../stores/shareLocationStore';

const POST_INTERVAL_MS = 60_000;

export function useShareLocation() {
  const watchIdRef = useRef<number | null>(null);
  const lastPostAtRef = useRef<number>(0);

  const stopWatch = useCallback(() => {
    if (watchIdRef.current !== null && navigator.geolocation) {
      navigator.geolocation.clearWatch(watchIdRef.current);
      watchIdRef.current = null;
    }
  }, []);

  const start = useCallback(async () => {
    if (!navigator.geolocation) {
      useShareLocationStore.getState().setError('Geolocation not supported in this browser');
      return;
    }
    useShareLocationStore.getState().setTransitioning(true);
    const r = await presenceClient.startShift('web');
    if (!r.ok) {
      useShareLocationStore.getState().setError(`${r.error} (${r.status})`);
      return;
    }
    if ('queued' in r) {
      // No connection: the clock-in is queued, but live location sharing needs
      // the network, so don't start the watcher.
      useShareLocationStore.getState().setError('Offline — location sharing will start when back online');
      return;
    }
    useShareLocationStore.getState().setSharing(true, r.shiftId);
    lastPostAtRef.current = 0;

    watchIdRef.current = navigator.geolocation.watchPosition(
      async (pos) => {
        const now = Date.now();
        if (now - lastPostAtRef.current < POST_INTERVAL_MS) return;
        lastPostAtRef.current = now;
        await presenceClient.postLocation({
          lat: pos.coords.latitude,
          lng: pos.coords.longitude,
          accuracyM: pos.coords.accuracy,
        });
      },
      (err) => {
        useShareLocationStore.getState().setError(`Geolocation error: ${err.message}`);
        stopWatch();
        // Best-effort end shift so server isn't left thinking we're still sharing.
        presenceClient.endShift().catch(() => { /* ignore */ });
        useShareLocationStore.getState().setSharing(false);
      },
      { enableHighAccuracy: true, maximumAge: 30_000, timeout: 60_000 }
    );
  }, [stopWatch]);

  const stop = useCallback(async () => {
    useShareLocationStore.getState().setTransitioning(true);
    stopWatch();
    await presenceClient.endShift();
    useShareLocationStore.getState().setSharing(false);
  }, [stopWatch]);

  // Tab close / unmount: do not lose tracking server-side — emit beacon end.
  useEffect(() => {
    const handler = () => {
      if (useShareLocationStore.getState().isSharing) {
        try {
          navigator.sendBeacon?.('/api/shifts/end', new Blob([JSON.stringify({})], { type: 'application/json' }));
        } catch { /* ignore */ }
      }
    };
    window.addEventListener('beforeunload', handler);
    return () => window.removeEventListener('beforeunload', handler);
  }, []);

  return { start, stop };
}
