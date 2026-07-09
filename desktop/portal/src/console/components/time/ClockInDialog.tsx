import { useEffect, useState } from 'react';
import { Modal } from '../ui/Modal';
import { Button } from '../ui/Button';
import { presenceClient } from '../../api/presenceClient';
import type { ClockInOpts } from '../header/useShiftToggle';

const ENTRY_TYPES: { value: string; label: string }[] = [
  { value: 'regular', label: 'Regular' },
  { value: 'overtime', label: 'Overtime' },
  { value: 'break', label: 'Break' },
  { value: 'travel', label: 'Travel' },
  { value: 'on_call', label: 'On call' },
];

type JobOption = { id: string; title: string; status: string };
type TaskOption = { id: string; title: string; status: string };

interface Props {
  open: boolean;
  onClose: () => void;
  onConfirm: (opts: ClockInOpts) => void;
}

export function ClockInDialog({ open, onClose, onConfirm }: Props) {
  const [entryType, setEntryType] = useState('regular');
  const [jobId, setJobId] = useState<string | null>(null);
  const [jobText, setJobText] = useState('');
  const [boardJobs, setBoardJobs] = useState<JobOption[]>([]);
  const [tasks, setTasks] = useState<TaskOption[]>([]);
  const [taskId, setTaskId] = useState<string | null>(null);

  // All-tier job picker: getMyJobs returns the caller's owned + assigned jobs
  // (works for solo, never 403). Reset selection state on (re)open so a prior
  // session's picks don't linger.
  useEffect(() => {
    if (!open) return;
    setEntryType('regular');
    setJobId(null);
    setJobText('');
    setTasks([]);
    setTaskId(null);
    let alive = true;
    void presenceClient.getMyJobs().then((r) => {
      if (alive && r.ok) setBoardJobs(r.jobs);
    });
    return () => {
      alive = false;
    };
  }, [open]);

  // Load this job's tasks whenever a real board job is selected.
  useEffect(() => {
    if (!jobId) {
      setTasks([]);
      setTaskId(null);
      return;
    }
    let alive = true;
    void presenceClient.getJobTasks(jobId).then((r) => {
      if (alive && r.ok) setTasks(r.tasks);
    });
    return () => {
      alive = false;
    };
  }, [jobId]);

  const confirm = () => {
    const opts: ClockInOpts = { entryType };
    const board = boardJobs.find((j) => j.id === jobId);
    if (jobId) opts.jobId = jobId;
    const title = jobText.trim() || board?.title;
    if (title) opts.jobTitle = title;
    if (jobId && taskId) {
      const task = tasks.find((t) => t.id === taskId);
      if (task) {
        opts.taskId = task.id;
        opts.taskTitle = task.title;
      }
    }
    onConfirm(opts);
  };

  return (
    <Modal open={open} onClose={onClose} title="Clock in">
      <div className="flex flex-col gap-3 text-sn-ink text-sm">
        <div className="flex flex-wrap gap-2">
          {ENTRY_TYPES.map((t) => (
            <button
              key={t.value}
              type="button"
              onClick={() => setEntryType(t.value)}
              className={`px-2 py-1 rounded border ${entryType === t.value ? 'border-sn-accent text-sn-accent' : 'border-sn-line text-sn-ink-muted'}`}
            >
              {t.label}
            </button>
          ))}
        </div>

        {boardJobs.length > 0 && (
          <select
            className="bg-sn-bg-base border border-sn-line rounded px-2 py-1 text-xs"
            value={jobId ?? ''}
            onChange={(e) => {
              const next = e.target.value || null;
              setJobId(next);
              if (next) setJobText('');
            }}
          >
            <option value="">No job (general time)</option>
            {boardJobs.map((j) => (
              <option key={j.id} value={j.id}>{j.title}</option>
            ))}
          </select>
        )}

        {jobId && tasks.length > 0 && (
          <select
            className="bg-sn-bg-base border border-sn-line rounded px-2 py-1 text-xs"
            value={taskId ?? ''}
            onChange={(e) => setTaskId(e.target.value || null)}
          >
            <option value="">No task</option>
            {tasks.map((t) => (
              <option key={t.id} value={t.id}>{t.title}</option>
            ))}
          </select>
        )}

        <input
          className="bg-sn-bg-base border border-sn-line rounded px-2 py-1 text-xs"
          placeholder="Or enter job name"
          value={jobText}
          onChange={(e) => {
            setJobText(e.target.value);
            if (e.target.value) {
              setJobId(null);
              setTasks([]);
              setTaskId(null);
            }
          }}
        />

        <div className="flex justify-end gap-2 pt-1">
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button onClick={confirm}>Clock in</Button>
        </div>
      </div>
    </Modal>
  );
}
