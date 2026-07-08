// desktop/portal/src/console/routes/ClientDetailRoute.tsx
import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { Button } from '../components/ui/Button';
import { ConfirmDialog } from '../components/ui/SmithDialog';
import { ClientContactLines } from '../components/clients/ClientContactLines';
import { CreateClientModal } from '../components/clients/CreateClientModal';
import { useClientsPolling } from '../hooks/useClientsPolling';
import { useClientsStore } from '../stores/clientsStore';
import { clientsClient } from '../api/clientsClient';
import { tasksClient, type Task } from '../api/tasksClient';
import { useToast } from '../hooks/useToast';

// Mirrors the relative-time helper in adaptive-home/cards.tsx (module-local there).
function formatRelative(iso: string): string {
  const secs = Math.max(0, Math.floor((Date.now() - new Date(iso).getTime()) / 1000));
  if (secs < 60) return 'just now';
  const mins = Math.floor(secs / 60);
  if (mins < 60) return `${mins}m ago`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `${hrs}h ago`;
  return `${Math.floor(hrs / 24)}d ago`;
}

interface TaskRow { task: Task; jobTitle: string; jobId: string }
interface Ev { at: string; label: string; jobId?: string }

const TIMELINE_CAP = 15;

export function ClientDetailRoute() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const toast = useToast();
  useClientsPolling({ detail: id ?? '' });
  const client = useClientsStore((s) => s.detailClient);
  const jobs = useClientsStore((s) => s.detailJobs) as any[];
  const [showEdit, setShowEdit] = useState(false);
  const [showAllActivity, setShowAllActivity] = useState(false);
  const [taskRows, setTaskRows] = useState<TaskRow[]>([]);
  const [confirmingDelete, setConfirmingDelete] = useState(false);

  // Fan out the client's jobs to gather every task (reuses the OpenTasksCard pattern).
  // Re-runs only when the set of job ids changes, not on every 15s client poll.
  const jobsKey = jobs.map((j) => j.id).sort().join(',');
  useEffect(() => {
    if (jobs.length === 0) { setTaskRows([]); return; }
    let alive = true;
    Promise.all(
      jobs.map(async (j) => {
        const r = await tasksClient.listForJob(j.id);
        return r.ok ? r.tasks.map((task) => ({ task, jobTitle: j.title, jobId: j.id })) : [];
      }),
    ).then((lists) => { if (alive) setTaskRows(lists.flat()); });
    return () => { alive = false; };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [jobsKey]);

  const openTasks = taskRows.filter((t) => t.task.status === 'pending');

  // Activity timeline, derived from job + task timestamps (no audit endpoint exists).
  const activity = useMemo<Ev[]>(() => {
    const evs: Ev[] = [];
    for (const j of jobs) {
      if (j.createdAt) evs.push({ at: j.createdAt, label: `Job "${j.title}" created`, jobId: j.id });
      if (j.updatedAt && j.updatedAt !== j.createdAt) {
        evs.push({ at: j.updatedAt, label: `"${j.title}" -> ${j.status}`, jobId: j.id });
      }
    }
    for (const { task, jobTitle, jobId } of taskRows) {
      if (task.createdAt) evs.push({ at: task.createdAt, label: `Task "${task.title}" added (${jobTitle})`, jobId });
      if (task.completedAt) evs.push({ at: task.completedAt, label: `Task "${task.title}" completed (${jobTitle})`, jobId });
    }
    return evs.sort((a, b) => new Date(b.at).getTime() - new Date(a.at).getTime());
  }, [jobs, taskRows]);

  const shownActivity = showAllActivity ? activity : activity.slice(0, TIMELINE_CAP);

  async function onDelete() {
    if (!client) return;
    const r = await clientsClient.remove(client.id);
    if (!r.ok) {
      toast.error((r as { error?: string }).error || 'Failed to delete client');
      return;
    }
    useClientsStore.getState().removeClient(client.id);
    navigate('/console/clients');
  }

  if (!client || client.id !== id) {
    return <div className="text-console-text-muted">Loading...</div>;
  }

  return (
    <div className="font-mono">
      <Link to="/console/clients" className="text-console-accent text-sm">back to clients</Link>
      <div className="flex items-center justify-between mt-2 mb-4">
        <h1 className="text-console-text text-lg">{client.name}</h1>
        <div className="flex gap-2">
          <Button variant="danger" onClick={() => setConfirmingDelete(true)}>Delete</Button>
          <Button onClick={() => setShowEdit(true)}>Edit</Button>
        </div>
      </div>

      <ConfirmDialog
        open={confirmingDelete}
        title="Delete this client?"
        body="Their jobs and invoices keep the record, but the client entry is removed."
        confirmLabel="Delete"
        onConfirm={() => { setConfirmingDelete(false); void onDelete(); }}
        onCancel={() => setConfirmingDelete(false)}
      />

      <ClientContactLines client={client} />

      {client.notes && (
        <div className="mt-4">
          <div className="text-xs uppercase tracking-wide text-console-text-muted mb-1">Notes</div>
          <div className="text-sm text-console-text whitespace-pre-wrap">{client.notes}</div>
        </div>
      )}

      {/* Open tasks across the client's jobs */}
      <section className="mt-6">
        <div className="text-xs uppercase tracking-wide text-console-text-muted mb-2">Open tasks ({openTasks.length})</div>
        {openTasks.length === 0
          ? <div className="text-console-text-muted text-sm">No open tasks.</div>
          : <div className="border border-console-border">
              {openTasks.map(({ task, jobTitle, jobId }) => (
                <Link key={task.id} to={`/console/jobs/${jobId}`}
                  className="flex items-baseline gap-2 px-3 py-2 border-b border-console-border hover:bg-console-surface text-sm">
                  <span className="text-console-text-muted text-[10px] leading-none">o</span>
                  <span className="truncate">{task.title}</span>
                  <span className="text-console-text-muted text-xs ml-auto pl-2 truncate">{jobTitle}</span>
                </Link>
              ))}
            </div>}
      </section>

      {/* Recent jobs */}
      <section className="mt-6">
        <div className="text-xs uppercase tracking-wide text-console-text-muted mb-2">Jobs ({jobs.length})</div>
        {jobs.length === 0
          ? <div className="text-console-text-muted text-sm">No jobs for this client.</div>
          : <div className="border border-console-border">
              {jobs.map((j) => (
                <Link key={j.id} to={`/console/jobs/${j.id}`}
                  className="flex items-center justify-between px-3 py-2 border-b border-console-border hover:bg-console-surface text-sm">
                  <span className="truncate">{j.title}</span>
                  <span className="text-console-text-muted text-xs shrink-0">
                    {j.status}{j.createdAt ? ` - ${formatRelative(j.createdAt)}` : ''}
                  </span>
                </Link>
              ))}
            </div>}
      </section>

      {/* Activity timeline (derived from timestamps) */}
      <section className="mt-6">
        <div className="text-xs uppercase tracking-wide text-console-text-muted mb-2">Activity</div>
        {activity.length === 0
          ? <div className="text-console-text-muted text-sm">No activity yet.</div>
          : <div className="flex flex-col gap-1.5 text-sm">
              {shownActivity.map((e, i) => (
                <div key={i} className="flex items-baseline gap-2">
                  <span className="text-console-text-muted text-[10px] leading-none shrink-0">-</span>
                  <span className="truncate">{e.label}</span>
                  <span className="text-console-text-muted text-xs ml-auto pl-2 shrink-0">{formatRelative(e.at)}</span>
                </div>
              ))}
              {activity.length > TIMELINE_CAP && !showAllActivity && (
                <button type="button" onClick={() => setShowAllActivity(true)}
                  className="text-console-accent text-xs self-start mt-1">show more</button>
              )}
            </div>}
      </section>

      <CreateClientModal open={showEdit} onClose={() => setShowEdit(false)} editing={client} />
    </div>
  );
}
