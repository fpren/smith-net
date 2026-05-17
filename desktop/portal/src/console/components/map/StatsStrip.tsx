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
    // Mobile: 2x2 grid so each stat stays whole and aligned. Desktop: the
    // original inline strip with `·` separators (whitespace-nowrap ensures
    // "IN PROGRESS 0" doesn't break in two when the row is tight).
    <div className="font-mono text-xs text-console-text-muted grid grid-cols-2 gap-x-3 gap-y-1 md:flex md:gap-3">
      <span className="whitespace-nowrap">PLANNED {stats.planned}</span>
      <span className="hidden md:inline">·</span>
      <span className="whitespace-nowrap">IN PROGRESS {stats.inProg}</span>
      <span className="hidden md:inline">·</span>
      <span className="whitespace-nowrap">COMPLETE {stats.complete} (week)</span>
      <span className="hidden md:inline">·</span>
      <span className="whitespace-nowrap">CANCELLED {stats.cancelled} (week)</span>
    </div>
  );
}
