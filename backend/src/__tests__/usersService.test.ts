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
  afterAll(async () => { await pg?.end(); });

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
