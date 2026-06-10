import express from 'express';
import request from 'supertest';
import cookieParser from 'cookie-parser';
import { authRouter } from '../authRoutes';
import { profilesRouter } from '../profilesRoutes';
import { userStore, generateTokens, UserRole } from '../auth';
import { pg, isPgEnabled } from '../db';

function buildApp() {
  const app = express();
  app.use(express.json());
  app.use(cookieParser());
  app.use('/api/auth', authRouter);
  app.use('/api/profiles', profilesRouter);
  return app;
}

// Register through the real endpoint so the user gets a profiles row + public_id
// (createUserAndProfile). userStore.createUser alone only writes the users row.
async function register(app: express.Express, email: string, displayName: string) {
  const res = await request(app)
    .post('/api/auth/register')
    .send({ email, password: 'password123', displayName });
  return res.body as { user: { id: string }; accessToken: string };
}

// The directory lookup/teammates endpoints are pg-backed; skip when no DB.
const d = isPgEnabled() ? describe : describe.skip;

d('GET /api/profiles/lookup', () => {
  const app = buildApp();

  it('requires auth (401)', async () => {
    const res = await request(app).get('/api/profiles/lookup?publicId=ABCD1234');
    expect(res.status).toBe(401);
  });

  it('rejects a malformed publicId (400)', async () => {
    const u = await userStore.createUser(`look-bad-${Date.now()}@ex.com`, 'password123', 'L', UserRole.SOLO);
    const { accessToken } = await generateTokens(u);
    const res = await request(app)
      .get('/api/profiles/lookup?publicId=nope')
      .set('Authorization', `Bearer ${accessToken}`);
    expect(res.status).toBe(400);
  });

  it('returns null for an unknown publicId', async () => {
    const u = await userStore.createUser(`look-none-${Date.now()}@ex.com`, 'password123', 'L', UserRole.SOLO);
    const { accessToken } = await generateTokens(u);
    const res = await request(app)
      .get('/api/profiles/lookup?publicId=ZZ999999')
      .set('Authorization', `Bearer ${accessToken}`);
    expect(res.status).toBe(200);
    expect(res.body.profile).toBeNull();
  });

  it('finds a user by public_id across orgs', async () => {
    const target = await register(app, `look-target-${Date.now()}@ex.com`, 'Target');
    const caller = await register(app, `look-caller-${Date.now()}@ex.com`, 'Caller');
    // Each user is its own org at registration -- this is a genuine cross-org lookup.
    const { rows } = await pg!.query('SELECT public_id FROM profiles WHERE id = $1', [target.user.id]);
    const publicId = rows[0].public_id as string;

    const res = await request(app)
      .get(`/api/profiles/lookup?publicId=${publicId}`)
      .set('Authorization', `Bearer ${caller.accessToken}`);
    expect(res.status).toBe(200);
    expect(res.body.profile).not.toBeNull();
    expect(res.body.profile.id).toBe(target.user.id);
    expect(res.body.profile.publicId).toBe(publicId);
  });
});

d('GET /api/profiles/teammates', () => {
  const app = buildApp();

  it('requires auth (401)', async () => {
    const res = await request(app).get('/api/profiles/teammates');
    expect(res.status).toBe(401);
  });

  it('returns same-org members and excludes self + other orgs', async () => {
    const me = await register(app, `tm-me-${Date.now()}@ex.com`, 'Me');
    const mate = await register(app, `tm-mate-${Date.now()}@ex.com`, 'Mate');
    const outsider = await register(app, `tm-out-${Date.now()}@ex.com`, 'Outsider');
    // Put `mate` in my org; leave `outsider` in its own.
    await pg!.query('UPDATE profiles SET organization_id = $1 WHERE id = $2', [me.user.id, mate.user.id]);

    const res = await request(app)
      .get('/api/profiles/teammates')
      .set('Authorization', `Bearer ${me.accessToken}`);
    expect(res.status).toBe(200);
    const ids = res.body.profiles.map((p: any) => p.id);
    expect(ids).toContain(mate.user.id);
    expect(ids).not.toContain(me.user.id);
    expect(ids).not.toContain(outsider.user.id);
  });
});
