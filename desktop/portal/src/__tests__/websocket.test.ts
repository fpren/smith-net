import { describe, it, expect, beforeEach, vi } from 'vitest';
import type { TypingEvent, MessageReadEvent } from '../websocket';

// Fresh WebSocketClient per test — we can't easily reset the singleton, so
// re-import a private class instance by isolating the module via vi.resetModules.
async function makeFreshClient() {
  vi.resetModules();
  const mod = await import('../websocket');
  return mod.wsClient;
}

interface MockWebSocket {
  url: string;
  readyState: number;
  sent: string[];
  onopen?: () => void;
  onmessage?: (ev: { data: string }) => void;
  onclose?: () => void;
  onerror?: (e: unknown) => void;
  send: (data: string) => void;
  close: () => void;
}

const OPEN = 1;
const CONNECTING = 0;

function installMockWebSocket(): { instances: MockWebSocket[] } {
  const instances: MockWebSocket[] = [];
  const MockClass = class {
    static CONNECTING = CONNECTING;
    static OPEN = OPEN;
    static CLOSING = 2;
    static CLOSED = 3;
    url: string;
    readyState: number = CONNECTING;
    sent: string[] = [];
    onopen?: () => void;
    onmessage?: (ev: { data: string }) => void;
    onclose?: () => void;
    onerror?: (e: unknown) => void;
    constructor(url: string) {
      this.url = url;
      instances.push(this as any);
    }
    send(data: string) {
      this.sent.push(data);
    }
    close() {
      this.readyState = 3;
    }
  };
  vi.stubGlobal('WebSocket', MockClass);
  return { instances };
}

function openAndAuth(ws: MockWebSocket) {
  ws.readyState = OPEN;
  ws.onopen?.();
  // The client sends the auth frame immediately. Echo auth_ok.
  ws.onmessage?.({
    data: JSON.stringify({
      type: 'auth_ok',
      payload: { userId: 'u1', channels: [] },
      timestamp: Date.now(),
    }),
  });
}

describe('wsClient', () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  it('connect is idempotent for the same user (second call shares the promise)', async () => {
    const { instances } = installMockWebSocket();
    const client = await makeFreshClient();
    const p1 = client.connect('u1', 'User 1');
    const p2 = client.connect('u1', 'User 1');
    expect(p1).toBe(p2);
    expect(instances).toHaveLength(1);
    openAndAuth(instances[0]);
    await expect(p1).resolves.toEqual([]);
  });

  it('on*() handlers return unsubscribe functions', async () => {
    installMockWebSocket();
    const client = await makeFreshClient();
    const handler = vi.fn();
    const unsub = client.onMessage(handler);
    expect(typeof unsub).toBe('function');
    unsub();
    // After unsubscribe, the handler should NOT be called for incoming msgs.
    // Drive a message through and assert no call:
    const { instances } = installMockWebSocket();
    const client2 = await makeFreshClient();
    const h2 = vi.fn();
    const off = client2.onMessage(h2);
    const p = client2.connect('u', 'U');
    openAndAuth(instances[0]);
    await p;
    off();
    instances[0].onmessage?.({
      data: JSON.stringify({ type: 'message', payload: { id: 'm1', channelId: 'c1', senderId: 's', senderName: 'S', content: 'hi', timestamp: 1, origin: 'online' } }),
    });
    expect(h2).not.toHaveBeenCalled();
  });

  it('sendTyping writes a FLAT typing_start frame to the socket', async () => {
    const { instances } = installMockWebSocket();
    const client = await makeFreshClient();
    const p = client.connect('u1', 'User One');
    openAndAuth(instances[0]);
    await p;
    client.sendTyping('chan-1', true);
    // sent[0] is the auth frame; sent[1] is our typing frame.
    const frame = JSON.parse(instances[0].sent[1]);
    expect(frame).toMatchObject({
      type: 'typing_start',
      channelId: 'chan-1',
      userId: 'u1',
      userName: 'User One',
    });
    expect(frame.payload).toBeUndefined();
  });

  it('sendReadReceipt writes a FLAT message_read frame', async () => {
    const { instances } = installMockWebSocket();
    const client = await makeFreshClient();
    const p = client.connect('u1', 'User One');
    openAndAuth(instances[0]);
    await p;
    client.sendReadReceipt('msg-7', 'chan-1');
    const frame = JSON.parse(instances[0].sent[1]);
    expect(frame).toMatchObject({
      type: 'message_read',
      messageId: 'msg-7',
      channelId: 'chan-1',
      readBy: 'u1',
    });
    expect(typeof frame.readAt).toBe('number');
  });

  it('incoming typing_start (flat) fires the typing handler with kind=start', async () => {
    const { instances } = installMockWebSocket();
    const client = await makeFreshClient();
    const got: Array<{ evt: TypingEvent; kind: 'start' | 'stop' }> = [];
    client.onTyping((evt, kind) => got.push({ evt, kind }));
    const p = client.connect('u1', 'U');
    openAndAuth(instances[0]);
    await p;
    instances[0].onmessage?.({
      data: JSON.stringify({
        type: 'typing_start',
        channelId: 'c1',
        userId: 'u2',
        userName: 'Alice',
      }),
    });
    expect(got).toHaveLength(1);
    expect(got[0]).toEqual({
      evt: { channelId: 'c1', userId: 'u2', userName: 'Alice' },
      kind: 'start',
    });
  });

  it('incoming message_read (flat) fires the message-read handler', async () => {
    const { instances } = installMockWebSocket();
    const client = await makeFreshClient();
    const got: MessageReadEvent[] = [];
    client.onMessageRead((evt) => got.push(evt));
    const p = client.connect('u1', 'U');
    openAndAuth(instances[0]);
    await p;
    instances[0].onmessage?.({
      data: JSON.stringify({
        type: 'message_read',
        messageId: 'msg-1',
        channelId: 'c1',
        readBy: 'u2',
        readAt: 1700000000000,
      }),
    });
    expect(got).toHaveLength(1);
    expect(got[0]).toEqual({
      messageId: 'msg-1',
      channelId: 'c1',
      readBy: 'u2',
      readAt: 1700000000000,
    });
  });

  it('incoming message_deleted (wrapped) fires the message-deleted handler', async () => {
    const { instances } = installMockWebSocket();
    const client = await makeFreshClient();
    const got: string[] = [];
    client.onMessageDeleted((id) => got.push(id));
    const p = client.connect('u1', 'U');
    openAndAuth(instances[0]);
    await p;
    instances[0].onmessage?.({
      data: JSON.stringify({
        type: 'message_deleted',
        payload: { messageId: 'msg-99' },
      }),
    });
    expect(got).toEqual(['msg-99']);
  });
});
