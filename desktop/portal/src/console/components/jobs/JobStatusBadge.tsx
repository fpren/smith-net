// desktop/portal/src/console/components/jobs/JobStatusBadge.tsx
//
// Visual lift: backed by Chip (Altara palette) instead of Badge tone presets.
// Same public API.

import { Chip } from '../ui/Chip';
import type { JobStatus } from '../../api/jobsClient';

const STATUS_COLOR: Record<JobStatus, string> = {
  planned:     '#9A6F2E', // accent gold
  in_progress: '#5A8C76', // sage
  complete:    '#3A6E8C', // dusty blue (distinct from in_progress)
  cancelled:   '#8C3A3A', // brick
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
