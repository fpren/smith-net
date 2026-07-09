import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { describe, it, expect, beforeEach } from 'vitest';
import { http, HttpResponse } from 'msw';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { InvoiceDetailRoute } from '../InvoiceDetailRoute';
import { useInvoicesStore } from '../../stores/invoicesStore';
import { server } from '../../test/msw-server';

describe('InvoiceDetailRoute', () => {
  beforeEach(() => useInvoicesStore.getState().clear());

  function renderAt(path: string) {
    return render(
      <MemoryRouter initialEntries={[path]}>
        <Routes>
          <Route path="/console/invoices/:id" element={<InvoiceDetailRoute />} />
        </Routes>
      </MemoryRouter>,
    );
  }

  it('loads and renders invoice header + empty line items state', async () => {
    renderAt('/console/invoices/inv-1');
    await waitFor(() => expect(screen.getByText('INV-2026-0001')).toBeInTheDocument());
    expect(screen.getByText('Acme Roofing')).toBeInTheDocument();
    expect(screen.getByText(/No line items yet/i)).toBeInTheDocument();
  });

  it('Issue button calls setStatus and flips the badge', async () => {
    renderAt('/console/invoices/inv-1');
    await waitFor(() => expect(screen.getByText('INV-2026-0001')).toBeInTheDocument());
    fireEvent.click(screen.getByRole('button', { name: /Issue/i }));
    // After PATCH the store gets the new status and the badge updates.
    await waitFor(() => {
      const badges = screen.getAllByText(/issued/i);
      expect(badges.length).toBeGreaterThan(0);
    });
  });

  it('renders line items + total when present', async () => {
    server.use(
      http.get('/api/invoices/:id', ({ params }) =>
        HttpResponse.json({
          invoice: {
            id: params.id, organizationId: 'o', createdBy: 'u',
            invoiceNumber: 'INV-2026-0001', clientName: 'Acme', clientEmail: null,
            issueDate: '2026-05-11T10:00:00Z', dueDate: null, status: 'draft',
            subtotal: 340, taxRate: 0.0825, taxAmount: 28.05, totalDue: 368.05,
            notes: null,
            createdAt: '2026-05-11T10:00:00Z', updatedAt: '2026-05-11T11:00:00Z',
          },
          lineItems: [
            {
              id: 'li-1', invoiceId: params.id, description: 'Labor',
              quantity: 4, unit: 'hr', rate: 85, total: 340,
              category: 'labor', sortOrder: 0, createdAt: '2026-05-11T11:00:00Z',
            },
          ],
        }),
      ),
    );
    renderAt('/console/invoices/inv-1');
    await waitFor(() => expect(screen.getByText('Labor')).toBeInTheDocument());
    expect(screen.getByText(/\$368\.05/)).toBeInTheDocument();
  });

  it('renders LoadingState before the invoice detail loads', () => {
    renderAt('/console/invoices/loading-test');
    expect(screen.getByRole('status')).toBeInTheDocument();
  });

  it('renders ErrorState and retry re-fires the detail fetch on failure', async () => {
    server.use(http.get('/api/invoices/:id', () => HttpResponse.json({ error: 'boom' }, { status: 500 })));
    renderAt('/console/invoices/err-1');
    const retry = await screen.findByRole('button', { name: /retry/i });

    server.use(
      http.get('/api/invoices/:id', ({ params }) => HttpResponse.json({
        invoice: {
          id: params.id, organizationId: 'o', createdBy: 'u', invoiceNumber: 'INV-RECOVERED',
          clientName: 'Recovered Client', clientEmail: null,
          issueDate: '2026-05-11T10:00:00Z', dueDate: null, status: 'draft',
          subtotal: 0, taxRate: 0, taxAmount: 0, totalDue: 0, notes: null,
          createdAt: '2026-05-11T10:00:00Z', updatedAt: '2026-05-11T10:00:00Z',
        },
        lineItems: [],
      })),
    );
    fireEvent.click(retry);

    expect(await screen.findByText('INV-RECOVERED')).toBeInTheDocument();
    expect(useInvoicesStore.getState().detailStale).toBe(false);
  });

  it('a stale list poll does not false-flash an ErrorState on a fresh detail mount', async () => {
    // listStale simulates a concurrent/previous list-scope poll failure (or
    // the offline-persistence hydrate marking cached list data stale). It
    // must not leak into the detail scope, which hasn't fetched yet.
    useInvoicesStore.getState().markListStale(true);
    renderAt('/console/invoices/fresh-inv');

    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    await waitFor(() => expect(screen.getByText('INV-2026-0001')).toBeInTheDocument());
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(useInvoicesStore.getState().listStale).toBe(true); // untouched by the detail fetch
  });

  it('back-link is hidden at xl (beside-list panel view supersedes it)', async () => {
    renderAt('/console/invoices/inv-1');
    await waitFor(() => expect(screen.getByText('INV-2026-0001')).toBeInTheDocument());
    const backLink = screen.getByText('back to invoices');
    expect(backLink.className).toMatch(/xl:hidden/);
  });
});
