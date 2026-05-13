// desktop/portal/src/console/stores/crewStore.ts
import { create } from 'zustand';
import type { CrewEntry } from '../api/crewClient';

export type Availability = 'free' | 'busy';

interface CrewState {
  roster: CrewEntry[];
  isLoadingRoster: boolean;
  lastFetchedAt: number | null;
  isStale: boolean;

  setRoster: (roster: CrewEntry[]) => void;
  markLoading: (b: boolean) => void;
  markStale: (b: boolean) => void;
  availabilityOf: (id: string) => Availability;
  clear: () => void;
}

export const useCrewStore = create<CrewState>((set, get) => ({
  roster: [],
  isLoadingRoster: false,
  lastFetchedAt: null,
  isStale: false,

  setRoster: (roster) => set({ roster, lastFetchedAt: Date.now(), isStale: false }),
  markLoading: (isLoadingRoster) => set({ isLoadingRoster }),
  markStale: (isStale) => set({ isStale }),
  availabilityOf: (id) => {
    const entry = get().roster.find((e) => e.id === id);
    return entry && entry.activeJob !== null ? 'busy' : 'free';
  },
  clear: () => set({ roster: [], isLoadingRoster: false, lastFetchedAt: null, isStale: false }),
}));
