// desktop/portal/src/console/hooks/useCurrentShift.ts
//
// Polls GET /api/shifts/current every 30s so the ClockButton / ShiftClock can
// show ON/OFF CLOCK and the live shift timer for the logged-in user. Returns
// `refresh` (re-fetch now, e.g. after toggling) and `setLocal` (apply state
// immediately for an optimistic clock-in/out before the server confirms).

import { useCallback, useEffect, useRef, useState } from 'react';
import { presenceClient } from '../api/presenceClient';

interface ShiftSnapshot {
  shiftId: string | null;
  onClock: boolean;
  startedAt: string | null;
  entryType: string | null;
  jobTitle: string | null;
  taskTitle: string | null;
}

export interface CurrentShift extends ShiftSnapshot {
  /** Re-fetch /api/shifts/current immediately (e.g. after toggling). */
  refresh: () => Promise<void>;
  /** Apply shift state locally (optimistic update or rollback). */
  setLocal: (next: ShiftSnapshot) => void;
}

export function useCurrentShift(intervalMs: number = 30_000): CurrentShift {
  const [state, setState] = useState<ShiftSnapshot>({
    shiftId: null,
    onClock: false,
    startedAt: null,
    entryType: null,
    jobTitle: null,
    taskTitle: null,
  });
  const cancelledRef = useRef(false);

  const refresh = useCallback(async () => {
    const r = await presenceClient.getCurrentShift();
    if (cancelledRef.current) return;
    if (r.ok) {
      setState({
        shiftId: r.shiftId,
        onClock: r.shiftId !== null,
        startedAt: r.startedAt,
        entryType: r.entryType,
        jobTitle: r.jobTitle,
        taskTitle: r.taskTitle,
      });
    }
  }, []);

  const setLocal = useCallback((next: ShiftSnapshot) => setState(next), []);

  useEffect(() => {
    cancelledRef.current = false;
    refresh();
    const id = setInterval(refresh, intervalMs);
    return () => {
      cancelledRef.current = true;
      clearInterval(id);
    };
  }, [intervalMs, refresh]);

  return { ...state, refresh, setLocal };
}
