/**
 * Gateway Manager
 * Manages relay connections and message injection
 */

import { WebSocket } from 'ws';
import { GatewayRelay, Message } from './types';

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
  register(relayId: string, name: string, capabilities: string[], ws: WebSocket): GatewayRelay {
    const relay: GatewayRelay = {
      id: relayId,
      name,
      connectedAt: Date.now(),
      lastActivity: Date.now(),
      capabilities,
    };

    this.relays.set(relayId, { relay, ws });
    console.log(`[GatewayManager] Relay registered: ${name} (${relayId})`);
    
    return relay;
  }

  /**
   * Unregister a relay
   */
  unregister(relayId: string): void {
    const connected = this.relays.get(relayId);
    if (connected) {
      this.relays.delete(relayId);
      console.log(`[GatewayManager] Relay unregistered: ${connected.relay.name}`);
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
    if (!connected || connected.ws.readyState !== WebSocket.OPEN) {
      console.log(`[GatewayManager] Cannot inject: relay ${relayId} not connected`);
      return false;
    }

    try {
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
  updateActivity(relayId: string): void {
    const connected = this.relays.get(relayId);
    if (connected) {
      connected.relay.lastActivity = Date.now();
    }
  }

  /**
   * Force disconnect a relay (admin action from dashboard)
   */
  forceDisconnect(relayId: string): boolean {
    const connected = this.relays.get(relayId);
    if (!connected) {
      return false;
    }

    try {
      // Send disconnect command to the phone
      connected.ws.send(JSON.stringify({
        type: 'admin_disconnect',
        payload: { reason: 'Disconnected by admin from dashboard' },
        timestamp: Date.now(),
      }));
      
      // Close the WebSocket
      connected.ws.close(1000, 'Admin disconnect');
      
      // Remove from registry
      this.relays.delete(relayId);
      
      console.log(`[GatewayManager] Force disconnected relay: ${connected.relay.name}`);
      return true;
    } catch (e) {
      console.error(`[GatewayManager] Force disconnect error:`, e);
      return false;
    }
  }
}

export const gatewayManager = new GatewayManager();
