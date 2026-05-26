import type { JobStage } from '../../api/jobsClient';

const STAGES: JobStage[] = [
  'lead', 'proposal', 'approved', 'in_progress', 'review', 'invoice', 'closed',
];

const LABELS: Record<JobStage, string> = {
  lead: 'LEAD',
  proposal: 'PROPOSAL',
  approved: 'APPROVED',
  in_progress: 'IN PROGRESS',
  review: 'REVIEW',
  invoice: 'INVOICE',
  closed: 'CLOSED',
};

export function JobStageBar({ stage }: { stage: JobStage }) {
  const currentIdx = STAGES.indexOf(stage);
  return (
    <div className="font-mono bg-console-surface border border-console-border px-4 py-3 mb-3">
      <div className="flex items-center">
        {STAGES.map((s, i) => {
          const filled = i <= currentIdx;
          const active = i === currentIdx;
          return (
            <div key={s} className="flex items-center flex-1 last:flex-none">
              <div className="flex items-center">
                <span className={filled ? 'text-console-accent' : 'text-console-text-muted/40'}>(</span>
                <span
                  data-stage-dot
                  data-stage={s}
                  data-stage-dot-active={active ? 'true' : 'false'}
                  className={[
                    'mx-1 inline-block rounded-full',
                    active ? 'h-2.5 w-2.5' : 'h-2 w-2',
                    filled
                      ? 'bg-console-accent'
                      : 'border border-console-text-muted/40',
                  ].join(' ')}
                />
                <span className={filled ? 'text-console-accent' : 'text-console-text-muted/40'}>)</span>
              </div>
              {i < STAGES.length - 1 && (
                <div
                  className={[
                    'flex-1 h-px mx-1',
                    i < currentIdx ? 'bg-console-accent' : 'bg-console-text-muted/15',
                  ].join(' ')}
                />
              )}
            </div>
          );
        })}
      </div>
      <div className="text-center text-xs tracking-wider text-console-accent mt-2">
        {LABELS[stage]}
      </div>
    </div>
  );
}
