// desktop/portal/src/console/hooks/useNotificationsPolling.ts
import { useCallback, useEffect, useRef } from 'react';
import { notificationsClient } from '../api/notificationsClient';
import { useNotificationsStore } from '../stores/notificationsStore';

export interface UseNotificationsPollingResult {
  reload: () => void;
}

export function useNotificationsPolling(intervalMs: number = 15_000): UseNotificationsPollingResult {
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  // Retry buttons need to trigger a fresh fetch that still respects the
  // "response landed after unmount" guard below. Mirrors useJobsPolling's /
  // useCrewRoster's reload wiring (Plan 4A Task 5 review finding #1).
  const reloadRef = useRef<() => Promise<void>>(async () => {});

  useEffect(() => {
    let cancelled = false;
    const fetchOnce = async () => {
      useNotificationsStore.getState().markLoading(true);
      const r = await notificationsClient.list();
      useNotificationsStore.getState().markLoading(false);
      if (cancelled) return;
      if (r.ok) useNotificationsStore.getState().setNotifications(r.notifications, r.unreadCount);
      else useNotificationsStore.getState().markStale(true);
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
  }, [intervalMs]);

  const reload = useCallback(() => {
    void reloadRef.current();
  }, []);

  return { reload };
}
