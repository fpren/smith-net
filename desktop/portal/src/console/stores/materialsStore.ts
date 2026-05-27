// desktop/portal/src/console/stores/materialsStore.ts
import { create } from 'zustand';
import type { Material } from '../api/materialsClient';

interface MaterialsStore {
  byJob: Record<string, Material[]>;
  setForJob: (jobId: string, items: Material[]) => void;
  upsert: (jobId: string, item: Material) => void;
  remove: (jobId: string, id: string) => void;
  clear: () => void;
}

export const useMaterialsStore = create<MaterialsStore>((set) => ({
  byJob: {},
  setForJob: (jobId, items) => set((s) => ({ byJob: { ...s.byJob, [jobId]: items } })),
  upsert: (jobId, item) => set((s) => {
    const list = s.byJob[jobId] ?? [];
    const idx = list.findIndex((m) => m.id === item.id);
    const next = idx === -1 ? [...list, item] : list.map((m, i) => (i === idx ? item : m));
    return { byJob: { ...s.byJob, [jobId]: next } };
  }),
  remove: (jobId, id) => set((s) => ({
    byJob: { ...s.byJob, [jobId]: (s.byJob[jobId] ?? []).filter((m) => m.id !== id) },
  })),
  clear: () => set({ byJob: {} }),
}));
