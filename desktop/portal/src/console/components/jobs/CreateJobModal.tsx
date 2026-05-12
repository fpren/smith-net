import { FormEvent, useState } from 'react';
import { Modal } from '../ui/Modal';
import { Input } from '../ui/Input';
import { Button } from '../ui/Button';
import { jobsClient, Job } from '../../api/jobsClient';
import { useToast } from '../../hooks/useToast';

interface Props {
  open: boolean;
  onClose: () => void;
  onCreated: (job: Job) => void;
}

export function CreateJobModal({ open, onClose, onCreated }: Props) {
  const [title, setTitle] = useState('');
  const [location, setLocation] = useState('');
  const [scheduledAt, setScheduledAt] = useState('');
  const [description, setDescription] = useState('');
  const [titleError, setTitleError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const toast = useToast();

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setTitleError(null);
    if (!title.trim()) {
      setTitleError('Title is required');
      return;
    }
    setBusy(true);
    const result = await jobsClient.create({
      title: title.trim(),
      ...(location ? { location } : {}),
      ...(scheduledAt ? { scheduledAt: new Date(scheduledAt).toISOString() } : {}),
      ...(description ? { description } : {}),
    });
    setBusy(false);
    if (!result.ok) {
      toast.error(result.error || 'Failed to create job');
      return;
    }
    setTitle(''); setLocation(''); setScheduledAt(''); setDescription('');
    onCreated(result.job);
    onClose();
  }

  return (
    <Modal open={open} onClose={onClose} title="Create Job">
      <form onSubmit={onSubmit} className="flex flex-col gap-3 min-w-[360px]">
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
        <div className="flex gap-2 justify-end mt-2">
          <Button variant="secondary" type="button" onClick={onClose} disabled={busy}>Cancel</Button>
          <Button type="submit" disabled={busy}>{busy ? 'Creating...' : 'Create'}</Button>
        </div>
      </form>
    </Modal>
  );
}
