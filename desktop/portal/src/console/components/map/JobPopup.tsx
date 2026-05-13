// desktop/portal/src/console/components/map/JobPopup.tsx
import { Link } from 'react-router-dom';
import { JobStatusBadge } from '../jobs/JobStatusBadge';
import type { Job, CrewAssignment } from '../../api/jobsClient';

interface Props {
  job: Job;
  crew: CrewAssignment[];
}

export function JobPopup({ job, crew }: Props) {
  return (
    <div className="p-3 min-w-[260px] max-w-[360px] font-mono text-sm">
      <div className="mb-2"><JobStatusBadge status={job.status} /></div>
      <div className="text-console-text mb-1">{job.title}</div>
      {job.location && <div className="text-console-text-muted text-xs mb-2">{job.location}</div>}
      {job.scheduledAt && <div className="text-console-text-muted text-xs mb-2">scheduled: {new Date(job.scheduledAt).toLocaleString()}</div>}

      <div className="border-t border-console-border mt-2 pt-2">
        <div className="text-console-text-muted text-xs uppercase tracking-wide mb-1">CREW ({crew.length})</div>
        {crew.length === 0 ? (
          <div className="text-console-text-muted text-xs">No crew assigned.</div>
        ) : (
          <ul className="text-xs">
            {crew.map((c) => (
              <li key={c.profileId}>• {c.profileId} <span className="text-console-text-muted">({c.roleOnJob})</span></li>
            ))}
          </ul>
        )}
      </div>

      <Link to={`/console/jobs/${job.id}`} className="text-console-accent text-xs block mt-3 hover:underline">
        [-&gt; open detail]
      </Link>
    </div>
  );
}
