/**
 * Smith Net WebSocket Client
 */

import { WSMessage, Message, Channel, Presence } from './types';

type MessageHandler = (message: Message) => void;
type ChannelHandler = (channel: Channel) => void;
type PresenceHandler = (presence: Presence[]) => void;
type ErrorHandler = (error: string) => void;
type ChannelClearedHandler = (channelId: string) => void;

class WebSocketClient {
  private ws: WebSocket | null = null;
  private reconnectTimeout: number | null = null;
  private messageHandlers: MessageHandler[] = [];
  private channelCreatedHandlers: ChannelHandler[] = [];
  private channelDeletedHandlers: ((id: string) => void)[] = [];
  private channelClearedHandlers: ChannelClearedHandler[] = [];
  private presenceHandlers: PresenceHandler[] = [];
  private errorHandlers: ErrorHandler[] = [];
  private authResolve: ((channels: Channel[]) => void) | null = null;
  private authReject: ((error: Error) => void) | null = null;

  connect(userId: string, userName: string): Promise<Channel[]> {
    return new Promise((resolve, reject) => {
      this.authResolve = resolve;
      this.authReject = reject;

      // Always connect to backend WebSocket
      const wsUrl = 'ws://localhost:3000';
      console.log('[WS] Connecting to:', wsUrl);
      
      this.ws = new WebSocket(wsUrl);

      this.ws.onopen = () => {
        console.log('[WS] Connected');
        this.send({
          type: 'auth',
          payload: { userId, userName },
          timestamp: Date.now(),
        });
      };

      this.ws.onmessage = (event) => {
        try {
          const msg: WSMessage = JSON.parse(event.data);
          this.handleMessage(msg);
        } catch (e) {
          console.error('[WS] Parse error:', e);
        }
      };

      this.ws.onclose = () => {
        console.log('[WS] Disconnected');
        this.scheduleReconnect(userId, userName);
      };

      this.ws.onerror = (error) => {
        console.error('[WS] Error:', error);
        if (this.authReject) {
          this.authReject(new Error('Connection failed'));
          this.authReject = null;
        }
      };
    });
  }

  private handleMessage(msg: WSMessage): void {
    switch (msg.type) {
      case 'auth_ok': {
        const payload = msg.payload as { userId: string; channels: Channel[] };
        if (this.authResolve) {
          this.authResolve(payload.channels);
          this.authResolve = null;
        }
        break;
      }

      case 'auth_error': {
        const payload = msg.payload as { error: string };
        if (this.authReject) {
          this.authReject(new Error(payload.error));
          this.authReject = null;
        }
        break;
      }

      case 'message': {
        const message = msg.payload as Message;
        this.messageHandlers.forEach(h => h(message));
        break;
      }

      case 'channel_created': {
        const channel = msg.payload as Channel;
        this.channelCreatedHandlers.forEach(h => h(channel));
        break;
      }

      case 'channel_deleted': {
        const payload = msg.payload as { id: string };
        this.channelDeletedHandlers.forEach(h => h(payload.id));
        break;
      }

      case 'channel_cleared': {
        const payload = msg.payload as { channelId: string };
        this.channelClearedHandlers.forEach(h => h(payload.channelId));
        break;
      }

      case 'presence_update': {
        const presence = msg.payload as Presence[];
        this.presenceHandlers.forEach(h => h(presence));
        break;
      }

      case 'error': {
        const payload = msg.payload as { error: string };
        this.errorHandlers.forEach(h => h(payload.error));
        break;
      }
    }
  }

  private send(msg: WSMessage): void {
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(msg));
    }
  }

  sendMessage(channelId: string, content: string): void {
    this.send({
      type: 'message',
      payload: { channelId, content },
      timestamp: Date.now(),
    });
  }

  private scheduleReconnect(userId: string, userName: string): void {
    if (this.reconnectTimeout) return;
    
    this.reconnectTimeout = window.setTimeout(() => {
      this.reconnectTimeout = null;
      console.log('[WS] Attempting reconnect...');
      this.connect(userId, userName).catch(console.error);
    }, 3000);
  }

  disconnect(): void {
    if (this.reconnectTimeout) {
      clearTimeout(this.reconnectTimeout);
      this.reconnectTimeout = null;
    }
    this.ws?.close();
    this.ws = null;
  }

  onMessage(handler: MessageHandler): void {
    this.messageHandlers.push(handler);
  }

  onChannelCreated(handler: ChannelHandler): void {
    this.channelCreatedHandlers.push(handler);
  }

  onChannelDeleted(handler: (id: string) => void): void {
    this.channelDeletedHandlers.push(handler);
  }

  onChannelCleared(handler: ChannelClearedHandler): void {
    this.channelClearedHandlers.push(handler);
  }

  onPresence(handler: PresenceHandler): void {
    this.presenceHandlers.push(handler);
  }

  onError(handler: ErrorHandler): void {
    this.errorHandlers.push(handler);
  }

  isConnected(): boolean {
    return this.ws?.readyState === WebSocket.OPEN;
  }
}

export const wsClient = new WebSocketClient();
