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
