-- Notifications N-1: per-user notification feed. One row = one alert for ONE
-- recipient (user_id). type/title/body/link describe it; actor_id is who/what
-- caused it (a user id, 'system', later 'smithai'). read_at NULL = unread.
-- user_id is TEXT to match users.id (consistent with prior tier sub-projects).
CREATE TABLE IF NOT EXISTS notifications (
  id         TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
  user_id    TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,  -- recipient
  type       TEXT NOT NULL,         -- 'message' | 'job_assigned' (later: 'invoice_viewed' | 'ai')
  title      TEXT NOT NULL,
  body       TEXT,
  link       TEXT,                  -- in-app target, e.g. /console/comm or /console/jobs/:id
  actor_id   TEXT,                  -- who/what caused it
  read_at    TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS notifications_user_created_idx
  ON notifications (user_id, created_at DESC);
