import express from 'express';
import request from 'supertest';
import cookieParser from 'cookie-parser';
import { authRouter } from '../authRoutes';
import { jobsRouter } from '../jobsRoutes';
import { materialsRouter } from '../materialsRoutes';
import { generateTokens, UserRole, userStore } from '../auth';
import { createUserAndProfile } from '../jobsService';
import { pg, isPgEnabled } from '../db';

const describeDb = isPgEnabled() ? describe : describe.skip;

function buildApp() {
  const app = express();
  app.use(express.json());
  app.use(cookieParser());
  app.use('/api/auth', authRouter);
  app.use('/api/jobs', jobsRouter);
  app.use('/api', materialsRouter);
  return app;
}

async function foreman(suffix: string): Promise<{ id: string; token: string }> {
  const user = await createUserAndProfile({
    email: `foreman-mat-${suffix}-${Date.now()}@example.com`,
    password: 'password123',
    displayName: `Foreman ${suffix}`,
    role: UserRole.FOREMAN,
  });
  const { accessToken } = await generateTokens(user);
  return { id: user.id, token: accessToken };
}

async function makeJob(token: string): Promise<string> {
  const res = await request(buildApp()).post('/api/jobs').set('Authorization', `Bearer ${token}`)
    .send({ title: 'mat-job' });
  return res.body.job.id;
}

describeDb('materials routes', () => {
  const app = buildApp();

  afterEach(async () => {
    if (!isPgEnabled() || !pg) return;
    await pg.query(`DELETE FROM materials WHERE job_id IN (
      SELECT id FROM jobs WHERE foreman_id IN (
        SELECT id FROM profiles WHERE email LIKE 'foreman-mat-%' OR email LIKE 'solo-mat-%'
      )
    )`);
    await pg.query(`DELETE FROM jobs WHERE foreman_id IN (
      SELECT id FROM profiles WHERE email LIKE 'foreman-mat-%' OR email LIKE 'solo-mat-%'
    )`);
    await pg.query(`DELETE FROM profiles WHERE email LIKE 'foreman-mat-%' OR email LIKE 'solo-mat-%'`);
    await pg.query(`DELETE FROM users WHERE email LIKE 'foreman-mat-%' OR email LIKE 'solo-mat-%'`);
  });
  afterAll(async () => { await pg?.end(); });

  it('401 without auth', async () => {
    const res = await request(app).post('/api/materials').send({ jobId: 'x', name: 'y' });
    expect(res.status).toBe(401);
  });

  it('403 tier_required for Solo', async () => {
    const u = await userStore.createUser(`solo-mat-${Date.now()}@example.com`, 'password123', 'Solo', UserRole.SOLO);
    const { accessToken } = await generateTokens(u);
    const res = await request(app).post('/api/materials').set('Authorization', `Bearer ${accessToken}`)
      .send({ jobId: '00000000-0000-0000-0000-000000000000', name: 'x' });
    expect(res.status).toBe(403);
    expect(res.body.code).toBe('tier_required');
  });

  it('creates, lists, updates, toggles, deletes a material', async () => {
    const f = await foreman('crud');
    const jobId = await makeJob(f.token);
    const create = await request(app).post('/api/materials').set('Authorization', `Bearer ${f.token}`)
      .send({ jobId, name: '10/2 Romex', quantity: 50, unit: 'ft', unitCost: 0.85 });
    expect(create.status).toBe(201);
    const id = create.body.material.id;
    expect(create.body.material.name).toBe('10/2 Romex');
    expect(create.body.material.checked).toBe(false);

    const list = await request(app).get(`/api/jobs/${jobId}/materials`).set('Authorization', `Bearer ${f.token}`);
    expect(list.body.materials).toHaveLength(1);

    const toggleOn = await request(app).patch(`/api/materials/${id}`).set('Authorization', `Bearer ${f.token}`)
      .send({ checked: true });
    expect(toggleOn.body.material.checked).toBe(true);
    expect(toggleOn.body.material.checkedAt).not.toBeNull();

    const toggleOff = await request(app).patch(`/api/materials/${id}`).set('Authorization', `Bearer ${f.token}`)
      .send({ checked: false });
    expect(toggleOff.body.material.checked).toBe(false);
    expect(toggleOff.body.material.checkedAt).toBeNull();

    const del = await request(app).delete(`/api/materials/${id}`).set('Authorization', `Bearer ${f.token}`);
    expect(del.status).toBe(204);
    const after = await request(app).get(`/api/jobs/${jobId}/materials`).set('Authorization', `Bearer ${f.token}`);
    expect(after.body.materials).toHaveLength(0);
  });

  it('isolates across foremen (403 not_owner)', async () => {
    const a = await foreman('iso-a');
    const b = await foreman('iso-b');
    const jobId = await makeJob(a.token);
    const create = await request(app).post('/api/materials').set('Authorization', `Bearer ${a.token}`)
      .send({ jobId, name: 'A only' });
    const id = create.body.material.id;
    const res = await request(app).patch(`/api/materials/${id}`).set('Authorization', `Bearer ${b.token}`)
      .send({ name: 'hack' });
    expect(res.status).toBe(403);
    expect(res.body.code).toBe('not_owner');
  });

  it('rejects unknown fields (zod strict)', async () => {
    const f = await foreman('strict');
    const jobId = await makeJob(f.token);
    const res = await request(app).post('/api/materials').set('Authorization', `Bearer ${f.token}`)
      .send({ jobId, name: 'X', bogus: 1 });
    expect(res.status).toBe(400);
    expect(res.body.code).toBe('validation');
  });

  it('rejects negative quantity', async () => {
    const f = await foreman('neg');
    const jobId = await makeJob(f.token);
    const res = await request(app).post('/api/materials').set('Authorization', `Bearer ${f.token}`)
      .send({ jobId, name: 'X', quantity: -1 });
    expect(res.status).toBe(400);
    expect(res.body.code).toBe('validation');
  });

  it('cascades on job delete', async () => {
    const f = await foreman('casc');
    const jobId = await makeJob(f.token);
    await request(app).post('/api/materials').set('Authorization', `Bearer ${f.token}`)
      .send({ jobId, name: 'm1' });
    expect((await pg!.query(`SELECT COUNT(*) FROM materials WHERE job_id = $1`, [jobId])).rows[0].count).toBe('1');
    await pg!.query(`DELETE FROM jobs WHERE id = $1`, [jobId]);
    expect((await pg!.query(`SELECT COUNT(*) FROM materials WHERE job_id = $1`, [jobId])).rows[0].count).toBe('0');
  });
});
