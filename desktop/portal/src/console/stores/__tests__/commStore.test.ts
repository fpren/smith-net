import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { useCommStore } from '../commStore';
import type { Channel, Message } from '../../../types';

const channel = (id: string, name = id): Channel => ({
  id,
  name,
  type: 'group',
  creatorId: 'u1',
  createdAt: 1700000000000,
  memberIds: ['u1'],
  isArchived: false,
  isDeleted: false,
});

const message = (id: string, channelId: string, ts = 1700000001000): Message => ({
  id,
  channelId,
  senderId: 'u1',
  senderName: 'U1',
  content: id,
  timestamp: ts,
  origin: 'online',
});

describe('commStore', () => {
  beforeEach(() => {
    useCommStore.getState().clear();
  });

  describe('addChannel', () => {
    it('adds a channel at the head and dedupes by id', () => {
      const s = useCommStore.getState();
      s.addChannel(channel('a'));
      s.addChannel(channel('b'));
      s.addChannel(channel('a'));
      expect(useCommStore.getState().channels.map((c) => c.id)).toEqual(['b', 'a']);
    });
  });

  describe('removeChannel', () => {
    it('drops the channel, its messages, its typing state, and clears selection if it was selected', () => {
      const s = useCommStore.getState();
      s.setChannels([channel('a'), channel('b')]);
      s.setMessages('a', [message('m1', 'a')]);
      s.setTyping('a', 'u2', 'Alice', true);
      s.selectChannel('a');

      s.removeChannel('a');
      const next = useCommStore.getState();
      expect(next.channels.map((c) => c.id)).toEqual(['b']);
      expect(next.messagesByChannel['a']).toBeUndefined();
      expect(next.typingByChannel['a']).toBeUndefined();
      expect(next.selectedChannelId).toBeNull();
    });
  });

  describe('clearChannelMessages', () => {
    it('empties the channel array but leaves the channel itself', () => {
      const s = useCommStore.getState();
      s.setChannels([channel('a')]);
      s.setMessages('a', [message('m1', 'a'), message('m2', 'a')]);
      s.clearChannelMessages('a');
      expect(useCommStore.getState().messagesByChannel['a']).toEqual([]);
      expect(useCommStore.getState().channels).toHaveLength(1);
    });
  });

  describe('removeMessage', () => {
    it('drops a message by id from its channel', () => {
      const s = useCommStore.getState();
      s.setMessages('a', [message('m1', 'a'), message('m2', 'a')]);
      s.removeMessage('a', 'm1');
      expect(useCommStore.getState().messagesByChannel['a'].map((m) => m.id)).toEqual(['m2']);
    });

    it('is a no-op for an unknown channel', () => {
      const s = useCommStore.getState();
      s.removeMessage('missing', 'whatever');
      expect(useCommStore.getState().messagesByChannel['missing']).toBeUndefined();
    });
  });

  describe('setTyping + sweepTyping', () => {
    beforeEach(() => {
      vi.useFakeTimers();
      vi.setSystemTime(new Date(1700000000000));
    });
    afterEach(() => {
      vi.useRealTimers();
    });

    it('setTyping(true) adds an entry; setTyping(false) removes it', () => {
      const s = useCommStore.getState();
      s.setTyping('a', 'u2', 'Alice', true);
      expect(useCommStore.getState().typingByChannel['a']?.['u2']).toBeDefined();
      s.setTyping('a', 'u2', 'Alice', false);
      expect(useCommStore.getState().typingByChannel['a']?.['u2']).toBeUndefined();
    });

    it('sweepTyping evicts entries whose expiresAt has passed', () => {
      const s = useCommStore.getState();
      s.setTyping('a', 'u2', 'Alice', true);
      // TTL is 5_000ms — advance past it.
      vi.setSystemTime(new Date(1700000000000 + 6_000));
      s.sweepTyping();
      expect(useCommStore.getState().typingByChannel['a']?.['u2']).toBeUndefined();
    });

    it('sweepTyping keeps entries within TTL', () => {
      const s = useCommStore.getState();
      s.setTyping('a', 'u2', 'Alice', true);
      vi.setSystemTime(new Date(1700000000000 + 2_000));
      s.sweepTyping();
      expect(useCommStore.getState().typingByChannel['a']?.['u2']).toBeDefined();
    });
  });

  describe('markRead', () => {
    it('accumulates readers per message and dedupes', () => {
      const s = useCommStore.getState();
      s.markRead('m1', 'u2');
      s.markRead('m1', 'u3');
      s.markRead('m1', 'u2'); // dedup
      const readers = useCommStore.getState().readByMessage['m1'];
      expect(readers).toBeDefined();
      expect(Array.from(readers!).sort()).toEqual(['u2', 'u3']);
    });
  });
});
