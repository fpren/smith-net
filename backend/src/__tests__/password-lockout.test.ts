/**
 * F1.3 — password floor + per-account lockout tests
 *
 * Covers AC-1..AC-5, AC-7 of F1.3. Uses the in-memory userStore directly
 * (current backing store; will move to Postgres in a later PRD).
 *
 * To run:
 *   npx jest backend/src/__tests__/password-lockout.test.ts
 */

import { userStore, validatePassword, UserRole } from '../auth';

describe('validatePassword (F1.3)', () => {
  it('AC-1: rejects passwords shorter than 8 chars', () => {
    const r = validatePassword('Ab1');
    expect(r.valid).toBe(false);
    if (!r.valid) expect(r.reason).toMatch(/at least 8/);
  });

  it('AC-2/AC-3: rejects 8-char passwords with no digit', () => {
    const r = validatePassword('abcdefgh');
    expect(r.valid).toBe(false);
    if (!r.valid) expect(r.reason).toMatch(/digit/);
  });

  it('rejects passwords with no letter', () => {
    const r = validatePassword('12345678');
    expect(r.valid).toBe(false);
    if (!r.valid) expect(r.reason).toMatch(/letter/);
  });

  it('AC-4: accepts an 8-char password with a letter and a digit', () => {
    expect(validatePassword('Pass1234').valid).toBe(true);
  });

  it('rejects non-string input defensively', () => {
    // @ts-expect-error — testing the runtime guard
    expect(validatePassword(undefined).valid).toBe(false);
  });
});

describe('account lockout (F1.3)', () => {
  // Generate a unique email per test run so repeated test runs in the same
  // node process don't collide on the in-memory userStore.
  const uniqueEmail = () => `lockout-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@example.com`;

  it('AC-7: successful login resets failedLoginCount', async () => {
    const email = uniqueEmail();
    const user = await userStore.createUser(email, 'Correct1', 'Locker', UserRole.SOLO);

    // Two wrong attempts then a correct one
    await userStore.verifyPassword(email, 'wrong-pw1');
    await userStore.verifyPassword(email, 'wrong-pw2');
    expect(userStore.getUserById(user.id)?.failedLoginCount).toBe(2);

    const ok = await userStore.verifyPassword(email, 'Correct1');
    expect(ok.ok).toBe(true);
    expect(userStore.getUserById(user.id)?.failedLoginCount).toBe(0);
  });

  it('AC-5: 5 wrong attempts triggers lockout, 6th returns reason=locked', async () => {
    const email = uniqueEmail();
    await userStore.createUser(email, 'Correct1', 'Locker', UserRole.SOLO);

    for (let i = 0; i < 5; i++) {
      const r = await userStore.verifyPassword(email, 'wrong');
      expect(r.ok).toBe(false);
      if (!r.ok) expect(r.reason).toBe('invalid_credentials');
    }

    // 6th — even with the *correct* password — must be blocked by lockout
    const blocked = await userStore.verifyPassword(email, 'Correct1');
    expect(blocked.ok).toBe(false);
    if (!blocked.ok && blocked.reason === 'locked') {
      expect(blocked.retryMinutes).toBeGreaterThan(0);
      expect(blocked.retryMinutes).toBeLessThanOrEqual(15);
    } else {
      throw new Error(`expected reason=locked, got ${JSON.stringify(blocked)}`);
    }
  });

  it('AC-6: stale lockedUntil clears on next successful login', async () => {
    const email = uniqueEmail();
    const user = await userStore.createUser(email, 'Correct1', 'Locker', UserRole.SOLO);

    // Manually expire a lockout into the past — simulates the 15-min window passing
    const stored = userStore.getUserById(user.id)!;
    stored.lockedUntil = Date.now() - 1000;
    stored.failedLoginCount = 5;

    const r = await userStore.verifyPassword(email, 'Correct1');
    expect(r.ok).toBe(true);
    const after = userStore.getUserById(user.id)!;
    expect(after.lockedUntil).toBeUndefined();
    expect(after.failedLoginCount).toBe(0);
  });

  it('AC-8: no-account login returns invalid_credentials and waits ~200ms (no enumeration)', async () => {
    const start = Date.now();
    const r = await userStore.verifyPassword('nobody@example.com', 'whatever');
    const elapsed = Date.now() - start;
    expect(r.ok).toBe(false);
    if (!r.ok) expect(r.reason).toBe('invalid_credentials');
    // Allow generous lower bound — CI clocks vary
    expect(elapsed).toBeGreaterThanOrEqual(150);
  });
});

describe('createUser password floor (F1.3)', () => {
  it('rejects weak password at the userStore boundary', async () => {
    await expect(
      userStore.createUser(`weak-${Date.now()}@example.com`, 'short', 'Weak', UserRole.SOLO)
    ).rejects.toThrow(/at least 8/);
  });
});
