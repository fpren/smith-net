// desktop/portal/src/console/api/invoicesClient.ts
//
// REST wrapper for /api/invoices and /api/line-items. Mirrors the shape
// of jobsClient.ts / tasksClient.ts (credentials:'include' cookie auth).

import { httpCall } from './httpCall';

export type InvoiceStatus =
  | 'draft' | 'issued' | 'sent' | 'viewed' | 'paid' | 'overdue' | 'disputed' | 'cancelled';
export type LineCategory = 'labor' | 'materials' | 'travel' | 'change_order' | 'other';

export interface Invoice {
  id: string;
  organizationId: string;
  createdBy: string;
  invoiceNumber: string;
  clientName: string | null;
  clientEmail: string | null;
  issueDate: string;
  dueDate: string | null;
  status: InvoiceStatus;
  subtotal: number;
  taxRate: number;
  taxAmount: number;
  totalDue: number;
  notes: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface InvoiceLineItem {
  id: string;
  invoiceId: string;
  description: string;
  quantity: number;
  unit: string;
  rate: number;
  total: number;
  category: LineCategory;
  sortOrder: number;
  createdAt: string;
}

export type InvoicesResult<T> =
  | ({ ok: true } & T)
  | { ok: false; status: number; error: string };

interface JsonInit { method?: string; body?: unknown }

async function call<T>(path: string, init: JsonInit = {}): Promise<InvoicesResult<T>> {
  const r = await httpCall<T>(path, {
    method: init.method ?? 'GET',
    headers: init.body !== undefined ? { 'Content-Type': 'application/json' } : undefined,
    body: init.body !== undefined ? JSON.stringify(init.body) : undefined,
  });
  if (!r.ok) {
    return { ok: false, status: r.status, error: r.error };
  }
  return { ok: true, ...((r.data ?? {}) as T) } as InvoicesResult<T>;
}

export interface CreateInvoiceInput {
  clientName?: string;
  clientEmail?: string;
  dueDate?: string;
  notes?: string;
}
export interface UpdateInvoiceInput {
  clientName?: string | null;
  clientEmail?: string | null;
  dueDate?: string | null;
  taxRate?: number;
  notes?: string | null;
}
export interface AddLineItemInput {
  description: string;
  quantity?: number;
  unit?: string;
  rate: number;
  category?: LineCategory;
}
export interface UpdateLineItemInput {
  description?: string;
  quantity?: number;
  unit?: string;
  rate?: number;
  category?: LineCategory;
  sortOrder?: number;
}

export const invoicesClient = {
  list: () => call<{ invoices: Invoice[] }>('/api/invoices'),
  getById: (id: string) =>
    call<{ invoice: Invoice; lineItems: InvoiceLineItem[] }>(`/api/invoices/${encodeURIComponent(id)}`),
  create: (input: CreateInvoiceInput) =>
    call<{ invoice: Invoice }>('/api/invoices', { method: 'POST', body: input }),
  update: (id: string, patch: UpdateInvoiceInput) =>
    call<{ invoice: Invoice }>(`/api/invoices/${encodeURIComponent(id)}`, { method: 'PATCH', body: patch }),
  setStatus: (id: string, status: InvoiceStatus) =>
    call<{ invoice: Invoice }>(`/api/invoices/${encodeURIComponent(id)}/status`, { method: 'PATCH', body: { status } }),
  delete: (id: string) =>
    call<{}>(`/api/invoices/${encodeURIComponent(id)}`, { method: 'DELETE' }),
  addLineItem: (invoiceId: string, input: AddLineItemInput) =>
    call<{ lineItem: InvoiceLineItem }>(`/api/invoices/${encodeURIComponent(invoiceId)}/line-items`, { method: 'POST', body: input }),
  updateLineItem: (itemId: string, patch: UpdateLineItemInput) =>
    call<{ lineItem: InvoiceLineItem }>(`/api/line-items/${encodeURIComponent(itemId)}`, { method: 'PATCH', body: patch }),
  deleteLineItem: (itemId: string) =>
    call<{}>(`/api/line-items/${encodeURIComponent(itemId)}`, { method: 'DELETE' }),
};
