/**
 * Smith Net Portal Types
 */

export interface Channel {
  id: string;
  name: string;
  type: 'broadcast' | 'group' | 'dm';
  creatorId: string;
  createdAt: number;
  memberIds: string[];
  isArchived: boolean;
  isDeleted: boolean;
  meshHash?: number;
}

export type MessageOrigin = 'online' | 'mesh' | 'gateway' | 'online+mesh';

export interface Message {
  id: string;
  channelId: string;
  senderId: string;
  senderName: string;
  content: string;
  timestamp: number;
  origin: MessageOrigin;
  recipientId?: string;
  recipientName?: string;
}

export interface Presence {
  userId: string;
  userName: string;
  status: 'online' | 'away' | 'offline';
  lastSeen: number;
  connectionType: 'online' | 'mesh' | 'gateway';
}

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
  relays: GatewayRelay[];
  lastMeshActivity?: number;
}

export type WSMessageType =
  | 'auth'
  | 'auth_ok'
  | 'auth_error'
  | 'message'
  | 'message_ack'
  | 'channel_created'
  | 'channel_updated'
  | 'channel_deleted'
  | 'presence_update'
  | 'error';

export interface WSMessage {
  type: WSMessageType;
  payload: unknown;
  timestamp: number;
}
