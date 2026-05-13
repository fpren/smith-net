import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { MapSidePanel } from '../MapSidePanel';
import type { Job } from '../../../api/jobsClient';

const j = (id: string, status: Job['status']): Job => ({
  id, foremanId: 'f', clientId: null, engagementId: null,
  title: `Job ${id}`, description: null, status,
  scheduledAt: null, location: null,
  latitude: null, longitude: null, geocodedAt: null,
  createdAt: '2026-05-13T00:00:00Z', updatedAt: '2026-05-13T00:00:00Z',
} as any);

describe('MapSidePanel', () => {
  beforeEach(() => localStorage.clear());

  it('shows counts in each section header', () => {
    const jobs = [j('a', 'planned'), j('b', 'planned'), j('c', 'in_progress')];
    render(<MemoryRouter><MapSidePanel jobs={jobs} mode="active" onModeChange={vi.fn()} onSelectJob={vi.fn()} /></MemoryRouter>);
    expect(screen.getByText(/PLANNED \(2\)/)).toBeInTheDocument();
    expect(screen.getByText(/IN PROGRESS \(1\)/)).toBeInTheDocument();
  });

  it('clicking a job row calls onSelectJob with its id', async () => {
    const jobs = [j('a', 'planned')];
    const onSelectJob = vi.fn();
    render(<MemoryRouter><MapSidePanel jobs={jobs} mode="active" onModeChange={vi.fn()} onSelectJob={onSelectJob} /></MemoryRouter>);
    await userEvent.click(screen.getByText('Job a'));
    expect(onSelectJob).toHaveBeenCalledWith('a');
  });

  it('renders cancelled/complete sections when mode=all', () => {
    const jobs = [j('a', 'complete'), j('b', 'cancelled')];
    render(<MemoryRouter><MapSidePanel jobs={jobs} mode="all" onModeChange={vi.fn()} onSelectJob={vi.fn()} /></MemoryRouter>);
    expect(screen.getByText(/COMPLETE \(1\)/)).toBeInTheDocument();
    expect(screen.getByText(/CANCELLED \(1\)/)).toBeInTheDocument();
  });
});
