-- 010_shifts_and_positions.sql
-- Phase 3.5 Slice 1: crew tracking foundation.
-- shifts gates location tracking (only while ended_at IS NULL).
-- crew_positions is latest-only (no history — Phase 3.6 adds a trail).

CREATE TABLE IF NOT EXISTS shifts (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id     TEXT NOT NULL REFERENCES profiles(id),
  started_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  ended_at    TIMESTAMPTZ,
  source      TEXT NOT NULL
);

-- At most one open shift per user. Partial unique index.
CREATE UNIQUE INDEX IF NOT EXISTS shifts_one_open_per_user_uidx
  ON shifts (user_id)
  WHERE ended_at IS NULL;

CREATE INDEX IF NOT EXISTS shifts_user_started_idx ON shifts (user_id, started_at DESC);

CREATE TABLE IF NOT EXISTS crew_positions (
  user_id        TEXT PRIMARY KEY REFERENCES profiles(id),
  latitude       DOUBLE PRECISION NOT NULL,
  longitude      DOUBLE PRECISION NOT NULL,
  accuracy_m     REAL,
  recorded_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  source         TEXT NOT NULL,
  battery_pct    INTEGER
);
