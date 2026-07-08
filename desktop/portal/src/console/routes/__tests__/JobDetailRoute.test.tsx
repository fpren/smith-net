import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { describe, it, expect, beforeEach } from 'vitest';
import { http, HttpResponse } from 'msw';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { JobDetailRoute } from '../JobDetailRoute';
import { useJobsStore } from '../../stores/jobsStore';
import { useTasksStore } from '../../stores/tasksStore';
import { server } from '../../test/msw-server';

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

  it('renders the JobStageBar and stage controls for the current stage', async () => {
    // Override MSW so GET /api/jobs/jX returns stage: 'approved'.
    server.use(
      http.get('/api/jobs/:id', ({ params }) => {
        if (params.id === 'jX') {
          return HttpResponse.json({
            job: {
              id: 'jX', foremanId: 'f-1', clientId: null, engagementId: null,
              title: 'Stage test', description: null, status: 'planned', stage: 'approved',
              scheduledAt: null, location: null, latitude: null, longitude: null,
              geocodedAt: null, createdAt: '2026-05-11T10:00:00Z',
              updatedAt: '2026-05-11T11:00:00Z', client: null,
            },
            crew: [],
          });
        }
        // Fall through to default handler shape for other ids.
        return HttpResponse.json({
          job: {
            id: params.id, foremanId: 'user-1', clientId: null, client: null,
            engagementId: null, title: 'Detail Job', description: null,
            status: 'planned', stage: 'lead', scheduledAt: null,
            location: 'Detail Location', latitude: null, longitude: null,
            geocodedAt: null, createdAt: '2026-05-11T10:00:00Z',
            updatedAt: '2026-05-11T10:00:00Z',
          },
          crew: [],
        });
      })
    );
    renderAt('/console/jobs/jX');
    expect(await screen.findByText('APPROVED')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /start work/i })).toBeInTheDocument();
  });

  it('renders MaterialsList, ExpensesTable, and JobCostRollup', async () => {
    server.use(
      http.get('/api/jobs/:id', ({ params }) => {
        if (params.id === 'jY') {
          return HttpResponse.json({
            job: {
              id: 'jY', foremanId: 'f-1', clientId: null, engagementId: null,
              title: 'Sections test', description: null, status: 'planned', stage: 'lead',
              scheduledAt: null, location: null, latitude: null, longitude: null,
              geocodedAt: null, createdAt: '2026-05-11T10:00:00Z',
              updatedAt: '2026-05-11T11:00:00Z', client: null,
            },
            crew: [],
          });
        }
        return HttpResponse.json({
          job: {
            id: params.id, foremanId: 'user-1', clientId: null, client: null,
            engagementId: null, title: 'Detail Job', description: null,
            status: 'planned', stage: 'lead', scheduledAt: null,
            location: 'Detail Location', latitude: null, longitude: null,
            geocodedAt: null, createdAt: '2026-05-11T10:00:00Z',
            updatedAt: '2026-05-11T10:00:00Z',
          },
          crew: [],
        });
      })
    );
    renderAt('/console/jobs/jY');
    // Section headers from MaterialsList and ExpensesTable
    expect(await screen.findByRole('heading', { name: /materials/i })).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: /expenses/i })).toBeInTheDocument();
    // JobCostRollup renders zeros from the default MSW empty-array responses.
    // "Job total:" and "$0.00" are sibling spans — assert them individually.
    expect(await screen.findByText('Job total:')).toBeInTheDocument();
    expect(screen.getAllByText('$0.00').length).toBeGreaterThanOrEqual(1);
  });

  it('renders LoadingState before the job detail loads', () => {
    renderAt('/console/jobs/loading-test');
    expect(screen.getByRole('status')).toBeInTheDocument();
  });

  it('renders ErrorState and retry re-fires the detail fetch on failure', async () => {
    server.use(http.get('/api/jobs/:id', () => HttpResponse.json({ error: 'boom' }, { status: 500 })));
    renderAt('/console/jobs/err-1');
    const retry = await screen.findByRole('button', { name: /retry/i });

    server.use(
      http.get('/api/jobs/:id', ({ params }) => HttpResponse.json({
        job: {
          id: params.id, foremanId: 'f-1', clientId: null, client: null, engagementId: null,
          title: 'Recovered Job', description: null, status: 'planned', stage: 'lead',
          scheduledAt: null, location: null, latitude: null, longitude: null,
          geocodedAt: null, createdAt: '2026-05-11T10:00:00Z', updatedAt: '2026-05-11T10:00:00Z',
        },
        crew: [],
      })),
    );
    fireEvent.click(retry);

    expect(await screen.findByText('Recovered Job')).toBeInTheDocument();
    expect(useJobsStore.getState().isStale).toBe(false);
  });
});
