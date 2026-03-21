-- Message Bus: Unified message storage for BLE mesh + IP chat
-- All messages stored here regardless of transport path

CREATE TABLE IF NOT EXISTS message_bus_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    channel_id UUID NOT NULL REFERENCES channels(id) ON DELETE CASCADE,
    sender_id UUID NOT NULL,
    sender_name TEXT NOT NULL,
    content TEXT NOT NULL DEFAULT '',
    timestamp TIMESTAMPTZ NOT NULL DEFAULT now(),
    vector_clock JSONB NOT NULL DEFAULT '{}',
    transport_type TEXT NOT NULL CHECK (transport_type IN ('ble', 'ip', 'supabase', 'gateway')),
    media_type TEXT NOT NULL DEFAULT 'TEXT' CHECK (media_type IN ('TEXT', 'IMAGE', 'VOICE', 'VIDEO', 'FILE')),
    media_url TEXT,
    ai_generated BOOLEAN NOT NULL DEFAULT false,
    ai_model TEXT,
    synced_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Index for channel history queries (most common access pattern)
CREATE INDEX idx_mbm_channel_timestamp ON message_bus_messages(channel_id, timestamp DESC);

-- Index for reconciliation: find unsynced messages
CREATE INDEX idx_mbm_unsynced ON message_bus_messages(synced_at) WHERE synced_at IS NULL;

-- Index for sender queries
CREATE INDEX idx_mbm_sender ON message_bus_messages(sender_id);

-- RLS: users can read messages in channels they belong to
ALTER TABLE message_bus_messages ENABLE ROW LEVEL SECURITY;

-- NOTE: channels table uses type/is_archived rather than a visibility column.
-- Broadcast channels that are not archived are treated as publicly readable;
-- all other channels require explicit membership via channel_members.
CREATE POLICY "Users can read channel messages"
    ON message_bus_messages FOR SELECT
    USING (
        channel_id IN (
            SELECT id FROM channels
            WHERE (type = 'broadcast' AND is_archived = false)
            OR id IN (SELECT channel_id FROM channel_members WHERE user_id = auth.uid())
        )
    );

CREATE POLICY "Authenticated users can insert messages"
    ON message_bus_messages FOR INSERT
    WITH CHECK (auth.uid() IS NOT NULL);
