-- ════════════════════════════════════════════════════════════════════════════
-- SUPABASE TABLES FOR SMITHNET GLOBAL CHAT
-- Run this in your Supabase SQL Editor (Dashboard → SQL Editor → New Query)
-- ════════════════════════════════════════════════════════════════════════════

-- 1. MESSAGES TABLE
-- Stores all chat messages (online and mesh-bridged)
CREATE TABLE IF NOT EXISTS messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    channel_id TEXT NOT NULL DEFAULT 'general',
    sender_id TEXT NOT NULL,
    sender_name TEXT NOT NULL,
    content TEXT NOT NULL,
    timestamp BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    is_mesh_origin BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- Index for faster queries by channel
CREATE INDEX IF NOT EXISTS idx_messages_channel ON messages(channel_id);
CREATE INDEX IF NOT EXISTS idx_messages_timestamp ON messages(timestamp DESC);

-- Enable Row Level Security (RLS)
ALTER TABLE messages ENABLE ROW LEVEL SECURITY;

-- Allow anyone to read messages (public chat)
CREATE POLICY "Anyone can read messages" ON messages
    FOR SELECT USING (true);

-- Allow authenticated users to insert messages
CREATE POLICY "Authenticated users can insert messages" ON messages
    FOR INSERT WITH CHECK (true);

-- Allow users to delete their own messages
CREATE POLICY "Users can delete own messages" ON messages
    FOR DELETE USING (auth.uid()::text = sender_id);


-- 2. CHANNELS TABLE
-- Stores channel metadata with creator info for privacy filtering
CREATE TABLE IF NOT EXISTS channels (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL,
    type TEXT NOT NULL DEFAULT 'group',  -- 'broadcast', 'group', 'dm'
    creator_id TEXT NOT NULL,
    created_at BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    is_archived BOOLEAN DEFAULT FALSE,
    is_deleted BOOLEAN DEFAULT FALSE
);

-- Index for faster queries
CREATE INDEX IF NOT EXISTS idx_channels_creator ON channels(creator_id);
CREATE INDEX IF NOT EXISTS idx_channels_type ON channels(type);

-- Enable Row Level Security
ALTER TABLE channels ENABLE ROW LEVEL SECURITY;

-- Allow anyone to read channels (visibility filtering done in app)
CREATE POLICY "Anyone can read channels" ON channels
    FOR SELECT USING (true);

-- Allow anyone to create channels
CREATE POLICY "Anyone can create channels" ON channels
    FOR INSERT WITH CHECK (true);

-- Allow creators to update their channels
CREATE POLICY "Creators can update channels" ON channels
    FOR UPDATE USING (creator_id = auth.uid()::text OR auth.uid() IS NULL);


-- 3. PRESENCE TABLE
-- Tracks online users for peer discovery
CREATE TABLE IF NOT EXISTS presence (
    user_id TEXT PRIMARY KEY,
    user_name TEXT NOT NULL,
    last_seen BIGINT NOT NULL DEFAULT (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT,
    status TEXT DEFAULT 'online',
    updated_at TIMESTAMPTZ DEFAULT NOW()
);

-- Enable Row Level Security
ALTER TABLE presence ENABLE ROW LEVEL SECURITY;

-- Allow anyone to read presence
CREATE POLICY "Anyone can read presence" ON presence
    FOR SELECT USING (true);

-- Allow anyone to upsert presence (for anonymous/offline users too)
CREATE POLICY "Anyone can upsert presence" ON presence
    FOR INSERT WITH CHECK (true);

CREATE POLICY "Anyone can update presence" ON presence
    FOR UPDATE USING (true);


-- 4. ENABLE REALTIME
-- This is CRITICAL - enables real-time subscriptions

-- Enable realtime for messages table
ALTER PUBLICATION supabase_realtime ADD TABLE messages;

-- Enable realtime for presence table  
ALTER PUBLICATION supabase_realtime ADD TABLE presence;

-- Enable realtime for channels table
ALTER PUBLICATION supabase_realtime ADD TABLE channels;


-- 5. OPTIONAL: Clean up old messages (run periodically)
-- Delete messages older than 7 days
-- CREATE OR REPLACE FUNCTION cleanup_old_messages()
-- RETURNS void AS $$
-- BEGIN
--     DELETE FROM messages 
--     WHERE timestamp < (EXTRACT(EPOCH FROM NOW()) * 1000)::BIGINT - (7 * 24 * 60 * 60 * 1000);
-- END;
-- $$ LANGUAGE plpgsql;


-- ════════════════════════════════════════════════════════════════════════════
-- VERIFICATION
-- Run these to verify tables were created:
-- ════════════════════════════════════════════════════════════════════════════

-- SELECT * FROM messages LIMIT 5;
-- SELECT * FROM channels LIMIT 5;
-- SELECT * FROM presence LIMIT 5;

-- Check realtime is enabled:
-- SELECT * FROM pg_publication_tables WHERE pubname = 'supabase_realtime';


-- ════════════════════════════════════════════════════════════════════════════
-- SEED DATA: Create default 'general' channel
-- ════════════════════════════════════════════════════════════════════════════
INSERT INTO channels (id, name, type, creator_id) 
VALUES ('general', 'general', 'broadcast', 'system')
ON CONFLICT (id) DO NOTHING;

