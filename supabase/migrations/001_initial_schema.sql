-- ════════════════════════════════════════════════════════════════════
-- GUILD OF SMITHS - SUPABASE SCHEMA
-- Run this in your Supabase SQL Editor
-- ════════════════════════════════════════════════════════════════════

-- Enable UUID extension
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ════════════════════════════════════════════════════════════════════
-- USERS & PROFILES (extends Supabase Auth)
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS profiles (
    id UUID PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
    email TEXT UNIQUE NOT NULL,
    display_name TEXT NOT NULL,
    role TEXT NOT NULL DEFAULT 'solo' CHECK (role IN ('solo', 'team', 'lead', 'foreman', 'enterprise', 'admin')),
    organization_id UUID REFERENCES organizations(id),
    phone TEXT,
    trade TEXT, -- 'electrician', 'plumber', etc.
    hourly_rate DECIMAL(10,2) DEFAULT 85.00,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    is_active BOOLEAN DEFAULT true
);

-- ════════════════════════════════════════════════════════════════════
-- ORGANIZATIONS
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS organizations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name TEXT NOT NULL,
    owner_id UUID REFERENCES auth.users(id),
    address TEXT,
    phone TEXT,
    email TEXT,
    tax_id TEXT,
    default_tax_rate DECIMAL(5,2) DEFAULT 8.25,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- ════════════════════════════════════════════════════════════════════
-- CHANNELS (SmithNet Messaging)
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS channels (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name TEXT NOT NULL,
    type TEXT NOT NULL CHECK (type IN ('broadcast', 'group', 'dm', 'job')),
    creator_id UUID REFERENCES auth.users(id),
    organization_id UUID REFERENCES organizations(id),
    job_id UUID REFERENCES jobs(id), -- Link channel to job
    mesh_hash INTEGER, -- 2-byte hash for mesh routing
    is_archived BOOLEAN DEFAULT false,
    is_deleted BOOLEAN DEFAULT false,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS channel_members (
    channel_id UUID REFERENCES channels(id) ON DELETE CASCADE,
    user_id UUID REFERENCES auth.users(id) ON DELETE CASCADE,
    joined_at TIMESTAMPTZ DEFAULT NOW(),
    role TEXT DEFAULT 'member' CHECK (role IN ('member', 'admin', 'owner')),
    PRIMARY KEY (channel_id, user_id)
);

-- ════════════════════════════════════════════════════════════════════
-- MESSAGES
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS messages (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    channel_id UUID REFERENCES channels(id) ON DELETE CASCADE,
    sender_id UUID REFERENCES auth.users(id),
    sender_name TEXT NOT NULL,
    content TEXT NOT NULL,
    origin TEXT DEFAULT 'online' CHECK (origin IN ('online', 'mesh', 'gateway', 'online+mesh')),
    reply_to_id UUID REFERENCES messages(id),
    is_deleted BOOLEAN DEFAULT false,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    
    -- Media attachments (stored in Supabase Storage)
    media_type TEXT, -- 'image', 'voice', 'video', 'file'
    media_url TEXT,
    media_thumbnail TEXT
);

-- Index for fast channel message lookups
CREATE INDEX IF NOT EXISTS idx_messages_channel ON messages(channel_id, created_at DESC);

-- ════════════════════════════════════════════════════════════════════
-- JOBS (Job Board)
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS jobs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    organization_id UUID REFERENCES organizations(id),
    title TEXT NOT NULL,
    description TEXT,
    client_name TEXT,
    client_email TEXT,
    client_phone TEXT,
    location TEXT,
    location_lat DECIMAL(10,7),
    location_lng DECIMAL(10,7),
    status TEXT NOT NULL DEFAULT 'todo' CHECK (status IN ('backlog', 'todo', 'in_progress', 'review', 'done', 'archived')),
    priority TEXT DEFAULT 'medium' CHECK (priority IN ('low', 'medium', 'high', 'urgent')),
    
    -- Crew
    created_by UUID REFERENCES auth.users(id),
    assigned_to UUID[], -- Array of user IDs
    crew_size INTEGER DEFAULT 1,
    
    -- Dates
    due_date TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW(),
    
    -- Financials
    estimated_hours DECIMAL(10,2),
    actual_hours DECIMAL(10,2),
    budget DECIMAL(12,2),
    tools_needed TEXT,
    expenses TEXT
);

-- ════════════════════════════════════════════════════════════════════
-- JOB TASKS
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS tasks (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    job_id UUID REFERENCES jobs(id) ON DELETE CASCADE,
    title TEXT NOT NULL,
    description TEXT,
    status TEXT DEFAULT 'pending' CHECK (status IN ('pending', 'in_progress', 'done', 'blocked')),
    assigned_to UUID REFERENCES auth.users(id),
    created_by UUID REFERENCES auth.users(id),
    sort_order INTEGER DEFAULT 0,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- ════════════════════════════════════════════════════════════════════
-- JOB MATERIALS
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS materials (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    job_id UUID REFERENCES jobs(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    notes TEXT,
    quantity DECIMAL(10,2) DEFAULT 1,
    unit TEXT DEFAULT 'ea', -- ea, ft, lot, hr
    unit_cost DECIMAL(10,2) DEFAULT 0,
    total_cost DECIMAL(10,2) DEFAULT 0,
    vendor TEXT,
    receipt_url TEXT, -- Photo in Supabase Storage
    is_checked BOOLEAN DEFAULT false,
    checked_at TIMESTAMPTZ,
    checked_by UUID REFERENCES auth.users(id),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- ════════════════════════════════════════════════════════════════════
-- JOB CREW MEMBERS
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS job_crew (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    job_id UUID REFERENCES jobs(id) ON DELETE CASCADE,
    user_id UUID REFERENCES auth.users(id),
    name TEXT NOT NULL,
    role TEXT, -- Foreman, Journeyman, Apprentice
    occupation TEXT,
    task TEXT,
    hourly_rate DECIMAL(10,2),
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- ════════════════════════════════════════════════════════════════════
-- WORK LOG (Notes per job)
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS work_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    job_id UUID REFERENCES jobs(id) ON DELETE CASCADE,
    author_id UUID REFERENCES auth.users(id),
    text TEXT NOT NULL,
    log_type TEXT DEFAULT 'note' CHECK (log_type IN ('note', 'issue', 'change_order', 'completion')),
    media_urls TEXT[], -- Array of storage URLs
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- ════════════════════════════════════════════════════════════════════
-- TIME ENTRIES (Time Clock)
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS time_entries (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES auth.users(id) NOT NULL,
    user_name TEXT NOT NULL,
    organization_id UUID REFERENCES organizations(id),
    job_id UUID REFERENCES jobs(id),
    job_title TEXT,
    
    clock_in_time TIMESTAMPTZ NOT NULL,
    clock_out_time TIMESTAMPTZ,
    clock_out_reason TEXT, -- 'complete', 'lunch', 'break', 'end_of_day'
    duration_minutes INTEGER,
    
    entry_type TEXT DEFAULT 'regular' CHECK (entry_type IN ('regular', 'overtime', 'break', 'travel', 'on_call')),
    source TEXT DEFAULT 'manual' CHECK (source IN ('manual', 'geofence', 'beacon', 'mesh')),
    status TEXT DEFAULT 'active' CHECK (status IN ('active', 'completed', 'pending_review', 'approved', 'disputed')),
    
    location TEXT,
    location_lat DECIMAL(10,7),
    location_lng DECIMAL(10,7),
    
    notes TEXT,
    immutable_hash TEXT, -- For audit trail
    
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Index for fast user time lookups
CREATE INDEX IF NOT EXISTS idx_time_entries_user ON time_entries(user_id, clock_in_time DESC);
CREATE INDEX IF NOT EXISTS idx_time_entries_job ON time_entries(job_id, clock_in_time DESC);

-- ════════════════════════════════════════════════════════════════════
-- INVOICES
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS invoices (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    invoice_number TEXT UNIQUE NOT NULL,
    organization_id UUID REFERENCES organizations(id),
    job_id UUID REFERENCES jobs(id),
    
    status TEXT DEFAULT 'draft' CHECK (status IN ('draft', 'issued', 'sent', 'viewed', 'paid', 'overdue', 'disputed', 'cancelled')),
    mode TEXT DEFAULT 'solo' CHECK (mode IN ('solo', 'enterprise')),
    
    -- From (Provider)
    from_name TEXT NOT NULL,
    from_business TEXT,
    from_trade TEXT,
    from_phone TEXT,
    from_email TEXT,
    from_address TEXT,
    
    -- To (Client)
    to_name TEXT,
    to_company TEXT,
    to_address TEXT,
    to_email TEXT,
    project_ref TEXT,
    po_number TEXT,
    
    -- Dates
    issue_date TIMESTAMPTZ DEFAULT NOW(),
    due_date TIMESTAMPTZ NOT NULL,
    project_start TIMESTAMPTZ,
    project_end TIMESTAMPTZ,
    working_days INTEGER DEFAULT 1,
    
    -- Totals
    subtotal DECIMAL(12,2) DEFAULT 0,
    tax_rate DECIMAL(5,2) DEFAULT 8.25,
    tax_amount DECIMAL(12,2) DEFAULT 0,
    total_due DECIMAL(12,2) DEFAULT 0,
    
    -- Crew (for enterprise)
    total_crew_hours DECIMAL(10,2) DEFAULT 0,
    
    -- AI Report data
    work_window TEXT,
    total_on_site_minutes INTEGER DEFAULT 0,
    photo_count INTEGER DEFAULT 0,
    voice_note_count INTEGER DEFAULT 0,
    checklist_count INTEGER DEFAULT 0,
    work_log_summary TEXT,
    efficiency_score INTEGER,
    
    payment_instructions TEXT,
    notes TEXT,
    
    created_at TIMESTAMPTZ DEFAULT NOW(),
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- ════════════════════════════════════════════════════════════════════
-- INVOICE LINE ITEMS
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS invoice_line_items (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    invoice_id UUID REFERENCES invoices(id) ON DELETE CASCADE,
    code TEXT NOT NULL, -- LAB-01, MAT-100, TRV-01
    description TEXT NOT NULL,
    quantity DECIMAL(10,2) NOT NULL,
    unit TEXT DEFAULT 'ea',
    rate DECIMAL(10,2) NOT NULL,
    total DECIMAL(12,2) NOT NULL,
    category TEXT CHECK (category IN ('labor', 'materials', 'travel', 'change_order', 'other')),
    sort_order INTEGER DEFAULT 0,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- ════════════════════════════════════════════════════════════════════
-- INVOICE CREW HOURS (for enterprise invoices)
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS invoice_crew_hours (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    invoice_id UUID REFERENCES invoices(id) ON DELETE CASCADE,
    name TEXT NOT NULL,
    role TEXT,
    occupation TEXT,
    total_hours DECIMAL(10,2) DEFAULT 0,
    productive_hours DECIMAL(10,2) DEFAULT 0,
    travel_hours DECIMAL(10,2) DEFAULT 0
);

-- ════════════════════════════════════════════════════════════════════
-- INVOICE DAILY BREAKDOWN (for enterprise invoices)
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS invoice_daily_breakdown (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    invoice_id UUID REFERENCES invoices(id) ON DELETE CASCADE,
    day_number INTEGER NOT NULL,
    date TIMESTAMPTZ NOT NULL,
    start_time TEXT,
    end_time TEXT,
    total_hours DECIMAL(10,2) DEFAULT 0,
    activities TEXT,
    mesh_sync_notes TEXT,
    photo_count INTEGER DEFAULT 0,
    voice_note_count INTEGER DEFAULT 0,
    checklist_count INTEGER DEFAULT 0,
    key_notes TEXT
);

-- ════════════════════════════════════════════════════════════════════
-- AUDIT LOG
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS audit_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES auth.users(id),
    action TEXT NOT NULL,
    entity_type TEXT, -- 'job', 'invoice', 'time_entry', etc.
    entity_id UUID,
    details JSONB,
    ip_address TEXT,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Index for audit lookups
CREATE INDEX IF NOT EXISTS idx_audit_logs_user ON audit_logs(user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_logs_entity ON audit_logs(entity_type, entity_id);

-- ════════════════════════════════════════════════════════════════════
-- GATEWAY RELAYS
-- ════════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS gateway_relays (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID REFERENCES auth.users(id),
    name TEXT NOT NULL,
    capabilities TEXT[],
    last_activity TIMESTAMPTZ,
    is_connected BOOLEAN DEFAULT false,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- ════════════════════════════════════════════════════════════════════
-- ROW LEVEL SECURITY POLICIES
-- ════════════════════════════════════════════════════════════════════

-- Enable RLS on all tables
ALTER TABLE profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE organizations ENABLE ROW LEVEL SECURITY;
ALTER TABLE channels ENABLE ROW LEVEL SECURITY;
ALTER TABLE channel_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE messages ENABLE ROW LEVEL SECURITY;
ALTER TABLE jobs ENABLE ROW LEVEL SECURITY;
ALTER TABLE tasks ENABLE ROW LEVEL SECURITY;
ALTER TABLE materials ENABLE ROW LEVEL SECURITY;
ALTER TABLE job_crew ENABLE ROW LEVEL SECURITY;
ALTER TABLE work_logs ENABLE ROW LEVEL SECURITY;
ALTER TABLE time_entries ENABLE ROW LEVEL SECURITY;
ALTER TABLE invoices ENABLE ROW LEVEL SECURITY;
ALTER TABLE invoice_line_items ENABLE ROW LEVEL SECURITY;
ALTER TABLE audit_logs ENABLE ROW LEVEL SECURITY;

-- Profiles: Users can read all, update own
CREATE POLICY "Profiles are viewable by everyone" ON profiles FOR SELECT USING (true);
CREATE POLICY "Users can update own profile" ON profiles FOR UPDATE USING (auth.uid() = id);

-- Jobs: Users see their own + org jobs
CREATE POLICY "Users can view own jobs" ON jobs FOR SELECT USING (
    created_by = auth.uid() OR 
    auth.uid() = ANY(assigned_to) OR
    organization_id IN (SELECT organization_id FROM profiles WHERE id = auth.uid())
);
CREATE POLICY "Users can create jobs" ON jobs FOR INSERT WITH CHECK (auth.uid() = created_by);
CREATE POLICY "Users can update own jobs" ON jobs FOR UPDATE USING (
    created_by = auth.uid() OR 
    organization_id IN (SELECT organization_id FROM profiles WHERE id = auth.uid())
);

-- Time entries: Users see their own
CREATE POLICY "Users can view own time entries" ON time_entries FOR SELECT USING (user_id = auth.uid());
CREATE POLICY "Users can create own time entries" ON time_entries FOR INSERT WITH CHECK (user_id = auth.uid());
CREATE POLICY "Users can update own time entries" ON time_entries FOR UPDATE USING (user_id = auth.uid());
CREATE POLICY "Users can delete own time entries" ON time_entries FOR DELETE USING (user_id = auth.uid());

-- Invoices: Users see their own + org invoices
CREATE POLICY "Users can view own invoices" ON invoices FOR SELECT USING (
    from_email = (SELECT email FROM profiles WHERE id = auth.uid()) OR
    organization_id IN (SELECT organization_id FROM profiles WHERE id = auth.uid())
);

-- Messages: Users see messages in channels they're members of
CREATE POLICY "Users can view channel messages" ON messages FOR SELECT USING (
    channel_id IN (SELECT channel_id FROM channel_members WHERE user_id = auth.uid())
);
CREATE POLICY "Users can send messages to their channels" ON messages FOR INSERT WITH CHECK (
    channel_id IN (SELECT channel_id FROM channel_members WHERE user_id = auth.uid())
);

-- ════════════════════════════════════════════════════════════════════
-- FUNCTIONS
-- ════════════════════════════════════════════════════════════════════

-- Auto-update updated_at timestamp
CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Apply to tables
CREATE TRIGGER update_profiles_updated_at BEFORE UPDATE ON profiles FOR EACH ROW EXECUTE FUNCTION update_updated_at();
CREATE TRIGGER update_jobs_updated_at BEFORE UPDATE ON jobs FOR EACH ROW EXECUTE FUNCTION update_updated_at();
CREATE TRIGGER update_tasks_updated_at BEFORE UPDATE ON tasks FOR EACH ROW EXECUTE FUNCTION update_updated_at();
CREATE TRIGGER update_time_entries_updated_at BEFORE UPDATE ON time_entries FOR EACH ROW EXECUTE FUNCTION update_updated_at();
CREATE TRIGGER update_invoices_updated_at BEFORE UPDATE ON invoices FOR EACH ROW EXECUTE FUNCTION update_updated_at();

-- Auto-create profile on user signup
CREATE OR REPLACE FUNCTION handle_new_user()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO profiles (id, email, display_name, role)
    VALUES (
        NEW.id,
        NEW.email,
        COALESCE(NEW.raw_user_meta_data->>'display_name', split_part(NEW.email, '@', 1)),
        'solo'
    );
    RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE TRIGGER on_auth_user_created
    AFTER INSERT ON auth.users
    FOR EACH ROW EXECUTE FUNCTION handle_new_user();

-- Calculate time entry duration on clock out
CREATE OR REPLACE FUNCTION calculate_duration()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.clock_out_time IS NOT NULL AND OLD.clock_out_time IS NULL THEN
        NEW.duration_minutes = EXTRACT(EPOCH FROM (NEW.clock_out_time - NEW.clock_in_time)) / 60;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER calculate_time_entry_duration
    BEFORE UPDATE ON time_entries
    FOR EACH ROW EXECUTE FUNCTION calculate_duration();

-- ════════════════════════════════════════════════════════════════════
-- REALTIME SUBSCRIPTIONS
-- ════════════════════════════════════════════════════════════════════

-- Enable realtime for messaging
ALTER PUBLICATION supabase_realtime ADD TABLE messages;
ALTER PUBLICATION supabase_realtime ADD TABLE channels;
ALTER PUBLICATION supabase_realtime ADD TABLE time_entries;
ALTER PUBLICATION supabase_realtime ADD TABLE jobs;
