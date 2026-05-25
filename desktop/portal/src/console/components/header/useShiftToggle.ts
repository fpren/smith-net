// desktop/portal/src/console/components/header/useShiftToggle.ts
//
// Shared clock-in/out logic. Optimistic (APK-style): the new shift state is
// applied locally the instant you tap -- so the header timer starts/stops
// immediately -- then the server round-trip runs in the background, reconciling
// on success or rolling back (with a toast) on failure. ClockButton (dashboard)
// and ShiftClock (console header) both use this; each renders from one instance.
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
  const { shiftId, onClock, startedAt, refresh, setLocal } = useCurrentShift();
  const [busy, setBusy] = useState(false);
  const pushToast = useToastStore((s) => s.push);

  async function toggle() {
    if (busy) return;
    const wasOnClock = onClock;
    const prev = { shiftId, onClock, startedAt };
    setBusy(true);

    // Optimistic: reflect the new state immediately, before the server replies,
    // so the timer starts (or stops) the instant you tap -- like the APK.
    setLocal(
      wasOnClock
        ? { shiftId: null, onClock: false, startedAt: null }
        : { shiftId: null, onClock: true, startedAt: new Date().toISOString() },
    );

    const result = wasOnClock
      ? await presenceClient.endShift()
      : await presenceClient.startShift('web');

    if (result.ok) {
      await refresh(); // reconcile with the authoritative server shift
    } else {
      setLocal(prev); // roll back the optimistic change
      pushToast({
        message: result.error || (wasOnClock ? 'Clock out failed' : 'Clock in failed'),
        tone: 'error',
        duration: 3000,
      });
    }
    setBusy(false); // re-enable only after the state is reconciled / rolled back
  }

  return { onClock, startedAt, busy, toggle };
}
