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
});
