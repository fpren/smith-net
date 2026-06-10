import { FormEvent, useEffect, useState } from 'react';
import { Modal } from '../ui/Modal';
import { Input } from '../ui/Input';
import { Button } from '../ui/Button';
import { jobsClient, Job } from '../../api/jobsClient';
import { useJobsStore } from '../../stores/jobsStore';
import { useToast } from '../../hooks/useToast';
import { useClientsPolling } from '../../hooks/useClientsPolling';
import { useClientsStore } from '../../stores/clientsStore';

interface Props {
  open: boolean;
  onClose: () => void;
  job: Job;
}

// ISO timestamp -> the value a <input type="datetime-local"> expects (local YYYY-MM-DDTHH:mm).
function isoToLocalInput(iso: string | null): string {
  if (!iso) return '';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '';
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

export function EditJobModal({ open, onClose, job }: Props) {
  const [title, setTitle] = useState('');
  const [location, setLocation] = useState('');
  const [scheduledAt, setScheduledAt] = useState('');
  const [description, setDescription] = useState('');
  const [clientId, setClientId] = useState('');
  const [titleError, setTitleError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const toast = useToast();
  useClientsPolling('list');
  const clients = useClientsStore((s) => s.clients);

  // Seed the form from the job whenever the modal opens (or the job changes).
  useEffect(() => {
    if (!open) return;
    setTitle(job.title);
    setLocation(job.location ?? '');
    setScheduledAt(isoToLocalInput(job.scheduledAt));
    setDescription(job.description ?? '');
    setClientId(job.clientId ?? '');
    setTitleError(null);
  }, [open, job]);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setTitleError(null);
    if (!title.trim()) {
      setTitleError('Title is required');
      return;
    }
    setBusy(true);
    const result = await jobsClient.update(job.id, {
      title: title.trim(),
      description: description.trim() ? description.trim() : null,
      location: location.trim() ? location.trim() : null,
      scheduledAt: scheduledAt ? new Date(scheduledAt).toISOString() : null,
      clientId: clientId || null,
    });
    setBusy(false);
    if (!result.ok) {
      toast.error(result.error || 'Failed to save job');
      return;
    }
    if ('queued' in result) {
      toast.info('Saved offline — will sync when back online');
      onClose();
      return;
    }
    useJobsStore.getState().upsertJob(result.job);
    onClose();
  }

  return (
    <Modal open={open} onClose={onClose} title="Edit Job">
      <form onSubmit={onSubmit} className="flex flex-col gap-3 w-full sm:w-[360px] max-w-full">
        <Input label="Title" value={title} onChange={(e) => setTitle(e.target.value)} error={titleError ?? undefined} />
        <Input label="Location" value={location} onChange={(e) => setLocation(e.target.value)} />
        <Input label="Scheduled At" type="datetime-local" value={scheduledAt} onChange={(e) => setScheduledAt(e.target.value)} />
        <label className="flex flex-col gap-1 font-mono text-sm">
          <span className="text-console-text-muted text-xs uppercase tracking-wide">Description</span>
          <textarea
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            className="bg-console-bg border border-console-border px-3 py-2 text-console-text focus:outline-none focus:border-console-accent font-mono"
            rows={4}
          />
        </label>
        <label className="flex flex-col gap-1 font-mono text-sm">
          <span className="text-console-text-muted">Client (optional)</span>
          <select value={clientId} onChange={(e) => setClientId(e.target.value)}
            className="bg-console-bg border border-console-border rounded px-2 py-1 text-sm text-console-text focus:border-console-accent outline-none">
            <option value="">No client</option>
            {clients.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
        </label>
        <div className="flex gap-2 justify-end mt-2">
          <Button variant="secondary" type="button" onClick={onClose} disabled={busy}>Cancel</Button>
          <Button type="submit" disabled={busy}>{busy ? 'Saving...' : 'Save'}</Button>
        </div>
      </form>
    </Modal>
  );
}
