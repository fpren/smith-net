import express from 'express';
import request from 'supertest';
import cookieParser from 'cookie-parser';
import { authRouter } from '../authRoutes';
import { shiftsRouter } from '../shiftsRoutes';
import { presenceLocationRouter } from '../presenceLocationRoutes';
import { createUserAndProfile } from '../jobsService';
import { generateTokens, UserRole } from '../auth';
import { pg, isPgEnabled } from '../db';

const describeDb = isPgEnabled() ? describe : describe.skip;

function buildApp() {
  const app = express();
  app.use(express.json());
  app.use(cookieParser());
  app.use('/api/auth', authRouter);
  app.use('/api/shifts', shiftsRouter);
  app.use('/api', presenceLocationRouter);
  return app;
}

async function createUserAndLogin(suffix: string, role: UserRole): Promise<{ id: string; token: string }> {
  const email = `org-${suffix}-${Date.now()}-${Math.random().toString(36).slice(2, 7)}@example.com`;
  const user = await createUserAndProfile({
    email,
    password: 'password123',
    displayName: `Org ${suffix}`,
    role,
  });
  const tokens = await generateTokens(user);
  return { id: user.id, token: tokens.accessToken };
}

async function clean() {
  if (!isPgEnabled()) return;
  await pg!.query('DELETE FROM organization_invites');
  await pg!.query('DELETE FROM crew_positions');
  await pg!.query('DELETE FROM shifts');
}

describeDb('auth /org routes (invite, join, members)', () => {
  let app: express.Express;
  beforeAll(() => { app = buildApp(); });
  beforeEach(clean);
  afterAll(async () => { await clean(); await pg?.end(); });

  it('POST /api/auth/org/invites requires authentication (401)', async () => {
    const res = await request(app).post('/api/auth/org/invites');
    expect(res.status).toBe(401);
  });

  it('POST /api/auth/org/invites rejects non-foreman role (403)', async () => {
    const solo = await createUserAndLogin('s1', UserRole.SOLO);
    const res = await request(app)
      .post('/api/auth/org/invites')
      .set('Authorization', `Bearer ${solo.token}`);
    expect(res.status).toBe(403);
  });

  it('POST /api/auth/org/invites returns code + expiresAt for a foreman', async () => {
    const boss = await createUserAndLogin('b1', UserRole.FOREMAN);
    const res = await request(app)
      .post('/api/auth/org/invites')
      .set('Authorization', `Bearer ${boss.token}`);
    expect(res.status).toBe(200);
    expect(res.body.code).toMatch(/^[A-Z2-9]{8}$/);
    expect(typeof res.body.expiresAt).toBe('string');
    expect(new Date(res.body.expiresAt).getTime()).toBeGreaterThan(Date.now());
  });

  it('POST /api/auth/org/join with valid code reassigns org_id and flips role to team_member', async () => {
    const boss = await createUserAndLogin('b2', UserRole.FOREMAN);
    const worker = await createUserAndLogin('w2', UserRole.SOLO);

    const inv = await request(app)
      .post('/api/auth/org/invites')
      .set('Authorization', `Bearer ${boss.token}`);
    const code = inv.body.code;

    const join = await request(app)
      .post('/api/auth/org/join')
      .set('Authorization', `Bearer ${worker.token}`)
      .send({ code });
    expect(join.status).toBe(200);
    expect(join.body.user.organizationId).toBe(boss.id);
    expect(join.body.user.role).toBe('team');
  });

  it('POST /api/auth/org/join with bad code returns 404', async () => {
    const worker = await createUserAndLogin('w3', UserRole.SOLO);
    const res = await request(app)
      .post('/api/auth/org/join')
      .set('Authorization', `Bearer ${worker.token}`)
      .send({ code: 'NOPESUCH' });
    expect(res.status).toBe(404);
  });

  it('POST /api/auth/org/join with expired code returns 410', async () => {
    const boss = await createUserAndLogin('b4', UserRole.FOREMAN);
    const worker = await createUserAndLogin('w4', UserRole.SOLO);

    const inv = await request(app)
      .post('/api/auth/org/invites')
      .set('Authorization', `Bearer ${boss.token}`);
    const code = inv.body.code;
    await pg!.query(
      `UPDATE organization_invites SET expires_at = NOW() - INTERVAL '1 second' WHERE code = $1`,
      [code]
    );

    const join = await request(app)
      .post('/api/auth/org/join')
      .set('Authorization', `Bearer ${worker.token}`)
      .send({ code });
    expect(join.status).toBe(410);
  });

  it('POST /api/auth/org/join with empty code returns 400', async () => {
    const worker = await createUserAndLogin('w5', UserRole.SOLO);
    const res = await request(app)
      .post('/api/auth/org/join')
      .set('Authorization', `Bearer ${worker.token}`)
      .send({});
    expect(res.status).toBe(400);
  });

  it('GET /api/auth/org/members returns foreman + joined worker', async () => {
    const boss = await createUserAndLogin('b6', UserRole.FOREMAN);
    const worker = await createUserAndLogin('w6', UserRole.SOLO);

    const inv = await request(app)
      .post('/api/auth/org/invites')
      .set('Authorization', `Bearer ${boss.token}`);
    await request(app)
      .post('/api/auth/org/join')
      .set('Authorization', `Bearer ${worker.token}`)
      .send({ code: inv.body.code });

    const list = await request(app)
      .get('/api/auth/org/members')
      .set('Authorization', `Bearer ${boss.token}`);
    expect(list.status).toBe(200);
    const ids = list.body.members.map((m: { id: string }) => m.id);
    expect(ids).toEqual(expect.arrayContaining([boss.id, worker.id]));
  });

  it('GET /api/auth/org/members rejects non-foreman (403)', async () => {
    const solo = await createUserAndLogin('s7', UserRole.SOLO);
    const res = await request(app)
      .get('/api/auth/org/members')
      .set('Authorization', `Bearer ${solo.token}`);
    expect(res.status).toBe(403);
  });

  it('end-to-end: joiner appears on foreman /api/crew/positions after joining', async () => {
    const boss = await createUserAndLogin('e1', UserRole.FOREMAN);
    const worker = await createUserAndLogin('w-e1', UserRole.SOLO);

    // Worker starts a shift + posts a position BEFORE joining (org-of-one).
    await request(app).post('/api/shifts/start').set('Authorization', `Bearer ${worker.token}`).send({ source: 'android' });
    await request(app)
      .post('/api/presence/location')
      .set('Authorization', `Bearer ${worker.token}`)
      .send({ lat: 40, lng: -73 });

    // Pre-join: boss sees only themselves (worker still solo + own org).
    const pre = await request(app).get('/api/crew/positions').set('Authorization', `Bearer ${boss.token}`);
    const preIds = pre.body.positions.map((p: { userId: string }) => p.userId);
    expect(preIds).not.toContain(worker.id);

    const inv = await request(app)
      .post('/api/auth/org/invites')
      .set('Authorization', `Bearer ${boss.token}`);
    await request(app)
      .post('/api/auth/org/join')
      .set('Authorization', `Bearer ${worker.token}`)
      .send({ code: inv.body.code });

    // Post-join: worker's existing crew_position row was reassigned to boss's
    // org, and their role flipped to team_member, so they're visible now.
    const post = await request(app).get('/api/crew/positions').set('Authorization', `Bearer ${boss.token}`);
    const postIds = post.body.positions.map((p: { userId: string }) => p.userId);
    expect(postIds).toEqual(expect.arrayContaining([worker.id]));
  });
});
