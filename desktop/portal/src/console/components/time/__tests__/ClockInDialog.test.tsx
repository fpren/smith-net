import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi } from 'vitest';
import { ClockInDialog } from '../ClockInDialog';

// jobsClient.list is called to populate the optional board picker; default to a
// 403 (solo) so only the free-text path shows unless a test overrides it.
vi.mock('../../../api/jobsClient', () => ({
  jobsClient: { list: vi.fn().mockResolvedValue({ ok: false, status: 403, error: 'tier' }) },
}));

describe('ClockInDialog', () => {
  it('renders all 5 entry types and a free-text job field', async () => {
    render(<ClockInDialog open onClose={() => {}} onConfirm={() => {}} />);
    for (const t of ['Regular', 'Overtime', 'Break', 'Travel', 'On call']) {
      expect(screen.getByText(t)).toBeInTheDocument();
    }
    expect(screen.getByPlaceholderText(/job name/i)).toBeInTheDocument();
  });

  it('confirms with the selected entry type + free-text job title', () => {
    const onConfirm = vi.fn();
    render(<ClockInDialog open onClose={() => {}} onConfirm={onConfirm} />);
    fireEvent.click(screen.getByText('Overtime'));
    fireEvent.change(screen.getByPlaceholderText(/job name/i), { target: { value: 'Kitchen Reno' } });
    fireEvent.click(screen.getByRole('button', { name: /clock in/i }));
    expect(onConfirm).toHaveBeenCalledWith({ entryType: 'overtime', jobTitle: 'Kitchen Reno' });
  });
});
