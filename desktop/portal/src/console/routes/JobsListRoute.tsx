import { useState } from 'react';
import { Outlet, useMatch } from 'react-router-dom';
import { Button } from '../components/ui/Button';
import { CreateJobModal } from '../components/jobs/CreateJobModal';
import { JobCard } from '../components/jobs/JobCard';
import { useJobsPolling } from '../hooks/useJobsPolling';
import { useJobsStore } from '../stores/jobsStore';
import { LoadingState, EmptyState, ErrorState } from '../components/ui/StateViews';
import type { JobStatus, Job } from '../api/jobsClient';

const STATUSES: { status: JobStatus; label: string; defaultOpen: boolean }[] = [
  { status: 'planned',     label: 'PLANNED',     defaultOpen: true  },
  { status: 'in_progress', label: 'IN PROGRESS', defaultOpen: true  },
  { status: 'complete',    label: 'COMPLETE',    defaultOpen: false },
  { status: 'cancelled',   label: 'CANCELLED',   defaultOpen: false },
];

function StatusSection({ label, jobs, defaultOpen }: { label: string; jobs: Job[]; defaultOpen: boolean }) {
  const [open, setOpen] = useState(defaultOpen);
  return (
    <div className="border border-sn-line mb-3">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        className="w-full flex items-center justify-between px-3 py-2 bg-sn-bg-panel text-sn-ink-muted text-xs uppercase tracking-wide font-mono"
      >
        <span>{label} ({jobs.length})</span>
        <span>{open ? '[-]' : '[+]'}</span>
      </button>
      {open && (
        <div>
          {jobs.length === 0 && <div className="px-3 py-2 text-sn-ink-muted text-sm">—</div>}
          {jobs.map((j) => <JobCard key={j.id} job={j} />)}
        </div>
      )}
    </div>
  );
}

export function JobsListRoute() {
  const { reload } = useJobsPolling('list');
  const jobs = useJobsStore((s) => s.jobs);
  const isLoadingList = useJobsStore((s) => s.isLoadingList);
  const listStale = useJobsStore((s) => s.listStale);
  const upsertJob = useJobsStore((s) => s.upsertJob);
  const [showCreate, setShowCreate] = useState(false);
  // Independent path match (not useParams — the :id param belongs to the
  // nested child route, which isn't in this component's own route context).
  const idActive = Boolean(useMatch('/console/jobs/:id'));

  const byStatus = (st: JobStatus) => jobs.filter((j) => j.status === st);

  // Precedence: loading -> error (no cached data to fall back on) -> empty -> data.
  let listContent: JSX.Element;
  if (isLoadingList && jobs.length === 0) {
    listContent = <LoadingState label="Loading jobs" />;
  } else if (listStale && jobs.length === 0) {
    listContent = (
      <div className="flex flex-col items-center mt-24 gap-4">
        <ErrorState message="Couldn't load jobs." onRetry={reload} />
      </div>
    );
  } else if (jobs.length === 0) {
    listContent = (
      <div className="flex flex-col items-center mt-24 gap-4">
        <EmptyState
          title="No jobs yet"
          action={<Button onClick={() => setShowCreate(true)}>Create your first job</Button>}
        />
        <CreateJobModal open={showCreate} onClose={() => setShowCreate(false)} onCreated={(job) => { upsertJob(job); setShowCreate(false); }} />
      </div>
    );
  } else {
    listContent = (
      <div className="font-mono">
        <div className="flex flex-col items-start gap-2 md:flex-row md:items-center md:justify-between mb-4">
          <h1 className="text-sn-ink text-lg">Jobs</h1>
          <Button onClick={() => setShowCreate(true)}>+ Create Job</Button>
        </div>
        {listStale && (
          <ErrorState message="Couldn't refresh — showing cached data." onRetry={reload} />
        )}
        {STATUSES.map((s) => (
          <StatusSection key={s.status} label={s.label} jobs={byStatus(s.status)} defaultOpen={s.defaultOpen} />
        ))}
        <CreateJobModal open={showCreate} onClose={() => setShowCreate(false)} onCreated={() => setShowCreate(false)} />
      </div>
    );
  }

  return (
    <div className="xl:grid xl:grid-cols-[minmax(0,1fr)_420px] xl:gap-6 xl:h-full">
      <div className={
        // The list column is an independent scroll region in BOTH states --
        // flipping scroll modes on selection would reset the list's scroll
        // position the moment you open the item you scrolled to find.
        idActive ? 'hidden xl:block xl:overflow-y-auto xl:min-h-0' : 'xl:overflow-y-auto xl:min-h-0'
      }>
        {listContent}
      </div>
      <div
        className={
          idActive
            ? 'block xl:overflow-y-auto xl:min-h-0 xl:border-l xl:border-sn-line xl:pl-6'
            : 'hidden xl:block xl:overflow-y-auto xl:min-h-0 xl:border-l xl:border-sn-line xl:pl-6'
        }
      >
        {idActive ? <Outlet /> : <EmptyState title="Select a job" />}
      </div>
    </div>
  );
}
