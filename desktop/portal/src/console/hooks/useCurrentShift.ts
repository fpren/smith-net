// desktop/portal/src/console/hooks/useCurrentShift.ts
//
// Reads the current shift from the shared shiftStore (so every consumer agrees)
// and polls GET /api/shifts/current to keep it fresh. Returns `refresh` (re-fetch
// now, e.g. after toggling) and `setLocal` (apply state immediately for an
// optimistic clock-in/out before the server confirms) -- both write the store.

import { useCallback, useEffect, useRef } from 'react';
import { presenceClient } from '../api/presenceClient';
import { useShiftStore, type ShiftSnapshot } from '../stores/shiftStore';

export interface CurrentShift extends ShiftSnapshot {
  /** Re-fetch /api/shifts/current immediately (e.g. after toggling). */
  refresh: () => Promise<void>;
  /** Apply shift state locally (optimistic update or rollback). */
  setLocal: (next: ShiftSnapshot) => void;
}

export function useCurrentShift(intervalMs: number = 30_000): CurrentShift {
  const shiftId = useShiftStore((s) => s.shiftId);
  const onClock = useShiftStore((s) => s.onClock);
  const startedAt = useShiftStore((s) => s.startedAt);
  const entryType = useShiftStore((s) => s.entryType);
  const jobTitle = useShiftStore((s) => s.jobTitle);
  const taskTitle = useShiftStore((s) => s.taskTitle);
  const cancelledRef = useRef(false);

  const refresh = useCallback(async () => {
    const r = await presenceClient.getCurrentShift();
    if (cancelledRef.current) return;
    if (r.ok) {
      useShiftStore.getState().setSnapshot({
        shiftId: r.shiftId,
        onClock: r.shiftId !== null,
        startedAt: r.startedAt,
        entryType: r.entryType,
        jobTitle: r.jobTitle,
        taskTitle: r.taskTitle,
      });
    }
  }, []);

  const setLocal = useCallback((next: ShiftSnapshot) => useShiftStore.getState().setSnapshot(next), []);

  useEffect(() => {
    cancelledRef.current = false;
    void refresh();
    const id = setInterval(refresh, intervalMs);
    return () => {
      cancelledRef.current = true;
      clearInterval(id);
    };
  }, [intervalMs, refresh]);

  return { shiftId, onClock, startedAt, entryType, jobTitle, taskTitle, refresh, setLocal };
}
