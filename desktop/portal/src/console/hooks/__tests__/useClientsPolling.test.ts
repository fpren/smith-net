// desktop/portal/src/console/hooks/__tests__/useClientsPolling.test.ts
import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useClientsPolling } from '../useClientsPolling';
import { useClientsStore } from '../../stores/clientsStore';
import * as clientsClientModule from '../../api/clientsClient';
import type { Client } from '../../api/clientsClient';

const fixture = (id: string): Client => ({
  id, ownerId: 'f-1', name: `Client ${id}`, email: null, phone: null, address: null,
  company: null, notes: null, createdAt: '', updatedAt: '',
});

describe('useClientsPolling', () => {
  beforeEach(() => {
    useClientsStore.getState().clear();
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('fires an immediate fetch on mount (list scope)', async () => {
    const spy = vi.spyOn(clientsClientModule.clientsClient, 'list').mockResolvedValue({ ok: true, clients: [] });
    renderHook(() => useClientsPolling('list', 15000));
    await act(async () => { await Promise.resolve(); });
    expect(spy).toHaveBeenCalledTimes(1);
  });

  it('fires another fetch after the interval', async () => {
    const spy = vi.spyOn(clientsClientModule.clientsClient, 'list').mockResolvedValue({ ok: true, clients: [] });
    renderHook(() => useClientsPolling('list', 15000));
    await act(async () => { await Promise.resolve(); });
    await act(async () => { vi.advanceTimersByTime(15001); await Promise.resolve(); });
    expect(spy).toHaveBeenCalledTimes(2);
  });

  it('detail scope calls getById with the id', async () => {
    const spy = vi.spyOn(clientsClientModule.clientsClient, 'getById')
      .mockResolvedValue({ ok: true, client: fixture('x'), jobs: [] });
    renderHook(() => useClientsPolling({ detail: 'x' }, 15000));
    await act(async () => { await Promise.resolve(); });
    expect(spy).toHaveBeenCalledWith('x');
  });

  it('sets listStale=true on list fetch failure', async () => {
    vi.spyOn(clientsClientModule.clientsClient, 'list').mockResolvedValue({ ok: false, status: 500, error: 'oops' });
    renderHook(() => useClientsPolling('list', 15000));
    await act(async () => { await Promise.resolve(); });
    expect(useClientsStore.getState().listStale).toBe(true);
    expect(useClientsStore.getState().detailStale).toBe(false);
  });

  it('sets detailStale=true on detail fetch failure, without touching listStale', async () => {
    vi.spyOn(clientsClientModule.clientsClient, 'getById').mockResolvedValue({ ok: false, status: 500, error: 'oops' });
    renderHook(() => useClientsPolling({ detail: 'x' }, 15000));
    await act(async () => { await Promise.resolve(); });
    expect(useClientsStore.getState().detailStale).toBe(true);
    expect(useClientsStore.getState().listStale).toBe(false);
  });

  it('cleans up interval on unmount', async () => {
    const spy = vi.spyOn(clientsClientModule.clientsClient, 'list').mockResolvedValue({ ok: true, clients: [] });
    const { unmount } = renderHook(() => useClientsPolling('list', 15000));
    await act(async () => { await Promise.resolve(); });
    unmount();
    await act(async () => { vi.advanceTimersByTime(60000); await Promise.resolve(); });
    expect(spy).toHaveBeenCalledTimes(1);
  });
});

describe('useClientsPolling unmount safety', () => {
  beforeEach(() => { useClientsStore.getState().clear(); });
  afterEach(() => { vi.restoreAllMocks(); });

  it('ignores a list response that resolves after unmount', async () => {
    let resolveFetch!: (v: Awaited<ReturnType<typeof clientsClientModule.clientsClient.list>>) => void;
    vi.spyOn(clientsClientModule.clientsClient, 'list').mockReturnValue(
      new Promise((res) => { resolveFetch = res; }),
    );
    const { unmount } = renderHook(() => useClientsPolling('list', 15000));
    unmount();
    await act(async () => {
      resolveFetch({ ok: true, clients: [fixture('late')] });
      await Promise.resolve();
    });
    expect(useClientsStore.getState().clients).toHaveLength(0);
    expect(useClientsStore.getState().isLoadingList).toBe(false);
  });
});

describe('useClientsPolling reload (cancellable retry)', () => {
  beforeEach(() => { useClientsStore.getState().clear(); });
  afterEach(() => { vi.restoreAllMocks(); });

  it('reload() re-fires the fetch for the mounted scope', async () => {
    const spy = vi.spyOn(clientsClientModule.clientsClient, 'getById')
      .mockResolvedValue({ ok: true, client: fixture('a'), jobs: [] });
    const { result } = renderHook(() => useClientsPolling({ detail: 'a' }, 15000));
    await act(async () => { await Promise.resolve(); });
    expect(spy).toHaveBeenCalledTimes(1);

    act(() => { result.current.reload(); });
    await act(async () => { await Promise.resolve(); });
    expect(spy).toHaveBeenCalledTimes(2);
  });

  it('a reload in flight when the component unmounts does not clobber the store on resolve', async () => {
    let resolveReload!: (v: Awaited<ReturnType<typeof clientsClientModule.clientsClient.getById>>) => void;
    vi.spyOn(clientsClientModule.clientsClient, 'getById')
      .mockResolvedValueOnce({ ok: true, client: fixture('a'), jobs: [] })
      .mockImplementationOnce(() => new Promise((res) => { resolveReload = res; }));

    const { result, unmount } = renderHook(() => useClientsPolling({ detail: 'a' }, 15000));
    await act(async () => { await Promise.resolve(); });
    expect(useClientsStore.getState().detailClient?.id).toBe('a');

    act(() => { result.current.reload(); });
    expect(useClientsStore.getState().isLoadingDetail).toBe(true);

    unmount();

    await act(async () => {
      resolveReload({ ok: true, client: fixture('b'), jobs: [] });
      await Promise.resolve();
    });

    expect(useClientsStore.getState().detailClient?.id).toBe('a');
    expect(useClientsStore.getState().isLoadingDetail).toBe(false);
  });

  it('reload() after unmount is a no-op (does not throw, does not fetch)', async () => {
    const spy = vi.spyOn(clientsClientModule.clientsClient, 'getById')
      .mockResolvedValue({ ok: true, client: fixture('a'), jobs: [] });
    const { result, unmount } = renderHook(() => useClientsPolling({ detail: 'a' }, 15000));
    await act(async () => { await Promise.resolve(); });
    unmount();
    const callsBefore = spy.mock.calls.length;
    expect(() => result.current.reload()).not.toThrow();
    await act(async () => { await Promise.resolve(); });
    expect(spy.mock.calls.length).toBe(callsBefore);
  });
});
