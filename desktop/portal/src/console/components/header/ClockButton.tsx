// desktop/portal/src/console/components/header/ClockButton.tsx
//
// Compact clock-in/clock-out toggle (used by the dashboard ShiftCard). Thin
// wrapper around useShiftToggle (state + server round-trip) + ClockToggleButton
// (the pill). The console header uses ShiftClock instead, which adds the live
// shift timer.
import { useShiftToggle } from './useShiftToggle';
import { ClockToggleButton } from './ClockToggleButton';

export function ClockButton() {
  const { onClock, busy, toggle } = useShiftToggle();
  return <ClockToggleButton onClock={onClock} busy={busy} onClick={toggle} />;
}
