import express from 'express';
import request from 'supertest';
import cookieParser from 'cookie-parser';
import { authRouter } from '../authRoutes';
import { jobsRouter } from '../jobsRoutes';
import { userStore, generateTokens, UserRole } from '../auth';
import { pg, isPgEnabled } from '../db';
import { __resetGeocoderState } from '../geocoder';

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
// userStore doesn't collide. ALSO inserts a matching row into the pg
// `profiles` table so jobs.foreman_id FK constraint is satisfied.
// Returns the access token.
async function createForemanAndLogin(suffix: string): Promise<{ id: string; token: string }> {
  const email = `foreman-jobs-${suffix}-${Date.now()}@example.com`;
  const user = await userStore.createUser(email, 'password123', `Foreman ${suffix}`, UserRole.FOREMAN);
  if (isPgEnabled() && pg) {
    await pg.query(
      `INSERT INTO profiles (id, email, display_name, role) VALUES ($1, $2, $3, $4)
       ON CONFLICT (id) DO NOTHING`,
      [user.id, user.email, user.displayName, 'foreman']
    );
  }
  const { accessToken } = await generateTokens(user);
  return { id: user.id, token: accessToken };
}

// Also expose a helper for creating crew members (TEAM_MEMBER) that get
// mirrored into the profiles table — the assign tests need this for FK.
async function createCrewProfile(suffix: string): Promise<{ id: string; email: string }> {
  const email = `crew-${suffix}-${Date.now()}@example.com`;
  const user = await userStore.createUser(email, 'password123', `Crew ${suffix}`, UserRole.TEAM_MEMBER);
  if (isPgEnabled() && pg) {
    await pg.query(
      `INSERT INTO profiles (id, email, display_name, role) VALUES ($1, $2, $3, $4)
       ON CONFLICT (id) DO NOTHING`,
      [user.id, user.email, user.displayName, 'team']
    );
  }
  return { id: user.id, email };
}

// Truncate the test data this suite produces so reruns are clean.
afterEach(async () => {
  if (!isPgEnabled() || !pg) return;
  await pg.query(`DELETE FROM job_crew`);
  await pg.query(`DELETE FROM jobs`);
  await pg.query(`DELETE FROM profiles WHERE email LIKE 'foreman-jobs-%' OR email LIKE 'crew-%'`);
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
    const { accessToken } = await generateTokens(user);
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

  it('latitude/longitude get populated by background geocode', async () => {
    // Reset geocoder rate-limit clock so we don't wait 1.1s from a prior test run.
    __resetGeocoderState();
    // Mock fetch globally so the geocoder returns coords without hitting Nominatim.
    const originalFetch = global.fetch;
    (global as any).fetch = jest.fn().mockResolvedValue({
      ok: true,
      status: 200,
      json: async () => [{ lat: '40.748817', lon: '-73.985428' }],
    });

    try {
      const f = await createForemanAndLogin('geocode-create');
      const res = await request(app)
        .post('/api/jobs')
        .set('Authorization', `Bearer ${f.token}`)
        .send({ title: 'Empire State smoke', location: 'Empire State Building, NYC' });
      expect(res.status).toBe(201);
      // Coords are null in the immediate response (async).
      expect(res.body.job.latitude).toBeNull();

      // Wait for the async geocode + UPDATE to complete.
      await new Promise((r) => setTimeout(r, 200));

      // Re-fetch — coords should be populated now.
      const got = await request(app)
        .get(`/api/jobs/${res.body.job.id}`)
        .set('Authorization', `Bearer ${f.token}`);
      expect(got.status).toBe(200);
      expect(got.body.job.latitude).toBeCloseTo(40.748817, 4);
      expect(got.body.job.longitude).toBeCloseTo(-73.985428, 4);
    } finally {
      global.fetch = originalFetch;
    }
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
    const crew = await createCrewProfile('a1');
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
    const crew = await createCrewProfile('dup');
    const created = await request(app).post('/api/jobs').set('Authorization', `Bearer ${f.token}`).send({ title: 'x' });
    const jobId = created.body.job.id;

    await request(app).post(`/api/jobs/${jobId}/assign`).set('Authorization', `Bearer ${f.token}`).send({ profileId: crew.id });
    const dup = await request(app).post(`/api/jobs/${jobId}/assign`).set('Authorization', `Bearer ${f.token}`).send({ profileId: crew.id });

    expect(dup.status).toBe(409);
    expect(dup.body.code).toBe('duplicate_assignment');
  });

  it('unassigns with 204', async () => {
    const f = await createForemanAndLogin('unassign');
    const crew = await createCrewProfile('un');
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
