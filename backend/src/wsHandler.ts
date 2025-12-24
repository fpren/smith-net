/**
 * WebSocket Handler
 * Real-time messaging for desktop portal and mobile online clients
 */

import { WebSocket, WebSocketServer } from 'ws';
import { v4 as uuidv4 } from 'uuid';
import { WSMessage, Message } from './types';
import { channelRegistry } from './channelRegistry';
import { messageStore } from './messageStore';
import { presenceManager } from './presenceManager';
import { gatewayManager } from './gatewayManager';

interface AuthenticatedClient {
  ws: WebSocket;
  userId: string;
  userName: string;
  subscribedChannels: Set<string>;
  isRelay: boolean;
  relayId?: string;
}

class WSHandler {
  private clients: Map<WebSocket, AuthenticatedClient> = new Map();
  private wss: WebSocketServer | null = null;

  /**
   * Initialize WebSocket server
   */
  initialize(wss: WebSocketServer): void {
    this.wss = wss;

    wss.on('connection', (ws) => {
      console.log('[WS] New connection');

      ws.on('message', (data) => {
        try {
          const msg: WSMessage = JSON.parse(data.toString());
          this.handleMessage(ws, msg);
        } catch (e) {
          this.sendError(ws, 'Invalid message format');
        }
      });

      ws.on('close', () => {
        this.handleDisconnect(ws);
      });

      ws.on('error', (err) => {
        console.error('[WS] Error:', err);
        this.handleDisconnect(ws);
      });
    });

    // Subscribe to gateway messages
    gatewayManager.onMessage((message, relayId) => {
      this.broadcastToChannel(message.channelId, message);
    });

    console.log('[WS] Handler initialized');
  }

  /**
   * Handle incoming WebSocket message
   */
  private handleMessage(ws: WebSocket, msg: WSMessage): void {
    switch (msg.type) {
      case 'auth':
        this.handleAuth(ws, msg.payload as { userId: string; userName: string; isRelay?: boolean; relayId?: string });
        break;

      case 'message':
        this.handleChatMessage(ws, msg.payload as { channelId: string; content: string; recipientId?: string; recipientName?: string });
        break;

      case 'gateway_connect':
        this.handleGatewayConnect(ws, msg.payload as { relayId: string; name: string; capabilities: string[] });
        break;

      case 'gateway_message':
        this.handleGatewayMessage(ws, msg.payload as Message);
        break;

      default:
        this.sendError(ws, `Unknown message type: ${msg.type}`);
    }
  }

  /**
   * Handle authentication
   */
  private handleAuth(ws: WebSocket, payload: { userId: string; userName: string; isRelay?: boolean; relayId?: string }): void {
    const { userId, userName, isRelay, relayId } = payload;

    const client: AuthenticatedClient = {
      ws,
      userId,
      userName,
      subscribedChannels: new Set(),
      isRelay: isRelay || false,
      relayId,
    };

    this.clients.set(ws, client);

    // Update presence
    presenceManager.update(userId, userName, 'online', isRelay ? 'gateway' : 'online');

    // Subscribe user to all appropriate channels (auto-adds to broadcast channels)
    const channelIds = channelRegistry.subscribeUserToChannels(userId);
    for (const channelId of channelIds) {
      client.subscribedChannels.add(channelId);
    }

    // Get full channel info for response
    const channels = channelRegistry.listForUser(userId);

    // Send auth confirmation
    this.send(ws, {
      type: 'auth_ok',
      payload: {
        userId,
        channels: channels.map(c => ({ id: c.id, name: c.name, type: c.type })),
      },
      timestamp: Date.now(),
    });

    // Broadcast presence update
    this.broadcastPresence();

    console.log(`[WS] Authenticated: ${userName} (${userId}) - subscribed to ${channelIds.length} channels`);
  }

  /**
   * Handle chat message
   */
  private handleChatMessage(
    ws: WebSocket,
    payload: { channelId: string; content: string; recipientId?: string; recipientName?: string }
  ): void {
    const client = this.clients.get(ws);
    if (!client) {
      this.sendError(ws, 'Not authenticated');
      return;
    }

    const { channelId, content, recipientId, recipientName } = payload;

    // Store message
    const message = messageStore.add(
      channelId,
      client.userId,
      client.userName,
      content,
      'online',
      recipientId,
      recipientName
    );

    // Send ACK
    this.send(ws, {
      type: 'message_ack',
      payload: { messageId: message.id },
      timestamp: Date.now(),
    });

    // Broadcast to channel subscribers
    this.broadcastToChannel(channelId, message);

    // Inject into mesh via any connected relay
    if (gatewayManager.hasConnectedRelay()) {
      const relays = gatewayManager.getAll();
      for (const relay of relays) {
        gatewayManager.injectMessage(relay.id, message);
      }
    }
  }

  /**
   * Handle gateway relay connection
   */
  private handleGatewayConnect(
    ws: WebSocket,
    payload: { relayId: string; name: string; capabilities: string[] }
  ): void {
    const { relayId, name, capabilities } = payload;

    // Register relay
    const relay = gatewayManager.register(relayId, name, capabilities, ws);

    // Update client
    const client = this.clients.get(ws);
    if (client) {
      client.isRelay = true;
      client.relayId = relayId;
    }

    // Confirm
    this.send(ws, {
      type: 'gateway_connect',
      payload: { relay },
      timestamp: Date.now(),
    });

    console.log(`[WS] Gateway connected: ${name}`);
  }

  /**
   * Handle mesh message from gateway
   */
  private handleGatewayMessage(ws: WebSocket, message: Message): void {
    const client = this.clients.get(ws);
    if (!client || !client.isRelay || !client.relayId) {
      this.sendError(ws, 'Not a registered gateway');
      return;
    }

    // Resolve channel ID - mesh uses channel name, we need UUID
    let resolvedChannelId = message.channelId;
    
    // Check if it's not a UUID (mesh uses channel names like "general")
    if (!message.channelId.includes('-')) {
      const channel = channelRegistry.findByName(message.channelId);
      if (channel) {
        resolvedChannelId = channel.id;
        console.log(`[Gateway] Resolved channel "${message.channelId}" -> ${resolvedChannelId}`);
      } else {
        console.log(`[Gateway] Unknown channel: ${message.channelId}`);
        return;
      }
    }

    // Forward to gateway manager
    gatewayManager.onMeshMessage(client.relayId, { ...message, channelId: resolvedChannelId });

    // Store message with resolved channel ID
    messageStore.add(
      resolvedChannelId,
      message.senderId,
      message.senderName,
      message.content,
      'mesh',
      message.recipientId,
      message.recipientName
    );
    
    console.log(`[Gateway] Stored mesh message: "${message.content}" from ${message.senderName}`);
  }

  /**
   * Handle client disconnect
   */
  private handleDisconnect(ws: WebSocket): void {
    const client = this.clients.get(ws);
    if (client) {
      presenceManager.setOffline(client.userId);
      
      if (client.isRelay && client.relayId) {
        gatewayManager.unregister(client.relayId);
      }

      this.clients.delete(ws);
      this.broadcastPresence();
      console.log(`[WS] Disconnected: ${client.userName}`);
    }
  }

  /**
   * Send message to client
   */
  private send(ws: WebSocket, msg: WSMessage): void {
    if (ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify(msg));
    }
  }

  /**
   * Send error to client
   */
  private sendError(ws: WebSocket, error: string): void {
    this.send(ws, {
      type: 'error',
      payload: { error },
      timestamp: Date.now(),
    });
  }

  /**
   * Broadcast message to all subscribers of a channel
   */
  broadcastToChannel(channelId: string, message: Message): void {
    const wsMsg: WSMessage = {
      type: 'message',
      payload: message,
      timestamp: Date.now(),
    };

    for (const [ws, client] of this.clients) {
      if (client.subscribedChannels.has(channelId)) {
        this.send(ws, wsMsg);
      }
    }
  }

  /**
   * Broadcast presence update to all clients
   */
  private broadcastPresence(): void {
    const presence = presenceManager.getOnline();
    const wsMsg: WSMessage = {
      type: 'presence_update',
      payload: presence,
      timestamp: Date.now(),
    };

    for (const [ws] of this.clients) {
      this.send(ws, wsMsg);
    }
  }

  /**
   * Broadcast channel event to all clients
   */
  broadcastChannelEvent(type: 'channel_created' | 'channel_updated' | 'channel_deleted' | 'channel_cleared' | 'message_deleted', channel: unknown): void {
    const wsMsg: WSMessage = {
      type,
      payload: channel,
      timestamp: Date.now(),
    };

    for (const [ws] of this.clients) {
      this.send(ws, wsMsg);
    }
  }
}

export const wsHandler = new WSHandler();
