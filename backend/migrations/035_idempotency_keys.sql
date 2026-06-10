-- W6 offline-write support: a generic idempotency-key store so the portal's
-- offline outbox can safely replay create mutations (jobs, expenses, time clock)
-- without creating duplicates. A request carrying an Idempotency-Key header is
-- processed at most once per (key, scope); replays return the cached response.
-- Scope is the authenticated user id, so keys never collide across users.

CREATE TABLE IF NOT EXISTS idempotency_keys (
  key         TEXT NOT NULL,
  scope       TEXT NOT NULL,
  status      TEXT NOT NULL DEFAULT 'in_progress',   -- in_progress | completed
  response    JSONB,                                  -- { status, body } captured on completion
  created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (key, scope)
);

CREATE INDEX IF NOT EXISTS idx_idempotency_created ON idempotency_keys (created_at);
