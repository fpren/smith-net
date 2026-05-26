import { render, screen } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import { JobStageBar } from '../JobStageBar';

describe('JobStageBar', () => {
  it('renders 7 stage dots', () => {
    const { container } = render(<JobStageBar stage="lead" />);
    expect(container.querySelectorAll('[data-stage-dot]')).toHaveLength(7);
  });

  it('shows the current stage label in uppercase', () => {
    render(<JobStageBar stage="in_progress" />);
    expect(screen.getByText('IN PROGRESS')).toBeInTheDocument();
  });

  it('marks the current dot as active', () => {
    const { container } = render(<JobStageBar stage="review" />);
    const active = container.querySelector('[data-stage-dot-active="true"]');
    expect(active).toHaveAttribute('data-stage', 'review');
  });
});
