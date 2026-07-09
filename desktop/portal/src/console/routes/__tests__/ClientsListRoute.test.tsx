import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { http, HttpResponse } from 'msw';
import { ClientsListRoute } from '../ClientsListRoute';
import { useClientsStore } from '../../stores/clientsStore';
import { server } from '../../test/msw-server';
import type { Client } from '../../api/clientsClient';

const c = (id: string, name: string, company: string | null = null): Client => ({
  id, ownerId: 'f-1', name, email: null, phone: null, address: null,
  company, notes: null, createdAt: '2026-05-11T10:00:00Z', updatedAt: '2026-05-11T10:00:00Z',
});

describe('ClientsListRoute', () => {
  beforeEach(() => useClientsStore.getState().clear());

  it('renders clients from the store', () => {
    useClientsStore.getState().setClients([c('a', 'Acme'), c('b', 'Globex')]);
    render(<MemoryRouter><ClientsListRoute /></MemoryRouter>);
    expect(screen.getByText('Acme')).toBeInTheDocument();
    expect(screen.getByText('Globex')).toBeInTheDocument();
  });

  it('search matches by company name too', () => {
    // Seed synchronously (mirrors the first test); the async poll is a no-op for this check.
    useClientsStore.getState().setClients([c('a', 'Acme', 'Northgate LLC'), c('b', 'Beta', 'Other Co')]);
    render(<MemoryRouter><ClientsListRoute /></MemoryRouter>);
    expect(screen.getByText('Acme')).toBeInTheDocument();
    expect(screen.getByText('Beta')).toBeInTheDocument();
    fireEvent.change(screen.getByPlaceholderText(/search by name/i), { target: { value: 'northgate' } });
    expect(screen.getByText('Acme')).toBeInTheDocument();        // matched via company
    expect(screen.queryByText('Beta')).not.toBeInTheDocument();
  });

  it('renders LoadingState while clients are loading', () => {
    render(<MemoryRouter><ClientsListRoute /></MemoryRouter>);
    expect(screen.getByRole('status')).toBeInTheDocument();
  });

  it('initial fetch failure shows ErrorState with retry, not "No clients."', async () => {
    server.use(http.get('/api/clients', () => HttpResponse.json({ error: 'boom' }, { status: 500 })));
    render(<MemoryRouter><ClientsListRoute /></MemoryRouter>);
    await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument());
    expect(screen.queryByText('No clients.')).not.toBeInTheDocument();

    server.use(http.get('/api/clients', () => HttpResponse.json({ clients: [c('a', 'Acme')] })));
    fireEvent.click(screen.getByRole('button', { name: /retry/i }));
    await waitFor(() => expect(screen.getByText('Acme')).toBeInTheDocument());
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('retry on the stale banner re-fires the fetch and clears the banner', async () => {
    useClientsStore.getState().setClients([c('a', 'Acme')]);
    useClientsStore.getState().markListStale(true);
    render(<MemoryRouter><ClientsListRoute /></MemoryRouter>);
    expect(screen.getByRole('alert')).toBeInTheDocument();

    server.use(http.get('/api/clients', () => HttpResponse.json({ clients: [c('a', 'Acme'), c('b', 'Globex')] })));
    fireEvent.click(screen.getByRole('button', { name: /retry/i }));
    await waitFor(() => expect(screen.getByText('Globex')).toBeInTheDocument());
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});
