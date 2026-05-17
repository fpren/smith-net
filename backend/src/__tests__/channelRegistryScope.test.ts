/**
 * Tenant-isolation tests for channelRegistry.listForUser. Closes the leak
 * where a public channel created by user-A was visible in user-B's listing
 * even when user-B was not a member, the channel did not have
 * requiresApproval, and the two users shared no org.
 */

import { pg, isPgEnabled } from '../db';
import { channelRegistry } from '../channelRegistry';

const describeDb = isPgEnabled() ? describe : describe.skip;

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
    const ch = await channelRegistry.create('owner-only', 'group', owner, [owner], 'public', false);

    expect(channelRegistry.listForUser(owner).map((c) => c.id)).toContain(ch.id);
    expect(channelRegistry.listForUser(stranger).map((c) => c.id)).not.toContain(ch.id);
  });

  it('exposes a public channel to a stranger when its memberIds is empty (open channel)', async () => {
    // An "open" channel — memberIds explicitly empty — is still discoverable.
    // This preserves the existing canUserAccess rule for genuinely shared
    // boards (e.g. an announcements channel a foreman wants everyone to read).
    const stranger = 'scope-open-stranger';
    const ch = await channelRegistry.create('announcements', 'group', 'scope-open-owner', [], 'public', false);
    expect(channelRegistry.listForUser(stranger).map((c) => c.id)).toContain(ch.id);
  });

  it('exposes a public broadcast channel to all users (regardless of membership)', async () => {
    const stranger = 'scope-bcast-stranger';
    const ch = await channelRegistry.create('siren', 'broadcast', 'scope-bcast-owner');
    expect(channelRegistry.listForUser(stranger).map((c) => c.id)).toContain(ch.id);
  });

  it('exposes a private channel with requiresApproval to non-members so they can request to join', async () => {
    const stranger = 'scope-priv-stranger';
    const ch = await channelRegistry.create('locked', 'group', 'scope-priv-owner', ['scope-priv-owner'], 'private', true);
    expect(channelRegistry.listForUser(stranger).map((c) => c.id)).toContain(ch.id);
  });

  it('does NOT expose a private channel that does not accept requests', async () => {
    const stranger = 'scope-noreq-stranger';
    const ch = await channelRegistry.create('cabal', 'group', 'scope-noreq-owner', ['scope-noreq-owner'], 'private', false);
    expect(channelRegistry.listForUser(stranger).map((c) => c.id)).not.toContain(ch.id);
  });

  it('exposes a public group channel to its explicit members', async () => {
    const owner = 'scope-mem-owner';
    const member = 'scope-mem-friend';
    const ch = await channelRegistry.create('crew-chat', 'group', owner, [owner, member], 'public', false);
    expect(channelRegistry.listForUser(member).map((c) => c.id)).toContain(ch.id);
  });

  it('exposes a DM channel to its participants but not to outsiders', async () => {
    const a = 'scope-dm-a';
    const b = 'scope-dm-b';
    const c = 'scope-dm-c';
    const ch = await channelRegistry.create('a<->b', 'dm', a, [a, b], 'public', false);
    expect(channelRegistry.listForUser(a).map((x) => x.id)).toContain(ch.id);
    expect(channelRegistry.listForUser(b).map((x) => x.id)).toContain(ch.id);
    expect(channelRegistry.listForUser(c).map((x) => x.id)).not.toContain(ch.id);
  });
});
