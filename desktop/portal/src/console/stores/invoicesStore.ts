// desktop/portal/src/console/stores/invoicesStore.ts
import { create } from 'zustand';
import type { Invoice, InvoiceLineItem } from '../api/invoicesClient';

interface InvoicesState {
  invoices: Invoice[];
  detailInvoice: Invoice | null;
  detailLineItems: InvoiceLineItem[];
  isLoadingList: boolean;
  isLoadingDetail: boolean;
  isStale: boolean;

  setInvoices: (invoices: Invoice[]) => void;
  setDetail: (invoice: Invoice, lineItems: InvoiceLineItem[]) => void;
  upsertInvoice: (invoice: Invoice) => void;
  removeInvoice: (id: string) => void;
  addLineItem: (item: InvoiceLineItem) => void;
  updateLineItem: (item: InvoiceLineItem) => void;
  removeLineItem: (itemId: string) => void;
  markListLoading: (b: boolean) => void;
  markDetailLoading: (b: boolean) => void;
  markStale: (b: boolean) => void;
  clear: () => void;
}

export const useInvoicesStore = create<InvoicesState>((set) => ({
  invoices: [],
  detailInvoice: null,
  detailLineItems: [],
  isLoadingList: false,
  isLoadingDetail: false,
  isStale: false,

  setInvoices: (invoices) => set({ invoices, isStale: false }),
  setDetail: (detailInvoice, detailLineItems) => set({ detailInvoice, detailLineItems }),
  upsertInvoice: (invoice) => set((s) => {
    const idx = s.invoices.findIndex((i) => i.id === invoice.id);
    const next = idx === -1 ? [invoice, ...s.invoices] : s.invoices.map((i, k) => (k === idx ? invoice : i));
    const nextDetail = s.detailInvoice && s.detailInvoice.id === invoice.id ? invoice : s.detailInvoice;
    return { invoices: next, detailInvoice: nextDetail };
  }),
  removeInvoice: (id) => set((s) => ({
    invoices: s.invoices.filter((i) => i.id !== id),
    detailInvoice: s.detailInvoice && s.detailInvoice.id === id ? null : s.detailInvoice,
  })),
  addLineItem: (item) => set((s) => {
    if (s.detailLineItems.some((li) => li.id === item.id)) return {};
    return { detailLineItems: [...s.detailLineItems, item].sort((a, b) => a.sortOrder - b.sortOrder) };
  }),
  updateLineItem: (item) => set((s) => ({
    detailLineItems: s.detailLineItems.map((li) => (li.id === item.id ? item : li)),
  })),
  removeLineItem: (itemId) => set((s) => ({
    detailLineItems: s.detailLineItems.filter((li) => li.id !== itemId),
  })),
  markListLoading: (isLoadingList) => set({ isLoadingList }),
  markDetailLoading: (isLoadingDetail) => set({ isLoadingDetail }),
  markStale: (isStale) => set({ isStale }),
  clear: () => set({
    invoices: [], detailInvoice: null, detailLineItems: [],
    isLoadingList: false, isLoadingDetail: false, isStale: false,
  }),
}));
