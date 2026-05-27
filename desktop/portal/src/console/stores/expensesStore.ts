// desktop/portal/src/console/stores/expensesStore.ts
import { create } from 'zustand';
import type { Expense } from '../api/expensesClient';

interface ExpensesStore {
  byJob: Record<string, Expense[]>;
  setForJob: (jobId: string, items: Expense[]) => void;
  upsert: (jobId: string, item: Expense) => void;
  remove: (jobId: string, id: string) => void;
  clear: () => void;
}

export const useExpensesStore = create<ExpensesStore>((set) => ({
  byJob: {},
  setForJob: (jobId, items) => set((s) => ({ byJob: { ...s.byJob, [jobId]: items } })),
  upsert: (jobId, item) => set((s) => {
    const list = s.byJob[jobId] ?? [];
    const idx = list.findIndex((e) => e.id === item.id);
    const next = idx === -1 ? [...list, item] : list.map((e, i) => (i === idx ? item : e));
    return { byJob: { ...s.byJob, [jobId]: next } };
  }),
  remove: (jobId, id) => set((s) => ({
    byJob: { ...s.byJob, [jobId]: (s.byJob[jobId] ?? []).filter((e) => e.id !== id) },
  })),
  clear: () => set({ byJob: {} }),
}));
