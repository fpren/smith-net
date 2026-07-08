import { describe, it, expect, beforeEach, vi, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useCrewRoster } from '../useCrewRoster';
import { useCrewStore } from '../../stores/crewStore';
import * as crewClient from '../../api/crewClient';

describe('useCrewRoster', () => {
  beforeEach(() => {
    useCrewStore.getState().clear();
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('fires an immediate fetch on mount', async () => {
    const spy = vi.spyOn(crewClient.crewClient, 'getRoster').mockResolvedValue({ ok: true, crew: [] });
    renderHook(() => useCrewRoster(15000));
    await act(async () => { await Promise.resolve(); });
    expect(spy).toHaveBeenCalledTimes(1);
  });

  it('fires another fetch after the interval', async () => {
    const spy = vi.spyOn(crewClient.crewClient, 'getRoster').mockResolvedValue({ ok: true, crew: [] });
    renderHook(() => useCrewRoster(15000));
    await act(async () => { await Promise.resolve(); });
    await act(async () => { vi.advanceTimersByTime(15001); await Promise.resolve(); });
    expect(spy).toHaveBeenCalledTimes(2);
  });

  it('sets isStale on fetch failure', async () => {
    vi.spyOn(crewClient.crewClient, 'getRoster').mockResolvedValue({ ok: false, status: 500, error: 'oops' });
    renderHook(() => useCrewRoster(15000));
    await act(async () => { await Promise.resolve(); });
    expect(useCrewStore.getState().isStale).toBe(true);
  });

  it('cleans up interval on unmount', async () => {
    const spy = vi.spyOn(crewClient.crewClient, 'getRoster').mockResolvedValue({ ok: true, crew: [] });
    const { unmount } = renderHook(() => useCrewRoster(15000));
    await act(async () => { await Promise.resolve(); });
    unmount();
    await act(async () => { vi.advanceTimersByTime(60000); await Promise.resolve(); });
    expect(spy).toHaveBeenCalledTimes(1);
  });
});

describe('useCrewRoster unmount safety', () => {
  beforeEach(() => { useCrewStore.getState().clear(); });
  afterEach(() => { vi.restoreAllMocks(); });

  it('ignores a response that resolves after unmount', async () => {
    let resolveFetch!: (v: Awaited<ReturnType<typeof crewClient.crewClient.getRoster>>) => void;
    vi.spyOn(crewClient.crewClient, 'getRoster').mockReturnValue(
      new Promise((res) => { resolveFetch = res; }),
    );
    const { unmount } = renderHook(() => useCrewRoster(15000));
    unmount();
    await act(async () => {
      resolveFetch({
        ok: true,
        crew: [{ id: 'late', email: 'late@x.com', displayName: 'Late', role: 'team', activeJob: null }],
      });
      await Promise.resolve();
    });
    expect(useCrewStore.getState().roster).toHaveLength(0);
    expect(useCrewStore.getState().isLoadingRoster).toBe(false);
  });
});

describe('useCrewRoster reload (cancellable retry)', () => {
  beforeEach(() => { useCrewStore.getState().clear(); });
  afterEach(() => { vi.restoreAllMocks(); });

  it('reload() re-fires the fetch', async () => {
    const spy = vi.spyOn(crewClient.crewClient, 'getRoster').mockResolvedValue({ ok: true, crew: [] });
    const { result } = renderHook(() => useCrewRoster(15000));
    await act(async () => { await Promise.resolve(); });
    expect(spy).toHaveBeenCalledTimes(1);

    act(() => { result.current.reload(); });
    await act(async () => { await Promise.resolve(); });
    expect(spy).toHaveBeenCalledTimes(2);
  });

  it('reload() after unmount is a no-op (does not throw, does not fetch)', async () => {
    const spy = vi.spyOn(crewClient.crewClient, 'getRoster').mockResolvedValue({ ok: true, crew: [] });
    const { result, unmount } = renderHook(() => useCrewRoster(15000));
    await act(async () => { await Promise.resolve(); });
    unmount();
    const callsBefore = spy.mock.calls.length;
    expect(() => result.current.reload()).not.toThrow();
    await act(async () => { await Promise.resolve(); });
    expect(spy.mock.calls.length).toBe(callsBefore);
  });
});
