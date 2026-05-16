-- 011_worker_heartbeats.sql
-- Phase 4 Slice 1: heartbeat table for daemons + workers.
-- Each row is one running worker/daemon process. UPSERTed every 30s.
-- /api/admin/health reads this to show liveness.

CREATE TABLE IF NOT EXISTS worker_heartbeats (
  worker_id    TEXT PRIMARY KEY,
  kinds        TEXT[] NOT NULL DEFAULT ARRAY[]::TEXT[],
  last_beat_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  started_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS worker_heartbeats_recent_idx
  ON worker_heartbeats (last_beat_at DESC);
