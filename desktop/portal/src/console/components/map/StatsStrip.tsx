// desktop/portal/src/console/components/map/StatsStrip.tsx
import { useMemo } from 'react';
import type { Job } from '../../api/jobsClient';

const WEEK_MS = 7 * 86400 * 1000;

export function StatsStrip({ jobs }: { jobs: Job[] }) {
  const stats = useMemo(() => {
    const weekAgo = Date.now() - WEEK_MS;
    let planned = 0, inProg = 0, complete = 0, cancelled = 0;
    for (const j of jobs) {
      if (j.status === 'planned') planned++;
      else if (j.status === 'in_progress') inProg++;
      else if (j.status === 'complete' && new Date(j.updatedAt).getTime() > weekAgo) complete++;
      else if (j.status === 'cancelled' && new Date(j.updatedAt).getTime() > weekAgo) cancelled++;
    }
    return { planned, inProg, complete, cancelled };
  }, [jobs]);

  return (
    <div className="font-mono text-xs text-console-text-muted flex gap-3">
      <span>PLANNED {stats.planned}</span><span>·</span>
      <span>IN PROGRESS {stats.inProg}</span><span>·</span>
      <span>COMPLETE {stats.complete} (week)</span><span>·</span>
      <span>CANCELLED {stats.cancelled} (week)</span>
    </div>
  );
}
