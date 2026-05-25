// desktop/portal/src/console/stores/shiftStore.ts
//
// One shared source of truth for the current shift, so the console header
// (ShiftClock) and the /console/time screen (TimeScreen) never disagree -- a
// clock-in/out from either updates both instantly, and `busy` is shared so they
// can't double-submit. (Fixes the dual-useShiftToggle split-brain, review C1.)
import { create } from 'zustand';

export interface ShiftSnapshot {
  shiftId: string | null;
  onClock: boolean;
  startedAt: string | null;
  entryType: string | null;
  jobTitle: string | null;
  taskTitle: string | null;
}

const EMPTY: ShiftSnapshot = {
  shiftId: null,
  onClock: false,
  startedAt: null,
  entryType: null,
  jobTitle: null,
  taskTitle: null,
};

interface ShiftStore extends ShiftSnapshot {
  busy: boolean;
  setSnapshot: (s: ShiftSnapshot) => void;
  setBusy: (b: boolean) => void;
  reset: () => void;
}

export const useShiftStore = create<ShiftStore>((set) => ({
  ...EMPTY,
  busy: false,
  setSnapshot: (s) => set(s),
  setBusy: (busy) => set({ busy }),
  reset: () => set({ ...EMPTY, busy: false }),
}));
