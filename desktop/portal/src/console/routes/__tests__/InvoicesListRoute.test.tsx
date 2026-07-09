import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { describe, it, expect, beforeEach } from 'vitest';
import { http, HttpResponse } from 'msw';
import { MemoryRouter, Routes, Route } from 'react-router-dom';
import { InvoicesListRoute } from '../InvoicesListRoute';
import { useInvoicesStore } from '../../stores/invoicesStore';
import { server } from '../../test/msw-server';

function renderNestedAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/console/invoices" element={<InvoicesListRoute />}>
          <Route path=":id" element={<div>Detail placeholder</div>} />
        </Route>
      </Routes>
    </MemoryRouter>,
  );
}

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

  it('shows the stale banner when list fetch fails but cached invoices exist', async () => {
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

  it('renders LoadingState while invoices are loading', () => {
    useInvoicesStore.getState().markListLoading(true);
    render(<MemoryRouter><InvoicesListRoute /></MemoryRouter>);
    expect(screen.getByRole('status')).toBeInTheDocument();
  });

  it('retry on the stale banner re-fires the fetch and clears the banner', async () => {
    server.use(http.get('/api/invoices', () => HttpResponse.json({
      invoices: [{
        id: 'inv-1', organizationId: 'o', createdBy: 'u', invoiceNumber: 'INV-2026-0001',
        clientName: 'Acme Roofing', clientEmail: null,
        issueDate: '2026-05-11T10:00:00Z', dueDate: null, status: 'draft',
        subtotal: 0, taxRate: 0, taxAmount: 0, totalDue: 0, notes: null,
        createdAt: '2026-05-11T10:00:00Z', updatedAt: '2026-05-11T10:00:00Z',
      }],
    })));
    render(<MemoryRouter><InvoicesListRoute /></MemoryRouter>);
    await screen.findByText('INV-2026-0001');
    useInvoicesStore.getState().markListStale(true);
    const retry = await screen.findByRole('button', { name: /retry/i });

    server.use(http.get('/api/invoices', () => HttpResponse.json({
      invoices: [{
        id: 'inv-1', organizationId: 'o', createdBy: 'u', invoiceNumber: 'INV-2026-0001',
        clientName: 'Acme Roofing', clientEmail: null,
        issueDate: '2026-05-11T10:00:00Z', dueDate: null, status: 'issued',
        subtotal: 0, taxRate: 0, taxAmount: 0, totalDue: 0, notes: null,
        createdAt: '2026-05-11T10:00:00Z', updatedAt: '2026-05-11T11:00:00Z',
      }],
    })));
    fireEvent.click(retry);

    await waitFor(() => expect(screen.getByText(/ISSUED \(1\)/)).toBeInTheDocument());
    expect(useInvoicesStore.getState().listStale).toBe(false);
  });

  it('initial fetch failure shows ErrorState with retry, not EmptyState', async () => {
    server.use(http.get('/api/invoices', () => HttpResponse.json({ error: 'boom' }, { status: 500 })));
    render(<MemoryRouter><InvoicesListRoute /></MemoryRouter>);

    const alert = await screen.findByRole('alert');
    expect(alert).toBeInTheDocument();
    expect(screen.queryByText(/no invoices yet/i)).not.toBeInTheDocument();
    const retry = screen.getByRole('button', { name: /retry/i });

    server.use(http.get('/api/invoices', () => HttpResponse.json({
      invoices: [{
        id: 'inv-1', organizationId: 'o', createdBy: 'u', invoiceNumber: 'INV-2026-0001',
        clientName: 'Acme Roofing', clientEmail: null,
        issueDate: '2026-05-11T10:00:00Z', dueDate: null, status: 'draft',
        subtotal: 0, taxRate: 0, taxAmount: 0, totalDue: 0, notes: null,
        createdAt: '2026-05-11T10:00:00Z', updatedAt: '2026-05-11T10:00:00Z',
      }],
    })));
    fireEvent.click(retry);

    await waitFor(() => expect(screen.getByText('INV-2026-0001')).toBeInTheDocument());
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  describe('beside-list detail panel (Plan 4C Task 2)', () => {
    const invoice = {
      id: 'inv-1', organizationId: 'o', createdBy: 'u', invoiceNumber: 'INV-2026-0001',
      clientName: 'Acme Roofing', clientEmail: null,
      issueDate: '2026-05-11T10:00:00Z', dueDate: null, status: 'draft' as const,
      subtotal: 0, taxRate: 0, taxAmount: 0, totalDue: 0, notes: null,
      createdAt: '2026-05-11T10:00:00Z', updatedAt: '2026-05-11T10:00:00Z',
    };

    it('shows the "Select an invoice" empty panel when no id is active', () => {
      useInvoicesStore.getState().setInvoices([invoice]);
      renderNestedAt('/console/invoices');
      expect(screen.getByText('Select an invoice')).toBeInTheDocument();
      expect(screen.queryByText('Detail placeholder')).not.toBeInTheDocument();
    });

    it('hides the list (below xl) and renders the outlet when an id is active', () => {
      useInvoicesStore.getState().setInvoices([invoice]);
      renderNestedAt('/console/invoices/inv-1');
      expect(screen.getByText('Detail placeholder')).toBeInTheDocument();
      expect(screen.queryByText('Select an invoice')).not.toBeInTheDocument();
      const listItem = screen.getByText('INV-2026-0001');
      const listContainer = listItem.closest('div.hidden');
      expect(listContainer).not.toBeNull();
      expect(listContainer?.className).toMatch(/hidden/);
      expect(listContainer?.className).toMatch(/xl:block/);
    });

    it('applies the panel-in slide animation class to the outlet wrapper, keyed on the active id', () => {
      useInvoicesStore.getState().setInvoices([invoice]);
      renderNestedAt('/console/invoices/inv-1');
      const panel = screen.getByText('Detail placeholder').closest('.panel-in');
      expect(panel).not.toBeNull();
    });

    it('does not render "Select an invoice" when the invoice list itself is empty and no id is active (double-EmptyState fix)', async () => {
      server.use(http.get('/api/invoices', () => HttpResponse.json({ invoices: [] })));
      renderNestedAt('/console/invoices');
      await waitFor(() => expect(screen.getByText(/No invoices yet/i)).toBeInTheDocument());
      expect(screen.queryByText('Select an invoice')).not.toBeInTheDocument();
    });
  });
});
