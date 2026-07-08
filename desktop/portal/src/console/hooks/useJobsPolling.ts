// desktop/portal/src/console/hooks/useJobsPolling.ts
import { useCallback, useEffect, useRef } from 'react';
import { jobsClient } from '../api/jobsClient';
import { useJobsStore } from '../stores/jobsStore';

type Scope = 'list' | { detail: string };

export interface UseJobsPollingResult {
  reload: () => void;
}

export function useJobsPolling(scope: Scope, intervalMs: number = 15_000): UseJobsPollingResult {
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  // Retry buttons need to trigger a fresh fetch that still respects the
  // "response landed after unmount" guard below. A function created inside
  // the effect can't be returned directly from the hook, so it's stashed in
  // a ref: the effect (re)assigns it to the closure's own `fetchOnce` on
  // mount and resets it to a no-op on cleanup. `reload` is a stable
  // useCallback wrapper so it's a valid effect dep / prop reference.
  const reloadRef = useRef<() => Promise<void>>(async () => {});

  useEffect(() => {
    // A response that lands after unmount must not write data into the store:
    // by then another route (or test) owns it, and this would overwrite fresher
    // state. Loading flags are still cleared so they can't stick on.
    let cancelled = false;
    const fetchOnce = async () => {
      if (scope === 'list') {
        useJobsStore.getState().markListLoading(true);
        const result = await jobsClient.list();
        useJobsStore.getState().markListLoading(false);
        if (cancelled) return;
        if (result.ok) {
          useJobsStore.getState().setJobs(result.jobs);
        } else {
          useJobsStore.getState().markListStale(true);
        }
      } else {
        const id = scope.detail;
        useJobsStore.getState().markDetailLoading(true);
        const result = await jobsClient.getById(id);
        useJobsStore.getState().markDetailLoading(false);
        if (cancelled) return;
        if (result.ok) {
          useJobsStore.getState().setDetail(result.job, result.crew);
          useJobsStore.getState().markDetailStale(false);
        } else {
          useJobsStore.getState().markDetailStale(true);
        }
      }
    };

    reloadRef.current = fetchOnce;

    const startInterval = () => {
      if (intervalRef.current !== null) return;
      intervalRef.current = setInterval(fetchOnce, intervalMs);
    };

    const stopInterval = () => {
      if (intervalRef.current !== null) {
        clearInterval(intervalRef.current);
        intervalRef.current = null;
      }
    };

    const onVisibility = () => {
      if (document.visibilityState === 'visible') {
        fetchOnce();
        startInterval();
      } else {
        stopInterval();
      }
    };

    // Initial: kick fetch + start interval
    fetchOnce();
    startInterval();
    document.addEventListener('visibilitychange', onVisibility);

    return () => {
      cancelled = true;
      reloadRef.current = async () => {};
      stopInterval();
      document.removeEventListener('visibilitychange', onVisibility);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [intervalMs, scope === 'list' ? 'list' : scope.detail]);

  const reload = useCallback(() => {
    void reloadRef.current();
  }, []);

  return { reload };
}
