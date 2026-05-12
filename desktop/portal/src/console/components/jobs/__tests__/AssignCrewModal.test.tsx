import { render, screen, waitFor, act } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { AssignCrewModal } from '../AssignCrewModal';

describe('AssignCrewModal', () => {
  beforeEach(() => { vi.useFakeTimers({ shouldAdvanceTime: true }); });
  afterEach(() => { vi.useRealTimers(); });

  it('shows "type to search" hint when query is empty', () => {
    render(<AssignCrewModal open jobId="j-1" alreadyAssigned={[]} onClose={vi.fn()} onAssigned={vi.fn()} />);
    expect(screen.getByText(/type to search/i)).toBeInTheDocument();
  });

  it('triggers search after debounce when 2+ chars typed', async () => {
    render(<AssignCrewModal open jobId="j-1" alreadyAssigned={[]} onClose={vi.fn()} onAssigned={vi.fn()} />);
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    await user.type(screen.getByLabelText(/search/i), 'al');
    await act(async () => { vi.advanceTimersByTime(350); await Promise.resolve(); });
    await waitFor(() => expect(screen.getByText('alice@example.com')).toBeInTheDocument());
  });

  it('selecting a result reveals role selector + assign button', async () => {
    render(<AssignCrewModal open jobId="j-1" alreadyAssigned={[]} onClose={vi.fn()} onAssigned={vi.fn()} />);
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    await user.type(screen.getByLabelText(/search/i), 'alice');
    await act(async () => { vi.advanceTimersByTime(350); await Promise.resolve(); });
    await waitFor(() => expect(screen.getByText('alice@example.com')).toBeInTheDocument());
    await user.click(screen.getByText('alice@example.com'));
    expect(screen.getByRole('button', { name: /assign/i })).toBeInTheDocument();
    expect(screen.getByLabelText(/role/i)).toBeInTheDocument();
  });

  it('marks already-assigned profiles and prevents selection', async () => {
    render(<AssignCrewModal open jobId="j-1" alreadyAssigned={['p-1']} onClose={vi.fn()} onAssigned={vi.fn()} />);
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    await user.type(screen.getByLabelText(/search/i), 'alice');
    await act(async () => { vi.advanceTimersByTime(350); await Promise.resolve(); });
    await waitFor(() => expect(screen.getByText(/already assigned/i)).toBeInTheDocument());
  });

  it('submit calls onAssigned with the new assignment', async () => {
    const onAssigned = vi.fn();
    render(<AssignCrewModal open jobId="j-1" alreadyAssigned={[]} onClose={vi.fn()} onAssigned={onAssigned} />);
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime });
    await user.type(screen.getByLabelText(/search/i), 'alice');
    await act(async () => { vi.advanceTimersByTime(350); await Promise.resolve(); });
    await waitFor(() => expect(screen.getByText('alice@example.com')).toBeInTheDocument());
    await user.click(screen.getByText('alice@example.com'));
    await user.click(screen.getByRole('button', { name: /assign/i }));
    await waitFor(() => expect(onAssigned).toHaveBeenCalled());
    const arg = onAssigned.mock.calls[0][0];
    expect(arg.profileId).toBe('p-1');
  });
});
