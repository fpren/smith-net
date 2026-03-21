import { UnifiedMessage, VectorClockState } from './types';
import { compare, merge } from './vectorClock';
import { supabase } from './supabase';

export interface ReconciliationRequest {
  channelId: string;
  localMessageIds: string[];
  localClock: VectorClockState;
}

export interface ReconciliationResponse {
  missingOnClient: UnifiedMessage[];
  missingOnServer: string[];
  mergedClock: VectorClockState;
}

export async function reconcile(req: ReconciliationRequest): Promise<ReconciliationResponse> {
  const { data: serverMessages, error } = await supabase
    .from('message_bus_messages')
    .select('*')
    .eq('channel_id', req.channelId)
    .order('timestamp', { ascending: true });

  if (error) throw error;

  const serverIds = new Set((serverMessages || []).map(m => m.id));
  const clientIds = new Set(req.localMessageIds);

  const missingOnClient: UnifiedMessage[] = (serverMessages || [])
    .filter(m => !clientIds.has(m.id))
    .map(row => ({
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
    }));

  const missingOnServer = req.localMessageIds.filter(id => !serverIds.has(id));

  let mergedClock = req.localClock;
  for (const msg of serverMessages || []) {
    mergedClock = merge(mergedClock, msg.vector_clock);
  }

  return { missingOnClient, missingOnServer, mergedClock };
}

export async function acceptClientMessages(messages: UnifiedMessage[]): Promise<void> {
  if (messages.length === 0) return;

  const rows = messages.map(m => ({
    id: m.id,
    channel_id: m.channelId,
    sender_id: m.senderId,
    sender_name: m.senderName,
    content: m.content,
    timestamp: new Date(m.timestamp).toISOString(),
    vector_clock: m.vectorClock,
    transport_type: m.transportType,
    media_type: m.mediaType || 'TEXT',
    media_url: m.mediaUrl,
    ai_generated: m.aiGenerated || false,
    ai_model: m.aiModel,
    synced_at: new Date().toISOString(),
  }));

  const { error } = await supabase
    .from('message_bus_messages')
    .upsert(rows, { onConflict: 'id' });

  if (error) throw error;
}

export function sortByCausalOrder(messages: UnifiedMessage[]): UnifiedMessage[] {
  return [...messages].sort((a, b) => {
    const cmp = compare(a.vectorClock, b.vectorClock);
    if (cmp !== 0) return cmp;
    return a.timestamp - b.timestamp;
  });
}
