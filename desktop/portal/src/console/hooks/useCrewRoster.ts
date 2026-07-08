// desktop/portal/src/console/hooks/useCrewRoster.ts
import { useCallback, useEffect, useRef } from 'react';
import { crewClient } from '../api/crewClient';
import { useCrewStore } from '../stores/crewStore';

export interface UseCrewRosterResult {
  reload: () => void;
}

export function useCrewRoster(intervalMs: number = 15_000): UseCrewRosterResult {
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  // Retry buttons need to trigger a fresh fetch that still respects the
  // "response landed after unmount" guard below. Mirrors useJobsPolling's
  // reload wiring (Plan 4A Task 5 review finding #1).
  const reloadRef = useRef<() => Promise<void>>(async () => {});

  useEffect(() => {
    // A response that lands after unmount must not write data into the store:
    // by then another route (or test) owns it, and this would overwrite fresher
    // state. Loading flags are still cleared so they can't stick on.
    let cancelled = false;
    const fetchOnce = async () => {
      useCrewStore.getState().markLoading(true);
      const result = await crewClient.getRoster();
      useCrewStore.getState().markLoading(false);
      if (cancelled) return;
      if (result.ok) {
        useCrewStore.getState().setRoster(result.crew);
      } else {
        useCrewStore.getState().markStale(true);
      }
    };

    reloadRef.current = fetchOnce;

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
      cancelled = true;
      reloadRef.current = async () => {};
      stop();
      document.removeEventListener('visibilitychange', onVisibility);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [intervalMs]);

  const reload = useCallback(() => {
    void reloadRef.current();
  }, []);

  return { reload };
}
