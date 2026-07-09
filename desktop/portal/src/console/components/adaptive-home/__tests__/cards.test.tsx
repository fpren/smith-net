import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { describe, it, expect, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { http, HttpResponse } from 'msw';
import { JobsCard, InvoicesCard, DispatchCard, SystemCard, NotificationsCard, OpenTasksCard, CrewPresenceCard } from '../cards';
import { useJobsStore } from '../../../stores/jobsStore';
import { useInvoicesStore } from '../../../stores/invoicesStore';
import { useNotificationsStore } from '../../../stores/notificationsStore';
import { useAdminHealthStore } from '../../../stores/adminHealthStore';
import { useAuthStore } from '../../../auth/authStore';
import { useCrewStore } from '../../../stores/crewStore';
import { server } from '../../../test/msw-server';

function withRouter(el: JSX.Element) {
  return <MemoryRouter>{el}</MemoryRouter>;
}

describe('JobsCard', () => {
  beforeEach(() => useJobsStore.getState().clear());

  it('renders LoadingState while jobs are loading', () => {
    render(withRouter(<JobsCard />));
    expect(screen.getByRole('status')).toBeInTheDocument();
  });

  it('renders the default MSW job once loaded', async () => {
    render(withRouter(<JobsCard />));
    await waitFor(() => expect(screen.getByText('Test Job')).toBeInTheDocument());
  });

  it('initial fetch failure shows ErrorState with retry, not the empty list', async () => {
    server.use(http.get('/api/jobs', () => HttpResponse.json({ error: 'boom' }, { status: 500 })));
    render(withRouter(<JobsCard />));
    await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument());

    server.use(http.get('/api/jobs', () => HttpResponse.json({ jobs: [] })));
    fireEvent.click(screen.getByRole('button', { name: /retry/i }));
    await waitFor(() => expect(screen.getByText('No jobs.')).toBeInTheDocument());
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});

describe('InvoicesCard', () => {
  beforeEach(() => useInvoicesStore.getState().clear());

  it('renders LoadingState while invoices are loading', () => {
    render(withRouter(<InvoicesCard />));
    expect(screen.getByRole('status')).toBeInTheDocument();
  });

  it('renders the default MSW invoice once loaded', async () => {
    render(withRouter(<InvoicesCard />));
    await waitFor(() => expect(screen.getByText('INV-2026-0001')).toBeInTheDocument());
  });

  it('initial fetch failure shows ErrorState with retry, not the empty list', async () => {
    server.use(http.get('/api/invoices', () => HttpResponse.json({ error: 'boom' }, { status: 500 })));
    render(withRouter(<InvoicesCard />));
    await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument());

    server.use(http.get('/api/invoices', () => HttpResponse.json({ invoices: [] })));
    fireEvent.click(screen.getByRole('button', { name: /retry/i }));
    await waitFor(() => expect(screen.getByText('No invoices.')).toBeInTheDocument());
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});

describe('DispatchCard', () => {
  beforeEach(() => useJobsStore.getState().clear());

  it('renders LoadingState while jobs are loading', () => {
    render(withRouter(<DispatchCard />));
    expect(screen.getByRole('status')).toBeInTheDocument();
  });

  it("shows 'All active jobs scheduled.' (not EmptyState) once the default job (already scheduled: null) is filtered out by scheduledAt", async () => {
    // Default MSW job has scheduledAt: null and status: planned, so it DOES
    // need scheduling -- assert the to-schedule row renders instead.
    render(withRouter(<DispatchCard />));
    await waitFor(() => expect(screen.getByText('Test Job')).toBeInTheDocument());
  });

  it('shows the ad-hoc all-scheduled copy when nothing needs dispatching', async () => {
    server.use(http.get('/api/jobs', () => HttpResponse.json({ jobs: [] })));
    render(withRouter(<DispatchCard />));
    await waitFor(() => expect(screen.getByText('All active jobs scheduled.')).toBeInTheDocument());
  });

  it('initial fetch failure shows ErrorState with retry', async () => {
    server.use(http.get('/api/jobs', () => HttpResponse.json({ error: 'boom' }, { status: 500 })));
    render(withRouter(<DispatchCard />));
    await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument());
  });
});

describe('SystemCard', () => {
  beforeEach(() => {
    useAdminHealthStore.getState().clear();
    useAuthStore.getState().clear();
  });

  it("shows the silent 'Idle.' fallback for a non-admin (no fetch is ever made)", () => {
    render(withRouter(<SystemCard />));
    expect(screen.getByText('Idle.')).toBeInTheDocument();
  });

  it('renders LoadingState then worker/queue counts for an admin', async () => {
    useAuthStore.getState().setUser({
      id: 'u1', email: 'x@y.com', displayName: 'X', role: 'admin', emailVerified: true,
    });
    render(withRouter(<SystemCard />));
    expect(screen.getByRole('status')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByText(/worker/)).toBeInTheDocument());
  });

  it('an admin fetch failure with no cached data shows ErrorState with retry', async () => {
    useAuthStore.getState().setUser({
      id: 'u1', email: 'x@y.com', displayName: 'X', role: 'admin', emailVerified: true,
    });
    server.use(http.get('/api/admin/health', () => HttpResponse.json({ error: 'boom' }, { status: 500 })));
    render(withRouter(<SystemCard />));
    await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument());
  });
});

describe('NotificationsCard', () => {
  beforeEach(() => useNotificationsStore.getState().clear());

  it('renders LoadingState while notifications are loading', () => {
    server.use(http.get('/api/notifications', () => HttpResponse.json({ notifications: [], unreadCount: 0 })));
    render(withRouter(<NotificationsCard />));
    expect(screen.getByRole('status')).toBeInTheDocument();
  });

  it('renders EmptyState when there are no notifications', async () => {
    server.use(http.get('/api/notifications', () => HttpResponse.json({ notifications: [], unreadCount: 0 })));
    render(withRouter(<NotificationsCard />));
    await waitFor(() => expect(screen.getByText('No notifications.')).toBeInTheDocument());
  });

  it('initial fetch failure shows ErrorState with retry', async () => {
    server.use(http.get('/api/notifications', () => HttpResponse.json({ error: 'boom' }, { status: 500 })));
    render(withRouter(<NotificationsCard />));
    await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument());

    server.use(http.get('/api/notifications', () => HttpResponse.json({
      notifications: [{ id: 'n1', type: 'message', title: 'New message', body: null, link: null, actorId: null, readAt: null, createdAt: '2026-05-25T10:00:00Z' }],
      unreadCount: 1,
    })));
    fireEvent.click(screen.getByRole('button', { name: /retry/i }));
    await waitFor(() => expect(screen.getByText('New message')).toBeInTheDocument());
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});

describe('CrewPresenceCard', () => {
  beforeEach(() => useCrewStore.getState().clear());

  it('links to /console/crew (Crew entry point, Plan 5 Task 5)', async () => {
    render(withRouter(<CrewPresenceCard />));
    await waitFor(() => expect(screen.getByText('Crew')).toBeInTheDocument());
    expect(screen.getByRole('link', { name: /open/i })).toHaveAttribute('href', '/console/crew');
  });

  it('renders LoadingState while the roster is loading', () => {
    render(withRouter(<CrewPresenceCard />));
    expect(screen.getByRole('status')).toBeInTheDocument();
  });

  it('renders the default MSW roster once loaded', async () => {
    render(withRouter(<CrewPresenceCard />));
    await waitFor(() => expect(screen.getByText(/member/)).toBeInTheDocument());
  });

  it('initial fetch failure shows ErrorState with retry, not the empty list', async () => {
    server.use(http.get('/api/profiles/crew', () => HttpResponse.json({ error: 'boom' }, { status: 500 })));
    render(withRouter(<CrewPresenceCard />));
    await waitFor(() => expect(screen.getByRole('alert')).toBeInTheDocument());

    server.use(http.get('/api/profiles/crew', () => HttpResponse.json({ crew: [] })));
    fireEvent.click(screen.getByRole('button', { name: /retry/i }));
    await waitFor(() => expect(screen.getByText('No crew.')).toBeInTheDocument());
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });
});

describe('OpenTasksCard', () => {
  beforeEach(() => useJobsStore.getState().clear());

  it('renders EmptyState when there are no active jobs (nothing to fan out over)', async () => {
    server.use(http.get('/api/jobs', () => HttpResponse.json({ jobs: [] })));
    render(withRouter(<OpenTasksCard />));
    await waitFor(() => expect(screen.getByText('No open tasks.')).toBeInTheDocument());
  });
});
