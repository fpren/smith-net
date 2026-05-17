import { describe, it, expect } from 'vitest';
import { http, HttpResponse } from 'msw';
import { commClient } from '../commClient';
import { server } from '../../test/msw-server';

describe('commClient', () => {
  it('listChannels returns the channels array (unwraps bare-array response)', async () => {
    const result = await commClient.listChannels();
    expect(result.ok).toBe(true);
    if (result.ok) {
      expect(result.channels).toHaveLength(1);
      expect(result.channels[0].name).toBe('general');
    }
  });

  it('listChannels surfaces error status on 500', async () => {
    server.use(
      http.get('/api/channels', () =>
        HttpResponse.json({ error: 'boom' }, { status: 500 })
      )
    );
    const result = await commClient.listChannels();
    expect(result.ok).toBe(false);
    if (!result.ok) {
      expect(result.status).toBe(500);
      expect(result.error).toBe('boom');
    }
  });

  it('listMessages returns the messages array for a channel', async () => {
    const result = await commClient.listMessages('ch-general');
    expect(result.ok).toBe(true);
    if (result.ok) {
      expect(result.messages).toHaveLength(1);
      expect(result.messages[0].channelId).toBe('ch-general');
      expect(result.messages[0].content).toBe('hello world');
    }
  });

  it('deleteMessage returns ok on 204', async () => {
    const result = await commClient.deleteMessage('msg-1');
    expect(result.ok).toBe(true);
  });

  it('deleteMessage surfaces error on non-2xx', async () => {
    server.use(
      http.delete('/api/messages/:id', () =>
        HttpResponse.json({ error: 'nope' }, { status: 403 }),
      ),
    );
    const result = await commClient.deleteMessage('msg-1');
    expect(result.ok).toBe(false);
    if (!result.ok) {
      expect(result.status).toBe(403);
      expect(result.error).toBe('nope');
    }
  });

  it('send posts the body and returns the persisted message (sans bookkeeping)', async () => {
    const result = await commClient.send('ch-general', 'hey there');
    expect(result.ok).toBe(true);
    if (result.ok) {
      expect(result.message.id).toBe('msg-new');
      expect(result.message.channelId).toBe('ch-general');
      expect(result.message.content).toBe('hey there');
      // bookkeeping fields stripped
      expect((result.message as any).meshInjected).toBeUndefined();
      expect((result.message as any).relayCount).toBeUndefined();
    }
  });
});
