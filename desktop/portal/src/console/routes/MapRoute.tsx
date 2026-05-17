// desktop/portal/src/console/routes/MapRoute.tsx
import { useEffect, useState } from 'react';
import { Button } from '../components/ui/Button';
import { CreateJobModal } from '../components/jobs/CreateJobModal';
import { StatsStrip } from '../components/map/StatsStrip';
import { MapSidePanel } from '../components/map/MapSidePanel';
import { MapCanvas } from '../components/map/MapCanvas';
import type { FilterMode } from '../components/map/MapFilterChips';
import { useJobsPolling } from '../hooks/useJobsPolling';
import { useJobsStore } from '../stores/jobsStore';
import { useCrewPositionsPolling } from '../hooks/useCrewPositionsPolling';
import { useCrewPositionsStore } from '../stores/crewPositionsStore';
import type { JobStatus } from '../api/jobsClient';

const FILTER_KEY = 'console.map.filterMode';

function readFilterMode(): FilterMode {
  try {
    const v = localStorage.getItem(FILTER_KEY);
    return v === 'all' ? 'all' : 'active';
  } catch {
    return 'active';
  }
}

const ACTIVE_STATUSES: JobStatus[] = ['planned', 'in_progress'];
const ALL_STATUSES: JobStatus[] = ['planned', 'in_progress', 'complete', 'cancelled'];

export function MapRoute() {
  useJobsPolling('list');
  const jobs = useJobsStore((s) => s.jobs);
  useCrewPositionsPolling();
  const crewPositions = useCrewPositionsStore((s) => s.positions);
  const [mode, setMode] = useState<FilterMode>(readFilterMode);
  const [showCreate, setShowCreate] = useState(false);

  useEffect(() => {
    try { localStorage.setItem(FILTER_KEY, mode); } catch { /* ignore */ }
  }, [mode]);

  const visibleStatuses = mode === 'all' ? ALL_STATUSES : ACTIVE_STATUSES;

  return (
    <div className="flex flex-col h-full font-mono">
      <div className="flex flex-col gap-2 md:flex-row md:items-center md:justify-between p-3 border-b border-console-border">
        <StatsStrip jobs={jobs} />
        <Button onClick={() => setShowCreate(true)}>+ Create Job</Button>
      </div>
      <div className="flex flex-col md:flex-row flex-1 min-h-0">
        <div className="flex-1 min-h-[40vh] md:min-h-0 relative">
          <MapCanvas
            jobs={jobs}
            crewPositions={crewPositions}
            visibleStatuses={visibleStatuses}
            selectedJobId={null}
            onSelectJob={(_id) => { /* future: open popup */ }}
          />
        </div>
        <MapSidePanel
          jobs={jobs}
          mode={mode}
          onModeChange={setMode}
          onSelectJob={(_id) => { /* future: fly to + open popup */ }}
        />
      </div>
      <CreateJobModal open={showCreate} onClose={() => setShowCreate(false)} onCreated={() => setShowCreate(false)} />
    </div>
  );
}
