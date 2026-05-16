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
    expect(res.body.user_id).toBe(id);
    expect(parseFloat(res.body.latitude)).toBeCloseTo(40.748, 3);
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

  it('GET /api/crew/positions returns positions for users with open shifts', async () => {
    const foreman = await createUserAndLogin('list2', UserRole.FOREMAN);
    const crewA = await createUserAndLogin('list2a', UserRole.FOREMAN);
    await request(app).post('/api/shifts/start').set('Cookie', `smithnet_access=${crewA.token}`).send({ source: 'android' });
    await request(app)
      .post('/api/presence/location')
      .set('Cookie', `smithnet_access=${crewA.token}`)
      .send({ lat: 30, lng: 60 });

    const res = await request(app).get('/api/crew/positions').set('Cookie', `smithnet_access=${foreman.token}`);
    expect(res.status).toBe(200);
    expect(Array.isArray(res.body)).toBe(true);
    const ids = res.body.map((r: { user_id: string }) => r.user_id);
    expect(ids).toContain(crewA.id);
  });
});
