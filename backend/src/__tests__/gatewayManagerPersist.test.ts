import { pg, isPgEnabled } from '../db';
import { gatewayManager } from '../gatewayManager';

const describeDb = isPgEnabled() ? describe : describe.skip;

async function cleanSessions() {
  if (!isPgEnabled()) return;
  await pg!.query('DELETE FROM gateway_sessions');
  // Clear the in-memory map and any session timestamps without the live WS.
  (gatewayManager as any).relays.clear();
}

describeDb('gatewayManager persistence', () => {
  beforeEach(cleanSessions);
  afterAll(async () => { await pg?.end(); });

  it('register() writes a row to gateway_sessions', async () => {
    const fakeWs: any = { readyState: 1 };
    const relay = await gatewayManager.register('relay-A', 'alpha', ['ble'], fakeWs);
    expect(relay.id).toBe('relay-A');

    const rows = await pg!.query('SELECT id, name FROM gateway_sessions WHERE id = $1', ['relay-A']);
    expect(rows.rowCount).toBe(1);
    expect(rows.rows[0].name).toBe('alpha');
  });

  it('initialize() loads non-stale rows (< 5 min old) but skips stale ones', async () => {
    const fakeWs: any = { readyState: 1 };
    await gatewayManager.register('relay-fresh', 'fresh', ['ble'], fakeWs);
    await pg!.query(
      `INSERT INTO gateway_sessions (id, name, capabilities, last_activity, created_at)
       VALUES ($1, $2, '[]'::jsonb, NOW() - INTERVAL '6 minutes', NOW() - INTERVAL '6 minutes')`,
      ['relay-stale', 'stale']
    );

    (gatewayManager as any).relays.clear();
    await gatewayManager.initialize();

    expect(gatewayManager.get('relay-fresh')).toBeDefined();
    expect(gatewayManager.get('relay-stale')).toBeUndefined();
  });

  it('unregister() deletes the pg row', async () => {
    const fakeWs: any = { readyState: 1 };
    await gatewayManager.register('relay-to-go', 'goner', [], fakeWs);
    await gatewayManager.unregister('relay-to-go');
    const rows = await pg!.query('SELECT id FROM gateway_sessions WHERE id = $1', ['relay-to-go']);
    expect(rows.rowCount).toBe(0);
  });
});
