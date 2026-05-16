import express from 'express';
import request from 'supertest';
import cookieParser from 'cookie-parser';
import { authRouter } from '../authRoutes';
import { healthRouter } from '../healthRoutes';
import { pg, isPgEnabled } from '../db';
import { createUserAndProfile } from '../jobsService';
import { generateTokens, UserRole } from '../auth';
import { enqueue } from '../queue/queue';
import { tick as heartbeatTick } from '../daemons/heartbeatDaemon';

const describeDb = isPgEnabled() ? describe : describe.skip;

function buildApp() {
  const app = express();
  app.use(express.json());
  app.use(cookieParser());
  app.use('/api/auth', authRouter);
  app.use('/api/admin', healthRouter);
  return app;
}

async function createUserAndLogin(suffix: string, role: UserRole): Promise<{ id: string; token: string }> {
  const email = `health-${suffix}-${Date.now()}@example.com`;
  const user = await createUserAndProfile({ email, password: 'password123', displayName: `Health ${suffix}`, role });
  const tokens = await generateTokens(user);
  return { id: user.id, token: tokens.accessToken };
}

describeDb('GET /api/admin/health', () => {
  let app: express.Express;
  beforeAll(() => { app = buildApp(); });
  beforeEach(async () => {
    if (!isPgEnabled()) return;
    await pg!.query(`DELETE FROM worker_heartbeats WHERE worker_id LIKE 'test-%'`);
    await pg!.query(`DELETE FROM background_jobs WHERE kind='health_test'`);
  });
  afterAll(async () => { await pg?.end(); });

  it('403 for non-admin role', async () => {
    const { token } = await createUserAndLogin('foreman', UserRole.FOREMAN);
    const res = await request(app).get('/api/admin/health').set('Cookie', `smithnet_access=${token}`);
    expect(res.status).toBe(403);
  });

  it('200 for admin role; returns workers + queue rollup', async () => {
    const { token } = await createUserAndLogin('admin', UserRole.ADMIN);

    await heartbeatTick('test-worker-health', ['geocode', 'email']);
    await enqueue({ kind: 'health_test' as any, payload: { x: 1 } });

    const res = await request(app).get('/api/admin/health').set('Cookie', `smithnet_access=${token}`);
    expect(res.status).toBe(200);
    expect(Array.isArray(res.body.workers)).toBe(true);
    const w = res.body.workers.find((w: any) => w.workerId === 'test-worker-health');
    expect(w).toBeDefined();
    expect(w.kinds).toEqual(['geocode', 'email']);
    expect(typeof w.ageSec).toBe('number');
    expect(Array.isArray(res.body.queue.byKindState)).toBe(true);
    const ourRow = res.body.queue.byKindState.find((r: any) => r.kind === 'health_test');
    expect(ourRow).toBeDefined();
    expect(ourRow.count).toBeGreaterThanOrEqual(1);
  });

  it('401 without a token', async () => {
    const res = await request(app).get('/api/admin/health');
    expect(res.status).toBe(401);
  });
});
