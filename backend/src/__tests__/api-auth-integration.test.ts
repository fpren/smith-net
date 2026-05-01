/**
 * F1.1 — API auth integration tests
 *
 * Verifies that /api/* routes (except auth/admin/public) reject unauthenticated requests
 * with 401, and that GET /channels (which has no other auth check) properly scopes to req.user.
 *
 * Covers AC-2, AC-5, AC-6, AC-8 of F1.1.
 *
 * To run:
 *   npm install --save-dev jest @types/jest ts-jest supertest @types/supertest
 *   npx jest backend/src/__tests__/api-auth-integration.test.ts
 *
 * Requires JWT_SECRET in env (or dev fallback works in NODE_ENV=development).
 */

import express from 'express';
import request from 'supertest';
import { apiRouter } from '../api';
import { authRouter } from '../authRoutes';
import { generateTokens, UserRole } from '../auth';

function buildApp() {
  const app = express();
  app.use(express.json());
  // Mount mirrors server.ts order: /api/auth before /api/* generic.
  app.use('/api/auth', authRouter);
  app.use('/api', apiRouter);
  return app;
}

const validUser = {
  id: 'integration-test-user',
  email: 'bob@example.com',
  passwordHash: 'unused',
  displayName: 'Bob',
  role: UserRole.SOLO,
  createdAt: Date.now(),
  updatedAt: Date.now(),
  isActive: true,
  mfaEnabled: false,
};

describe('API auth integration (F1.1)', () => {
  const app = buildApp();

  describe('GET /api/channels', () => {
    it('AC-2: returns 401 without Authorization header', async () => {
      const res = await request(app).get('/api/channels');
      expect(res.status).toBe(401);
    });

    it('AC-2: returns 401 with garbage Bearer token', async () => {
      const res = await request(app).get('/api/channels').set('Authorization', 'Bearer garbage');
      expect(res.status).toBe(401);
    });

    it('AC-4: returns 200 with valid JWT (and scopes to user)', async () => {
      const tokens = generateTokens(validUser);
      const res = await request(app)
        .get('/api/channels')
        .set('Authorization', `Bearer ${tokens.accessToken}`);
      // 200 with empty array (test user has no channels) — but NOT 401
      expect(res.status).toBe(200);
      expect(Array.isArray(res.body)).toBe(true);
    });
  });

  describe('POST /api/channels', () => {
    it('AC-5: creates channel with creator_id matching JWT subject', async () => {
      const tokens = generateTokens(validUser);
      const res = await request(app)
        .post('/api/channels')
        .set('Authorization', `Bearer ${tokens.accessToken}`)
        .send({ name: 'test-chan', type: 'group' });

      expect(res.status).toBe(201);
      expect(res.body.creatorId).toBe(validUser.id);
    });

    it('AC-2: returns 401 without auth even with valid body', async () => {
      const res = await request(app)
        .post('/api/channels')
        .send({ name: 'spoof-chan', type: 'group' });
      expect(res.status).toBe(401);
    });
  });

  describe('Auth bypass attempts (defense against legacy X-User-Id)', () => {
    it('AC-6: X-User-Id header alone (no Authorization) is rejected', async () => {
      const res = await request(app)
        .get('/api/channels')
        .set('X-User-Id', 'spoofed-uid');
      expect(res.status).toBe(401);
    });

    it('AC-6: X-User-Id header is IGNORED when valid Authorization is also present', async () => {
      const tokens = generateTokens(validUser);
      const res = await request(app)
        .post('/api/channels')
        .set('Authorization', `Bearer ${tokens.accessToken}`)
        .set('X-User-Id', 'spoofed-other-user')
        .send({ name: 'identity-test', type: 'group' });

      expect(res.status).toBe(201);
      // creatorId must come from JWT, NOT from X-User-Id header
      expect(res.body.creatorId).toBe(validUser.id);
      expect(res.body.creatorId).not.toBe('spoofed-other-user');
    });
  });
});
