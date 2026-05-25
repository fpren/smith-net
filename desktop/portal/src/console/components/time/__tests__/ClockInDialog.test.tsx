import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ClockInDialog } from '../ClockInDialog';
import { presenceClient } from '../../../api/presenceClient';

// The dialog populates an all-tier job picker from presenceClient.getMyJobs()
// (works for solo, never 403) and loads tasks per job via getJobTasks().
vi.mock('../../../api/presenceClient', () => ({
  presenceClient: {
    getMyJobs: vi.fn(),
    getJobTasks: vi.fn(),
  },
}));

const getMyJobs = vi.mocked(presenceClient.getMyJobs);
const getJobTasks = vi.mocked(presenceClient.getJobTasks);

describe('ClockInDialog', () => {
  beforeEach(() => {
    getMyJobs.mockReset();
    getJobTasks.mockReset();
    // Default: no jobs -> free-text only.
    getMyJobs.mockResolvedValue({ ok: true, jobs: [] });
    getJobTasks.mockResolvedValue({ ok: true, tasks: [] });
  });

  it('renders all 5 entry types and a free-text job field', async () => {
    render(<ClockInDialog open onClose={() => {}} onConfirm={() => {}} />);
    for (const t of ['Regular', 'Overtime', 'Break', 'Travel', 'On call']) {
      expect(screen.getByText(t)).toBeInTheDocument();
    }
    expect(screen.getByPlaceholderText(/job name/i)).toBeInTheDocument();
  });

  it('confirms with the selected entry type + free-text job title', async () => {
    const onConfirm = vi.fn();
    render(<ClockInDialog open onClose={() => {}} onConfirm={onConfirm} />);
    fireEvent.click(screen.getByText('Overtime'));
    fireEvent.change(screen.getByPlaceholderText(/job name/i), { target: { value: 'Kitchen Reno' } });
    fireEvent.click(screen.getByRole('button', { name: /clock in/i }));
    expect(onConfirm).toHaveBeenCalledWith({ entryType: 'overtime', jobTitle: 'Kitchen Reno' });
  });

  it('loads tasks on job pick and confirms with jobId + jobTitle + taskId + taskTitle', async () => {
    getMyJobs.mockResolvedValue({ ok: true, jobs: [{ id: 'job-1', title: 'Kitchen', status: 'in_progress' }] });
    getJobTasks.mockResolvedValue({ ok: true, tasks: [{ id: 'task-1', title: 'Rough-in', status: 'open' }] });
    const onConfirm = vi.fn();
    render(<ClockInDialog open onClose={() => {}} onConfirm={onConfirm} />);

    // Job select appears once getMyJobs resolves.
    const jobSelect = await screen.findByDisplayValue('No job (general time)');
    fireEvent.change(jobSelect, { target: { value: 'job-1' } });
    expect(getJobTasks).toHaveBeenCalledWith('job-1');

    // Task select appears once getJobTasks resolves.
    const taskSelect = await screen.findByDisplayValue('No task');
    fireEvent.change(taskSelect, { target: { value: 'task-1' } });

    fireEvent.click(screen.getByRole('button', { name: /clock in/i }));
    expect(onConfirm).toHaveBeenCalledWith({
      entryType: 'regular',
      jobId: 'job-1',
      jobTitle: 'Kitchen',
      taskId: 'task-1',
      taskTitle: 'Rough-in',
    });
  });

  it('resets selection state when the dialog reopens', async () => {
    getMyJobs.mockResolvedValue({ ok: true, jobs: [{ id: 'job-1', title: 'Kitchen', status: 'in_progress' }] });
    const onConfirm = vi.fn();
    const { rerender } = render(<ClockInDialog open onClose={() => {}} onConfirm={onConfirm} />);
    fireEvent.click(screen.getByText('Overtime'));
    fireEvent.change(screen.getByPlaceholderText(/job name/i), { target: { value: 'Stale' } });

    rerender(<ClockInDialog open={false} onClose={() => {}} onConfirm={onConfirm} />);
    rerender(<ClockInDialog open onClose={() => {}} onConfirm={onConfirm} />);

    await waitFor(() => expect(screen.getByPlaceholderText(/job name/i)).toHaveValue(''));
    fireEvent.click(screen.getByRole('button', { name: /clock in/i }));
    expect(onConfirm).toHaveBeenCalledWith({ entryType: 'regular' });
  });
});
