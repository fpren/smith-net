import { useCallback, useEffect, useRef } from 'react';
import { adminHealthClient } from '../api/adminHealthClient';
import { useAdminHealthStore } from '../stores/adminHealthStore';
import { useAuthStore } from '../auth/authStore';

export interface UseAdminHealthResult {
  reload: () => void;
}

export function useAdminHealth(intervalMs: number = 15_000): UseAdminHealthResult {
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  // Retry buttons need to trigger a fresh fetch that still respects the
  // "response landed after unmount" guard below. Mirrors useJobsPolling's /
  // useCrewRoster's reload wiring (Plan 4A Task 5 review finding #1).
  const reloadRef = useRef<() => Promise<void>>(async () => {});
  const isAdmin = useAuthStore((s) => s.user?.role === 'admin');

  useEffect(() => {
    if (!isAdmin) return;
    // A response that lands after unmount must not write into the store: by
    // then another route (or test) owns it, and this would overwrite fresher
    // state. Loading flags are still cleared so they can't stick on.
    let cancelled = false;
    const fetchOnce = async () => {
      useAdminHealthStore.getState().markLoading(true);
      const r = await adminHealthClient.get();
      useAdminHealthStore.getState().markLoading(false);
      if (cancelled) return;
      if (r.ok) {
        useAdminHealthStore.getState().setData({ workers: r.workers, queue: r.queue });
      } else if (r.status !== 403) {
        // 403 is silent (non-admin); any other failure marks stale.
        useAdminHealthStore.getState().markStale(true);
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
  }, [intervalMs, isAdmin]);

  const reload = useCallback(() => {
    void reloadRef.current();
  }, []);

  return { reload };
}
