// desktop/portal/src/console/hooks/useNotificationsPolling.ts
import { useEffect, useRef } from 'react';
import { notificationsClient } from '../api/notificationsClient';
import { useNotificationsStore } from '../stores/notificationsStore';

export function useNotificationsPolling(intervalMs: number = 15_000): void {
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    let cancelled = false;
    const fetchOnce = async () => {
      const r = await notificationsClient.list();
      if (cancelled) return;
      if (r.ok) useNotificationsStore.getState().setNotifications(r.notifications, r.unreadCount);
      else useNotificationsStore.getState().markStale(true);
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
      cancelled = true;
      stop();
      document.removeEventListener('visibilitychange', onVisibility);
    };
  }, [intervalMs]);
}
