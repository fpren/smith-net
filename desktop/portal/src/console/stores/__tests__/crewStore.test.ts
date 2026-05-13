import { describe, it, expect, beforeEach } from 'vitest';
import { useCrewStore } from '../crewStore';
import type { CrewEntry } from '../../api/crewClient';

const sample: CrewEntry[] = [
  { id: 'a', email: 'a@x.com', displayName: 'Alice', role: 'team',
    activeJob: { id: 'j1', title: 'X', status: 'in_progress' } },
  { id: 'b', email: 'b@x.com', displayName: 'Bob',   role: 'lead', activeJob: null },
];

describe('crewStore', () => {
  beforeEach(() => useCrewStore.getState().clear());

  it('starts empty', () => {
    expect(useCrewStore.getState().roster).toEqual([]);
    expect(useCrewStore.getState().isStale).toBe(false);
  });

  it('setRoster updates the list and lastFetched', () => {
    const before = useCrewStore.getState().lastFetchedAt;
    useCrewStore.getState().setRoster(sample);
    const s = useCrewStore.getState();
    expect(s.roster).toEqual(sample);
    expect(s.lastFetchedAt).not.toBe(before);
  });

  it('availabilityOf returns "busy" when crew has activeJob', () => {
    useCrewStore.getState().setRoster(sample);
    expect(useCrewStore.getState().availabilityOf('a')).toBe('busy');
  });

  it('availabilityOf returns "free" when no activeJob', () => {
    useCrewStore.getState().setRoster(sample);
    expect(useCrewStore.getState().availabilityOf('b')).toBe('free');
  });

  it('availabilityOf returns "free" for unknown profile id', () => {
    expect(useCrewStore.getState().availabilityOf('nope')).toBe('free');
  });

  it('markStale toggles', () => {
    useCrewStore.getState().markStale(true);
    expect(useCrewStore.getState().isStale).toBe(true);
  });

  it('clear resets everything', () => {
    useCrewStore.getState().setRoster(sample);
    useCrewStore.getState().markStale(true);
    useCrewStore.getState().clear();
    const s = useCrewStore.getState();
    expect(s.roster).toEqual([]);
    expect(s.isStale).toBe(false);
    expect(s.lastFetchedAt).toBeNull();
  });
});
