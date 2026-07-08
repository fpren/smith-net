import { useCallback, useEffect, useRef, useState } from 'react';
import { presenceClient, type TimeEntryRow } from '../../api/presenceClient';
import { startOfTodayMs } from '../header/shiftFormat';

export interface UseTodayEntriesResult {
  entries: TimeEntryRow[];
  loading: boolean;
  error: boolean;
  reload: () => void;
}

// Today's full time-entry rows for the /console/time screen. Mirrors
// useDayShiftTotal's fetch but keeps every field (it needs the rows, not just
// the summed seconds). Re-fetches on the on/off-clock transition + a 30s poll.
// Exposes loading/error/reload so TimeScreen (the fetch's actual owner) can
// wire the state-view trio -- mirrors useJobsPolling/useInvoicesPolling's
// cancelled-guard + reload-via-ref shape (Plan 4A sweep).
export function useTodayEntries(onClock?: boolean, pollMs = 30_000): UseTodayEntriesResult {
  const [rows, setRows] = useState<TimeEntryRow[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);
  const reloadRef = useRef<() => Promise<void>>(async () => {});

  useEffect(() => {
    let cancelled = false;
    const refresh = async () => {
      setLoading(true);
      const r = await presenceClient.getTodayShifts(startOfTodayMs());
      if (cancelled) return;
      setLoading(false);
      if (r.ok) {
        setRows(r.shifts);
        setError(false);
      } else {
        setError(true);
      }
    };
    reloadRef.current = refresh;
    void refresh();
    const id = setInterval(() => void refresh(), pollMs);
    return () => {
      cancelled = true;
      reloadRef.current = async () => {};
      clearInterval(id);
    };
  }, [pollMs, onClock]);

  const reload = useCallback(() => {
    void reloadRef.current();
  }, []);

  return { entries: rows, loading, error, reload };
}
