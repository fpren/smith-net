import { FC } from 'react';
import { useContainerSize } from '../../hooks/useContainerSize';
import { adaptLayout, surfaceFromPx, LayoutPlan, ModuleId } from '../surface-lab/surface';
import { useJobsPolling } from '../../hooks/useJobsPolling';
import { useJobsStore } from '../../stores/jobsStore';
import { JobsPanel, CommPanel, MapPanel, CrewPanel } from './panels';

// The concept made real: this measures its OWN container and runs the same pure
// adaptLayout decision the sandbox uses, then renders the portal's real data.
//   app   -> a grid of real feature panels (jobs/comm/map/crew)
//   card  -> the single primary feature (jobs)
//   glyph -> a one-line glance
// ('job' single-card + 'clients' have no app-grid panel: job folds into the card
//  mode, clients has no data source yet.)

const MODULE_PANELS: Partial<Record<ModuleId, FC>> = {
  jobs: JobsPanel,
  comm: CommPanel,
  map: MapPanel,
  crew: CrewPanel,
};

function GlanceLine() {
  useJobsPolling('list');
  const jobs = useJobsStore((s) => s.jobs);
  const active = jobs.filter((j) => j.status !== 'complete' && j.status !== 'cancelled').length;
  return (
    <div className="h-full w-full flex items-center justify-center gap-2 text-console-text">
      <span className="text-console-ok text-xs leading-none">●</span>
      <span className="font-mono text-console-text font-bold tabular-nums text-lg">{active}</span>
      <span className="font-sans text-console-text-muted text-xs">active jobs</span>
    </div>
  );
}

function renderPlan(plan: LayoutPlan) {
  if (plan.mode === 'glyph') return <GlanceLine />;
  if (plan.mode === 'card') {
    return (
      <div className="h-full">
        <JobsPanel />
      </div>
    );
  }
  const mods = plan.modules.filter((m) => MODULE_PANELS[m]);
  return (
    <div
      className="grid gap-2.5 h-full"
      style={{
        gridTemplateColumns: `repeat(${plan.columns}, minmax(0, 1fr))`,
        gridAutoRows: 'minmax(0, 1fr)',
      }}
    >
      {mods.map((m) => {
        const Panel = MODULE_PANELS[m]!;
        return <Panel key={m} />;
      })}
    </div>
  );
}

export function AdaptiveDashboard() {
  const [ref, size] = useContainerSize();
  // First paint (before ResizeObserver fires) has size 0 -- render the empty
  // measured container, then re-render with the real plan.
  const plan = size.width > 0 ? adaptLayout(surfaceFromPx(size.width, size.height)) : null;
  return (
    <div ref={ref} className="h-full w-full min-h-0">
      {plan && renderPlan(plan)}
    </div>
  );
}
