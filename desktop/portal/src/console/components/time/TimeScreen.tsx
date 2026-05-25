import { useEffect, useState } from 'react';
import { useShiftToggle } from '../header/useShiftToggle';
import { useTodayEntries } from './useTodayEntries';
import { ClockInDialog } from './ClockInDialog';
import { ClockOutDialog } from './ClockOutDialog';
import { DailySummaryBar } from './DailySummaryBar';
import { TodayEntriesList } from './TodayEntriesList';
import { formatElapsed, startOfTodayMs, sumClosedSecondsToday } from '../header/shiftFormat';
import { Button } from '../ui/Button';

export function TimeScreen() {
  const { onClock, startedAt, entryType, jobTitle, busy, clockIn, clockOut } = useShiftToggle();
  const entries = useTodayEntries(onClock);
  const [now, setNow] = useState(() => Date.now());
  const [showIn, setShowIn] = useState(false);
  const [showOut, setShowOut] = useState(false);

  useEffect(() => {
    if (!onClock) return;
    setNow(Date.now());
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, [onClock]);

  const currentElapsed = onClock && startedAt ? (now - new Date(startedAt).getTime()) / 1000 : 0;
  const todaySeconds = sumClosedSecondsToday(entries, startOfTodayMs()) + currentElapsed;

  return (
    <div className="h-full overflow-y-auto bg-console-bg p-4 font-mono text-console-text">
      <div className="text-xs uppercase tracking-wide text-console-text-muted mb-3">Time Clock</div>

      <div className="flex items-center gap-3 mb-2">
        <span className="text-2xl tabular-nums" aria-label="shift elapsed">
          {onClock ? formatElapsed(currentElapsed) : '--:--:--'}
        </span>
        <span className={onClock ? 'text-console-ok text-sm' : 'text-console-text-muted text-sm'}>
          {onClock ? 'ON CLOCK' : 'OFF CLOCK'}
        </span>
        {onClock && entryType && (
          <span className="text-console-text-muted text-xs uppercase">
            {entryType}{jobTitle ? ` @ ${jobTitle}` : ''}
          </span>
        )}
      </div>

      <div className="mb-4">
        {onClock
          ? <Button variant="secondary" disabled={busy} onClick={() => setShowOut(true)}>clock out</Button>
          : <Button disabled={busy} onClick={() => setShowIn(true)}>clock in</Button>}
      </div>

      <DailySummaryBar secondsWorked={todaySeconds} />

      <div className="mt-4">
        <div className="text-xs uppercase tracking-wide text-console-text-muted mb-2">Today's entries</div>
        <TodayEntriesList entries={entries} />
      </div>

      <ClockInDialog
        open={showIn}
        onClose={() => setShowIn(false)}
        onConfirm={(opts) => { setShowIn(false); void clockIn(opts); }}
      />
      <ClockOutDialog
        open={showOut}
        onClose={() => setShowOut(false)}
        onConfirm={(reason) => { setShowOut(false); void clockOut(reason); }}
      />
    </div>
  );
}
