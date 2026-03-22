CREATE TABLE IF NOT EXISTS invoice_links (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id TEXT NOT NULL,
    uuid TEXT UNIQUE NOT NULL DEFAULT gen_random_uuid()::text,
    contractor_name TEXT,
    contractor_phone TEXT,
    contractor_license TEXT,
    client_name TEXT,
    client_address TEXT,
    work_summary TEXT,
    hours_worked DECIMAL DEFAULT 0,
    hourly_rate DECIMAL DEFAULT 0,
    labor_cost DECIMAL DEFAULT 0,
    materials JSONB DEFAULT '[]',
    materials_cost DECIMAL DEFAULT 0,
    total_due DECIMAL DEFAULT 0,
    payment_info TEXT,
    status TEXT DEFAULT 'unpaid',
    created_at TIMESTAMPTZ DEFAULT NOW()
);
CREATE INDEX idx_invoice_links_uuid ON invoice_links(uuid);
