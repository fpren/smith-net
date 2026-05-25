-- backend/migrations/030_clients.sql
-- Clients container (A0). Owner-scoped by owner_id (mirrors jobs.foreman_id).
-- Also gives the pre-existing jobs.client_id column a real FK home.

CREATE TABLE IF NOT EXISTS clients (
  id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  owner_id    TEXT NOT NULL REFERENCES profiles(id),
  name        TEXT NOT NULL,
  email       TEXT,
  phone       TEXT,
  address     TEXT,
  company     TEXT,
  notes       TEXT,
  is_deleted  BOOLEAN NOT NULL DEFAULT FALSE,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_clients_owner ON clients (owner_id) WHERE is_deleted = FALSE;
CREATE INDEX IF NOT EXISTS idx_clients_owner_name ON clients (owner_id, lower(name));

-- jobs.client_id already exists (003_jobs_expansion.sql) but has no FK. Add it now.
ALTER TABLE jobs
  ADD CONSTRAINT jobs_client_id_fkey
  FOREIGN KEY (client_id) REFERENCES clients(id) ON DELETE SET NULL;
