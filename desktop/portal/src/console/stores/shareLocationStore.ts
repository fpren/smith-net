import { create } from 'zustand';

interface State {
  isSharing: boolean;
  shiftId: string | null;
  isTransitioning: boolean;
  error: string | null;

  setSharing: (sharing: boolean, shiftId?: string | null) => void;
  setTransitioning: (b: boolean) => void;
  setError: (msg: string | null) => void;
  reset: () => void;
}

export const useShareLocationStore = create<State>((set) => ({
  isSharing: false,
  shiftId: null,
  isTransitioning: false,
  error: null,

  setSharing: (sharing, shiftId = null) =>
    set({ isSharing: sharing, shiftId: sharing ? shiftId : null, isTransitioning: false, error: null }),
  setTransitioning: (isTransitioning) => set({ isTransitioning, error: null }),
  setError: (error) => set({ error, isTransitioning: false }),
  reset: () => set({ isSharing: false, shiftId: null, isTransitioning: false, error: null }),
}));
