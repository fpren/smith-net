/**
 * Smith Net Core Types
 * Shared across backend, desktop portal, and gateway
 */

// ════════════════════════════════════════════════════════════════════
// CANONICAL CHANNEL
// ════════════════════════════════════════════════════════════════════

export interface Channel {
  id: string;                    // Canonical UUID - source of truth
  name: string;
  type: 'broadcast' | 'group' | 'dm';
  creatorId: string;
  createdAt: number;
  memberIds: string[];
  isArchived: boolean;
  isDeleted: boolean;
  meshHash?: number;             // 2-byte hash for mesh routing (derived)
}

// ════════════════════════════════════════════════════════════════════
// MESSAGE
// ════════════════════════════════════════════════════════════════════

export type MessageOrigin = 'online' | 'mesh' | 'gateway' | 'online+mesh';

export interface Message {
  id: string;
  channelId: string;
  senderId: string;
  senderName: string;
  content: string;
  timestamp: number;
  origin: MessageOrigin;
  recipientId?: string;          // For DMs
  recipientName?: string;
}

// ════════════════════════════════════════════════════════════════════
// USER / PRESENCE
// ════════════════════════════════════════════════════════════════════

export interface User {
  id: string;
  name: string;
  lastSeen: number;
  status: 'online' | 'away' | 'offline';
  role: 'user' | 'foreman' | 'admin';
}

export interface Presence {
  userId: string;
  userName: string;
  status: 'online' | 'away' | 'offline';
  lastSeen: number;
  connectionType: 'online' | 'mesh' | 'gateway';
}

// ════════════════════════════════════════════════════════════════════
// GATEWAY
// ════════════════════════════════════════════════════════════════════

export type GatewayMode = 'online' | 'gateway' | 'hybrid';

export interface GatewayRelay {
  id: string;
  name: string;
  connectedAt: number;
  lastActivity: number;
  capabilities: string[];
}

export interface GatewayStatus {
  mode: GatewayMode;
  relayConnected: boolean;
  relay?: GatewayRelay;
  lastMeshActivity?: number;
}

// ════════════════════════════════════════════════════════════════════
// WEBSOCKET MESSAGES
// ════════════════════════════════════════════════════════════════════

export type WSMessageType = 
  | 'auth'
  | 'auth_ok'
  | 'auth_error'
  | 'message'
  | 'message_ack'
  | 'message_deleted'
  | 'channel_created'
  | 'channel_updated'
  | 'channel_deleted'
  | 'channel_cleared'
  | 'presence_update'
  | 'gateway_connect'
  | 'gateway_disconnect'
  | 'gateway_message'
  | 'error';

export interface WSMessage {
  type: WSMessageType;
  payload: unknown;
  timestamp: number;
}

// ════════════════════════════════════════════════════════════════════
// API REQUESTS / RESPONSES
// ════════════════════════════════════════════════════════════════════

export interface CreateChannelRequest {
  name: string;
  type: 'broadcast' | 'group' | 'dm';
  memberIds?: string[];
}

export interface InjectMessageRequest {
  channelId: string;
  content: string;
  origin: MessageOrigin;
}

export interface RegisterGatewayRequest {
  relayId: string;
  capabilities: string[];
}
