import { pg, isPgEnabled } from '../db';
import { enqueue } from '../queue/queue';
import { tick } from '../workers/geocodeWorker';
import { createUserAndProfile } from '../jobsService';
import { UserRole } from '../auth';

const describeDb = isPgEnabled() ? describe : describe.skip;

// jobs.foreman_id references profiles(id), so the foreman must be created
// via the transactional createUserAndProfile path (users + profiles atomically).
let foremanId: string;

async function cleanJobs() {
  if (!isPgEnabled()) return;
  await pg!.query('TRUNCATE background_jobs RESTART IDENTITY');
  await pg!.query("DELETE FROM jobs WHERE title LIKE 'geocode-worker-test-%'");
}

async function insertJob(): Promise<string> {
  const { v4: uuidv4 } = require('uuid');
  const id = uuidv4();
  await pg!.query(
    `INSERT INTO jobs (id, foreman_id, title, status, created_at, updated_at)
     VALUES ($1::uuid, $2, 'geocode-worker-test-' || $1::text, 'planned', NOW(), NOW())`,
    [id, foremanId]
  );
  return id;
}

describeDb('geocodeWorker.tick', () => {
  let origFetch: typeof fetch;
  beforeAll(async () => {
    const user = await createUserAndProfile({
      email: `foreman-geocode-${Date.now()}@example.com`,
      password: 'password123',
      displayName: 'Geocode Foreman',
      role: UserRole.FOREMAN,
    });
    foremanId = user.id;
  });
  beforeEach(async () => {
    await cleanJobs();
    origFetch = global.fetch;
  });
  afterEach(() => { global.fetch = origFetch; });
  afterAll(async () => { await pg?.end(); });

  it('happy path: claims, geocodes, UPDATEs jobs row, completes', async () => {
    global.fetch = (async () => ({
      ok: true,
      status: 200,
      json: async () => [{ lat: '40.7484', lon: '-73.9856' }],
    })) as any;

    const jobId = await insertJob();
    await enqueue({
      kind: 'geocode',
      payload: { job_id: jobId, address: 'Empire State Building' },
      dedupeKey: `geocode:${jobId}`,
    });

    const did = await tick('w-test');
    expect(did).toBe(true);

    const updated = await pg!.query('SELECT latitude, longitude, geocoded_at FROM jobs WHERE id = $1', [jobId]);
    expect(parseFloat(updated.rows[0].latitude)).toBeCloseTo(40.7484, 3);
    expect(parseFloat(updated.rows[0].longitude)).toBeCloseTo(-73.9856, 3);
    expect(updated.rows[0].geocoded_at).toBeTruthy();

    const jobRow = await pg!.query("SELECT state FROM background_jobs WHERE kind='geocode'");
    expect(jobRow.rows[0].state).toBe('succeeded');
  });

  it('returns false when no jobs to claim', async () => {
    const did = await tick('w-test');
    expect(did).toBe(false);
  });

  it('503 from Nominatim: marks failed with retry scheduled', async () => {
    global.fetch = (async () => ({
      ok: false, status: 503, json: async () => null,
    })) as any;

    const jobId = await insertJob();
    await enqueue({ kind: 'geocode', payload: { job_id: jobId, address: 'x' } });

    await tick('w-test');
    const row = await pg!.query("SELECT state, last_error FROM background_jobs WHERE kind='geocode'");
    expect(row.rows[0].state).toBe('failed');
    expect(row.rows[0].last_error).toMatch(/503/);
  });

  it('empty Nominatim result: marks dead immediately (no point retrying) with maxAttempts=1', async () => {
    global.fetch = (async () => ({
      ok: true, status: 200, json: async () => [],
    })) as any;

    const jobId = await insertJob();
    await enqueue({ kind: 'geocode', payload: { job_id: jobId, address: 'fakeville' }, maxAttempts: 1 });

    await tick('w-test');
    const row = await pg!.query("SELECT state, last_error FROM background_jobs WHERE kind='geocode'");
    expect(row.rows[0].state).toBe('dead');
    expect(row.rows[0].last_error).toMatch(/no_match|no result|empty/i);
  });
});
