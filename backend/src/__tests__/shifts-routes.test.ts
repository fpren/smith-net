import express from 'express';
import request from 'supertest';
import cookieParser from 'cookie-parser';
import { authRouter } from '../authRoutes';
import { shiftsRouter } from '../shiftsRoutes';
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
  return app;
}

async function createForemanAndLogin(suffix: string): Promise<{ id: string; token: string }> {
  const email = `shifts-${suffix}-${Date.now()}@example.com`;
  const user = await createUserAndProfile({
    email,
    password: 'password123',
    displayName: `Shifts ${suffix}`,
    role: UserRole.FOREMAN,
  });
  const tokens = await generateTokens(user);
  return { id: user.id, token: tokens.accessToken };
}

async function makeUserWithToken(
  role: UserRole,
  suffix: string,
): Promise<{ id: string; token: string }> {
  const email = `shifts-${suffix}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@example.com`;
  const user = await createUserAndProfile({
    email,
    password: 'password123',
    displayName: `Shifts ${suffix}`,
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

describeDb('shifts routes', () => {
  let app: express.Express;
  beforeAll(() => { app = buildApp(); });
  beforeEach(cleanShifts);
  afterAll(cleanShifts);

  it('POST /api/shifts/start opens a shift (200) — requires auth (401 otherwise)', async () => {
    const noauth = await request(app).post('/api/shifts/start').send({ source: 'android' });
    expect(noauth.status).toBe(401);

    const { token } = await createForemanAndLogin('start1');
    const res = await request(app)
      .post('/api/shifts/start')
      .set('Cookie', `smithnet_access=${token}`)
      .send({ source: 'android' });
    expect(res.status).toBe(200);
    expect(res.body.shift.id).toBeTruthy();
    expect(res.body.shift.source).toBe('android');
    expect(res.body.shift.endedAt).toBeNull();
  });

  it('POST /api/shifts/start returns 409 if user already has an open shift', async () => {
    const { token } = await createForemanAndLogin('start2');
    await request(app).post('/api/shifts/start').set('Cookie', `smithnet_access=${token}`).send({ source: 'android' });
    const res = await request(app).post('/api/shifts/start').set('Cookie', `smithnet_access=${token}`).send({ source: 'web' });
    expect(res.status).toBe(409);
  });

  it('POST /api/shifts/end closes the open shift (200) or 404 if none', async () => {
    const { token } = await createForemanAndLogin('end1');
    const noopen = await request(app).post('/api/shifts/end').set('Cookie', `smithnet_access=${token}`).send();
    expect(noopen.status).toBe(404);

    await request(app).post('/api/shifts/start').set('Cookie', `smithnet_access=${token}`).send({ source: 'android' });
    const closed = await request(app).post('/api/shifts/end').set('Cookie', `smithnet_access=${token}`).send();
    expect(closed.status).toBe(200);
    expect(closed.body.shift.endedAt).toBeTruthy();
  });

  it('GET /api/shifts/current returns the open shift or null', async () => {
    const { token } = await createForemanAndLogin('cur1');
    const empty = await request(app).get('/api/shifts/current').set('Cookie', `smithnet_access=${token}`);
    expect(empty.status).toBe(200);
    expect(empty.body.shift).toBeNull();

    await request(app).post('/api/shifts/start').set('Cookie', `smithnet_access=${token}`).send({ source: 'web' });
    const full = await request(app).get('/api/shifts/current').set('Cookie', `smithnet_access=${token}`);
    expect(full.body.shift.source).toBe('web');
  });

  it('source must be one of android / web / admin (400 otherwise)', async () => {
    const { token } = await createForemanAndLogin('src1');
    const res = await request(app)
      .post('/api/shifts/start')
      .set('Cookie', `smithnet_access=${token}`)
      .send({ source: 'martian-rover' });
    expect(res.status).toBe(400);
  });
});

describeDb('shifts time-entry fields', () => {
  let app: express.Express;
  beforeAll(() => { app = buildApp(); });
  beforeEach(cleanShifts);
  afterAll(cleanShifts);

  it('start persists + serializes entryType/jobTitle (camelCase)', async () => {
    const u = await makeUserWithToken(UserRole.SOLO, 'st1');
    const res = await request(app)
      .post('/api/shifts/start')
      .set('Authorization', `Bearer ${u.token}`)
      .send({ source: 'web', entryType: 'overtime', jobTitle: 'Kitchen Reno' });
    expect(res.status).toBe(200);
    expect(res.body.shift.entryType).toBe('overtime');
    expect(res.body.shift.jobTitle).toBe('Kitchen Reno');
    expect(res.body.shift.jobId).toBeNull();
    expect(res.body.shift.clockOutReason).toBeNull();
  });

  it('start defaults entryType to regular when omitted', async () => {
    const u = await makeUserWithToken(UserRole.SOLO, 'st2');
    const res = await request(app)
      .post('/api/shifts/start')
      .set('Authorization', `Bearer ${u.token}`)
      .send({});
    expect(res.status).toBe(200);
    expect(res.body.shift.entryType).toBe('regular');
  });

  it('rejects an invalid entryType with 400', async () => {
    const u = await makeUserWithToken(UserRole.SOLO, 'st3');
    const res = await request(app)
      .post('/api/shifts/start')
      .set('Authorization', `Bearer ${u.token}`)
      .send({ entryType: 'napping' });
    expect(res.status).toBe(400);
    expect(res.body.code).toBe('validation');
  });

  it('end persists the clock-out reason', async () => {
    const u = await makeUserWithToken(UserRole.SOLO, 'st4');
    await request(app).post('/api/shifts/start').set('Authorization', `Bearer ${u.token}`).send({});
    const res = await request(app)
      .post('/api/shifts/end')
      .set('Authorization', `Bearer ${u.token}`)
      .send({ reason: 'lunch' });
    expect(res.status).toBe(200);
    expect(res.body.shift.clockOutReason).toBe('lunch');
    expect(res.body.shift.endedAt).toBeTruthy();
  });

  it('a second open shift still 409s', async () => {
    const u = await makeUserWithToken(UserRole.SOLO, 'st5');
    await request(app).post('/api/shifts/start').set('Authorization', `Bearer ${u.token}`).send({});
    const res = await request(app).post('/api/shifts/start').set('Authorization', `Bearer ${u.token}`).send({});
    expect(res.status).toBe(409);
  });
});

afterAll(async () => {
  await pg?.end();
});
