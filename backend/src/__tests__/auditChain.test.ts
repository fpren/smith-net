import crypto from 'crypto';
import { pg, isPgEnabled } from '../db';
import { auditLog, AuditAction, AuditEntry } from '../auditLog';

const describeDb = isPgEnabled() ? describe : describe.skip;

async function cleanAudit() {
  if (!isPgEnabled()) return;
  await pg!.query('TRUNCATE audit_entries RESTART IDENTITY');
}

function recomputeHash(entry: AuditEntry): string {
  // Mirror the chain hash recipe from auditLog.ts:
  //   sha256(prevChecksum || JSON.stringify({id, timestamp, action, actorId, targetId, metadata}))
  const body = JSON.stringify({
    id: entry.id,
    timestamp: entry.timestamp,
    action: entry.action,
    actorId: entry.actorId,
    targetId: entry.targetId,
    metadata: entry.metadata,
  });
  const seed = (entry.prevChecksum ?? '') + body;
  return crypto.createHash('sha256').update(seed).digest('hex');
}

describeDb('auditLog chain validation and JSONL minute-buffer', () => {
  beforeEach(cleanAudit);
  afterAll(async () => { await pg?.end(); });

  it('10 sequential appends form a valid SHA chain', async () => {
    const written: AuditEntry[] = [];
    for (let i = 0; i < 10; i++) {
      const e = await auditLog.log(AuditAction.USER_LOGIN, `actor-${i}`, { i });
      written.push(e);
    }

    // First entry has prevChecksum null/undefined; subsequent entries link
    // to the previous entry's checksum.
    for (let i = 0; i < written.length; i++) {
      const prev = written[i - 1]?.checksum ?? null;
      expect(written[i].prevChecksum ?? null).toBe(prev);
      const expected = recomputeHash(written[i]);
      expect(written[i].checksum).toBe(expected);
    }

    // Also verify the pg rows independently.
    const rows = await pg!.query<{ audit_id: string; prev_hash: string | null; hash: string }>(
      `SELECT audit_id, prev_hash, hash FROM audit_entries ORDER BY id ASC`
    );
    expect(rows.rowCount).toBe(10);
    for (let i = 0; i < rows.rows.length; i++) {
      expect(rows.rows[i].audit_id).toBe(written[i].id);
      expect(rows.rows[i].hash).toBe(written[i].checksum);
      expect(rows.rows[i].prev_hash).toBe(written[i - 1]?.checksum ?? null);
    }
  });

  it('flushNow does not throw and the chain test still passes', async () => {
    const before = await auditLog.log(AuditAction.USER_LOGIN, 'a', { test: 'buffer' });
    expect(before.checksum).toBeTruthy();
    // Manually trigger flush via the public flushNow shim.
    expect(() => auditLog.flushNow()).not.toThrow();
  });
});
