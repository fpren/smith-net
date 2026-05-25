// desktop/portal/src/console/components/time/TimeScreen.tsx
//
// The /console/time container -- a faithful mirror of the APK TimeTracking screen:
// a centered clock card (blinking ">" + CLOCKED IN/OUT status, a big live timer,
// "Started HH:MM - TYPE" + "@ Job / Task"), the TODAY 8-hour glyph bar, and the
// read-only today's-entries log. The switch keeps its instant feel; clocking IN
// opens the entry-type/job/task picker, clocking OUT is instant.
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
  const blink = onClock && Math.floor(now / 1000) % 2 === 0; // pulsing ">" like the APK

  return (
    <div className="h-full overflow-y-auto bg-console-bg p-4 font-mono text-console-text">
     <div className="mx-auto w-full sm:w-[80%]">
      <div className="text-xs uppercase tracking-wide text-console-text-muted mb-3">Time Clock</div>

      {/* Centered clock card -- mirrors the APK clock card. Scales down on phones. */}
      <div className="border border-console-border rounded-md bg-console-surface p-4 sm:p-6 mb-4 flex flex-col items-center gap-2">
        {/* Status with blinking indicator */}
        <div className="flex items-center gap-2">
          {onClock && (
            <span className="text-console-ok text-sm w-2 text-center" aria-hidden="true">
              {blink ? '>' : ' '}
            </span>
          )}
          <span className={`text-[11px] uppercase tracking-wide font-medium ${onClock ? 'text-console-ok' : 'text-console-text-muted'}`}>
            {onClock ? 'CLOCKED IN' : 'CLOCKED OUT'}
          </span>
        </div>

        {/* Big timer -- scales with the viewport */}
        <span
          className={`text-4xl sm:text-5xl tabular-nums ${onClock ? 'text-console-text' : 'text-console-text-muted'}`}
          aria-label="shift elapsed"
        >
          {onClock ? formatElapsed(currentElapsed) : '--:--:--'}
        </span>

        {/* Started + entry type + job/task (centered, leaf rows) */}
        {onClock && startedAt && (
          <div className="text-console-text-muted text-xs">
            Started {formatStart(startedAt)} - {entryType ?? 'regular'}
          </div>
        )}
        {onClock && jobTitle && (
          <div className="text-console-accent text-xs">
            @ {jobTitle}{taskTitle ? ` / ${taskTitle}` : ''}
          </div>
        )}

        {/* The mirror on/off switch: off -> pick entry type + job/task; on -> instant clock-out. */}
        <div className="mt-2">
          <ClockToggleButton
            onClock={onClock}
            busy={busy}
            onClick={() => (onClock ? void clockOut() : setShowIn(true))}
          />
        </div>
      </div>

      {/* TODAY summary bar, bracketed by separators like the APK. */}
      <div className="border-y border-console-border py-2">
        <DailySummaryBar secondsWorked={todaySeconds} />
      </div>

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
    </div>
  );
}
