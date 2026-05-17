/**
 * Smith Net WebSocket Client
 *
 * Singleton WS bridge to the backend wsHandler. Mirrors the Android
 * ChatManager protocol (auth, message, channel_*, typing_*, message_read,
 * message_deleted). Used by both the legacy Portal.tsx and the new
 * /console/comm route — connect() is idempotent so multiple consumers can
 * share one socket. on*() methods return unsubscribe functions so React
 * components can clean up their handlers on unmount; existing callers that
 * ignore the return value are unaffected.
 */

import { WSMessage, Message, Channel, Presence } from './types';

type MessageHandler = (message: Message) => void;
type ChannelHandler = (channel: Channel) => void;
type PresenceHandler = (presence: Presence[]) => void;
type ErrorHandler = (error: string) => void;
type ChannelClearedHandler = (channelId: string) => void;

export interface TypingEvent {
  channelId: string;
  userId: string;
  userName: string;
}

export interface MessageReadEvent {
  messageId: string;
  channelId: string;
  readBy: string;
  readAt: number;
}

type TypingHandler = (event: TypingEvent, kind: 'start' | 'stop') => void;
type MessageReadHandler = (event: MessageReadEvent) => void;
type MessageDeletedHandler = (messageId: string) => void;

type Unsubscribe = () => void;

function subscribe<H>(list: H[], handler: H): Unsubscribe {
  list.push(handler);
  return () => {
    const idx = list.indexOf(handler);
    if (idx >= 0) list.splice(idx, 1);
  };
}

class WebSocketClient {
  private ws: WebSocket | null = null;
  private reconnectTimeout: number | null = null;
  private messageHandlers: MessageHandler[] = [];
  private channelCreatedHandlers: ChannelHandler[] = [];
  private channelDeletedHandlers: ((id: string) => void)[] = [];
  private channelClearedHandlers: ChannelClearedHandler[] = [];
  private presenceHandlers: PresenceHandler[] = [];
  private errorHandlers: ErrorHandler[] = [];
  private typingHandlers: TypingHandler[] = [];
  private messageReadHandlers: MessageReadHandler[] = [];
  private messageDeletedHandlers: MessageDeletedHandler[] = [];
  private authResolve: ((channels: Channel[]) => void) | null = null;
  private authReject: ((error: Error) => void) | null = null;

  // In-flight or settled connect promise. connect() returns this when called
  // while a socket is already open/opening, so multiple consumers share one WS.
  private currentConnect: Promise<Channel[]> | null = null;

  // Cached identity from the most recent successful connect(). Outbound
  // senders (sendTyping, sendReadReceipt) attach these without taking them as
  // arguments — matches ChatManager.kt where the user is implicit.
  private authedUserId: string | null = null;
  private authedUserName: string | null = null;

  connect(userId: string, userName: string): Promise<Channel[]> {
    // Idempotent: if we're already connected/connecting for this user, share.
    if (this.currentConnect && this.authedUserId === userId) {
      return this.currentConnect;
    }

    this.authedUserId = userId;
    this.authedUserName = userName;

    this.currentConnect = new Promise<Channel[]>((resolve, reject) => {
      this.authResolve = resolve;
      this.authReject = reject;

      // Connect to the page's own origin at /api/ws so:
      //  (a) the smithnet_access cookie (Path=/api) rides the upgrade, and
      //  (b) the vite dev server's `ws: true` proxy forwards the upgrade to
      //      the real backend on a different port without CORS pain.
      // In jsdom tests window.location is http://localhost/ so this still
      // produces a stable URL.
      const loc = typeof window !== 'undefined' ? window.location : null;
      const proto = loc && loc.protocol === 'https:' ? 'wss:' : 'ws:';
      const host = loc?.host || 'localhost:3030';
      const wsUrl = `${proto}//${host}/api/ws`;
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
          const msg = JSON.parse(event.data);
          this.handleMessage(msg);
        } catch (e) {
          console.error('[WS] Parse error:', e);
        }
      };

      this.ws.onclose = () => {
        console.log('[WS] Disconnected');
        // Allow a fresh connect cycle on reconnect.
        this.currentConnect = null;
        this.scheduleReconnect(userId, userName);
      };

      this.ws.onerror = (error) => {
        console.error('[WS] Error:', error);
        if (this.authReject) {
          this.authReject(new Error('Connection failed'));
          this.authReject = null;
        }
        this.currentConnect = null;
      };
    });

    return this.currentConnect;
  }

  // Backend mixes wrapped ({ type, payload }) and FLAT ({ type, …fields })
  // event shapes (see backend/src/wsHandler.ts:138, 156). Parse from any shape.
  private handleMessage(raw: any): void {
    const type: string = raw?.type;
    switch (type) {
      case 'auth_ok': {
        const payload = (raw as WSMessage).payload as { userId: string; channels: Channel[] };
        if (this.authResolve) {
          this.authResolve(payload.channels);
          this.authResolve = null;
        }
        break;
      }

      case 'auth_error': {
        const payload = (raw as WSMessage).payload as { error: string };
        if (this.authReject) {
          this.authReject(new Error(payload.error));
          this.authReject = null;
        }
        break;
      }

      case 'message': {
        const message = (raw as WSMessage).payload as Message;
        this.messageHandlers.forEach((h) => h(message));
        break;
      }

      case 'channel_created': {
        const channel = (raw as WSMessage).payload as Channel;
        this.channelCreatedHandlers.forEach((h) => h(channel));
        break;
      }

      case 'channel_deleted': {
        const payload = (raw as WSMessage).payload as { id: string };
        this.channelDeletedHandlers.forEach((h) => h(payload.id));
        break;
      }

      case 'channel_cleared': {
        const payload = (raw as WSMessage).payload as { channelId: string };
        this.channelClearedHandlers.forEach((h) => h(payload.channelId));
        break;
      }

      case 'presence_update': {
        const presence = (raw as WSMessage).payload as Presence[];
        this.presenceHandlers.forEach((h) => h(presence));
        break;
      }

      case 'typing_start':
      case 'typing_stop': {
        // FLAT shape on the wire (backend wsHandler.ts:152-160).
        const evt: TypingEvent = {
          channelId: raw.channelId,
          userId: raw.userId,
          userName: raw.userName,
        };
        const kind: 'start' | 'stop' = type === 'typing_start' ? 'start' : 'stop';
        this.typingHandlers.forEach((h) => h(evt, kind));
        break;
      }

      case 'message_read': {
        // FLAT shape (backend wsHandler.ts:138-145).
        const evt: MessageReadEvent = {
          messageId: raw.messageId,
          channelId: raw.channelId,
          readBy: raw.readBy,
          readAt: raw.readAt ?? Date.now(),
        };
        this.messageReadHandlers.forEach((h) => h(evt));
        break;
      }

      case 'message_deleted': {
        // Wrapped via broadcastChannelEvent (wsHandler.ts:385-390). The
        // payload may be an object with .messageId or a bare string id.
        const payload = (raw as WSMessage).payload as { messageId?: string } | string;
        const messageId =
          typeof payload === 'string'
            ? payload
            : payload?.messageId;
        if (messageId) {
          this.messageDeletedHandlers.forEach((h) => h(messageId));
        }
        break;
      }

      case 'error': {
        const payload = (raw as WSMessage).payload as { error: string };
        this.errorHandlers.forEach((h) => h(payload.error));
        break;
      }
    }
  }

  private send(msg: WSMessage): void {
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(msg));
    }
  }

  // Send a top-level raw frame (used for typing/read which the backend reads
  // FLAT, not wrapped). Validates the socket is open.
  private sendRaw(obj: object): void {
    if (this.ws?.readyState === WebSocket.OPEN) {
      this.ws.send(JSON.stringify(obj));
    }
  }

  sendMessage(channelId: string, content: string): void {
    this.send({
      type: 'message',
      payload: { channelId, content },
      timestamp: Date.now(),
    });
  }

  sendTyping(channelId: string, start: boolean): void {
    if (!this.authedUserId) return;
    this.sendRaw({
      type: start ? 'typing_start' : 'typing_stop',
      channelId,
      userId: this.authedUserId,
      userName: this.authedUserName ?? '',
    });
  }

  sendReadReceipt(messageId: string, channelId: string): void {
    if (!this.authedUserId) return;
    this.sendRaw({
      type: 'message_read',
      messageId,
      channelId,
      readBy: this.authedUserId,
      readAt: Date.now(),
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
    this.currentConnect = null;
  }

  onMessage(handler: MessageHandler): Unsubscribe {
    return subscribe(this.messageHandlers, handler);
  }

  onChannelCreated(handler: ChannelHandler): Unsubscribe {
    return subscribe(this.channelCreatedHandlers, handler);
  }

  onChannelDeleted(handler: (id: string) => void): Unsubscribe {
    return subscribe(this.channelDeletedHandlers, handler);
  }

  onChannelCleared(handler: ChannelClearedHandler): Unsubscribe {
    return subscribe(this.channelClearedHandlers, handler);
  }

  onPresence(handler: PresenceHandler): Unsubscribe {
    return subscribe(this.presenceHandlers, handler);
  }

  onError(handler: ErrorHandler): Unsubscribe {
    return subscribe(this.errorHandlers, handler);
  }

  onTyping(handler: TypingHandler): Unsubscribe {
    return subscribe(this.typingHandlers, handler);
  }

  onMessageRead(handler: MessageReadHandler): Unsubscribe {
    return subscribe(this.messageReadHandlers, handler);
  }

  onMessageDeleted(handler: MessageDeletedHandler): Unsubscribe {
    return subscribe(this.messageDeletedHandlers, handler);
  }

  isConnected(): boolean {
    return this.ws?.readyState === WebSocket.OPEN;
  }
}

export const wsClient = new WebSocketClient();
