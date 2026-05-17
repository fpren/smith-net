// desktop/portal/src/console/hooks/useCurrentShift.ts
//
// Polls GET /api/shifts/current every 30s so the AppHeader / ClockButton
// can show "ON CLOCK" or "OFF CLOCK" for the logged-in user. The hook also
// returns a `refresh` function so a caller that just POSTed shifts/start
// or shifts/end can re-fetch immediately without waiting for the 30s tick.

import { useCallback, useEffect, useRef, useState } from 'react';
import { presenceClient } from '../api/presenceClient';

export interface CurrentShift {
  shiftId: string | null;
  /** True if the user has an open shift right now. */
  onClock: boolean;
  /** Re-fetch /api/shifts/current immediately (e.g. after toggling). */
  refresh: () => Promise<void>;
}

export function useCurrentShift(intervalMs: number = 30_000): CurrentShift {
  const [state, setState] = useState<{ shiftId: string | null; onClock: boolean }>({
    shiftId: null,
    onClock: false,
  });
  const cancelledRef = useRef(false);

  const refresh = useCallback(async () => {
    const r = await presenceClient.getCurrentShift();
    if (cancelledRef.current) return;
    if (r.ok) {
      setState({ shiftId: r.shiftId, onClock: r.shiftId !== null });
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
