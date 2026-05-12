import { Link } from 'react-router-dom';
import type { Job } from '../../api/jobsClient';

function relativeTime(iso: string | null): string {
  if (iso === null) return 'unsch';
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

export function JobCard({ job }: { job: Job }) {
  return (
    <div className="grid grid-cols-[10ch_1fr_20ch_12ch_8ch] gap-3 items-center px-3 py-2 border-b border-console-border text-sm font-mono">
      <span className="text-console-accent">#{job.id.slice(0, 8)}</span>
      <span className="text-console-text truncate">{job.title}</span>
      <span className="text-console-text-muted truncate">{job.location ?? '—'}</span>
      <span className="text-console-text-muted">{relativeTime(job.scheduledAt)}</span>
      <Link to={`/console/jobs/${job.id}`} className="text-console-accent hover:underline text-right">
        {`[-> detail]`}
      </Link>
    </div>
  );
}
