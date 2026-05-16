import { describe, it, expect, beforeEach } from 'vitest';
import { useShareLocationStore } from '../shareLocationStore';

describe('shareLocationStore', () => {
  beforeEach(() => useShareLocationStore.getState().reset());

  it('setSharing(true, shiftId) transitions to sharing', () => {
    useShareLocationStore.getState().setSharing(true, 'shift-1');
    const s = useShareLocationStore.getState();
    expect(s.isSharing).toBe(true);
    expect(s.shiftId).toBe('shift-1');
  });

  it('setSharing(false) clears shiftId', () => {
    useShareLocationStore.getState().setSharing(true, 'shift-1');
    useShareLocationStore.getState().setSharing(false);
    expect(useShareLocationStore.getState().isSharing).toBe(false);
    expect(useShareLocationStore.getState().shiftId).toBeNull();
  });

  it('setError sets error and isTransitioning=false', () => {
    useShareLocationStore.getState().setTransitioning(true);
    useShareLocationStore.getState().setError('Permission denied');
    expect(useShareLocationStore.getState().error).toBe('Permission denied');
    expect(useShareLocationStore.getState().isTransitioning).toBe(false);
  });
});
