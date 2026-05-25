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
