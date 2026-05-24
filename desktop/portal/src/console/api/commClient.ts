// desktop/portal/src/console/api/commClient.ts
//
// REST wrapper for the channels + messages backend (backend/src/channelsRoutes.ts).
// The backend returns bare arrays for /api/channels and /api/channels/:id/messages
// (res.json(channels) / res.json(messages)) and a flat Message body (spread,
// not wrapped) for /api/messages/inject — handlers below normalize all three
// shapes to a stable { ok, channels|messages|message } envelope.

import type { Channel, Message } from '../../types';

export type CommResult<T> =
  | ({ ok: true } & T)
  | { ok: false; status: number; error: string };

interface JsonInit { method?: string; body?: unknown }

async function fetchJson(path: string, init: JsonInit = {}): Promise<
  | { ok: true; data: unknown }
  | { ok: false; status: number; error: string }
> {
  const res = await fetch(path, {
    method: init.method ?? 'GET',
    credentials: 'include',
    headers: init.body !== undefined ? { 'Content-Type': 'application/json' } : undefined,
    body: init.body !== undefined ? JSON.stringify(init.body) : undefined,
  });

  if (res.status === 204) {
    return { ok: true, data: null };
  }

  if (!res.ok) {
    const errBody = await res.json().catch(() => ({ error: res.statusText }));
    return {
      ok: false,
      status: res.status,
      error: errBody.error || 'Request failed',
    };
  }

  const data = await res.json();
  return { ok: true, data };
}

export const commClient = {
  listChannels: async (): Promise<CommResult<{ channels: Channel[] }>> => {
    const r = await fetchJson('/api/channels');
    if (!r.ok) return r;
    const channels = Array.isArray(r.data) ? (r.data as Channel[]) : ((r.data as any).channels ?? []);
    return { ok: true, channels };
  },

  listMessages: async (channelId: string, limit = 100): Promise<CommResult<{ messages: Message[] }>> => {
    const r = await fetchJson(`/api/channels/${encodeURIComponent(channelId)}/messages?limit=${limit}`);
    if (!r.ok) return r;
    const messages = Array.isArray(r.data) ? (r.data as Message[]) : ((r.data as any).messages ?? []);
    return { ok: true, messages };
  },

  deleteMessage: async (messageId: string): Promise<CommResult<{}>> => {
    const r = await fetchJson(`/api/messages/${encodeURIComponent(messageId)}`, { method: 'DELETE' });
    if (!r.ok) return r;
    return { ok: true };
  },

  createChannel: async (input: {
    name: string;
    type: Channel['type'];
    visibility?: string;
    memberIds?: string[];
  }): Promise<CommResult<{ channel: Channel }>> => {
    const r = await fetchJson('/api/channels', { method: 'POST', body: input });
    if (!r.ok) return r;
    // POST /channels responds with the bare channel object (res.json(channel));
    // tolerate a { channel } wrapper too.
    const data = r.data as any;
    const channel = (data && data.channel ? data.channel : data) as Channel;
    return { ok: true, channel };
  },

  send: async (channelId: string, content: string): Promise<CommResult<{ message: Message }>> => {
    const r = await fetchJson('/api/messages/inject', {
      method: 'POST',
      body: { channelId, content },
    });
    if (!r.ok) return r;
    // /messages/inject responds with ...message + meshInjected/relayCount. Strip
    // the bookkeeping fields and surface only the Message shape consumers expect.
    const body = r.data as any;
    const { meshInjected: _m, relayCount: _r, ...rest } = body ?? {};
    return { ok: true, message: rest as Message };
  },
};
