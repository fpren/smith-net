import { describe, it, expect } from 'vitest';
import { authClient } from '../authClient';

describe('authClient', () => {
  it('login returns user on valid credentials', async () => {
    const result = await authClient.login('foreman@example.com', 'password123');
    expect(result.ok).toBe(true);
    if (result.ok) {
      expect(result.user.email).toBe('foreman@example.com');
      expect(result.user.role).toBe('foreman');
    }
  });

  it('login returns error on invalid credentials', async () => {
    const result = await authClient.login('foreman@example.com', 'wrong');
    expect(result.ok).toBe(false);
    if (!result.ok) {
      expect(result.status).toBe(401);
    }
  });

  it('me returns the current user', async () => {
    const result = await authClient.me();
    expect(result.ok).toBe(true);
    if (result.ok) {
      expect(result.user.email).toBe('foreman@example.com');
    }
  });

  it('register returns a new user on success', async () => {
    const result = await authClient.register('new@example.com', 'password123', 'New User');
    expect(result.ok).toBe(true);
    if (result.ok) {
      expect(result.user.email).toBe('new@example.com');
    }
  });

  it('logout returns ok', async () => {
    const result = await authClient.logout();
    expect(result.ok).toBe(true);
  });
});
