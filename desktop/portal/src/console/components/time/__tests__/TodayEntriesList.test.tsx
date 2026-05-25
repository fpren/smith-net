import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { TodayEntriesList } from '../TodayEntriesList';
import type { TimeEntryRow } from '../../../api/presenceClient';

function row(o: Partial<TimeEntryRow>): TimeEntryRow {
  return { id: 'r', startedAt: '2026-05-25T09:00:00Z', endedAt: '2026-05-25T10:30:00Z', source: 'web', entryType: 'regular', jobId: null, jobTitle: null, taskId: null, taskTitle: null, clockOutReason: null, ...o };
}

describe('TodayEntriesList', () => {
  it('renders the empty state', () => {
    render(<TodayEntriesList entries={[]} />);
    expect(screen.getByText(/no entries/i)).toBeInTheDocument();
  });

  it('renders a closed entry with type, job, reason and duration', () => {
    render(<TodayEntriesList entries={[row({ jobTitle: 'Kitchen', clockOutReason: 'lunch' })]} />);
    expect(screen.getByText(/REGULAR/i)).toBeInTheDocument();
    expect(screen.getByText(/Kitchen/)).toBeInTheDocument();
    expect(screen.getByText(/1:30/)).toBeInTheDocument(); // 90 min duration
  });

  it('shows NOW for an active (open) entry', () => {
    render(<TodayEntriesList entries={[row({ endedAt: null })]} />);
    expect(screen.getByText(/NOW/)).toBeInTheDocument();
  });
});
