-- 008_gateway_sessions.sql
-- Phase 2 Slice 4: persist gatewayManager relay metadata. The WS reference
-- itself is per-process and not persisted — relays must reconnect to
-- repopulate it. `last_activity` is the TTL anchor: rows older than 5min
-- are considered dead and skipped at boot-time rebuild. A Phase 3 cleanup
-- daemon will DELETE them.

CREATE TABLE IF NOT EXISTS gateway_sessions (
  id             TEXT PRIMARY KEY,
  name           TEXT NOT NULL,
  capabilities   JSONB NOT NULL DEFAULT '[]'::jsonb,
  last_activity  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS gateway_sessions_last_activity_idx
  ON gateway_sessions (last_activity);
