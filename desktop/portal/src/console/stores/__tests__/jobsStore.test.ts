// desktop/portal/src/console/stores/__tests__/jobsStore.test.ts
import { describe, it, expect, beforeEach } from 'vitest';
import { useJobsStore } from '../jobsStore';
import type { Job, CrewAssignment } from '../../api/jobsClient';

const sampleJob: Job = {
  id: 'job-1',
  foremanId: 'f-1',
  clientId: null,
  client: null,
  engagementId: null,
  title: 'First job',
  description: null,
  status: 'planned',
  scheduledAt: null,
  location: null,
  latitude: null,
  longitude: null,
  geocodedAt: null,
  createdAt: '2026-05-11T10:00:00Z',
  updatedAt: '2026-05-11T10:00:00Z',
};

const sampleCrew: CrewAssignment = {
  jobId: 'job-1',
  profileId: 'p-1',
  roleOnJob: 'crew',
  assignedAt: '2026-05-11T10:30:00Z',
};

describe('jobsStore', () => {
  beforeEach(() => {
    useJobsStore.getState().clear();
  });

  it('starts empty', () => {
    const s = useJobsStore.getState();
    expect(s.jobs).toEqual([]);
    expect(s.detailJob).toBeNull();
    expect(s.detailCrew).toEqual([]);
    expect(s.isStale).toBe(false);
  });

  it('setJobs replaces the list and updates lastFetchedAt', () => {
    const before = useJobsStore.getState().lastFetchedAt;
    useJobsStore.getState().setJobs([sampleJob]);
    const s = useJobsStore.getState();
    expect(s.jobs).toHaveLength(1);
    expect(s.lastFetchedAt).not.toBe(before);
    expect(s.lastFetchedAt).toBeGreaterThan(0);
  });

  it('setDetail populates detail slice', () => {
    useJobsStore.getState().setDetail(sampleJob, [sampleCrew]);
    const s = useJobsStore.getState();
    expect(s.detailJob).toEqual(sampleJob);
    expect(s.detailCrew).toEqual([sampleCrew]);
  });

  it('upsertJob inserts when not present', () => {
    useJobsStore.getState().setJobs([]);
    useJobsStore.getState().upsertJob(sampleJob);
    expect(useJobsStore.getState().jobs).toEqual([sampleJob]);
  });

  it('upsertJob updates in place when present', () => {
    useJobsStore.getState().setJobs([sampleJob]);
    const updated: Job = { ...sampleJob, title: 'Renamed', status: 'in_progress' };
    useJobsStore.getState().upsertJob(updated);
    const s = useJobsStore.getState();
    expect(s.jobs).toHaveLength(1);
    expect(s.jobs[0].title).toBe('Renamed');
    expect(s.jobs[0].status).toBe('in_progress');
  });

  it('upsertJob updates detailJob too when ids match', () => {
    useJobsStore.getState().setDetail(sampleJob, []);
    const updated: Job = { ...sampleJob, status: 'complete' };
    useJobsStore.getState().upsertJob(updated);
    expect(useJobsStore.getState().detailJob?.status).toBe('complete');
  });

  it('markStale toggles the flag', () => {
    useJobsStore.getState().markStale(true);
    expect(useJobsStore.getState().isStale).toBe(true);
    useJobsStore.getState().markStale(false);
    expect(useJobsStore.getState().isStale).toBe(false);
  });

  it('clear resets everything', () => {
    useJobsStore.getState().setJobs([sampleJob]);
    useJobsStore.getState().setDetail(sampleJob, [sampleCrew]);
    useJobsStore.getState().markStale(true);
    useJobsStore.getState().clear();
    const s = useJobsStore.getState();
    expect(s.jobs).toEqual([]);
    expect(s.detailJob).toBeNull();
    expect(s.detailCrew).toEqual([]);
    expect(s.isStale).toBe(false);
  });
});
