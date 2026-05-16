// desktop/portal/src/console/components/jobs/JobCard.tsx
//
// Card-style job row. Two variants:
//   variant="card"    — used in /console/jobs list. Link to detail.
//   variant="compact" — used in MapSidePanel. onClick fires; no link.

import { Link } from 'react-router-dom';
import { JobStatusBadge } from './JobStatusBadge';
import type { Job } from '../../api/jobsClient';

function relativeTime(iso: string | null): string {
  if (iso === null) return 'unscheduled';
  const target = new Date(iso).getTime();
  const now = Date.now();
  const diff = target - now;
  const minutes = Math.round(diff / 60000);
  const hours = Math.round(minutes / 60);
  const days = Math.round(hours / 24);
  if (Math.abs(minutes) < 60) return minutes >= 0 ? `in ${minutes}m` : `${-minutes}m ago`;
  if (Math.abs(hours) < 24) return hours >= 0 ? `in ${hours}h` : `${-hours}h ago`;
  if (days === 1) return 'tomorrow';
  if (days === -1) return 'yesterday';
  return days >= 0 ? `in ${days}d` : `${-days}d ago`;
}

interface Props {
  job: Job;
  variant?: 'card' | 'compact';
  /** When set on the compact variant, called on click. */
  onClick?: () => void;
}

export function JobCard({ job, variant = 'card', onClick }: Props) {
  const idShort = `#${job.id.slice(0, 8)}`;
  const location = job.location ?? '— no location';
  const time = relativeTime(job.scheduledAt);

  if (variant === 'compact') {
    return (
      <button
        type="button"
        onClick={onClick}
        className="w-full text-left bg-console-surface border-b border-console-border hover:bg-console-bg transition-colors font-mono"
      >
        <div className="px-3 py-2 flex items-start justify-between gap-2">
          <div className="flex-1 min-w-0">
            <div className="text-console-text text-sm font-medium truncate">{job.title}</div>
            <div className="text-console-accent text-xs">{idShort}</div>
          </div>
          <JobStatusBadge status={job.status} xs />
        </div>
        <div className="px-3 pb-2 flex items-center justify-between text-xs">
          <span className="text-console-text-muted truncate">{location}</span>
          <span className="text-console-text-muted whitespace-nowrap ml-2">{time}</span>
        </div>
      </button>
    );
  }

  // Default: card variant for the /console/jobs list.
  return (
    <div className="bg-console-surface border border-console-border mb-2 font-mono">
      <div className="px-4 py-3 flex items-start justify-between gap-3">
        <div className="flex-1 min-w-0">
          <div className="text-console-text font-medium truncate">{job.title}</div>
          <div className="text-console-accent text-xs mt-0.5">{idShort}</div>
        </div>
        <JobStatusBadge status={job.status} />
      </div>
      <div className="px-4 pb-3 flex items-center justify-between text-xs">
        <span className="text-console-text-muted truncate">{location}</span>
        <span className="text-console-text-muted">{time}</span>
      </div>
      <div className="px-4 py-2 border-t border-console-border bg-console-bg flex items-center justify-end">
        <Link
          to={`/console/jobs/${job.id}`}
          className="text-console-accent text-xs hover:underline"
        >
          {'[-> open detail]'}
        </Link>
      </div>
    </div>
  );
}
