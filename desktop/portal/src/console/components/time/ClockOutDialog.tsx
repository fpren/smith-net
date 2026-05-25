import { useState } from 'react';
import { Modal } from '../ui/Modal';
import { Button } from '../ui/Button';

const REASONS: { value: string; label: string }[] = [
  { value: 'lunch', label: 'Lunch Break' },
  { value: 'job_done', label: 'Job Completed' },
  { value: 'end_day', label: 'End of Day' },
  { value: 'break', label: 'Short Break' },
  { value: 'other', label: 'Other' },
];

interface Props {
  open: boolean;
  onClose: () => void;
  onConfirm: (reason?: string) => void;
}

export function ClockOutDialog({ open, onClose, onConfirm }: Props) {
  const [reason, setReason] = useState<string | null>(null);
  const [custom, setCustom] = useState('');

  const confirm = () => {
    if (reason === 'other') onConfirm(custom.trim() || 'other');
    else onConfirm(reason ?? undefined);
  };

  return (
    <Modal open={open} onClose={onClose} title="Clock out">
      <div className="flex flex-col gap-3 text-console-text text-sm">
        <div className="flex flex-wrap gap-2">
          {REASONS.map((r) => (
            <button
              key={r.value}
              type="button"
              onClick={() => setReason(r.value)}
              className={`px-2 py-1 rounded border ${reason === r.value ? 'border-console-accent text-console-accent' : 'border-console-border text-console-text-muted'}`}
            >
              {r.label}
            </button>
          ))}
        </div>

        {reason === 'other' && (
          <input
            className="bg-console-bg border border-console-border rounded px-2 py-1 text-xs"
            placeholder="Specify reason"
            value={custom}
            onChange={(e) => setCustom(e.target.value)}
          />
        )}

        <div className="flex justify-end gap-2 pt-1">
          <Button variant="secondary" onClick={onClose}>Cancel</Button>
          <Button onClick={confirm} disabled={!reason}>Clock out</Button>
        </div>
      </div>
    </Modal>
  );
}
