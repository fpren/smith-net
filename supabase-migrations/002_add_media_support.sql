-- ════════════════════════════════════════════════════════════════════
-- Smith Net: Add Media Support Migration
-- Run this in your Supabase SQL Editor
-- ════════════════════════════════════════════════════════════════════

-- Add media columns to messages table
ALTER TABLE messages 
ADD COLUMN IF NOT EXISTS media_type TEXT,
ADD COLUMN IF NOT EXISTS media_url TEXT,
ADD COLUMN IF NOT EXISTS media_filename TEXT,
ADD COLUMN IF NOT EXISTS media_size BIGINT,
ADD COLUMN IF NOT EXISTS media_duration INTEGER;

-- Create storage bucket for media files (if not exists)
INSERT INTO storage.buckets (id, name, public)
VALUES ('media', 'media', true)
ON CONFLICT (id) DO NOTHING;

-- Allow public read access to media bucket
CREATE POLICY IF NOT EXISTS "Public read access for media"
ON storage.objects FOR SELECT
USING (bucket_id = 'media');

-- Allow authenticated users to upload to media bucket
CREATE POLICY IF NOT EXISTS "Authenticated users can upload media"
ON storage.objects FOR INSERT
WITH CHECK (bucket_id = 'media');

-- Allow users to delete their own uploads
CREATE POLICY IF NOT EXISTS "Users can delete own media"
ON storage.objects FOR DELETE
USING (bucket_id = 'media');

-- Index for faster media queries
CREATE INDEX IF NOT EXISTS idx_messages_media_type ON messages(media_type) WHERE media_type IS NOT NULL;

-- Verify the changes
SELECT column_name, data_type 
FROM information_schema.columns 
WHERE table_name = 'messages' 
AND column_name LIKE 'media_%';
