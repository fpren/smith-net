/**
 * Tenant-isolation tests for channelRegistry.listForUser. Covers two leak
 * paths in layered fashion:
 *
 *   1. Cross-tenant leak through canUserSeeInList (closed in ffe0af5):
 *      a public channel created by user-A would appear in user-B's listing
 *      even when user-B was not a member.
 *   2. Cross-org leak via the new organization_id fence (migration 015): a
 *      channel from another org is invisible regardless of memberIds.
 *      Mirrors crew_positions / users isolation (012, 013).
 *
 * The original within-org rules (membership, broadcast everyone-visibility,
 * "discoverable via requiresApproval") still apply — but ONLY after the
 * org gate.
 */

import { pg, isPgEnabled } from '../db';
import { channelRegistry } from '../channelRegistry';

const describeDb = isPgEnabled() ? describe : describe.skip;

// All existing within-org tests share one org id so the new tenant fence is
// a no-op for them — those tests pin the membership/discovery rules. New
// cross-org tests use distinct org ids.
const ORG = 'scope-org-1';
const OTHER_ORG = 'scope-org-2';

async function reset() {
  if (!isPgEnabled()) return;
  await pg!.query('DELETE FROM channels');
  (channelRegistry as any).channels.clear();
  (channelRegistry as any).meshHashIndex.clear();
}

describeDb('channelRegistry.listForUser scope', () => {
  beforeEach(reset);
  afterAll(async () => { await pg?.end(); });

  it('does NOT expose a non-member public group channel to a stranger', async () => {
    const owner = 'scope-owner';
    const stranger = 'scope-stranger';
    const ch = await channelRegistry.create('owner-only', 'group', owner, ORG, [owner], 'public', false);

    expect(channelRegistry.listForUser(owner, ORG).map((c) => c.id)).toContain(ch.id);
    expect(channelRegistry.listForUser(stranger, ORG).map((c) => c.id)).not.toContain(ch.id);
  });

  it('exposes a public channel to a stranger when its memberIds is empty (open channel)', async () => {
    const stranger = 'scope-open-stranger';
    const ch = await channelRegistry.create('announcements', 'group', 'scope-open-owner', ORG, [], 'public', false);
    expect(channelRegistry.listForUser(stranger, ORG).map((c) => c.id)).toContain(ch.id);
  });

  it('exposes a public broadcast channel to all users (regardless of membership)', async () => {
    const stranger = 'scope-bcast-stranger';
    const ch = await channelRegistry.create('siren', 'broadcast', 'scope-bcast-owner', ORG);
    expect(channelRegistry.listForUser(stranger, ORG).map((c) => c.id)).toContain(ch.id);
  });

  it('exposes a private channel with requiresApproval to non-members so they can request to join', async () => {
    const stranger = 'scope-priv-stranger';
    const ch = await channelRegistry.create('locked', 'group', 'scope-priv-owner', ORG, ['scope-priv-owner'], 'private', true);
    expect(channelRegistry.listForUser(stranger, ORG).map((c) => c.id)).toContain(ch.id);
  });

  it('does NOT expose a private channel that does not accept requests', async () => {
    const stranger = 'scope-noreq-stranger';
    const ch = await channelRegistry.create('cabal', 'group', 'scope-noreq-owner', ORG, ['scope-noreq-owner'], 'private', false);
    expect(channelRegistry.listForUser(stranger, ORG).map((c) => c.id)).not.toContain(ch.id);
  });

  it('exposes a public group channel to its explicit members', async () => {
    const owner = 'scope-mem-owner';
    const member = 'scope-mem-friend';
    const ch = await channelRegistry.create('crew-chat', 'group', owner, ORG, [owner, member], 'public', false);
    expect(channelRegistry.listForUser(member, ORG).map((c) => c.id)).toContain(ch.id);
  });

  it('exposes a DM channel to its participants but not to outsiders', async () => {
    const a = 'scope-dm-a';
    const b = 'scope-dm-b';
    const c = 'scope-dm-c';
    const ch = await channelRegistry.create('a<->b', 'dm', a, ORG, [a, b], 'public', false);
    expect(channelRegistry.listForUser(a, ORG).map((x) => x.id)).toContain(ch.id);
    expect(channelRegistry.listForUser(b, ORG).map((x) => x.id)).toContain(ch.id);
    expect(channelRegistry.listForUser(c, ORG).map((x) => x.id)).not.toContain(ch.id);
  });

  describe('organization_id tenant fence', () => {
    it('does NOT expose a channel from another org even if memberIds matches', async () => {
      // Corruption-resilience case: A's channel in ORG, but its memberIds
      // somehow lists a user X who is actually in OTHER_ORG. The org gate
      // runs BEFORE membership, so X still cannot see the channel.
      const owner = 'tf-owner';
      const stray = 'tf-stranger';
      const ch = await channelRegistry.create('cross-org', 'group', owner, ORG, [owner, stray], 'public', false);

      // X is in OTHER_ORG — list returns []
      expect(channelRegistry.listForUser(stray, OTHER_ORG).map((c) => c.id)).not.toContain(ch.id);
      // Within the channel's org, X (as a member) WOULD see it.
      expect(channelRegistry.listForUser(stray, ORG).map((c) => c.id)).toContain(ch.id);
    });

    it('does NOT expose a broadcast channel from another org', async () => {
      const ch = await channelRegistry.create('alpha-siren', 'broadcast', 'tf-bcast-owner', ORG);
      expect(channelRegistry.listForUser('tf-bcast-bystander', OTHER_ORG).map((c) => c.id)).not.toContain(ch.id);
    });

    it('two foremen in different orgs each see only their own channels', async () => {
      const bossA = 'tf-boss-a';
      const bossB = 'tf-boss-b';
      const chA = await channelRegistry.create('alpha', 'group', bossA, ORG, [bossA], 'public', false);
      const chB = await channelRegistry.create('beta',  'group', bossB, OTHER_ORG, [bossB], 'public', false);

      expect(channelRegistry.listForUser(bossA, ORG).map((c) => c.id)).toEqual([chA.id]);
      expect(channelRegistry.listForUser(bossB, OTHER_ORG).map((c) => c.id)).toEqual([chB.id]);
    });

    it('create() persists organization_id and a re-read finds it', async () => {
      const ch = await channelRegistry.create('persisted', 'group', 'tf-persist-owner', ORG);
      expect(ch.organizationId).toBe(ORG);
      const row = await pg!.query<{ organization_id: string }>(
        'SELECT organization_id FROM channels WHERE id = $1',
        [ch.id],
      );
      expect(row.rows[0].organization_id).toBe(ORG);
    });
  });
});
