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
  const email = `pres-${suffix}-${Date.now()}@example.com`;
  const user = await createUserAndProfile({
    email,
    password: 'password123',
    displayName: `Pres ${suffix}`,
    role,
  });
  const tokens = await generateTokens(user);
  return { id: user.id, token: tokens.accessToken };
}

async function cleanShifts() {
  if (!isPgEnabled()) return;
  await pg!.query('DELETE FROM crew_positions');
  await pg!.query('DELETE FROM shifts');
}

describeDb('presence/location + crew/positions routes', () => {
  let app: express.Express;
  beforeAll(() => { app = buildApp(); });
  beforeEach(cleanShifts);
  afterAll(async () => {
    await cleanShifts();
    await pg?.end();
  });

  it('POST /api/presence/location requires an open shift (403 otherwise)', async () => {
    const { token } = await createUserAndLogin('loc1', UserRole.FOREMAN);
    const noshift = await request(app)
      .post('/api/presence/location')
      .set('Cookie', `smithnet_access=${token}`)
      .send({ lat: 40.7, lng: -73.9 });
    expect(noshift.status).toBe(403);
  });

  it('POST /api/presence/location upserts the position (200)', async () => {
    const { token, id } = await createUserAndLogin('loc2', UserRole.FOREMAN);
    await request(app).post('/api/shifts/start').set('Cookie', `smithnet_access=${token}`).send({ source: 'android' });
    const res = await request(app)
      .post('/api/presence/location')
      .set('Cookie', `smithnet_access=${token}`)
      .send({ lat: 40.748, lng: -73.985, accuracy_m: 8.0, battery_pct: 72 });
    expect(res.status).toBe(200);
    expect(res.body.position.userId).toBe(id);
    expect(res.body.position.latitude).toBeCloseTo(40.748, 3);
  });

  it('POST /api/presence/location rejects invalid lat/lng (400)', async () => {
    const { token } = await createUserAndLogin('loc3', UserRole.FOREMAN);
    await request(app).post('/api/shifts/start').set('Cookie', `smithnet_access=${token}`).send({ source: 'web' });
    const res = await request(app)
      .post('/api/presence/location')
      .set('Cookie', `smithnet_access=${token}`)
      .send({ lat: 'not-a-number', lng: -73.9 });
    expect(res.status).toBe(400);
  });

  it('GET /api/crew/positions requires foreman+ role (403 for solo)', async () => {
    const { token } = await createUserAndLogin('list1', UserRole.SOLO);
    const res = await request(app).get('/api/crew/positions').set('Cookie', `smithnet_access=${token}`);
    expect(res.status).toBe(403);
  });

  it('GET /api/crew/positions returns positions in the caller org', async () => {
    // Org-of-one: foreman sees their own position (same org_id), not another
    // foreman's position in a different org. The cross-foreman case is covered
    // by the next test.
    const foreman = await createUserAndLogin('list2', UserRole.FOREMAN);
    await request(app).post('/api/shifts/start').set('Cookie', `smithnet_access=${foreman.token}`).send({ source: 'android' });
    await request(app)
      .post('/api/presence/location')
      .set('Cookie', `smithnet_access=${foreman.token}`)
      .send({ lat: 30, lng: 60 });

    const res = await request(app).get('/api/crew/positions').set('Cookie', `smithnet_access=${foreman.token}`);
    expect(res.status).toBe(200);
    expect(Array.isArray(res.body.positions)).toBe(true);
    const ids = res.body.positions.map((r: { userId: string }) => r.userId);
    expect(ids).toContain(foreman.id);
    const row = res.body.positions.find((r: { userId: string }) => r.userId === foreman.id);
    expect(typeof row.displayName).toBe('string');
  });

  it('GET /api/crew/positions does NOT leak positions across orgs', async () => {
    // Two foremen in separate orgs (the org-of-one default). Each must only
    // see themselves; neither should see the other's dot.
    const alpha = await createUserAndLogin('list3a', UserRole.FOREMAN);
    const bravo = await createUserAndLogin('list3b', UserRole.FOREMAN);

    for (const u of [alpha, bravo]) {
      await request(app).post('/api/shifts/start').set('Cookie', `smithnet_access=${u.token}`).send({ source: 'android' });
      await request(app)
        .post('/api/presence/location')
        .set('Cookie', `smithnet_access=${u.token}`)
        .send({ lat: 40, lng: -73 });
    }

    const alphaRes = await request(app).get('/api/crew/positions').set('Cookie', `smithnet_access=${alpha.token}`);
    expect(alphaRes.status).toBe(200);
    const alphaIds = alphaRes.body.positions.map((r: { userId: string }) => r.userId);
    expect(alphaIds).toContain(alpha.id);
    expect(alphaIds).not.toContain(bravo.id);

    const bravoRes = await request(app).get('/api/crew/positions').set('Cookie', `smithnet_access=${bravo.token}`);
    expect(bravoRes.status).toBe(200);
    const bravoIds = bravoRes.body.positions.map((r: { userId: string }) => r.userId);
    expect(bravoIds).toContain(bravo.id);
    expect(bravoIds).not.toContain(alpha.id);
  });
});
