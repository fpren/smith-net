import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { JobStatusBadge } from '../JobStatusBadge';

describe('JobStatusBadge', () => {
  it('renders PLANNED label for planned status', () => {
    render(<JobStatusBadge status="planned" />);
    expect(screen.getByText('PLANNED')).toBeInTheDocument();
  });

  it('renders IN PROGRESS label for in_progress status', () => {
    render(<JobStatusBadge status="in_progress" />);
    expect(screen.getByText('IN PROGRESS')).toBeInTheDocument();
  });

  it('renders COMPLETE label for complete status', () => {
    render(<JobStatusBadge status="complete" />);
    expect(screen.getByText('COMPLETE')).toBeInTheDocument();
  });

  it('renders CANCELLED label using the sn-status-error token for cancelled status', () => {
    const { container } = render(<JobStatusBadge status="cancelled" />);
    expect(screen.getByText('CANCELLED')).toBeInTheDocument();
    // Chip uses inline style for color (visual lift), sourced from an sn-* var().
    const el = container.firstChild as HTMLElement;
    expect(el.style.color).toBe('var(--sn-status-error)');
  });
});
