// desktop/portal/src/console/stores/invoicesStore.ts
import { create } from 'zustand';
import type { Invoice, InvoiceLineItem } from '../api/invoicesClient';

interface InvoicesState {
  invoices: Invoice[];
  detailInvoice: Invoice | null;
  detailLineItems: InvoiceLineItem[];
  isLoadingList: boolean;
  isLoadingDetail: boolean;
  // Split per Plan 4A Task 5 review finding #2 (see jobsStore): list and
  // detail scopes poll independently (useInvoicesPolling), so a single
  // shared flag let a stale list poll false-flash an ErrorState on a detail
  // view that hadn't even fetched yet. Each scope now owns its own flag.
  listStale: boolean;
  detailStale: boolean;

  setInvoices: (invoices: Invoice[]) => void;
  setDetail: (invoice: Invoice, lineItems: InvoiceLineItem[]) => void;
  upsertInvoice: (invoice: Invoice) => void;
  removeInvoice: (id: string) => void;
  addLineItem: (item: InvoiceLineItem) => void;
  updateLineItem: (item: InvoiceLineItem) => void;
  removeLineItem: (itemId: string) => void;
  markListLoading: (b: boolean) => void;
  markDetailLoading: (b: boolean) => void;
  markListStale: (b: boolean) => void;
  markDetailStale: (b: boolean) => void;
  clear: () => void;
}

export const useInvoicesStore = create<InvoicesState>((set) => ({
  invoices: [],
  detailInvoice: null,
  detailLineItems: [],
  isLoadingList: false,
  isLoadingDetail: false,
  listStale: false,
  detailStale: false,

  setInvoices: (invoices) => set({ invoices, listStale: false }),
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
  markListStale: (listStale) => set({ listStale }),
  markDetailStale: (detailStale) => set({ detailStale }),
  clear: () => set({
    invoices: [], detailInvoice: null, detailLineItems: [],
    isLoadingList: false, isLoadingDetail: false, listStale: false, detailStale: false,
  }),
}));
