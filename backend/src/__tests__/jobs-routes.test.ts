import express from 'express';
import request from 'supertest';
import cookieParser from 'cookie-parser';
import { authRouter } from '../authRoutes';
import { jobsRouter } from '../jobsRoutes';
import { userStore, generateTokens, UserRole } from '../auth';
import { pg, isPgEnabled } from '../db';

// Skip the entire suite when no Postgres test DB is configured.
// To run these tests, set DATABASE_URL pointing at a dev/test Postgres
// with migration 003_jobs_expansion.sql applied.
const describeDb = isPgEnabled() ? describe : describe.skip;

function buildApp() {
  const app = express();
  app.use(express.json());
  app.use(cookieParser());
  app.use('/api/auth', authRouter);
  app.use('/api/jobs', jobsRouter);
  return app;
}

// Each test creates its own foreman with a unique email so the in-memory
// userStore doesn't collide. Returns the access token.
async function createForemanAndLogin(suffix: string): Promise<{ id: string; token: string }> {
  const user = await userStore.createUser(
    `foreman-jobs-${suffix}-${Date.now()}@example.com`,
    'password123',
    `Foreman ${suffix}`,
    UserRole.FOREMAN
  );
  const { accessToken } = generateTokens(user);
  return { id: user.id, token: accessToken };
}

// Truncate the test data this suite produces so reruns are clean.
afterEach(async () => {
  if (!isPgEnabled() || !pg) return;
  await pg.query(`DELETE FROM job_crew`);
  await pg.query(`DELETE FROM jobs WHERE foreman_id LIKE 'foreman-jobs-%' OR foreman_id IN (SELECT id FROM profiles WHERE email LIKE 'foreman-jobs-%')`);
});

describeDb('GET /api/jobs', () => {
  const app = buildApp();

  it('returns empty list for a new foreman', async () => {
    const f = await createForemanAndLogin('list-empty');
    const res = await request(app)
      .get('/api/jobs')
      .set('Authorization', `Bearer ${f.token}`);
    expect(res.status).toBe(200);
    expect(res.body.jobs).toEqual([]);
  });

  it('returns 401 with no auth', async () => {
    const res = await request(app).get('/api/jobs');
    expect(res.status).toBe(401);
  });

  it('returns 403 tier_required for Solo user', async () => {
    const user = await userStore.createUser(
      `solo-jobs-${Date.now()}@example.com`,
      'password123',
      'Solo',
      UserRole.SOLO
    );
    const { accessToken } = generateTokens(user);
    const res = await request(app)
      .get('/api/jobs')
      .set('Authorization', `Bearer ${accessToken}`);
    expect(res.status).toBe(403);
    expect(res.body.code).toBe('tier_required');
  });
});

describeDb('GET /api/jobs/:id', () => {
  const app = buildApp();

  it('returns 404 when job does not exist', async () => {
    const f = await createForemanAndLogin('getone-404');
    const res = await request(app)
      .get('/api/jobs/00000000-0000-0000-0000-000000000000')
      .set('Authorization', `Bearer ${f.token}`);
    expect(res.status).toBe(404);
  });
});
