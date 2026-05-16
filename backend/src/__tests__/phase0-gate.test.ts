/**
 * Phase 4 Slice 4 (ADR-0001): Phase-0 routes are feature-flagged off.
 * This test locks in the gate behavior: with PHASE_0_ENABLED unset
 * (default), every Phase-0 endpoint returns 404 from Express's default
 * not-found handler.
 *
 * To verify the flag works the other way, run the dev server with
 * `PHASE_0_ENABLED=true` and curl one of the endpoints. That path isn't
 * worth a test — the router unit-tests in phase0Routes.ts (if/when
 * written) would cover behavior; this file only protects the off-state.
 */

import express from 'express';
import request from 'supertest';
import { apiRouter } from '../api';
import { authRouter } from '../authRoutes';
import { userStore, generateTokens, UserRole, StoredUser } from '../auth';

function buildApp() {
  const app = express();
  app.use(express.json());
  app.use('/api/auth', authRouter);
  app.use('/api', apiRouter);
  return app;
}

const TEST_EMAIL = `phase0-gate-${Date.now()}@example.com`;
let user: StoredUser;
let token: string;

describe('Phase-0 routes are feature-flagged off (ADR-0001)', () => {
  const app = buildApp();

  beforeAll(async () => {
    user = await userStore.createUser(TEST_EMAIL, 'password123', 'Phase0Test', UserRole.SOLO);
    token = (await generateTokens(user)).accessToken;
  });

  const cases: Array<{ method: 'get' | 'post'; path: string }> = [
    { method: 'post', path: '/api/intent-authority/validate-creation' },
    { method: 'post', path: '/api/intents' },
    { method: 'post', path: '/api/synthesis-authority/validate-inputs' },
    { method: 'post', path: '/api/synthesize' },
    { method: 'get',  path: '/api/artifacts/some-id' },
    { method: 'post', path: '/api/ledger/seal' },
    { method: 'post', path: '/api/ledger/amend' },
    { method: 'get',  path: '/api/ledger/some-id' },
    { method: 'post', path: '/api/small-project/synthesize-and-generate-intent' },
    { method: 'post', path: '/api/small-project/confirm-and-seal' },
  ];

  for (const c of cases) {
    it(`${c.method.toUpperCase()} ${c.path} returns 404`, async () => {
      const req = request(app)[c.method](c.path).set('Authorization', `Bearer ${token}`);
      const res = c.method === 'post' ? await req.send({}) : await req;
      expect(res.status).toBe(404);
    });
  }

  it('a sibling extracted route still works (sanity: gate did not break the parent router)', async () => {
    // /api/channels is on channelsRoutes and should respond 200 (empty list).
    const res = await request(app)
      .get('/api/channels')
      .set('Authorization', `Bearer ${token}`);
    expect(res.status).toBe(200);
    expect(Array.isArray(res.body)).toBe(true);
  });
});
