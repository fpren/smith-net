import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useTodayEntries } from '../useTodayEntries';
import * as presence from '../../../api/presenceClient';

describe('useTodayEntries', () => {
  beforeEach(() => { vi.useFakeTimers(); });
  afterEach(() => { vi.useRealTimers(); vi.restoreAllMocks(); });

  it('fetches today entries on mount and exposes them', async () => {
    const rows = [{ id: 'a', startedAt: '2026-05-25T09:00:00Z', endedAt: '2026-05-25T10:00:00Z', source: 'web', entryType: 'regular', jobId: null, jobTitle: 'Reno', clockOutReason: 'lunch' }];
    const spy = vi.spyOn(presence.presenceClient, 'getTodayShifts').mockResolvedValue({ ok: true, shifts: rows });
    const { result } = renderHook(() => useTodayEntries());
    await act(async () => { await Promise.resolve(); });
    expect(spy).toHaveBeenCalled();
    expect(result.current.map((e) => e.id)).toEqual(['a']);
  });
});
