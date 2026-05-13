-- 006_audit_entries.sql
-- Phase 2 Slice 2: move audit chain into pg. JSONL becomes cold backup.
-- prev_hash + hash form a SHA256 chain. id is the row order; created_at is
-- the wall clock. Both are indexed for typical queries (per-actor, time
-- range).

CREATE TABLE IF NOT EXISTS audit_entries (
  id           BIGSERIAL PRIMARY KEY,
  audit_id     TEXT NOT NULL,
  actor_id     TEXT NOT NULL,
  target_id    TEXT,
  action       TEXT NOT NULL,
  metadata     JSONB NOT NULL DEFAULT '{}'::jsonb,
  ip           TEXT,
  user_agent   TEXT,
  prev_hash    TEXT,
  hash         TEXT NOT NULL,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS audit_entries_audit_id_uidx ON audit_entries (audit_id);
CREATE INDEX IF NOT EXISTS audit_entries_actor_idx ON audit_entries (actor_id, created_at DESC);
CREATE INDEX IF NOT EXISTS audit_entries_action_idx ON audit_entries (action, created_at DESC);
