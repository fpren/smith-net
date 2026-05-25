// desktop/portal/src/console/components/header/useDayShiftTotal.ts
//
// Total time worked today (the off-clock mirror number): sum of today's CLOSED
// shifts, clamped to the user's local day. No live tick -- off the clock nothing
// runs, so it is static until a shift completes or the day rolls over. Refetches
// on mount, on each on/off-clock transition (clock-out adds a completed shift),
// and on a 30s poll (which also resets it to 00:00:00 after local midnight).
import { useEffect, useState } from 'react';
import { presenceClient } from '../../api/presenceClient';
import { useShiftStore } from '../../stores/shiftStore';
import { startOfTodayMs, sumClosedSecondsToday } from './shiftFormat';

type ShiftRow = { startedAt: string | null; endedAt: string | null };

export function useDayShiftTotal(pollMs: number = 30_000): number {
  // Refetch on the on/off-clock transition (clock-out adds a completed shift);
  // onClock comes from the shared store (the single source of truth).
  const onClock = useShiftStore((s) => s.onClock);
  const [shifts, setShifts] = useState<ShiftRow[]>([]);

  useEffect(() => {
    let cancelled = false;
    const refresh = async () => {
      const r = await presenceClient.getTodayShifts(startOfTodayMs());
      if (!cancelled && r.ok) setShifts(r.shifts);
    };
    void refresh();
    const id = setInterval(() => void refresh(), pollMs);
    return () => {
      cancelled = true;
      clearInterval(id);
    };
  }, [pollMs, onClock]);

  return sumClosedSecondsToday(shifts, startOfTodayMs());
}
