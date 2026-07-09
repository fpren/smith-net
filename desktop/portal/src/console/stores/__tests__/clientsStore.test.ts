import { describe, it, expect, beforeEach } from 'vitest';
import { useClientsStore } from '../clientsStore';
import type { Client } from '../../api/clientsClient';

const c = (id: string, name: string): Client => ({
  id, ownerId: 'f-1', name, email: null, phone: null, address: null,
  company: null, notes: null, createdAt: '2026-05-11T10:00:00Z', updatedAt: '2026-05-11T10:00:00Z',
});

describe('clientsStore', () => {
  beforeEach(() => useClientsStore.getState().clear());

  it('setClients clears listStale + stamps fetch', () => {
    useClientsStore.getState().markListStale(true);
    useClientsStore.getState().setClients([c('a', 'A')]);
    expect(useClientsStore.getState().clients).toHaveLength(1);
    expect(useClientsStore.getState().listStale).toBe(false);
  });

  it('upsertClient replaces by id or prepends', () => {
    useClientsStore.getState().setClients([c('a', 'A')]);
    useClientsStore.getState().upsertClient(c('a', 'A2'));
    expect(useClientsStore.getState().clients[0].name).toBe('A2');
    useClientsStore.getState().upsertClient(c('b', 'B'));
    expect(useClientsStore.getState().clients).toHaveLength(2);
  });

  it('removeClient drops by id', () => {
    useClientsStore.getState().setClients([c('a', 'A'), c('b', 'B')]);
    useClientsStore.getState().removeClient('a');
    expect(useClientsStore.getState().clients.map((x) => x.id)).toEqual(['b']);
  });

  it('markListStale and markDetailStale toggle independently', () => {
    useClientsStore.getState().markListStale(true);
    expect(useClientsStore.getState().listStale).toBe(true);
    expect(useClientsStore.getState().detailStale).toBe(false);
    useClientsStore.getState().markDetailStale(true);
    expect(useClientsStore.getState().detailStale).toBe(true);
    useClientsStore.getState().markListStale(false);
    expect(useClientsStore.getState().listStale).toBe(false);
    expect(useClientsStore.getState().detailStale).toBe(true);
  });

  it('markListLoading and markDetailLoading toggle independently', () => {
    useClientsStore.getState().markListLoading(true);
    expect(useClientsStore.getState().isLoadingList).toBe(true);
    expect(useClientsStore.getState().isLoadingDetail).toBe(false);
    useClientsStore.getState().markDetailLoading(true);
    expect(useClientsStore.getState().isLoadingDetail).toBe(true);
  });

  it('clear resets loading + stale flags for both scopes', () => {
    useClientsStore.getState().markListLoading(true);
    useClientsStore.getState().markDetailLoading(true);
    useClientsStore.getState().markListStale(true);
    useClientsStore.getState().markDetailStale(true);
    useClientsStore.getState().clear();
    const s = useClientsStore.getState();
    expect(s.isLoadingList).toBe(false);
    expect(s.isLoadingDetail).toBe(false);
    expect(s.listStale).toBe(false);
    expect(s.detailStale).toBe(false);
  });
});
