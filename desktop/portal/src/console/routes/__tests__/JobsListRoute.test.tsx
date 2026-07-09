import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, beforeEach } from 'vitest';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { JobsListRoute } from '../JobsListRoute';
import { useJobsStore } from '../../stores/jobsStore';
import { server } from '../../test/msw-server';
import { http, HttpResponse } from 'msw';
import type { Job } from '../../api/jobsClient';

function renderNestedAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/console/jobs" element={<JobsListRoute />}>
          <Route path=":id" element={<div>Detail placeholder</div>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

const j = (id: string, status: Job['status']): Job => ({
  id, foremanId: 'f-1', clientId: null, client: null, engagementId: null,
  title: `Job ${id}`, description: null, status, stage: 'lead',
  scheduledAt: null, location: null,
  latitude: null, longitude: null, geocodedAt: null,
  createdAt: '2026-05-11T10:00:00Z', updatedAt: '2026-05-11T10:00:00Z',
});

describe('JobsListRoute', () => {
  beforeEach(() => { useJobsStore.getState().clear(); });

  it('renders 4 status section headers', async () => {
    useJobsStore.getState().setJobs([j('a', 'planned'), j('b', 'in_progress'), j('c', 'complete'), j('d', 'cancelled')]);
    render(<MemoryRouter><JobsListRoute /></MemoryRouter>);
    // Status labels appear in section headers AND JobCard chips (visual lift).
    // Use the "X (N)" header form to disambiguate.
    expect(screen.getByText('PLANNED (1)')).toBeInTheDocument();
    expect(screen.getByText('IN PROGRESS (1)')).toBeInTheDocument();
    expect(screen.getByText('COMPLETE (1)')).toBeInTheDocument();
    expect(screen.getByText('CANCELLED (1)')).toBeInTheDocument();
  });

  it('renders correct count next to each header', () => {
    useJobsStore.getState().setJobs([j('a', 'planned'), j('b', 'planned'), j('c', 'in_progress')]);
    render(<MemoryRouter><JobsListRoute /></MemoryRouter>);
    expect(screen.getByText('PLANNED (2)')).toBeInTheDocument();
    expect(screen.getByText('IN PROGRESS (1)')).toBeInTheDocument();
    expect(screen.getByText('COMPLETE (0)')).toBeInTheDocument();
  });

  it('shows empty state when zero jobs total', async () => {
    // The route's poller fetches on mount; the default MSW handler returns one
    // job. A "zero jobs" test must actually mock zero jobs, or it is racing
    // the mocked response (this exact race failed on CI, run 28919295385).
    // The mount also renders LoadingState synchronously until that fetch
    // settles, so this must await rather than assert immediately.
    server.use(http.get('/api/jobs', () => HttpResponse.json({ jobs: [] })));
    render(<MemoryRouter><JobsListRoute /></MemoryRouter>);
    expect(await screen.findByText(/no jobs yet/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /create your first/i })).toBeInTheDocument();
  });

  it('renders LoadingState while jobs are loading', () => {
    useJobsStore.getState().markListLoading(true);
    render(<MemoryRouter><JobsListRoute /></MemoryRouter>);
    expect(screen.getByRole('status')).toBeInTheDocument();
  });

  it('renders [+ Create Job] button when jobs exist', () => {
    useJobsStore.getState().setJobs([j('a', 'planned')]);
    render(<MemoryRouter><JobsListRoute /></MemoryRouter>);
    expect(screen.getByRole('button', { name: /create job/i })).toBeInTheDocument();
  });

  it('renders stale strip when listStale is true', () => {
    useJobsStore.getState().setJobs([j('a', 'planned')]);
    useJobsStore.getState().markListStale(true);
    render(<MemoryRouter><JobsListRoute /></MemoryRouter>);
    expect(screen.getByText(/couldn't refresh/i)).toBeInTheDocument();
  });

  it('retry on the stale banner re-fires the fetch and clears the banner', async () => {
    server.use(http.get('/api/jobs', () => HttpResponse.json({ jobs: [j('a', 'planned')] })));
    render(<MemoryRouter><JobsListRoute /></MemoryRouter>);
    await screen.findByText('PLANNED (1)');
    useJobsStore.getState().markListStale(true);
    const retry = await screen.findByRole('button', { name: /retry/i });

    server.use(
      http.get('/api/jobs', () => HttpResponse.json({ jobs: [j('a', 'planned'), j('b', 'in_progress')] })),
    );
    fireEvent.click(retry);

    await waitFor(() => expect(screen.getByText('IN PROGRESS (1)')).toBeInTheDocument());
    expect(useJobsStore.getState().listStale).toBe(false);
  });

  it('finding #3: initial fetch failure shows ErrorState with retry, not EmptyState', async () => {
    server.use(http.get('/api/jobs', () => HttpResponse.json({ error: 'boom' }, { status: 500 })));
    render(<MemoryRouter><JobsListRoute /></MemoryRouter>);

    const alert = await screen.findByRole('alert');
    expect(alert).toBeInTheDocument();
    expect(screen.queryByText(/no jobs yet/i)).not.toBeInTheDocument();
    const retry = screen.getByRole('button', { name: /retry/i });

    server.use(http.get('/api/jobs', () => HttpResponse.json({ jobs: [j('a', 'planned')] })));
    fireEvent.click(retry);

    await waitFor(() => expect(screen.getByText('PLANNED (1)')).toBeInTheDocument());
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  describe('beside-list detail panel (Plan 4C Task 2)', () => {
    it('shows the "Select a job" empty panel when no id is active', () => {
      useJobsStore.getState().setJobs([j('a', 'planned')]);
      renderNestedAt('/console/jobs');
      expect(screen.getByText('Select a job')).toBeInTheDocument();
      expect(screen.queryByText('Detail placeholder')).not.toBeInTheDocument();
    });

    it('hides the list (below xl) and renders the outlet when an id is active', () => {
      useJobsStore.getState().setJobs([j('a', 'planned')]);
      renderNestedAt('/console/jobs/a');
      // The outlet content (child route) renders in the panel slot.
      expect(screen.getByText('Detail placeholder')).toBeInTheDocument();
      expect(screen.queryByText('Select a job')).not.toBeInTheDocument();
      // The list container is CSS-hidden below xl, but stays in the DOM.
      const listHeading = screen.getByText('PLANNED (1)');
      const listContainer = listHeading.closest('div.hidden');
      expect(listContainer).not.toBeNull();
      expect(listContainer?.className).toMatch(/hidden/);
      expect(listContainer?.className).toMatch(/xl:block/);
    });

    it('applies the panel-in slide animation class to the outlet wrapper, keyed on the active id', () => {
      useJobsStore.getState().setJobs([j('a', 'planned')]);
      renderNestedAt('/console/jobs/a');
      const panel = screen.getByText('Detail placeholder').closest('.panel-in');
      expect(panel).not.toBeNull();
    });

    it('does not render "Select a job" when the job list itself is empty and no id is active (double-EmptyState fix)', async () => {
      server.use(http.get('/api/jobs', () => HttpResponse.json({ jobs: [] })));
      renderNestedAt('/console/jobs');
      await screen.findByText(/no jobs yet/i);
      expect(screen.queryByText('Select a job')).not.toBeInTheDocument();
    });

    it('remounts the panel-in wrapper (re-triggering the slide-in animation) when the active job id changes', () => {
      // The panel wrapper is keyed on detailMatch?.params.id (see JobsListRoute),
      // so switching the selected job must unmount + remount that node -- that
      // remount is what re-runs the CSS animation. Prove it via node identity:
      // same className, different element instance.
      useJobsStore.getState().setJobs([j('a', 'planned'), j('b', 'in_progress')]);
      const { container } = renderNestedAt('/console/jobs/a');

      const first = container.querySelector('.panel-in');
      expect(first).not.toBeNull();

      const linkToB = container.querySelector('a[href="/console/jobs/b"]');
      expect(linkToB).not.toBeNull();
      fireEvent.click(linkToB!);

      const second = container.querySelector('.panel-in');
      expect(second).not.toBeNull();
      expect(second).not.toBe(first);
    });
  });
});
