import { useCallback, useEffect, useRef } from 'react';
import { invoicesClient } from '../api/invoicesClient';
import { useInvoicesStore } from '../stores/invoicesStore';

type Scope = 'list' | { detail: string };

export interface UseInvoicesPollingResult {
  reload: () => void;
}

export function useInvoicesPolling(scope: Scope, intervalMs: number = 15_000): UseInvoicesPollingResult {
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
        useInvoicesStore.getState().markListLoading(true);
        const r = await invoicesClient.list();
        useInvoicesStore.getState().markListLoading(false);
        if (cancelled) return;
        if (r.ok) {
          useInvoicesStore.getState().setInvoices(r.invoices);
        } else {
          useInvoicesStore.getState().markListStale(true);
        }
      } else {
        const id = scope.detail;
        useInvoicesStore.getState().markDetailLoading(true);
        const r = await invoicesClient.getById(id);
        useInvoicesStore.getState().markDetailLoading(false);
        if (cancelled) return;
        if (r.ok) {
          useInvoicesStore.getState().setDetail(r.invoice, r.lineItems);
          useInvoicesStore.getState().markDetailStale(false);
        } else {
          useInvoicesStore.getState().markDetailStale(true);
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
