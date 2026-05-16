-- 009_background_jobs.sql
-- Phase 3 Slice 1: Postgres-backed background-job queue.
-- One table per the daemon-worker-queue plan. Different `kind` values
-- partition the work; workers claim WHERE kind=$1 FOR UPDATE SKIP LOCKED.

DO $$ BEGIN
  CREATE TYPE bg_job_state AS ENUM ('queued', 'running', 'succeeded', 'failed', 'dead');
EXCEPTION WHEN duplicate_object THEN null; END $$;

CREATE TABLE IF NOT EXISTS background_jobs (
  id              BIGSERIAL PRIMARY KEY,
  kind            TEXT NOT NULL,
  payload         JSONB NOT NULL DEFAULT '{}'::jsonb,
  state           bg_job_state NOT NULL DEFAULT 'queued',
  attempts        INTEGER NOT NULL DEFAULT 0,
  max_attempts    INTEGER NOT NULL DEFAULT 5,
  scheduled_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  locked_at       TIMESTAMPTZ,
  locked_by       TEXT,
  last_error      TEXT,
  dedupe_key      TEXT,
  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  finished_at     TIMESTAMPTZ
);

CREATE INDEX IF NOT EXISTS bg_jobs_claim_idx
  ON background_jobs (kind, scheduled_at)
  WHERE state = 'queued';

CREATE UNIQUE INDEX IF NOT EXISTS bg_jobs_dedupe_idx
  ON background_jobs (kind, dedupe_key)
  WHERE state IN ('queued', 'running', 'failed') AND dedupe_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS bg_jobs_state_idx ON background_jobs (state, kind);
