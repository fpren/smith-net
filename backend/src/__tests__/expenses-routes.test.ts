import express from 'express';
import request from 'supertest';
import cookieParser from 'cookie-parser';
import { authRouter } from '../authRoutes';
import { jobsRouter } from '../jobsRoutes';
import { expensesRouter } from '../expensesRoutes';
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
  app.use('/api', expensesRouter);
  return app;
}

async function foreman(suffix: string): Promise<{ id: string; token: string }> {
  const user = await createUserAndProfile({
    email: `foreman-exp-${suffix}-${Date.now()}@example.com`,
    password: 'password123',
    displayName: `Foreman ${suffix}`,
    role: UserRole.FOREMAN,
  });
  const { accessToken } = await generateTokens(user);
  return { id: user.id, token: accessToken };
}

async function makeJob(token: string): Promise<string> {
  const res = await request(buildApp()).post('/api/jobs').set('Authorization', `Bearer ${token}`)
    .send({ title: 'exp-job' });
  return res.body.job.id;
}

describeDb('expenses routes', () => {
  const app = buildApp();

  afterEach(async () => {
    if (!isPgEnabled() || !pg) return;
    await pg.query(`DELETE FROM job_expenses WHERE job_id IN (
      SELECT id FROM jobs WHERE foreman_id IN (
        SELECT id FROM profiles WHERE email LIKE 'foreman-exp-%' OR email LIKE 'solo-exp-%'
      )
    )`);
    await pg.query(`DELETE FROM jobs WHERE foreman_id IN (
      SELECT id FROM profiles WHERE email LIKE 'foreman-exp-%' OR email LIKE 'solo-exp-%'
    )`);
    await pg.query(`DELETE FROM profiles WHERE email LIKE 'foreman-exp-%' OR email LIKE 'solo-exp-%'`);
    await pg.query(`DELETE FROM users WHERE email LIKE 'foreman-exp-%' OR email LIKE 'solo-exp-%'`);
  });
  afterAll(async () => { await pg?.end(); });

  it('401 without auth', async () => {
    const res = await request(app).post('/api/expenses').send({ jobId: 'x', category: 'fuel', description: 'gas', amount: 0 });
    expect(res.status).toBe(401);
  });

  it('403 tier_required for Solo', async () => {
    const u = await userStore.createUser(`solo-exp-${Date.now()}@example.com`, 'password123', 'Solo', UserRole.SOLO);
    const { accessToken } = await generateTokens(u);
    const res = await request(app).post('/api/expenses').set('Authorization', `Bearer ${accessToken}`)
      .send({ jobId: '00000000-0000-0000-0000-000000000000', category: 'fuel', description: 'x', amount: 0 });
    expect(res.status).toBe(403);
    expect(res.body.code).toBe('tier_required');
  });

  it('creates, lists, updates, deletes an expense with expense_date round-trip', async () => {
    const f = await foreman('crud');
    const jobId = await makeJob(f.token);
    const create = await request(app).post('/api/expenses').set('Authorization', `Bearer ${f.token}`)
      .send({ jobId, category: 'permit_fee', description: 'Electrical permit', amount: 175.50, expenseDate: '2026-05-20' });
    expect(create.status).toBe(201);
    expect(create.body.expense.expenseDate).toBe('2026-05-20');
    const id = create.body.expense.id;

    const list = await request(app).get(`/api/jobs/${jobId}/expenses`).set('Authorization', `Bearer ${f.token}`);
    expect(list.body.expenses).toHaveLength(1);

    const patch = await request(app).patch(`/api/expenses/${id}`).set('Authorization', `Bearer ${f.token}`)
      .send({ amount: 200.00 });
    expect(Number(patch.body.expense.amount)).toBe(200);

    const del = await request(app).delete(`/api/expenses/${id}`).set('Authorization', `Bearer ${f.token}`);
    expect(del.status).toBe(204);
  });

  it('isolates across foremen (403 not_owner)', async () => {
    const a = await foreman('iso-a');
    const b = await foreman('iso-b');
    const jobId = await makeJob(a.token);
    const create = await request(app).post('/api/expenses').set('Authorization', `Bearer ${a.token}`)
      .send({ jobId, category: 'fuel', description: 'gas', amount: 40 });
    const id = create.body.expense.id;
    const res = await request(app).patch(`/api/expenses/${id}`).set('Authorization', `Bearer ${b.token}`)
      .send({ amount: 1 });
    expect(res.status).toBe(403);
    expect(res.body.code).toBe('not_owner');
  });

  it('rejects unknown fields (zod strict)', async () => {
    const f = await foreman('strict');
    const jobId = await makeJob(f.token);
    const res = await request(app).post('/api/expenses').set('Authorization', `Bearer ${f.token}`)
      .send({ jobId, category: 'fuel', description: 'x', amount: 1, bogus: 1 });
    expect(res.status).toBe(400);
    expect(res.body.code).toBe('validation');
  });

  it('rejects bad expense_date format', async () => {
    const f = await foreman('date');
    const jobId = await makeJob(f.token);
    const res = await request(app).post('/api/expenses').set('Authorization', `Bearer ${f.token}`)
      .send({ jobId, category: 'fuel', description: 'x', amount: 1, expenseDate: 'May 20, 2026' });
    expect(res.status).toBe(400);
    expect(res.body.code).toBe('validation');
  });

  it('cascades on job delete', async () => {
    const f = await foreman('casc');
    const jobId = await makeJob(f.token);
    await request(app).post('/api/expenses').set('Authorization', `Bearer ${f.token}`)
      .send({ jobId, category: 'fuel', description: 'gas', amount: 40 });
    expect((await pg!.query(`SELECT COUNT(*) FROM job_expenses WHERE job_id = $1`, [jobId])).rows[0].count).toBe('1');
    await pg!.query(`DELETE FROM jobs WHERE id = $1`, [jobId]);
    expect((await pg!.query(`SELECT COUNT(*) FROM job_expenses WHERE job_id = $1`, [jobId])).rows[0].count).toBe('0');
  });
});
