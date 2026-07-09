import { render, screen, waitFor, fireEvent, within } from '@testing-library/react';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { http, HttpResponse } from 'msw';
import { ClientDetailRoute } from '../ClientDetailRoute';
import { server } from '../../test/msw-server';
import { useClientsStore } from '../../stores/clientsStore';
import { clientsClient } from '../../api/clientsClient';

function renderAt(id: string) {
  return render(
    <MemoryRouter initialEntries={[`/console/clients/${id}`]}>
      <Routes>
        <Route path="/console/clients/:id" element={<ClientDetailRoute />} />
      </Routes>
    </MemoryRouter>,
  );
}

describe('ClientDetailRoute depth', () => {
  beforeEach(() => useClientsStore.getState().clear());

  it('renders LoadingState before the client detail loads', () => {
    renderAt('c1');
    expect(screen.getByRole('status')).toBeInTheDocument();
  });

  it('renders ErrorState and retry re-fires the detail fetch on failure', async () => {
    server.use(http.get('/api/clients/c1', () => HttpResponse.json({ error: 'boom' }, { status: 500 })));
    renderAt('c1');
    await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument());

    server.use(
      http.get('/api/clients/c1', () =>
        HttpResponse.json({
          client: {
            id: 'c1', ownerId: 'f-1', name: 'Acme', email: null, phone: null, address: null,
            company: null, notes: null, createdAt: '2026-05-10T10:00:00Z', updatedAt: '2026-05-10T10:00:00Z',
          },
          jobs: [],
        }),
      ),
    );
    fireEvent.click(screen.getByRole('button', { name: /retry/i }));
    await waitFor(() => expect(screen.getByText('Acme')).toBeInTheDocument());
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(useClientsStore.getState().detailStale).toBe(false);
  });

  it('a stale list poll does not false-flash an ErrorState on a fresh detail mount', async () => {
    useClientsStore.getState().markListStale(true);
    server.use(
      http.get('/api/clients/c1', () =>
        HttpResponse.json({
          client: {
            id: 'c1', ownerId: 'f-1', name: 'Acme', email: null, phone: null, address: null,
            company: null, notes: null, createdAt: '2026-05-10T10:00:00Z', updatedAt: '2026-05-10T10:00:00Z',
          },
          jobs: [],
        }),
      ),
    );
    renderAt('c1');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    await waitFor(() => expect(screen.getByText('Acme')).toBeInTheDocument());
    expect(useClientsStore.getState().listStale).toBe(true);
  });

  it('shows notes, open tasks (pending only), recent jobs, and an activity timeline', async () => {
    server.use(
      http.get('/api/clients/c1', () =>
        HttpResponse.json({
          client: {
            id: 'c1', ownerId: 'f-1', name: 'Acme', email: null, phone: null, address: null,
            company: 'Acme LLC', notes: 'VIP client', createdAt: '2026-05-10T10:00:00Z', updatedAt: '2026-05-10T10:00:00Z',
          },
          jobs: [{
            id: 'j1', title: 'Lobby reno', status: 'in_progress', stage: 'lead', clientId: 'c1', client: null,
            description: null, location: null, scheduledAt: null, foremanId: 'f-1', engagementId: null,
            latitude: null, longitude: null, geocodedAt: null,
            createdAt: '2026-05-11T10:00:00Z', updatedAt: '2026-05-12T10:00:00Z',
          }],
        }),
      ),
      http.get('/api/jobs/j1/tasks', () =>
        HttpResponse.json({
          tasks: [
            { id: 't1', jobId: 'j1', title: 'Demo wall', status: 'pending', sortOrder: 0, createdBy: null, createdAt: '2026-05-11T11:00:00Z', updatedAt: '2026-05-11T11:00:00Z', completedAt: null },
            { id: 't2', jobId: 'j1', title: 'Haul debris', status: 'done', sortOrder: 1, createdBy: null, createdAt: '2026-05-11T12:00:00Z', updatedAt: '2026-05-12T09:00:00Z', completedAt: '2026-05-12T09:00:00Z' },
          ],
        }),
      ),
    );

    renderAt('c1');

    // notes block
    await waitFor(() => expect(screen.getByText('VIP client')).toBeInTheDocument());
    // recent jobs (the title also appears as the open-task's job label, so allow >=1)
    expect(screen.getAllByText('Lobby reno').length).toBeGreaterThanOrEqual(1);
    // open tasks: only the pending one, count reflects the fan-out
    await waitFor(() => expect(screen.getByText('Open tasks (1)')).toBeInTheDocument());
    expect(screen.getByText('Demo wall')).toBeInTheDocument();
    expect(screen.queryByText('Haul debris')).not.toBeInTheDocument();
    // activity timeline: job-created + completed-task events derived from timestamps
    expect(screen.getByText(/Job "Lobby reno" created/)).toBeInTheDocument();
    expect(screen.getByText(/Task "Haul debris" completed/)).toBeInTheDocument();
  });

  it('client delete asks for confirmation first', async () => {
    server.use(
      http.get('/api/clients/c1', () =>
        HttpResponse.json({
          client: {
            id: 'c1', ownerId: 'f-1', name: 'Acme', email: null, phone: null, address: null,
            company: 'Acme LLC', notes: null, createdAt: '2026-05-10T10:00:00Z', updatedAt: '2026-05-10T10:00:00Z',
          },
          jobs: [],
        }),
      ),
    );
    const removeSpy = vi.spyOn(clientsClient, 'remove');

    renderAt('c1');
    await waitFor(() => expect(screen.getByText('Acme')).toBeInTheDocument());

    const deleteTrigger = screen.getByRole('button', { name: 'Delete' });
    fireEvent.click(deleteTrigger);
    const dialog = screen.getByRole('dialog');
    expect(dialog).toBeInTheDocument();
    expect(removeSpy).not.toHaveBeenCalled();

    // Cancel: no delete, dialog closes.
    fireEvent.click(within(dialog).getByRole('button', { name: 'Cancel' }));
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(removeSpy).not.toHaveBeenCalled();

    // Confirm: delete happens.
    fireEvent.click(deleteTrigger);
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Delete' }));
    await waitFor(() => expect(removeSpy).toHaveBeenCalledWith('c1'));
  });

  it('back-link is hidden at xl (beside-list panel view supersedes it)', async () => {
    renderAt('c1');
    await waitFor(() => expect(screen.getByText('Test Client')).toBeInTheDocument());
    const backLink = screen.getByText('back to clients');
    expect(backLink.className).toMatch(/xl:hidden/);
  });
});
