// desktop/portal/src/console/stores/clientsStore.ts
import { create } from 'zustand';
import type { Client } from '../api/clientsClient';

interface ClientsState {
  clients: Client[];
  detailClient: Client | null;
  detailJobs: any[];
  isLoadingList: boolean;
  isLoadingDetail: boolean;
  lastFetchedAt: number | null;
  // Split per Plan 4A jobs/invoices precedent (commit 3038933): list and
  // detail scopes poll independently (see useClientsPolling), so a single
  // shared flag let a stale list poll false-flash an ErrorState on a detail
  // view that hadn't even fetched yet. Each scope now owns its own flag.
  listStale: boolean;
  detailStale: boolean;
  setClients: (clients: Client[]) => void;
  setDetail: (client: Client, jobs: any[]) => void;
  upsertClient: (client: Client) => void;
  removeClient: (id: string) => void;
  markListLoading: (b: boolean) => void;
  markDetailLoading: (b: boolean) => void;
  markListStale: (b: boolean) => void;
  markDetailStale: (b: boolean) => void;
  clear: () => void;
}

export const useClientsStore = create<ClientsState>((set) => ({
  clients: [],
  detailClient: null,
  detailJobs: [],
  isLoadingList: false,
  isLoadingDetail: false,
  lastFetchedAt: null,
  listStale: false,
  detailStale: false,
  setClients: (clients) => set({ clients, lastFetchedAt: Date.now(), listStale: false }),
  setDetail: (detailClient, detailJobs) => set({ detailClient, detailJobs }),
  upsertClient: (client) => set((s) => {
    const idx = s.clients.findIndex((c) => c.id === client.id);
    const clients = idx === -1 ? [client, ...s.clients] : s.clients.map((c, i) => (i === idx ? client : c));
    const detailClient = s.detailClient && s.detailClient.id === client.id ? client : s.detailClient;
    return { clients, detailClient };
  }),
  removeClient: (id) => set((s) => ({ clients: s.clients.filter((c) => c.id !== id) })),
  markListLoading: (isLoadingList) => set({ isLoadingList }),
  markDetailLoading: (isLoadingDetail) => set({ isLoadingDetail }),
  markListStale: (listStale) => set({ listStale }),
  markDetailStale: (detailStale) => set({ detailStale }),
  clear: () => set({
    clients: [], detailClient: null, detailJobs: [],
    isLoadingList: false, isLoadingDetail: false,
    lastFetchedAt: null, listStale: false, detailStale: false,
  }),
}));
