-- Profile avatar: optional uploaded photo. The comm redesign renders circular
-- avatars (photo with initials fallback); this is where the photo URL lives.
-- Idempotent (safe to re-run).

ALTER TABLE profiles ADD COLUMN IF NOT EXISTS avatar_url TEXT;
