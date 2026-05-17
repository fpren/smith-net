-- 016_tasks.sql
-- Per-job task list. The skeleton tasks table (id, created_at only) is
-- extended with the columns the API and clients actually need. Tenant
-- isolation is inherited transitively: every task belongs to a job, and
-- jobs.foreman_id pins the tenant.

ALTER TABLE tasks
  ADD COLUMN IF NOT EXISTS job_id        UUID,
  ADD COLUMN IF NOT EXISTS title         TEXT,
  ADD COLUMN IF NOT EXISTS status        TEXT NOT NULL DEFAULT 'pending',
  ADD COLUMN IF NOT EXISTS sort_order    INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS created_by    TEXT,
  ADD COLUMN IF NOT EXISTS updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  ADD COLUMN IF NOT EXISTS completed_at  TIMESTAMPTZ;

-- A prior failed apply of this migration may have created job_id as TEXT
-- (because the initial draft of this file used TEXT). Convert it to UUID
-- so the foreign key to jobs.id (UUID) attaches cleanly. Table starts empty
-- so the cast is risk-free.
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 'tasks' AND column_name = 'job_id' AND data_type = 'text'
  ) THEN
    ALTER TABLE tasks ALTER COLUMN job_id TYPE UUID USING job_id::uuid;
  END IF;
END $$;

-- The empty skeleton table can't have NOT NULL job_id/title applied via
-- ADD COLUMN, so set them after the column exists; the table starts empty
-- so the lock is safe.
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 'tasks' AND column_name = 'job_id' AND is_nullable = 'YES'
  ) THEN
    ALTER TABLE tasks ALTER COLUMN job_id SET NOT NULL;
  END IF;
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 'tasks' AND column_name = 'title' AND is_nullable = 'YES'
  ) THEN
    ALTER TABLE tasks ALTER COLUMN title SET NOT NULL;
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'tasks_job_id_fkey'
  ) THEN
    ALTER TABLE tasks
      ADD CONSTRAINT tasks_job_id_fkey
        FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE;
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'tasks_status_chk'
  ) THEN
    ALTER TABLE tasks
      ADD CONSTRAINT tasks_status_chk CHECK (status IN ('pending', 'done'));
  END IF;
END $$;

CREATE INDEX IF NOT EXISTS tasks_job_id_idx ON tasks (job_id);
CREATE INDEX IF NOT EXISTS tasks_job_sort_idx ON tasks (job_id, sort_order);
