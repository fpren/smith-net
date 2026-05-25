// desktop/portal/src/console/components/header/ClockToggleButton.tsx
//
// Presentational clock-in/out pill. State + the server round-trip live in
// useShiftToggle; this only renders the label and fires onClick. Shared by
// ClockButton (dashboard) and ShiftClock (console header).

interface Props {
  onClock: boolean;
  busy: boolean;
  onClick: () => void;
}

export function ClockToggleButton({ onClock, busy, onClick }: Props) {
  const label = busy
    ? (onClock ? 'clocking out…' : 'clocking in…')
    : (onClock ? '● ON CLOCK · clock out' : '○ OFF CLOCK · clock in');

  return (
    <button
      type="button"
      onClick={onClick}
      disabled={busy}
      className={
        'rounded-full text-xs font-mono px-3 py-1 border transition-colors ' +
        (onClock
          ? 'text-console-ok border-console-ok hover:bg-console-ok hover:text-console-bg'
          : 'text-console-text-muted border-console-border hover:text-console-accent hover:border-console-accent') +
        ' disabled:opacity-50'
      }
      aria-label={onClock ? 'Clock out' : 'Clock in'}
    >
      {label}
    </button>
  );
}
