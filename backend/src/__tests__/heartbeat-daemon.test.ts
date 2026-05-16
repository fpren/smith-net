import { pg, isPgEnabled } from '../db';
import { tick as heartbeatTick } from '../daemons/heartbeatDaemon';

const describeDb = isPgEnabled() ? describe : describe.skip;

async function cleanHeartbeats() {
  if (!isPgEnabled()) return;
  await pg!.query(`DELETE FROM worker_heartbeats WHERE worker_id LIKE 'test-%'`);
}

describeDb('heartbeatDaemon', () => {
  beforeEach(cleanHeartbeats);
  afterAll(async () => { await pg?.end(); });

  it('inserts a heartbeat row on first tick', async () => {
    await heartbeatTick('test-worker-1', ['geocode', 'audit_flush']);
    const r = await pg!.query<{ worker_id: string; kinds: string[]; last_beat_at: Date }>(
      `SELECT worker_id, kinds, last_beat_at FROM worker_heartbeats WHERE worker_id=$1`,
      ['test-worker-1']
    );
    expect(r.rowCount).toBe(1);
    expect(r.rows[0].kinds).toEqual(['geocode', 'audit_flush']);
    expect(Date.now() - r.rows[0].last_beat_at.getTime()).toBeLessThan(2000);
  });

  it('updates last_beat_at on subsequent ticks (UPSERT)', async () => {
    await heartbeatTick('test-worker-2', ['email']);
    const first = await pg!.query<{ last_beat_at: Date; started_at: Date }>(
      `SELECT last_beat_at, started_at FROM worker_heartbeats WHERE worker_id=$1`,
      ['test-worker-2']
    );
    const firstStartedAt = first.rows[0].started_at;

    await new Promise((r) => setTimeout(r, 50));
    await heartbeatTick('test-worker-2', ['email']);
    const second = await pg!.query<{ last_beat_at: Date; started_at: Date }>(
      `SELECT last_beat_at, started_at FROM worker_heartbeats WHERE worker_id=$1`,
      ['test-worker-2']
    );
    expect(second.rows[0].last_beat_at.getTime()).toBeGreaterThan(first.rows[0].last_beat_at.getTime());
    expect(second.rows[0].started_at.getTime()).toBe(firstStartedAt.getTime());
  });

  it('updates kinds array if the set changes', async () => {
    await heartbeatTick('test-worker-3', ['geocode']);
    await heartbeatTick('test-worker-3', ['geocode', 'email']);
    const r = await pg!.query<{ kinds: string[] }>(
      `SELECT kinds FROM worker_heartbeats WHERE worker_id=$1`,
      ['test-worker-3']
    );
    expect(r.rows[0].kinds).toEqual(['geocode', 'email']);
  });
});
