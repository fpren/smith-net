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

  it('DELETE /api/auth/org/members/:id without token returns 401', async () => {
    const res = await request(app).delete('/api/auth/org/members/anything');
    expect(res.status).toBe(401);
  });

  it('DELETE /api/auth/org/members/:id by non-foreman returns 403', async () => {
    const solo = await createUserAndLogin('rmS', UserRole.SOLO);
    const res = await request(app)
      .delete('/api/auth/org/members/anything')
      .set('Authorization', `Bearer ${solo.token}`);
    expect(res.status).toBe(403);
  });

  it('DELETE /api/auth/org/members/:id removes a member and returns 200', async () => {
    const boss = await createUserAndLogin('rmB', UserRole.FOREMAN);
    const worker = await createUserAndLogin('rmW', UserRole.SOLO);

    const inv = await request(app)
      .post('/api/auth/org/invites')
      .set('Authorization', `Bearer ${boss.token}`);
    await request(app)
      .post('/api/auth/org/join')
      .set('Authorization', `Bearer ${worker.token}`)
      .send({ code: inv.body.code });

    const del = await request(app)
      .delete(`/api/auth/org/members/${worker.id}`)
      .set('Authorization', `Bearer ${boss.token}`);
    expect(del.status).toBe(200);
    expect(del.body.removed).toEqual({ id: worker.id, role: 'solo', organizationId: worker.id });
  });

  it('DELETE /api/auth/org/members/:id self-kick returns 400', async () => {
    const boss = await createUserAndLogin('rmSelf', UserRole.FOREMAN);
    const res = await request(app)
      .delete(`/api/auth/org/members/${boss.id}`)
      .set('Authorization', `Bearer ${boss.token}`);
    expect(res.status).toBe(400);
  });

  it('DELETE /api/auth/org/members/:id targeting a foreman peer returns 403', async () => {
    const bossA = await createUserAndLogin('rmFA', UserRole.FOREMAN);
    const bossB = await createUserAndLogin('rmFB', UserRole.FOREMAN);
    const inv = await request(app)
      .post('/api/auth/org/invites')
      .set('Authorization', `Bearer ${bossA.token}`);
    await request(app)
      .post('/api/auth/org/join')
      .set('Authorization', `Bearer ${bossB.token}`)
      .send({ code: inv.body.code });

    const res = await request(app)
      .delete(`/api/auth/org/members/${bossB.id}`)
      .set('Authorization', `Bearer ${bossA.token}`);
    expect(res.status).toBe(403);
  });

  it('DELETE /api/auth/org/members/:id across orgs returns 404', async () => {
    const bossA = await createUserAndLogin('rmXA', UserRole.FOREMAN);
    const bossB = await createUserAndLogin('rmXB', UserRole.FOREMAN);
    const outsider = await createUserAndLogin('rmXO', UserRole.SOLO);
    const inv = await request(app)
      .post('/api/auth/org/invites')
      .set('Authorization', `Bearer ${bossB.token}`);
    await request(app)
      .post('/api/auth/org/join')
      .set('Authorization', `Bearer ${outsider.token}`)
      .send({ code: inv.body.code });

    // bossA tries to remove outsider (who belongs to bossB's org).
    const res = await request(app)
      .delete(`/api/auth/org/members/${outsider.id}`)
      .set('Authorization', `Bearer ${bossA.token}`);
    expect(res.status).toBe(404);
  });

  it('end-to-end: kicked worker disappears from /api/auth/org/members and /api/crew/positions', async () => {
    const boss = await createUserAndLogin('rmE2E', UserRole.FOREMAN);
    const worker = await createUserAndLogin('rmE2EW', UserRole.SOLO);

    const inv = await request(app)
      .post('/api/auth/org/invites')
      .set('Authorization', `Bearer ${boss.token}`);
    await request(app)
      .post('/api/auth/org/join')
      .set('Authorization', `Bearer ${worker.token}`)
      .send({ code: inv.body.code });
    await request(app).post('/api/shifts/start').set('Authorization', `Bearer ${worker.token}`).send({ source: 'android' });
    await request(app)
      .post('/api/presence/location')
      .set('Authorization', `Bearer ${worker.token}`)
      .send({ lat: 41, lng: -72 });

    // Pre-kick: worker is visible.
    const pre = await request(app).get('/api/crew/positions').set('Authorization', `Bearer ${boss.token}`);
    expect(pre.body.positions.map((p: { userId: string }) => p.userId)).toEqual(expect.arrayContaining([worker.id]));

    await request(app)
      .delete(`/api/auth/org/members/${worker.id}`)
      .set('Authorization', `Bearer ${boss.token}`);

    const members = await request(app).get('/api/auth/org/members').set('Authorization', `Bearer ${boss.token}`);
    expect(members.body.members.map((m: { id: string }) => m.id)).not.toContain(worker.id);

    const post = await request(app).get('/api/crew/positions').set('Authorization', `Bearer ${boss.token}`);
    expect(post.body.positions.map((p: { userId: string }) => p.userId)).not.toContain(worker.id);
  });

  it('POST /api/auth/org/leave without token returns 401', async () => {
    const res = await request(app).post('/api/auth/org/leave');
    expect(res.status).toBe(401);
  });

  it('POST /api/auth/org/leave by a team_member who joined a foreman\'s org returns 200', async () => {
    const boss = await createUserAndLogin('lvBoss', UserRole.FOREMAN);
    const worker = await createUserAndLogin('lvWorker', UserRole.SOLO);
    const inv = await request(app)
      .post('/api/auth/org/invites')
      .set('Authorization', `Bearer ${boss.token}`);
    await request(app)
      .post('/api/auth/org/join')
      .set('Authorization', `Bearer ${worker.token}`)
      .send({ code: inv.body.code });

    const res = await request(app)
      .post('/api/auth/org/leave')
      .set('Authorization', `Bearer ${worker.token}`);
    expect(res.status).toBe(200);
    expect(res.body.user.role).toBe('solo');
    expect(res.body.user.organizationId).toBe(worker.id);
  });

  it('POST /api/auth/org/leave by an original foreman returns 403', async () => {
    const boss = await createUserAndLogin('lvOrig', UserRole.FOREMAN);
    const res = await request(app)
      .post('/api/auth/org/leave')
      .set('Authorization', `Bearer ${boss.token}`);
    expect(res.status).toBe(403);
  });

  it('POST /api/auth/org/leave by a solo user in own org-of-one returns 403', async () => {
    const solo = await createUserAndLogin('lvSolo', UserRole.SOLO);
    const res = await request(app)
      .post('/api/auth/org/leave')
      .set('Authorization', `Bearer ${solo.token}`);
    expect(res.status).toBe(403);
  });

  it('POST /api/auth/org/leave by a peer foreman keeps their foreman role', async () => {
    const bossA = await createUserAndLogin('lvPa', UserRole.FOREMAN);
    const bossB = await createUserAndLogin('lvPb', UserRole.FOREMAN);
    const inv = await request(app)
      .post('/api/auth/org/invites')
      .set('Authorization', `Bearer ${bossA.token}`);
    await request(app)
      .post('/api/auth/org/join')
      .set('Authorization', `Bearer ${bossB.token}`)
      .send({ code: inv.body.code });

    const res = await request(app)
      .post('/api/auth/org/leave')
      .set('Authorization', `Bearer ${bossB.token}`);
    expect(res.status).toBe(200);
    expect(res.body.user.role).toBe('foreman');
    expect(res.body.user.organizationId).toBe(bossB.id);
  });

  it('end-to-end: a worker who leaves disappears from foreman /api/auth/org/members and /api/crew/positions', async () => {
    const boss = await createUserAndLogin('lvE2E', UserRole.FOREMAN);
    const worker = await createUserAndLogin('lvE2EW', UserRole.SOLO);
    const inv = await request(app)
      .post('/api/auth/org/invites')
      .set('Authorization', `Bearer ${boss.token}`);
    await request(app)
      .post('/api/auth/org/join')
      .set('Authorization', `Bearer ${worker.token}`)
      .send({ code: inv.body.code });
    await request(app).post('/api/shifts/start').set('Authorization', `Bearer ${worker.token}`).send({ source: 'android' });
    await request(app)
      .post('/api/presence/location')
      .set('Authorization', `Bearer ${worker.token}`)
      .send({ lat: 42, lng: -71 });

    // Pre-leave: worker is visible.
    const pre = await request(app).get('/api/crew/positions').set('Authorization', `Bearer ${boss.token}`);
    expect(pre.body.positions.map((p: { userId: string }) => p.userId)).toEqual(expect.arrayContaining([worker.id]));

    await request(app)
      .post('/api/auth/org/leave')
      .set('Authorization', `Bearer ${worker.token}`);

    const members = await request(app).get('/api/auth/org/members').set('Authorization', `Bearer ${boss.token}`);
    expect(members.body.members.map((m: { id: string }) => m.id)).not.toContain(worker.id);

    const post = await request(app).get('/api/crew/positions').set('Authorization', `Bearer ${boss.token}`);
    expect(post.body.positions.map((p: { userId: string }) => p.userId)).not.toContain(worker.id);
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
