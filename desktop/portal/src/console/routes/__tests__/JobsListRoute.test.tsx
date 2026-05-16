import { render, screen } from '@testing-library/react';
import { describe, it, expect, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { JobsListRoute } from '../JobsListRoute';
import { useJobsStore } from '../../stores/jobsStore';
import type { Job } from '../../api/jobsClient';

const j = (id: string, status: Job['status']): Job => ({
  id, foremanId: 'f-1', clientId: null, engagementId: null,
  title: `Job ${id}`, description: null, status,
  scheduledAt: null, location: null,
  latitude: null, longitude: null, geocodedAt: null,
  createdAt: '2026-05-11T10:00:00Z', updatedAt: '2026-05-11T10:00:00Z',
});

describe('JobsListRoute', () => {
  beforeEach(() => { useJobsStore.getState().clear(); });

  it('renders 4 status section headers', async () => {
    useJobsStore.getState().setJobs([j('a', 'planned'), j('b', 'in_progress'), j('c', 'complete'), j('d', 'cancelled')]);
    render(<MemoryRouter><JobsListRoute /></MemoryRouter>);
    // Status labels appear in section headers AND JobCard chips (visual lift).
    // Use the "X (N)" header form to disambiguate.
    expect(screen.getByText('PLANNED (1)')).toBeInTheDocument();
    expect(screen.getByText('IN PROGRESS (1)')).toBeInTheDocument();
    expect(screen.getByText('COMPLETE (1)')).toBeInTheDocument();
    expect(screen.getByText('CANCELLED (1)')).toBeInTheDocument();
  });

  it('renders correct count next to each header', () => {
    useJobsStore.getState().setJobs([j('a', 'planned'), j('b', 'planned'), j('c', 'in_progress')]);
    render(<MemoryRouter><JobsListRoute /></MemoryRouter>);
    expect(screen.getByText('PLANNED (2)')).toBeInTheDocument();
    expect(screen.getByText('IN PROGRESS (1)')).toBeInTheDocument();
    expect(screen.getByText('COMPLETE (0)')).toBeInTheDocument();
  });

  it('shows empty state when zero jobs total', () => {
    render(<MemoryRouter><JobsListRoute /></MemoryRouter>);
    expect(screen.getByText(/no jobs yet/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /create your first/i })).toBeInTheDocument();
  });

  it('renders [+ Create Job] button when jobs exist', () => {
    useJobsStore.getState().setJobs([j('a', 'planned')]);
    render(<MemoryRouter><JobsListRoute /></MemoryRouter>);
    expect(screen.getByRole('button', { name: /create job/i })).toBeInTheDocument();
  });

  it('renders stale strip when isStale is true', () => {
    useJobsStore.getState().setJobs([j('a', 'planned')]);
    useJobsStore.getState().markStale(true);
    render(<MemoryRouter><JobsListRoute /></MemoryRouter>);
    expect(screen.getByText(/couldn't refresh/i)).toBeInTheDocument();
  });
});
