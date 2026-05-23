-- Sub-project 4: trial records against users.tier (the single tier source).
-- UNIQUE(user_id, tier) enforces "a tier's trial can be used only once" (the
-- expired row stays, spending the slot). previous_tier is the revert target.
CREATE TABLE IF NOT EXISTS trials (
  id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id       TEXT NOT NULL REFERENCES users(id),
  tier          TEXT NOT NULL CHECK (tier IN ('solo', 'advanced')),
  previous_tier TEXT NOT NULL,
  started_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at    TIMESTAMPTZ NOT NULL,
  status        TEXT NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'expired', 'canceled')),
  UNIQUE (user_id, tier)
);

CREATE INDEX IF NOT EXISTS idx_trials_due ON trials (status, expires_at);
