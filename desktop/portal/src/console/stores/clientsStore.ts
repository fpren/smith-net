// desktop/portal/src/console/stores/clientsStore.ts
import { create } from 'zustand';
import type { Client } from '../api/clientsClient';

interface ClientsState {
  clients: Client[];
  detailClient: Client | null;
  detailJobs: any[];
  lastFetchedAt: number | null;
  isStale: boolean;
  setClients: (clients: Client[]) => void;
  setDetail: (client: Client, jobs: any[]) => void;
  upsertClient: (client: Client) => void;
  removeClient: (id: string) => void;
  markStale: (b: boolean) => void;
  clear: () => void;
}

export const useClientsStore = create<ClientsState>((set) => ({
  clients: [],
  detailClient: null,
  detailJobs: [],
  lastFetchedAt: null,
  isStale: false,
  setClients: (clients) => set({ clients, lastFetchedAt: Date.now(), isStale: false }),
  setDetail: (detailClient, detailJobs) => set({ detailClient, detailJobs }),
  upsertClient: (client) => set((s) => {
    const idx = s.clients.findIndex((c) => c.id === client.id);
    const clients = idx === -1 ? [client, ...s.clients] : s.clients.map((c, i) => (i === idx ? client : c));
    const detailClient = s.detailClient && s.detailClient.id === client.id ? client : s.detailClient;
    return { clients, detailClient };
  }),
  removeClient: (id) => set((s) => ({ clients: s.clients.filter((c) => c.id !== id) })),
  markStale: (isStale) => set({ isStale }),
  clear: () => set({ clients: [], detailClient: null, detailJobs: [], lastFetchedAt: null, isStale: false }),
}));
