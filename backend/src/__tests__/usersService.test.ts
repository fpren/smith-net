import { pg, isPgEnabled } from '../db';
import { usersService } from '../usersService';
import { UserRole } from '../auth';

const describeDb = isPgEnabled() ? describe : describe.skip;

async function cleanUsers() {
  if (!isPgEnabled()) return;
  // Remove all users except admin-001 between tests.
  await pg!.query("DELETE FROM users WHERE id != 'admin-001'");
}

describeDb('usersService.createUser', () => {
  beforeEach(cleanUsers);

  it('inserts a user row and returns the StoredUser', async () => {
    const email = `t1-${Date.now()}@example.com`;
    const user = await usersService.createUser(email, 'password123', 'Test One', UserRole.SOLO);
    expect(user.id).toBeTruthy();
    expect(user.email).toBe(email.toLowerCase());
    expect(user.displayName).toBe('Test One');
    expect(user.role).toBe(UserRole.SOLO);
    expect(user.passwordHash).not.toBe('password123'); // hashed
    expect(user.emailVerificationToken).toBeTruthy();
    expect(user.emailVerifiedAt).toBeUndefined();
  });

  it('rejects duplicate emails (case-insensitive)', async () => {
    const email = `t2-${Date.now()}@example.com`;
    await usersService.createUser(email, 'password123', 'A', UserRole.SOLO);
    await expect(
      usersService.createUser(email.toUpperCase(), 'password123', 'B', UserRole.SOLO)
    ).rejects.toThrow(/already registered/i);
  });

  it('rejects weak passwords', async () => {
    const email = `t3-${Date.now()}@example.com`;
    await expect(
      usersService.createUser(email, 'short', 'C', UserRole.SOLO)
    ).rejects.toThrow();
  });
});

describeDb('usersService.getUserById / getUserByEmail', () => {
  beforeEach(cleanUsers);

  it('getUserById returns user when present, undefined otherwise', async () => {
    const email = `t4-${Date.now()}@example.com`;
    const created = await usersService.createUser(email, 'password123', 'D', UserRole.SOLO);
    const fetched = await usersService.getUserById(created.id);
    expect(fetched?.id).toBe(created.id);
    expect(fetched?.email).toBe(email.toLowerCase());

    const missing = await usersService.getUserById('does-not-exist');
    expect(missing).toBeUndefined();
  });

  it('getUserByEmail is case-insensitive', async () => {
    const email = `t5-${Date.now()}@example.com`;
    await usersService.createUser(email, 'password123', 'E', UserRole.SOLO);
    const a = await usersService.getUserByEmail(email);
    const b = await usersService.getUserByEmail(email.toUpperCase());
    expect(a?.id).toBe(b?.id);
    expect(a?.id).toBeTruthy();
  });
});

describeDb('usersService.verifyPassword — happy path', () => {
  beforeEach(cleanUsers);

  it('returns ok=true and resets failed counter on success', async () => {
    const email = `t6-${Date.now()}@example.com`;
    const created = await usersService.createUser(email, 'password123', 'F', UserRole.SOLO);
    const result = await usersService.verifyPassword(email, 'password123');
    expect(result.ok).toBe(true);
    if (result.ok) {
      expect(result.user.id).toBe(created.id);
    }
    const fresh = await usersService.getUserById(created.id);
    expect(fresh?.failedLoginCount).toBe(0);
    expect(fresh?.lastLoginAt).toBeDefined();
  });

  it('returns ok=false / invalid_credentials when password is wrong', async () => {
    const email = `t7-${Date.now()}@example.com`;
    await usersService.createUser(email, 'password123', 'G', UserRole.SOLO);
    const result = await usersService.verifyPassword(email, 'wrong-password');
    expect(result.ok).toBe(false);
    if (!result.ok) {
      expect(result.reason).toBe('invalid_credentials');
    }
  });

  it('returns ok=false / invalid_credentials with constant-time delay for unknown email', async () => {
    const start = Date.now();
    const result = await usersService.verifyPassword('nobody@example.com', 'whatever');
    const elapsed = Date.now() - start;
    expect(result.ok).toBe(false);
    expect(elapsed).toBeGreaterThanOrEqual(190); // ENUMERATION_DELAY_MS = 200, allow 10ms jitter
  });
});

describeDb('usersService.verifyPassword — lockout', () => {
  beforeEach(cleanUsers);

  it('locks the account after 5 failed attempts', async () => {
    const email = `t8-${Date.now()}@example.com`;
    const created = await usersService.createUser(email, 'password123', 'H', UserRole.SOLO);
    for (let i = 0; i < 5; i++) {
      const r = await usersService.verifyPassword(email, 'wrong');
      expect(r.ok).toBe(false);
    }
    const locked = await usersService.verifyPassword(email, 'password123');
    expect(locked.ok).toBe(false);
    if (!locked.ok) {
      expect(locked.reason).toBe('locked');
      expect((locked as { retryMinutes: number }).retryMinutes).toBeGreaterThan(0);
    }
    const fresh = await usersService.getUserById(created.id);
    expect(fresh?.failedLoginCount).toBe(5);
    expect(fresh?.lockedUntil).toBeGreaterThan(Date.now());
  });
});

describeDb('usersService refresh tokens', () => {
  beforeEach(cleanUsers);
  afterAll(async () => { await pg?.end(); });

  it('storeRefreshToken + validateRefreshToken returns the userId', async () => {
    const email = `t9-${Date.now()}@example.com`;
    const u = await usersService.createUser(email, 'password123', 'I', UserRole.SOLO);
    await usersService.storeRefreshToken('refresh-abc', u.id);
    const found = await usersService.validateRefreshToken('refresh-abc');
    expect(found).toBe(u.id);
  });

  it('revokeRefreshToken removes it', async () => {
    const email = `t10-${Date.now()}@example.com`;
    const u = await usersService.createUser(email, 'password123', 'J', UserRole.SOLO);
    await usersService.storeRefreshToken('refresh-xyz', u.id);
    await usersService.revokeRefreshToken('refresh-xyz');
    const found = await usersService.validateRefreshToken('refresh-xyz');
    expect(found).toBeUndefined();
  });
});
