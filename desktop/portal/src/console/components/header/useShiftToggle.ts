// desktop/portal/src/console/components/header/useShiftToggle.ts
//
// Shared clock-in/out logic. Wraps useCurrentShift + the start/end round-trip so
// ClockButton (dashboard) and ShiftClock (console header) behave identically. A
// single useShiftToggle() instance owns one shift state, so a clock-in updates
// everything it drives immediately (the header timer starts the moment you tap).
import { useState } from 'react';
import { presenceClient } from '../../api/presenceClient';
import { useCurrentShift } from '../../hooks/useCurrentShift';
import { useToastStore } from '../../stores/toastStore';

export interface ShiftToggle {
  onClock: boolean;
  startedAt: string | null;
  busy: boolean;
  toggle: () => Promise<void>;
}

export function useShiftToggle(): ShiftToggle {
  const { onClock, startedAt, refresh } = useCurrentShift();
  const [busy, setBusy] = useState(false);
  const pushToast = useToastStore((s) => s.push);

  async function toggle() {
    if (busy) return;
    setBusy(true);
    const result = onClock
      ? await presenceClient.endShift()
      : await presenceClient.startShift('web');
    setBusy(false);
    if (result.ok) {
      await refresh();
    } else {
      pushToast({
        message: result.error || (onClock ? 'Clock out failed' : 'Clock in failed'),
        tone: 'error',
        duration: 3000,
      });
    }
  }

  return { onClock, startedAt, busy, toggle };
}
