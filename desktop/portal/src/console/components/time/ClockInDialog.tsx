import { useEffect, useState } from 'react';
import { Modal } from '../ui/Modal';
import { Button } from '../ui/Button';
import { jobsClient, type Job } from '../../api/jobsClient';
import type { ClockInOpts } from '../header/useShiftToggle';

const ENTRY_TYPES: { value: string; label: string }[] = [
  { value: 'regular', label: 'Regular' },
  { value: 'overtime', label: 'Overtime' },
  { value: 'break', label: 'Break' },
  { value: 'travel', label: 'Travel' },
  { value: 'on_call', label: 'On call' },
];

interface Props {
  open: boolean;
  onClose: () => void;
  onConfirm: (opts: ClockInOpts) => void;
}

export function ClockInDialog({ open, onClose, onConfirm }: Props) {
  const [entryType, setEntryType] = useState('regular');
  const [jobId, setJobId] = useState<string | null>(null);
  const [jobText, setJobText] = useState('');
  const [boardJobs, setBoardJobs] = useState<Job[]>([]);

  // Optional board picker: only foreman/enterprise can fetch /api/jobs (others
  // get 403). On 403 we silently show free-text only.
  useEffect(() => {
    if (!open) return;
    let alive = true;
    void jobsClient.list().then((r) => {
      if (alive && r.ok) setBoardJobs(r.jobs);
    });
    return () => {
      alive = false;
    };
  }, [open]);

  const confirm = () => {
    const opts: ClockInOpts = { entryType };
    if (jobId) opts.jobId = jobId;
    const title = jobText.trim() || boardJobs.find((j) => j.id === jobId)?.title;
    if (title) opts.jobTitle = title;
    onConfirm(opts);
  };

  return (
    <Modal open={open} onClose={onClose} title="Clock in">
      <div className="flex flex-col gap-3 text-console-text text-sm">
        <div className="flex flex-wrap gap-2">
          {ENTRY_TYPES.map((t) => (
            <button
              key={t.value}
              type="button"
              onClick={() => setEntryType(t.value)}
              className={`px-2 py-1 rounded border ${entryType === t.value ? 'border-console-accent text-console-accent' : 'border-console-border text-console-text-muted'}`}
            >
              {t.label}
            </button>
          ))}
        </div>

        {boardJobs.length > 0 && (
          <select
            className="bg-console-bg border border-console-border rounded px-2 py-1 text-xs"
            value={jobId ?? ''}
            onChange={(e) => {
              setJobId(e.target.value || null);
              if (e.target.value) setJobText('');
            }}
          >
            <option value="">No job (general time)</option>
            {boardJobs.map((j) => (
              <option key={j.id} value={j.id}>{j.title}</option>
            ))}
          </select>
        )}

        <input
          className="bg-console-bg border border-console-border rounded px-2 py-1 text-xs"
          placeholder="Or enter job name"
          value={jobText}
          onChange={(e) => {
            setJobText(e.target.value);
            if (e.target.value) setJobId(null);
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
