import { useEffect, useRef } from 'react';
import { adminHealthClient } from '../api/adminHealthClient';
import { useAdminHealthStore } from '../stores/adminHealthStore';
import { useAuthStore } from '../auth/authStore';

export function useAdminHealth(intervalMs: number = 15_000): void {
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const isAdmin = useAuthStore((s) => s.user?.role === 'admin');

  useEffect(() => {
    if (!isAdmin) return;
    const fetchOnce = async () => {
      useAdminHealthStore.getState().markLoading(true);
      const r = await adminHealthClient.get();
      useAdminHealthStore.getState().markLoading(false);
      if (r.ok) {
        useAdminHealthStore.getState().setData({ workers: r.workers, queue: r.queue });
      } else if (r.status !== 403) {
        // 403 is silent (non-admin); any other failure marks stale.
        useAdminHealthStore.getState().markStale(true);
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
  }, [intervalMs, isAdmin]);
}
