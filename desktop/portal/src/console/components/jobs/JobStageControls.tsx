// desktop/portal/src/console/components/jobs/JobStageControls.tsx
import { useState } from 'react';
import { jobsClient } from '../../api/jobsClient';
import type { Job, JobStage } from '../../api/jobsClient';
import { useJobsStore } from '../../stores/jobsStore';
import { useToast } from '../../hooks/useToast';
import { Button } from '../ui/Button';
import { useMaterialsStore } from '../../stores/materialsStore';
import type { Material } from '../../api/materialsClient';

const EMPTY_MATERIALS: Material[] = [];

interface Transition {
  label: string;
  to: JobStage;
  variant?: 'primary' | 'secondary';
}

const TRANSITIONS: Record<JobStage, Transition[]> = {
  lead:        [{ label: 'CREATE PROPOSAL',    to: 'proposal' }],
  proposal:    [{ label: 'MARK APPROVED',      to: 'approved' },
                { label: 'MARK REJECTED',      to: 'lead',      variant: 'secondary' }],
  approved:    [{ label: 'START WORK',         to: 'in_progress' }],
  in_progress: [{ label: 'MARK WORK COMPLETE', to: 'review' }],
  review:      [{ label: 'GENERATE INVOICE',   to: 'invoice' }],
  invoice:     [{ label: 'MARK PAID - CLOSE',  to: 'closed' },
                { label: 'REOPEN INVOICE',     to: 'review',    variant: 'secondary' }],
  closed:      [{ label: 'REOPEN JOB',         to: 'invoice',   variant: 'secondary' }],
};

export function JobStageControls({ job }: { job: Job }) {
  const transitions = TRANSITIONS[job.stage] ?? [];
  const upsertJob = useJobsStore((s) => s.upsertJob);
  const toast = useToast();
  const [busy, setBusy] = useState(false);
  const materials = useMaterialsStore((s) => s.byJob[job.id] ?? EMPTY_MATERIALS);
  const uncheckedCount = materials.filter((m) => !m.checked).length;
  const showReviewWarning = job.stage === 'review' && uncheckedCount > 0;

  async function handleClick(to: JobStage) {
    if (busy) return;
    setBusy(true);
    const result = await jobsClient.changeStage(job.id, to);
    setBusy(false);
    if (result.ok) {
      upsertJob(result.job);
      toast.info(`Stage: ${to.replace('_', ' ').toUpperCase()}`);
    } else {
      toast.error(result.error || 'Failed to change stage');
    }
  }

  if (transitions.length === 0) {
    return <div className="text-console-text-muted text-xs mb-3">Job closed.</div>;
  }

  return (
    <div className="flex flex-wrap gap-2 mb-4">
      {showReviewWarning && (
        <div className="text-console-warn text-xs mb-2 w-full" role="alert">
          ! {uncheckedCount} materials not checked off
        </div>
      )}
      {transitions.map((t) => (
        <Button
          key={t.to}
          variant={t.variant ?? 'primary'}
          onClick={() => handleClick(t.to)}
          disabled={busy}
        >
          {t.label}
        </Button>
      ))}
    </div>
  );
}
