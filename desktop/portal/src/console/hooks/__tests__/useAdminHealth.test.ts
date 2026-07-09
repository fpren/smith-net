import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useAdminHealth } from '../useAdminHealth';
import { useAdminHealthStore } from '../../stores/adminHealthStore';
import { useAuthStore } from '../../auth/authStore';
import * as adminHealthClient from '../../api/adminHealthClient';

const HEALTH_OK = {
  ok: true as const,
  workers: [],
  queue: { byKindState: [], oldestQueued: null, oldestRunning: null },
};

function loginAsAdmin() {
  useAuthStore.getState().setUser({
    id: 'u1', email: 'x@y.com', displayName: 'X', role: 'admin', emailVerified: true,
  });
}

describe('useAdminHealth', () => {
  beforeEach(() => {
    useAdminHealthStore.getState().clear();
    useAuthStore.getState().clear();
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('fires an immediate fetch on mount for an admin', async () => {
    loginAsAdmin();
    const spy = vi.spyOn(adminHealthClient.adminHealthClient, 'get').mockResolvedValue(HEALTH_OK);
    renderHook(() => useAdminHealth(15000));
    await act(async () => { await Promise.resolve(); });
    expect(spy).toHaveBeenCalledTimes(1);
  });

  it('does not fetch for a non-admin', async () => {
    const spy = vi.spyOn(adminHealthClient.adminHealthClient, 'get').mockResolvedValue(HEALTH_OK);
    renderHook(() => useAdminHealth(15000));
    await act(async () => { await Promise.resolve(); });
    expect(spy).not.toHaveBeenCalled();
  });

  it('sets isStale on a non-403 fetch failure', async () => {
    loginAsAdmin();
    vi.spyOn(adminHealthClient.adminHealthClient, 'get').mockResolvedValue({
      ok: false, status: 500, error: 'oops',
    });
    renderHook(() => useAdminHealth(15000));
    await act(async () => { await Promise.resolve(); });
    expect(useAdminHealthStore.getState().isStale).toBe(true);
  });

  it('silently ignores a 403 (non-admin perimeter response)', async () => {
    loginAsAdmin();
    vi.spyOn(adminHealthClient.adminHealthClient, 'get').mockResolvedValue({
      ok: false, status: 403, error: 'forbidden',
    });
    renderHook(() => useAdminHealth(15000));
    await act(async () => { await Promise.resolve(); });
    expect(useAdminHealthStore.getState().isStale).toBe(false);
  });
});

describe('useAdminHealth unmount safety', () => {
  beforeEach(() => {
    useAdminHealthStore.getState().clear();
    useAuthStore.getState().clear();
    loginAsAdmin();
  });
  afterEach(() => { vi.restoreAllMocks(); });

  it('ignores a response that resolves after unmount', async () => {
    let resolveFetch!: (v: Awaited<ReturnType<typeof adminHealthClient.adminHealthClient.get>>) => void;
    vi.spyOn(adminHealthClient.adminHealthClient, 'get').mockReturnValue(
      new Promise((res) => { resolveFetch = res; }),
    );
    const { unmount } = renderHook(() => useAdminHealth(15000));
    unmount();
    await act(async () => {
      resolveFetch(HEALTH_OK);
      await Promise.resolve();
    });
    expect(useAdminHealthStore.getState().data).toBeNull();
    expect(useAdminHealthStore.getState().isLoading).toBe(false);
  });
});

describe('useAdminHealth reload (cancellable retry)', () => {
  beforeEach(() => {
    useAdminHealthStore.getState().clear();
    useAuthStore.getState().clear();
    loginAsAdmin();
  });
  afterEach(() => { vi.restoreAllMocks(); });

  it('reload() re-fires the fetch', async () => {
    const spy = vi.spyOn(adminHealthClient.adminHealthClient, 'get').mockResolvedValue(HEALTH_OK);
    const { result } = renderHook(() => useAdminHealth(15000));
    await act(async () => { await Promise.resolve(); });
    expect(spy).toHaveBeenCalledTimes(1);

    act(() => { result.current.reload(); });
    await act(async () => { await Promise.resolve(); });
    expect(spy).toHaveBeenCalledTimes(2);
  });

  it('reload() after unmount is a no-op (does not throw, does not fetch)', async () => {
    const spy = vi.spyOn(adminHealthClient.adminHealthClient, 'get').mockResolvedValue(HEALTH_OK);
    const { result, unmount } = renderHook(() => useAdminHealth(15000));
    await act(async () => { await Promise.resolve(); });
    unmount();
    const callsBefore = spy.mock.calls.length;
    expect(() => result.current.reload()).not.toThrow();
    await act(async () => { await Promise.resolve(); });
    expect(spy.mock.calls.length).toBe(callsBefore);
  });
});
