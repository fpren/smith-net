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
});
