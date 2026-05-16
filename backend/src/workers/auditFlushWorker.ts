// backend/src/workers/auditFlushWorker.ts
//
// Phase 3 Slice 2: audit-flush worker.
//
// Drains kind='audit_flush' jobs, computes the SHA chain hash under
// pg_advisory_xact_lock(42), INSERTs into audit_entries, and mirrors the
// row into auditLog's in-memory cache + JSONL via bufferFromWorker.

import crypto from 'crypto';
import { pg, isPgEnabled } from '../db';
import { claimNext, complete, fail } from '../queue/queue';
import { auditLog, AuditAction, AuditEntry } from '../auditLog';
import { requestLogger } from '../log';

const KIND = 'audit_flush';

interface AuditFlushPayload {
  auditId: string;
  timestamp: number;
  action: AuditAction;
  actorId: string;
  targetId: string | null;
  metadata: Record<string, any>;
  ip: string | null;
  userAgent: string | null;
}

function computeHash(prev: string | null, p: AuditFlushPayload): string {
  // Mirror auditLog.generateChecksum: targetId is `undefined` when null in
  // the body JSON so SHA chains stay compatible with rows written under the
  // Phase 2 inline path.
  const body = JSON.stringify({
    id: p.auditId,
    timestamp: p.timestamp,
    action: p.action,
    actorId: p.actorId,
    targetId: p.targetId ?? undefined,
    metadata: p.metadata,
  });
  return crypto.createHash('sha256').update((prev ?? '') + body).digest('hex');
}

export async function tick(workerId: string): Promise<boolean> {
  if (!isPgEnabled() || !pg) return false;
  const job = await claimNext(KIND, workerId);
  if (!job) return false;

  const p = job.payload as unknown as AuditFlushPayload;
  const client = await pg.connect();
  try {
    await client.query('BEGIN');
    await client.query('SELECT pg_advisory_xact_lock(42)');

    const prevR = await client.query<{ hash: string }>(
      `SELECT hash FROM audit_entries ORDER BY id DESC LIMIT 1`
    );
    const prevHash = prevR.rows[0]?.hash ?? null;
    const hash = computeHash(prevHash, p);

    await client.query(
      `INSERT INTO audit_entries
         (audit_id, actor_id, target_id, action, metadata, ip, user_agent, prev_hash, hash)
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)
       ON CONFLICT (audit_id) DO NOTHING`,
      [p.auditId, p.actorId, p.targetId, p.action, p.metadata, p.ip, p.userAgent, prevHash, hash]
    );
    await client.query('COMMIT');

    const entry: AuditEntry = {
      id: p.auditId,
      timestamp: p.timestamp,
      action: p.action,
      actorId: p.actorId,
      targetId: p.targetId ?? undefined,
      metadata: p.metadata,
      ip: p.ip ?? undefined,
      userAgent: p.userAgent ?? undefined,
      prevChecksum: prevHash,
      checksum: hash,
    };
    auditLog.bufferFromWorker(entry);

    await complete(job.id);
    requestLogger().info(
      { event: 'audit_flushed', jobId: job.id, auditId: p.auditId },
      'audit row flushed'
    );
    return true;
  } catch (err) {
    try { await client.query('ROLLBACK'); } catch { /* ignore */ }
    await fail(job.id, err as Error, { attempts: job.attempts, maxAttempts: job.max_attempts });
    requestLogger().warn(
      { event: 'audit_flush_failed', jobId: job.id, attempts: job.attempts, err: (err as Error).message },
      'audit flush failed'
    );
    return true;
  } finally {
    client.release();
  }
}
