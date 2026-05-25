import express from 'express';
import request from 'supertest';
import cookieParser from 'cookie-parser';
import { authRouter } from '../authRoutes';
import { shiftsRouter } from '../shiftsRoutes';
import { createUserAndProfile, create as createJob, assignCrew } from '../jobsService';
import { create as createTask } from '../tasksService';
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

describeDb('shifts clock-scoped job/task reads (all-tier)', () => {
  let app: express.Express;
  beforeAll(() => { app = buildApp(); });
  beforeEach(cleanShifts);
  afterAll(async () => {
    if (!isPgEnabled()) return;
    await cleanShifts();
    await pg!.query('DELETE FROM tasks');
    await pg!.query('DELETE FROM job_crew');
    await pg!.query('DELETE FROM jobs');
  });

  it('start persists + serializes taskId/taskTitle (camelCase)', async () => {
    const owner = await makeUserWithToken(UserRole.FOREMAN, 'tk-own');
    const job = await createJob({ foremanId: owner.id, title: 'Roof Job' });
    const task = await createTask({ jobId: job.id, title: 'Strip shingles', createdBy: owner.id });

    const res = await request(app)
      .post('/api/shifts/start')
      .set('Authorization', `Bearer ${owner.token}`)
      .send({ source: 'web', jobId: job.id, jobTitle: job.title, taskId: task.id, taskTitle: task.title });
    expect(res.status).toBe(200);
    expect(res.body.shift.jobId).toBe(job.id);
    expect(res.body.shift.taskId).toBe(task.id);
    expect(res.body.shift.taskTitle).toBe('Strip shingles');
  });

  it('start with no task leaves taskId/taskTitle null', async () => {
    const u = await makeUserWithToken(UserRole.SOLO, 'tk-null');
    const res = await request(app)
      .post('/api/shifts/start')
      .set('Authorization', `Bearer ${u.token}`)
      .send({ source: 'web' });
    expect(res.status).toBe(200);
    expect(res.body.shift.taskId).toBeNull();
    expect(res.body.shift.taskTitle).toBeNull();
  });

  it('start with a bad taskId returns 400 (FK violation)', async () => {
    const u = await makeUserWithToken(UserRole.SOLO, 'tk-badfk');
    const res = await request(app)
      .post('/api/shifts/start')
      .set('Authorization', `Bearer ${u.token}`)
      .send({ source: 'web', taskId: '00000000-0000-0000-0000-000000000000' });
    expect(res.status).toBe(400);
  });

  it('GET /api/shifts/jobs returns OWNED + ASSIGNED jobs (all-tier, not 403)', async () => {
    const me = await makeUserWithToken(UserRole.SOLO, 'jobs-me');
    const foreman = await makeUserWithToken(UserRole.FOREMAN, 'jobs-fm');

    const ownedJob = await createJob({ foremanId: me.id, title: 'My Owned Job' });
    const assignedJob = await createJob({ foremanId: foreman.id, title: 'Assigned Job' });
    await assignCrew(assignedJob.id, me.id);

    const res = await request(app)
      .get('/api/shifts/jobs')
      .set('Authorization', `Bearer ${me.token}`);
    expect(res.status).toBe(200); // NOT 403 — un-gated for solo
    const ids = res.body.jobs.map((j: { id: string }) => j.id).sort();
    expect(ids).toEqual([ownedJob.id, assignedJob.id].sort());
    const owned = res.body.jobs.find((j: { id: string }) => j.id === ownedJob.id);
    expect(owned.title).toBe('My Owned Job');
    expect(owned.status).toBe('planned');
  });

  it('GET /api/shifts/jobs returns [] for a solo user with no jobs (and is not 403)', async () => {
    const lonely = await makeUserWithToken(UserRole.SOLO, 'jobs-none');
    const res = await request(app)
      .get('/api/shifts/jobs')
      .set('Authorization', `Bearer ${lonely.token}`);
    expect(res.status).toBe(200);
    expect(res.body.jobs).toEqual([]);
  });

  it('GET /api/shifts/jobs does NOT leak another user\'s unrelated job (isolation)', async () => {
    const me = await makeUserWithToken(UserRole.SOLO, 'jobs-iso-me');
    const other = await makeUserWithToken(UserRole.FOREMAN, 'jobs-iso-other');
    await createJob({ foremanId: other.id, title: 'Not Mine' });

    const res = await request(app)
      .get('/api/shifts/jobs')
      .set('Authorization', `Bearer ${me.token}`);
    expect(res.status).toBe(200);
    expect(res.body.jobs).toEqual([]);
  });

  it('GET /api/shifts/jobs/:jobId/tasks returns tasks for an owner', async () => {
    const owner = await makeUserWithToken(UserRole.FOREMAN, 'tasks-own');
    const job = await createJob({ foremanId: owner.id, title: 'Task Owner Job' });
    const t1 = await createTask({ jobId: job.id, title: 'Task A', createdBy: owner.id });

    const res = await request(app)
      .get(`/api/shifts/jobs/${job.id}/tasks`)
      .set('Authorization', `Bearer ${owner.token}`);
    expect(res.status).toBe(200);
    expect(res.body.tasks.map((t: { id: string }) => t.id)).toContain(t1.id);
    expect(res.body.tasks[0].title).toBe('Task A');
    expect(res.body.tasks[0].status).toBe('pending');
  });

  it('GET /api/shifts/jobs/:jobId/tasks returns tasks for an assignee', async () => {
    const me = await makeUserWithToken(UserRole.SOLO, 'tasks-assignee');
    const foreman = await makeUserWithToken(UserRole.FOREMAN, 'tasks-fm');
    const job = await createJob({ foremanId: foreman.id, title: 'Assignee Tasks Job' });
    await assignCrew(job.id, me.id);
    const t1 = await createTask({ jobId: job.id, title: 'Crew Task', createdBy: foreman.id });

    const res = await request(app)
      .get(`/api/shifts/jobs/${job.id}/tasks`)
      .set('Authorization', `Bearer ${me.token}`);
    expect(res.status).toBe(200);
    expect(res.body.tasks.map((t: { id: string }) => t.id)).toContain(t1.id);
  });

  it('GET /api/shifts/jobs/:jobId/tasks returns 404 for a user with no access', async () => {
    const me = await makeUserWithToken(UserRole.SOLO, 'tasks-noaccess');
    const other = await makeUserWithToken(UserRole.FOREMAN, 'tasks-other');
    const job = await createJob({ foremanId: other.id, title: 'Forbidden Job' });
    await createTask({ jobId: job.id, title: 'Secret Task', createdBy: other.id });

    const res = await request(app)
      .get(`/api/shifts/jobs/${job.id}/tasks`)
      .set('Authorization', `Bearer ${me.token}`);
    expect(res.status).toBe(404);
  });
});

afterAll(async () => {
  await pg?.end();
});
