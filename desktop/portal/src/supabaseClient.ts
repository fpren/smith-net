/**
 * Supabase Client for Desktop Portal
 * Enables global chat via Supabase Realtime
 */

import { createClient, SupabaseClient, RealtimeChannel } from '@supabase/supabase-js';
import { Message, Channel } from './types';

// Supabase configuration (same as Android app)
const SUPABASE_URL = 'https://bhmeeuzjfniuocovwbyl.supabase.co';
const SUPABASE_ANON_KEY = 'eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJobWVldXpqZm5pdW9jb3Z3YnlsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NjY2MzEzNjksImV4cCI6MjA4MjIwNzM2OX0.SC_I94o68Q86rzaHi1Ojz_CeWa4rY7Le5y7b4-AyHgc';

// Media attachment type
interface MediaAttachment {
  type: 'image' | 'voice' | 'video' | 'file';
  url: string;
  filename?: string;
  mimeType?: string;
  size?: number;
  duration?: number;
}

// Message row type (matches Supabase table)
interface MessageRow {
  id: string;
  channel_id: string;
  sender_id: string;
  sender_name: string;
  content: string;
  timestamp: number;
  is_mesh_origin: boolean;
  created_at?: string;
  media_type?: string | null;
  media_url?: string | null;
  media_filename?: string | null;
  media_size?: number | null;
  media_duration?: number | null;
}

// Presence row type
interface PresenceRow {
  user_id: string;
  user_name: string;
  last_seen: number;
  status: string;
}

// Channel row type (matches Supabase table)
interface ChannelRow {
  id: string;
  name: string;
  type: string;
  creator_id: string;
  created_at: number;
  is_archived: boolean;
  is_deleted: boolean;
}

type MessageHandler = (message: Message) => void;
type PresenceHandler = (users: PresenceRow[]) => void;

class SupabaseChatClient {
  private client: SupabaseClient;
  private messagesChannel: RealtimeChannel | null = null;
  private presenceChannel: RealtimeChannel | null = null;
  private messageHandlers: MessageHandler[] = [];
  private presenceHandlers: PresenceHandler[] = [];
  private userId: string = '';
  private userName: string = '';
  private isConnected: boolean = false;

  constructor() {
    this.client = createClient(SUPABASE_URL, SUPABASE_ANON_KEY);
  }

  /**
   * Connect to Supabase Realtime
   */
  async connect(userId: string, userName: string): Promise<void> {
    this.userId = userId;
    this.userName = userName;

    console.log('[Supabase] Connecting...', { userId, userName });

    try {
      // Subscribe to messages table changes (real-time)
      this.messagesChannel = this.client
        .channel('public:messages')
        .on(
          'postgres_changes',
          { event: 'INSERT', schema: 'public', table: 'messages' },
          (payload) => {
            this.handleNewMessage(payload.new as MessageRow);
          }
        )
        .subscribe((status) => {
          console.log('[Supabase] Messages subscription:', status);
        });

      // Subscribe to presence table changes (real-time - instant updates!)
      this.presenceChannel = this.client
        .channel('public:presence')
        .on(
          'postgres_changes',
          { event: '*', schema: 'public', table: 'presence' },
          (payload) => {
            console.log('[Supabase] Presence update:', payload.eventType);
            this.handlePresenceChange(payload);
          }
        )
        .subscribe((status) => {
          console.log('[Supabase] Presence subscription:', status);
          this.isConnected = status === 'SUBSCRIBED';
        });

      // Update our presence
      await this.updatePresence();

      // Load recent messages
      await this.loadRecentMessages();

      // Load current online users
      await this.loadOnlineUsers();
      
      // DEBUG: Show all messages in database
      await this.getAllMessages();

      console.log('[Supabase] Connected successfully (real-time messages + presence)');
    } catch (error) {
      console.error('[Supabase] Connection error:', error);
      throw error;
    }
  }

  /**
   * Disconnect from Supabase Realtime
   */
  disconnect(): void {
    if (this.messagesChannel) {
      this.client.removeChannel(this.messagesChannel);
      this.messagesChannel = null;
    }
    if (this.presenceChannel) {
      this.client.removeChannel(this.presenceChannel);
      this.presenceChannel = null;
    }
    this.isConnected = false;
    console.log('[Supabase] Disconnected');
  }

  /**
   * Send a message via Supabase
   */
  async sendMessage(channelId: string, content: string, media?: MediaAttachment): Promise<void> {
    const row: Partial<MessageRow> = {
      id: crypto.randomUUID(),
      channel_id: channelId,
      sender_id: this.userId,
      sender_name: this.userName,
      content,
      timestamp: Date.now(),
      is_mesh_origin: false,
      media_type: media?.type || null,
      media_url: media?.url || null,
      media_filename: media?.filename || null,
      media_size: media?.size || null,
      media_duration: media?.duration || null,
    };

    const { error } = await this.client.from('messages').insert(row);

    if (error) {
      console.error('[Supabase] Send error:', error);
      throw error;
    }

    console.log('[Supabase] Message sent:', content.substring(0, 30), media ? `[${media.type}]` : '');
  }

  /**
   * Upload a file to Supabase Storage
   */
  async uploadFile(file: File, channelId: string): Promise<MediaAttachment> {
    const fileExt = file.name.split('.').pop() || 'bin';
    const fileName = `${channelId}/${Date.now()}-${crypto.randomUUID()}.${fileExt}`;
    
    console.log('[Supabase] Uploading file:', file.name, 'size:', file.size);

    const { error: uploadError } = await this.client.storage
      .from('media')
      .upload(fileName, file, {
        cacheControl: '3600',
        upsert: false
      });

    if (uploadError) {
      console.error('[Supabase] Upload error:', uploadError);
      throw uploadError;
    }

    // Get public URL
    const { data: urlData } = this.client.storage
      .from('media')
      .getPublicUrl(fileName);

    const mediaType = this.getMediaType(file.type);
    
    console.log('[Supabase] File uploaded:', urlData.publicUrl);

    return {
      type: mediaType,
      url: urlData.publicUrl,
      filename: file.name,
      mimeType: file.type,
      size: file.size,
    };
  }

  /**
   * Determine media type from MIME type
   */
  private getMediaType(mimeType: string): 'image' | 'voice' | 'video' | 'file' {
    if (mimeType.startsWith('image/')) return 'image';
    if (mimeType.startsWith('audio/')) return 'voice';
    if (mimeType.startsWith('video/')) return 'video';
    return 'file';
  }

  /**
   * Load recent messages from Supabase
   */
  private async loadRecentMessages(): Promise<void> {
    const { data, error } = await this.client
      .from('messages')
      .select('*')
      .order('timestamp', { ascending: false })
      .limit(100);

    if (error) {
      console.error('[Supabase] Load messages error:', error);
      return;
    }

    console.log('[Supabase] Loaded', data?.length || 0, 'messages');

    // Notify handlers (in chronological order)
    const messages = (data || []).reverse();
    messages.forEach((row: MessageRow) => {
      const message = this.rowToMessage(row);
      this.messageHandlers.forEach((h) => h(message));
    });
  }

  /**
   * Handle new message from realtime subscription
   */
  private handleNewMessage(row: MessageRow): void {
    console.log('[Supabase] 📨 REALTIME MESSAGE:', {
      id: row.id,
      channel: row.channel_id,
      sender: row.sender_name,
      content: row.content?.substring(0, 30),
    });
    
    // Skip our own messages (we already have them)
    if (row.sender_id === this.userId) {
      console.log('[Supabase] Skipping own message');
      return;
    }

    const message = this.rowToMessage(row);
    console.log('[Supabase] ✓ Notifying', this.messageHandlers.length, 'handlers');
    this.messageHandlers.forEach((h) => h(message));
  }

  /**
   * Convert database row to Message type
   */
  private rowToMessage(row: MessageRow): Message {
    const message: Message = {
      id: row.id,
      channelId: row.channel_id,
      senderId: row.sender_id,
      senderName: row.sender_name,
      content: row.content,
      timestamp: row.timestamp,
      origin: row.is_mesh_origin ? 'mesh' : 'online',
    };

    // Add media attachment if present
    if (row.media_type && row.media_url) {
      message.media = {
        type: row.media_type as 'image' | 'voice' | 'video' | 'file',
        url: row.media_url,
        filename: row.media_filename || undefined,
        size: row.media_size || undefined,
        duration: row.media_duration || undefined,
      };
    }

    return message;
  }

  /**
   * Update our presence
   */
  private async updatePresence(): Promise<void> {
    const { error } = await this.client.from('presence').upsert({
      user_id: this.userId,
      user_name: this.userName,
      last_seen: Date.now(),
      status: 'online',
    });

    if (error) {
      console.warn('[Supabase] Presence update error:', error);
    }
  }

  /**
   * Load online users
   */
  private async loadOnlineUsers(): Promise<void> {
    const recentThreshold = Date.now() - 120000; // 2 minutes

    const { data, error } = await this.client
      .from('presence')
      .select('*')
      .gt('last_seen', recentThreshold);

    if (error) {
      console.warn('[Supabase] Load presence error:', error);
      return;
    }

    this.onlineUsers = (data || []).filter((u: PresenceRow) => u.user_id !== this.userId);
    console.log('[Supabase] Online users:', this.onlineUsers.length);
    this.presenceHandlers.forEach((h) => h(this.onlineUsers));
  }

  // Track online users for real-time updates
  private onlineUsers: PresenceRow[] = [];

  /**
   * Handle real-time presence change (instant - no polling!)
   */
  private handlePresenceChange(payload: { eventType: string; new?: unknown; old?: unknown }): void {
    const { eventType } = payload;
    
    if (eventType === 'INSERT' || eventType === 'UPDATE') {
      const record = payload.new as PresenceRow;
      if (!record || record.user_id === this.userId) return;
      
      console.log('[Supabase] 👤 User online:', record.user_name);
      
      // Update or add user
      const existingIndex = this.onlineUsers.findIndex(u => u.user_id === record.user_id);
      if (existingIndex >= 0) {
        this.onlineUsers[existingIndex] = record;
      } else {
        this.onlineUsers.push(record);
      }
      
      // Notify handlers
      this.presenceHandlers.forEach((h) => h([...this.onlineUsers]));
      
    } else if (eventType === 'DELETE') {
      const record = payload.old as PresenceRow;
      if (!record) return;
      
      console.log('[Supabase] 👤 User offline:', record.user_id);
      
      // Remove user
      this.onlineUsers = this.onlineUsers.filter(u => u.user_id !== record.user_id);
      
      // Notify handlers
      this.presenceHandlers.forEach((h) => h([...this.onlineUsers]));
    }
  }

  /**
   * Refresh online users
   */
  async refreshOnlineUsers(): Promise<void> {
    await this.updatePresence();
    await this.loadOnlineUsers();
  }

  /**
   * Load channels (filtered for privacy)
   * - Shows channels created by this dashboard
   * - Shows public/broadcast/group channels
   * - Hides private DMs unless dashboard created them
   */
  async getChannels(): Promise<Channel[]> {
    const { data, error } = await this.client
      .from('channels')
      .select('*')
      .eq('is_deleted', false)
      .order('created_at', { ascending: true });

    if (error) {
      console.error('[Supabase] Load channels error:', error);
      return [];
    }

    // Filter for privacy
    const filtered = (data || []).filter((row: ChannelRow) => {
      // Always show channels created by this dashboard
      if (row.creator_id === this.userId) return true;
      // Show public/broadcast/group channels (not DMs)
      if (row.type !== 'dm') return true;
      // Hide private DMs from other users
      return false;
    });

    console.log('[Supabase] Channels:', data?.length, 'total,', filtered.length, 'visible');

    return filtered.map((row: ChannelRow) => ({
      id: row.id,
      name: row.name,
      type: row.type as 'broadcast' | 'group' | 'dm',
      creatorId: row.creator_id,
      createdAt: row.created_at,
      memberIds: [],
      isArchived: row.is_archived,
      isDeleted: row.is_deleted,
    }));
  }

  /**
   * Create a new channel
   */
  async createChannel(name: string, type: 'broadcast' | 'group' | 'dm' = 'group'): Promise<Channel> {
    const id = `${name.toLowerCase().replace(/\s+/g, '-')}-${Date.now()}`;
    
    const row: ChannelRow = {
      id,
      name,
      type,
      creator_id: this.userId,
      created_at: Date.now(),
      is_archived: false,
      is_deleted: false,
    };

    const { error } = await this.client.from('channels').insert(row);

    if (error) {
      console.error('[Supabase] Create channel error:', error);
      throw error;
    }

    console.log('[Supabase] Channel created:', name);

    return {
      id: row.id,
      name: row.name,
      type: row.type as 'broadcast' | 'group' | 'dm',
      creatorId: row.creator_id,
      createdAt: row.created_at,
      memberIds: [],
      isArchived: row.is_archived,
      isDeleted: row.is_deleted,
    };
  }

  /**
   * Delete a channel (soft delete)
   * Only the creator can delete their channel
   */
  async deleteChannel(channelId: string): Promise<void> {
    // First verify this user created the channel
    const { data: channel, error: fetchError } = await this.client
      .from('channels')
      .select('creator_id')
      .eq('id', channelId)
      .single();

    if (fetchError) {
      console.error('[Supabase] Delete channel - fetch error:', fetchError);
      throw new Error('Channel not found');
    }

    if (channel?.creator_id !== this.userId) {
      throw new Error('Only the channel creator can delete this channel');
    }

    // Soft delete the channel
    const { error } = await this.client
      .from('channels')
      .update({ is_deleted: true })
      .eq('id', channelId);

    if (error) {
      console.error('[Supabase] Delete channel error:', error);
      throw error;
    }

    console.log('[Supabase] Channel deleted:', channelId);
  }

  /**
   * Get messages for a specific channel
   */
  async getChannelMessages(channelId: string, limit: number = 100): Promise<Message[]> {
    console.log('[Supabase] Loading messages for channel:', channelId);
    
    const { data, error } = await this.client
      .from('messages')
      .select('*')
      .eq('channel_id', channelId)
      .order('timestamp', { ascending: true })
      .limit(limit);

    if (error) {
      console.error('[Supabase] Load channel messages error:', error);
      return [];
    }

    console.log('[Supabase] Found', data?.length || 0, 'messages for channel', channelId);
    return (data || []).map((row: MessageRow) => this.rowToMessage(row));
  }

  /**
   * Get ALL messages (for debugging)
   */
  async getAllMessages(limit: number = 50): Promise<Message[]> {
    const { data, error } = await this.client
      .from('messages')
      .select('*')
      .order('timestamp', { ascending: false })
      .limit(limit);

    if (error) {
      console.error('[Supabase] Load all messages error:', error);
      return [];
    }

    console.log('[Supabase] ALL MESSAGES IN DB:', data?.length || 0);
    data?.forEach((row: MessageRow) => {
      console.log(`  - [${row.channel_id}] ${row.sender_name}: ${row.content?.substring(0, 30)}`);
    });

    return (data || []).map((row: MessageRow) => this.rowToMessage(row));
  }

  // Event handlers
  onMessage(handler: MessageHandler): void {
    this.messageHandlers.push(handler);
  }

  onPresence(handler: PresenceHandler): void {
    this.presenceHandlers.push(handler);
  }

  getIsConnected(): boolean {
    return this.isConnected;
  }

  getUserId(): string {
    return this.userId;
  }

  /**
   * Get all users (for user selection in channels)
   */
  async getAllUsers(): Promise<{ userId: string; userName: string; status: string }[]> {
    const { data, error } = await this.client
      .from('presence')
      .select('user_id, user_name, status, last_seen')
      .order('last_seen', { ascending: false });

    if (error) {
      console.error('[Supabase] Get all users error:', error);
      return [];
    }

    return (data || []).map((row: PresenceRow) => ({
      userId: row.user_id,
      userName: row.user_name,
      status: row.status,
      lastSeen: row.last_seen,
      connectionType: 'online' as const
    }));
  }
}

export const supabaseChat = new SupabaseChatClient();

