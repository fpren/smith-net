-- 017_invoices.sql
-- Real invoices + line items. Replaces the route stubs that returned [] /
-- 404 with persisted rows. Tenant isolation mirrors channels (015):
-- invoices.organization_id is NOT NULL and indexed; list query filters by
-- it before any other ACL check.
--
-- An invoices skeleton (id, created_at only) already exists from an earlier
-- exploratory commit, so this migration ADDs the columns it needs; the
-- starting state has zero rows so the NOT NULL constraints lock safely.

ALTER TABLE invoices
  ADD COLUMN IF NOT EXISTS organization_id TEXT,
  ADD COLUMN IF NOT EXISTS created_by      TEXT,
  ADD COLUMN IF NOT EXISTS invoice_number  TEXT,
  ADD COLUMN IF NOT EXISTS client_name     TEXT,
  ADD COLUMN IF NOT EXISTS client_email    TEXT,
  ADD COLUMN IF NOT EXISTS issue_date      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  ADD COLUMN IF NOT EXISTS due_date        TIMESTAMPTZ,
  ADD COLUMN IF NOT EXISTS status          TEXT NOT NULL DEFAULT 'draft',
  ADD COLUMN IF NOT EXISTS subtotal        NUMERIC(12, 2) NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS tax_rate        NUMERIC(6, 4)  NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS tax_amount      NUMERIC(12, 2) NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS total_due       NUMERIC(12, 2) NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS notes           TEXT,
  ADD COLUMN IF NOT EXISTS is_deleted      BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW();

-- Lock NOT NULL on the cluster of required columns once they exist.
DO $$
BEGIN
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 'invoices' AND column_name = 'organization_id' AND is_nullable = 'YES'
  ) THEN
    ALTER TABLE invoices ALTER COLUMN organization_id SET NOT NULL;
  END IF;
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 'invoices' AND column_name = 'created_by' AND is_nullable = 'YES'
  ) THEN
    ALTER TABLE invoices ALTER COLUMN created_by SET NOT NULL;
  END IF;
  IF EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_name = 'invoices' AND column_name = 'invoice_number' AND is_nullable = 'YES'
  ) THEN
    ALTER TABLE invoices ALTER COLUMN invoice_number SET NOT NULL;
  END IF;
END $$;

DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'invoices_created_by_fkey'
  ) THEN
    ALTER TABLE invoices
      ADD CONSTRAINT invoices_created_by_fkey
        FOREIGN KEY (created_by) REFERENCES users(id);
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'invoices_status_chk'
  ) THEN
    ALTER TABLE invoices
      ADD CONSTRAINT invoices_status_chk
        CHECK (status IN ('draft', 'issued', 'sent', 'viewed', 'paid', 'overdue', 'disputed', 'cancelled'));
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint WHERE conname = 'invoices_org_number_unique'
  ) THEN
    ALTER TABLE invoices
      ADD CONSTRAINT invoices_org_number_unique
        UNIQUE (organization_id, invoice_number);
  END IF;
END $$;

CREATE INDEX IF NOT EXISTS invoices_organization_id_idx ON invoices (organization_id);
CREATE INDEX IF NOT EXISTS invoices_org_status_idx       ON invoices (organization_id, status) WHERE NOT is_deleted;
CREATE INDEX IF NOT EXISTS invoices_created_by_idx       ON invoices (created_by);

CREATE TABLE IF NOT EXISTS invoice_line_items (
  id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  invoice_id   UUID NOT NULL REFERENCES invoices(id) ON DELETE CASCADE,
  description  TEXT NOT NULL,
  quantity     NUMERIC(12, 3) NOT NULL DEFAULT 1,
  unit         TEXT NOT NULL DEFAULT 'ea',
  rate         NUMERIC(12, 2) NOT NULL DEFAULT 0,
  total        NUMERIC(12, 2) NOT NULL DEFAULT 0,
  category     TEXT NOT NULL DEFAULT 'other',
  sort_order   INTEGER NOT NULL DEFAULT 0,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),

  CONSTRAINT invoice_line_items_category_chk
    CHECK (category IN ('labor', 'materials', 'travel', 'change_order', 'other'))
);

CREATE INDEX IF NOT EXISTS invoice_line_items_invoice_id_idx
  ON invoice_line_items (invoice_id);
CREATE INDEX IF NOT EXISTS invoice_line_items_invoice_sort_idx
  ON invoice_line_items (invoice_id, sort_order);
