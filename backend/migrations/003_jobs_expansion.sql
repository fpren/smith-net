-- 003_jobs_expansion.sql
-- Plan 2: expand jobs + recreate job_crew

-- ────────────── Expand jobs ──────────────
ALTER TABLE jobs
  ADD COLUMN IF NOT EXISTS foreman_id     TEXT REFERENCES profiles(id),
  ADD COLUMN IF NOT EXISTS client_id      UUID,
  ADD COLUMN IF NOT EXISTS engagement_id  UUID,
  ADD COLUMN IF NOT EXISTS scheduled_at   TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS location       TEXT,
  ADD COLUMN IF NOT EXISTS updated_at     TIMESTAMPTZ DEFAULT NOW();

ALTER TABLE jobs ALTER COLUMN status SET DEFAULT 'planned';

CREATE INDEX IF NOT EXISTS idx_jobs_foreman ON jobs(foreman_id);
CREATE INDEX IF NOT EXISTS idx_jobs_status  ON jobs(status);

-- ────────────── Recreate job_crew ──────────────
DROP TABLE IF EXISTS job_crew;
CREATE TABLE job_crew (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  job_id       UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
  profile_id   TEXT NOT NULL REFERENCES profiles(id),
  role_on_job  TEXT NOT NULL DEFAULT 'crew',
  assigned_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  UNIQUE(job_id, profile_id)
);
CREATE INDEX idx_job_crew_job     ON job_crew(job_id);
CREATE INDEX idx_job_crew_profile ON job_crew(profile_id);
