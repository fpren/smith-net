-- Per-job BOL-style expenses. Category is free text; UI suggests slugs.
-- amount is flat (not unit-priced). expense_date is a calendar date.

CREATE TABLE IF NOT EXISTS job_expenses (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  job_id UUID NOT NULL REFERENCES jobs(id) ON DELETE CASCADE,
  category TEXT NOT NULL,
  description TEXT NOT NULL,
  amount NUMERIC(10,2) NOT NULL DEFAULT 0,
  vendor TEXT,
  notes TEXT,
  expense_date DATE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_job_expenses_job ON job_expenses (job_id);
