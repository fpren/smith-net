import express from 'express';
import request from 'supertest';
import cookieParser from 'cookie-parser';
import { authRouter } from '../authRoutes';
import { profilesRouter } from '../profilesRoutes';
import { jobsRouter } from '../jobsRoutes';
import { userStore, generateTokens, UserRole } from '../auth';
import { pg, isPgEnabled } from '../db';

const describeDb = isPgEnabled() ? describe : describe.skip;

function buildApp() {
  const app = express();
  app.use(express.json());
  app.use(cookieParser());
  app.use('/api/auth', authRouter);
  app.use('/api/profiles', profilesRouter);
  app.use('/api/jobs', jobsRouter);
  return app;
}

async function createForeman(suffix: string) {
  const email = `foreman-crew-${suffix}-${Date.now()}@example.com`;
  const user = await userStore.createUser(email, 'password123', `Foreman ${suffix}`, UserRole.FOREMAN);
  if (isPgEnabled() && pg) {
    await pg.query(
      `INSERT INTO profiles (id, email, display_name, role) VALUES ($1, $2, $3, $4) ON CONFLICT (id) DO NOTHING`,
      [user.id, user.email, user.displayName, 'foreman']
    );
  }
  return { id: user.id, token: (await generateTokens(user)).accessToken };
}

async function createCrew(suffix: string) {
  const email = `crewroster-${suffix}-${Date.now()}@example.com`;
  const user = await userStore.createUser(email, 'password123', `Crew ${suffix}`, UserRole.TEAM_MEMBER);
  if (isPgEnabled() && pg) {
    await pg.query(
      `INSERT INTO profiles (id, email, display_name, role) VALUES ($1, $2, $3, $4) ON CONFLICT (id) DO NOTHING`,
      [user.id, user.email, user.displayName, 'team']
    );
  }
  return { id: user.id, email };
}

afterEach(async () => {
  if (!isPgEnabled() || !pg) return;
  await pg.query(`DELETE FROM job_crew`);
  await pg.query(`DELETE FROM jobs WHERE title LIKE 'crew-roster%'`);
  await pg.query(`DELETE FROM profiles WHERE email LIKE 'foreman-crew-%' OR email LIKE 'crewroster-%'`);
});

describe('GET /api/profiles/crew — auth gates', () => {
  const app = buildApp();

  it('returns 401 with no auth', async () => {
    const res = await request(app).get('/api/profiles/crew');
    expect(res.status).toBe(401);
  });

  it('returns 403 tier_required for Solo user', async () => {
    const u = await userStore.createUser(`solo-roster-${Date.now()}@example.com`, 'password123', 'S', UserRole.SOLO);
    const { accessToken } = await generateTokens(u);
    const res = await request(app).get('/api/profiles/crew').set('Authorization', `Bearer ${accessToken}`);
    expect(res.status).toBe(403);
    expect(res.body.code).toBe('tier_required');
  });
});

describeDb('GET /api/profiles/crew — roster derivation', () => {
  const app = buildApp();

  it('returns empty roster when the foreman has no crew assignments', async () => {
    const f = await createForeman('empty');
    const res = await request(app).get('/api/profiles/crew').set('Authorization', `Bearer ${f.token}`);
    expect(res.status).toBe(200);
    expect(res.body.crew).toEqual([]);
  });

  it('returns assigned crew with activeJob=null when no in-progress job', async () => {
    const f = await createForeman('idle');
    const c = await createCrew('idle');
    // Create a planned job, assign crew, leave it planned.
    const created = await request(app).post('/api/jobs').set('Authorization', `Bearer ${f.token}`).send({ title: 'crew-roster-idle' });
    await request(app).post(`/api/jobs/${created.body.job.id}/assign`).set('Authorization', `Bearer ${f.token}`).send({ profileId: c.id });

    const res = await request(app).get('/api/profiles/crew').set('Authorization', `Bearer ${f.token}`);
    expect(res.status).toBe(200);
    expect(res.body.crew.length).toBe(1);
    expect(res.body.crew[0].id).toBe(c.id);
    expect(res.body.crew[0].activeJob).toBeNull();
  });

  it('returns assigned crew with activeJob populated when assigned to in_progress job', async () => {
    const f = await createForeman('busy');
    const c = await createCrew('busy');
    const created = await request(app).post('/api/jobs').set('Authorization', `Bearer ${f.token}`).send({ title: 'crew-roster-busy' });
    const jobId = created.body.job.id;
    await request(app).post(`/api/jobs/${jobId}/assign`).set('Authorization', `Bearer ${f.token}`).send({ profileId: c.id });
    await request(app).patch(`/api/jobs/${jobId}/status`).set('Authorization', `Bearer ${f.token}`).send({ status: 'in_progress' });

    const res = await request(app).get('/api/profiles/crew').set('Authorization', `Bearer ${f.token}`);
    expect(res.status).toBe(200);
    expect(res.body.crew.length).toBe(1);
    expect(res.body.crew[0].activeJob).not.toBeNull();
    expect(res.body.crew[0].activeJob.id).toBe(jobId);
    expect(res.body.crew[0].activeJob.status).toBe('in_progress');
  });

  it('does NOT leak crew from another foreman', async () => {
    const fA = await createForeman('iso-A');
    const fB = await createForeman('iso-B');
    const c = await createCrew('iso-shared');
    const createdA = await request(app).post('/api/jobs').set('Authorization', `Bearer ${fA.token}`).send({ title: 'crew-roster-iso-A' });
    await request(app).post(`/api/jobs/${createdA.body.job.id}/assign`).set('Authorization', `Bearer ${fA.token}`).send({ profileId: c.id });

    const res = await request(app).get('/api/profiles/crew').set('Authorization', `Bearer ${fB.token}`);
    expect(res.status).toBe(200);
    expect(res.body.crew).toEqual([]);
  });
});
