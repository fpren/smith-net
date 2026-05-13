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
