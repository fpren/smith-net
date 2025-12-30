// Test script to upload a video to Supabase and send as a message
// Run with: node test-video-upload.js

const { createClient } = require('@supabase/supabase-js');
const fs = require('fs');
const path = require('path');

const SUPABASE_URL = 'https://bhmeeuzjfniuocovwbyl.supabase.co';
const SUPABASE_ANON_KEY = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJobWVldXpqZm5pdW9jb3Z3YnlsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjY2MzEzNjksImV4cCI6MjA4MjIwNzM2OX0.SC_I94o68Q86rzaHi1Ojz_CeWa4rY7Le5y7b4-AyHgc';

const supabase = createClient(SUPABASE_URL, SUPABASE_ANON_KEY);

async function uploadAndSendVideo() {
  const videoPath = 'C:\\Users\\sonic\\Downloads\\grok-video-6ea36c9b-ae52-4fe0-8e2f-2fde0b68bb4f.mp4';
  const fileName = path.basename(videoPath);
  
  console.log('Reading video file:', videoPath);
  const fileBuffer = fs.readFileSync(videoPath);
  
  const storagePath = `general/${Date.now()}-test-video.mp4`;
  
  console.log('Uploading to Supabase Storage:', storagePath);
  const { error: uploadError } = await supabase.storage
    .from('media')
    .upload(storagePath, fileBuffer, {
      contentType: 'video/mp4',
      cacheControl: '3600',
      upsert: false
    });

  if (uploadError) {
    console.error('Upload error:', uploadError);
    return;
  }

  // Get public URL
  const { data: urlData } = supabase.storage.from('media').getPublicUrl(storagePath);
  console.log('Public URL:', urlData.publicUrl);

  // Insert message with video
  const messageRow = {
    id: crypto.randomUUID(),
    channel_id: 'general',
    sender_id: 'test-dashboard',
    sender_name: 'Foreman',
    content: '[VIDEO] test-video.mp4',
    timestamp: Date.now(),
    is_mesh_origin: false,
    media_type: 'video',
    media_url: urlData.publicUrl,
    media_filename: 'test-video.mp4',
    media_size: fileBuffer.length,
    media_duration: null
  };

  console.log('Inserting message:', messageRow.id);
  const { error: insertError } = await supabase.from('messages').insert(messageRow);

  if (insertError) {
    console.error('Insert error:', insertError);
    return;
  }

  console.log('✓ Video message sent successfully!');
  console.log('URL:', urlData.publicUrl);
}

uploadAndSendVideo().catch(console.error);
