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
import { createMessage, publish, subscribe } from './messageBus';
import { requestLogger } from './log';

interface AuthenticatedClient {
  ws: WebSocket;
  userId: string;
  userName: string;
  organizationId: string;
  subscribedChannels: Set<string>;
  isRelay: boolean;
  relayId?: string;
  channelUnsubs: Map<string, () => void>;
}

class WSHandler {
  private clients: Map<WebSocket, AuthenticatedClient> = new Map();
  private wss: WebSocketServer | null = null;
  private presenceInterval: NodeJS.Timeout | null = null;

  /**
   * Initialize WebSocket server
   */
  initialize(wss: WebSocketServer): void {
    this.wss = wss;

    // Keep presence alive for all connected clients every 30 seconds
    this.presenceInterval = setInterval(() => {
      this.refreshAllPresence();
    }, 30_000);

    // Subscribe to gateway messages
    gatewayManager.onMessage((message, _relayId) => {
      this.broadcastToChannel(message.channelId, message);
    });

    requestLogger().info({ event: 'ws_handler_initialized' }, 'ws handler initialized');
  }

  /**
   * Phase 2 Slice 3 entry point. Called by wsAuth.setupWsServer after JWT
   * validation. Identity is trusted (comes from the validated JWT). Sets up
   * the post-connect state that handleAuth used to do.
   */
  async onConnection(ws: WebSocket, identity: { userId: string; userName: string; email: string; role: string; organizationId: string }): Promise<void> {
    const { userId, userName, organizationId } = identity;

    const client: AuthenticatedClient = {
      ws,
      userId,
      userName,
      organizationId,
      subscribedChannels: new Set(),
      isRelay: false,
      relayId: undefined,
      channelUnsubs: new Map(),
    };
    this.clients.set(ws, client);

    presenceManager.update(userId, userName, 'online', 'online');

    const channelIds = await channelRegistry.subscribeUserToChannels(userId);
    for (const channelId of channelIds) {
      client.subscribedChannels.add(channelId);
      if (!client.channelUnsubs.has(channelId)) {
        const unsub = subscribe(channelId, (unifiedMsg) => {
          if (ws.readyState === WebSocket.OPEN) {
            ws.send(JSON.stringify({ type: 'message', data: unifiedMsg }));
          }
        });
        client.channelUnsubs.set(channelId, unsub);
      }
    }

    const channels = channelRegistry.listForUser(userId, organizationId);
    this.send(ws, {
      type: 'auth_ok',
      payload: {
        userId,
        channels: channels.map(c => ({ id: c.id, name: c.name, type: c.type })),
      },
      timestamp: Date.now(),
    });
    this.broadcastPresence();

    // Wire up message + close + error handlers (previously done in the
    // wss.on('connection') callback in initialize()).
    ws.on('message', (data) => {
      try {
        const msg: WSMessage = JSON.parse(data.toString());
        this.handleMessage(ws, msg);
      } catch (e) {
        this.sendError(ws, 'Invalid message format');
      }
    });
    ws.on('close', () => { this.handleDisconnect(ws); });
    ws.on('error', (err) => {
      requestLogger().error({ event: 'ws_error', err }, 'ws error');
      this.handleDisconnect(ws);
    });

    requestLogger().info({ event: 'ws_authenticated', userId, userName, channelCount: channelIds.length }, 'ws authenticated');
  }

  /**
   * Handle incoming WebSocket message
   */
  private handleMessage(ws: WebSocket, msg: WSMessage): void {
    switch (msg.type) {
      case 'message':
        this.handleChatMessage(ws, msg.payload as { channelId: string; content: string; recipientId?: string; recipientName?: string });
        break;

      case 'gateway_connect':
        this.handleGatewayConnect(ws, msg.payload as { relayId: string; name: string; capabilities: string[] }).catch((err) => {
          requestLogger().error({ err, event: 'handle_gateway_connect_failed' }, 'handleGatewayConnect failed');
          this.sendError(ws, 'Gateway register failed');
        });
        break;

      case 'gateway_message':
        this.handleGatewayMessage(ws, msg.payload as Message);
        break;

      case 'message_read': {
        const { messageId, channelId, readBy, readAt } = msg.payload as { messageId: string; channelId: string; readBy: string; readAt?: number };
        for (const [client] of this.clients) {
          if (client !== ws && client.readyState === WebSocket.OPEN) {
            client.send(JSON.stringify({
              type: 'message_read',
              messageId,
              channelId,
              readBy,
              readAt: readAt || Date.now()
            }));
          }
        }
        break;
      }

      case 'typing_start':
      case 'typing_stop': {
        const { channelId, userId, userName } = msg.payload as { channelId: string; userId: string; userName: string };
        for (const [client] of this.clients) {
          if (client !== ws && client.readyState === WebSocket.OPEN) {
            client.send(JSON.stringify({
              type: msg.type,
              channelId,
              userId,
              userName
            }));
          }
        }
        break;
      }

      default:
        this.sendError(ws, `Unknown message type: ${msg.type}`);
    }
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

    // Store message in legacy messageStore for backward compatibility
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

    // Route through MessageBus (handles dedup, subscriber delivery, and Supabase persistence)
    // Use the same ID as the messageStore entry so both stores refer to the same message
    const unifiedMsg = createMessage(channelId, client.userId, client.userName, content, 'ip', message.id);
    publish(unifiedMsg);

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
  private async handleGatewayConnect(
    ws: WebSocket,
    payload: { relayId: string; name: string; capabilities: string[] }
  ): Promise<void> {
    // Phase 2 Slice 3: only admin/foreman/system can register as a gateway relay.
    // Identity comes from the validated JWT on the upgrade, so this check is
    // server-authoritative.
    const ident = (ws as any).identity as { role: string } | undefined;
    if (!ident || (ident.role !== 'admin' && ident.role !== 'foreman' && ident.role !== 'system')) {
      this.sendError(ws, 'Gateway registration requires admin/foreman role');
      return;
    }

    const { relayId, name, capabilities } = payload;

    // Register relay
    const relay = await gatewayManager.register(relayId, name, capabilities, ws);

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

    requestLogger().info({ event: 'gateway_connected', relayId, name }, 'gateway connected');
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
        requestLogger().info({ event: 'gateway_channel_resolved', channelName: message.channelId, resolvedChannelId }, 'gateway channel resolved');
      } else {
        requestLogger().warn({ event: 'gateway_unknown_channel', channelName: message.channelId }, 'gateway unknown channel');
        return;
      }
    }

    // Forward to gateway manager
    gatewayManager.onMeshMessage(client.relayId, { ...message, channelId: resolvedChannelId });

    // Store message in legacy messageStore for backward compatibility
    const storedMsg = messageStore.add(
      resolvedChannelId,
      message.senderId,
      message.senderName,
      message.content,
      'mesh',
      message.recipientId,
      message.recipientName
    );

    // MessageBus publish happens in gatewayManager.onMeshMessage() — no duplicate publish here

    requestLogger().info({ event: 'gateway_mesh_message_stored', senderId: message.senderId, senderName: message.senderName, channelId: resolvedChannelId }, 'gateway mesh message stored');
  }

  /**
   * Handle client disconnect
   */
  private handleDisconnect(ws: WebSocket): void {
    const client = this.clients.get(ws);
    if (client) {
      presenceManager.setOffline(client.userId);

      // Unsubscribe from all MessageBus channel subscriptions
      for (const unsub of client.channelUnsubs.values()) {
        unsub();
      }
      client.channelUnsubs.clear();

      if (client.isRelay && client.relayId) {
        // Fire-and-forget: handleDisconnect is called from ws 'close' event listeners
        // and is intentionally sync. The pg DELETE is best-effort; any error is logged.
        gatewayManager.unregister(client.relayId).catch((err) => {
          requestLogger().error({ err, event: 'gateway_unregister_failed', relayId: client.relayId }, 'gateway unregister failed');
        });
      }

      this.clients.delete(ws);
      this.broadcastPresence();
      requestLogger().info({ event: 'ws_disconnected', userId: client.userId, userName: client.userName }, 'ws disconnected');
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
   * Can be called with a specific presence to broadcast, or will fetch all online
   */
  broadcastPresence(presence?: unknown): void {
    const payload = presence || presenceManager.getOnline();
    const wsMsg: WSMessage = {
      type: 'presence_update',
      payload,
      timestamp: Date.now(),
    };

    for (const [ws] of this.clients) {
      this.send(ws, wsMsg);
    }
  }

  /**
   * Broadcast a channel event. The previous implementation fanned out every
   * event to every connected client — which leaked private/group channels
   * created by one user into another user's UI via `channel_created` (closes
   * the second half of the leak whose REST-list half was fixed in ffe0af5).
   * Filtering is per-event:
   *
   *  - channel_created / channel_updated: only push to members (or to
   *    everyone for broadcast channels).
   *  - channel_deleted / channel_cleared: only push to clients already
   *    subscribed to the channel (i.e. clients that knew it existed).
   *  - message_deleted: still broadcast — the payload is only `{ messageId }`
   *    and receivers no-op unless they have the message in their local store,
   *    so there is no scope leak, just wasted bandwidth.
   */
  broadcastChannelEvent(type: 'channel_created' | 'channel_updated' | 'channel_deleted' | 'channel_cleared' | 'message_deleted', channel: unknown): void {
    const wsMsg: WSMessage = {
      type,
      payload: channel,
      timestamp: Date.now(),
    };

    for (const [ws, client] of this.clients) {
      if (this.shouldBroadcastTo(type, channel, client)) {
        this.send(ws, wsMsg);
      }
    }

    // Auto-subscribe all clients to new broadcast channels
    if (type === 'channel_created' && channel && typeof channel === 'object') {
      const ch = channel as { id: string; type: string; name: string };
      if (ch.type === 'broadcast') {
        this.subscribeAllToChannel(ch.id, ch.name);
      }
    }
  }

  /**
   * Whether a given channel event should be sent to a given client. Pulled
   * out as a pure method so the filter rules are unit-testable without
   * standing up a real WebSocketServer.
   */
  shouldBroadcastTo(
    type: 'channel_created' | 'channel_updated' | 'channel_deleted' | 'channel_cleared' | 'message_deleted',
    payload: unknown,
    client: { userId: string; organizationId?: string; subscribedChannels: Set<string> },
  ): boolean {
    if (type === 'channel_created' || type === 'channel_updated') {
      const ch = payload as {
        type?: string;
        organizationId?: string;
        memberIds?: string[];
        creatorId?: string;
        allowedUsers?: string[];
      } | null;
      if (!ch) return false;
      // DM exception: a direct message crosses the org fence for its members
      // only (mirrors channelRegistry.listForUser). Strictly type === 'dm' +
      // explicit membership — never creator/allowedUsers shortcuts, so a
      // non-member in either org still gets nothing.
      if (ch.type === 'dm') {
        return Array.isArray(ch.memberIds) && ch.memberIds.includes(client.userId);
      }
      // Tenant fence — even broadcast channels stay scoped to one org.
      // Defense-in-depth on top of the REST list filter: if memberIds is
      // stale or includes a cross-org user, the org gate still drops the
      // event before it crosses the tenant boundary.
      if (ch.organizationId && client.organizationId && ch.organizationId !== client.organizationId) {
        return false;
      }
      if (ch.type === 'broadcast') return true;
      if (ch.creatorId === client.userId) return true;
      if (Array.isArray(ch.memberIds) && ch.memberIds.includes(client.userId)) return true;
      if (Array.isArray(ch.allowedUsers) && ch.allowedUsers.includes(client.userId)) return true;
      return false;
    }
    if (type === 'channel_deleted' || type === 'channel_cleared') {
      const id =
        (payload as { id?: string; channelId?: string } | null)?.id ??
        (payload as { id?: string; channelId?: string } | null)?.channelId;
      if (!id) return false;
      return client.subscribedChannels.has(id);
    }
    // message_deleted — broadcast (see method JSDoc).
    return true;
  }

  /**
   * Subscribe all connected clients to a channel
   * Used when new broadcast channels are created
   */
  subscribeAllToChannel(channelId: string, channelName: string): void {
    let subscribed = 0;
    for (const [ws, client] of this.clients) {
      if (!client.subscribedChannels.has(channelId)) {
        client.subscribedChannels.add(channelId);
        subscribed++;

        // Subscribe to MessageBus for this newly added channel
        if (!client.channelUnsubs.has(channelId)) {
          const unsub = subscribe(channelId, (unifiedMsg) => {
            if (ws.readyState === WebSocket.OPEN) {
              ws.send(JSON.stringify({ type: 'message', data: unifiedMsg }));
            }
          });
          client.channelUnsubs.set(channelId, unsub);
        }

        // Notify client of new subscription
        this.send(ws, {
          type: 'channel_subscribed',
          payload: { channelId, channelName },
          timestamp: Date.now(),
        });
      }
    }
    requestLogger().info({ event: 'ws_auto_subscribed', subscribed, channelId, channelName }, 'ws auto subscribed clients');
  }

  /**
   * Get count of connected clients
   */
  getClientCount(): number {
    return this.clients.size;
  }

  /**
   * Refresh presence for all connected clients
   * Keeps them marked as "online" while WebSocket is connected
   */
  refreshAllPresence(): void {
    for (const [ws, client] of this.clients) {
      if (ws.readyState === WebSocket.OPEN) {
        presenceManager.update(client.userId, client.userName, 'online', client.isRelay ? 'gateway' : 'online');
      }
    }
    // Also broadcast presence update to all clients
    if (this.clients.size > 0) {
      this.broadcastPresence();
    }
  }

  /**
   * Get list of connected users (for debugging)
   */
  getConnectedUsers(): Array<{userId: string; userName: string}> {
    return Array.from(this.clients.values()).map(c => ({
      userId: c.userId,
      userName: c.userName
    }));
  }

  /**
   * Force refresh subscriptions for all clients
   * Useful after channel creation when clients were already connected
   */
  async refreshAllSubscriptions(): Promise<void> {
    for (const [ws, client] of this.clients) {
      const channelIds = await channelRegistry.subscribeUserToChannels(client.userId);
      for (const channelId of channelIds) {
        client.subscribedChannels.add(channelId);
      }
      
      // Send updated channel list
      const channels = channelRegistry.listForUser(client.userId, client.organizationId);
      this.send(ws, {
        type: 'channels_updated',
        payload: {
          channels: channels.map(c => ({ id: c.id, name: c.name, type: c.type })),
        },
        timestamp: Date.now(),
      });
    }
    requestLogger().info({ event: 'ws_subscriptions_refreshed', clientCount: this.clients.size }, 'ws subscriptions refreshed');
  }
}

export const wsHandler = new WSHandler();
