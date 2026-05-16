import { pg, isPgEnabled } from '../db';
import { enqueue, claimNext, fail } from '../queue/queue';

const describeDb = isPgEnabled() ? describe : describe.skip;

async function cleanJobs() {
  if (!isPgEnabled()) return;
  await pg!.query('TRUNCATE background_jobs RESTART IDENTITY');
}

describeDb('queue.fail + backoff', () => {
  beforeEach(cleanJobs);
  afterAll(async () => { await pg?.end(); });

  it('fail with attempts < max moves to state=failed and schedules retry', async () => {
    const e = await enqueue({ kind: 'geocode', payload: {} });
    const c = await claimNext('geocode', 'w');
    await fail(c!.id, new Error('boom'), { attempts: c!.attempts, maxAttempts: c!.max_attempts });

    const row = await pg!.query(
      `SELECT state, last_error, scheduled_at, locked_at, locked_by FROM background_jobs WHERE id = $1`,
      [e.id]
    );
    expect(row.rows[0].state).toBe('failed');
    expect(row.rows[0].last_error).toBe('boom');
    expect(row.rows[0].locked_at).toBeNull();
    expect(row.rows[0].locked_by).toBeNull();
    // First-attempt backoff: 60 * 3^1 = 180s. Allow 30s clock slop.
    const scheduledIn = (row.rows[0].scheduled_at as Date).getTime() - Date.now();
    expect(scheduledIn).toBeGreaterThan(150_000);
    expect(scheduledIn).toBeLessThan(210_000);
  });

  it('fail with attempts >= max moves to state=dead with finished_at', async () => {
    const e = await enqueue({ kind: 'geocode', payload: {}, maxAttempts: 2 });
    // Burn 2 attempts
    for (let i = 0; i < 2; i++) {
      const c = await claimNext('geocode', 'w');
      await fail(c!.id, new Error('e' + i), { attempts: c!.attempts, maxAttempts: c!.max_attempts });
      if (i < 1) {
        await pg!.query(`UPDATE background_jobs SET state='queued', scheduled_at=NOW() WHERE id=$1`, [e.id]);
      }
    }

    const row = await pg!.query('SELECT state, finished_at FROM background_jobs WHERE id = $1', [e.id]);
    expect(row.rows[0].state).toBe('dead');
    expect(row.rows[0].finished_at).toBeTruthy();
  });

  it('backoff formula: min(60 * 3^attempts, 6h)', async () => {
    // Synthetic row with attempts already high — verify backoff caps at 6h.
    await pg!.query(`
      INSERT INTO background_jobs (kind, payload, state, attempts, max_attempts, locked_at, locked_by)
      VALUES ('geocode', '{}'::jsonb, 'running', 10, 99, NOW(), 'w')
    `);
    const { rows } = await pg!.query<{ id: string }>(`SELECT id FROM background_jobs ORDER BY id DESC LIMIT 1`);
    const jobId = Number(rows[0].id);
    await fail(jobId, new Error('cap'), { attempts: 10, maxAttempts: 99 });

    const row = await pg!.query(`SELECT scheduled_at FROM background_jobs WHERE id = $1`, [jobId]);
    const scheduledIn = (row.rows[0].scheduled_at as Date).getTime() - Date.now();
    expect(scheduledIn).toBeLessThanOrEqual(6 * 3600 * 1000 + 5_000); // 6h + tolerance
    expect(scheduledIn).toBeGreaterThan(6 * 3600 * 1000 - 5_000);
  });

  it('truncates last_error to 1000 chars', async () => {
    const e = await enqueue({ kind: 'geocode', payload: {} });
    const c = await claimNext('geocode', 'w');
    const huge = 'x'.repeat(2000);
    await fail(c!.id, new Error(huge), { attempts: c!.attempts, maxAttempts: c!.max_attempts });
    const row = await pg!.query('SELECT last_error FROM background_jobs WHERE id = $1', [e.id]);
    expect(row.rows[0].last_error.length).toBe(1000);
  });
});
