import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { JobPopup } from '../JobPopup';
import type { Job, CrewAssignment } from '../../../api/jobsClient';

const job: Job = {
  id: 'j-1', foremanId: 'f', clientId: null, engagementId: null,
  title: 'Service call', description: null, status: 'in_progress',
  scheduledAt: null, location: '47 Maple Ave',
  latitude: 40.7, longitude: -73.9, geocodedAt: '2026-05-13T00:00:00Z',
  createdAt: '2026-05-13T00:00:00Z', updatedAt: '2026-05-13T00:00:00Z',
} as any;

const crew: CrewAssignment[] = [
  { jobId: 'j-1', profileId: 'p-1', roleOnJob: 'lead', assignedAt: '2026-05-13T00:00:00Z' },
];

describe('JobPopup', () => {
  it('renders status badge + title + location', () => {
    render(<MemoryRouter><JobPopup job={job} crew={[]} /></MemoryRouter>);
    expect(screen.getByText(/IN PROGRESS/i)).toBeInTheDocument();
    expect(screen.getByText('Service call')).toBeInTheDocument();
    expect(screen.getByText('47 Maple Ave')).toBeInTheDocument();
  });

  it('renders crew list when present', () => {
    render(<MemoryRouter><JobPopup job={job} crew={crew} /></MemoryRouter>);
    expect(screen.getByText(/CREW \(1\)/)).toBeInTheDocument();
    expect(screen.getByText(/p-1/)).toBeInTheDocument();
    expect(screen.getByText(/\(lead\)/)).toBeInTheDocument();
  });

  it('renders "No crew assigned." when crew is empty', () => {
    render(<MemoryRouter><JobPopup job={job} crew={[]} /></MemoryRouter>);
    expect(screen.getByText(/No crew assigned/i)).toBeInTheDocument();
  });

  it('detail link points at /console/jobs/:id', () => {
    render(<MemoryRouter><JobPopup job={job} crew={[]} /></MemoryRouter>);
    expect(screen.getByRole('link', { name: /open detail/i })).toHaveAttribute('href', '/console/jobs/j-1');
  });
});
