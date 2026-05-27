import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, beforeEach } from 'vitest';
import { JobStageControls } from '../JobStageControls';
import { useJobsStore } from '../../../stores/jobsStore';
import { useMaterialsStore } from '../../../stores/materialsStore';
import type { Job, JobStage } from '../../../api/jobsClient';

function mockJob(stage: JobStage): Job {
  return {
    id: 'j1', foremanId: 'f-1', clientId: null, engagementId: null,
    title: 't', description: null, status: 'planned', stage,
    scheduledAt: null, location: null, latitude: null, longitude: null,
    geocodedAt: null, createdAt: '2026-05-11T10:00:00Z',
    updatedAt: '2026-05-11T11:00:00Z', client: null,
  };
}

describe('JobStageControls', () => {
  beforeEach(() => {
    useJobsStore.getState().clear();
    useMaterialsStore.getState().clear();
  });

  it('lead -> shows CREATE PROPOSAL', () => {
    render(<JobStageControls job={mockJob('lead')} />);
    expect(screen.getByRole('button', { name: /create proposal/i })).toBeInTheDocument();
  });

  it('proposal -> shows MARK APPROVED + MARK REJECTED', () => {
    render(<JobStageControls job={mockJob('proposal')} />);
    expect(screen.getByRole('button', { name: /mark approved/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /mark rejected/i })).toBeInTheDocument();
  });

  it('approved -> shows START WORK', () => {
    render(<JobStageControls job={mockJob('approved')} />);
    expect(screen.getByRole('button', { name: /start work/i })).toBeInTheDocument();
  });

  it('in_progress -> shows MARK WORK COMPLETE', () => {
    render(<JobStageControls job={mockJob('in_progress')} />);
    expect(screen.getByRole('button', { name: /mark work complete/i })).toBeInTheDocument();
  });

  it('review -> shows GENERATE INVOICE', () => {
    render(<JobStageControls job={mockJob('review')} />);
    expect(screen.getByRole('button', { name: /generate invoice/i })).toBeInTheDocument();
  });

  it('invoice -> shows MARK PAID - CLOSE + REOPEN INVOICE', () => {
    render(<JobStageControls job={mockJob('invoice')} />);
    expect(screen.getByRole('button', { name: /mark paid - close/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /reopen invoice/i })).toBeInTheDocument();
  });

  it('closed -> shows REOPEN JOB only', () => {
    render(<JobStageControls job={mockJob('closed')} />);
    expect(screen.getByRole('button', { name: /reopen job/i })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /create proposal/i })).not.toBeInTheDocument();
  });

  it('clicking a forward button calls changeStage and upserts the job', async () => {
    useJobsStore.getState().setJobs([mockJob('lead')]);
    render(<JobStageControls job={mockJob('lead')} />);
    fireEvent.click(screen.getByRole('button', { name: /create proposal/i }));
    await waitFor(() => {
      const j = useJobsStore.getState().jobs.find((x) => x.id === 'j1');
      expect(j?.stage).toBe('proposal');
    });
  });

  it('shows the unchecked-materials warning when stage is review', () => {
    useMaterialsStore.getState().setForJob('j1', [
      { id: 'a', jobId: 'j1', name: 'X', notes: null, checked: false, checkedAt: null,
        quantity: 1, unit: 'ea', unitCost: 0, vendor: null, createdAt: '', updatedAt: '' },
      { id: 'b', jobId: 'j1', name: 'Y', notes: null, checked: false, checkedAt: null,
        quantity: 1, unit: 'ea', unitCost: 0, vendor: null, createdAt: '', updatedAt: '' },
      { id: 'c', jobId: 'j1', name: 'Z', notes: null, checked: true, checkedAt: '2026-05-26T11:00:00Z',
        quantity: 1, unit: 'ea', unitCost: 0, vendor: null, createdAt: '', updatedAt: '' },
    ]);
    render(<JobStageControls job={mockJob('review')} />);
    expect(screen.getByText(/2 materials not checked off/)).toBeInTheDocument();
    // Warning is non-blocking: button remains enabled
    expect(screen.getByRole('button', { name: /generate invoice/i })).toBeEnabled();
  });

  it('does not show the warning when stage is not review', () => {
    useMaterialsStore.getState().setForJob('j1', [
      { id: 'a', jobId: 'j1', name: 'X', notes: null, checked: false, checkedAt: null,
        quantity: 1, unit: 'ea', unitCost: 0, vendor: null, createdAt: '', updatedAt: '' },
    ]);
    render(<JobStageControls job={mockJob('in_progress')} />);
    expect(screen.queryByText(/not checked off/)).not.toBeInTheDocument();
  });
});
