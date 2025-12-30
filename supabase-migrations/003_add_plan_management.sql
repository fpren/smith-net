-- ════════════════════════════════════════════════════════════════════════════
-- PLAN MANAGEMENT SYSTEM TABLES
-- Run this in your Supabase SQL Editor (Dashboard → SQL Editor → New Query)
-- ════════════════════════════════════════════════════════════════════════════

-- 1. ENGAGEMENTS TABLE
-- Stores initial engagements (intent only, no facts yet)
CREATE TABLE IF NOT EXISTS engagements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL,
    description TEXT,
    client_name TEXT,
    location TEXT,
    created_by TEXT NOT NULL,
    created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    status TEXT NOT NULL DEFAULT 'active', -- 'active', 'converted', 'archived'
    intent TEXT NOT NULL,
    updated_at BIGINT DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT
);

-- Index for faster queries
CREATE INDEX IF NOT EXISTS idx_engagements_created_by ON engagements(created_by);
CREATE INDEX IF NOT EXISTS idx_engagements_status ON engagements(status);

-- Enable Row Level Security
ALTER TABLE engagements ENABLE ROW LEVEL SECURITY;

-- Allow anyone to read engagements (for now - can be restricted later)
CREATE POLICY "Anyone can read engagements" ON engagements
    FOR SELECT USING (true);

-- Allow authenticated users to create engagements
CREATE POLICY "Authenticated users can create engagements" ON engagements
    FOR INSERT WITH CHECK (true);

-- Allow creators to update their engagements
CREATE POLICY "Creators can update engagements" ON engagements
    FOR UPDATE USING (created_by = auth.uid()::text OR auth.uid() IS NULL);


-- 2. PLANS TABLE
-- Core plan entity that flows through phases
CREATE TABLE IF NOT EXISTS plans (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    engagement_id UUID NOT NULL REFERENCES engagements(id),
    name TEXT NOT NULL,
    description TEXT,
    phase TEXT NOT NULL DEFAULT 'draft', -- See PlanPhase type
    created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    updated_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    job_ids JSONB DEFAULT '[]'::jsonb, -- Array of job IDs
    time_entry_ids JSONB DEFAULT '[]'::jsonb, -- Array of time entry IDs
    archived_at BIGINT,
    immutable_hash TEXT,
    created_by TEXT NOT NULL
);

-- Index for faster queries
CREATE INDEX IF NOT EXISTS idx_plans_engagement ON plans(engagement_id);
CREATE INDEX IF NOT EXISTS idx_plans_phase ON plans(phase);
CREATE INDEX IF NOT EXISTS idx_plans_created_by ON plans(created_by);

-- Enable Row Level Security
ALTER TABLE plans ENABLE ROW LEVEL SECURITY;

-- Allow anyone to read plans (for now)
CREATE POLICY "Anyone can read plans" ON plans
    FOR SELECT USING (true);

-- Allow authenticated users to create plans
CREATE POLICY "Authenticated users can create plans" ON plans
    FOR INSERT WITH CHECK (true);

-- Allow creators to update their plans
CREATE POLICY "Creators can update plans" ON plans
    FOR UPDATE USING (created_by = auth.uid()::text OR auth.uid() IS NULL);


-- 3. PROPOSALS TABLE
-- For standard flow (Create Proposal → Confirm)
CREATE TABLE IF NOT EXISTS proposals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id UUID NOT NULL REFERENCES plans(id),
    title TEXT NOT NULL,
    description TEXT NOT NULL,
    scope JSONB NOT NULL, -- Array of scope items
    estimated_hours DECIMAL,
    estimated_cost DECIMAL,
    created_by TEXT NOT NULL,
    created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    confirmed_at BIGINT,
    confirmed_by TEXT,
    status TEXT NOT NULL DEFAULT 'draft' -- 'draft', 'submitted', 'approved', 'rejected'
);

-- Index for faster queries
CREATE INDEX IF NOT EXISTS idx_proposals_plan ON proposals(plan_id);
CREATE INDEX IF NOT EXISTS idx_proposals_status ON proposals(status);

-- Enable Row Level Security
ALTER TABLE proposals ENABLE ROW LEVEL SECURITY;

-- Allow anyone to read proposals
CREATE POLICY "Anyone can read proposals" ON proposals
    FOR SELECT USING (true);

-- Allow authenticated users to create proposals
CREATE POLICY "Authenticated users can create proposals" ON proposals
    FOR INSERT WITH CHECK (true);

-- Allow creators to update their proposals
CREATE POLICY "Creators can update proposals" ON proposals
    FOR UPDATE USING (created_by = auth.uid()::text OR auth.uid() IS NULL);


-- 4. PLAN SUMMARIES TABLE
-- Synthesis phase output
CREATE TABLE IF NOT EXISTS plan_summaries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id UUID NOT NULL REFERENCES plans(id),
    title TEXT NOT NULL,
    executive_summary TEXT NOT NULL,
    work_performed JSONB NOT NULL, -- Array of work items
    challenges JSONB, -- Array of challenges
    recommendations JSONB, -- Array of recommendations
    total_hours DECIMAL NOT NULL DEFAULT 0,
    total_cost DECIMAL,
    created_by TEXT NOT NULL, -- AI or human
    created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    confirmed_at BIGINT,
    confirmed_by TEXT,
    status TEXT NOT NULL DEFAULT 'draft' -- 'draft', 'confirmed'
);

-- Index for faster queries
CREATE INDEX IF NOT EXISTS idx_plan_summaries_plan ON plan_summaries(plan_id);
CREATE INDEX IF NOT EXISTS idx_plan_summaries_status ON plan_summaries(status);

-- Enable Row Level Security
ALTER TABLE plan_summaries ENABLE ROW LEVEL SECURITY;

-- Allow anyone to read summaries
CREATE POLICY "Anyone can read plan summaries" ON plan_summaries
    FOR SELECT USING (true);

-- Allow authenticated users to create summaries
CREATE POLICY "Authenticated users can create summaries" ON plan_summaries
    FOR INSERT WITH CHECK (true);

-- Allow creators to update their summaries
CREATE POLICY "Creators can update summaries" ON plan_summaries
    FOR UPDATE USING (created_by = auth.uid()::text OR auth.uid() IS NULL);


-- 5. REPORTS TABLE
-- Generated narrative reports
CREATE TABLE IF NOT EXISTS reports (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id UUID NOT NULL REFERENCES plans(id),
    title TEXT NOT NULL,
    content TEXT NOT NULL, -- Narrative explanation
    total_hours DECIMAL, -- May include pricing if not report-only
    created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    created_by TEXT NOT NULL,
    archived BOOLEAN DEFAULT FALSE
);

-- Index for faster queries
CREATE INDEX IF NOT EXISTS idx_reports_plan ON reports(plan_id);
CREATE INDEX IF NOT EXISTS idx_reports_archived ON reports(archived);

-- Enable Row Level Security
ALTER TABLE reports ENABLE ROW LEVEL SECURITY;

-- Allow anyone to read reports
CREATE POLICY "Anyone can read reports" ON reports
    FOR SELECT USING (true);

-- Allow authenticated users to create reports
CREATE POLICY "Authenticated users can create reports" ON reports
    FOR INSERT WITH CHECK (true);

-- Allow creators to update their reports (until archived)
CREATE POLICY "Creators can update reports" ON reports
    FOR UPDATE USING (created_by = auth.uid()::text OR auth.uid() IS NULL);


-- 6. INVOICES TABLE
-- Generated invoices with pricing
CREATE TABLE IF NOT EXISTS invoices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id UUID NOT NULL REFERENCES plans(id),
    title TEXT NOT NULL,
    client_name TEXT,
    line_items JSONB NOT NULL, -- Array of InvoiceLineItem
    subtotal DECIMAL NOT NULL,
    tax DECIMAL,
    total DECIMAL NOT NULL,
    due_date BIGINT NOT NULL,
    status TEXT NOT NULL DEFAULT 'draft', -- 'draft', 'sent', 'paid', 'overdue'
    created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    created_by TEXT NOT NULL,
    archived BOOLEAN DEFAULT FALSE
);

-- Index for faster queries
CREATE INDEX IF NOT EXISTS idx_invoices_plan ON invoices(plan_id);
CREATE INDEX IF NOT EXISTS idx_invoices_status ON invoices(status);
CREATE INDEX IF NOT EXISTS idx_invoices_archived ON invoices(archived);

-- Enable Row Level Security
ALTER TABLE invoices ENABLE ROW LEVEL SECURITY;

-- Allow anyone to read invoices
CREATE POLICY "Anyone can read invoices" ON invoices
    FOR SELECT USING (true);

-- Allow authenticated users to create invoices
CREATE POLICY "Authenticated users can create invoices" ON invoices
    FOR INSERT WITH CHECK (true);

-- Allow creators to update their invoices (until archived)
CREATE POLICY "Creators can update invoices" ON invoices
    FOR UPDATE USING (created_by = auth.uid()::text OR auth.uid() IS NULL);


-- 7. PLAN OUTPUTS TABLE
-- Tracks what outputs were generated for each plan
CREATE TABLE IF NOT EXISTS plan_outputs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id UUID NOT NULL REFERENCES plans(id),
    type TEXT NOT NULL, -- 'report_only', 'invoice_only', 'report_and_invoice'
    report_id UUID REFERENCES reports(id),
    invoice_id UUID REFERENCES invoices(id),
    generated_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    generated_by TEXT NOT NULL
);

-- Index for faster queries
CREATE INDEX IF NOT EXISTS idx_plan_outputs_plan ON plan_outputs(plan_id);

-- Enable Row Level Security
ALTER TABLE plan_outputs ENABLE ROW LEVEL SECURITY;

-- Allow anyone to read outputs
CREATE POLICY "Anyone can read plan outputs" ON plan_outputs
    FOR SELECT USING (true);

-- Allow authenticated users to create outputs
CREATE POLICY "Authenticated users can create outputs" ON plan_outputs
    FOR INSERT WITH CHECK (true);


-- 8. PLAN SNAPSHOTS TABLE
-- Immutable snapshots for archive (read-only forever)
CREATE TABLE IF NOT EXISTS plan_snapshots (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    plan_id UUID NOT NULL REFERENCES plans(id),
    snapshot_type TEXT NOT NULL, -- 'archive', 'output'
    data JSONB NOT NULL, -- Complete plan data snapshot
    jobs JSONB, -- Associated jobs snapshot
    time_entries JSONB, -- Associated time entries snapshot
    messages JSONB, -- Associated chat context snapshot
    created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    immutable_hash TEXT NOT NULL -- SHA256 for tamper detection
);

-- Index for faster queries
CREATE INDEX IF NOT EXISTS idx_plan_snapshots_plan ON plan_snapshots(plan_id);
CREATE INDEX IF NOT EXISTS idx_plan_snapshots_type ON plan_snapshots(snapshot_type);

-- Enable Row Level Security
ALTER TABLE plan_snapshots ENABLE ROW LEVEL SECURITY;

-- Allow anyone to read snapshots (they are immutable)
CREATE POLICY "Anyone can read plan snapshots" ON plan_snapshots
    FOR SELECT USING (true);

-- Allow system to create snapshots
CREATE POLICY "System can create snapshots" ON plan_snapshots
    FOR INSERT WITH CHECK (true);


-- 9. ENABLE REALTIME
-- Enable real-time subscriptions for plan management tables

ALTER PUBLICATION supabase_realtime ADD TABLE engagements;
ALTER PUBLICATION supabase_realtime ADD TABLE plans;
ALTER PUBLICATION supabase_realtime ADD TABLE proposals;
ALTER PUBLICATION supabase_realtime ADD TABLE plan_summaries;
ALTER PUBLICATION supabase_realtime ADD TABLE reports;
ALTER PUBLICATION supabase_realtime ADD TABLE invoices;
ALTER PUBLICATION supabase_realtime ADD TABLE plan_outputs;
ALTER PUBLICATION supabase_realtime ADD TABLE plan_snapshots;


-- ════════════════════════════════════════════════════════════════════════════
-- VERIFICATION
-- Run these to verify tables were created:
-- ════════════════════════════════════════════════════════════════════════════

-- SELECT * FROM engagements LIMIT 5;
-- SELECT * FROM plans LIMIT 5;
-- SELECT * FROM proposals LIMIT 5;
-- SELECT * FROM plan_summaries LIMIT 5;
-- SELECT * FROM reports LIMIT 5;
-- SELECT * FROM invoices LIMIT 5;
-- SELECT * FROM plan_outputs LIMIT 5;
-- SELECT * FROM plan_snapshots LIMIT 5;

-- Check realtime is enabled:
-- SELECT * FROM pg_publication_tables WHERE pubname = 'supabase_realtime' AND tablename IN ('engagements', 'plans', 'proposals', 'plan_summaries', 'reports', 'invoices', 'plan_outputs', 'plan_snapshots');
