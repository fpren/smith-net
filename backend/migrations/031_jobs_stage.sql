-- Add the pipeline stage column to jobs.
-- Stage is additive to status; both fields coexist.

ALTER TABLE jobs ADD COLUMN IF NOT EXISTS stage TEXT NOT NULL DEFAULT 'lead'
  CHECK (stage IN ('lead','proposal','approved','in_progress','review','invoice','closed'));

-- Backfill from existing status (lossy by design — see spec section 9).
UPDATE jobs SET stage = CASE
  WHEN status = 'planned'     THEN 'lead'
  WHEN status = 'in_progress' THEN 'in_progress'
  WHEN status = 'complete'    THEN 'closed'
  WHEN status = 'cancelled'   THEN 'closed'
  ELSE 'lead'
END
WHERE stage = 'lead';

CREATE INDEX IF NOT EXISTS idx_jobs_foreman_stage ON jobs (foreman_id, stage);
