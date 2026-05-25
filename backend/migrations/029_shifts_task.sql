-- Clock job/task connection: link a shift to a specific task (and denormalize
-- the title for display, mirroring job_id/job_title). Nullable; FK SET NULL.
ALTER TABLE shifts ADD COLUMN IF NOT EXISTS task_id    UUID;
ALTER TABLE shifts ADD COLUMN IF NOT EXISTS task_title TEXT;
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints WHERE constraint_name = 'shifts_task_id_fkey') THEN
    ALTER TABLE shifts ADD CONSTRAINT shifts_task_id_fkey FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE SET NULL;
  END IF;
END $$;
