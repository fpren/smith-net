// desktop/portal/src/console/stores/jobsStore.ts
import { create } from 'zustand';
import type { Job, CrewAssignment } from '../api/jobsClient';

interface JobsState {
  jobs: Job[];
  detailJob: Job | null;
  detailCrew: CrewAssignment[];
  isLoadingList: boolean;
  isLoadingDetail: boolean;
  lastFetchedAt: number | null;
  // Split per Plan 4A Task 5 review finding #2: list and detail scopes poll
  // independently (see useJobsPolling), so a single shared flag let a stale
  // list poll false-flash an ErrorState on a detail view that hadn't even
  // fetched yet. Each scope now owns its own flag.
  listStale: boolean;
  detailStale: boolean;

  setJobs: (jobs: Job[]) => void;
  setDetail: (job: Job, crew: CrewAssignment[]) => void;
  upsertJob: (job: Job) => void;
  markListLoading: (b: boolean) => void;
  markDetailLoading: (b: boolean) => void;
  markListStale: (b: boolean) => void;
  markDetailStale: (b: boolean) => void;
  clear: () => void;
}

export const useJobsStore = create<JobsState>((set) => ({
  jobs: [],
  detailJob: null,
  detailCrew: [],
  isLoadingList: false,
  isLoadingDetail: false,
  lastFetchedAt: null,
  listStale: false,
  detailStale: false,

  setJobs: (jobs) => set({ jobs, lastFetchedAt: Date.now(), listStale: false }),
  setDetail: (detailJob, detailCrew) => set({ detailJob, detailCrew }),

  upsertJob: (job) => set((state) => {
    const idx = state.jobs.findIndex((j) => j.id === job.id);
    const nextJobs = idx === -1
      ? [job, ...state.jobs]
      : state.jobs.map((j, i) => (i === idx ? job : j));
    const nextDetail = state.detailJob && state.detailJob.id === job.id ? job : state.detailJob;
    return { jobs: nextJobs, detailJob: nextDetail };
  }),

  markListLoading: (isLoadingList) => set({ isLoadingList }),
  markDetailLoading: (isLoadingDetail) => set({ isLoadingDetail }),
  markListStale: (listStale) => set({ listStale }),
  markDetailStale: (detailStale) => set({ detailStale }),

  clear: () => set({
    jobs: [],
    detailJob: null,
    detailCrew: [],
    isLoadingList: false,
    isLoadingDetail: false,
    lastFetchedAt: null,
    listStale: false,
    detailStale: false,
  }),
}));
