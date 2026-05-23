-- Tier sub-project 1: real tier source. Backfilled from role; the column is the
-- source of truth going forward (trials/billing/admin set it later).
ALTER TABLE users ADD COLUMN IF NOT EXISTS tier TEXT NOT NULL DEFAULT 'open'
  CHECK (tier IN ('open', 'solo', 'advanced', 'enterprise'));

UPDATE users SET tier = CASE role
  WHEN 'solo' THEN 'solo'
  WHEN 'team' THEN 'solo'
  WHEN 'lead' THEN 'advanced'
  WHEN 'foreman' THEN 'advanced'
  WHEN 'enterprise' THEN 'enterprise'
  WHEN 'admin' THEN 'enterprise'
  ELSE 'open'
END
WHERE tier = 'open';
