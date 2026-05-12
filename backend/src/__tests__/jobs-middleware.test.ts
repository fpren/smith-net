import express, { Response } from 'express';
import request from 'supertest';
import cookieParser from 'cookie-parser';
import { authRouter } from '../authRoutes';
import { userStore, generateTokens, UserRole, authenticateToken, AuthenticatedRequest } from '../auth';
import { requireConsoleTier } from '../middleware/requireConsoleTier';

function buildApp() {
  const app = express();
  app.use(express.json());
  app.use(cookieParser());
  app.use('/api/auth', authRouter);
  app.get('/api/protected', authenticateToken, requireConsoleTier, (req: AuthenticatedRequest, res: Response) => {
    res.json({ ok: true, role: req.user!.role });
  });
  return app;
}

describe('requireConsoleTier', () => {
  const app = buildApp();

  it('returns 401 when no token', async () => {
    const res = await request(app).get('/api/protected');
    expect(res.status).toBe(401);
  });

  it('returns 403 tier_required for SOLO role', async () => {
    const u = await userStore.createUser('tier-solo@example.com', 'password123', 'S', UserRole.SOLO);
    const { accessToken } = generateTokens(u);
    const res = await request(app).get('/api/protected').set('Authorization', `Bearer ${accessToken}`);
    expect(res.status).toBe(403);
    expect(res.body.code).toBe('tier_required');
    expect(res.body.currentRole).toBe('solo');
  });

  it('returns 200 for FOREMAN role', async () => {
    const u = await userStore.createUser('tier-foreman@example.com', 'password123', 'F', UserRole.FOREMAN);
    const { accessToken } = generateTokens(u);
    const res = await request(app).get('/api/protected').set('Authorization', `Bearer ${accessToken}`);
    expect(res.status).toBe(200);
    expect(res.body.role).toBe('foreman');
  });

  it('returns 200 for ENTERPRISE role', async () => {
    const u = await userStore.createUser('tier-ent@example.com', 'password123', 'E', UserRole.ENTERPRISE);
    const { accessToken } = generateTokens(u);
    const res = await request(app).get('/api/protected').set('Authorization', `Bearer ${accessToken}`);
    expect(res.status).toBe(200);
  });

  it('returns 200 for ADMIN role', async () => {
    const u = await userStore.createUser('tier-admin@example.com', 'password123', 'A', UserRole.ADMIN);
    const { accessToken } = generateTokens(u);
    const res = await request(app).get('/api/protected').set('Authorization', `Bearer ${accessToken}`);
    expect(res.status).toBe(200);
  });

  it('returns 403 tier_required for TEAM_LEAD role', async () => {
    const u = await userStore.createUser('tier-lead@example.com', 'password123', 'L', UserRole.TEAM_LEAD);
    const { accessToken } = generateTokens(u);
    const res = await request(app).get('/api/protected').set('Authorization', `Bearer ${accessToken}`);
    expect(res.status).toBe(403);
    expect(res.body.code).toBe('tier_required');
  });
});
