-- 019_invoice_line_items_client_id.sql
--
-- Adds a per-invoice client-supplied id to line items so the Android
-- outbox worker can replay partial-failure CREATE sequences without
-- duplicating items. See docs/superpowers/specs/2026-05-17-android-
-- invoice-wiring-design.md (final-review fix B).
--
-- Existing rows get a synthetic UUID; new rows from Android must
-- supply one. The UNIQUE constraint guarantees the same client_item_id
-- can be retried into the same invoice without dup.

ALTER TABLE invoice_line_items
  ADD COLUMN IF NOT EXISTS client_item_id TEXT;

-- Backfill any existing rows with a unique value so the UNIQUE works.
UPDATE invoice_line_items
   SET client_item_id = id::text
 WHERE client_item_id IS NULL;

-- After backfill, enforce NOT NULL going forward.
ALTER TABLE invoice_line_items
  ALTER COLUMN client_item_id SET NOT NULL;

-- Partial unique just like invoices: (invoice_id, client_item_id).
CREATE UNIQUE INDEX IF NOT EXISTS invoice_line_items_invoice_client_unique
  ON invoice_line_items (invoice_id, client_item_id);
