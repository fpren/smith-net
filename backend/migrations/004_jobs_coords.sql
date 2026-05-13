-- 004_jobs_coords.sql
-- Plan 4: add latitude/longitude/geocoded_at columns to jobs for map pins.

ALTER TABLE jobs
  ADD COLUMN IF NOT EXISTS latitude    DOUBLE PRECISION,
  ADD COLUMN IF NOT EXISTS longitude   DOUBLE PRECISION,
  ADD COLUMN IF NOT EXISTS geocoded_at TIMESTAMPTZ;

CREATE INDEX IF NOT EXISTS idx_jobs_coords ON jobs(latitude, longitude)
  WHERE latitude IS NOT NULL AND longitude IS NOT NULL;
