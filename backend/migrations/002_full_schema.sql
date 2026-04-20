-- Full schema for smith-net self-hosted Postgres. Run AFTER 001 (relay tables).
-- Safe to re-run; all statements are idempotent.

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ════════════════════════════════════════════════════════════════════
-- INTENT (scope declaration + versioning)
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS intents (
    id UUID PRIMARY KEY,
    current_version_id UUID,
    created_by TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS intent_versions (
    id UUID PRIMARY KEY,
    intent_id UUID NOT NULL,
    version_number INTEGER NOT NULL,
    status TEXT NOT NULL DEFAULT 'draft',
    scope_statement TEXT NOT NULL,
    intended_job_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    parties JSONB NOT NULL DEFAULT '[]'::jsonb,
    confirmed_at TIMESTAMPTZ,
    confirmed_by TEXT,
    superseded_by UUID,
    supersedes UUID,
    auto_generated BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_intent_versions_intent ON intent_versions(intent_id);

-- ════════════════════════════════════════════════════════════════════
-- LEDGER (sealed artifact entries)
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS ledger_entries (
    id UUID PRIMARY KEY,
    artifact_serial TEXT NOT NULL,
    artifact_id UUID NOT NULL,
    sha256_hash TEXT NOT NULL,
    blockchain_ref TEXT,
    actor_uuid TEXT NOT NULL,
    supersedes UUID,
    superseded_by UUID,
    sealed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_ledger_serial ON ledger_entries(artifact_serial);
CREATE INDEX IF NOT EXISTS idx_ledger_artifact ON ledger_entries(artifact_id);

-- ════════════════════════════════════════════════════════════════════
-- SUMMARY ARTIFACTS (synthesizer output)
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS summary_artifacts (
    id UUID PRIMARY KEY,
    serial TEXT NOT NULL UNIQUE,
    intent_version_id UUID,
    scope_statement TEXT NOT NULL,
    work_performed JSONB NOT NULL DEFAULT '[]'::jsonb,
    labor_recorded JSONB NOT NULL DEFAULT '[]'::jsonb,
    materials_used JSONB NOT NULL DEFAULT '[]'::jsonb,
    contextual_notes JSONB NOT NULL DEFAULT '[]'::jsonb,
    total_hours NUMERIC(10,2),
    total_cost NUMERIC(12,2),
    job_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    time_entry_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    chat_message_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- ════════════════════════════════════════════════════════════════════
-- PROPOSALS (shareable client-facing)
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS proposals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    uuid TEXT UNIQUE NOT NULL DEFAULT replace(gen_random_uuid()::text, '-', ''),
    job_id TEXT,
    contractor_name TEXT,
    contractor_phone TEXT,
    contractor_license TEXT,
    client_name TEXT,
    client_address TEXT,
    scope TEXT,
    tasks JSONB DEFAULT '[]'::jsonb,
    materials JSONB DEFAULT '[]'::jsonb,
    equipment JSONB DEFAULT '[]'::jsonb,
    labor_hours NUMERIC(10,2) DEFAULT 0,
    labor_rate NUMERIC(10,2) DEFAULT 0,
    labor_cost NUMERIC(12,2) DEFAULT 0,
    materials_cost NUMERIC(12,2) DEFAULT 0,
    total_cost NUMERIC(12,2) DEFAULT 0,
    status TEXT DEFAULT 'pending',
    client_response TEXT,
    client_notes TEXT,
    expires_at TIMESTAMPTZ DEFAULT NOW() + INTERVAL '30 days',
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_proposals_uuid ON proposals(uuid);
CREATE INDEX IF NOT EXISTS idx_proposals_job ON proposals(job_id);

-- ════════════════════════════════════════════════════════════════════
-- INVOICE LINKS (shareable client-facing)
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS invoice_links (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    uuid TEXT UNIQUE NOT NULL DEFAULT replace(gen_random_uuid()::text, '-', ''),
    job_id TEXT,
    contractor_name TEXT,
    contractor_phone TEXT,
    contractor_license TEXT,
    client_name TEXT,
    client_address TEXT,
    work_summary TEXT,
    hours_worked NUMERIC(10,2) DEFAULT 0,
    hourly_rate NUMERIC(10,2) DEFAULT 0,
    labor_cost NUMERIC(12,2) DEFAULT 0,
    materials JSONB DEFAULT '[]'::jsonb,
    materials_cost NUMERIC(12,2) DEFAULT 0,
    total_due NUMERIC(12,2) DEFAULT 0,
    payment_info TEXT,
    status TEXT DEFAULT 'unpaid',
    created_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_invoice_links_uuid ON invoice_links(uuid);

-- ════════════════════════════════════════════════════════════════════
-- READ-ONLY SIDE TABLES (populated by app; empty until app writes)
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS jobs (
    id UUID PRIMARY KEY,
    title TEXT,
    description TEXT,
    status TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS time_entries (
    id UUID PRIMARY KEY,
    user_id TEXT,
    job_id UUID,
    duration_minutes INTEGER,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS materials (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id UUID,
    name TEXT,
    quantity NUMERIC(10,2),
    unit TEXT,
    unit_cost NUMERIC(10,2),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Admin cleanup tables — placeholder shells so the admin route doesn't blow up.
-- Add columns incrementally when the app actually starts writing to them.
CREATE TABLE IF NOT EXISTS profiles (id TEXT PRIMARY KEY, email TEXT UNIQUE, display_name TEXT, role TEXT, created_at TIMESTAMPTZ DEFAULT NOW());
CREATE TABLE IF NOT EXISTS organizations (id TEXT PRIMARY KEY, created_at TIMESTAMPTZ DEFAULT NOW());
CREATE TABLE IF NOT EXISTS channel_members (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), channel_id TEXT, user_id TEXT, created_at TIMESTAMPTZ DEFAULT NOW());
CREATE TABLE IF NOT EXISTS work_logs (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), created_at TIMESTAMPTZ DEFAULT NOW());
CREATE TABLE IF NOT EXISTS job_crew (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), created_at TIMESTAMPTZ DEFAULT NOW());
CREATE TABLE IF NOT EXISTS tasks (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), created_at TIMESTAMPTZ DEFAULT NOW());
CREATE TABLE IF NOT EXISTS plan_snapshots (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), created_at TIMESTAMPTZ DEFAULT NOW());
CREATE TABLE IF NOT EXISTS plan_outputs (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), created_at TIMESTAMPTZ DEFAULT NOW());
CREATE TABLE IF NOT EXISTS invoices (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), created_at TIMESTAMPTZ DEFAULT NOW());
CREATE TABLE IF NOT EXISTS reports (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), created_at TIMESTAMPTZ DEFAULT NOW());
CREATE TABLE IF NOT EXISTS plan_summaries (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), created_at TIMESTAMPTZ DEFAULT NOW());
CREATE TABLE IF NOT EXISTS plans (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), created_at TIMESTAMPTZ DEFAULT NOW());
CREATE TABLE IF NOT EXISTS engagements (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), created_at TIMESTAMPTZ DEFAULT NOW());

-- Seed admin profile so adminRoutes.ts's "preserve admin" logic has something to preserve.
INSERT INTO profiles (id, email, display_name, role)
VALUES ('admin', 'admin@smithnet.local', 'Admin', 'admin')
ON CONFLICT (id) DO NOTHING;

-- Grant everything to smith
GRANT ALL ON ALL TABLES IN SCHEMA public TO smith;
GRANT ALL ON ALL SEQUENCES IN SCHEMA public TO smith;
