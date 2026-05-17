// desktop/portal/src/console/stores/tasksStore.ts
import { create } from 'zustand';
import type { Task } from '../api/tasksClient';

interface TasksState {
  tasksByJob: Record<string, Task[]>;
  isLoadingByJob: Record<string, boolean>;
  isStaleByJob: Record<string, boolean>;

  setTasks: (jobId: string, tasks: Task[]) => void;
  addTask: (task: Task) => void;
  updateTask: (task: Task) => void;
  removeTask: (taskId: string) => void;
  markLoading: (jobId: string, b: boolean) => void;
  markStale: (jobId: string, b: boolean) => void;
  clear: () => void;
}

export const useTasksStore = create<TasksState>((set) => ({
  tasksByJob: {},
  isLoadingByJob: {},
  isStaleByJob: {},

  setTasks: (jobId, tasks) =>
    set((s) => ({
      tasksByJob: { ...s.tasksByJob, [jobId]: tasks },
      isStaleByJob: { ...s.isStaleByJob, [jobId]: false },
    })),

  addTask: (task) =>
    set((s) => {
      const existing = s.tasksByJob[task.jobId] ?? [];
      if (existing.some((t) => t.id === task.id)) return {};
      return {
        tasksByJob: {
          ...s.tasksByJob,
          [task.jobId]: [...existing, task].sort((a, b) => a.sortOrder - b.sortOrder),
        },
      };
    }),

  updateTask: (task) =>
    set((s) => {
      const existing = s.tasksByJob[task.jobId];
      if (!existing) return {};
      return {
        tasksByJob: {
          ...s.tasksByJob,
          [task.jobId]: existing.map((t) => (t.id === task.id ? task : t)),
        },
      };
    }),

  removeTask: (taskId) =>
    set((s) => {
      const next: Record<string, Task[]> = {};
      let changed = false;
      for (const [jobId, list] of Object.entries(s.tasksByJob)) {
        const filtered = list.filter((t) => t.id !== taskId);
        if (filtered.length !== list.length) changed = true;
        next[jobId] = filtered;
      }
      return changed ? { tasksByJob: next } : {};
    }),

  markLoading: (jobId, b) =>
    set((s) => ({ isLoadingByJob: { ...s.isLoadingByJob, [jobId]: b } })),
  markStale: (jobId, b) =>
    set((s) => ({ isStaleByJob: { ...s.isStaleByJob, [jobId]: b } })),
  clear: () => set({ tasksByJob: {}, isLoadingByJob: {}, isStaleByJob: {} }),
}));
