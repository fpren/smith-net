// desktop/portal/src/console/components/map/JobMarker.tsx
import type { Job, JobStatus } from '../../api/jobsClient';

const GLYPH: Record<JobStatus, string> = {
  planned: 'P',
  in_progress: '>',
  complete: 'o',
  cancelled: 'x',
};

export function createJobMarkerElement(job: Job): HTMLDivElement {
  const el = document.createElement('div');
  el.className = `job-marker job-marker-${job.status}`;
  el.textContent = GLYPH[job.status];
  el.setAttribute('data-job-id', job.id);
  el.setAttribute('aria-label', `${job.status} job ${job.title}`);
  return el;
}
