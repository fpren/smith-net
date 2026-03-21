import { v4 as uuidv4 } from 'uuid';
import { UnifiedMessage, VectorClockState, TransportType } from './types';
import { increment, merge } from './vectorClock';
import { supabase } from './supabase';

type MessageHandler = (message: UnifiedMessage) => void;

// In-memory state
const channelSubscribers: Map<string, Set<MessageHandler>> = new Map();
const recentMessageIds: Set<string> = new Set();
const MAX_RECENT_IDS = 10000;

// Server's device ID for vector clock
const SERVER_DEVICE_ID = 'server-' + uuidv4().slice(0, 8);
let serverClock: VectorClockState = {};

export function createMessage(
  channelId: string,
  senderId: string,
  senderName: string,
  content: string,
  transportType: TransportType,
  existingId?: string,
  existingClock?: VectorClockState
): UnifiedMessage {
  serverClock = increment(serverClock, SERVER_DEVICE_ID);
  const messageClock = existingClock
    ? merge(increment(existingClock, SERVER_DEVICE_ID), serverClock)
    : { ...serverClock };

  return {
    id: existingId || uuidv4(),
    channelId,
    senderId,
    senderName,
    content,
    timestamp: Date.now(),
    vectorClock: messageClock,
    transportType,
  };
}

export function isDuplicate(messageId: string): boolean {
  if (recentMessageIds.has(messageId)) return true;
  recentMessageIds.add(messageId);
  if (recentMessageIds.size > MAX_RECENT_IDS) {
    const first = recentMessageIds.values().next().value;
    if (first) recentMessageIds.delete(first);
  }
  return false;
}

export function subscribe(channelId: string, handler: MessageHandler): () => void {
  if (!channelSubscribers.has(channelId)) {
    channelSubscribers.set(channelId, new Set());
  }
  channelSubscribers.get(channelId)!.add(handler);
  return () => channelSubscribers.get(channelId)?.delete(handler);
}

export function publish(message: UnifiedMessage): void {
  if (isDuplicate(message.id)) return;

  // Notify all subscribers for this channel
  const handlers = channelSubscribers.get(message.channelId);
  if (handlers) {
    for (const handler of handlers) {
      handler(message);
    }
  }

  // Persist to Supabase asynchronously
  persistMessage(message).catch(err =>
    console.error('[MessageBus] Failed to persist message:', err)
  );
}

async function persistMessage(message: UnifiedMessage): Promise<void> {
  const { error } = await supabase.from('message_bus_messages').upsert({
    id: message.id,
    channel_id: message.channelId,
    sender_id: message.senderId,
    sender_name: message.senderName,
    content: message.content,
    timestamp: new Date(message.timestamp).toISOString(),
    vector_clock: message.vectorClock,
    transport_type: message.transportType,
    media_type: message.mediaType || 'TEXT',
    media_url: message.mediaUrl,
    ai_generated: message.aiGenerated || false,
    ai_model: message.aiModel,
    synced_at: new Date().toISOString(),
  }, { onConflict: 'id' });

  if (error) throw error;
}

export async function getHistory(
  channelId: string,
  limit: number = 100,
  before?: number
): Promise<UnifiedMessage[]> {
  let query = supabase
    .from('message_bus_messages')
    .select('*')
    .eq('channel_id', channelId)
    .order('timestamp', { ascending: false })
    .limit(limit);

  if (before) {
    query = query.lt('timestamp', new Date(before).toISOString());
  }

  const { data, error } = await query;
  if (error) throw error;

  return (data || []).map(row => ({
    id: row.id,
    channelId: row.channel_id,
    senderId: row.sender_id,
    senderName: row.sender_name,
    content: row.content,
    timestamp: new Date(row.timestamp).getTime(),
    vectorClock: row.vector_clock,
    transportType: row.transport_type,
    mediaType: row.media_type,
    mediaUrl: row.media_url,
    aiGenerated: row.ai_generated,
    aiModel: row.ai_model,
  })).reverse();
}
