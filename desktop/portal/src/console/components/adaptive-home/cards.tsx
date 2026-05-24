import { ReactNode, useEffect, useState } from 'react';
import { NavLink } from 'react-router-dom';
import { useCurrentTime } from '../../hooks/useCurrentTime';
import { ClockButton } from '../header/ClockButton';
import { ShareLocationToggle } from '../header/ShareLocationToggle';
import { useJobsPolling } from '../../hooks/useJobsPolling';
import { useJobsStore } from '../../stores/jobsStore';
import { tasksClient, type Task } from '../../api/tasksClient';
import type { JobStatus } from '../../api/jobsClient';
import { useAdminHealth } from '../../hooks/useAdminHealth';
import { useAdminHealthStore } from '../../stores/adminHealthStore';
import { useCrewPositionsPolling } from '../../hooks/useCrewPositionsPolling';
import { useCrewPositionsStore } from '../../stores/crewPositionsStore';
import { useInvoicesPolling } from '../../hooks/useInvoicesPolling';
import { useInvoicesStore } from '../../stores/invoicesStore';
import type { InvoiceStatus } from '../../api/invoicesClient';
import { MapCanvas } from '../map/MapCanvas';

// Two dashboard MODULES that the app has but the portal has no full route for
// (Android: the TIME CLOCK and the TODAY'S TASKS module). Built from real portal
// data, in the app's module idiom: an ALL-CAPS mono header + content, filling the
// same hairline pane as the other containers (AdaptiveDashboard wraps each in a
// Pane, so no own border here -- keeps the dashboard visually uniform).

function ModuleCard({ title, right, children }: { title: string; right?: ReactNode; children: ReactNode }) {
  return (
    <div className="h-full flex flex-col min-h-0">
      <div className="flex items-center justify-between mb-2">
        <span className="font-mono text-[11px] uppercase tracking-wide font-medium text-console-text-muted">{title}</span>
        {right}
      </div>
      <div className="flex-1 min-h-0 overflow-y-auto font-sans text-[13px] text-console-text">{children}</div>
    </div>
  );
}

// MAP PREVIEW -- a chrome-less locator map (just the canvas, with job + crew
// markers). NO + Create Job / stats / job side-panel: the Jobs card is the single
// place to create + manage jobs. The full map screen (with create) lives at /console.
const ALL_STATUSES: JobStatus[] = ['planned', 'in_progress', 'complete', 'cancelled'];

export function MapPreview() {
  useJobsPolling('list');
  useCrewPositionsPolling();
  const jobs = useJobsStore((s) => s.jobs);
  const positions = useCrewPositionsStore((s) => s.positions);
  return (
    <div className="h-full w-full relative">
      <MapCanvas
        jobs={jobs}
        crewPositions={positions}
        visibleStatuses={ALL_STATUSES}
        selectedJobId={null}
        onSelectJob={() => {}}
      />
    </div>
  );
}

// SHIFT -- live clock + the real clock-in/out and share-location controls.
export function ShiftCard() {
  const { hh, mm, ss } = useCurrentTime();
  return (
    <ModuleCard title="Shift">
      <div className="flex flex-col gap-3">
        <div className="font-mono tabular-nums">
          <span className="text-console-text text-2xl">
            {hh}:{mm}
          </span>
          <span className="text-console-text-muted text-sm">:{ss}</span>
        </div>
        <div className="flex flex-wrap items-center gap-2">
          <ClockButton />
          <ShareLocationToggle />
        </div>
      </div>
    </ModuleCard>
  );
}

// OPEN TASKS -- real pending tasks aggregated across the user's active jobs.
// Tasks are per-job (tasksClient.listForJob); there's no cross-job feed, so we
// fan out over the active jobs and flatten. Re-fetches only when the set of
// active job ids changes (not on every 15s jobs poll).
export function OpenTasksCard() {
  useJobsPolling('list');
  const jobs = useJobsStore((s) => s.jobs);
  const activeJobs = jobs.filter((j) => j.status === 'planned' || j.status === 'in_progress');
  const activeKey = activeJobs.map((j) => j.id).sort().join(',');
  const [items, setItems] = useState<{ task: Task; jobTitle: string }[]>([]);

  useEffect(() => {
    if (!activeKey) {
      setItems([]);
      return;
    }
    let alive = true;
    const active = useJobsStore
      .getState()
      .jobs.filter((j) => j.status === 'planned' || j.status === 'in_progress');
    Promise.all(
      active.map(async (j) => {
        const r = await tasksClient.listForJob(j.id);
        return r.ok ? r.tasks.filter((t) => t.status === 'pending').map((task) => ({ task, jobTitle: j.title })) : [];
      }),
    ).then((lists) => {
      if (alive) setItems(lists.flat());
    });
    return () => {
      alive = false;
    };
  }, [activeKey]);

  return (
    <ModuleCard
      title="Open Tasks"
      right={<span className="font-mono text-[11px] text-console-text-muted tabular-nums">{items.length}</span>}
    >
      {items.length === 0 ? (
        <div className="text-console-text-muted">No open tasks.</div>
      ) : (
        <div className="flex flex-col gap-1.5">
          {items.slice(0, 12).map(({ task, jobTitle }) => (
            <div key={task.id} className="flex items-baseline gap-2">
              <span className="text-console-text-muted text-[10px] leading-none">○</span>
              <span className="truncate">{task.title}</span>
              <span className="text-console-text-muted text-[11px] truncate ml-auto pl-2">{jobTitle}</span>
            </div>
          ))}
        </div>
      )}
    </ModuleCard>
  );
}

// DISPATCH -- active jobs that still need scheduling (real, from the jobs list:
// active status + no scheduledAt). Mirrors the app's DISPATCH module intent
// (jobs needing attention) with the data the portal list actually exposes.
export function DispatchCard() {
  useJobsPolling('list');
  const jobs = useJobsStore((s) => s.jobs);
  const toSchedule = jobs.filter(
    (j) => (j.status === 'planned' || j.status === 'in_progress') && !j.scheduledAt,
  );
  return (
    <ModuleCard
      title="Dispatch"
      right={<span className="font-mono text-[11px] text-console-warn tabular-nums">{toSchedule.length}</span>}
    >
      {toSchedule.length === 0 ? (
        <div className="text-console-text-muted">All active jobs scheduled.</div>
      ) : (
        <div className="flex flex-col gap-1.5">
          {toSchedule.slice(0, 10).map((j) => (
            <div key={j.id} className="flex items-baseline gap-2">
              <span className="text-console-warn text-[10px] leading-none">!</span>
              <span className="truncate">{j.title}</span>
              <span className="text-console-text-muted text-[11px] ml-auto pl-2 whitespace-nowrap">to schedule</span>
            </div>
          ))}
        </div>
      )}
    </ModuleCard>
  );
}

// SYSTEM -- background worker + queue health (real, GET /api/admin/health).
// Admin-only: silently empty ("Idle.") for non-admins, like the app's MESH HUB
// module shows "idle" when there's nothing to report.
export function SystemCard() {
  useAdminHealth();
  const data = useAdminHealthStore((s) => s.data);
  const stale = useAdminHealthStore((s) => s.isStale);
  const workers = data?.workers.length ?? 0;
  const queued =
    data?.queue.byKindState.filter((q) => q.state === 'queued').reduce((n, q) => n + q.count, 0) ?? 0;
  return (
    <ModuleCard
      title="System"
      right={stale ? <span className="text-console-warn text-[11px]">[offline]</span> : undefined}
    >
      {data ? (
        <div className="flex flex-col gap-1.5">
          <div>
            <span className="text-console-ok text-[10px] leading-none">●</span> {workers} worker
            {workers === 1 ? '' : 's'}
          </div>
          <div className="text-console-text-muted">
            {queued} job{queued === 1 ? '' : 's'} queued
          </div>
        </div>
      ) : (
        <div className="text-console-text-muted">Idle.</div>
      )}
    </ModuleCard>
  );
}

// ── Summary panels with a status DROPDOWN ──────────────────────────────────
// Instead of cramming a full scrolling list into a small card, these show a
// status <select> (counts in each option = "all visible"), and only the chosen
// status's rows list below. [open] goes to the full screen to create/manage.

function PanelHeader({ title, to }: { title: string; to: string }) {
  return (
    <div className="flex items-center justify-between mb-2 shrink-0">
      <span className="font-mono text-[11px] uppercase tracking-wide font-medium text-console-text-muted">{title}</span>
      <NavLink to={to} className="font-mono text-[11px] text-console-accent hover:underline">
        [open]
      </NavLink>
    </div>
  );
}

const SELECT_CLASS =
  'shrink-0 w-full bg-console-bg border border-console-border rounded px-2 py-1 text-xs font-mono text-console-text focus:border-console-accent outline-none mb-2';

const JOB_STATUS_LABELS: Record<JobStatus, string> = {
  planned: 'Planned',
  in_progress: 'In progress',
  complete: 'Complete',
  cancelled: 'Cancelled',
};
const JOB_STATUS_DOT: Record<JobStatus, string> = {
  planned: 'text-console-text-muted',
  in_progress: 'text-console-warn',
  complete: 'text-console-ok',
  cancelled: 'text-console-text-muted',
};

export function JobsCard() {
  useJobsPolling('list');
  const jobs = useJobsStore((s) => s.jobs);
  const [filter, setFilter] = useState<'all' | JobStatus>('all');
  const count = (s: JobStatus) => jobs.filter((j) => j.status === s).length;
  const shown = filter === 'all' ? jobs : jobs.filter((j) => j.status === filter);
  return (
    <div className="h-full flex flex-col min-h-0">
      <PanelHeader title="Jobs" to="/console/jobs" />
      <select
        className={SELECT_CLASS}
        value={filter}
        onChange={(e) => setFilter(e.target.value as 'all' | JobStatus)}
      >
        <option value="all">All ({jobs.length})</option>
        {(Object.keys(JOB_STATUS_LABELS) as JobStatus[]).map((s) => (
          <option key={s} value={s}>
            {JOB_STATUS_LABELS[s]} ({count(s)})
          </option>
        ))}
      </select>
      <div className="flex-1 min-h-0 overflow-y-auto font-sans text-[13px] text-console-text">
        {shown.length === 0 ? (
          <div className="text-console-text-muted">No jobs.</div>
        ) : (
          <div className="flex flex-col gap-1.5">
            {shown.map((j) => (
              <div key={j.id} className="flex items-center gap-2">
                <span className={`text-[10px] leading-none ${JOB_STATUS_DOT[j.status]}`}>●</span>
                <span className="truncate">{j.title}</span>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

const INVOICE_STATUSES: InvoiceStatus[] = [
  'draft', 'issued', 'sent', 'viewed', 'paid', 'overdue', 'disputed', 'cancelled',
];
const INVOICE_STATUS_DOT: Record<InvoiceStatus, string> = {
  draft: 'text-console-text-muted',
  issued: 'text-console-text-muted',
  sent: 'text-console-accent',
  viewed: 'text-console-accent',
  paid: 'text-console-ok',
  overdue: 'text-console-warn',
  disputed: 'text-console-warn',
  cancelled: 'text-console-text-muted',
};

export function InvoicesCard() {
  useInvoicesPolling('list');
  const invoices = useInvoicesStore((s) => s.invoices);
  const [filter, setFilter] = useState<'all' | InvoiceStatus>('all');
  const count = (s: InvoiceStatus) => invoices.filter((i) => i.status === s).length;
  const shown = filter === 'all' ? invoices : invoices.filter((i) => i.status === filter);
  return (
    <div className="h-full flex flex-col min-h-0">
      <PanelHeader title="Invoices" to="/console/invoices" />
      <select
        className={SELECT_CLASS}
        value={filter}
        onChange={(e) => setFilter(e.target.value as 'all' | InvoiceStatus)}
      >
        <option value="all">All ({invoices.length})</option>
        {INVOICE_STATUSES.map((s) => (
          <option key={s} value={s}>
            {s[0].toUpperCase() + s.slice(1)} ({count(s)})
          </option>
        ))}
      </select>
      <div className="flex-1 min-h-0 overflow-y-auto font-sans text-[13px] text-console-text">
        {shown.length === 0 ? (
          <div className="text-console-text-muted">No invoices.</div>
        ) : (
          <div className="flex flex-col gap-1.5">
            {shown.map((inv) => (
              <div key={inv.id} className="flex items-center gap-2">
                <span className={`text-[10px] leading-none ${INVOICE_STATUS_DOT[inv.status]}`}>●</span>
                <span className="truncate">{inv.invoiceNumber}</span>
                <span className="text-console-text-muted truncate">{inv.clientName ?? ''}</span>
                <span className="ml-auto tabular-nums pl-2">${Math.round(inv.totalDue).toLocaleString()}</span>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
