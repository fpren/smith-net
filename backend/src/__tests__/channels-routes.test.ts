/**
 * /api/channels route smoke for the org_id tenant fence (migration 015).
 *
 * Two foremen in distinct orgs each create a channel. Each can only see
 * their own. POST + GET pass through the auth layer to channelRegistry,
 * which now filters by organization_id BEFORE the membership rules.
 */

import express from 'express';
import request from 'supertest';
import cookieParser from 'cookie-parser';
import { apiRouter } from '../api';
import { authRouter } from '../authRoutes';
import { createUserAndProfile } from '../jobsService';
import { generateTokens, UserRole } from '../auth';
import { channelRegistry } from '../channelRegistry';
import { pg, isPgEnabled } from '../db';

const describeDb = isPgEnabled() ? describe : describe.skip;

function buildApp() {
  const app = express();
  app.use(express.json());
  app.use(cookieParser());
  app.use('/api/auth', authRouter);
  app.use('/api', apiRouter);
  return app;
}

async function makeForemanWithToken(suffix: string) {
  const email = `chan-${suffix}-${Date.now()}-${Math.random().toString(36).slice(2, 7)}@example.com`;
  const user = await createUserAndProfile({
    email,
    password: 'password123',
    displayName: `Boss ${suffix}`,
    role: UserRole.FOREMAN,
  });
  const tokens = await generateTokens(user);
  return { id: user.id, token: tokens.accessToken };
}

async function clean() {
  if (!isPgEnabled()) return;
  await pg!.query('DELETE FROM channels');
  (channelRegistry as any).channels.clear();
  (channelRegistry as any).meshHashIndex.clear();
}

describeDb('GET/POST /api/channels — org tenant fence', () => {
  let app: express.Express;
  beforeAll(() => { app = buildApp(); });
  beforeEach(clean);
  afterAll(async () => { await clean(); await pg?.end(); });

  it('POST /api/channels persists organization_id from the caller', async () => {
    const boss = await makeForemanWithToken('a');
    const res = await request(app)
      .post('/api/channels')
      .set('Authorization', `Bearer ${boss.token}`)
      .send({ name: 'A-general', type: 'group' });
    expect(res.status).toBe(201);
    expect(res.body.organizationId).toBe(boss.id);

    const row = await pg!.query<{ organization_id: string }>(
      'SELECT organization_id FROM channels WHERE id = $1',
      [res.body.id],
    );
    expect(row.rows[0].organization_id).toBe(boss.id);
  });

  it('GET /api/channels for boss-B does NOT include boss-A\'s channel', async () => {
    const bossA = await makeForemanWithToken('a2');
    const bossB = await makeForemanWithToken('b2');

    const createA = await request(app)
      .post('/api/channels')
      .set('Authorization', `Bearer ${bossA.token}`)
      .send({ name: 'alpha', type: 'group' });
    const createB = await request(app)
      .post('/api/channels')
      .set('Authorization', `Bearer ${bossB.token}`)
      .send({ name: 'beta', type: 'group' });

    const listB = await request(app)
      .get('/api/channels')
      .set('Authorization', `Bearer ${bossB.token}`);
    const ids = listB.body.map((c: { id: string }) => c.id);
    expect(ids).toContain(createB.body.id);
    expect(ids).not.toContain(createA.body.id);
  });

  it('GET /api/channels even sees a broadcast channel only within the same org', async () => {
    const bossA = await makeForemanWithToken('a3');
    const bossB = await makeForemanWithToken('b3');

    const createA = await request(app)
      .post('/api/channels')
      .set('Authorization', `Bearer ${bossA.token}`)
      .send({ name: 'A-broadcast', type: 'broadcast' });

    const listB = await request(app)
      .get('/api/channels')
      .set('Authorization', `Bearer ${bossB.token}`);
    const ids = listB.body.map((c: { id: string }) => c.id);
    expect(ids).not.toContain(createA.body.id);
  });
});
