import { render, screen, waitFor } from '@testing-library/react';
import { describe, it, expect, beforeEach } from 'vitest';
import { http, HttpResponse } from 'msw';
import { MemoryRouter } from 'react-router-dom';
import { InvoicesListRoute } from '../InvoicesListRoute';
import { useInvoicesStore } from '../../stores/invoicesStore';
import { server } from '../../test/msw-server';

describe('InvoicesListRoute', () => {
  beforeEach(() => useInvoicesStore.getState().clear());

  it('renders the polled invoice in a DRAFT section row', async () => {
    render(<MemoryRouter><InvoicesListRoute /></MemoryRouter>);
    await waitFor(() => expect(screen.getByText('INV-2026-0001')).toBeInTheDocument());
    expect(screen.getByText('Acme Roofing')).toBeInTheDocument();
    expect(screen.getByText(/DRAFT \(1\)/)).toBeInTheDocument();
  });

  it('shows empty-state CTA when there are no invoices', async () => {
    server.use(http.get('/api/invoices', () => HttpResponse.json({ invoices: [] })));
    render(<MemoryRouter><InvoicesListRoute /></MemoryRouter>);
    await waitFor(() => expect(screen.getByText(/No invoices yet/i)).toBeInTheDocument());
    expect(screen.getByRole('button', { name: /create your first invoice/i })).toBeInTheDocument();
  });

  it('shows the [OFFLINE] banner when list fetch fails', async () => {
    server.use(http.get('/api/invoices', () => HttpResponse.json({ error: 'boom' }, { status: 500 })));
    useInvoicesStore.getState().setInvoices([{
      id: 'inv-1', organizationId: 'o', createdBy: 'u', invoiceNumber: 'INV-2026-0001',
      clientName: null, clientEmail: null,
      issueDate: '2026-05-11T10:00:00Z', dueDate: null, status: 'draft',
      subtotal: 0, taxRate: 0, taxAmount: 0, totalDue: 0, notes: null,
      createdAt: '2026-05-11T10:00:00Z', updatedAt: '2026-05-11T10:00:00Z',
    }]);
    render(<MemoryRouter><InvoicesListRoute /></MemoryRouter>);
    await waitFor(() => expect(screen.getByText(/Couldn't refresh/i)).toBeInTheDocument());
  });
});
