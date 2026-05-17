import { useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { Button } from '../components/ui/Button';
import { JobStatusBadge } from '../components/jobs/JobStatusBadge';
import { StatusButtons } from '../components/jobs/StatusButtons';
import { AssignCrewModal } from '../components/jobs/AssignCrewModal';
import { useJobsPolling } from '../hooks/useJobsPolling';
import { useJobsStore } from '../stores/jobsStore';
import { jobsClient } from '../api/jobsClient';
import { useToast } from '../hooks/useToast';

export function JobDetailRoute() {
  const { id } = useParams<{ id: string }>();
  useJobsPolling({ detail: id ?? '' });
  const job = useJobsStore((s) => s.detailJob);
  const crew = useJobsStore((s) => s.detailCrew);
  const upsertJob = useJobsStore((s) => s.upsertJob);
  const setDetail = useJobsStore((s) => s.setDetail);
  const [showAssign, setShowAssign] = useState(false);
  const toast = useToast();

  if (!job || job.id !== id) {
    return <div className="text-console-text-muted">Loading...</div>;
  }

  async function onUnassign(profileId: string) {
    const result = await jobsClient.unassignCrew(job!.id, profileId);
    if (!result.ok) {
      toast.error(result.error || 'Failed to unassign');
      return;
    }
    setDetail(job!, crew.filter((c) => c.profileId !== profileId));
  }

  return (
    <div className="font-mono">
      <Link to="/console/jobs" className="text-console-accent text-sm">back to jobs</Link>
      <div className="flex items-center gap-3 mt-2">
        <JobStatusBadge status={job.status} />
        <h1 className="text-console-text text-lg">{job.title}</h1>
      </div>
      <dl className="text-sm grid grid-cols-[12ch_1fr] gap-y-1 mt-4">
        <dt className="text-console-text-muted">id</dt>          <dd>#{job.id}</dd>
        <dt className="text-console-text-muted">scheduled</dt>   <dd>{job.scheduledAt ?? '—'}</dd>
        <dt className="text-console-text-muted">location</dt>    <dd>{job.location ?? '—'}</dd>
        <dt className="text-console-text-muted">created</dt>     <dd>{job.createdAt}</dd>
      </dl>
      <div className="mt-6">
        <StatusButtons jobId={job.id} status={job.status} onChanged={upsertJob} />
      </div>
      <div className="mt-8">
        <div className="flex flex-col items-start gap-2 md:flex-row md:items-center md:justify-between mb-2">
          <h2 className="text-console-text-muted text-xs uppercase tracking-wide">Crew ({crew.length})</h2>
          <Button onClick={() => setShowAssign(true)}>+ Assign crew</Button>
        </div>
        {crew.length === 0 && <div className="text-console-text-muted text-sm">No crew assigned.</div>}
        {crew.map((c) => (
          <div key={c.profileId} className="flex items-center justify-between border-b border-console-border px-3 py-2 text-sm">
            <span>{c.profileId} <span className="text-console-text-muted">({c.roleOnJob})</span></span>
            <button onClick={() => onUnassign(c.profileId)} className="text-console-danger">[x]</button>
          </div>
        ))}
      </div>
      <AssignCrewModal
        open={showAssign}
        jobId={job.id}
        alreadyAssigned={crew.map((c) => c.profileId)}
        onClose={() => setShowAssign(false)}
        onAssigned={(a) => setDetail(job, [...crew, a])}
      />
    </div>
  );
}
