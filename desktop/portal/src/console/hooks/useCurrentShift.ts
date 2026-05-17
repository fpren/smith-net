// desktop/portal/src/console/hooks/useCurrentShift.ts
//
// Polls GET /api/shifts/current every 30s so AppHeader can show
// "ON CLOCK · <source>" or "OFF CLOCK" for the logged-in user.

import { useEffect, useState } from 'react';
import { presenceClient } from '../api/presenceClient';

export interface CurrentShift {
  shiftId: string | null;
  /** True if the user has an open shift right now. */
  onClock: boolean;
}

export function useCurrentShift(intervalMs: number = 30_000): CurrentShift {
  const [state, setState] = useState<CurrentShift>({ shiftId: null, onClock: false });

  useEffect(() => {
    let cancelled = false;
    const fetchOnce = async () => {
      const r = await presenceClient.getCurrentShift();
      if (cancelled) return;
      if (r.ok) {
        setState({ shiftId: r.shiftId, onClock: r.shiftId !== null });
      }
    };
    fetchOnce();
    const id = setInterval(fetchOnce, intervalMs);
    return () => { cancelled = true; clearInterval(id); };
  }, [intervalMs]);

  return state;
}
