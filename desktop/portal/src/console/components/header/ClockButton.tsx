// desktop/portal/src/console/components/header/ClockButton.tsx
//
// Compact clock-in/clock-out toggle. Replaces the static OFF-CLOCK Chip
// that used to sit in AppHeader, and is now placed in ConsoleShell's
// share-location bar so it works on both desktop and mobile.
//
// Mirrors the Android time clock: tap toggles shift state, optimistic
// label flip, server round-trip via presenceClient, refresh state on
// success, toast on failure.

import { useState } from 'react';
import { presenceClient } from '../../api/presenceClient';
import { useCurrentShift } from '../../hooks/useCurrentShift';
import { useToastStore } from '../../stores/toastStore';

export function ClockButton() {
  const { onClock, refresh } = useCurrentShift();
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

  const label = busy
    ? (onClock ? 'clocking out…' : 'clocking in…')
    : (onClock ? '● ON CLOCK · clock out' : '○ OFF CLOCK · clock in');

  return (
    <button
      type="button"
      onClick={toggle}
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
