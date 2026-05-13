import { pg, isPgEnabled } from '../db';
import { channelRegistry } from '../channelRegistry';

const describeDb = isPgEnabled() ? describe : describe.skip;

async function cleanChannels() {
  if (!isPgEnabled()) return;
  await pg!.query('DELETE FROM channels');
  // Also reset the in-memory map so loads after each test start fresh.
  (channelRegistry as any).channels.clear();
  (channelRegistry as any).meshHashIndex.clear();
}

describeDb('channelRegistry persistence', () => {
  beforeEach(cleanChannels);
  afterAll(async () => { await pg?.end(); });

  it('create() writes a row to channels and stays in the in-memory map', async () => {
    const ch = await channelRegistry.create('test-A', 'broadcast', 'user-1');
    expect(ch.id).toBeTruthy();
    expect(channelRegistry.get(ch.id)).toBeDefined();

    const rows = await pg!.query('SELECT id, name, type, creator_id, mesh_hash FROM channels WHERE id = $1', [ch.id]);
    expect(rows.rowCount).toBe(1);
    expect(rows.rows[0].name).toBe('test-A');
    expect(rows.rows[0].type).toBe('broadcast');
    expect(rows.rows[0].creator_id).toBe('user-1');
    expect(rows.rows[0].mesh_hash).toBe(ch.meshHash);
  });

  it('initialize() loads existing rows into the in-memory map', async () => {
    const ch = await channelRegistry.create('test-B', 'group', 'user-2');
    (channelRegistry as any).channels.clear();
    (channelRegistry as any).meshHashIndex.clear();
    expect(channelRegistry.get(ch.id)).toBeUndefined();

    await channelRegistry.initialize();
    expect(channelRegistry.get(ch.id)?.name).toBe('test-B');
  });
});
