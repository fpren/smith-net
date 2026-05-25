import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { TimeScreen } from '../TimeScreen';

const h = vi.hoisted(() => ({
  toggle: {
    onClock: false, startedAt: null as string | null, entryType: null as string | null,
    jobTitle: null as string | null, busy: false, clockIn: vi.fn(), clockOut: vi.fn(),
  },
}));
vi.mock('../../header/useShiftToggle', () => ({ useShiftToggle: () => h.toggle }));
vi.mock('../useTodayEntries', () => ({ useTodayEntries: () => [] }));
// ClockInDialog (opened on clock-in) fetches the job board; stub to a 403 so no real fetch fires.
vi.mock('../../../api/jobsClient', () => ({
  jobsClient: { list: vi.fn().mockResolvedValue({ ok: false, status: 403, error: 'tier' }) },
}));

describe('TimeScreen container', () => {
  beforeEach(() => {
    h.toggle = { onClock: false, startedAt: null, entryType: null, jobTitle: null, busy: false, clockIn: vi.fn(), clockOut: vi.fn() };
  });

  it('off-clock: the switch opens the clock-in dialog (does not clock in instantly)', () => {
    render(<TimeScreen />);
    expect(screen.getByLabelText('shift elapsed')).toHaveTextContent('--:--:--');
    fireEvent.click(screen.getByRole('button', { name: /clock in/i }));
    expect(h.toggle.clockIn).not.toHaveBeenCalled();
    expect(screen.getByTestId('modal-backdrop')).toBeInTheDocument(); // dialog opened
  });

  it('on-clock: shows entry type + job, and the switch clocks out instantly (no dialog)', () => {
    h.toggle = {
      onClock: true, startedAt: new Date(Date.now() - 60_000).toISOString(),
      entryType: 'overtime', jobTitle: 'Kitchen', busy: false, clockIn: vi.fn(), clockOut: vi.fn(),
    };
    render(<TimeScreen />);
    expect(screen.getByText(/overtime/i)).toBeInTheDocument();
    expect(screen.getByText(/Kitchen/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /clock out/i }));
    expect(h.toggle.clockOut).toHaveBeenCalled();
    expect(screen.queryByTestId('modal-backdrop')).not.toBeInTheDocument(); // no dialog
  });
});
