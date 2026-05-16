import { describe, it, expect, beforeEach } from 'vitest';
import { useCrewPositionsStore } from '../crewPositionsStore';

describe('crewPositionsStore', () => {
  beforeEach(() => useCrewPositionsStore.getState().clear());

  it('setPositions replaces list and clears stale', () => {
    useCrewPositionsStore.getState().markStale(true);
    useCrewPositionsStore.getState().setPositions([
      { userId: 'u-1', displayName: 'A', latitude: 1, longitude: 2, accuracyM: null, recordedAt: 't', source: 'web', batteryPct: null },
    ]);
    const s = useCrewPositionsStore.getState();
    expect(s.positions).toHaveLength(1);
    expect(s.isStale).toBe(false);
  });

  it('markLoading and markStale flip flags independently', () => {
    useCrewPositionsStore.getState().markLoading(true);
    expect(useCrewPositionsStore.getState().isLoading).toBe(true);
    useCrewPositionsStore.getState().markStale(true);
    expect(useCrewPositionsStore.getState().isStale).toBe(true);
    expect(useCrewPositionsStore.getState().isLoading).toBe(true);
  });

  it('clear resets all fields', () => {
    useCrewPositionsStore.getState().setPositions([
      { userId: 'u-1', displayName: 'A', latitude: 1, longitude: 2, accuracyM: null, recordedAt: 't', source: 'web', batteryPct: null },
    ]);
    useCrewPositionsStore.getState().markLoading(true);
    useCrewPositionsStore.getState().clear();
    const s = useCrewPositionsStore.getState();
    expect(s.positions).toEqual([]);
    expect(s.isLoading).toBe(false);
    expect(s.isStale).toBe(false);
  });
});
