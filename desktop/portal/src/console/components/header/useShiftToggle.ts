// desktop/portal/src/console/components/header/useShiftToggle.ts
//
// Shared clock-in/out logic. Optimistic (APK-style): the new shift state is
// applied locally the instant the dialog is confirmed, then the server round-trip
// reconciles on success or rolls back (with a toast) on failure. ClockInDialog /
// ClockOutDialog drive these; ShiftClock + TimeScreen render from one instance.
import { useState } from 'react';
import { presenceClient } from '../../api/presenceClient';
import { useCurrentShift } from '../../hooks/useCurrentShift';
import { useToastStore } from '../../stores/toastStore';

export interface ClockInOpts {
  entryType: string;
  jobId?: string;
  jobTitle?: string;
}

export interface ShiftToggle {
  onClock: boolean;
  startedAt: string | null;
  entryType: string | null;
  jobTitle: string | null;
  busy: boolean;
  clockIn: (opts: ClockInOpts) => Promise<void>;
  clockOut: (reason?: string) => Promise<void>;
}

export function useShiftToggle(): ShiftToggle {
  const { shiftId, onClock, startedAt, entryType, jobTitle, refresh, setLocal } = useCurrentShift();
  const [busy, setBusy] = useState(false);
  const pushToast = useToastStore((s) => s.push);

  async function clockIn(opts: ClockInOpts) {
    if (busy || onClock) return;
    const prev = { shiftId, onClock, startedAt, entryType, jobTitle };
    setBusy(true);
    setLocal({
      shiftId: null,
      onClock: true,
      startedAt: new Date().toISOString(),
      entryType: opts.entryType,
      jobTitle: opts.jobTitle ?? null,
    });
    const result = await presenceClient.startShift('web', opts);
    if (result.ok) await refresh();
    else {
      setLocal(prev);
      pushToast({ message: result.error || 'Clock in failed', tone: 'error', duration: 3000 });
    }
    setBusy(false);
  }

  async function clockOut(reason?: string) {
    if (busy || !onClock) return;
    const prev = { shiftId, onClock, startedAt, entryType, jobTitle };
    setBusy(true);
    setLocal({ shiftId: null, onClock: false, startedAt: null, entryType: null, jobTitle: null });
    const result = await presenceClient.endShift(reason);
    if (result.ok) await refresh();
    else {
      setLocal(prev);
      pushToast({ message: result.error || 'Clock out failed', tone: 'error', duration: 3000 });
    }
    setBusy(false);
  }

  return { onClock, startedAt, entryType, jobTitle, busy, clockIn, clockOut };
}
