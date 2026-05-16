// desktop/portal/src/console/hooks/useCrewPositionsPolling.ts
import { useEffect, useRef } from 'react';
import { crewPositionsClient } from '../api/crewPositionsClient';
import { useCrewPositionsStore } from '../stores/crewPositionsStore';

export function useCrewPositionsPolling(intervalMs: number = 15_000): void {
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    const fetchOnce = async () => {
      useCrewPositionsStore.getState().markLoading(true);
      const result = await crewPositionsClient.list();
      useCrewPositionsStore.getState().markLoading(false);
      if (result.ok) {
        useCrewPositionsStore.getState().setPositions(result.positions);
      } else if (result.status !== 403) {
        // 403 is expected for non-foreman roles. Treat as no-op (no error UI,
        // no stale flag). Any other failure flips stale so the route can show
        // an offline banner.
        useCrewPositionsStore.getState().markStale(true);
      }
    };

    const start = () => {
      if (intervalRef.current !== null) return;
      intervalRef.current = setInterval(fetchOnce, intervalMs);
    };
    const stop = () => {
      if (intervalRef.current !== null) {
        clearInterval(intervalRef.current);
        intervalRef.current = null;
      }
    };
    const onVisibility = () => {
      if (document.visibilityState === 'visible') {
        fetchOnce();
        start();
      } else {
        stop();
      }
    };

    fetchOnce();
    start();
    document.addEventListener('visibilitychange', onVisibility);
    return () => {
      stop();
      document.removeEventListener('visibilitychange', onVisibility);
    };
  }, [intervalMs]);
}
