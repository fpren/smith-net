import { create } from 'zustand';
import type { CrewPosition } from '../api/crewPositionsClient';

interface State {
  positions: CrewPosition[];
  isLoading: boolean;
  isStale: boolean;
  lastFetchedAt: number | null;

  setPositions: (p: CrewPosition[]) => void;
  markLoading: (b: boolean) => void;
  markStale: (b: boolean) => void;
  clear: () => void;
}

export const useCrewPositionsStore = create<State>((set) => ({
  positions: [],
  isLoading: false,
  isStale: false,
  lastFetchedAt: null,

  setPositions: (positions) => set({ positions, lastFetchedAt: Date.now(), isStale: false }),
  markLoading: (isLoading) => set({ isLoading }),
  markStale: (isStale) => set({ isStale }),
  clear: () => set({ positions: [], isLoading: false, isStale: false, lastFetchedAt: null }),
}));
