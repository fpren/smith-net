import { pg, isPgEnabled } from '../db';
import { enqueue } from '../queue/queue';
import { tick as cleanupTick } from '../daemons/cleanupDaemon';

const describeDb = isPgEnabled() ? describe : describe.skip;

async function cleanQueue() {
  if (!isPgEnabled()) return;
  await pg!.query(`DELETE FROM background_jobs WHERE kind='test_cleanup'`);
}

describeDb('cleanupDaemon', () => {
  beforeEach(cleanQueue);
  afterAll(async () => { await pg?.end(); });

  it('deletes dead rows older than 30 days; keeps newer dead rows', async () => {
    const old = await enqueue({ kind: 'test_cleanup' as any, payload: { tag: 'old' } });
    const recent = await enqueue({ kind: 'test_cleanup' as any, payload: { tag: 'recent' } });
    await pg!.query(
      `UPDATE background_jobs SET state='dead', finished_at = NOW() - INTERVAL '31 days' WHERE id=$1`,
      [old.id]
    );
    await pg!.query(
      `UPDATE background_jobs SET state='dead', finished_at = NOW() - INTERVAL '5 days' WHERE id=$1`,
      [recent.id]
    );

    await cleanupTick();

    const r = await pg!.query<{ id: string; tag: string }>(
      `SELECT id, (payload->>'tag') AS tag FROM background_jobs WHERE kind='test_cleanup' ORDER BY id`
    );
    expect(r.rowCount).toBe(1);
    expect(r.rows[0].tag).toBe('recent');
  });

  it('does NOT delete non-dead old rows', async () => {
    const enq = await enqueue({ kind: 'test_cleanup' as any, payload: { tag: 'queued' } });
    await pg!.query(
      `UPDATE background_jobs SET finished_at = NOW() - INTERVAL '99 days' WHERE id=$1`,
      [enq.id]
    );
    await cleanupTick();
    const r = await pg!.query<{ count: string }>(
      `SELECT COUNT(*)::text AS count FROM background_jobs WHERE id=$1`,
      [enq.id]
    );
    expect(parseInt(r.rows[0].count, 10)).toBe(1);
  });
});
