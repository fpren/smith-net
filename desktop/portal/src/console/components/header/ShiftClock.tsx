// desktop/portal/src/console/components/header/ShiftClock.tsx
//
// Console-header shift module -- one clock concept mirrored by state:
//   ON CLOCK : [ current-shift HH:MM:SS ]  [ clock-out pill ]   (number left, live)
//   OFF CLOCK: [ clock-in pill ]  [ day-total HH:MM:SS ]        (number right, static)
// The on-clock timer counts the current shift up from its start; the off-clock
// number is the total worked today (00:00:00 at the start of the day, into
// overtime). Timer + pill share one useShiftToggle instance, so tapping clock-in
// starts the timer immediately.
import { useEffect, useState } from 'react';
import { useShiftToggle } from './useShiftToggle';
import { useDayShiftTotal } from './useDayShiftTotal';
import { ClockToggleButton } from './ClockToggleButton';
import { formatElapsed } from './shiftFormat';
export { formatElapsed } from './shiftFormat';

export function ShiftClock() {
  // Task 5 will replace this quick toggle with the clock-in/out dialogs. For now,
  // keep the existing one-tap behaviour by deriving a toggle from the new
  // clockIn/clockOut API (regular entry type), so the header keeps compiling.
  const { onClock, startedAt, busy, clockIn, clockOut } = useShiftToggle();
  const toggle = () => (onClock ? clockOut() : clockIn({ entryType: 'regular' }));
  const dayTotalSeconds = useDayShiftTotal();
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    if (!onClock) return;
    setNow(Date.now());
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, [onClock]);

  const elapsed = onClock && startedAt ? (now - new Date(startedAt).getTime()) / 1000 : null;

  return (
    <div
      role="group"
      aria-label="shift"
      className="flex items-center gap-3 bg-sn-bg-base border border-sn-line rounded-md px-3 py-1.5"
    >
      {/* ON CLOCK: current shift on the left */}
      {elapsed !== null && startedAt && (
        <span
          className="text-sn-status-online text-sm tabular-nums whitespace-nowrap"
          style={{ fontFamily: 'var(--font-mono)' }}
          aria-label="shift elapsed"
        >
          {formatElapsed(elapsed)}
        </span>
      )}

      <ClockToggleButton onClock={onClock} busy={busy} onClick={toggle} />

      {/* OFF CLOCK: total worked today on the right (the mirror) */}
      {!onClock && (
        <span
          className="text-sn-ink text-sm tabular-nums whitespace-nowrap"
          style={{ fontFamily: 'var(--font-mono)' }}
          aria-label="day total"
        >
          {formatElapsed(dayTotalSeconds)}
        </span>
      )}
    </div>
  );
}
