-- 018_invoices_android_wiring.sql
-- Adds the columns Android needs to push paper-trail invoices into the
-- existing invoices table without a separate sidecar:
--   summary         - opaque jsonb carrying the 37 apk fields the backend
--                     does not model (crew hours, daily breakdown, mesh
--                     presence, etc.). See the Android invoice wiring spec.
--   idempotency_key - client-generated UUID; doubles as the dedupe key so
--                     a retried POST returns the existing row.
-- Partial unique index so existing rows (which have NULL key) don't
-- collide with each other.

ALTER TABLE invoices
  ADD COLUMN IF NOT EXISTS summary         JSONB,
  ADD COLUMN IF NOT EXISTS idempotency_key TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS invoices_org_idem_unique
  ON invoices (organization_id, idempotency_key)
  WHERE idempotency_key IS NOT NULL;

-- Guard against empty-string idempotency keys: '' satisfies IS NOT NULL and
-- would permanently burn a unique slot for that org. NULL is still allowed
-- (existing rows + opt-out for non-android callers).
DO $$
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM pg_constraint
     WHERE conname = 'invoices_idem_not_empty'
  ) THEN
    ALTER TABLE invoices
      ADD CONSTRAINT invoices_idem_not_empty
      CHECK (idempotency_key IS NULL OR idempotency_key <> '');
  END IF;
END $$;
