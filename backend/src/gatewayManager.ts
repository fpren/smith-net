/**
 * Gateway Manager
 * Manages relay connections and message injection
 */

import { WebSocket } from 'ws';
import { GatewayRelay, Message } from './types';
import { createMessage, publish } from './messageBus';
import { pg, isPgEnabled } from './db';
import { requestLogger } from './log';

interface ConnectedRelay {
  relay: GatewayRelay;
  ws: WebSocket;
}

class GatewayManager {
  private relays: Map<string, ConnectedRelay> = new Map();
  private messageListeners: ((message: Message, relayId: string) => void)[] = [];

  /**
   * Register a gateway relay
   */
  async register(relayId: string, name: string, capabilities: string[], ws: WebSocket): Promise<GatewayRelay> {
    const relay: GatewayRelay = {
      id: relayId,
      name,
      connectedAt: Date.now(),
      lastActivity: Date.now(),
      capabilities,
    };

    this.relays.set(relayId, { relay, ws });

    if (isPgEnabled() && pg) {
      await pg.query(
        `INSERT INTO gateway_sessions (id, name, capabilities, last_activity, created_at)
         VALUES ($1, $2, $3::jsonb, NOW(), NOW())
         ON CONFLICT (id) DO UPDATE SET
           name          = EXCLUDED.name,
           capabilities  = EXCLUDED.capabilities,
           last_activity = NOW()`,
        [relayId, name, JSON.stringify(capabilities)]
      );
    }

    requestLogger().info({ event: 'gateway_registered', relayId, name }, 'gateway relay registered');
    return relay;
  }

  /**
   * Unregister a relay
   */
  async unregister(relayId: string): Promise<void> {
    const connected = this.relays.get(relayId);
    if (connected) {
      this.relays.delete(relayId);
      if (isPgEnabled() && pg) {
        await pg.query('DELETE FROM gateway_sessions WHERE id = $1', [relayId]);
      }
      requestLogger().info({ event: 'gateway_unregistered', relayId, name: connected.relay.name }, 'gateway relay unregistered');
    }
  }

  /**
   * Get relay by ID
   */
  get(relayId: string): GatewayRelay | undefined {
    return this.relays.get(relayId)?.relay;
  }

  /**
   * Get all connected relays
   */
  getAll(): GatewayRelay[] {
    return Array.from(this.relays.values()).map(c => c.relay);
  }

  /**
   * Check if any relay is connected
   */
  hasConnectedRelay(): boolean {
    return this.relays.size > 0;
  }

  /**
   * Inject message into mesh via relay
   */
  injectMessage(relayId: string, message: Message): boolean {
    const connected = this.relays.get(relayId);
    if (!connected || !connected.ws || connected.ws.readyState !== WebSocket.OPEN) {
      console.log(`[GatewayManager] Cannot inject: relay ${relayId} not connected`);
      return false;
    }

    try {
      // MessageBus publish happens in the caller (wsHandler.handleChatMessage) — no duplicate publish here

      connected.ws.send(JSON.stringify({
        type: 'inject_message',
        payload: message,
        timestamp: Date.now(),
      }));
      connected.relay.lastActivity = Date.now();
      console.log(`[GatewayManager] Injected message to relay: ${message.id.substring(0, 8)}`);
      return true;
    } catch (e) {
      console.error(`[GatewayManager] Inject error:`, e);
      return false;
    }
  }

  /**
   * Broadcast message to all relays
   */
  broadcastToRelays(message: Message): number {
    let count = 0;
    for (const [relayId] of this.relays) {
      if (this.injectMessage(relayId, message)) {
        count++;
      }
    }
    return count;
  }

  /**
   * Handle incoming mesh message from relay
   */
  onMeshMessage(relayId: string, message: Message): void {
    const connected = this.relays.get(relayId);
    if (connected) {
      connected.relay.lastActivity = Date.now();
    }

    console.log(`[GatewayManager] Mesh message from ${relayId}: ${message.id.substring(0, 8)}`);

    const unifiedMsg = createMessage(message.channelId, message.senderId, message.senderName, message.content, 'ble', message.id);
    publish(unifiedMsg);

    // Notify listeners
    for (const listener of this.messageListeners) {
      listener(message, relayId);
    }
  }

  /**
   * Subscribe to mesh messages from relays
   */
  onMessage(listener: (message: Message, relayId: string) => void): void {
    this.messageListeners.push(listener);
  }

  /**
   * Update activity timestamp
   */
  async updateActivity(relayId: string): Promise<void> {
    const connected = this.relays.get(relayId);
    if (connected) {
      connected.relay.lastActivity = Date.now();
      if (isPgEnabled() && pg) {
        await pg.query(
          `UPDATE gateway_sessions SET last_activity = NOW() WHERE id = $1`,
          [relayId]
        );
      }
    }
  }

  /**
   * Force disconnect a relay (admin action from dashboard)
   */
  async forceDisconnect(relayId: string): Promise<boolean> {
    const connected = this.relays.get(relayId);
    if (!connected) {
      return false;
    }

    try {
      // Send disconnect command to the phone
      if (connected.ws) {
        connected.ws.send(JSON.stringify({
          type: 'admin_disconnect',
          payload: { reason: 'Disconnected by admin from dashboard' },
          timestamp: Date.now(),
        }));

        // Close the WebSocket
        connected.ws.close(1000, 'Admin disconnect');
      }

      // Remove from registry
      this.relays.delete(relayId);

      if (isPgEnabled() && pg) {
        await pg.query('DELETE FROM gateway_sessions WHERE id = $1', [relayId]);
      }

      console.log(`[GatewayManager] Force disconnected relay: ${connected.relay.name}`);
      return true;
    } catch (e) {
      console.error(`[GatewayManager] Force disconnect error:`, e);
      return false;
    }
  }

  /**
   * Initialize gateway manager by loading non-stale (< 5 min) sessions from Postgres.
   * The WS reference is per-process — relays must reconnect to repopulate it.
   * `injectMessage` skips entries with a null ws.
   */
  async initialize(): Promise<void> {
    if (!isPgEnabled() || !pg) {
      requestLogger().info({ event: 'gateway_manager_initialized', mode: 'memory_only' }, 'gateway manager initialized (no DB)');
      return;
    }
    const { rows } = await pg.query<{
      id: string; name: string; capabilities: string[]; last_activity: Date; created_at: Date;
    }>(
      `SELECT id, name, capabilities, last_activity, created_at
         FROM gateway_sessions
        WHERE last_activity > NOW() - INTERVAL '5 minutes'`
    );
    // Note: the WS reference is per-process. We rebuild metadata but
    // not the live socket — relays must reconnect to repopulate that.
    for (const row of rows) {
      const relay: GatewayRelay = {
        id: row.id,
        name: row.name,
        connectedAt: row.created_at.getTime(),
        lastActivity: row.last_activity.getTime(),
        capabilities: row.capabilities ?? [],
      };
      this.relays.set(row.id, { relay, ws: null as any });
    }
    requestLogger().info({ event: 'gateway_manager_initialized', count: rows.length }, 'gateway manager loaded from pg');
  }
}

export const gatewayManager = new GatewayManager();
