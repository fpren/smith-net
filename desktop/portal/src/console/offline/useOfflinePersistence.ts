// desktop/portal/src/console/offline/useOfflinePersistence.ts
//
// At console mount, hydrate the office-data stores (jobs / crew / invoices /
// tasks) from the per-profile IndexedDB cache and write changes through. On
// logout or profile switch, clear the previous profile's cache. Comm is
// intentionally not cached (the portal Channel type has no ephemeral
// marker; see the SP2 slice 1 spec). Crew map positions are also excluded
// (realtime, low value cached).
import { useEffect, useRef } from 'react';
import { useAuthStore } from '../auth/authStore';
import { useJobsStore } from '../stores/jobsStore';
import { useCrewStore } from '../stores/crewStore';
import { useInvoicesStore } from '../stores/invoicesStore';
import { useTasksStore } from '../stores/tasksStore';
import { persistStore } from './persistStore';
import { clearProfile } from './db';

export function useOfflinePersistence(): void {
  const userId = useAuthStore((s) => s.user?.id ?? null);
  const prevIdRef = useRef<string | null>(null);

  useEffect(() => {
    // Clear the profile we were caching when it changes (logout / switch).
    const prev = prevIdRef.current;
    if (prev && prev !== userId) void clearProfile(prev);
    prevIdRef.current = userId;

    if (!userId) return;

    let cancelled = false;
    const unsubs: Array<() => void> = [];
    const bind = (p: Promise<() => void>) =>
      void p.then((u) => {
        if (cancelled) u();
        else unsubs.push(u);
      });

    bind(
      persistStore({
        store: useJobsStore,
        profileId: userId,
        collection: 'jobs',
        pick: (s) => ({ jobs: s.jobs }),
        hydrate: (api, d) => {
          api.setJobs(d.jobs);
          api.markListStale(true);
        },
        shouldPersist: (s) => s.lastFetchedAt != null,
      }),
    );
    bind(
      persistStore({
        store: useCrewStore,
        profileId: userId,
        collection: 'crew',
        pick: (s) => ({ roster: s.roster }),
        hydrate: (api, d) => {
          api.setRoster(d.roster);
          api.markStale(true);
        },
        shouldPersist: (s) => s.lastFetchedAt != null,
      }),
    );
    bind(
      persistStore({
        store: useInvoicesStore,
        profileId: userId,
        collection: 'invoices',
        pick: (s) => ({ invoices: s.invoices }),
        hydrate: (api, d) => {
          api.setInvoices(d.invoices);
          api.markListStale(true);
        },
        shouldPersist: (s) => s.invoices.length > 0,
      }),
    );
    bind(
      persistStore({
        store: useTasksStore,
        profileId: userId,
        collection: 'tasks',
        pick: (s) => ({ tasksByJob: s.tasksByJob }),
        hydrate: (api, d) => {
          for (const [jobId, list] of Object.entries(d.tasksByJob)) {
            api.setTasks(jobId, list);
            api.markStale(jobId, true);
          }
        },
        shouldPersist: (s) => Object.values(s.tasksByJob).some((list) => list.length > 0),
      }),
    );

    return () => {
      cancelled = true;
      unsubs.forEach((u) => u());
    };
  }, [userId]);
}
