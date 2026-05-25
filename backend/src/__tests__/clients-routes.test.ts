// backend/src/__tests__/clients-routes.test.ts
import express from 'express';
import request from 'supertest';
import cookieParser from 'cookie-parser';
import { authRouter } from '../authRoutes';
import { clientsRouter } from '../clientsRoutes';
import { generateTokens, UserRole, userStore } from '../auth';
import { createUserAndProfile } from '../jobsService';
import { pg, isPgEnabled } from '../db';

const describeDb = isPgEnabled() ? describe : describe.skip;

function buildApp() {
  const app = express();
  app.use(express.json());
  app.use(cookieParser());
  app.use('/api/auth', authRouter);
  app.use('/api/clients', clientsRouter);
  return app;
}

async function foreman(suffix: string): Promise<{ id: string; token: string }> {
  const user = await createUserAndProfile({
    email: `foreman-clients-${suffix}-${Date.now()}@example.com`,
    password: 'password123',
    displayName: `Foreman ${suffix}`,
    role: UserRole.FOREMAN,
  });
  const { accessToken } = await generateTokens(user);
  return { id: user.id, token: accessToken };
}

describeDb('clients routes', () => {
  const app = buildApp();

  afterEach(async () => {
    if (!isPgEnabled() || !pg) return;
    await pg.query(`DELETE FROM clients`);
    await pg.query(`DELETE FROM profiles WHERE email LIKE 'foreman-clients-%' OR email LIKE 'solo-clients-%'`);
    await pg.query(`DELETE FROM users WHERE email LIKE 'foreman-clients-%' OR email LIKE 'solo-clients-%'`);
  });
  afterAll(async () => { await pg?.end(); });

  it('401 without auth', async () => {
    const res = await request(app).get('/api/clients');
    expect(res.status).toBe(401);
  });

  it('403 tier_required for Solo', async () => {
    const u = await userStore.createUser(`solo-clients-${Date.now()}@example.com`, 'password123', 'Solo', UserRole.SOLO);
    const { accessToken } = await generateTokens(u);
    const res = await request(app).get('/api/clients').set('Authorization', `Bearer ${accessToken}`);
    expect(res.status).toBe(403);
    expect(res.body.code).toBe('tier_required');
  });

  it('creates, lists, gets, updates, soft-deletes a client', async () => {
    const f = await foreman('crud');
    const create = await request(app).post('/api/clients')
      .set('Authorization', `Bearer ${f.token}`)
      .send({ name: 'Acme Co', email: 'a@acme.test' });
    expect(create.status).toBe(201);
    const id = create.body.client.id;
    expect(create.body.client.name).toBe('Acme Co');

    const list = await request(app).get('/api/clients').set('Authorization', `Bearer ${f.token}`);
    expect(list.body.clients).toHaveLength(1);

    const get = await request(app).get(`/api/clients/${id}`).set('Authorization', `Bearer ${f.token}`);
    expect(get.body.client.id).toBe(id);
    expect(get.body.jobs).toEqual([]);

    const patch = await request(app).patch(`/api/clients/${id}`)
      .set('Authorization', `Bearer ${f.token}`).send({ phone: '555-1234' });
    expect(patch.body.client.phone).toBe('555-1234');

    const del = await request(app).delete(`/api/clients/${id}`).set('Authorization', `Bearer ${f.token}`);
    expect(del.status).toBe(204);
    const after = await request(app).get('/api/clients').set('Authorization', `Bearer ${f.token}`);
    expect(after.body.clients).toHaveLength(0);
  });

  it('isolates clients across owners (404 cross-owner)', async () => {
    const a = await foreman('iso-a');
    const b = await foreman('iso-b');
    const created = await request(app).post('/api/clients')
      .set('Authorization', `Bearer ${a.token}`).send({ name: 'A only' });
    const id = created.body.client.id;
    const res = await request(app).get(`/api/clients/${id}`).set('Authorization', `Bearer ${b.token}`);
    expect(res.status).toBe(404);
  });

  it('rejects unknown fields (zod strict)', async () => {
    const f = await foreman('strict');
    const res = await request(app).post('/api/clients')
      .set('Authorization', `Bearer ${f.token}`).send({ name: 'X', bogus: 1 });
    expect(res.status).toBe(400);
    expect(res.body.code).toBe('validation');
  });
});
