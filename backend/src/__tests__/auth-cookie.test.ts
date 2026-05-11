/**
 * Tests that authenticateToken accepts JWT from the smithnet_access httpOnly cookie
 * in addition to the existing Authorization: Bearer header.
 *
 * Pattern: build an inline Express app from authRouter + the cookie-parser
 * middleware — matches the convention used by other __tests__/ files.
 */

import express from 'express';
import cookieParser from 'cookie-parser';
import request from 'supertest';
import { authRouter } from '../authRoutes';
import { userStore, generateTokens, UserRole } from '../auth';

function buildApp() {
  const app = express();
  app.use(express.json());
  app.use(cookieParser());
  app.use('/api/auth', authRouter);
  return app;
}

describe('Cookie-based authentication', () => {
  const app = buildApp();
  let accessToken: string;

  beforeAll(async () => {
    const user = await userStore.createUser(
      'cookie-test@example.com',
      'password123',
      'Cookie Tester',
      UserRole.FOREMAN
    );
    accessToken = generateTokens(user).accessToken;
  });

  it('accepts token from smithnet_access cookie when Authorization header is absent', async () => {
    const res = await request(app)
      .get('/api/auth/me')
      .set('Cookie', [`smithnet_access=${accessToken}`]);
    expect(res.status).toBe(200);
    expect(res.body.user.email).toBe('cookie-test@example.com');
  });

  it('still accepts Authorization Bearer header when cookie is absent', async () => {
    const res = await request(app)
      .get('/api/auth/me')
      .set('Authorization', `Bearer ${accessToken}`);
    expect(res.status).toBe(200);
  });

  it('returns 401 when neither cookie nor header is present', async () => {
    const res = await request(app).get('/api/auth/me');
    expect(res.status).toBe(401);
  });
});

describe('setAuthCookies / clearAuthCookies', () => {
  const app = buildApp();

  it('login response sets httpOnly access + refresh cookies', async () => {
    await userStore.createUser('login-cookie@example.com', 'password123', 'X', UserRole.FOREMAN);
    const res = await request(app)
      .post('/api/auth/login')
      .send({ email: 'login-cookie@example.com', password: 'password123' });
    expect(res.status).toBe(200);
    const cookies = (res.headers['set-cookie'] || []) as unknown as string[];
    const accessCookie = cookies.find((c: string) => c.startsWith('smithnet_access='));
    const refreshCookie = cookies.find((c: string) => c.startsWith('smithnet_refresh='));
    expect(accessCookie).toBeDefined();
    expect(refreshCookie).toBeDefined();
    expect(accessCookie).toMatch(/HttpOnly/);
    expect(accessCookie).toMatch(/SameSite=Strict/);
    expect(refreshCookie).toMatch(/HttpOnly/);
    // In test env NODE_ENV is not 'production' so cookies must not be marked Secure
    // (would break dev over http://localhost). Guards against accidental hardcoding.
    expect(accessCookie).not.toMatch(/; Secure/i);
    expect(refreshCookie).not.toMatch(/; Secure/i);
  });
});
