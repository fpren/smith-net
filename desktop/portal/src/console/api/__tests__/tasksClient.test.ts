import { describe, it, expect } from 'vitest';
import { http, HttpResponse } from 'msw';
import { tasksClient } from '../tasksClient';
import { server } from '../../test/msw-server';

describe('tasksClient', () => {
  it('listForJob returns the tasks array', async () => {
    const r = await tasksClient.listForJob('job-1');
    expect(r.ok).toBe(true);
    if (r.ok) {
      expect(r.tasks).toHaveLength(1);
      expect(r.tasks[0].title).toBe('First task');
      expect(r.tasks[0].status).toBe('pending');
    }
  });

  it('create posts { jobId, title } and returns the task', async () => {
    const r = await tasksClient.create('job-1', 'Order materials');
    expect(r.ok).toBe(true);
    if (r.ok) {
      expect(r.task.id).toBe('t-new');
      expect(r.task.title).toBe('Order materials');
      expect(r.task.jobId).toBe('job-1');
    }
  });

  it('update toggles status to done and stamps completedAt', async () => {
    const r = await tasksClient.update('t-1', { status: 'done' });
    expect(r.ok).toBe(true);
    if (r.ok) {
      expect(r.task.status).toBe('done');
      expect(r.task.completedAt).toBeTruthy();
    }
  });

  it('delete returns ok on 204', async () => {
    const r = await tasksClient.delete('t-1');
    expect(r.ok).toBe(true);
  });

  it('surfaces error status on non-2xx', async () => {
    server.use(
      http.get('/api/jobs/:jobId/tasks', () =>
        HttpResponse.json({ error: 'boom' }, { status: 500 }),
      ),
    );
    const r = await tasksClient.listForJob('job-1');
    expect(r.ok).toBe(false);
    if (!r.ok) {
      expect(r.status).toBe(500);
      expect(r.error).toBe('boom');
    }
  });
});
