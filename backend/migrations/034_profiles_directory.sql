-- W3 directory parity: give the crew directory the fields the Supabase version
-- had. Adds a human-shareable public_id and an organization_id to profiles,
-- backfills both, and indexes them. Idempotent (safe to re-run).

ALTER TABLE profiles ADD COLUMN IF NOT EXISTS organization_id TEXT;
ALTER TABLE profiles ADD COLUMN IF NOT EXISTS public_id TEXT;

-- Backfill org from the matching users row. users.organization_id defaults to
-- the user's own id at registration, so solo users are their own org.
UPDATE profiles p
   SET organization_id = u.organization_id
  FROM users u
 WHERE u.id = p.id
   AND p.organization_id IS NULL;

-- Fallback: any profile still without an org (e.g. the bootstrap admin seed,
-- which predates the users table) becomes its own org, matching the
-- "solo user is their own org" model used at registration.
UPDATE profiles
   SET organization_id = id
 WHERE organization_id IS NULL;

-- Backfill a stable 8-char uppercase public_id derived from the profile id.
-- Deterministic, so re-running is a no-op; md5-prefix collisions are negligible
-- at this scale and the unique index below would surface any.
UPDATE profiles
   SET public_id = upper(substr(md5(id), 1, 8))
 WHERE public_id IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS idx_profiles_public_id ON profiles (public_id);
CREATE INDEX IF NOT EXISTS idx_profiles_org ON profiles (organization_id);
