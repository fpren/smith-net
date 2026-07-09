import { render, screen, waitFor, fireEvent, act } from '@testing-library/react';
import { describe, it, expect, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { http, HttpResponse } from 'msw';
import { AdminRoute } from '../AdminRoute';
import { useAdminHealthStore } from '../../stores/adminHealthStore';
import { useAuthStore } from '../../auth/authStore';
import { server } from '../../test/msw-server';

function loginAsAdmin() {
  useAuthStore.getState().setUser({
    id: 'u1', email: 'x@y.com', displayName: 'X', role: 'admin', emailVerified: true,
  });
}

describe('AdminRoute', () => {
  beforeEach(() => {
    useAdminHealthStore.getState().clear();
    useAuthStore.getState().clear();
  });

  it('renders LoadingState while the health payload is loading', () => {
    // useAdminHealth only fetches for admins (perimeter gate).
    loginAsAdmin();
    render(<MemoryRouter><AdminRoute /></MemoryRouter>);
    expect(screen.getByRole('status')).toBeInTheDocument();
  });

  it('renders workers + queue tables once polled data lands', async () => {
    loginAsAdmin();
    render(<MemoryRouter><AdminRoute /></MemoryRouter>);

    await waitFor(() => expect(screen.getByText(/12345@host/)).toBeInTheDocument());
    expect(screen.getByText(/geocode, audit_flush, email/)).toBeInTheDocument();

    // Queue rollup — both kind:state combos visible.
    // "audit_flush" appears in worker kinds AND queue kind — getAllByText
    // for that one. "succeeded" is queue-only.
    expect(screen.getAllByText(/audit_flush/).length).toBeGreaterThan(1);
    expect(screen.getByText('succeeded')).toBeInTheDocument();
    expect(screen.getByText('12')).toBeInTheDocument();

    // Oldest queued card
    expect(screen.getByText(/oldest queued/i)).toBeInTheDocument();
  });

  it('renders EmptyState when there are no workers heartbeating or queue rows', async () => {
    loginAsAdmin();
    server.use(
      http.get('/api/admin/health', () =>
        HttpResponse.json({
          workers: [],
          queue: { byKindState: [], oldestQueued: null, oldestRunning: null },
        })
      )
    );
    render(<MemoryRouter><AdminRoute /></MemoryRouter>);
    await waitFor(() => expect(screen.getByText('[DOWN] No workers heartbeating.')).toBeInTheDocument());
    expect(screen.getByText('No background_jobs rows.')).toBeInTheDocument();
  });

  it('initial fetch failure shows ErrorState with retry, not a perpetual spinner', async () => {
    loginAsAdmin();
    server.use(http.get('/api/admin/health', () => HttpResponse.json({ error: 'boom' }, { status: 500 })));
    render(<MemoryRouter><AdminRoute /></MemoryRouter>);
    await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument());
    expect(screen.queryByRole('status')).not.toBeInTheDocument();

    server.use(
      http.get('/api/admin/health', () =>
        HttpResponse.json({
          workers: [{ workerId: '12345@host', kinds: ['geocode'], lastBeatAt: '2026-05-16T00:00:00Z', ageSec: 7 }],
          queue: { byKindState: [], oldestQueued: null, oldestRunning: null },
        })
      )
    );
    fireEvent.click(screen.getByRole('button', { name: /retry/i }));
    await waitFor(() => expect(screen.getByText(/12345@host/)).toBeInTheDocument());
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('a later poll failure shows an inline stale banner while cached data stays visible', async () => {
    loginAsAdmin();
    render(<MemoryRouter><AdminRoute /></MemoryRouter>);
    await waitFor(() => expect(screen.getByText(/12345@host/)).toBeInTheDocument());

    act(() => { useAdminHealthStore.getState().markStale(true); });
    await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument());
    // Cached table is still visible under the banner (not full-page replaced).
    expect(screen.getByText(/12345@host/)).toBeInTheDocument();
  });
});
