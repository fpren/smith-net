// desktop/portal/src/console/hooks/useJobsPolling.ts
import { useEffect, useRef } from 'react';
import { jobsClient } from '../api/jobsClient';
import { useJobsStore } from '../stores/jobsStore';

type Scope = 'list' | { detail: string };

// Standalone reload functions used by the retry buttons on JobsListRoute /
// JobDetailRoute's ErrorState. These write to the store unconditionally on
// resolve — safe for a user-triggered retry, unlike the interval-driven
// fetchOnce below which guards against a response landing after unmount.
export async function reloadJobsList(): Promise<void> {
  useJobsStore.getState().markListLoading(true);
  try {
    const result = await jobsClient.list();
    if (result.ok) {
      useJobsStore.getState().setJobs(result.jobs);
    } else {
      useJobsStore.getState().markStale(true);
    }
  } finally {
    useJobsStore.getState().markListLoading(false);
  }
}

export async function reloadJobDetail(id: string): Promise<void> {
  useJobsStore.getState().markDetailLoading(true);
  try {
    const result = await jobsClient.getById(id);
    if (result.ok) {
      useJobsStore.getState().setDetail(result.job, result.crew);
      useJobsStore.getState().markStale(false);
    } else {
      useJobsStore.getState().markStale(true);
    }
  } finally {
    useJobsStore.getState().markDetailLoading(false);
  }
}

export function useJobsPolling(scope: Scope, intervalMs: number = 15_000): void {
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

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
          useJobsStore.getState().markStale(true);
        }
      } else {
        const id = scope.detail;
        useJobsStore.getState().markDetailLoading(true);
        const result = await jobsClient.getById(id);
        useJobsStore.getState().markDetailLoading(false);
        if (cancelled) return;
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
      cancelled = true;
      stopInterval();
      document.removeEventListener('visibilitychange', onVisibility);
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [intervalMs, scope === 'list' ? 'list' : scope.detail]);
}
