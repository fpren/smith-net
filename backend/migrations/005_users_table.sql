-- 005_users_table.sql
-- Phase 2 Slice 1: replace in-memory UserStore Map with a pg table.
-- Mirrors the StoredUser shape in backend/src/auth.ts. Admin row is
-- inserted at runtime by usersService.bootstrapAdmin() (idempotent),
-- so this migration is pure DDL.

CREATE TABLE IF NOT EXISTS users (
  id                              TEXT PRIMARY KEY,
  email                           TEXT NOT NULL,
  email_lower                     TEXT GENERATED ALWAYS AS (LOWER(email)) STORED,
  password_hash                   TEXT NOT NULL,
  display_name                    TEXT NOT NULL,
  role                            TEXT NOT NULL,
  organization_id                 TEXT,
  is_active                       BOOLEAN NOT NULL DEFAULT TRUE,
  mfa_enabled                     BOOLEAN NOT NULL DEFAULT FALSE,
  mfa_secret                      TEXT,
  failed_login_count              INTEGER NOT NULL DEFAULT 0,
  locked_until                    TIMESTAMPTZ,
  email_verified_at               TIMESTAMPTZ,
  email_verification_token        TEXT,
  email_verification_expires_at   TIMESTAMPTZ,
  email_verification_last_sent_at TIMESTAMPTZ,
  refresh_tokens                  JSONB NOT NULL DEFAULT '[]'::jsonb,
  last_login_at                   TIMESTAMPTZ,
  created_at                      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at                      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX IF NOT EXISTS users_email_lower_uidx ON users (email_lower);
CREATE INDEX IF NOT EXISTS users_role_idx ON users (role);
CREATE INDEX IF NOT EXISTS users_email_verification_token_idx
  ON users (email_verification_token)
  WHERE email_verification_token IS NOT NULL;
