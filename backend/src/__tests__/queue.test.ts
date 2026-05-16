import { pg, isPgEnabled } from '../db';
import { enqueue, claimNext, complete } from '../queue/queue';

const describeDb = isPgEnabled() ? describe : describe.skip;

async function cleanJobs() {
  if (!isPgEnabled()) return;
  await pg!.query('TRUNCATE background_jobs RESTART IDENTITY');
}

describeDb('queue.enqueue', () => {
  beforeEach(cleanJobs);

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

describeDb('queue.claimNext + complete', () => {
  beforeEach(cleanJobs);
  afterAll(async () => { await pg?.end(); });

  it('claims oldest queued row matching kind; sets state=running, locked_by, attempts+1', async () => {
    const a = await enqueue({ kind: 'geocode', payload: { x: 1 } });
    await new Promise(r => setTimeout(r, 10));
    await enqueue({ kind: 'geocode', payload: { x: 2 } });

    const claimed = await claimNext('geocode', 'worker-A');
    expect(claimed?.id).toBe(a.id);
    expect(claimed?.attempts).toBe(1);
    expect(claimed?.payload).toEqual({ x: 1 });

    const row = await pg!.query('SELECT state, locked_by, locked_at FROM background_jobs WHERE id = $1', [a.id]);
    expect(row.rows[0].state).toBe('running');
    expect(row.rows[0].locked_by).toBe('worker-A');
    expect(row.rows[0].locked_at).toBeTruthy();
  });

  it('returns null when no queued rows for that kind', async () => {
    const claimed = await claimNext('email', 'worker-A');
    expect(claimed).toBeNull();
  });

  it('skips jobs with scheduled_at in the future', async () => {
    await enqueue({ kind: 'geocode', payload: {}, scheduledAt: new Date(Date.now() + 60_000) });
    const claimed = await claimNext('geocode', 'worker-A');
    expect(claimed).toBeNull();
  });

  it('two concurrent claims return different jobs (SKIP LOCKED)', async () => {
    await enqueue({ kind: 'geocode', payload: { x: 1 } });
    await new Promise(r => setTimeout(r, 10));
    await enqueue({ kind: 'geocode', payload: { x: 2 } });

    const [a, b] = await Promise.all([
      claimNext('geocode', 'worker-A'),
      claimNext('geocode', 'worker-B'),
    ]);
    expect(a?.id).not.toBe(b?.id);
    expect(a?.id).toBeTruthy();
    expect(b?.id).toBeTruthy();
  });

  it('complete() moves running -> succeeded with finished_at set', async () => {
    const e = await enqueue({ kind: 'geocode', payload: {} });
    const c = await claimNext('geocode', 'worker-A');
    await complete(c!.id);
    const row = await pg!.query('SELECT state, finished_at FROM background_jobs WHERE id = $1', [e.id]);
    expect(row.rows[0].state).toBe('succeeded');
    expect(row.rows[0].finished_at).toBeTruthy();
  });
});
