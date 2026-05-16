import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { renderHook, waitFor } from '@testing-library/react';
import { useCrewPositionsPolling } from '../useCrewPositionsPolling';
import { useCrewPositionsStore } from '../../stores/crewPositionsStore';

describe('useCrewPositionsPolling', () => {
  beforeEach(() => { useCrewPositionsStore.getState().clear(); });
  afterEach(() => { vi.restoreAllMocks(); });

  it('fetches once and writes to store on mount', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true,
      json: async () => ({
        positions: [
          { userId: 'u-1', displayName: 'Alice', latitude: 40.7, longitude: -74,
            accuracyM: 5, recordedAt: 't', source: 'web', batteryPct: 80 },
        ],
      }),
    }));
    renderHook(() => useCrewPositionsPolling(60_000));
    await waitFor(() => expect(useCrewPositionsStore.getState().positions).toHaveLength(1));
  });

  it('marks stale on fetch failure (non-403)', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: false, status: 500, json: async () => ({ error: 'boom' }),
    }));
    renderHook(() => useCrewPositionsPolling(60_000));
    await waitFor(() => expect(useCrewPositionsStore.getState().isStale).toBe(true));
  });

  it('silently ignores 403 (non-foreman) — does NOT mark stale', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: false, status: 403, json: async () => ({ error: 'forbidden' }),
    }));
    renderHook(() => useCrewPositionsPolling(60_000));
    // Give the effect time to fire and complete the fetch.
    await new Promise((r) => setTimeout(r, 50));
    expect(useCrewPositionsStore.getState().isStale).toBe(false);
    expect(useCrewPositionsStore.getState().positions).toEqual([]);
  });
});
