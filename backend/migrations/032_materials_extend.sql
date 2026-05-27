-- Extend the materials table with checklist + cost-capture columns.
-- Synthesizer continues reading name/quantity/unit_cost; canonical hash unchanged.

ALTER TABLE materials
  ADD COLUMN IF NOT EXISTS notes TEXT,
  ADD COLUMN IF NOT EXISTS checked BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS checked_at TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS vendor TEXT,
  ADD COLUMN IF NOT EXISTS updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- Tighten and link.
ALTER TABLE materials ALTER COLUMN job_id SET NOT NULL;

-- Idempotent FK add: drop-if-exists, then add.
ALTER TABLE materials DROP CONSTRAINT IF EXISTS materials_job_id_fkey;
ALTER TABLE materials ADD CONSTRAINT materials_job_id_fkey
  FOREIGN KEY (job_id) REFERENCES jobs(id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_materials_job ON materials (job_id);
