import { describe, it, expect, beforeEach } from 'vitest';
import { useClientsStore } from '../clientsStore';
import type { Client } from '../../api/clientsClient';

const c = (id: string, name: string): Client => ({
  id, ownerId: 'f-1', name, email: null, phone: null, address: null,
  company: null, notes: null, createdAt: '2026-05-11T10:00:00Z', updatedAt: '2026-05-11T10:00:00Z',
});

describe('clientsStore', () => {
  beforeEach(() => useClientsStore.getState().clear());

  it('setClients clears stale + stamps fetch', () => {
    useClientsStore.getState().markStale(true);
    useClientsStore.getState().setClients([c('a', 'A')]);
    expect(useClientsStore.getState().clients).toHaveLength(1);
    expect(useClientsStore.getState().isStale).toBe(false);
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
});
