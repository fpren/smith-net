// desktop/portal/src/console/hooks/useCrewRoster.ts
import { useEffect, useRef } from 'react';
import { crewClient } from '../api/crewClient';
import { useCrewStore } from '../stores/crewStore';

export function useCrewRoster(intervalMs: number = 15_000): void {
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    const fetchOnce = async () => {
      useCrewStore.getState().markLoading(true);
      const result = await crewClient.getRoster();
      useCrewStore.getState().markLoading(false);
      if (result.ok) {
        useCrewStore.getState().setRoster(result.crew);
      } else {
        useCrewStore.getState().markStale(true);
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
