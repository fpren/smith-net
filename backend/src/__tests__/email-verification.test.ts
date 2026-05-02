/**
 * F1.4 — email verification state-machine tests.
 *
 * Drives the userStore directly (the in-memory backing store currently used
 * by all auth code). Endpoint-level coverage will land alongside supertest
 * integration tests when the test infra is set up.
 *
 * To run:
 *   npx jest backend/src/__tests__/email-verification.test.ts
 */

import { userStore, UserRole, EMAIL_VERIFICATION_TTL_MS, EMAIL_RESEND_COOLDOWN_MS } from '../auth';

const uniqueEmail = (tag: string) => `${tag}-${Date.now()}-${Math.random().toString(36).slice(2, 8)}@example.com`;

describe('email verification (F1.4)', () => {
  it('AC-1: new account is unverified with a 24h-expiring token', async () => {
    const u = await userStore.createUser(uniqueEmail('reg'), 'StrongP1', 'Reg', UserRole.SOLO);
    expect(u.emailVerifiedAt).toBeUndefined();
    expect(u.emailVerificationToken).toBeDefined();
    expect(u.emailVerificationToken!.length).toBeGreaterThanOrEqual(32);
    const ttl = (u.emailVerificationExpiresAt ?? 0) - Date.now();
    expect(ttl).toBeGreaterThan(EMAIL_VERIFICATION_TTL_MS - 5_000);
    expect(ttl).toBeLessThanOrEqual(EMAIL_VERIFICATION_TTL_MS + 1_000);
  });

  it('AC-3: valid token marks user verified and clears the token (single-use)', async () => {
    const u = await userStore.createUser(uniqueEmail('verify'), 'StrongP1', 'V', UserRole.SOLO);
    const token = u.emailVerificationToken!;

    const found = userStore.findByVerificationToken(token);
    expect(found?.id).toBe(u.id);

    userStore.markEmailVerified(u.id);
    const after = userStore.getUserById(u.id)!;
    expect(after.emailVerifiedAt).toBeGreaterThan(0);
    expect(after.emailVerificationToken).toBeUndefined();

    // AC-6: reusing the same token is rejected
    expect(userStore.findByVerificationToken(token)).toBeUndefined();
  });

  it('AC-5: expired token is rejected', async () => {
    const u = await userStore.createUser(uniqueEmail('expired'), 'StrongP1', 'E', UserRole.SOLO);
    const stored = userStore.getUserById(u.id)!;
    stored.emailVerificationExpiresAt = Date.now() - 1000;
    expect(userStore.findByVerificationToken(u.emailVerificationToken!)).toBeUndefined();
  });

  it('garbage / empty tokens return undefined without throwing', () => {
    expect(userStore.findByVerificationToken('garbage-not-a-token')).toBeUndefined();
    expect(userStore.findByVerificationToken('')).toBeUndefined();
  });

  it('AC-9: regenerateVerificationToken issues a fresh token + bumps lastSentAt', async () => {
    const u = await userStore.createUser(uniqueEmail('regen'), 'StrongP1', 'R', UserRole.SOLO);
    const original = u.emailVerificationToken!;

    const fresh = userStore.regenerateVerificationToken(u.id);
    expect(fresh).toBeDefined();
    expect(fresh).not.toBe(original);

    const after = userStore.getUserById(u.id)!;
    expect(after.emailVerificationLastSentAt).toBeGreaterThan(0);
    // Original token must no longer work
    expect(userStore.findByVerificationToken(original)).toBeUndefined();
    // New token works
    expect(userStore.findByVerificationToken(fresh!)?.id).toBe(u.id);
  });

  it('regenerateVerificationToken is a no-op for verified users (returns null)', async () => {
    const u = await userStore.createUser(uniqueEmail('verified'), 'StrongP1', 'X', UserRole.SOLO);
    userStore.markEmailVerified(u.id);
    expect(userStore.regenerateVerificationToken(u.id)).toBeNull();
  });

  it('AC-10: admin seed user is grandfathered as verified', () => {
    const admin = userStore.getUserByEmail('admin@smithnet.local');
    expect(admin).toBeDefined();
    expect(admin?.emailVerifiedAt).toBeGreaterThan(0);
  });

  it('toPublicUser exposes emailVerified for client gating', async () => {
    const u = await userStore.createUser(uniqueEmail('public'), 'StrongP1', 'P', UserRole.SOLO);
    const { toPublicUser } = await import('../auth');
    expect(toPublicUser(u).emailVerified).toBe(false);
    userStore.markEmailVerified(u.id);
    const reloaded = userStore.getUserById(u.id)!;
    expect(toPublicUser(reloaded).emailVerified).toBe(true);
  });

  it('cooldown constant is 60s — drives /resend-verification 429 response', () => {
    expect(EMAIL_RESEND_COOLDOWN_MS).toBe(60_000);
  });
});
