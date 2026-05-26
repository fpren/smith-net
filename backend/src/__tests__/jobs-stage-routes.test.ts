// backend/src/__tests__/jobs-stage-routes.test.ts
import express from 'express';
import request from 'supertest';
import cookieParser from 'cookie-parser';
import { authRouter } from '../authRoutes';
import { jobsRouter } from '../jobsRoutes';
import { generateTokens, UserRole } from '../auth';
import { createUserAndProfile } from '../jobsService';
import * as jobsService from '../jobsService';
import { pg, isPgEnabled } from '../db';

const describeDb = isPgEnabled() ? describe : describe.skip;

function buildApp() {
  const app = express();
  app.use(express.json());
  app.use(cookieParser());
  app.use('/api/auth', authRouter);
  app.use('/api/jobs', jobsRouter);
  return app;
}

async function foreman(suffix: string): Promise<{ id: string; token: string }> {
  const user = await createUserAndProfile({
    email: `foreman-stage-${suffix}-${Date.now()}@example.com`,
    password: 'password123',
    displayName: `Foreman ${suffix}`,
    role: UserRole.FOREMAN,
  });
  const { accessToken } = await generateTokens(user);
  return { id: user.id, token: accessToken };
}

async function createJobAt(token: string, stage: jobsService.JobStage): Promise<string> {
  const res = await request(buildApp()).post('/api/jobs')
    .set('Authorization', `Bearer ${token}`)
    .send({ title: `job-${stage}` });
  const id = res.body.job.id;
  // Walk the job forward to the target stage using the route under test.
  // For 'lead' (default) nothing to do.
  if (stage === 'lead') return id;
  const path: jobsService.JobStage[] =
    ['lead','proposal','approved','in_progress','review','invoice','closed'];
  const targetIdx = path.indexOf(stage);
  for (let i = 1; i <= targetIdx; i++) {
    await request(buildApp()).patch(`/api/jobs/${id}/stage`)
      .set('Authorization', `Bearer ${token}`)
      .send({ stage: path[i] });
  }
  return id;
}

describeDb('jobs stage routes', () => {
  const app = buildApp();

  afterEach(async () => {
    if (!isPgEnabled() || !pg) return;
    await pg.query(`DELETE FROM jobs WHERE foreman_id IN (SELECT id FROM profiles WHERE email LIKE 'foreman-stage-%')`);
    await pg.query(`DELETE FROM profiles WHERE email LIKE 'foreman-stage-%'`);
    await pg.query(`DELETE FROM users WHERE email LIKE 'foreman-stage-%'`);
  });
  afterAll(async () => { await pg?.end(); });

  it('401 without auth', async () => {
    const res = await request(app).patch('/api/jobs/00000000-0000-0000-0000-000000000000/stage')
      .send({ stage: 'proposal' });
    expect(res.status).toBe(401);
  });

  it('403 cross-foreman', async () => {
    const a = await foreman('iso-a');
    const b = await foreman('iso-b');
    const id = await createJobAt(a.token, 'lead');
    const res = await request(app).patch(`/api/jobs/${id}/stage`)
      .set('Authorization', `Bearer ${b.token}`).send({ stage: 'proposal' });
    expect(res.status).toBe(403);
  });

  it('rejects unknown stage value (zod strict)', async () => {
    const f = await foreman('zod');
    const id = await createJobAt(f.token, 'lead');
    const res = await request(app).patch(`/api/jobs/${id}/stage`)
      .set('Authorization', `Bearer ${f.token}`).send({ stage: 'bogus' });
    expect(res.status).toBe(400);
    expect(res.body.code).toBe('validation');
  });

  it('valid forward transitions (full spine)', async () => {
    const f = await foreman('fwd');
    const id = await createJobAt(f.token, 'lead');
    const path: jobsService.JobStage[] =
      ['proposal','approved','in_progress','review','invoice','closed'];
    for (const stage of path) {
      const res = await request(app).patch(`/api/jobs/${id}/stage`)
        .set('Authorization', `Bearer ${f.token}`).send({ stage });
      expect(res.status).toBe(200);
      expect(res.body.job.stage).toBe(stage);
    }
  });

  it('valid reverse: proposal -> lead', async () => {
    const f = await foreman('rev1');
    const id = await createJobAt(f.token, 'proposal');
    const res = await request(app).patch(`/api/jobs/${id}/stage`)
      .set('Authorization', `Bearer ${f.token}`).send({ stage: 'lead' });
    expect(res.status).toBe(200);
    expect(res.body.job.stage).toBe('lead');
  });

  it('valid reverse: invoice -> review', async () => {
    const f = await foreman('rev2');
    const id = await createJobAt(f.token, 'invoice');
    const res = await request(app).patch(`/api/jobs/${id}/stage`)
      .set('Authorization', `Bearer ${f.token}`).send({ stage: 'review' });
    expect(res.status).toBe(200);
    expect(res.body.job.stage).toBe('review');
  });

  it('valid reverse: closed -> invoice', async () => {
    const f = await foreman('rev3');
    const id = await createJobAt(f.token, 'closed');
    const res = await request(app).patch(`/api/jobs/${id}/stage`)
      .set('Authorization', `Bearer ${f.token}`).send({ stage: 'invoice' });
    expect(res.status).toBe(200);
    expect(res.body.job.stage).toBe('invoice');
  });

  it('refuses invalid: lead -> in_progress', async () => {
    const f = await foreman('inv1');
    const id = await createJobAt(f.token, 'lead');
    const res = await request(app).patch(`/api/jobs/${id}/stage`)
      .set('Authorization', `Bearer ${f.token}`).send({ stage: 'in_progress' });
    expect(res.status).toBe(400);
    expect(res.body.code).toBe('invalid_stage_transition');
    expect(res.body.from).toBe('lead');
    expect(res.body.to).toBe('in_progress');
  });

  it('refuses invalid: closed -> lead', async () => {
    const f = await foreman('inv2');
    const id = await createJobAt(f.token, 'closed');
    const res = await request(app).patch(`/api/jobs/${id}/stage`)
      .set('Authorization', `Bearer ${f.token}`).send({ stage: 'lead' });
    expect(res.status).toBe(400);
    expect(res.body.code).toBe('invalid_stage_transition');
  });

  it('self-loop returns 200 (idempotent)', async () => {
    const f = await foreman('self');
    const id = await createJobAt(f.token, 'lead');
    const res = await request(app).patch(`/api/jobs/${id}/stage`)
      .set('Authorization', `Bearer ${f.token}`).send({ stage: 'lead' });
    expect(res.status).toBe(200);
    expect(res.body.job.stage).toBe('lead');
  });
});
