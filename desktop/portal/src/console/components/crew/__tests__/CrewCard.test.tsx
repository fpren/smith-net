import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { CrewCard } from '../CrewCard';
import type { CrewEntry } from '../../../api/crewClient';

const busy: CrewEntry = {
  id: 'a', email: 'a@x.com', displayName: 'Alice', role: 'team',
  activeJob: { id: 'j1', title: 'Maple Ave', status: 'in_progress' },
};

const free: CrewEntry = {
  id: 'b', email: 'b@x.com', displayName: 'Bob', role: 'lead', activeJob: null,
};

describe('CrewCard', () => {
  it('renders display name and email', () => {
    render(<CrewCard entry={free} />);
    expect(screen.getByText('Bob')).toBeInTheDocument();
    expect(screen.getByText('b@x.com')).toBeInTheDocument();
  });

  it('renders role chip (uppercase)', () => {
    render(<CrewCard entry={free} />);
    expect(screen.getByText('LEAD')).toBeInTheDocument();
  });

  it('renders "on <title>" when busy', () => {
    render(<CrewCard entry={busy} />);
    expect(screen.getByText(/on Maple Ave/i)).toBeInTheDocument();
  });

  it('renders "idle" when free', () => {
    render(<CrewCard entry={free} />);
    expect(screen.getByText(/idle/i)).toBeInTheDocument();
  });

  it('availability dot has correct aria-label for busy', () => {
    render(<CrewCard entry={busy} />);
    expect(screen.getByLabelText('busy')).toBeInTheDocument();
  });

  it('availability dot has correct aria-label for free', () => {
    render(<CrewCard entry={free} />);
    expect(screen.getByLabelText('free')).toBeInTheDocument();
  });
});
