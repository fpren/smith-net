// desktop/portal/src/console/components/tasks/TaskList.tsx
import { Fragment, useState } from 'react';
import { useTasksStore } from '../../stores/tasksStore';
import { tasksClient } from '../../api/tasksClient';
import { useToastStore } from '../../stores/toastStore';
import { ConfirmDialog } from '../ui/SmithDialog';
import type { Task, TaskStatus } from '../../api/tasksClient';

interface Props {
  jobId: string;
}

const EMPTY: Task[] = [];

export function TaskList({ jobId }: Props) {
  const tasks = useTasksStore((s) => s.tasksByJob[jobId] ?? EMPTY);
  const updateTask = useTasksStore((s) => s.updateTask);
  const removeTask = useTasksStore((s) => s.removeTask);
  const pushToast = useToastStore((s) => s.push);
  const [confirmingId, setConfirmingId] = useState<string | null>(null);

  async function toggle(task: Task) {
    const next: TaskStatus = task.status === 'done' ? 'pending' : 'done';
    // Optimistic update — the polling reconcile will overwrite with the
    // canonical row in ~15s if the server disagrees.
    updateTask({ ...task, status: next, completedAt: next === 'done' ? new Date().toISOString() : null });
    const r = await tasksClient.update(task.id, { status: next });
    if (r.ok) {
      updateTask(r.task);
    } else {
      // Revert.
      updateTask(task);
      pushToast({ message: r.error || 'Failed to update task', tone: 'error', duration: 3000 });
    }
  }

  async function doDelete(taskId: string) {
    const snapshot = tasks.find((t) => t.id === taskId);
    removeTask(taskId);
    const r = await tasksClient.delete(taskId);
    if (!r.ok && snapshot) {
      // Restore on failure.
      useTasksStore.getState().addTask(snapshot);
      pushToast({ message: r.error || 'Failed to delete task', tone: 'error', duration: 3000 });
    }
  }

  if (tasks.length === 0) {
    return (
      <div className="text-sn-ink-muted text-sm py-2">
        No tasks yet. Add one below.
      </div>
    );
  }

  return (
    <Fragment>
      <ul className="divide-y divide-sn-line border border-sn-line font-mono">
        {tasks.map((t) => {
          const done = t.status === 'done';
          return (
            <li key={t.id} className="flex items-center gap-2 px-3 py-2 text-sm group">
              <button
                type="button"
                onClick={() => toggle(t)}
                aria-label={done ? 'Mark task pending' : 'Mark task done'}
                className={
                  'w-5 h-5 flex items-center justify-center border text-xs flex-shrink-0 transition-colors ' +
                  (done
                    ? 'border-sn-accent text-sn-accent'
                    : 'border-sn-line text-transparent hover:border-sn-accent')
                }
              >
                {done ? '✓' : ''}
              </button>
              <span
                className={
                  'flex-1 break-words ' +
                  (done ? 'text-sn-ink-muted line-through' : 'text-sn-ink')
                }
              >
                {t.title}
              </span>
              <button
                type="button"
                onClick={() => setConfirmingId(t.id)}
                aria-label="Delete task"
                className="text-xs text-sn-ink-muted opacity-40 hover:opacity-100 focus:opacity-100 hover:text-sn-status-error focus:text-sn-status-error transition-opacity"
              >
                [x]
              </button>
            </li>
          );
        })}
      </ul>
      <ConfirmDialog
        open={confirmingId !== null}
        title="Delete task?"
        confirmLabel="Delete"
        body="It's removed from this job's task list."
        onConfirm={() => {
          const id = confirmingId;
          setConfirmingId(null);
          if (id) void doDelete(id);
        }}
        onCancel={() => setConfirmingId(null)}
      />
    </Fragment>
  );
}
