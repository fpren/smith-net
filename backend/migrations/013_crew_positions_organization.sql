-- 013_crew_positions_organization.sql
-- Tenant isolation, slice 1: scope crew_positions to a single org.
-- After this migration /api/crew/positions filters by p.organization_id,
-- so a foreman only sees crew within their own org.

ALTER TABLE crew_positions
  ADD COLUMN IF NOT EXISTS organization_id TEXT;

-- Backfill from the owning user's org (012 guarantees users.organization_id is NOT NULL).
UPDATE crew_positions p
   SET organization_id = u.organization_id
  FROM users u
 WHERE p.user_id = u.id
   AND p.organization_id IS NULL;

ALTER TABLE crew_positions
  ALTER COLUMN organization_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS crew_positions_organization_id_idx
  ON crew_positions (organization_id);
