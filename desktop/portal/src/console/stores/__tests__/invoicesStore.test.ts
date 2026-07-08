import { describe, it, expect, beforeEach } from 'vitest';
import { useInvoicesStore } from '../invoicesStore';
import type { Invoice, InvoiceLineItem } from '../../api/invoicesClient';

function inv(id: string, overrides: Partial<Invoice> = {}): Invoice {
  return {
    id,
    organizationId: 'org-1',
    createdBy: 'u-1',
    invoiceNumber: `INV-2026-${id}`,
    clientName: null,
    clientEmail: null,
    issueDate: '2026-05-11T10:00:00Z',
    dueDate: null,
    status: 'draft',
    subtotal: 0,
    taxRate: 0,
    taxAmount: 0,
    totalDue: 0,
    notes: null,
    createdAt: '2026-05-11T10:00:00Z',
    updatedAt: '2026-05-11T10:00:00Z',
    ...overrides,
  };
}

function line(id: string, invoiceId: string, sortOrder = 0): InvoiceLineItem {
  return {
    id, invoiceId, description: id, quantity: 1, unit: 'ea', rate: 10, total: 10,
    category: 'other', sortOrder, createdAt: '2026-05-11T10:00:00Z',
  };
}

describe('invoicesStore', () => {
  beforeEach(() => useInvoicesStore.getState().clear());

  it('setInvoices replaces the list and clears listStale', () => {
    useInvoicesStore.getState().markListStale(true);
    useInvoicesStore.getState().setInvoices([inv('a'), inv('b')]);
    const s = useInvoicesStore.getState();
    expect(s.invoices.map((i) => i.id)).toEqual(['a', 'b']);
    expect(s.listStale).toBe(false);
  });

  it('markListStale toggles the list flag independently of detail', () => {
    useInvoicesStore.getState().markListStale(true);
    expect(useInvoicesStore.getState().listStale).toBe(true);
    expect(useInvoicesStore.getState().detailStale).toBe(false);
    useInvoicesStore.getState().markListStale(false);
    expect(useInvoicesStore.getState().listStale).toBe(false);
  });

  it('markDetailStale toggles the detail flag independently of list', () => {
    useInvoicesStore.getState().markDetailStale(true);
    expect(useInvoicesStore.getState().detailStale).toBe(true);
    expect(useInvoicesStore.getState().listStale).toBe(false);
    useInvoicesStore.getState().markDetailStale(false);
    expect(useInvoicesStore.getState().detailStale).toBe(false);
  });

  it('clear resets both stale flags', () => {
    useInvoicesStore.getState().markListStale(true);
    useInvoicesStore.getState().markDetailStale(true);
    useInvoicesStore.getState().clear();
    const s = useInvoicesStore.getState();
    expect(s.listStale).toBe(false);
    expect(s.detailStale).toBe(false);
  });

  it('upsertInvoice updates an existing row in place and mirrors into detail when matched', () => {
    useInvoicesStore.getState().setInvoices([inv('a', { status: 'draft' })]);
    useInvoicesStore.getState().setDetail(inv('a', { status: 'draft' }), []);
    useInvoicesStore.getState().upsertInvoice(inv('a', { status: 'issued' }));
    expect(useInvoicesStore.getState().invoices[0].status).toBe('issued');
    expect(useInvoicesStore.getState().detailInvoice?.status).toBe('issued');
  });

  it('upsertInvoice prepends a new row when id is unknown', () => {
    useInvoicesStore.getState().setInvoices([inv('a')]);
    useInvoicesStore.getState().upsertInvoice(inv('b'));
    expect(useInvoicesStore.getState().invoices.map((i) => i.id)).toEqual(['b', 'a']);
  });

  it('addLineItem dedupes by id and sorts by sortOrder', () => {
    useInvoicesStore.getState().setDetail(inv('a'), []);
    useInvoicesStore.getState().addLineItem(line('l2', 'a', 1));
    useInvoicesStore.getState().addLineItem(line('l1', 'a', 0));
    useInvoicesStore.getState().addLineItem(line('l1', 'a', 0)); // duplicate
    expect(useInvoicesStore.getState().detailLineItems.map((l) => l.id)).toEqual(['l1', 'l2']);
  });

  it('removeLineItem drops by id', () => {
    useInvoicesStore.getState().setDetail(inv('a'), [line('l1', 'a'), line('l2', 'a', 1)]);
    useInvoicesStore.getState().removeLineItem('l1');
    expect(useInvoicesStore.getState().detailLineItems.map((l) => l.id)).toEqual(['l2']);
  });
});
