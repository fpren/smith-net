import { describe, it, expect } from 'vitest';
import { accentForId, colorForRole } from '../utils';

describe('accentForId', () => {
  it('returns one of the six sn-avatar tokens', () => {
    const valid = [1, 2, 3, 4, 5, 6].map((n) => `var(--sn-avatar-a${n})`);
    expect(valid).toContain(accentForId('user-1'));
  });

  it('is deterministic for the same id', () => {
    expect(accentForId('user-42')).toBe(accentForId('user-42'));
  });

  it('is a CSS var() reference, not a literal hex', () => {
    expect(accentForId('anything')).toMatch(/^var\(--sn-avatar-a[1-6]\)$/);
  });
});

describe('colorForRole', () => {
  it('maps every role to a var(--sn-*) reference', () => {
    const roles = ['admin', 'enterprise', 'foreman', 'lead', 'team', 'solo', 'unknown-role'];
    for (const role of roles) {
      expect(colorForRole(role)).toMatch(/^var\(--sn-[a-z-]+\)$/);
    }
  });

  it('preserves the exact-hex-match jobs (foreman -> accent, team -> status-online)', () => {
    expect(colorForRole('foreman')).toBe('var(--sn-accent)');
    expect(colorForRole('team')).toBe('var(--sn-status-online)');
  });

  it('maps the calm/neutral admin role to ink-muted', () => {
    expect(colorForRole('admin')).toBe('var(--sn-ink-muted)');
  });

  it('falls back to the solo/default mapping for unknown roles', () => {
    expect(colorForRole('unknown-role')).toBe(colorForRole('solo'));
  });
});
