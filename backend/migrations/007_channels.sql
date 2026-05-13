-- 007_channels.sql
-- Phase 2 Slice 4: persist channelRegistry state. JSONB columns hold the
-- ACL arrays so the entire Channel struct round-trips through one row.

CREATE TABLE IF NOT EXISTS channels (
  id                TEXT PRIMARY KEY,
  name              TEXT NOT NULL,
  type              TEXT NOT NULL,
  visibility        TEXT NOT NULL DEFAULT 'public',
  creator_id        TEXT NOT NULL,
  member_ids        JSONB NOT NULL DEFAULT '[]'::jsonb,
  allowed_users     JSONB NOT NULL DEFAULT '[]'::jsonb,
  blocked_users     JSONB NOT NULL DEFAULT '[]'::jsonb,
  pending_requests  JSONB NOT NULL DEFAULT '[]'::jsonb,
  requires_approval BOOLEAN NOT NULL DEFAULT FALSE,
  is_archived       BOOLEAN NOT NULL DEFAULT FALSE,
  is_deleted        BOOLEAN NOT NULL DEFAULT FALSE,
  mesh_hash         INTEGER NOT NULL,
  created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS channels_mesh_hash_idx ON channels (mesh_hash);
CREATE INDEX IF NOT EXISTS channels_creator_idx   ON channels (creator_id);
