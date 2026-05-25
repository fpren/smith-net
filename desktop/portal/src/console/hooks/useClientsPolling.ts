// desktop/portal/src/console/hooks/useClientsPolling.ts
import { useEffect, useRef } from 'react';
import { clientsClient } from '../api/clientsClient';
import { useClientsStore } from '../stores/clientsStore';

type Scope = 'list' | { detail: string };

export function useClientsPolling(scope: Scope, intervalMs: number = 15_000): void {
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  useEffect(() => {
    const fetchOnce = async () => {
      if (scope === 'list') {
        const r = await clientsClient.list();
        if (r.ok) useClientsStore.getState().setClients(r.clients);
        else useClientsStore.getState().markStale(true);
      } else {
        const r = await clientsClient.getById(scope.detail);
        if (r.ok) { useClientsStore.getState().setDetail(r.client, r.jobs); useClientsStore.getState().markStale(false); }
        else useClientsStore.getState().markStale(true);
      }
    };
    const start = () => { if (intervalRef.current === null) intervalRef.current = setInterval(fetchOnce, intervalMs); };
    const stop = () => { if (intervalRef.current !== null) { clearInterval(intervalRef.current); intervalRef.current = null; } };
    const onVis = () => { if (document.visibilityState === 'visible') { fetchOnce(); start(); } else stop(); };
    fetchOnce(); start();
    document.addEventListener('visibilitychange', onVis);
    return () => { stop(); document.removeEventListener('visibilitychange', onVis); };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [intervalMs, scope === 'list' ? 'list' : scope.detail]);
}
