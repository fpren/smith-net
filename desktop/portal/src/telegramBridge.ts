/**
 * Telegram Bridge Client for Desktop Portal
 * Connects to the local bridge server for cross-device messaging
 */

import { Message } from './types';

// Bridge configuration (default - can be changed in settings)
let BRIDGE_URL = 'http://192.168.8.169:8080';
let BRIDGE_TOKEN = 'rHGvP0GADcaUHwHqutuA9Kv1kYdRDzJSpof5gpLGYHM';
let CHAT_ID = '8018493389';

// Message from bridge
interface BridgeMessage {
  id: string;
  channel: string;
  sender_id: string;
  sender_name: string;
  content: string;
  timestamp: number;
  is_mesh_origin: boolean;
  media_type?: string | null;
  media_url?: string | null;
  media_filename?: string | null;
}

type MessageHandler = (message: Message) => void;
type ConnectionHandler = (connected: boolean) => void;

class TelegramBridgeClient {
  private ws: WebSocket | null = null;
  private messageHandlers: MessageHandler[] = [];
  private connectionHandlers: ConnectionHandler[] = [];
  private userId: string = '';
  private userName: string = '';
  private isConnected: boolean = false;
  private reconnectAttempts: number = 0;
  private maxReconnectAttempts: number = 10;
  private reconnectTimeout: number | null = null;
  private seenMessageIds: Set<string> = new Set();
  private pollInterval: number | null = null;

  /**
   * Configure bridge connection
   */
  configure(url: string, token: string, chatId: string): void {
    BRIDGE_URL = url;
    BRIDGE_TOKEN = token;
    CHAT_ID = chatId;
    console.log('[TelegramBridge] Configured:', url);

    // Save to localStorage
    localStorage.setItem('bridge_url', url);
    localStorage.setItem('bridge_token', token);
    localStorage.setItem('bridge_chat_id', chatId);
  }

  /**
   * Load config from localStorage
   */
  loadConfig(): { url: string; token: string; chatId: string } | null {
    const url = localStorage.getItem('bridge_url');
    const token = localStorage.getItem('bridge_token');
    const chatId = localStorage.getItem('bridge_chat_id');

    if (url && token && chatId) {
      BRIDGE_URL = url;
      BRIDGE_TOKEN = token;
      CHAT_ID = chatId;
      return { url, token, chatId };
    }
    return null;
  }

  /**
   * Connect to the bridge server
   */
  async connect(userId: string, userName: string): Promise<void> {
    this.userId = userId;
    this.userName = userName;

    // Try WebSocket first
    try {
      await this.connectWebSocket();
    } catch (e) {
      console.warn('[TelegramBridge] WebSocket failed, falling back to polling');
      this.startPolling();
    }
  }

  /**
   * Connect via WebSocket
   */
  private connectWebSocket(): Promise<void> {
    return new Promise((resolve, reject) => {
      const wsUrl = BRIDGE_URL.replace('http://', 'ws://').replace('https://', 'wss://') + '/stream';

      console.log('[TelegramBridge] Connecting WebSocket:', wsUrl);

      this.ws = new WebSocket(wsUrl);

      // Add auth headers via URL params (WebSocket doesn't support custom headers in browser)
      const authUrl = `${wsUrl}?token=${BRIDGE_TOKEN}&device_id=${this.userId}&chat_id=${CHAT_ID}`;
      this.ws = new WebSocket(authUrl);

      const timeout = setTimeout(() => {
        reject(new Error('WebSocket connection timeout'));
      }, 5000);

      this.ws.onopen = () => {
        clearTimeout(timeout);
        console.log('[TelegramBridge] ✓ WebSocket connected');
        this.isConnected = true;
        this.reconnectAttempts = 0;
        this.notifyConnectionHandlers(true);
        resolve();
      };

      this.ws.onmessage = (event) => {
        try {
          const msg: BridgeMessage = JSON.parse(event.data);
          this.handleIncomingMessage(msg);
        } catch (e) {
          console.warn('[TelegramBridge] Failed to parse message:', e);
        }
      };

      this.ws.onclose = () => {
        console.log('[TelegramBridge] WebSocket closed');
        this.isConnected = false;
        this.notifyConnectionHandlers(false);
        this.scheduleReconnect();
      };

      this.ws.onerror = (error) => {
        clearTimeout(timeout);
        console.error('[TelegramBridge] WebSocket error:', error);
        reject(error);
      };
    });
  }

  /**
   * Start polling for messages (fallback when WebSocket unavailable)
   */
  private startPolling(): void {
    let lastTimestamp = Date.now();

    this.pollInterval = window.setInterval(async () => {
      try {
        const response = await fetch(`${BRIDGE_URL}/poll?since=${lastTimestamp}&chat_id=${CHAT_ID}`, {
          headers: {
            'Authorization': `Bearer ${BRIDGE_TOKEN}`
          }
        });

        if (response.ok) {
          const data = await response.json();
          const messages: BridgeMessage[] = data.messages || [];

          for (const msg of messages) {
            this.handleIncomingMessage(msg);
            if (msg.timestamp > lastTimestamp) {
              lastTimestamp = msg.timestamp;
            }
          }

          if (!this.isConnected) {
            this.isConnected = true;
            this.notifyConnectionHandlers(true);
          }
        }
      } catch (e) {
        console.warn('[TelegramBridge] Poll failed:', e);
        if (this.isConnected) {
          this.isConnected = false;
          this.notifyConnectionHandlers(false);
        }
      }
    }, 2000);

    console.log('[TelegramBridge] Started polling');
  }

  /**
   * Handle incoming message from bridge
   */
  private handleIncomingMessage(msg: BridgeMessage): void {
    // Skip if we've seen this message
    if (this.seenMessageIds.has(msg.id)) {
      return;
    }
    this.seenMessageIds.add(msg.id);

    // Skip our own messages
    if (msg.sender_id === this.userId) {
      return;
    }

    console.log('[TelegramBridge] 📨 Received:', msg.sender_name, ':', msg.content);

    // Convert to app Message format
    const message: Message = {
      id: msg.id,
      channelId: msg.channel || 'general',
      senderId: msg.sender_id,
      senderName: msg.sender_name,
      content: msg.content,
      timestamp: msg.timestamp,
      isMeshOrigin: msg.is_mesh_origin
    };

    // Notify handlers
    this.messageHandlers.forEach(handler => handler(message));
  }

  /**
   * Send a message via the bridge
   */
  async sendMessage(message: Message): Promise<boolean> {
    console.log('[TelegramBridge] 📤 Sending:', message.content);

    // Add to seen to prevent echo
    this.seenMessageIds.add(message.id);

    try {
      const response = await fetch(`${BRIDGE_URL}/send`, {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${BRIDGE_TOKEN}`,
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          id: message.id,
          channel: message.channelId,
          sender_id: this.userId,
          sender_name: this.userName,
          content: message.content,
          timestamp: message.timestamp,
          chat_id: CHAT_ID,
          is_mesh_origin: false
        })
      });

      if (response.ok) {
        console.log('[TelegramBridge] ✓ Message sent');
        return true;
      } else {
        console.error('[TelegramBridge] Send failed:', response.status);
        return false;
      }
    } catch (e) {
      console.error('[TelegramBridge] Send error:', e);
      return false;
    }
  }

  /**
   * Schedule reconnection with exponential backoff
   */
  private scheduleReconnect(): void {
    if (this.reconnectAttempts >= this.maxReconnectAttempts) {
      console.log('[TelegramBridge] Max reconnect attempts reached, falling back to polling');
      this.startPolling();
      return;
    }

    const delay = Math.min(1000 * Math.pow(2, this.reconnectAttempts), 30000);
    console.log(`[TelegramBridge] Reconnecting in ${delay}ms...`);

    this.reconnectTimeout = window.setTimeout(() => {
      this.reconnectAttempts++;
      this.connectWebSocket().catch(() => {
        this.scheduleReconnect();
      });
    }, delay);
  }

  /**
   * Disconnect from bridge
   */
  disconnect(): void {
    if (this.ws) {
      this.ws.close();
      this.ws = null;
    }
    if (this.reconnectTimeout) {
      clearTimeout(this.reconnectTimeout);
      this.reconnectTimeout = null;
    }
    if (this.pollInterval) {
      clearInterval(this.pollInterval);
      this.pollInterval = null;
    }
    this.isConnected = false;
    this.notifyConnectionHandlers(false);
    console.log('[TelegramBridge] Disconnected');
  }

  /**
   * Register message handler
   */
  onMessage(handler: MessageHandler): void {
    this.messageHandlers.push(handler);
  }

  /**
   * Register connection handler
   */
  onConnectionChange(handler: ConnectionHandler): void {
    this.connectionHandlers.push(handler);
  }

  /**
   * Notify connection handlers
   */
  private notifyConnectionHandlers(connected: boolean): void {
    this.connectionHandlers.forEach(handler => handler(connected));
  }

  /**
   * Check if connected
   */
  getIsConnected(): boolean {
    return this.isConnected;
  }

  /**
   * Test bridge connection
   */
  async testConnection(): Promise<{ ok: boolean; message: string }> {
    try {
      const response = await fetch(`${BRIDGE_URL}/health`, {
        headers: {
          'Authorization': `Bearer ${BRIDGE_TOKEN}`
        }
      });

      if (response.ok) {
        const data = await response.json();
        return { ok: true, message: `Bridge healthy: ${data.ws_clients} clients connected` };
      } else {
        return { ok: false, message: `Bridge returned ${response.status}` };
      }
    } catch (e) {
      return { ok: false, message: `Connection failed: ${e}` };
    }
  }
}

// Export singleton
export const telegramBridge = new TelegramBridgeClient();
export default telegramBridge;
