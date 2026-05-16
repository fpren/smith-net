import crypto from 'crypto';
import { pg, isPgEnabled } from '../db';
import { auditLog, AuditAction } from '../auditLog';
import { tick as auditFlushTick } from '../workers/auditFlushWorker';

const describeDb = isPgEnabled() ? describe : describe.skip;

async function cleanAudit() {
  if (!isPgEnabled()) return;
  await pg!.query('TRUNCATE audit_entries RESTART IDENTITY');
  await pg!.query(`DELETE FROM background_jobs WHERE kind='audit_flush'`);
}

/**
 * Drive auditFlushTick repeatedly until no queued/running audit_flush rows
 * remain. Throws if the queue does not drain within timeoutMs.
 */
async function drainAuditFlush(timeoutMs = 5000): Promise<void> {
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
  throw new Error('audit_flush did not drain within timeout');
}

describeDb('auditFlushWorker chain validation', () => {
  beforeEach(cleanAudit);
  afterAll(async () => { await pg?.end(); });

  it('10 sequential log() calls drain to a valid SHA chain', async () => {
    const ids: string[] = [];
    for (let i = 0; i < 10; i++) {
      const r = await auditLog.log(AuditAction.USER_LOGIN, `actor-${i}`, { i });
      ids.push(r.auditId);
    }

    await drainAuditFlush();

    const rows = await pg!.query<{
      id: number; audit_id: string; prev_hash: string | null; hash: string;
    }>(
      `SELECT id, audit_id, prev_hash, hash FROM audit_entries ORDER BY id ASC`
    );
    expect(rows.rowCount).toBe(10);

    // Chain integrity: each prev_hash links to the previous row's hash.
    for (let i = 0; i < rows.rows.length; i++) {
      const expectedPrev = i === 0 ? null : rows.rows[i - 1].hash;
      expect(rows.rows[i].prev_hash).toBe(expectedPrev);
      expect(rows.rows[i].hash).toMatch(/^[a-f0-9]{64}$/);
    }

    // The 10 audit_ids that came out of log() should be the 10 audit_ids in pg, same order.
    expect(rows.rows.map((r) => r.audit_id)).toEqual(ids);
  });

  it('concurrent log() (5 in parallel) drain to a valid chain in some order', async () => {
    await Promise.all(
      [0, 1, 2, 3, 4].map((i) => auditLog.log(AuditAction.USER_LOGIN, `concurrent-${i}`, { i }))
    );
    await drainAuditFlush();

    const rows = await pg!.query<{ prev_hash: string | null; hash: string }>(
      `SELECT prev_hash, hash FROM audit_entries ORDER BY id ASC`
    );
    expect(rows.rowCount).toBe(5);
    // Advisory lock serializes writers — chain must be intact regardless of enqueue order.
    for (let i = 0; i < rows.rows.length; i++) {
      const expectedPrev = i === 0 ? null : rows.rows[i - 1].hash;
      expect(rows.rows[i].prev_hash).toBe(expectedPrev);
    }
  });

  it('flushNow does not throw', async () => {
    await auditLog.log(AuditAction.USER_LOGIN, 'a', { test: 'buffer' });
    await drainAuditFlush();
    expect(() => auditLog.flushNow()).not.toThrow();
  });

  it('recomputed hash matches stored hash (sanity check on chain recipe)', async () => {
    await auditLog.log(AuditAction.USER_LOGIN, 'recomp-actor', { check: 'recompute' });
    await drainAuditFlush();

    const row = (await pg!.query<{
      audit_id: string; action: string; actor_id: string; target_id: string | null;
      metadata: Record<string, any>; prev_hash: string | null; hash: string;
    }>(`SELECT audit_id, action, actor_id, target_id, metadata, prev_hash, hash FROM audit_entries LIMIT 1`)).rows[0];

    // We don't know the worker's recorded timestamp without an extra SELECT,
    // so this test does a structural sanity check: hash is a 64-char hex of
    // sha256, and prev_hash for the first-ever row is null.
    expect(row.hash).toMatch(/^[a-f0-9]{64}$/);
    expect(row.prev_hash).toBeNull();

    // Reconstruct the body envelope used by auditFlushWorker.computeHash and
    // confirm sha256(prev_hash + body) lands on the same hash IF we knew the
    // timestamp. Instead, retrieve the row WITH the timestamp encoded in
    // audit_id (`audit-<timestamp>-<counter>`) and rebuild:
    const match = row.audit_id.match(/^audit-(\d+)-\d+$/);
    expect(match).not.toBeNull();
    const timestamp = parseInt(match![1], 10);

    const body = JSON.stringify({
      id: row.audit_id,
      timestamp,
      action: row.action,
      actorId: row.actor_id,
      targetId: row.target_id ?? undefined,
      metadata: row.metadata,
    });
    const expected = crypto.createHash('sha256').update((row.prev_hash ?? '') + body).digest('hex');
    expect(row.hash).toBe(expected);
  });
});
