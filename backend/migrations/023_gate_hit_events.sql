-- Sub-project 3: tier-gate telemetry sink (F5.2). PII-free: user_id_hash =
-- SHA256(profile.id). Append-only event log for the conversion funnel.
CREATE TABLE IF NOT EXISTS gate_hit_events (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  event        TEXT NOT NULL,
  user_id_hash TEXT NOT NULL,
  current_tier TEXT NOT NULL,
  metadata     JSONB NOT NULL DEFAULT '{}',
  occurred_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_gate_hit_events_event
  ON gate_hit_events (event, occurred_at DESC);
CREATE INDEX IF NOT EXISTS idx_gate_hit_events_user_hash
  ON gate_hit_events (user_id_hash, occurred_at DESC);
