import { highestTier, tierTransitionEvent, recomputeUserTier } from '../billingService';

describe('highestTier', () => {
  it('returns open for an empty list', () => {
    expect(highestTier([])).toBe('open');
  });
  it('returns the single tier', () => {
    expect(highestTier(['solo'])).toBe('solo');
  });
  it('returns the maximum tier regardless of order', () => {
    expect(highestTier(['solo', 'advanced', 'enterprise'])).toBe('enterprise');
    expect(highestTier(['advanced', 'solo'])).toBe('advanced');
    expect(highestTier(['enterprise', 'open', 'solo'])).toBe('enterprise');
  });
  it('returns open when all are open', () => {
    expect(highestTier(['open', 'open'])).toBe('open');
  });
});

describe('tierTransitionEvent', () => {
  it('returns paid_converted on an upgrade', () => {
    expect(tierTransitionEvent('open', 'solo')).toBe('tier_upgrade.paid_converted');
    expect(tierTransitionEvent('open', 'enterprise')).toBe('tier_upgrade.paid_converted');
    expect(tierTransitionEvent('solo', 'advanced')).toBe('tier_upgrade.paid_converted');
  });
  it('returns canceled on a downgrade', () => {
    expect(tierTransitionEvent('advanced', 'open')).toBe('tier_downgrade.canceled');
    expect(tierTransitionEvent('enterprise', 'solo')).toBe('tier_downgrade.canceled');
    expect(tierTransitionEvent('enterprise', 'open')).toBe('tier_downgrade.canceled');
  });
  it('returns null when unchanged', () => {
    expect(tierTransitionEvent('solo', 'solo')).toBeNull();
    expect(tierTransitionEvent('open', 'open')).toBeNull();
  });
});

function mockClient(tierRows: { tier: string }[]) {
  const calls: { sql: string; params?: unknown[] }[] = [];
  const query = async (sql: string, params?: unknown[]) => {
    calls.push({ sql, params });
    return { rows: /SELECT tier/.test(sql) ? tierRows : [] };
  };
  return { query, calls };
}

describe('recomputeUserTier', () => {
  it('writes the highest active tier and returns it', async () => {
    const c = mockClient([{ tier: 'solo' }, { tier: 'advanced' }]);
    const t = await recomputeUserTier(c as never, 'u1');
    expect(t).toBe('advanced');
    const update = c.calls.find((x) => /UPDATE users/.test(x.sql));
    expect(update?.params).toEqual(['advanced', 'u1']);
  });
  it('falls back to open when there is no active source', async () => {
    const c = mockClient([]);
    expect(await recomputeUserTier(c as never, 'u1')).toBe('open');
  });
});
