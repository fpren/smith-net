/**
 * Smith Net API Client
 */

import { Channel, Message, Presence, GatewayStatus } from './types';

const API_BASE = '/api';

async function fetchJson<T>(url: string, options?: RequestInit): Promise<T> {
  const res = await fetch(API_BASE + url, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      'X-User-Id': localStorage.getItem('userId') || '',
      'X-User-Name': localStorage.getItem('userName') || '',
      ...options?.headers,
    },
  });
  
  if (!res.ok) {
    const error = await res.json().catch(() => ({ error: res.statusText }));
    throw new Error(error.error || 'Request failed');
  }
  
  if (res.status === 204) return undefined as T;
  return res.json();
}

// ════════════════════════════════════════════════════════════════════
// CHANNELS
// ════════════════════════════════════════════════════════════════════

export async function getChannels(): Promise<Channel[]> {
  return fetchJson('/channels');
}

export async function listChannels(): Promise<Channel[]> {
  return fetchJson('/channels');
}

export async function getChannel(id: string): Promise<Channel> {
  return fetchJson(`/channels/${id}`);
}

export async function createChannel(name: string, type: Channel['type'], memberIds?: string[]): Promise<Channel> {
  return fetchJson('/channels', {
    method: 'POST',
    body: JSON.stringify({ name, type, memberIds }),
  });
}

export async function deleteChannel(id: string): Promise<void> {
  return fetchJson(`/channels/${id}`, { method: 'DELETE' });
}

export async function clearChannel(id: string): Promise<void> {
  return fetchJson(`/channels/${id}/messages`, { method: 'DELETE' });
}

// ════════════════════════════════════════════════════════════════════
// MESSAGES
// ════════════════════════════════════════════════════════════════════

export async function getMessages(channelId: string, limit = 100): Promise<Message[]> {
  return fetchJson(`/channels/${channelId}/messages?limit=${limit}`);
}

/**
 * Send message with smart routing
 * - Always broadcasts to online clients
 * - Automatically injects to mesh if relay is connected
 */
export async function sendMessage(channelId: string, content: string): Promise<Message & { meshInjected?: boolean; relayCount?: number }> {
  return fetchJson('/messages/inject', {
    method: 'POST',
    body: JSON.stringify({ channelId, content }),
  });
}

export async function injectViaGateway(channelId: string, content: string): Promise<{ message: Message; injectedToRelays: number }> {
  return fetchJson('/gateway/inject', {
    method: 'POST',
    body: JSON.stringify({ channelId, content }),
  });
}

// ════════════════════════════════════════════════════════════════════
// PRESENCE
// ════════════════════════════════════════════════════════════════════

export async function getPresence(): Promise<Presence[]> {
  return fetchJson('/presence');
}

export async function getOnlinePresence(): Promise<Presence[]> {
  return fetchJson('/presence/online');
}

// ════════════════════════════════════════════════════════════════════
// GATEWAY
// ════════════════════════════════════════════════════════════════════

export async function getGatewayStatus(): Promise<GatewayStatus> {
  return fetchJson('/gateway/status');
}

export async function disconnectRelay(relayId: string): Promise<{ success: boolean; disconnected: string }> {
  return fetchJson(`/gateway/relays/${relayId}`, { method: 'DELETE' });
}

// ════════════════════════════════════════════════════════════════════
// HEALTH
// ════════════════════════════════════════════════════════════════════

export async function getHealth(): Promise<{ status: string; timestamp: number; channels: number; onlineUsers: number; relays: number }> {
  return fetchJson('/health');
}
