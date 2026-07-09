import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useNotificationsPolling } from '../useNotificationsPolling';
import { useNotificationsStore } from '../../stores/notificationsStore';
import * as client from '../../api/notificationsClient';

describe('useNotificationsPolling', () => {
  beforeEach(() => {
    useNotificationsStore.getState().clear();
    vi.useFakeTimers();
  });
  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('fetches on mount and stores the result', async () => {
    const spy = vi.spyOn(client.notificationsClient, 'list').mockResolvedValue({
      ok: true,
      notifications: [{ id: 'a', type: 'message', title: 't', body: null, link: null, actorId: null, readAt: null, createdAt: '2026-05-25T10:00:00Z' }],
      unreadCount: 1,
    });
    renderHook(() => useNotificationsPolling(15000));
    await act(async () => { await Promise.resolve(); });
    expect(spy).toHaveBeenCalledTimes(1);
    expect(useNotificationsStore.getState().unreadCount).toBe(1);
  });

  it('fetches again after the interval', async () => {
    const spy = vi.spyOn(client.notificationsClient, 'list').mockResolvedValue({ ok: true, notifications: [], unreadCount: 0 });
    renderHook(() => useNotificationsPolling(15000));
    await act(async () => { await Promise.resolve(); });
    await act(async () => { vi.advanceTimersByTime(15001); await Promise.resolve(); });
    expect(spy).toHaveBeenCalledTimes(2);
  });

  it('marks stale on fetch failure', async () => {
    vi.spyOn(client.notificationsClient, 'list').mockResolvedValue({ ok: false, status: 500, error: 'oops' });
    renderHook(() => useNotificationsPolling(15000));
    await act(async () => { await Promise.resolve(); });
    expect(useNotificationsStore.getState().isStale).toBe(true);
  });

  it('cleans up the interval on unmount', async () => {
    const spy = vi.spyOn(client.notificationsClient, 'list').mockResolvedValue({ ok: true, notifications: [], unreadCount: 0 });
    const { unmount } = renderHook(() => useNotificationsPolling(15000));
    await act(async () => { await Promise.resolve(); });
    unmount();
    await act(async () => { vi.advanceTimersByTime(60000); await Promise.resolve(); });
    expect(spy).toHaveBeenCalledTimes(1);
  });
});

describe('useNotificationsPolling unmount safety', () => {
  beforeEach(() => { useNotificationsStore.getState().clear(); });
  afterEach(() => { vi.restoreAllMocks(); });

  it('ignores a response that resolves after unmount', async () => {
    let resolveFetch!: (v: Awaited<ReturnType<typeof client.notificationsClient.list>>) => void;
    vi.spyOn(client.notificationsClient, 'list').mockReturnValue(
      new Promise((res) => { resolveFetch = res; }),
    );
    const { unmount } = renderHook(() => useNotificationsPolling(15000));
    unmount();
    await act(async () => {
      resolveFetch({ ok: true, notifications: [], unreadCount: 0 });
      await Promise.resolve();
    });
    expect(useNotificationsStore.getState().notifications).toHaveLength(0);
    expect(useNotificationsStore.getState().isLoading).toBe(false);
  });
});

describe('useNotificationsPolling reload (cancellable retry)', () => {
  beforeEach(() => { useNotificationsStore.getState().clear(); });
  afterEach(() => { vi.restoreAllMocks(); });

  it('reload() re-fires the fetch', async () => {
    const spy = vi.spyOn(client.notificationsClient, 'list').mockResolvedValue({ ok: true, notifications: [], unreadCount: 0 });
    const { result } = renderHook(() => useNotificationsPolling(15000));
    await act(async () => { await Promise.resolve(); });
    expect(spy).toHaveBeenCalledTimes(1);

    act(() => { result.current.reload(); });
    await act(async () => { await Promise.resolve(); });
    expect(spy).toHaveBeenCalledTimes(2);
  });

  it('reload() after unmount is a no-op (does not throw, does not fetch)', async () => {
    const spy = vi.spyOn(client.notificationsClient, 'list').mockResolvedValue({ ok: true, notifications: [], unreadCount: 0 });
    const { result, unmount } = renderHook(() => useNotificationsPolling(15000));
    await act(async () => { await Promise.resolve(); });
    unmount();
    const callsBefore = spy.mock.calls.length;
    expect(() => result.current.reload()).not.toThrow();
    await act(async () => { await Promise.resolve(); });
    expect(spy.mock.calls.length).toBe(callsBefore);
  });
});
