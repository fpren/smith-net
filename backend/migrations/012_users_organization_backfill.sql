-- 012_users_organization_backfill.sql
-- Tenant isolation, slice 1: every existing user becomes their own org of one.
-- The column already exists (005_users_table.sql:14) but was never populated.
-- After backfill we lock it NOT NULL so future writers must set it.

UPDATE users
   SET organization_id = id,
       updated_at = NOW()
 WHERE organization_id IS NULL;

ALTER TABLE users
  ALTER COLUMN organization_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS users_organization_id_idx
  ON users (organization_id);
