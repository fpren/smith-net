/**
 * Tests for wsHandler.shouldBroadcastTo — the per-event gate that closes
 * the cross-user WS leak (channel_created push from one user appearing in
 * an unrelated user's UI). Pairs with channelRegistryScope.test.ts which
 * covers the REST-list half (commit ffe0af5).
 */

import { wsHandler } from '../wsHandler';

function client(userId: string, subs: string[] = [], organizationId?: string) {
  return { userId, subscribedChannels: new Set(subs), organizationId };
}

describe('wsHandler.shouldBroadcastTo', () => {
  describe('channel_created / channel_updated', () => {
    const stranger = client('u-stranger');
    const member = client('u-member');
    const creator = client('u-creator');
    const allowed = client('u-allowed');
    const baseChannel = {
      id: 'c1',
      type: 'group',
      memberIds: ['u-member'],
      creatorId: 'u-creator',
      allowedUsers: ['u-allowed'],
      visibility: 'public',
    };

    it('sends channel_created to a member', () => {
      expect(wsHandler.shouldBroadcastTo('channel_created', baseChannel, member)).toBe(true);
    });

    it('sends channel_created to the creator', () => {
      expect(wsHandler.shouldBroadcastTo('channel_created', baseChannel, creator)).toBe(true);
    });

    it('sends channel_created to a user in allowedUsers (restricted/discoverable case)', () => {
      expect(wsHandler.shouldBroadcastTo('channel_created', baseChannel, allowed)).toBe(true);
    });

    it('does NOT send channel_created to a stranger (closes the leak)', () => {
      expect(wsHandler.shouldBroadcastTo('channel_created', baseChannel, stranger)).toBe(false);
    });

    it('sends a public broadcast channel to everyone', () => {
      const bcast = { ...baseChannel, type: 'broadcast' };
      expect(wsHandler.shouldBroadcastTo('channel_created', bcast, stranger)).toBe(true);
    });

    it('applies the same rules to channel_updated', () => {
      expect(wsHandler.shouldBroadcastTo('channel_updated', baseChannel, member)).toBe(true);
      expect(wsHandler.shouldBroadcastTo('channel_updated', baseChannel, stranger)).toBe(false);
    });

    it('returns false for a malformed/null payload rather than crashing', () => {
      expect(wsHandler.shouldBroadcastTo('channel_created', null, stranger)).toBe(false);
    });
  });

  describe('channel_deleted / channel_cleared', () => {
    const subscribed = client('u-sub', ['c1']);
    const unsubscribed = client('u-unsub');

    it('sends channel_deleted only to clients subscribed to the channel', () => {
      expect(wsHandler.shouldBroadcastTo('channel_deleted', { id: 'c1' }, subscribed)).toBe(true);
      expect(wsHandler.shouldBroadcastTo('channel_deleted', { id: 'c1' }, unsubscribed)).toBe(false);
    });

    it('sends channel_cleared only to subscribed clients (channelId field shape)', () => {
      expect(wsHandler.shouldBroadcastTo('channel_cleared', { channelId: 'c1' }, subscribed)).toBe(true);
      expect(wsHandler.shouldBroadcastTo('channel_cleared', { channelId: 'c1' }, unsubscribed)).toBe(false);
    });

    it('returns false when the payload lacks an id or channelId', () => {
      expect(wsHandler.shouldBroadcastTo('channel_deleted', {}, subscribed)).toBe(false);
    });
  });

  describe('organization tenant fence', () => {
    const baseChannel = {
      id: 'c1',
      type: 'group',
      organizationId: 'org-A',
      memberIds: ['u-X'],
      creatorId: 'u-creator',
      allowedUsers: [],
      visibility: 'public',
    };

    it('blocks channel_created when the channel and client are in different orgs (even if memberIds matches)', () => {
      const wrongOrg = client('u-X', [], 'org-B');
      expect(wsHandler.shouldBroadcastTo('channel_created', baseChannel, wrongOrg)).toBe(false);
    });

    it('allows channel_created when org matches and client is a member', () => {
      const sameOrg = client('u-X', [], 'org-A');
      expect(wsHandler.shouldBroadcastTo('channel_created', baseChannel, sameOrg)).toBe(true);
    });

    it('blocks a broadcast channel from another org', () => {
      const bcast = { ...baseChannel, type: 'broadcast', memberIds: [] };
      expect(wsHandler.shouldBroadcastTo('channel_created', bcast, client('any', [], 'org-B'))).toBe(false);
      expect(wsHandler.shouldBroadcastTo('channel_created', bcast, client('any', [], 'org-A'))).toBe(true);
    });

    it('is permissive when either side lacks organizationId (backward compat)', () => {
      // If the channel payload lacks org (legacy event before migration) the
      // gate falls back to the membership-only check. Similarly if the
      // client lacks it (a pre-migration WS handshake). Avoids breaking
      // older deployments mid-rollout.
      const noOrgChannel = { ...baseChannel, organizationId: undefined };
      expect(wsHandler.shouldBroadcastTo('channel_created', noOrgChannel, client('u-X', [], 'org-B'))).toBe(true);
      const noOrgClient = client('u-X', []);
      expect(wsHandler.shouldBroadcastTo('channel_created', baseChannel, noOrgClient)).toBe(true);
    });
  });

  describe('message_deleted', () => {
    it('broadcasts to every client — payload is only { messageId } and receivers no-op without the message', () => {
      expect(wsHandler.shouldBroadcastTo('message_deleted', { messageId: 'm1' }, client('any'))).toBe(true);
    });
  });
});
