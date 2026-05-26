import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { JobCard } from '../JobCard';
import type { Job } from '../../../api/jobsClient';

const baseJob: Job = {
  id: 'abcdef1234567890',
  foremanId: 'f-1',
  clientId: null,
  client: null,
  engagementId: null,
  title: 'Install panel',
  description: null,
  status: 'planned',
  stage: 'lead',
  scheduledAt: null,
  location: '123 Main St',
  latitude: null,
  longitude: null,
  geocodedAt: null,
  createdAt: '2026-05-11T10:00:00Z',
  updatedAt: '2026-05-11T10:00:00Z',
};

describe('JobCard', () => {
  it('renders title', () => {
    render(<MemoryRouter><JobCard job={baseJob} /></MemoryRouter>);
    expect(screen.getByText('Install panel')).toBeInTheDocument();
  });

  it('renders location', () => {
    render(<MemoryRouter><JobCard job={baseJob} /></MemoryRouter>);
    expect(screen.getByText('123 Main St')).toBeInTheDocument();
  });

  it('renders "unscheduled" when scheduledAt is null', () => {
    render(<MemoryRouter><JobCard job={baseJob} /></MemoryRouter>);
    expect(screen.getByText('unscheduled')).toBeInTheDocument();
  });

  it('renders id prefix (first 8 chars after #)', () => {
    render(<MemoryRouter><JobCard job={baseJob} /></MemoryRouter>);
    expect(screen.getByText('#abcdef12')).toBeInTheDocument();
  });

  it('detail link href points at /console/jobs/:id', () => {
    render(<MemoryRouter><JobCard job={baseJob} /></MemoryRouter>);
    const link = screen.getByRole('link', { name: /detail/i });
    expect(link).toHaveAttribute('href', `/console/jobs/${baseJob.id}`);
  });
});
