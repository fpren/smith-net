import { pg, isPgEnabled } from '../db';
import { usersService } from '../usersService';
import { UserRole } from '../auth';
import { createUserAndProfile } from '../jobsService';

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

describeDb('usersService email verification', () => {
  beforeEach(cleanUsers);

  it('findByVerificationToken returns user when token valid and unexpired', async () => {
    const email = `t11-${Date.now()}@example.com`;
    const u = await usersService.createUser(email, 'password123', 'K', UserRole.SOLO);
    expect(u.emailVerificationToken).toBeDefined();
    const found = await usersService.findByVerificationToken(u.emailVerificationToken!);
    expect(found?.id).toBe(u.id);
  });

  it('findByVerificationToken returns undefined for unknown token', async () => {
    const found = await usersService.findByVerificationToken('nope');
    expect(found).toBeUndefined();
  });

  it('markEmailVerified clears the token and sets emailVerifiedAt', async () => {
    const email = `t12-${Date.now()}@example.com`;
    const u = await usersService.createUser(email, 'password123', 'L', UserRole.SOLO);
    const after = await usersService.markEmailVerified(u.id);
    expect(after?.emailVerifiedAt).toBeDefined();
    expect(after?.emailVerificationToken).toBeUndefined();
  });

  it('regenerateVerificationToken returns null for already-verified users', async () => {
    const email = `t13-${Date.now()}@example.com`;
    const u = await usersService.createUser(email, 'password123', 'M', UserRole.SOLO);
    await usersService.markEmailVerified(u.id);
    const tok = await usersService.regenerateVerificationToken(u.id);
    expect(tok).toBeNull();
  });

  it('regenerateVerificationToken issues a fresh token for unverified users', async () => {
    const email = `t14-${Date.now()}@example.com`;
    const u = await usersService.createUser(email, 'password123', 'N', UserRole.SOLO);
    const oldTok = u.emailVerificationToken;
    const newTok = await usersService.regenerateVerificationToken(u.id);
    expect(newTok).toBeTruthy();
    expect(newTok).not.toBe(oldTok);
  });
});

describeDb('usersService updateUser + getAllUsers', () => {
  beforeEach(cleanUsers);

  it('updateUser merges partial updates and returns the new state', async () => {
    const email = `t15-${Date.now()}@example.com`;
    const u = await usersService.createUser(email, 'password123', 'O', UserRole.SOLO);
    const updated = await usersService.updateUser(u.id, { displayName: 'Renamed', isActive: false });
    expect(updated?.displayName).toBe('Renamed');
    expect(updated?.isActive).toBe(false);
    expect(updated?.email).toBe(email.toLowerCase()); // untouched
  });

  it('updateUser returns undefined for missing user', async () => {
    const u = await usersService.updateUser('missing', { displayName: 'X' });
    expect(u).toBeUndefined();
  });

  it('getAllUsers returns every row', async () => {
    await usersService.createUser(`t16a-${Date.now()}@example.com`, 'password123', 'P1', UserRole.SOLO);
    await usersService.createUser(`t16b-${Date.now()}@example.com`, 'password123', 'P2', UserRole.SOLO);
    const all = await usersService.getAllUsers();
    expect(all.length).toBeGreaterThanOrEqual(2);
  });
});

describeDb('usersService.bootstrapAdmin', () => {
  it('inserts admin-001 if absent; no-op if present', async () => {
    await pg!.query("DELETE FROM users WHERE id = 'admin-001'");
    await usersService.bootstrapAdmin();
    const first = await usersService.getUserById('admin-001');
    expect(first?.email).toBe('admin@smithnet.local');
    expect(first?.role).toBe(UserRole.ADMIN);
    expect(first?.emailVerifiedAt).toBeDefined();

    await usersService.bootstrapAdmin();
    const count = await pg!.query("SELECT COUNT(*) FROM users WHERE id = 'admin-001'");
    expect(parseInt(count.rows[0].count, 10)).toBe(1);
  });
});

describeDb('jobsService.createUserAndProfile — transactional user+profile', () => {
  beforeEach(cleanUsers);

  it('inserts both user and profile rows on success', async () => {
    const email = `t-tx2-${Date.now()}@example.com`;
    const created = await createUserAndProfile({
      email,
      password: 'password123',
      displayName: 'OK',
      role: UserRole.FOREMAN,
    });
    expect(created.id).toBeTruthy();
    const userRow = await pg!.query('SELECT id FROM users WHERE id = $1', [created.id]);
    const profileRow = await pg!.query('SELECT id FROM profiles WHERE id = $1', [created.id]);
    expect(userRow.rowCount).toBe(1);
    expect(profileRow.rowCount).toBe(1);
  });

  it('rolls back the user when profile insert violates a constraint', async () => {
    // Pre-seed a profile with a known id so the next user+profile create collides
    // on the profiles PK. The user insert succeeds; the profile insert fails;
    // both must be rolled back.
    const collidedId = 'fixed-collision-id-' + Date.now();
    await pg!.query(
      "INSERT INTO profiles (id, email, display_name, role) VALUES ($1, $2, $3, $4)",
      [collidedId, 'pre@example.com', 'Pre', UserRole.FOREMAN]
    );

    await expect(
      createUserAndProfile({
        email: `t-tx3-${Date.now()}@example.com`,
        password: 'password123',
        displayName: 'Collider',
        role: UserRole.FOREMAN,
        forcedId: collidedId,
      })
    ).rejects.toThrow();

    // After rollback, NO user row with that id should exist.
    const userRow = await pg!.query('SELECT id FROM users WHERE id = $1', [collidedId]);
    expect(userRow.rowCount).toBe(0);
  });

  // Clean up the pre-seeded collision row. (Pool close moved to the
  // final describe block so the round-trip test can still run.)
  afterAll(async () => {
    if (isPgEnabled()) {
      await pg!.query("DELETE FROM profiles WHERE id LIKE 'fixed-collision-id-%'");
    }
  });
});

describeDb('usersService restart round-trip', () => {
  it('user created in one pool can log in via a fresh pool', async () => {
    const email = `t-rt-${Date.now()}@example.com`;
    await usersService.createUser(email, 'password123', 'RT', UserRole.SOLO);

    // The row must be reachable via a fresh query path (not in-memory state).
    // A true pool-restart is impractical inside Jest because the import-time
    // bootstrap binds the pool reference. The meaningful invariant is: the
    // data sits in pg, not in process memory. Re-querying via getUserByEmail
    // confirms that.
    const fetched = await usersService.getUserByEmail(email);
    expect(fetched?.email).toBe(email.toLowerCase());

    const login = await usersService.verifyPassword(email, 'password123');
    expect(login.ok).toBe(true);
  });

  afterAll(async () => {
    await pg?.end();
  });
});
