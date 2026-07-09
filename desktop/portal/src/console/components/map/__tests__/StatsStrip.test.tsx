import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { StatsStrip } from '../StatsStrip';
import type { Job } from '../../../api/jobsClient';

const j = (id: string, status: Job['status'], daysAgo: number = 0): Job => {
  const d = new Date(Date.now() - daysAgo * 86400 * 1000).toISOString();
  return {
    id, foremanId: 'f', clientId: null, engagementId: null,
    title: id, description: null, status,
    scheduledAt: null, location: null,
    latitude: null, longitude: null, geocodedAt: null,
    createdAt: d, updatedAt: d,
  } as any;
};

describe('StatsStrip', () => {
  it('counts planned and in_progress jobs', () => {
    const jobs = [j('a', 'planned'), j('b', 'planned'), j('c', 'in_progress')];
    render(<StatsStrip jobs={jobs} />);
    expect(screen.getByText(/PLANNED/)).toBeInTheDocument();
    expect(screen.getByText('2')).toBeInTheDocument();
    expect(screen.getByText(/IN PROGRESS/)).toBeInTheDocument();
    expect(screen.getByText('1')).toBeInTheDocument();
  });

  it('only counts complete/cancelled within 7 days', () => {
    const jobs = [
      j('recent-c', 'complete', 3),
      j('old-c',    'complete', 10),
      j('recent-x', 'cancelled', 1),
    ];
    render(<StatsStrip jobs={jobs} />);
    // recent complete counted; old not counted
    expect(screen.getByText(/COMPLETE/)).toBeInTheDocument();
    expect(screen.getByText(/CANCELLED/)).toBeInTheDocument();
  });

  it('renders zeros for empty jobs', () => {
    render(<StatsStrip jobs={[]} />);
    expect(screen.getByText(/PLANNED/)).toBeInTheDocument();
    expect(screen.getByText(/IN PROGRESS/)).toBeInTheDocument();
    expect(screen.getByText(/COMPLETE/)).toBeInTheDocument();
    expect(screen.getByText(/CANCELLED/)).toBeInTheDocument();
  });

  it('numeric values in StatsStrip carry tabular-nums class', () => {
    const jobs = [j('a', 'planned'), j('b', 'planned'), j('c', 'in_progress')];
    const { container } = render(<StatsStrip jobs={jobs} />);
    const tabulaNumeralSpans = container.querySelectorAll('.tabular-nums');
    expect(tabulaNumeralSpans.length).toBeGreaterThan(0);
    tabulaNumeralSpans.forEach((span) => {
      expect(span).toHaveClass('tabular-nums');
    });
  });
});
