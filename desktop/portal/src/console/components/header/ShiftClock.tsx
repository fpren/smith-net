// desktop/portal/src/console/components/header/ShiftClock.tsx
//
// Console-header shift module. When on the clock, shows a live HH:MM:SS timer
// counting up from the shift's start (ticking every second) plus the clock-in
// time, then the clock-out pill -- mirroring the APK TimeTrackingScreen. When
// off the clock, shows only the clock-in pill. The timer and the pill share one
// useShiftToggle instance, so tapping clock-in starts the timer immediately.
import { useEffect, useState } from 'react';
import { useShiftToggle } from './useShiftToggle';
import { ClockToggleButton } from './ClockToggleButton';

/** Format a non-negative elapsed-seconds count as HH:MM:SS (zero-padded). */
export function formatElapsed(totalSeconds: number): string {
  const s = Math.max(0, Math.floor(totalSeconds));
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${pad(Math.floor(s / 3600))}:${pad(Math.floor((s % 3600) / 60))}:${pad(s % 60)}`;
}

function formatStart(iso: string): string {
  return new Date(iso).toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' });
}

export function ShiftClock() {
  const { onClock, startedAt, busy, toggle } = useShiftToggle();
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    if (!onClock) return;
    setNow(Date.now()); // immediate, so the timer is correct the instant we clock in
    const id = setInterval(() => setNow(Date.now()), 1000);
    return () => clearInterval(id);
  }, [onClock]);

  const elapsed = onClock && startedAt ? (now - new Date(startedAt).getTime()) / 1000 : null;

  return (
    <div
      role="group"
      aria-label="shift"
      className="flex items-center gap-3 bg-console-bg border border-console-border rounded-md px-3 py-1.5"
    >
      {elapsed !== null && startedAt && (
        <div className="flex items-baseline gap-2">
          <span
            className="text-console-ok text-sm tabular-nums"
            style={{ fontFamily: 'var(--font-mono)' }}
            aria-label="shift elapsed"
          >
            {formatElapsed(elapsed)}
          </span>
          <span className="text-console-text-muted text-[10px] whitespace-nowrap">
            started {formatStart(startedAt)}
          </span>
          <span className="text-console-border" aria-hidden="true">|</span>
        </div>
      )}
      <ClockToggleButton onClock={onClock} busy={busy} onClick={toggle} />
    </div>
  );
}
