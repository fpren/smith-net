import { ReactNode } from 'react';
import { NavLink } from 'react-router-dom';
import { clsx } from 'clsx';
import { useJobsPolling } from '../../hooks/useJobsPolling';
import { useJobsStore } from '../../stores/jobsStore';
import { useCommWebSocket } from '../../hooks/useCommWebSocket';
import { useCommStore } from '../../stores/commStore';
import { useCrewRoster } from '../../hooks/useCrewRoster';
import { useCrewStore } from '../../stores/crewStore';
import { useCrewPositionsPolling } from '../../hooks/useCrewPositionsPolling';
import { useCrewPositionsStore } from '../../stores/crewPositionsStore';
import type { JobStatus } from '../../api/jobsClient';

// Adaptive-home feature panels, styled to match the Android app's dashboard
// module idiom: warm console surface, 4px radius, hairline border, NO shadow,
// 14px padding, an ALL-CAPS mono section label, IBM Plex Sans body rows,
// [bracket] accent actions, and the app's status dots (filled = active /
// hollow = idle; these are the design-system glyphs, not emoji).

const STATUS_DOT: Record<JobStatus, string> = {
  planned: '○', // hollow circle
  in_progress: '●', // filled circle
  complete: '●',
  cancelled: '○',
};
const STATUS_COLOR: Record<JobStatus, string> = {
  planned: 'text-console-text-muted',
  in_progress: 'text-console-warn',
  complete: 'text-console-ok',
  cancelled: 'text-console-text-muted',
};
const DOT_ON = '●';
const DOT_OFF = '○';

function ModuleCard({
  title,
  to,
  stale,
  children,
}: {
  title: string;
  to: string;
  stale?: boolean;
  children: ReactNode;
}) {
  return (
    <section className="flex flex-col min-h-0 bg-console-surface rounded border border-console-border p-3.5">
      <div className="flex items-center justify-between mb-2">
        <span className="font-mono text-[11px] uppercase tracking-wide font-medium text-console-text-muted">
          {title}
          {stale && <span className="text-console-warn"> [offline]</span>}
        </span>
        <NavLink to={to} className="font-mono text-[11px] text-console-accent hover:underline whitespace-nowrap">
          [open]
        </NavLink>
      </div>
      <div className="flex-1 min-h-0 overflow-auto font-sans text-[13px] text-console-text">{children}</div>
    </section>
  );
}

function Empty({ label }: { label: string }) {
  return <div className="font-sans text-[13px] text-console-text-muted">{label}</div>;
}

export function JobsPanel() {
  useJobsPolling('list');
  const jobs = useJobsStore((s) => s.jobs);
  const stale = useJobsStore((s) => s.isStale);
  const active = jobs.filter((j) => j.status !== 'complete' && j.status !== 'cancelled').length;
  return (
    <ModuleCard title="Jobs" to="/console/jobs" stale={stale}>
      {jobs.length === 0 ? (
        <Empty label="No jobs yet." />
      ) : (
        <div className="flex flex-col gap-1.5">
          {jobs.slice(0, 8).map((j) => (
            <div key={j.id} className="flex items-center gap-2 truncate">
              <span className={clsx('text-[10px] leading-none', STATUS_COLOR[j.status])}>{STATUS_DOT[j.status]}</span>
              <span className="truncate">{j.title}</span>
            </div>
          ))}
          <div className="font-mono text-[11px] text-console-text-muted mt-1">{active} active</div>
        </div>
      )}
    </ModuleCard>
  );
}

export function CommPanel() {
  useCommWebSocket();
  const channels = useCommStore((s) => s.channels);
  const stale = useCommStore((s) => s.isStaleChannels);
  return (
    <ModuleCard title="Comm" to="/console/comm" stale={stale}>
      {channels.length === 0 ? (
        <Empty label="No channels." />
      ) : (
        <div className="flex flex-col gap-1.5">
          {channels.slice(0, 8).map((c) => (
            <div key={c.id} className="flex items-center gap-2 truncate">
              <span className="text-console-accent">#</span>
              <span className="truncate">{c.name}</span>
            </div>
          ))}
        </div>
      )}
    </ModuleCard>
  );
}

export function CrewPanel() {
  useCrewRoster();
  useCrewPositionsPolling();
  const roster = useCrewStore((s) => s.roster);
  const stale = useCrewStore((s) => s.isStale);
  const positions = useCrewPositionsStore((s) => s.positions);
  const onShift = new Set(positions.map((p) => p.userId));
  return (
    <ModuleCard title="Crew" to="/console/crew" stale={stale}>
      {roster.length === 0 ? (
        <Empty label="No crew." />
      ) : (
        <div className="flex flex-col gap-1.5">
          {roster.slice(0, 8).map((m) => {
            const online = onShift.has(m.id);
            return (
              <div key={m.id} className="flex items-center gap-2 truncate">
                <span
                  className={clsx('text-[10px] leading-none', online ? 'text-console-ok' : 'text-console-text-muted')}
                >
                  {online ? DOT_ON : DOT_OFF}
                </span>
                <span className="truncate">{m.displayName}</span>
              </div>
            );
          })}
        </div>
      )}
    </ModuleCard>
  );
}

export function MapPanel() {
  useJobsPolling('list');
  useCrewPositionsPolling();
  const jobs = useJobsStore((s) => s.jobs);
  const positions = useCrewPositionsStore((s) => s.positions);
  const located = jobs.filter((j) => j.latitude != null && j.longitude != null).length;
  return (
    <ModuleCard title="Map" to="/console">
      <div className="flex flex-col gap-1.5">
        <div>
          <span className="font-mono text-console-text font-semibold tabular-nums">{jobs.length}</span> jobs{' '}
          <span className="text-console-text-muted">({located} located)</span>
        </div>
        <div className="flex items-center gap-2">
          <span className="text-console-ok text-[10px] leading-none">{DOT_ON}</span>
          <span>{positions.length} crew on site</span>
        </div>
      </div>
    </ModuleCard>
  );
}
