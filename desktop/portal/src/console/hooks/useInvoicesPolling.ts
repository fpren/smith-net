// desktop/portal/src/console/hooks/useInvoicesPolling.ts
import { useEffect, useRef } from 'react';
import { invoicesClient } from '../api/invoicesClient';
import { useInvoicesStore } from '../stores/invoicesStore';

type Scope = 'list' | { detail: string };

export function useInvoicesPolling(scope: Scope, intervalMs: number = 15_000): void {
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    const fetchOnce = async () => {
      if (scope === 'list') {
        useInvoicesStore.getState().markListLoading(true);
        const r = await invoicesClient.list();
        useInvoicesStore.getState().markListLoading(false);
        if (r.ok) useInvoicesStore.getState().setInvoices(r.invoices);
        else useInvoicesStore.getState().markStale(true);
      } else {
        const id = scope.detail;
        useInvoicesStore.getState().markDetailLoading(true);
        const r = await invoicesClient.getById(id);
        useInvoicesStore.getState().markDetailLoading(false);
        if (r.ok) useInvoicesStore.getState().setDetail(r.invoice, r.lineItems);
        else useInvoicesStore.getState().markStale(true);
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
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [intervalMs, scope === 'list' ? 'list' : scope.detail]);
}
