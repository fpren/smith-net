-- Clock parity: enrich shifts to mirror the APK TimeEntry. All nullable/defaulted
-- so existing rows and the current bare clock-in keep working.
ALTER TABLE shifts ADD COLUMN IF NOT EXISTS entry_type       TEXT NOT NULL DEFAULT 'regular';
ALTER TABLE shifts ADD COLUMN IF NOT EXISTS job_id           UUID;
ALTER TABLE shifts ADD COLUMN IF NOT EXISTS job_title        TEXT;
ALTER TABLE shifts ADD COLUMN IF NOT EXISTS clock_out_reason TEXT;

-- job_id references the board when picked from it; NULL for free-text-only tags.
-- ON DELETE SET NULL so deleting a job never orphans/breaks a shift row.
DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.table_constraints
                 WHERE constraint_name = 'shifts_job_id_fkey') THEN
    ALTER TABLE shifts
      ADD CONSTRAINT shifts_job_id_fkey FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE SET NULL;
  END IF;
END $$;

DO $$ BEGIN
  IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conname = 'shifts_entry_type_check') THEN
    ALTER TABLE shifts ADD CONSTRAINT shifts_entry_type_check
      CHECK (entry_type IN ('regular','overtime','break','travel','on_call'));
  END IF;
END $$;
