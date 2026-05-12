import { useState } from 'react';
import { Button } from '../ui/Button';
import { jobsClient, JobStatus, Job } from '../../api/jobsClient';
import { useToast } from '../../hooks/useToast';

const NEXT_STATES: Record<JobStatus, { label: string; status: JobStatus }[]> = {
  planned: [
    { label: '[>] Start', status: 'in_progress' },
    { label: '[x] Cancel', status: 'cancelled' },
  ],
  in_progress: [
    { label: '[+] Complete', status: 'complete' },
    { label: '[x] Cancel', status: 'cancelled' },
  ],
  complete: [],
  cancelled: [],
};

interface Props {
  jobId: string;
  status: JobStatus;
  onChanged: (job: Job) => void;
}

export function StatusButtons({ jobId, status, onChanged }: Props) {
  const [pending, setPending] = useState<JobStatus | null>(null);
  const toast = useToast();

  const next = NEXT_STATES[status];
  if (next.length === 0) return null;

  async function handleClick(target: JobStatus) {
    setPending(target);
    const result = await jobsClient.changeStatus(jobId, target);
    setPending(null);
    if (result.ok) {
      onChanged(result.job);
    } else if (result.code === 'invalid_status_transition') {
      toast.error(`Server rejected this transition (was ${result.from}, tried ${result.to}). Refreshing...`);
    } else {
      toast.error(result.error || 'Failed to change status');
    }
  }

  return (
    <div className="flex gap-2">
      {next.map((opt) => (
        <Button
          key={opt.status}
          onClick={() => handleClick(opt.status)}
          disabled={pending !== null}
        >
          {pending === opt.status ? `${opt.label}...` : opt.label}
        </Button>
      ))}
    </div>
  );
}
