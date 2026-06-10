// desktop/portal/src/console/components/header/useShiftToggle.ts
//
// Shared clock-in/out logic. Optimistic (APK-style): the new shift state is
// applied locally the instant clock-in/out fires, then the server round-trip
// reconciles on success or rolls back (with a toast) on failure. ClockInDialog
// drives clock-in (entry type + job/task); clock-out is instant.
import { presenceClient } from '../../api/presenceClient';
import { useCurrentShift } from '../../hooks/useCurrentShift';
import { useShiftStore } from '../../stores/shiftStore';
import { useToastStore } from '../../stores/toastStore';

export interface ClockInOpts {
  entryType: string;
  jobId?: string;
  jobTitle?: string;
  taskId?: string;
  taskTitle?: string;
}

export interface ShiftToggle {
  onClock: boolean;
  startedAt: string | null;
  entryType: string | null;
  jobTitle: string | null;
  taskTitle: string | null;
  busy: boolean;
  clockIn: (opts: ClockInOpts) => Promise<void>;
  clockOut: (reason?: string) => Promise<void>;
}

export function useShiftToggle(): ShiftToggle {
  const { shiftId, onClock, startedAt, entryType, jobTitle, taskTitle, refresh, setLocal } = useCurrentShift();
  // busy lives in the shared store so the header + /console/time can't double-submit.
  const busy = useShiftStore((s) => s.busy);
  const setBusy = (b: boolean) => useShiftStore.getState().setBusy(b);
  const pushToast = useToastStore((s) => s.push);

  async function clockIn(opts: ClockInOpts) {
    if (busy || onClock) return;
    const prev = { shiftId, onClock, startedAt, entryType, jobTitle, taskTitle };
    setBusy(true);
    setLocal({
      shiftId: null,
      onClock: true,
      startedAt: new Date().toISOString(),
      entryType: opts.entryType,
      jobTitle: opts.jobTitle ?? null,
      taskTitle: opts.taskTitle ?? null,
    });
    const result = await presenceClient.startShift('web', opts);
    if (result.ok && 'queued' in result) {
      // Offline: keep the optimistic clocked-in state; the outbox replays it.
      pushToast({ message: 'Clocked in offline — will sync when back online', tone: 'info', duration: 3000 });
    } else if (result.ok) {
      await refresh();
    } else {
      setLocal(prev);
      pushToast({ message: result.error || 'Clock in failed', tone: 'error', duration: 3000 });
    }
    setBusy(false);
  }

  async function clockOut(reason?: string) {
    if (busy || !onClock) return;
    const prev = { shiftId, onClock, startedAt, entryType, jobTitle, taskTitle };
    setBusy(true);
    setLocal({ shiftId: null, onClock: false, startedAt: null, entryType: null, jobTitle: null, taskTitle: null });
    const result = await presenceClient.endShift(reason);
    if (result.ok && 'queued' in result) {
      pushToast({ message: 'Clocked out offline — will sync when back online', tone: 'info', duration: 3000 });
    } else if (result.ok) {
      await refresh();
    } else {
      setLocal(prev);
      pushToast({ message: result.error || 'Clock out failed', tone: 'error', duration: 3000 });
    }
    setBusy(false);
  }

  return { onClock, startedAt, entryType, jobTitle, taskTitle, busy, clockIn, clockOut };
}
