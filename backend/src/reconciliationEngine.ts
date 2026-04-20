import { UnifiedMessage, VectorClockState, TransportType } from './types';
import { compare, merge } from './vectorClock';
import { pg, isPgEnabled } from './db';

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
  if (!isPgEnabled() || !pg) throw new Error('[Reconcile] Postgres not initialized');
  const { rows: serverMessages } = await pg.query(
    `SELECT id, channel_id, sender_id, sender_name, content, timestamp,
            vector_clock, transport_type, media_type, media_url,
            ai_generated, ai_model
       FROM message_bus_messages
      WHERE channel_id = $1
      ORDER BY timestamp ASC`,
    [req.channelId]
  );

  const serverIds = new Set(serverMessages.map((m: any) => m.id));
  const clientIds = new Set(req.localMessageIds);

  // pg returns BIGINT as string; Supabase returns ISO-8601 string; both need normalizing to ms.
  const toTs = (v: any): number => {
    if (typeof v === 'number') return v;
    if (typeof v === 'string') {
      if (/^\d+$/.test(v)) return Number(v);        // numeric BIGINT string from pg
      const parsed = new Date(v).getTime();          // ISO string from Supabase
      return Number.isFinite(parsed) ? parsed : 0;
    }
    return Number(v) || 0;
  };

  const missingOnClient: UnifiedMessage[] = serverMessages
    .filter((m: any) => !clientIds.has(m.id))
    .map((row: any) => ({
      id: row.id,
      channelId: row.channel_id,
      senderId: row.sender_id,
      senderName: row.sender_name,
      content: row.content,
      timestamp: toTs(row.timestamp),
      vectorClock: row.vector_clock,
      transportType: row.transport_type as TransportType,
      mediaType: row.media_type,
      mediaUrl: row.media_url,
      aiGenerated: row.ai_generated,
      aiModel: row.ai_model,
    }));

  const missingOnServer = req.localMessageIds.filter(id => !serverIds.has(id));

  let mergedClock = req.localClock;
  for (const msg of serverMessages) {
    mergedClock = merge(mergedClock, msg.vector_clock);
  }

  return { missingOnClient, missingOnServer, mergedClock };
}

export async function acceptClientMessages(messages: UnifiedMessage[]): Promise<void> {
  if (messages.length === 0) return;
  if (!isPgEnabled() || !pg) throw new Error('[Reconcile] Postgres not initialized');
  for (const m of messages) {
    await pg.query(
      `INSERT INTO message_bus_messages
         (id, channel_id, sender_id, sender_name, content, timestamp,
          vector_clock, transport_type, media_type, media_url,
          ai_generated, ai_model, synced_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7::jsonb,$8,$9,$10,$11,$12,NOW())
       ON CONFLICT (id) DO UPDATE SET
         content = EXCLUDED.content,
         vector_clock = EXCLUDED.vector_clock,
         synced_at = NOW()`,
      [
        m.id,
        m.channelId,
        m.senderId,
        m.senderName,
        m.content,
        m.timestamp,
        JSON.stringify(m.vectorClock),
        m.transportType,
        m.mediaType || 'TEXT',
        m.mediaUrl || null,
        m.aiGenerated || false,
        m.aiModel || null,
      ]
    );
  }
}

export function sortByCausalOrder(messages: UnifiedMessage[]): UnifiedMessage[] {
  return [...messages].sort((a, b) => {
    const cmp = compare(a.vectorClock, b.vectorClock);
    if (cmp !== 0) return cmp;
    return a.timestamp - b.timestamp;
  });
}
