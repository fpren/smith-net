-- 015_channels_organization.sql
-- Tenant isolation, slice 2: scope channels to a single org. After this,
-- channelRegistry.listForUser filters by organization_id before any
-- membership/access check, mirroring crew_positions (013) and users (012).

ALTER TABLE channels
  ADD COLUMN IF NOT EXISTS organization_id TEXT;

-- Backfill from the creator's org (012 guarantees users.organization_id is NOT NULL).
UPDATE channels c
   SET organization_id = u.organization_id
  FROM users u
 WHERE c.creator_id = u.id
   AND c.organization_id IS NULL;

-- Any leftover NULL rows (creator deleted, or seed channels with no user
-- row) get an explicit fallback so the NOT NULL lock holds. In production
-- these rows should not exist after the user-table backfill.
UPDATE channels
   SET organization_id = creator_id
 WHERE organization_id IS NULL;

ALTER TABLE channels
  ALTER COLUMN organization_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS channels_organization_id_idx
  ON channels (organization_id);
