// desktop/portal/src/console/api/commClient.ts
//
// REST wrapper for the channels + messages backend (backend/src/channelsRoutes.ts).
// The backend returns bare arrays for /api/channels and /api/channels/:id/messages
// (res.json(channels) / res.json(messages)) and a flat Message body (spread,
// not wrapped) for /api/messages/inject — handlers below normalize all three
// shapes to a stable { ok, channels|messages|message } envelope.

import type { Channel, Message, MediaAttachment } from '../../types';

// Directory/profile shape returned by backend/src/profilesRoutes.ts mapProfile().
export interface Profile {
  id: string;
  email?: string;
  displayName: string;
  role?: string;
  publicId?: string | null;
  avatarUrl?: string | null;
  organizationId?: string | null;
}

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

  send: async (
    channelId: string,
    content: string,
    opts?: { id?: string; media?: MediaAttachment }
  ): Promise<CommResult<{ message: Message }>> => {
    const r = await fetchJson('/api/messages/inject', {
      method: 'POST',
      body: { channelId, content, id: opts?.id, media: opts?.media },
    });
    if (!r.ok) return r;
    // /messages/inject responds with ...message + meshInjected/relayCount. Strip
    // the bookkeeping fields and surface only the Message shape consumers expect.
    const body = r.data as any;
    const { meshInjected: _m, relayCount: _r, ...rest } = body ?? {};
    return { ok: true, message: rest as Message };
  },

  // Start (or return existing) a direct message with the owner of a public id.
  // Cross-org by design — this is how two solos reach each other. Backend:
  // POST /api/dm {publicId} (channelsRoutes.ts).
  createDm: async (publicId: string): Promise<CommResult<{ channel: Channel }>> => {
    const r = await fetchJson('/api/dm', { method: 'POST', body: { publicId } });
    if (!r.ok) return r;
    const data = r.data as any;
    const channel = (data && data.channel ? data.channel : data) as Channel;
    return { ok: true, channel };
  },

  // The caller's own profile (public id + avatar) — not carried on the auth
  // user object. Backend: GET /api/profiles/me.
  getMe: async (): Promise<CommResult<{ profile: Profile | null }>> => {
    const r = await fetchJson('/api/profiles/me');
    if (!r.ok) return r;
    return { ok: true, profile: (r.data as any).profile ?? null };
  },

  // Look up one profile by 8-char public id. Backend: GET /api/profiles/lookup.
  lookupProfile: async (publicId: string): Promise<CommResult<{ profile: Profile | null }>> => {
    const r = await fetchJson(`/api/profiles/lookup?publicId=${encodeURIComponent(publicId)}`);
    if (!r.ok) return r;
    return { ok: true, profile: (r.data as any).profile ?? null };
  },

  // Everyone in the caller's org (excl. self). Backend: GET /api/profiles/teammates.
  listTeammates: async (): Promise<CommResult<{ profiles: Profile[] }>> => {
    const r = await fetchJson('/api/profiles/teammates');
    if (!r.ok) return r;
    return { ok: true, profiles: (r.data as any).profiles ?? [] };
  },

  // Multipart avatar upload. Backend: POST /api/profile/avatar -> { avatarUrl }.
  uploadAvatar: async (file: File): Promise<CommResult<{ avatarUrl: string }>> => {
    const form = new FormData();
    form.append('file', file);
    const res = await fetch('/api/profile/avatar', {
      method: 'POST',
      credentials: 'include',
      body: form,
    });
    if (!res.ok) {
      const errBody = await res.json().catch(() => ({ error: res.statusText }));
      return { ok: false, status: res.status, error: errBody.error || 'Upload failed' };
    }
    const data = await res.json();
    return { ok: true, avatarUrl: data.avatarUrl };
  },
};
