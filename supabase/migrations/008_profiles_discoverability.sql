-- ════════════════════════════════════════════════════════════════════
-- PROFILES: discoverability + public_id + org_id
-- Supports scoped Add Colleague: team-only vs searchable vs hidden.
-- ════════════════════════════════════════════════════════════════════

-- ── Columns ──────────────────────────────────────────────────────────

ALTER TABLE profiles
    ADD COLUMN IF NOT EXISTS public_id TEXT,
    ADD COLUMN IF NOT EXISTS discoverability TEXT NOT NULL DEFAULT 'team',
    ADD COLUMN IF NOT EXISTS org_id UUID;

-- Backfill public_id for existing rows (first 8 chars of md5 of UUID).
UPDATE profiles
   SET public_id = UPPER(SUBSTR(MD5(id::text), 1, 8))
 WHERE public_id IS NULL;

-- Make public_id NOT NULL + UNIQUE now that it's backfilled.
ALTER TABLE profiles
    ALTER COLUMN public_id SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS profiles_public_id_key
    ON profiles (public_id);

-- Constrain discoverability to known values.
ALTER TABLE profiles
    DROP CONSTRAINT IF EXISTS profiles_discoverability_check;

ALTER TABLE profiles
    ADD CONSTRAINT profiles_discoverability_check
    CHECK (discoverability IN ('nobody', 'team', 'anyone'));

-- Index for name prefix search.
CREATE INDEX IF NOT EXISTS profiles_display_name_idx
    ON profiles (LOWER(display_name));

CREATE INDEX IF NOT EXISTS profiles_org_id_idx
    ON profiles (org_id);

-- ── Auto-assign public_id on new signup ──────────────────────────────

CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO public.profiles (id, email, display_name, public_id)
    VALUES (
        NEW.id,
        NEW.email,
        COALESCE(NEW.raw_user_meta_data->>'display_name', split_part(NEW.email, '@', 1)),
        UPPER(SUBSTR(MD5(NEW.id::text), 1, 8))
    )
    ON CONFLICT (id) DO NOTHING;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- ── RLS: discoverability-gated SELECT ────────────────────────────────
--
-- Existing "Users can view own profile" policy stays (owner always sees
-- own row). We add a second SELECT policy that permits others to read
-- a row only if the target opted in and the caller is in scope.
--
-- Policy semantics:
--   discoverability = 'nobody'  → never visible to others
--   discoverability = 'team'    → visible to callers with same org_id
--   discoverability = 'anyone'  → visible to any authenticated caller
--
-- The public_id exact-match path uses the same policy: if someone knows
-- your public_id but you're 'nobody', the lookup returns nothing.

DROP POLICY IF EXISTS "Discoverable profiles are visible to peers" ON profiles;

CREATE POLICY "Discoverable profiles are visible to peers" ON profiles
    FOR SELECT
    USING (
        auth.uid() <> id
        AND (
            discoverability = 'anyone'
            OR (
                discoverability = 'team'
                AND org_id IS NOT NULL
                AND org_id = (SELECT p.org_id FROM profiles p WHERE p.id = auth.uid())
            )
        )
    );
