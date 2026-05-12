import { Badge } from '../ui/Badge';
import type { JobStatus } from '../../api/jobsClient';

const STATUS_TONE: Record<JobStatus, 'default' | 'ok' | 'warn' | 'danger'> = {
  planned: 'default',
  in_progress: 'ok',
  complete: 'ok',
  cancelled: 'danger',
};

const STATUS_LABEL: Record<JobStatus, string> = {
  planned: 'PLANNED',
  in_progress: 'IN PROGRESS',
  complete: 'COMPLETE',
  cancelled: 'CANCELLED',
};

export function JobStatusBadge({ status }: { status: JobStatus }) {
  return <Badge tone={STATUS_TONE[status]}>{STATUS_LABEL[status]}</Badge>;
}
