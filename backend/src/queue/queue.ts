/**
 * Phase 3 Slice 1: Postgres-backed background-job queue.
 *
 * Public API:
 *   enqueue()    -- INSERT a job row; dedupe via partial unique index
 *   claimNext()  -- pop one queued job via FOR UPDATE SKIP LOCKED (Task 3)
 *   complete()   -- mark terminal success (Task 3)
 *   fail()       -- backoff retry or mark dead (Task 4)
 */

import { pg, isPgEnabled } from '../db';

export type BgJobKind =
  | 'geocode'
  | 'audit_flush'
  | 'email'
  | 'invoice_draft'
  | 'report_render'
  | 'llm_call'
  | 'cleanup'
  | 'heartbeat';

export interface EnqueueOptions {
  kind: BgJobKind;
  payload: Record<string, unknown>;
  scheduledAt?: Date;
  dedupeKey?: string;
  maxAttempts?: number;
}

function requirePg() {
  if (!isPgEnabled() || !pg) {
    throw new Error('[queue] DATABASE_URL is required; pg pool is not initialized');
  }
  return pg;
}

export async function enqueue(opts: EnqueueOptions): Promise<{ id: number; created: boolean }> {
  const db = requirePg();
  const r = await db.query<{ id: number }>(
    `INSERT INTO background_jobs (kind, payload, scheduled_at, dedupe_key, max_attempts)
     VALUES ($1, $2::jsonb, COALESCE($3, NOW()), $4, COALESCE($5, 5))
     ON CONFLICT (kind, dedupe_key)
       WHERE state IN ('queued','running','failed') AND dedupe_key IS NOT NULL
       DO NOTHING
     RETURNING id`,
    [opts.kind, JSON.stringify(opts.payload), opts.scheduledAt ?? null, opts.dedupeKey ?? null, opts.maxAttempts ?? null]
  );
  if (r.rowCount === 0) return { id: -1, created: false };
  // BIGSERIAL is returned as string from node-pg; normalize to number for consumers.
  return { id: Number(r.rows[0].id), created: true };
}

export interface ClaimedJob {
  id: number;
  payload: Record<string, unknown>;
  attempts: number;
  max_attempts: number;
}

export async function claimNext(kind: BgJobKind, workerId: string): Promise<ClaimedJob | null> {
  const db = requirePg();
  const r = await db.query<{ id: string; payload: Record<string, unknown>; attempts: number; max_attempts: number }>(
    `UPDATE background_jobs
        SET state = 'running',
            locked_at = NOW(),
            locked_by = $2,
            attempts = attempts + 1,
            updated_at = NOW()
      WHERE id = (
        SELECT id FROM background_jobs
         WHERE kind = $1 AND state = 'queued' AND scheduled_at <= NOW()
         ORDER BY scheduled_at, id
         FOR UPDATE SKIP LOCKED
         LIMIT 1
      )
      RETURNING id, payload, attempts, max_attempts`,
    [kind, workerId]
  );
  const row = r.rows[0];
  if (!row) return null;
  // node-pg returns BIGSERIAL id as string; normalize to number.
  return { id: Number(row.id), payload: row.payload, attempts: row.attempts, max_attempts: row.max_attempts };
}

export async function complete(id: number): Promise<void> {
  const db = requirePg();
  await db.query(
    `UPDATE background_jobs
        SET state = 'succeeded', finished_at = NOW(), updated_at = NOW()
      WHERE id = $1`,
    [id]
  );
}

export async function fail(id: number, err: Error, opts: { attempts: number; maxAttempts: number }): Promise<void> {
  const db = requirePg();
  const dead = opts.attempts >= opts.maxAttempts;
  const backoffSec = Math.min(60 * Math.pow(3, opts.attempts), 6 * 3600);
  await db.query(
    `UPDATE background_jobs
        SET state = $2::bg_job_state,
            last_error = $3,
            scheduled_at = CASE WHEN $2 = 'failed' THEN NOW() + ($4::int * INTERVAL '1 second') ELSE scheduled_at END,
            finished_at = CASE WHEN $2 = 'dead' THEN NOW() ELSE NULL END,
            locked_at = NULL,
            locked_by = NULL,
            updated_at = NOW()
      WHERE id = $1`,
    [id, dead ? 'dead' : 'failed', err.message.slice(0, 1000), backoffSec]
  );
}
