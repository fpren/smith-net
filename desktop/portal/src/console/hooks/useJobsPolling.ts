// desktop/portal/src/console/hooks/useJobsPolling.ts
import { useEffect, useRef } from 'react';
import { jobsClient } from '../api/jobsClient';
import { useJobsStore } from '../stores/jobsStore';

type Scope = 'list' | { detail: string };

export function useJobsPolling(scope: Scope, intervalMs: number = 15_000): void {
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    const fetchOnce = async () => {
      if (scope === 'list') {
        useJobsStore.getState().markListLoading(true);
        const result = await jobsClient.list();
        useJobsStore.getState().markListLoading(false);
        if (result.ok) {
          useJobsStore.getState().setJobs(result.jobs);
        } else {
          useJobsStore.getState().markStale(true);
        }
      } else {
        const id = scope.detail;
        useJobsStore.getState().markDetailLoading(true);
        const result = await jobsClient.getById(id);
        useJobsStore.getState().markDetailLoading(false);
        if (result.ok) {
          useJobsStore.getState().setDetail(result.job, result.crew);
          useJobsStore.getState().markStale(false);
        } else {
          useJobsStore.getState().markStale(true);
        }
      }
    };

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
      stopInterval();
      document.removeEventListener('visibilitychange', onVisibility);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [intervalMs, scope === 'list' ? 'list' : scope.detail]);
}
