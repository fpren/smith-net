CREATE TABLE IF NOT EXISTS proposals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id TEXT NOT NULL,
    uuid TEXT UNIQUE NOT NULL DEFAULT gen_random_uuid()::text,
    contractor_name TEXT,
    contractor_phone TEXT,
    contractor_license TEXT,
    client_name TEXT,
    client_address TEXT,
    scope TEXT,
    tasks JSONB DEFAULT '[]',
    materials JSONB DEFAULT '[]',
    equipment JSONB DEFAULT '[]',
    labor_hours DECIMAL DEFAULT 0,
    labor_rate DECIMAL DEFAULT 0,
    labor_cost DECIMAL DEFAULT 0,
    materials_cost DECIMAL DEFAULT 0,
    total_cost DECIMAL DEFAULT 0,
    status TEXT DEFAULT 'pending',
    client_response TEXT,
    client_notes TEXT,
    expires_at TIMESTAMPTZ DEFAULT (NOW() + INTERVAL '30 days'),
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_proposals_uuid ON proposals(uuid);
CREATE INDEX idx_proposals_job_id ON proposals(job_id);
