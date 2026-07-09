// desktop/portal/src/console/components/header/ClockToggleButton.tsx
//
// Presentational clock-in/out pill. State + the server round-trip live in
// useShiftToggle (optimistic), so this shows the current state label directly
// and is merely disabled while a toggle is in flight. Shared by ClockButton
// (dashboard) and ShiftClock (console header).

interface Props {
  onClock: boolean;
  busy: boolean;
  onClick: () => void;
}

export function ClockToggleButton({ onClock, busy, onClick }: Props) {
  const label = onClock ? '● ON CLOCK · clock out' : '○ OFF CLOCK · clock in';

  return (
    <button
      type="button"
      onClick={onClick}
      disabled={busy}
      className={
        'rounded-full text-xs font-mono whitespace-nowrap px-3 py-1 border transition-colors ' +
        (onClock
          ? 'text-sn-status-online border-sn-status-online hover:bg-sn-status-online hover:text-sn-bg-base'
          : 'text-sn-ink-muted border-sn-line hover:text-sn-accent hover:border-sn-accent') +
        ' disabled:opacity-50'
      }
      aria-label={onClock ? 'Clock out' : 'Clock in'}
    >
      {label}
    </button>
  );
}
