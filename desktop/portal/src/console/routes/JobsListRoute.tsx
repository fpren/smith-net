import { useState } from 'react';
import { Button } from '../components/ui/Button';
import { CreateJobModal } from '../components/jobs/CreateJobModal';
import { JobCard } from '../components/jobs/JobCard';
import { useJobsPolling } from '../hooks/useJobsPolling';
import { useJobsStore } from '../stores/jobsStore';
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
    <div className="border border-console-border mb-3">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        className="w-full flex items-center justify-between px-3 py-2 bg-console-surface text-console-text-muted text-xs uppercase tracking-wide font-mono"
      >
        <span>{label} ({jobs.length})</span>
        <span>{open ? '[-]' : '[+]'}</span>
      </button>
      {open && (
        <div>
          {jobs.length === 0 && <div className="px-3 py-2 text-console-text-muted text-sm">—</div>}
          {jobs.map((j) => <JobCard key={j.id} job={j} />)}
        </div>
      )}
    </div>
  );
}

export function JobsListRoute() {
  useJobsPolling('list');
  const jobs = useJobsStore((s) => s.jobs);
  const isStale = useJobsStore((s) => s.isStale);
  const upsertJob = useJobsStore((s) => s.upsertJob);
  const [showCreate, setShowCreate] = useState(false);

  const byStatus = (st: JobStatus) => jobs.filter((j) => j.status === st);

  if (jobs.length === 0) {
    return (
      <div className="flex flex-col items-center mt-24 gap-4">
        <div className="text-console-text-muted">No jobs yet.</div>
        <Button onClick={() => setShowCreate(true)}>Create your first job</Button>
        <CreateJobModal open={showCreate} onClose={() => setShowCreate(false)} onCreated={(job) => { upsertJob(job); setShowCreate(false); }} />
      </div>
    );
  }

  return (
    <div className="font-mono">
      <div className="flex flex-col items-start gap-2 md:flex-row md:items-center md:justify-between mb-4">
        <h1 className="text-console-text text-lg">Jobs</h1>
        <Button onClick={() => setShowCreate(true)}>+ Create Job</Button>
      </div>
      {isStale && (
        <div className="bg-console-surface border border-console-warn text-console-warn px-3 py-1 text-xs mb-3">
          [OFFLINE] Couldn't refresh — showing cached data
        </div>
      )}
      {STATUSES.map((s) => (
        <StatusSection key={s.status} label={s.label} jobs={byStatus(s.status)} defaultOpen={s.defaultOpen} />
      ))}
      <CreateJobModal open={showCreate} onClose={() => setShowCreate(false)} onCreated={() => setShowCreate(false)} />
    </div>
  );
}
