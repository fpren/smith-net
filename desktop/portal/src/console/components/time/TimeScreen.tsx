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
import { LoadingState, ErrorState } from '../ui/StateViews';
import { formatElapsed, startOfTodayMs, sumClosedSecondsToday } from '../header/shiftFormat';

function formatStart(iso: string): string {
  return new Date(iso).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
}

export function TimeScreen() {
  const { onClock, startedAt, entryType, jobTitle, taskTitle, busy, clockIn, clockOut } = useShiftToggle();
  // TimeScreen is the trio's home: it's the container that owns the today's-
  // entries fetch (via useTodayEntries), so loading/error/retry are wired
  // here rather than in TimeRoute, which is a bare pass-through.
  const { entries, loading: entriesLoading, error: entriesError, reload: reloadEntries } = useTodayEntries(onClock);
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
    <div className="h-full overflow-y-auto bg-sn-bg-base p-4 font-mono text-sn-ink">
     <div className="mx-auto w-full sm:w-[80%]">
      <div className="text-xs uppercase tracking-wide text-sn-ink-muted mb-3">Time Clock</div>

      {/* Centered clock card -- mirrors the APK clock card. Scales down on phones. */}
      <div className="border border-sn-line rounded-md bg-sn-bg-panel p-4 sm:p-6 mb-4 flex flex-col items-center gap-2">
        {/* Status with blinking indicator */}
        <div className="flex items-center gap-2">
          {onClock && (
            <span className="text-sn-status-online text-sm w-2 text-center" aria-hidden="true">
              {blink ? '>' : ' '}
            </span>
          )}
          <span className={`text-[11px] uppercase tracking-wide font-medium ${onClock ? 'text-sn-status-online' : 'text-sn-ink-muted'}`}>
            {onClock ? 'CLOCKED IN' : 'CLOCKED OUT'}
          </span>
        </div>

        {/* Big timer -- scales with the viewport */}
        <span
          className={`text-4xl sm:text-5xl tabular-nums ${onClock ? 'text-sn-ink' : 'text-sn-ink-muted'}`}
          aria-label="shift elapsed"
        >
          {onClock ? formatElapsed(currentElapsed) : '--:--:--'}
        </span>

        {/* Started + entry type + job/task (centered, leaf rows) */}
        {onClock && startedAt && (
          <div className="text-sn-ink-muted text-xs">
            Started {formatStart(startedAt)} - {entryType ?? 'regular'}
          </div>
        )}
        {onClock && jobTitle && (
          <div className="text-sn-accent text-xs">
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
      <div className="border-y border-sn-line py-2">
        <DailySummaryBar secondsWorked={todaySeconds} />
      </div>

      <div className="mt-4">
        <div className="text-xs uppercase tracking-wide text-sn-ink-muted mb-2">Today's entries</div>
        {/* Precedence: loading -> error (no cached rows to fall back on) -> empty -> data.
            EmptyState lives inside TodayEntriesList (reuses its existing copy). */}
        {entriesLoading && entries.length === 0 ? (
          <LoadingState label="Loading entries" />
        ) : entriesError && entries.length === 0 ? (
          <ErrorState message="Couldn't load today's entries." onRetry={reloadEntries} />
        ) : (
          <TodayEntriesList entries={entries} />
        )}
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
