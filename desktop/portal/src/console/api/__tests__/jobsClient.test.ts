import { describe, it, expect } from 'vitest';
import { jobsClient } from '../jobsClient';

describe('jobsClient', () => {
  it('list returns jobs', async () => {
    const result = await jobsClient.list();
    expect(result.ok).toBe(true);
    if (result.ok) {
      expect(result.jobs).toHaveLength(1);
      expect(result.jobs[0].title).toBe('Test Job');
    }
  });

  it('getById returns job + crew', async () => {
    const result = await jobsClient.getById('abc');
    expect(result.ok).toBe(true);
    if (result.ok) {
      expect(result.job.id).toBe('abc');
      expect(Array.isArray(result.crew)).toBe(true);
    }
  });

  it('create returns the new job on 201', async () => {
    const result = await jobsClient.create({ title: 'Brand new', location: 'X' });
    expect(result.ok).toBe(true);
    if (result.ok) {
      expect(result.job.id).toBe('new-job-id');
      expect(result.job.title).toBe('Brand new');
      expect(result.job.status).toBe('planned');
    }
  });

  it('update patches a job', async () => {
    const result = await jobsClient.update('abc', { title: 'Renamed' });
    expect(result.ok).toBe(true);
    if (result.ok) {
      expect(result.job.title).toBe('Renamed');
    }
  });

  it('changeStatus updates status', async () => {
    const result = await jobsClient.changeStatus('abc', 'in_progress');
    expect(result.ok).toBe(true);
    if (result.ok) {
      expect(result.job.status).toBe('in_progress');
    }
  });

  it('assignCrew returns the assignment on 201', async () => {
    const result = await jobsClient.assignCrew('abc', 'profile-x', 'lead');
    expect(result.ok).toBe(true);
    if (result.ok) {
      expect(result.assignment.profileId).toBe('profile-x');
      expect(result.assignment.roleOnJob).toBe('lead');
    }
  });

  it('unassignCrew returns ok on 204', async () => {
    const result = await jobsClient.unassignCrew('abc', 'profile-x');
    expect(result.ok).toBe(true);
  });
});
