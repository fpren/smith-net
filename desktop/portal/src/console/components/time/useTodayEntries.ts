import { useEffect, useState } from 'react';
import { presenceClient, type TimeEntryRow } from '../../api/presenceClient';
import { startOfTodayMs } from '../header/shiftFormat';

// Today's full time-entry rows for the /console/time screen. Mirrors
// useDayShiftTotal's fetch but keeps every field (it needs the rows, not just
// the summed seconds). Re-fetches on the on/off-clock transition + a 30s poll.
export function useTodayEntries(onClock?: boolean, pollMs = 30_000): TimeEntryRow[] {
  const [rows, setRows] = useState<TimeEntryRow[]>([]);
  useEffect(() => {
    let cancelled = false;
    const refresh = async () => {
      const r = await presenceClient.getTodayShifts(startOfTodayMs());
      if (!cancelled && r.ok) setRows(r.shifts);
    };
    void refresh();
    const id = setInterval(() => void refresh(), pollMs);
    return () => { cancelled = true; clearInterval(id); };
  }, [pollMs, onClock]);
  return rows;
}
