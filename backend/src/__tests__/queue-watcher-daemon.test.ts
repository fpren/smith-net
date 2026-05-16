import { pg, isPgEnabled } from '../db';
import { enqueue } from '../queue/queue';
import { tick as queueWatcherTick } from '../daemons/queueWatcherDaemon';

const describeDb = isPgEnabled() ? describe : describe.skip;

async function cleanQueue() {
  if (!isPgEnabled()) return;
  await pg!.query(`DELETE FROM background_jobs WHERE kind='geocode'`);
}

describeDb('queueWatcherDaemon', () => {
  beforeEach(cleanQueue);
  afterAll(async () => { await pg?.end(); });

  it('resets a row whose locked_at is > 10 minutes old back to queued', async () => {
    const enq = await enqueue({ kind: 'geocode', payload: { job_id: 'j1', address: 'x' } });
    await pg!.query(
      `UPDATE background_jobs SET state='running', locked_at = NOW() - INTERVAL '11 minutes', locked_by='dead-worker' WHERE id=$1`,
      [enq.id]
    );

    await queueWatcherTick();

    const r = await pg!.query<{ state: string; locked_at: Date | null; locked_by: string | null }>(
      `SELECT state, locked_at, locked_by FROM background_jobs WHERE id=$1`,
      [enq.id]
    );
    expect(r.rows[0].state).toBe('queued');
    expect(r.rows[0].locked_at).toBeNull();
    expect(r.rows[0].locked_by).toBeNull();
  });

  it('leaves recently-locked running rows alone', async () => {
    const enq = await enqueue({ kind: 'geocode', payload: { job_id: 'j2', address: 'y' } });
    await pg!.query(
      `UPDATE background_jobs SET state='running', locked_at = NOW() - INTERVAL '1 minute', locked_by='live-worker' WHERE id=$1`,
      [enq.id]
    );

    await queueWatcherTick();

    const r = await pg!.query<{ state: string; locked_by: string | null }>(
      `SELECT state, locked_by FROM background_jobs WHERE id=$1`,
      [enq.id]
    );
    expect(r.rows[0].state).toBe('running');
    expect(r.rows[0].locked_by).toBe('live-worker');
  });
});
