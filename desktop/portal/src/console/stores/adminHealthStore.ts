import { create } from 'zustand';
import type { HealthData } from '../api/adminHealthClient';

interface State {
  data: HealthData | null;
  isLoading: boolean;
  isStale: boolean;
  lastFetchedAt: number | null;

  setData: (d: HealthData) => void;
  markLoading: (b: boolean) => void;
  markStale: (b: boolean) => void;
  clear: () => void;
}

export const useAdminHealthStore = create<State>((set) => ({
  data: null,
  isLoading: false,
  isStale: false,
  lastFetchedAt: null,

  setData: (data) => set({ data, lastFetchedAt: Date.now(), isStale: false }),
  markLoading: (isLoading) => set({ isLoading }),
  markStale: (isStale) => set({ isStale }),
  clear: () => set({ data: null, isLoading: false, isStale: false, lastFetchedAt: null }),
}));
