import { ReactNode, useEffect, useState } from 'react';
import { NavLink, useNavigate } from 'react-router-dom';
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
import { useNotificationsPolling } from '../../hooks/useNotificationsPolling';
import { useNotificationsStore } from '../../stores/notificationsStore';
import { notificationsClient, type NotificationItem } from '../../api/notificationsClient';
import { MapCanvas } from '../map/MapCanvas';
import { LoadingState, EmptyState, ErrorState } from '../ui/StateViews';

// Two dashboard MODULES that the app has but the portal has no full route for
// (Android: the TIME CLOCK and the TODAY'S TASKS module). Built from real portal
// data, in the app's module idiom: an ALL-CAPS mono header + content, filling the
// same hairline pane as the other containers (AdaptiveDashboard wraps each in a
// Pane, so no own border here -- keeps the dashboard visually uniform).

function ModuleCard({ title, right, children }: { title: string; right?: ReactNode; children: ReactNode }) {
  return (
    <div className="h-full flex flex-col min-h-0">
      <div className="flex items-center justify-between mb-2">
        <span className="font-mono text-[11px] uppercase tracking-wide font-medium text-sn-ink-muted">{title}</span>
        {right}
      </div>
      <div className="flex-1 min-h-0 overflow-y-auto font-sans text-[13px] text-sn-ink">{children}</div>
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
          <span className="text-sn-ink text-2xl">
            {hh}:{mm}
          </span>
          <span className="text-sn-ink-muted text-sm">:{ss}</span>
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
  // Local loading flag for the per-job task fan-out (below): there's no
  // shared polling store/hook backing this fetch (tasksClient.listForJob is
  // called once per active job, not a single collection endpoint), so it
  // doesn't have a listStale/isLoadingList pair to read like the other
  // cards. Per-job failures are already silently treated as "no tasks for
  // that job" by the `r.ok ? ... : []` fallback below (pre-existing), so
  // there's no failure signal left to surface as an ErrorState here.
  const [loadingTasks, setLoadingTasks] = useState(false);

  useEffect(() => {
    if (!activeKey) {
      setItems([]);
      setLoadingTasks(false);
      return;
    }
    let alive = true;
    setLoadingTasks(true);
    const active = useJobsStore
      .getState()
      .jobs.filter((j) => j.status === 'planned' || j.status === 'in_progress');
    Promise.all(
      active.map(async (j) => {
        const r = await tasksClient.listForJob(j.id);
        return r.ok ? r.tasks.filter((t) => t.status === 'pending').map((task) => ({ task, jobTitle: j.title })) : [];
      }),
    ).then((lists) => {
      if (!alive) return;
      setItems(lists.flat());
      setLoadingTasks(false);
    });
    return () => {
      alive = false;
    };
  }, [activeKey]);

  return (
    <ModuleCard
      title="Open Tasks"
      right={<span className="font-mono text-[11px] text-sn-ink-muted tabular-nums">{items.length}</span>}
    >
      {loadingTasks && items.length === 0 ? (
        <LoadingState label="Loading tasks" />
      ) : items.length === 0 ? (
        <EmptyState title="No open tasks." />
      ) : (
        <div className="flex flex-col gap-1.5">
          {items.slice(0, 12).map(({ task, jobTitle }) => (
            <div key={task.id} className="flex items-baseline gap-2">
              <span className="text-sn-ink-muted text-[10px] leading-none">○</span>
              <span className="truncate">{task.title}</span>
              <span className="text-sn-ink-muted text-[11px] truncate ml-auto pl-2">{jobTitle}</span>
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
  const { reload } = useJobsPolling('list');
  const jobs = useJobsStore((s) => s.jobs);
  const isLoadingList = useJobsStore((s) => s.isLoadingList);
  const listStale = useJobsStore((s) => s.listStale);
  const toSchedule = jobs.filter(
    (j) => (j.status === 'planned' || j.status === 'in_progress') && !j.scheduledAt,
  );
  return (
    <ModuleCard
      title="Dispatch"
      right={<span className="font-mono text-[11px] text-sn-attention tabular-nums">{toSchedule.length}</span>}
    >
      {isLoadingList && jobs.length === 0 ? (
        <LoadingState label="Loading jobs" />
      ) : listStale && jobs.length === 0 ? (
        <ErrorState message="Couldn't load jobs." onRetry={reload} />
      ) : toSchedule.length === 0 ? (
        // Not the trio's EmptyState: this is a filtered-to-zero view of an
        // otherwise-loaded jobs list (nothing needs scheduling right now),
        // not "the primary collection failed to load" -- same distinction
        // ClientsListRoute/JobsCard's status filter draws (task 7 report).
        <div className="text-sn-ink-muted">All active jobs scheduled.</div>
      ) : (
        <div className="flex flex-col gap-1.5">
          {toSchedule.slice(0, 10).map((j) => (
            <div key={j.id} className="flex items-baseline gap-2">
              <span className="text-sn-attention text-[10px] leading-none">!</span>
              <span className="truncate">{j.title}</span>
              <span className="text-sn-ink-muted text-[11px] ml-auto pl-2 whitespace-nowrap">to schedule</span>
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
  const { reload } = useAdminHealth();
  const data = useAdminHealthStore((s) => s.data);
  const isLoading = useAdminHealthStore((s) => s.isLoading);
  const stale = useAdminHealthStore((s) => s.isStale);
  const workers = data?.workers.length ?? 0;
  const queued =
    data?.queue.byKindState.filter((q) => q.state === 'queued').reduce((n, q) => n + q.count, 0) ?? 0;
  return (
    <ModuleCard
      title="System"
      right={stale && data ? <span className="text-sn-attention text-[11px]">[offline]</span> : undefined}
    >
      {isLoading && !data ? (
        <LoadingState label="Loading" />
      ) : stale && !data ? (
        <ErrorState message="Couldn't load system health." onRetry={reload} />
      ) : data ? (
        <div className="flex flex-col gap-1.5">
          <div>
            <span className="text-sn-status-online text-[10px] leading-none">●</span> {workers} worker
            {workers === 1 ? '' : 's'}
          </div>
          <div className="text-sn-ink-muted">
            {queued} job{queued === 1 ? '' : 's'} queued
          </div>
        </div>
      ) : (
        // Non-admin: useAdminHealth never fetches (403 silenced at the
        // perimeter), so isLoading/data/stale never leave their initial
        // falsy state -- same silent "idle" fallback as the app's MESH HUB
        // module (see comment above).
        <div className="text-sn-ink-muted">Idle.</div>
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
      <span className="font-mono text-[11px] uppercase tracking-wide font-medium text-sn-ink-muted">{title}</span>
      <NavLink to={to} className="font-mono text-[11px] text-sn-accent hover:underline">
        [open]
      </NavLink>
    </div>
  );
}

const SELECT_CLASS =
  'shrink-0 w-full bg-sn-bg-base border border-sn-line rounded px-2 py-1 text-xs font-mono text-sn-ink focus:border-sn-accent outline-none mb-2';

const JOB_STATUS_LABELS: Record<JobStatus, string> = {
  planned: 'Planned',
  in_progress: 'In progress',
  complete: 'Complete',
  cancelled: 'Cancelled',
};
const JOB_STATUS_DOT: Record<JobStatus, string> = {
  planned: 'text-sn-ink-muted',
  in_progress: 'text-sn-attention',
  complete: 'text-sn-status-online',
  cancelled: 'text-sn-ink-muted',
};

export function JobsCard() {
  const { reload } = useJobsPolling('list');
  const jobs = useJobsStore((s) => s.jobs);
  const isLoadingList = useJobsStore((s) => s.isLoadingList);
  const listStale = useJobsStore((s) => s.listStale);
  const [filter, setFilter] = useState<'all' | JobStatus>('all');
  const count = (s: JobStatus) => jobs.filter((j) => j.status === s).length;
  const shown = filter === 'all' ? jobs : jobs.filter((j) => j.status === filter);

  if (isLoadingList && jobs.length === 0) {
    return (
      <div className="h-full flex flex-col min-h-0">
        <PanelHeader title="Jobs" to="/console/jobs" />
        <LoadingState label="Loading jobs" />
      </div>
    );
  }

  if (listStale && jobs.length === 0) {
    return (
      <div className="h-full flex flex-col min-h-0">
        <PanelHeader title="Jobs" to="/console/jobs" />
        <ErrorState message="Couldn't load jobs." onRetry={reload} />
      </div>
    );
  }

  return (
    <div className="h-full flex flex-col min-h-0">
      <PanelHeader title="Jobs" to="/console/jobs" />
      {listStale && <ErrorState message="Couldn't refresh jobs — showing cached data." onRetry={reload} />}
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
      <div className="flex-1 min-h-0 overflow-y-auto font-sans text-[13px] text-sn-ink">
        {shown.length === 0 ? (
          <EmptyState title="No jobs." />
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
  draft: 'text-sn-ink-muted',
  issued: 'text-sn-ink-muted',
  sent: 'text-sn-accent',
  viewed: 'text-sn-accent',
  paid: 'text-sn-status-online',
  overdue: 'text-sn-attention',
  disputed: 'text-sn-attention',
  cancelled: 'text-sn-ink-muted',
};

export function InvoicesCard() {
  const { reload } = useInvoicesPolling('list');
  const invoices = useInvoicesStore((s) => s.invoices);
  const isLoadingList = useInvoicesStore((s) => s.isLoadingList);
  const listStale = useInvoicesStore((s) => s.listStale);
  const [filter, setFilter] = useState<'all' | InvoiceStatus>('all');
  const count = (s: InvoiceStatus) => invoices.filter((i) => i.status === s).length;
  const shown = filter === 'all' ? invoices : invoices.filter((i) => i.status === filter);

  if (isLoadingList && invoices.length === 0) {
    return (
      <div className="h-full flex flex-col min-h-0">
        <PanelHeader title="Invoices" to="/console/invoices" />
        <LoadingState label="Loading invoices" />
      </div>
    );
  }

  if (listStale && invoices.length === 0) {
    return (
      <div className="h-full flex flex-col min-h-0">
        <PanelHeader title="Invoices" to="/console/invoices" />
        <ErrorState message="Couldn't load invoices." onRetry={reload} />
      </div>
    );
  }

  return (
    <div className="h-full flex flex-col min-h-0">
      <PanelHeader title="Invoices" to="/console/invoices" />
      {listStale && <ErrorState message="Couldn't refresh invoices — showing cached data." onRetry={reload} />}
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
      <div className="flex-1 min-h-0 overflow-y-auto font-sans text-[13px] text-sn-ink">
        {shown.length === 0 ? (
          <EmptyState title="No invoices." />
        ) : (
          <div className="flex flex-col gap-1.5">
            {shown.map((inv) => (
              <div key={inv.id} className="flex items-center gap-2">
                <span className={`text-[10px] leading-none ${INVOICE_STATUS_DOT[inv.status]}`}>●</span>
                <span className="truncate">{inv.invoiceNumber}</span>
                <span className="text-sn-ink-muted truncate">{inv.clientName ?? ''}</span>
                <span className="ml-auto tabular-nums pl-2">${Math.round(inv.totalDue).toLocaleString()}</span>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

// NOTIFICATIONS -- true alerts for the current user (job assigned, new message;
// later invoice-viewed + AI). Replaces the redundant SHIFT card on the dashboard
// (the shift clock lives in the console header now). Clicking an item marks it
// read (optimistic store + best-effort PATCH) and navigates to its in-app target.
function formatRelative(iso: string): string {
  const secs = Math.max(0, Math.floor((Date.now() - new Date(iso).getTime()) / 1000));
  if (secs < 60) return 'just now';
  const mins = Math.floor(secs / 60);
  if (mins < 60) return `${mins}m ago`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `${hrs}h ago`;
  return `${Math.floor(hrs / 24)}d ago`;
}

export function NotificationsCard() {
  const { reload } = useNotificationsPolling();
  const navigate = useNavigate();
  const notifications = useNotificationsStore((s) => s.notifications);
  const unreadCount = useNotificationsStore((s) => s.unreadCount);
  const isLoading = useNotificationsStore((s) => s.isLoading);
  const isStale = useNotificationsStore((s) => s.isStale);

  const onOpen = (item: NotificationItem) => {
    useNotificationsStore.getState().markRead(item.id);
    void notificationsClient.markRead(item.id);
    if (item.link) navigate(item.link);
  };

  return (
    <ModuleCard
      title="Notifications"
      right={
        unreadCount > 0
          ? <span className="font-mono text-[11px] text-sn-accent tabular-nums">{unreadCount}</span>
          : undefined
      }
    >
      {isLoading && notifications.length === 0 ? (
        <LoadingState label="Loading" />
      ) : isStale && notifications.length === 0 ? (
        <ErrorState message="Couldn't load notifications." onRetry={reload} />
      ) : notifications.length === 0 ? (
        <EmptyState title="No notifications." />
      ) : (
        <>
          {isStale && (
            <ErrorState message="Couldn't refresh — showing cached data." onRetry={reload} />
          )}
          <div className="flex flex-col gap-1.5">
            {notifications.slice(0, 12).map((item) => (
              <button
                key={item.id}
                type="button"
                onClick={() => onOpen(item)}
                className="flex items-baseline gap-2 text-left w-full hover:bg-sn-bg-base rounded px-1 -mx-1"
              >
                <span className={`text-[10px] leading-none ${item.readAt ? 'text-sn-ink-muted' : 'text-sn-accent'}`}>●</span>
                <span className="truncate">{item.title}</span>
                <span className="text-sn-ink-muted text-[11px] ml-auto pl-2 whitespace-nowrap">
                  {formatRelative(item.createdAt)}
                </span>
              </button>
            ))}
          </div>
        </>
      )}
    </ModuleCard>
  );
}
