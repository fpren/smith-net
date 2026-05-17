-- 014_organization_invites.sql
-- Org invite & join, slice 1: a foreman generates a one-time 8-char code;
-- the joiner POSTs the code and is moved into the foreman's org_id.
-- See organizationInviteService.ts for the consumption rules.

CREATE TABLE IF NOT EXISTS organization_invites (
  code            TEXT PRIMARY KEY,
  organization_id TEXT NOT NULL,
  created_by      TEXT NOT NULL REFERENCES users(id),
  created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  expires_at      TIMESTAMPTZ NOT NULL,
  consumed_at     TIMESTAMPTZ,
  consumed_by     TEXT REFERENCES users(id)
);

CREATE INDEX IF NOT EXISTS org_invites_org_idx
  ON organization_invites (organization_id);

-- Partial index for the "pending invites for this org" listing path
-- (foreman dashboard / cleanup of stale codes).
CREATE INDEX IF NOT EXISTS org_invites_pending_idx
  ON organization_invites (organization_id, created_at DESC)
  WHERE consumed_at IS NULL;
