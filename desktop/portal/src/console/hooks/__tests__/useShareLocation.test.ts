import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useShareLocation } from '../useShareLocation';
import { useShareLocationStore } from '../../stores/shareLocationStore';

const mockGeo = (watchImpl: any) => {
  const watchPosition = vi.fn(watchImpl);
  const clearWatch = vi.fn();
  // Replace just the geolocation property; keep the rest of navigator intact.
  Object.defineProperty(navigator, 'geolocation', {
    value: { watchPosition, clearWatch },
    configurable: true,
    writable: true,
  });
  return { watchPosition, clearWatch };
};

describe('useShareLocation', () => {
  beforeEach(() => {
    useShareLocationStore.getState().reset();
    vi.useFakeTimers();
  });
  afterEach(() => { vi.useRealTimers(); vi.restoreAllMocks(); });

  it('start() opens shift then begins watchPosition', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: true, status: 200,
      json: async () => ({ shift: { id: 'shift-1', userId: 'u-1', startedAt: 't', endedAt: null, source: 'web' } }),
    }));
    const geo = mockGeo(() => 42);

    const { result } = renderHook(() => useShareLocation());
    await act(async () => { await result.current.start(); });

    expect(useShareLocationStore.getState().isSharing).toBe(true);
    expect(useShareLocationStore.getState().shiftId).toBe('shift-1');
    expect(geo.watchPosition).toHaveBeenCalled();
  });

  it('start() surfaces error when shift start fails', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue({
      ok: false, status: 401, json: async () => ({ error: 'unauthenticated' }),
    }));
    mockGeo(() => 42);

    const { result } = renderHook(() => useShareLocation());
    await act(async () => { await result.current.start(); });

    expect(useShareLocationStore.getState().isSharing).toBe(false);
    expect(useShareLocationStore.getState().error).toMatch(/unauthenticated|401/i);
  });

  it('stop() calls endShift and clears watch', async () => {
    const geo = mockGeo(() => 42);
    vi.stubGlobal('fetch', vi.fn().mockImplementation(async (url: string) => {
      if (url.endsWith('/start')) return { ok: true, status: 200, json: async () => ({ shift: { id: 's-1' } }) };
      if (url.endsWith('/end')) return { ok: true, status: 200, json: async () => ({ shift: { id: 's-1' } }) };
      return { ok: true, status: 200, json: async () => ({}) };
    }));

    const { result } = renderHook(() => useShareLocation());
    await act(async () => { await result.current.start(); });
    await act(async () => { await result.current.stop(); });

    expect(useShareLocationStore.getState().isSharing).toBe(false);
    expect(geo.clearWatch).toHaveBeenCalledWith(42);
  });
});
