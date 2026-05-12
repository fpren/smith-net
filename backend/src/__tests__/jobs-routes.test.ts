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

describeDb('POST /api/jobs', () => {
  const app = buildApp();

  it('creates a job with status="planned" and foreman_id from req.user', async () => {
    const f = await createForemanAndLogin('create');
    const res = await request(app)
      .post('/api/jobs')
      .set('Authorization', `Bearer ${f.token}`)
      .send({ title: 'Install panel', location: '123 Main St' });
    expect(res.status).toBe(201);
    expect(res.body.job.title).toBe('Install panel');
    expect(res.body.job.location).toBe('123 Main St');
    expect(res.body.job.status).toBe('planned');
    expect(res.body.job.foremanId).toBe(f.id);
    expect(res.body.job.id).toBeDefined();
  });

  it('rejects empty title with 400', async () => {
    const f = await createForemanAndLogin('create-bad');
    const res = await request(app)
      .post('/api/jobs')
      .set('Authorization', `Bearer ${f.token}`)
      .send({ title: '' });
    expect(res.status).toBe(400);
  });

  it('rejects extra fields (strict schema)', async () => {
    const f = await createForemanAndLogin('create-strict');
    const res = await request(app)
      .post('/api/jobs')
      .set('Authorization', `Bearer ${f.token}`)
      .send({ title: 'x', foremanId: 'spoofed', status: 'complete' });
    expect(res.status).toBe(400);
  });
});

describeDb('PATCH /api/jobs/:id', () => {
  const app = buildApp();

  it('updates title + location, leaves other fields untouched', async () => {
    const f = await createForemanAndLogin('update');
    const created = await request(app)
      .post('/api/jobs')
      .set('Authorization', `Bearer ${f.token}`)
      .send({ title: 'orig', location: 'A' });
    const jobId = created.body.job.id;

    const res = await request(app)
      .patch(`/api/jobs/${jobId}`)
      .set('Authorization', `Bearer ${f.token}`)
      .send({ title: 'updated', location: 'B' });

    expect(res.status).toBe(200);
    expect(res.body.job.title).toBe('updated');
    expect(res.body.job.location).toBe('B');
    expect(res.body.job.status).toBe('planned');
  });

  it('returns 403 not_owner when another foreman patches', async () => {
    const a = await createForemanAndLogin('update-a');
    const b = await createForemanAndLogin('update-b');
    const created = await request(app)
      .post('/api/jobs')
      .set('Authorization', `Bearer ${a.token}`)
      .send({ title: 'a-job' });
    const jobId = created.body.job.id;

    const res = await request(app)
      .patch(`/api/jobs/${jobId}`)
      .set('Authorization', `Bearer ${b.token}`)
      .send({ title: 'hijacked' });

    expect(res.status).toBe(403);
    expect(res.body.code).toBe('not_owner');
  });

  it('rejects `status` in patch body (status has its own endpoint)', async () => {
    const f = await createForemanAndLogin('update-no-status');
    const created = await request(app)
      .post('/api/jobs')
      .set('Authorization', `Bearer ${f.token}`)
      .send({ title: 'x' });
    const jobId = created.body.job.id;

    const res = await request(app)
      .patch(`/api/jobs/${jobId}`)
      .set('Authorization', `Bearer ${f.token}`)
      .send({ status: 'complete' });

    expect(res.status).toBe(400);
  });
});

describeDb('PATCH /api/jobs/:id/status', () => {
  const app = buildApp();

  it('allows planned -> in_progress', async () => {
    const f = await createForemanAndLogin('status-start');
    const created = await request(app)
      .post('/api/jobs')
      .set('Authorization', `Bearer ${f.token}`)
      .send({ title: 'x' });
    const jobId = created.body.job.id;

    const res = await request(app)
      .patch(`/api/jobs/${jobId}/status`)
      .set('Authorization', `Bearer ${f.token}`)
      .send({ status: 'in_progress' });

    expect(res.status).toBe(200);
    expect(res.body.job.status).toBe('in_progress');
  });

  it('rejects complete -> planned with invalid_status_transition', async () => {
    const f = await createForemanAndLogin('status-bad');
    const created = await request(app)
      .post('/api/jobs')
      .set('Authorization', `Bearer ${f.token}`)
      .send({ title: 'x' });
    const jobId = created.body.job.id;
    await request(app).patch(`/api/jobs/${jobId}/status`).set('Authorization', `Bearer ${f.token}`).send({ status: 'in_progress' });
    await request(app).patch(`/api/jobs/${jobId}/status`).set('Authorization', `Bearer ${f.token}`).send({ status: 'complete' });

    const res = await request(app)
      .patch(`/api/jobs/${jobId}/status`)
      .set('Authorization', `Bearer ${f.token}`)
      .send({ status: 'planned' });

    expect(res.status).toBe(400);
    expect(res.body.code).toBe('invalid_status_transition');
    expect(res.body.from).toBe('complete');
    expect(res.body.to).toBe('planned');
  });

  it('rejects unknown status value with zod 400', async () => {
    const f = await createForemanAndLogin('status-unknown');
    const created = await request(app)
      .post('/api/jobs')
      .set('Authorization', `Bearer ${f.token}`)
      .send({ title: 'x' });

    const res = await request(app)
      .patch(`/api/jobs/${created.body.job.id}/status`)
      .set('Authorization', `Bearer ${f.token}`)
      .send({ status: 'in-orbit' });

    expect(res.status).toBe(400);
    expect(res.body.code).toBe('validation');
  });
});

describeDb('POST /api/jobs/:id/assign + DELETE /api/jobs/:id/assign/:profileId', () => {
  const app = buildApp();

  it('assigns crew member then lists them on GET', async () => {
    const f = await createForemanAndLogin('assign-1');
    const crew = await userStore.createUser(`crew-${Date.now()}@example.com`, 'password123', 'C', UserRole.TEAM_MEMBER);
    const created = await request(app).post('/api/jobs').set('Authorization', `Bearer ${f.token}`).send({ title: 'x' });
    const jobId = created.body.job.id;

    const assign = await request(app)
      .post(`/api/jobs/${jobId}/assign`)
      .set('Authorization', `Bearer ${f.token}`)
      .send({ profileId: crew.id, roleOnJob: 'lead' });
    expect(assign.status).toBe(201);
    expect(assign.body.assignment.profileId).toBe(crew.id);
    expect(assign.body.assignment.roleOnJob).toBe('lead');

    const fetched = await request(app).get(`/api/jobs/${jobId}`).set('Authorization', `Bearer ${f.token}`);
    expect(fetched.status).toBe(200);
    expect(fetched.body.crew).toHaveLength(1);
    expect(fetched.body.crew[0].profileId).toBe(crew.id);
  });

  it('rejects duplicate assignment with 409 duplicate_assignment', async () => {
    const f = await createForemanAndLogin('assign-dup');
    const crew = await userStore.createUser(`crew-dup-${Date.now()}@example.com`, 'password123', 'C', UserRole.TEAM_MEMBER);
    const created = await request(app).post('/api/jobs').set('Authorization', `Bearer ${f.token}`).send({ title: 'x' });
    const jobId = created.body.job.id;

    await request(app).post(`/api/jobs/${jobId}/assign`).set('Authorization', `Bearer ${f.token}`).send({ profileId: crew.id });
    const dup = await request(app).post(`/api/jobs/${jobId}/assign`).set('Authorization', `Bearer ${f.token}`).send({ profileId: crew.id });

    expect(dup.status).toBe(409);
    expect(dup.body.code).toBe('duplicate_assignment');
  });

  it('unassigns with 204', async () => {
    const f = await createForemanAndLogin('unassign');
    const crew = await userStore.createUser(`crew-unassign-${Date.now()}@example.com`, 'password123', 'C', UserRole.TEAM_MEMBER);
    const created = await request(app).post('/api/jobs').set('Authorization', `Bearer ${f.token}`).send({ title: 'x' });
    const jobId = created.body.job.id;

    await request(app).post(`/api/jobs/${jobId}/assign`).set('Authorization', `Bearer ${f.token}`).send({ profileId: crew.id });

    const del = await request(app)
      .delete(`/api/jobs/${jobId}/assign/${crew.id}`)
      .set('Authorization', `Bearer ${f.token}`);
    expect(del.status).toBe(204);

    const fetched = await request(app).get(`/api/jobs/${jobId}`).set('Authorization', `Bearer ${f.token}`);
    expect(fetched.body.crew).toHaveLength(0);
  });
});
