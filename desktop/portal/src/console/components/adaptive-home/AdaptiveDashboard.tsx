import { ReactNode } from 'react';
import { useContainerSize } from '../../hooks/useContainerSize';
import { adaptLayout, surfaceFromPx } from '../surface-lab/surface';
import { MapRoute } from '../../routes/MapRoute';
import { JobsListRoute } from '../../routes/JobsListRoute';
import { CrewRoute } from '../../routes/CrewRoute';
import { CommRoute } from '../../routes/CommRoute';
import { InvoicesListRoute } from '../../routes/InvoicesListRoute';
import { ShiftCard, OpenTasksCard, DispatchCard, SystemCard } from './cards';
import { useJobsPolling } from '../../hooks/useJobsPolling';
import { useJobsStore } from '../../stores/jobsStore';
import { useCrewPositionsPolling } from '../../hooks/useCrewPositionsPolling';
import { useCrewPositionsStore } from '../../stores/crewPositionsStore';

// "Use the app's real screens", adaptively. The home is the app's dashboard --
// a scrolling grid of rounded cards (the Android DashboardModules idiom), each
// holding a REAL feature screen / module, that reflows columns as the surface
// changes:
//
//   tiny (glance/minimal) -> a one-line glance (active jobs + crew on site)
//   phone-width (narrow)  -> all 9 panels as a horizontal SWIPE carousel (one
//                            full-screen panel at a time, scroll-snap = swipe)
//   tablet / desktop      -> all 9 panels as a scrolling card grid, organised
//                            into sections (map / status modules / features /
//                            comm) whose columns auto-fit the width
//
// Container size (useContainerSize) drives the glance/dashboard decision; the
// column count then auto-fits the real width. ConsoleShell supplies the header +
// bottom nav around this.

function Card({ pad = true, className = '', children }: { pad?: boolean; className?: string; children: ReactNode }) {
  return (
    <div
      className={`bg-console-surface border border-console-border rounded-md overflow-hidden shadow-sm hover:shadow-md transition-shadow ${className}`}
    >
      <div className={`h-full overflow-y-auto ${pad ? 'p-4' : ''}`}>{children}</div>
    </div>
  );
}

// Tiny surfaces (watch / embed): a glanceable summary, not a whole screen.
function GlanceLine() {
  useJobsPolling('list');
  useCrewPositionsPolling();
  const jobs = useJobsStore((s) => s.jobs);
  const positions = useCrewPositionsStore((s) => s.positions);
  const active = jobs.filter((j) => j.status !== 'complete' && j.status !== 'cancelled').length;
  const onSite = positions.length;
  return (
    <div className="h-full w-full flex flex-col items-center justify-center gap-1 bg-console-bg p-2 text-center font-mono">
      <div className="text-console-text">
        <span className="text-console-accent">{'●'}</span> {active} active
      </div>
      {onSite > 0 && <div className="text-[11px] text-console-text-muted">{`((+)) ${onSite} on site`}</div>}
    </div>
  );
}

export function AdaptiveDashboard() {
  const [ref, size] = useContainerSize();
  const plan = size.width > 0 ? adaptLayout(surfaceFromPx(size.width, size.height)) : null;

  let content: ReactNode = null;
  if (plan) {
    if (plan.profile === 'minimal' || plan.profile === 'glance') {
      content = <GlanceLine />;
    } else {
      // All 9 panels at every size. When it's too small for a grid (phone-width:
      // card mode or a single column), switch to a horizontal SWIPE carousel --
      // one full-screen panel at a time (peeking the next), native scroll-snap so
      // touch swipe works. Otherwise a scrolling card grid.
      const narrow = plan.mode === 'card' || plan.columns <= 1;
      const PANELS: { key: string; el: ReactNode; pad?: boolean }[] = [
        { key: 'map', el: <MapRoute />, pad: false },
        { key: 'shift', el: <ShiftCard /> },
        { key: 'tasks', el: <OpenTasksCard /> },
        { key: 'dispatch', el: <DispatchCard /> },
        { key: 'system', el: <SystemCard /> },
        { key: 'jobs', el: <JobsListRoute /> },
        { key: 'crew', el: <CrewRoute /> },
        { key: 'invoices', el: <InvoicesListRoute /> },
        { key: 'comm', el: <CommRoute />, pad: false },
      ];

      if (narrow) {
        content = (
          <div className="h-full w-full flex flex-col bg-console-bg">
            <div className="shrink-0 px-3 pt-2 font-mono text-[10px] text-console-text-muted">
              swipe · {PANELS.length} panels
            </div>
            <div className="flex-1 min-h-0 flex gap-3 overflow-x-auto snap-x snap-mandatory px-3 pb-3">
              {PANELS.map((p) => (
                <div key={p.key} className="snap-center shrink-0 w-[88%] h-full">
                  <Card pad={p.pad} className="h-full w-full">
                    {p.el}
                  </Card>
                </div>
              ))}
            </div>
          </div>
        );
      } else {
        // tablet / desktop: a scrolling grid of all 9, organised so rows don't
        // mix card heights -- map (full) / status modules / features / comm.
        content = (
          <div className="h-full overflow-y-auto bg-console-bg p-3 sm:p-4 space-y-3 sm:space-y-4">
            <Card pad={false} className="h-[360px]">
              <MapRoute />
            </Card>
            <div
              className="grid gap-3 sm:gap-4 items-start"
              style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))' }}
            >
              <Card className="h-[172px]">
                <ShiftCard />
              </Card>
              <Card className="h-[172px]">
                <OpenTasksCard />
              </Card>
              <Card className="h-[172px]">
                <DispatchCard />
              </Card>
              <Card className="h-[172px]">
                <SystemCard />
              </Card>
            </div>
            <div
              className="grid gap-3 sm:gap-4 items-start"
              style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))' }}
            >
              <Card className="h-[280px]">
                <JobsListRoute />
              </Card>
              <Card className="h-[280px]">
                <CrewRoute />
              </Card>
              <Card className="h-[280px]">
                <InvoicesListRoute />
              </Card>
            </div>
            <Card pad={false} className="h-[380px]">
              <CommRoute />
            </Card>
          </div>
        );
      }
    }
  }

  return (
    <div ref={ref} className="h-full w-full min-h-0">
      {content}
    </div>
  );
}
