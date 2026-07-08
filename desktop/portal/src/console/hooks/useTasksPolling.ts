// desktop/portal/src/console/hooks/useTasksPolling.ts
//
// Polls the tasks for a single job every 15s while the tab is visible.
// Mirrors useJobsPolling's structure. JobDetailRoute mounts this with the
// current job id so changes from other clients (apk, second tab) appear.
import { useEffect, useRef } from 'react';
import { tasksClient } from '../api/tasksClient';
import { useTasksStore } from '../stores/tasksStore';

const INTERVAL_MS = 15_000;

export function useTasksPolling(jobId: string): void {
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    if (!jobId) return;

    let cancelled = false;
    const fetchOnce = async () => {
      useTasksStore.getState().markLoading(jobId, true);
      const result = await tasksClient.listForJob(jobId);
      useTasksStore.getState().markLoading(jobId, false);
      if (cancelled) return;
      if (result.ok) {
        useTasksStore.getState().setTasks(jobId, result.tasks);
      } else {
        useTasksStore.getState().markStale(jobId, true);
      }
    };

    const start = () => {
      if (intervalRef.current !== null) return;
      intervalRef.current = setInterval(fetchOnce, INTERVAL_MS);
    };
    const stop = () => {
      if (intervalRef.current !== null) {
        clearInterval(intervalRef.current);
        intervalRef.current = null;
      }
    };
    const onVisibility = () => {
      if (document.visibilityState === 'visible') {
        fetchOnce();
        start();
      } else {
        stop();
      }
    };

    fetchOnce();
    start();
    document.addEventListener('visibilitychange', onVisibility);

    return () => {
      cancelled = true;
      stop();
      document.removeEventListener('visibilitychange', onVisibility);
    };
  }, [jobId]);
}
