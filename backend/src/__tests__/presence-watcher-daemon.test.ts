import { pg, isPgEnabled } from '../db';
import { createUserAndProfile } from '../jobsService';
import { UserRole } from '../auth';
import { crewPositionService } from '../crewPositionService';
import { tick as presenceWatcherTick, __resetWarnedForTests } from '../daemons/presenceWatcherDaemon';
import { tick as auditFlushTick } from '../workers/auditFlushWorker';

const describeDb = isPgEnabled() ? describe : describe.skip;

async function cleanState() {
  if (!isPgEnabled()) return;
  await pg!.query('DELETE FROM crew_positions');
  await pg!.query('DELETE FROM shifts');
  await pg!.query(`DELETE FROM background_jobs WHERE kind='audit_flush'`);
  await pg!.query(`DELETE FROM audit_entries WHERE actor_id='presenceWatcherDaemon'`);
  __resetWarnedForTests();
}

async function makeUser(suffix: string): Promise<string> {
  const email = `pw-${suffix}-${Date.now()}-${Math.random().toString(36).slice(2, 6)}@example.com`;
  const user = await createUserAndProfile({ email, password: 'password123', displayName: `PW ${suffix}`, role: UserRole.SOLO });
  return user.id;
}

async function drainAuditFlush(timeoutMs = 3000): Promise<void> {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    const r = await pg!.query<{ count: string }>(
      `SELECT COUNT(*)::text AS count FROM background_jobs
        WHERE kind='audit_flush' AND state IN ('queued','running')`
    );
    if (parseInt(r.rows[0].count, 10) === 0) return;
    const did = await auditFlushTick('test-worker');
    if (!did) await new Promise((res) => setTimeout(res, 25));
  }
  throw new Error('audit_flush did not drain');
}

describeDb('presenceWatcherDaemon', () => {
  beforeEach(cleanState);
  afterAll(async () => { await pg?.end(); });

  it('emits stale_presence when an open shift has no recent GPS report', async () => {
    const uid = await makeUser('stale');
    await crewPositionService.startShift(uid, 'android');
    // Backdate the shift so the threshold (30 min) is crossed; no position rows.
    await pg!.query(`UPDATE shifts SET started_at = NOW() - INTERVAL '45 minutes' WHERE user_id=$1`, [uid]);

    await presenceWatcherTick();
    await drainAuditFlush();

    const rows = await pg!.query<{ metadata: any }>(
      `SELECT metadata FROM audit_entries WHERE actor_id='presenceWatcherDaemon' AND metadata->>'user_id'=$1`,
      [uid]
    );
    const events = rows.rows.map((r) => r.metadata.event);
    expect(events).toContain('stale_presence');
  });

  it('does NOT emit when GPS report is fresh', async () => {
    const uid = await makeUser('fresh');
    await crewPositionService.startShift(uid, 'android');
    await crewPositionService.upsertPosition(uid, { lat: 1, lng: 2 }, 'android');
    // Shift backdated, but GPS is recent.
    await pg!.query(`UPDATE shifts SET started_at = NOW() - INTERVAL '45 minutes' WHERE user_id=$1`, [uid]);

    await presenceWatcherTick();
    await drainAuditFlush();

    const rows = await pg!.query<{ c: string }>(
      `SELECT COUNT(*)::text AS c FROM audit_entries WHERE actor_id='presenceWatcherDaemon' AND metadata->>'user_id'=$1`,
      [uid]
    );
    expect(parseInt(rows.rows[0].c, 10)).toBe(0);
  });

  it('emits ultra_long_shift for an open shift older than 16h', async () => {
    const uid = await makeUser('long');
    await crewPositionService.startShift(uid, 'web');
    await pg!.query(`UPDATE shifts SET started_at = NOW() - INTERVAL '17 hours' WHERE user_id=$1`, [uid]);

    await presenceWatcherTick();
    await drainAuditFlush();

    const rows = await pg!.query<{ metadata: any }>(
      `SELECT metadata FROM audit_entries WHERE actor_id='presenceWatcherDaemon' AND metadata->>'user_id'=$1`,
      [uid]
    );
    const events = rows.rows.map((r) => r.metadata.event);
    expect(events).toContain('ultra_long_shift');
  });

  it('dedupes: same shift only audited once per daemon process', async () => {
    const uid = await makeUser('dedupe');
    await crewPositionService.startShift(uid, 'android');
    await pg!.query(`UPDATE shifts SET started_at = NOW() - INTERVAL '17 hours' WHERE user_id=$1`, [uid]);

    await presenceWatcherTick();
    await presenceWatcherTick();
    await presenceWatcherTick();
    await drainAuditFlush();

    const rows = await pg!.query<{ c: string }>(
      `SELECT COUNT(*)::text AS c FROM audit_entries
        WHERE actor_id='presenceWatcherDaemon'
          AND metadata->>'user_id'=$1
          AND metadata->>'event'='ultra_long_shift'`,
      [uid]
    );
    expect(parseInt(rows.rows[0].c, 10)).toBe(1);
  });

  it('does not emit for an ended shift', async () => {
    const uid = await makeUser('ended');
    await crewPositionService.startShift(uid, 'android');
    await pg!.query(`UPDATE shifts SET started_at = NOW() - INTERVAL '17 hours' WHERE user_id=$1`, [uid]);
    await crewPositionService.endShift(uid);

    await presenceWatcherTick();
    await drainAuditFlush();

    const rows = await pg!.query<{ c: string }>(
      `SELECT COUNT(*)::text AS c FROM audit_entries WHERE actor_id='presenceWatcherDaemon' AND metadata->>'user_id'=$1`,
      [uid]
    );
    expect(parseInt(rows.rows[0].c, 10)).toBe(0);
  });
});
