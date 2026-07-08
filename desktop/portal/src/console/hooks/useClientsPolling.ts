// desktop/portal/src/console/hooks/useClientsPolling.ts
import { useCallback, useEffect, useRef } from 'react';
import { clientsClient } from '../api/clientsClient';
import { useClientsStore } from '../stores/clientsStore';

type Scope = 'list' | { detail: string };

export interface UseClientsPollingResult {
  reload: () => void;
}

export function useClientsPolling(scope: Scope, intervalMs: number = 15_000): UseClientsPollingResult {
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);
  // Retry buttons need to trigger a fresh fetch that still respects the
  // "response landed after unmount" guard below. Mirrors useJobsPolling's
  // reload wiring (Plan 4A Task 5 review finding #1): the effect stashes its
  // own fetchOnce in a ref so a stable useCallback can invoke it later.
  const reloadRef = useRef<() => Promise<void>>(async () => {});

  useEffect(() => {
    // A response that lands after unmount must not write data into the store:
    // by then another route (or test) owns it, and this would overwrite fresher
    // state. Loading flags are still cleared so they can't stick on.
    let cancelled = false;
    const fetchOnce = async () => {
      if (scope === 'list') {
        useClientsStore.getState().markListLoading(true);
        const r = await clientsClient.list();
        useClientsStore.getState().markListLoading(false);
        if (cancelled) return;
        if (r.ok) {
          useClientsStore.getState().setClients(r.clients);
        } else {
          useClientsStore.getState().markListStale(true);
        }
      } else {
        const id = scope.detail;
        useClientsStore.getState().markDetailLoading(true);
        const r = await clientsClient.getById(id);
        useClientsStore.getState().markDetailLoading(false);
        if (cancelled) return;
        if (r.ok) {
          useClientsStore.getState().setDetail(r.client, r.jobs);
          useClientsStore.getState().markDetailStale(false);
        } else {
          useClientsStore.getState().markDetailStale(true);
        }
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
  }, [intervalMs, scope === 'list' ? 'list' : scope.detail]);

  const reload = useCallback(() => {
    void reloadRef.current();
  }, []);

  return { reload };
}
