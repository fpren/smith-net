// desktop/portal/src/console/components/time/TimeScreen.tsx
//
// The /console/time container -- mirrors the APK clock screen: a framed clock
// card with the same mirror on/off switch as the header, then the 8-hour daily
// bar and the read-only today's-entries log. The switch keeps its instant feel;
// clocking IN opens the entry-type/job picker first (like the APK clock-in
// dialog), clocking OUT is instant.
import { useEffect, useState } from 'react';
import { useShiftToggle } from '../header/useShiftToggle';
import { useTodayEntries } from './useTodayEntries';
import { ClockInDialog } from './ClockInDialog';
import { ClockToggleButton } from '../header/ClockToggleButton';
import { DailySummaryBar } from './DailySummaryBar';
import { TodayEntriesList } from './TodayEntriesList';
import { formatElapsed, startOfTodayMs, sumClosedSecondsToday } from '../header/shiftFormat';

function formatStart(iso: string): string {
  return new Date(iso).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
}

export function TimeScreen() {
  const { onClock, startedAt, entryType, jobTitle, taskTitle, busy, clockIn, clockOut } = useShiftToggle();
  const entries = useTodayEntries(onClock);
  const [now, setNow] = useState(() => Date.now());
  const [showIn, setShowIn] = useState(false);

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

      {/* Clock container -- the framed clock card (mirrors the APK clock card). */}
      <div className="border border-console-border rounded-md bg-console-surface p-4 mb-4 flex flex-col gap-3">
        <div className="flex items-baseline gap-3">
          <span className="text-3xl tabular-nums" aria-label="shift elapsed">
            {onClock ? formatElapsed(currentElapsed) : '--:--:--'}
          </span>
          <span className={onClock ? 'text-console-ok text-sm' : 'text-console-text-muted text-sm'}>
            {onClock ? 'ON CLOCK' : 'OFF CLOCK'}
          </span>
        </div>

        {onClock && startedAt && (
          <div className="text-console-text-muted text-xs uppercase whitespace-nowrap">
            {(entryType ?? 'regular')}{jobTitle ? ` @ ${jobTitle}` : ''}{jobTitle && taskTitle ? ` / ${taskTitle}` : ''} · started {formatStart(startedAt)}
          </div>
        )}

        {/* The mirror on/off switch: off -> pick entry type + job first; on -> instant clock-out. */}
        <div>
          <ClockToggleButton
            onClock={onClock}
            busy={busy}
            onClick={() => (onClock ? void clockOut() : setShowIn(true))}
          />
        </div>
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
    </div>
  );
}
