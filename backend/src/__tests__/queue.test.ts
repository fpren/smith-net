import { pg, isPgEnabled } from '../db';
import { enqueue } from '../queue/queue';

const describeDb = isPgEnabled() ? describe : describe.skip;

async function cleanJobs() {
  if (!isPgEnabled()) return;
  await pg!.query('TRUNCATE background_jobs RESTART IDENTITY');
}

describeDb('queue.enqueue', () => {
  beforeEach(cleanJobs);
  afterAll(async () => { await pg?.end(); });

  it('inserts a row with state=queued, attempts=0', async () => {
    const r = await enqueue({ kind: 'geocode', payload: { job_id: 'j-1', address: 'NYC' } });
    expect(r.created).toBe(true);
    expect(r.id).toBeGreaterThan(0);

    const rows = await pg!.query('SELECT kind, state, attempts, payload, max_attempts FROM background_jobs WHERE id = $1', [r.id]);
    expect(rows.rowCount).toBe(1);
    expect(rows.rows[0].kind).toBe('geocode');
    expect(rows.rows[0].state).toBe('queued');
    expect(rows.rows[0].attempts).toBe(0);
    expect(rows.rows[0].max_attempts).toBe(5);
    expect(rows.rows[0].payload).toEqual({ job_id: 'j-1', address: 'NYC' });
  });

  it('respects dedupeKey: same key twice yields one row, second returns created=false', async () => {
    const a = await enqueue({ kind: 'geocode', payload: { x: 1 }, dedupeKey: 'geocode:abc' });
    const b = await enqueue({ kind: 'geocode', payload: { x: 2 }, dedupeKey: 'geocode:abc' });
    expect(a.created).toBe(true);
    expect(b.created).toBe(false);
    expect(b.id).toBe(-1);

    const count = await pg!.query("SELECT COUNT(*) FROM background_jobs WHERE dedupe_key = 'geocode:abc'");
    expect(parseInt(count.rows[0].count, 10)).toBe(1);
  });

  it('respects scheduledAt for delayed jobs', async () => {
    const future = new Date(Date.now() + 60_000);
    const r = await enqueue({ kind: 'geocode', payload: {}, scheduledAt: future });
    const row = await pg!.query('SELECT scheduled_at FROM background_jobs WHERE id = $1', [r.id]);
    const got = (row.rows[0].scheduled_at as Date).getTime();
    expect(Math.abs(got - future.getTime())).toBeLessThan(1000);
  });

  it('honors maxAttempts override', async () => {
    const r = await enqueue({ kind: 'geocode', payload: {}, maxAttempts: 10 });
    const row = await pg!.query('SELECT max_attempts FROM background_jobs WHERE id = $1', [r.id]);
    expect(row.rows[0].max_attempts).toBe(10);
  });
});
