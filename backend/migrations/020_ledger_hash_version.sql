-- backend/migrations/020_ledger_hash_version.sql
-- M2: per-entry ledger hash format version. Existing rows are v1 (legacy
-- float-JSON canonicalization); new seals are v2 (ROM canonical byte encoding).
ALTER TABLE ledger_entries
  ADD COLUMN IF NOT EXISTS hash_version SMALLINT NOT NULL DEFAULT 1;
