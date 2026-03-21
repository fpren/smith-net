-- Phase 2: Intent / Synthesizer / Ledger tables

CREATE TABLE IF NOT EXISTS intents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    current_version_id UUID,
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS intent_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    intent_id UUID NOT NULL REFERENCES intents(id) ON DELETE CASCADE,
    version_number INT NOT NULL DEFAULT 1,
    status TEXT NOT NULL DEFAULT 'draft' CHECK (status IN ('draft', 'proposed', 'confirmed', 'superseded')),
    scope_statement TEXT NOT NULL,
    intended_job_ids UUID[] NOT NULL DEFAULT '{}',
    parties UUID[] NOT NULL DEFAULT '{}',
    confirmed_at TIMESTAMPTZ,
    confirmed_by UUID,
    superseded_by UUID REFERENCES intent_versions(id),
    supersedes UUID REFERENCES intent_versions(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    auto_generated BOOLEAN NOT NULL DEFAULT false
);

ALTER TABLE intents ADD CONSTRAINT fk_current_version
    FOREIGN KEY (current_version_id) REFERENCES intent_versions(id);

CREATE INDEX idx_iv_intent ON intent_versions(intent_id);
CREATE INDEX idx_iv_status ON intent_versions(status);

CREATE TABLE IF NOT EXISTS summary_artifacts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    serial TEXT UNIQUE NOT NULL,
    intent_version_id UUID NOT NULL REFERENCES intent_versions(id),
    scope_statement TEXT NOT NULL,
    work_performed TEXT[] NOT NULL DEFAULT '{}',
    labor_recorded TEXT[] NOT NULL DEFAULT '{}',
    materials_used TEXT[] NOT NULL DEFAULT '{}',
    contextual_notes TEXT[] NOT NULL DEFAULT '{}',
    total_hours NUMERIC NOT NULL DEFAULT 0,
    total_cost NUMERIC NOT NULL DEFAULT 0,
    job_ids UUID[] NOT NULL DEFAULT '{}',
    time_entry_ids UUID[] NOT NULL DEFAULT '{}',
    chat_message_ids UUID[] NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_sa_intent_version ON summary_artifacts(intent_version_id);
CREATE INDEX idx_sa_serial ON summary_artifacts(serial);

CREATE TABLE IF NOT EXISTS ledger_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    artifact_serial TEXT NOT NULL REFERENCES summary_artifacts(serial),
    artifact_id UUID NOT NULL REFERENCES summary_artifacts(id),
    sha256_hash TEXT NOT NULL,
    blockchain_ref TEXT,
    actor_uuid UUID NOT NULL,
    supersedes UUID REFERENCES ledger_entries(id),
    superseded_by UUID REFERENCES ledger_entries(id),
    sealed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_le_artifact ON ledger_entries(artifact_serial);
CREATE INDEX idx_le_sealed ON ledger_entries(sealed_at DESC);

ALTER TABLE intents ENABLE ROW LEVEL SECURITY;
ALTER TABLE intent_versions ENABLE ROW LEVEL SECURITY;
ALTER TABLE summary_artifacts ENABLE ROW LEVEL SECURITY;
ALTER TABLE ledger_entries ENABLE ROW LEVEL SECURITY;

CREATE POLICY "Authenticated users can manage intents"
    ON intents FOR ALL USING (auth.uid() IS NOT NULL);
CREATE POLICY "Authenticated users can manage intent versions"
    ON intent_versions FOR ALL USING (auth.uid() IS NOT NULL);
CREATE POLICY "Authenticated users can read artifacts"
    ON summary_artifacts FOR SELECT USING (auth.uid() IS NOT NULL);
CREATE POLICY "Authenticated users can create artifacts"
    ON summary_artifacts FOR INSERT WITH CHECK (auth.uid() IS NOT NULL);
CREATE POLICY "Authenticated users can read ledger"
    ON ledger_entries FOR SELECT USING (auth.uid() IS NOT NULL);
CREATE POLICY "Authenticated users can create ledger entries"
    ON ledger_entries FOR INSERT WITH CHECK (auth.uid() IS NOT NULL);
