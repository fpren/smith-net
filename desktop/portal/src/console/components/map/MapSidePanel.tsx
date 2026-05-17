// desktop/portal/src/console/components/map/MapSidePanel.tsx
import { useState } from 'react';
import { MapFilterChips, FilterMode } from './MapFilterChips';
import { JobCard } from '../jobs/JobCard';
import type { Job, JobStatus } from '../../api/jobsClient';

const STATUSES: { status: JobStatus; label: string; defaultOpen: boolean }[] = [
  { status: 'planned',     label: 'PLANNED',     defaultOpen: true  },
  { status: 'in_progress', label: 'IN PROGRESS', defaultOpen: true  },
  { status: 'complete',    label: 'COMPLETE',    defaultOpen: false },
  { status: 'cancelled',   label: 'CANCELLED',   defaultOpen: false },
];

interface Props {
  jobs: Job[];
  mode: FilterMode;
  onModeChange: (m: FilterMode) => void;
  onSelectJob: (jobId: string) => void;
}

function Section({ label, jobs, defaultOpen, onSelectJob }: { label: string; jobs: Job[]; defaultOpen: boolean; onSelectJob: (id: string) => void }) {
  const [open, setOpen] = useState(defaultOpen);
  return (
    <div className="border-b border-console-border">
      <button
        type="button"
        onClick={() => setOpen((o) => !o)}
        className="w-full flex items-center justify-between px-3 py-2 bg-console-surface text-console-text-muted text-xs uppercase tracking-wide font-mono"
      >
        <span>{label} ({jobs.length})</span>
        <span>{open ? '[-]' : '[+]'}</span>
      </button>
      {open && jobs.map((j) => (
        <JobCard key={j.id} job={j} variant="compact" onClick={() => onSelectJob(j.id)} />
      ))}
    </div>
  );
}

export function MapSidePanel({ jobs, mode, onModeChange, onSelectJob }: Props) {
  const visible = STATUSES.filter((s) =>
    mode === 'all' ? true : s.status === 'planned' || s.status === 'in_progress'
  );

  return (
    <aside className="w-full md:w-[300px] border-t md:border-t-0 md:border-l border-console-border bg-console-surface flex flex-col font-mono">
      <div className="p-3 border-b border-console-border">
        <MapFilterChips mode={mode} onChange={onModeChange} />
      </div>
      <div className="flex-1 overflow-y-auto">
        {visible.map((s) => (
          <Section
            key={s.status}
            label={s.label}
            jobs={jobs.filter((j) => j.status === s.status)}
            defaultOpen={s.defaultOpen}
            onSelectJob={onSelectJob}
          />
        ))}
      </div>
    </aside>
  );
}
