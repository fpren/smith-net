/**
 * Per-job task routes — happy paths + cross-foreman 403 + worker tier gate.
 * Mirrors jobs-routes.test.ts in shape; relies on the same migration set.
 */

import express from 'express';
import request from 'supertest';
import cookieParser from 'cookie-parser';
import { authRouter } from '../authRoutes';
import { jobsRouter } from '../jobsRoutes';
import { tasksRouter } from '../tasksRoutes';
import { authenticateToken, generateTokens, UserRole } from '../auth';
import { createUserAndProfile } from '../jobsService';
import { pg, isPgEnabled } from '../db';

const describeDb = isPgEnabled() ? describe : describe.skip;

function buildApp() {
  const app = express();
  app.use(express.json());
  app.use(cookieParser());
  app.use('/api/auth', authRouter);
  app.use('/api/jobs', jobsRouter);
  // tasksRouter is normally mounted under /api by the apiRouter (which
  // applies authenticateToken). Mirror that here.
  app.use('/api', authenticateToken, tasksRouter);
  return app;
}

async function createForemanAndLogin(suffix: string) {
  const email = `foreman-tasks-${suffix}-${Date.now()}-${Math.random().toString(36).slice(2, 7)}@example.com`;
  const user = await createUserAndProfile({
    email,
    password: 'password123',
    displayName: `Foreman ${suffix}`,
    role: UserRole.FOREMAN,
  });
  const { accessToken } = await generateTokens(user);
  return { id: user.id, token: accessToken };
}

async function createSoloAndLogin(suffix: string) {
  const email = `solo-tasks-${suffix}-${Date.now()}-${Math.random().toString(36).slice(2, 7)}@example.com`;
  const user = await createUserAndProfile({
    email,
    password: 'password123',
    displayName: `Solo ${suffix}`,
    role: UserRole.SOLO,
  });
  const { accessToken } = await generateTokens(user);
  return { id: user.id, token: accessToken };
}

afterEach(async () => {
  if (!isPgEnabled() || !pg) return;
  await pg.query(`DELETE FROM tasks`);
  await pg.query(`DELETE FROM job_crew`);
  await pg.query(`DELETE FROM background_jobs WHERE kind = 'geocode'`);
  await pg.query(`DELETE FROM jobs`);
  await pg.query(`DELETE FROM profiles WHERE email LIKE 'foreman-tasks-%' OR email LIKE 'solo-tasks-%'`);
  await pg.query(`DELETE FROM users WHERE email LIKE 'foreman-tasks-%' OR email LIKE 'solo-tasks-%'`);
});

afterAll(async () => { await pg?.end(); });

describeDb('per-job tasks routes', () => {
  const app = buildApp();

  it('POST /api/tasks creates a task on the foreman\'s own job', async () => {
    const f = await createForemanAndLogin('post-ok');
    const jobRes = await request(app)
      .post('/api/jobs')
      .set('Authorization', `Bearer ${f.token}`)
      .send({ title: 'Roofing job' });
    expect(jobRes.status).toBe(201);
    const jobId = jobRes.body.job.id;

    const taskRes = await request(app)
      .post('/api/tasks')
      .set('Authorization', `Bearer ${f.token}`)
      .send({ jobId, title: 'Order materials' });
    expect(taskRes.status).toBe(201);
    expect(taskRes.body.task.title).toBe('Order materials');
    expect(taskRes.body.task.status).toBe('pending');
    expect(taskRes.body.task.jobId).toBe(jobId);
  });

  it('GET /api/jobs/:id/tasks returns tasks ordered by sort_order', async () => {
    const f = await createForemanAndLogin('list-order');
    const jobRes = await request(app).post('/api/jobs').set('Authorization', `Bearer ${f.token}`).send({ title: 'J' });
    const jobId = jobRes.body.job.id;

    await request(app).post('/api/tasks').set('Authorization', `Bearer ${f.token}`).send({ jobId, title: 'First' });
    await request(app).post('/api/tasks').set('Authorization', `Bearer ${f.token}`).send({ jobId, title: 'Second' });
    await request(app).post('/api/tasks').set('Authorization', `Bearer ${f.token}`).send({ jobId, title: 'Third' });

    const list = await request(app)
      .get(`/api/jobs/${jobId}/tasks`)
      .set('Authorization', `Bearer ${f.token}`);
    expect(list.status).toBe(200);
    expect(list.body.tasks.map((t: any) => t.title)).toEqual(['First', 'Second', 'Third']);
    expect(list.body.tasks.map((t: any) => t.sortOrder)).toEqual([0, 1, 2]);
  });

  it('PATCH /api/tasks/:id toggling status=done stamps completedAt; reverting clears it', async () => {
    const f = await createForemanAndLogin('patch-status');
    const jobRes = await request(app).post('/api/jobs').set('Authorization', `Bearer ${f.token}`).send({ title: 'J' });
    const jobId = jobRes.body.job.id;
    const created = await request(app).post('/api/tasks').set('Authorization', `Bearer ${f.token}`).send({ jobId, title: 'X' });
    const taskId = created.body.task.id;

    const done = await request(app)
      .patch(`/api/tasks/${taskId}`)
      .set('Authorization', `Bearer ${f.token}`)
      .send({ status: 'done' });
    expect(done.status).toBe(200);
    expect(done.body.task.status).toBe('done');
    expect(done.body.task.completedAt).toBeTruthy();

    const back = await request(app)
      .patch(`/api/tasks/${taskId}`)
      .set('Authorization', `Bearer ${f.token}`)
      .send({ status: 'pending' });
    expect(back.body.task.status).toBe('pending');
    expect(back.body.task.completedAt).toBeNull();
  });

  it('DELETE /api/tasks/:id removes the task', async () => {
    const f = await createForemanAndLogin('del');
    const jobRes = await request(app).post('/api/jobs').set('Authorization', `Bearer ${f.token}`).send({ title: 'J' });
    const jobId = jobRes.body.job.id;
    const created = await request(app).post('/api/tasks').set('Authorization', `Bearer ${f.token}`).send({ jobId, title: 'X' });
    const taskId = created.body.task.id;

    const del = await request(app).delete(`/api/tasks/${taskId}`).set('Authorization', `Bearer ${f.token}`);
    expect(del.status).toBe(204);

    const list = await request(app).get(`/api/jobs/${jobId}/tasks`).set('Authorization', `Bearer ${f.token}`);
    expect(list.body.tasks).toEqual([]);
  });

  it('Foreman B cannot read Foreman A\'s tasks (cross-foreman 403)', async () => {
    const a = await createForemanAndLogin('xfA');
    const b = await createForemanAndLogin('xfB');
    const jobRes = await request(app).post('/api/jobs').set('Authorization', `Bearer ${a.token}`).send({ title: 'A-job' });
    const jobId = jobRes.body.job.id;
    await request(app).post('/api/tasks').set('Authorization', `Bearer ${a.token}`).send({ jobId, title: 'A-task' });

    const cross = await request(app).get(`/api/jobs/${jobId}/tasks`).set('Authorization', `Bearer ${b.token}`);
    expect(cross.status).toBe(403);
  });

  it('Foreman B cannot create a task on Foreman A\'s job (403)', async () => {
    const a = await createForemanAndLogin('xpostA');
    const b = await createForemanAndLogin('xpostB');
    const jobRes = await request(app).post('/api/jobs').set('Authorization', `Bearer ${a.token}`).send({ title: 'A-job' });
    const jobId = jobRes.body.job.id;

    const res = await request(app)
      .post('/api/tasks')
      .set('Authorization', `Bearer ${b.token}`)
      .send({ jobId, title: 'B sneaks in' });
    expect(res.status).toBe(403);
  });

  it('Foreman B cannot mutate Foreman A\'s task (PATCH 403, DELETE 403)', async () => {
    const a = await createForemanAndLogin('xmutA');
    const b = await createForemanAndLogin('xmutB');
    const jobRes = await request(app).post('/api/jobs').set('Authorization', `Bearer ${a.token}`).send({ title: 'J' });
    const jobId = jobRes.body.job.id;
    const created = await request(app).post('/api/tasks').set('Authorization', `Bearer ${a.token}`).send({ jobId, title: 'X' });
    const taskId = created.body.task.id;

    const p = await request(app).patch(`/api/tasks/${taskId}`).set('Authorization', `Bearer ${b.token}`).send({ status: 'done' });
    expect(p.status).toBe(403);
    const d = await request(app).delete(`/api/tasks/${taskId}`).set('Authorization', `Bearer ${b.token}`);
    expect(d.status).toBe(403);
  });

  it('Solo worker is blocked from /api/jobs/:id/tasks by requireConsoleTier (403)', async () => {
    const f = await createForemanAndLogin('tier-f');
    const jobRes = await request(app).post('/api/jobs').set('Authorization', `Bearer ${f.token}`).send({ title: 'J' });
    const jobId = jobRes.body.job.id;
    const solo = await createSoloAndLogin('tier-solo');

    const res = await request(app).get(`/api/jobs/${jobId}/tasks`).set('Authorization', `Bearer ${solo.token}`);
    expect(res.status).toBe(403);
    expect(res.body.code).toBe('tier_required');
  });

  it('POST /api/tasks with an unknown jobId returns 404', async () => {
    const f = await createForemanAndLogin('not-found');
    const res = await request(app)
      .post('/api/tasks')
      .set('Authorization', `Bearer ${f.token}`)
      .send({ jobId: '00000000-0000-0000-0000-000000000000', title: 'X' });
    expect(res.status).toBe(404);
  });
});
