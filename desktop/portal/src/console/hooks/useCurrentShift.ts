// desktop/portal/src/console/hooks/useCurrentShift.ts
//
// Polls GET /api/shifts/current every 30s so the ClockButton / ShiftClock can
// show ON/OFF CLOCK and the live shift timer for the logged-in user. Also
// returns a `refresh` so a caller that just toggled can re-fetch immediately.

import { useCallback, useEffect, useRef, useState } from 'react';
import { presenceClient } from '../api/presenceClient';

export interface CurrentShift {
  shiftId: string | null;
  /** True if the user has an open shift right now. */
  onClock: boolean;
  /** ISO start time of the open shift, or null when off the clock. */
  startedAt: string | null;
  /** Re-fetch /api/shifts/current immediately (e.g. after toggling). */
  refresh: () => Promise<void>;
}

export function useCurrentShift(intervalMs: number = 30_000): CurrentShift {
  const [state, setState] = useState<{ shiftId: string | null; onClock: boolean; startedAt: string | null }>({
    shiftId: null,
    onClock: false,
    startedAt: null,
  });
  const cancelledRef = useRef(false);

  const refresh = useCallback(async () => {
    const r = await presenceClient.getCurrentShift();
    if (cancelledRef.current) return;
    if (r.ok) {
      setState({ shiftId: r.shiftId, onClock: r.shiftId !== null, startedAt: r.startedAt });
    }
  }, []);

  useEffect(() => {
    cancelledRef.current = false;
    refresh();
    const id = setInterval(refresh, intervalMs);
    return () => {
      cancelledRef.current = true;
      clearInterval(id);
    };
  }, [intervalMs, refresh]);

  return { ...state, refresh };
}
