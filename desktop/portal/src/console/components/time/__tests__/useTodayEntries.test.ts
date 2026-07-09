import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useTodayEntries } from '../useTodayEntries';
import * as presence from '../../../api/presenceClient';

describe('useTodayEntries', () => {
  beforeEach(() => { vi.useFakeTimers(); });
  afterEach(() => { vi.useRealTimers(); vi.restoreAllMocks(); });

  it('fetches today entries on mount and exposes them', async () => {
    const rows = [{ id: 'a', startedAt: '2026-05-25T09:00:00Z', endedAt: '2026-05-25T10:00:00Z', source: 'web', entryType: 'regular', jobId: null, jobTitle: 'Reno', taskId: null, taskTitle: null, clockOutReason: 'lunch' }];
    const spy = vi.spyOn(presence.presenceClient, 'getTodayShifts').mockResolvedValue({ ok: true, shifts: rows });
    const { result } = renderHook(() => useTodayEntries());
    await act(async () => { await Promise.resolve(); });
    expect(spy).toHaveBeenCalled();
    expect(result.current.entries.map((e) => e.id)).toEqual(['a']);
    expect(result.current.loading).toBe(false);
    expect(result.current.error).toBe(false);
  });

  it('is loading before the first fetch resolves', () => {
    vi.spyOn(presence.presenceClient, 'getTodayShifts').mockReturnValue(new Promise(() => {}));
    const { result } = renderHook(() => useTodayEntries());
    expect(result.current.loading).toBe(true);
  });

  it('sets error=true on fetch failure', async () => {
    vi.spyOn(presence.presenceClient, 'getTodayShifts').mockResolvedValue({ ok: false, status: 500, error: 'boom' });
    const { result } = renderHook(() => useTodayEntries());
    await act(async () => { await Promise.resolve(); });
    expect(result.current.error).toBe(true);
    expect(result.current.loading).toBe(false);
  });

  it('reload() re-fires the fetch and can clear a prior error', async () => {
    const spy = vi.spyOn(presence.presenceClient, 'getTodayShifts')
      .mockResolvedValueOnce({ ok: false, status: 500, error: 'boom' })
      .mockResolvedValueOnce({ ok: true, shifts: [] });
    const { result } = renderHook(() => useTodayEntries());
    await act(async () => { await Promise.resolve(); });
    expect(result.current.error).toBe(true);

    act(() => { result.current.reload(); });
    await act(async () => { await Promise.resolve(); });
    expect(spy).toHaveBeenCalledTimes(2);
    expect(result.current.error).toBe(false);
  });

  it('ignores a response that resolves after unmount', async () => {
    let resolveFetch!: (v: Awaited<ReturnType<typeof presence.presenceClient.getTodayShifts>>) => void;
    vi.spyOn(presence.presenceClient, 'getTodayShifts').mockReturnValue(
      new Promise((res) => { resolveFetch = res; }),
    );
    const { result, unmount } = renderHook(() => useTodayEntries());
    unmount();
    await act(async () => {
      resolveFetch({ ok: true, shifts: [{ id: 'late', startedAt: null, endedAt: null, source: 'web', entryType: 'regular', jobId: null, jobTitle: null, taskId: null, taskTitle: null, clockOutReason: null }] });
      await Promise.resolve();
    });
    expect(result.current.entries).toHaveLength(0);
  });
});
