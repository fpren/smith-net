// desktop/portal/src/console/components/jobs/JobStatusBadge.tsx
//
// Visual lift: backed by Chip (Altara palette) instead of Badge tone presets.
// Same public API.

import { Chip } from '../ui/Chip';
import type { JobStatus } from '../../api/jobsClient';

const STATUS_COLOR: Record<JobStatus, string> = {
  planned:     'var(--sn-ink-muted)',
  in_progress: 'var(--sn-status-online)',
  complete:    'var(--sn-accent)',
  cancelled:   'var(--sn-status-error)',
};

const STATUS_LABEL: Record<JobStatus, string> = {
  planned: 'PLANNED',
  in_progress: 'IN PROGRESS',
  complete: 'COMPLETE',
  cancelled: 'CANCELLED',
};

interface Props {
  status: JobStatus;
  /** Compact variant for inline use in cards. */
  xs?: boolean;
}

export function JobStatusBadge({ status, xs }: Props) {
  return <Chip label={STATUS_LABEL[status]} color={STATUS_COLOR[status]} xs={xs} />;
}
