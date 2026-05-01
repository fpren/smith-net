/**
 * F1.1 — auth middleware unit tests
 *
 * Verifies authenticateToken correctly populates req.user from JWT
 * or rejects with 401. Covers AC-3 + AC-4 of F1.1.
 *
 * To run:
 *   npm install --save-dev jest @types/jest ts-jest
 *   npx jest backend/src/__tests__/auth-middleware.test.ts
 *
 * (Project has no test infra yet; install required before first run.)
 */

import { authenticateToken, generateTokens, AuthenticatedRequest, UserRole } from '../auth';
import type { Response, NextFunction } from 'express';

function makeReq(authHeader?: string): AuthenticatedRequest {
  return {
    headers: authHeader ? { authorization: authHeader } : {},
  } as AuthenticatedRequest;
}

function makeRes() {
  const res: Partial<Response> = {};
  res.status = jest.fn().mockReturnValue(res);
  res.json = jest.fn().mockReturnValue(res);
  return res as Response;
}

describe('authenticateToken middleware (F1.1)', () => {
  const validUser = {
    id: 'test-user-uuid',
    email: 'alice@example.com',
    passwordHash: 'unused',
    displayName: 'Alice',
    role: UserRole.SOLO,
    createdAt: Date.now(),
    updatedAt: Date.now(),
    isActive: true,
    mfaEnabled: false,
  };

  it('AC-3: rejects with 401 when no Authorization header', () => {
    const req = makeReq();
    const res = makeRes();
    const next = jest.fn() as NextFunction;

    authenticateToken(req, res, next);

    expect(res.status).toHaveBeenCalledWith(401);
    expect(next).not.toHaveBeenCalled();
    expect(req.user).toBeUndefined();
  });

  it('AC-3: rejects with 401 when Authorization header missing Bearer prefix', () => {
    const req = makeReq('Basic abc123');
    const res = makeRes();
    const next = jest.fn() as NextFunction;

    authenticateToken(req, res, next);

    expect(res.status).toHaveBeenCalledWith(401);
    expect(next).not.toHaveBeenCalled();
  });

  it('AC-3: rejects with 401 when Bearer token is invalid garbage', () => {
    const req = makeReq('Bearer not.a.real.jwt');
    const res = makeRes();
    const next = jest.fn() as NextFunction;

    authenticateToken(req, res, next);

    expect(res.status).toHaveBeenCalledWith(401);
    expect(next).not.toHaveBeenCalled();
  });

  it('AC-4: populates req.user when valid JWT provided', () => {
    const tokens = generateTokens(validUser);
    const req = makeReq(`Bearer ${tokens.accessToken}`);
    const res = makeRes();
    const next = jest.fn() as NextFunction;

    authenticateToken(req, res, next);

    expect(next).toHaveBeenCalled();
    expect(req.user).toBeDefined();
    expect(req.user?.id).toBe(validUser.id);
    expect(req.user?.email).toBe(validUser.email);
    expect(res.status).not.toHaveBeenCalled();
  });
});
