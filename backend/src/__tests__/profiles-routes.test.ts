import express from 'express';
import request from 'supertest';
import cookieParser from 'cookie-parser';
import { authRouter } from '../authRoutes';
import { profilesRouter } from '../profilesRoutes';
import { userStore, generateTokens, UserRole } from '../auth';

function buildApp() {
  const app = express();
  app.use(express.json());
  app.use(cookieParser());
  app.use('/api/auth', authRouter);
  app.use('/api/profiles', profilesRouter);
  return app;
}

describe('GET /api/profiles', () => {
  const app = buildApp();

  it('returns 401 with no auth', async () => {
    const res = await request(app).get('/api/profiles?q=admin');
    expect(res.status).toBe(401);
  });

  it('returns 403 tier_required for Solo user', async () => {
    const user = await userStore.createUser(
      `solo-profiles-${Date.now()}@example.com`,
      'password123',
      'Solo',
      UserRole.SOLO
    );
    const { accessToken } = await generateTokens(user);
    const res = await request(app)
      .get('/api/profiles?q=admin')
      .set('Authorization', `Bearer ${accessToken}`);
    expect(res.status).toBe(403);
    expect(res.body.code).toBe('tier_required');
  });

  it('returns 400 validation when q is missing', async () => {
    const user = await userStore.createUser(
      `foreman-profiles-missing-${Date.now()}@example.com`,
      'password123',
      'F',
      UserRole.FOREMAN
    );
    const { accessToken } = await generateTokens(user);
    const res = await request(app)
      .get('/api/profiles')
      .set('Authorization', `Bearer ${accessToken}`);
    expect(res.status).toBe(400);
    expect(res.body.code).toBe('validation');
  });

  it('returns 400 validation when q is too short', async () => {
    const user = await userStore.createUser(
      `foreman-profiles-short-${Date.now()}@example.com`,
      'password123',
      'F',
      UserRole.FOREMAN
    );
    const { accessToken } = await generateTokens(user);
    const res = await request(app)
      .get('/api/profiles?q=x')
      .set('Authorization', `Bearer ${accessToken}`);
    expect(res.status).toBe(400);
    expect(res.body.code).toBe('validation');
  });

  it('returns matching profiles by email substring', async () => {
    const target = await userStore.createUser(
      `target-search-${Date.now()}@example.com`,
      'password123',
      'Searchable Person',
      UserRole.TEAM_MEMBER
    );
    const foreman = await userStore.createUser(
      `foreman-search-${Date.now()}@example.com`,
      'password123',
      'F',
      UserRole.FOREMAN
    );
    const { accessToken } = await generateTokens(foreman);
    const res = await request(app)
      .get(`/api/profiles?q=target-search`)
      .set('Authorization', `Bearer ${accessToken}`);
    expect(res.status).toBe(200);
    expect(Array.isArray(res.body.profiles)).toBe(true);
    const found = res.body.profiles.find((p: any) => p.id === target.id);
    expect(found).toBeDefined();
    expect(found.email).toContain('target-search');
    expect(found.displayName).toBe('Searchable Person');
    expect(found.role).toBe('team');
  });

  it('returns matching profiles by displayName substring', async () => {
    const target = await userStore.createUser(
      `dn-${Date.now()}@example.com`,
      'password123',
      'Distinctive Crew Member',
      UserRole.TEAM_MEMBER
    );
    const foreman = await userStore.createUser(
      `foreman-dn-${Date.now()}@example.com`,
      'password123',
      'F',
      UserRole.FOREMAN
    );
    const { accessToken } = await generateTokens(foreman);
    const res = await request(app)
      .get(`/api/profiles?q=Distinctive`)
      .set('Authorization', `Bearer ${accessToken}`);
    expect(res.status).toBe(200);
    expect(res.body.profiles.find((p: any) => p.id === target.id)).toBeDefined();
  });

  it('caps results at 20', async () => {
    const foreman = await userStore.createUser(
      `foreman-cap-${Date.now()}@example.com`,
      'password123',
      'F',
      UserRole.FOREMAN
    );
    const tag = `bulk-${Date.now()}`;
    for (let i = 0; i < 25; i++) {
      await userStore.createUser(`${tag}-${i}@example.com`, 'password123', `Bulk ${i}`, UserRole.TEAM_MEMBER);
    }
    const { accessToken } = await generateTokens(foreman);
    const res = await request(app)
      .get(`/api/profiles?q=${tag}`)
      .set('Authorization', `Bearer ${accessToken}`);
    expect(res.status).toBe(200);
    expect(res.body.profiles.length).toBeLessThanOrEqual(20);
  });
});
