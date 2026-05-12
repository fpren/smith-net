import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import { StatusButtons } from '../StatusButtons';

describe('StatusButtons', () => {
  it('renders Start + Cancel for planned status', () => {
    render(<StatusButtons jobId="j-1" status="planned" onChanged={vi.fn()} />);
    expect(screen.getByRole('button', { name: /start/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /cancel/i })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /complete/i })).not.toBeInTheDocument();
  });

  it('renders Complete + Cancel for in_progress status', () => {
    render(<StatusButtons jobId="j-1" status="in_progress" onChanged={vi.fn()} />);
    expect(screen.getByRole('button', { name: /complete/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /cancel/i })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /start/i })).not.toBeInTheDocument();
  });

  it('renders nothing for complete status (terminal)', () => {
    const { container } = render(<StatusButtons jobId="j-1" status="complete" onChanged={vi.fn()} />);
    expect(container.firstChild).toBeNull();
  });

  it('renders nothing for cancelled status (terminal)', () => {
    const { container } = render(<StatusButtons jobId="j-1" status="cancelled" onChanged={vi.fn()} />);
    expect(container.firstChild).toBeNull();
  });

  it('calls onChanged with the new job after clicking Start', async () => {
    const onChanged = vi.fn();
    render(<StatusButtons jobId="j-1" status="planned" onChanged={onChanged} />);
    await userEvent.click(screen.getByRole('button', { name: /start/i }));
    await waitFor(() => expect(onChanged).toHaveBeenCalled());
    const callArg = onChanged.mock.calls[0][0];
    expect(callArg.status).toBe('in_progress');
  });
});
