import { render, screen, waitFor } from '@testing-library/react';
import { describe, it, expect, beforeEach } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { JobDetailRoute } from '../JobDetailRoute';
import { useJobsStore } from '../../stores/jobsStore';
import { useTasksStore } from '../../stores/tasksStore';

describe('JobDetailRoute', () => {
  beforeEach(() => {
    useJobsStore.getState().clear();
    useTasksStore.getState().clear();
  });

  function renderAt(path: string) {
    return render(
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/console/jobs/:id" element={<JobDetailRoute />} />
        </Routes>
      </MemoryRouter>
    );
  }

  it('renders the job title once detail is loaded', async () => {
    renderAt('/console/jobs/abc');
    // useJobsPolling hits MSW mock -> store gets populated
    await waitFor(() => expect(screen.getByText('Detail Job')).toBeInTheDocument());
  });

  it('shows StatusButtons for non-terminal status', async () => {
    renderAt('/console/jobs/abc');
    await waitFor(() => expect(screen.getByText('Detail Job')).toBeInTheDocument());
    expect(screen.getByRole('button', { name: /start/i })).toBeInTheDocument();
  });

  it('shows [+ Assign crew] button', async () => {
    renderAt('/console/jobs/abc');
    await waitFor(() => expect(screen.getByText('Detail Job')).toBeInTheDocument());
    expect(screen.getByRole('button', { name: /assign crew/i })).toBeInTheDocument();
  });

  it('renders the Tasks section + AddTaskInput once detail is loaded', async () => {
    renderAt('/console/jobs/abc');
    await waitFor(() => expect(screen.getByText('Detail Job')).toBeInTheDocument());
    expect(screen.getByText(/Tasks /)).toBeInTheDocument();
    // MSW fixture returns one task with title "First task".
    await waitFor(() => expect(screen.getByText('First task')).toBeInTheDocument());
    expect(screen.getByPlaceholderText(/Add a task/i)).toBeInTheDocument();
  });

  it('shows the task count in the header', async () => {
    renderAt('/console/jobs/abc');
    await waitFor(() => expect(screen.getByText('First task')).toBeInTheDocument());
    // Header text is built from nested text nodes ("Tasks (", "1", ")")
    // so flatten via textContent on the matching h2.
    const headings = screen.getAllByRole('heading');
    const tasksHeader = headings.find((h) => h.textContent?.startsWith('Tasks'));
    expect(tasksHeader?.textContent).toMatch(/Tasks\s*\(\s*1\s*,\s*0\s*done\s*\)/);
  });
});
