// desktop/portal/src/console/stores/toastStore.ts
import { create } from 'zustand';

export type ToastTone = 'info' | 'error';

export interface ToastEntry {
  id: number;
  message: string;
  tone: ToastTone;
  duration: number;
}

interface ToastState {
  toasts: ToastEntry[];
  push: (entry: Omit<ToastEntry, 'id'>) => number;
  dismiss: (id: number) => void;
}

const MAX_STACK = 5;
let nextId = 1;

export const useToastStore = create<ToastState>((set) => ({
  toasts: [],
  push: (entry) => {
    const id = nextId++;
    set((state) => {
      const next = [{ id, ...entry }, ...state.toasts];
      return { toasts: next.slice(0, MAX_STACK) };
    });
    return id;
  },
  dismiss: (id) => set((state) => ({ toasts: state.toasts.filter((t) => t.id !== id) })),
}));
