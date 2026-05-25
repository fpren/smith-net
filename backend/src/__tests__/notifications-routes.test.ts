import express from 'express';
import request from 'supertest';
import cookieParser from 'cookie-parser';
import { notificationsRouter } from '../notificationsRoutes';
import { authRouter } from '../authRoutes';
import { notificationService } from '../notificationService';
import { createUserAndProfile } from '../jobsService';
import { generateTokens, UserRole } from '../auth';
import { pg, isPgEnabled } from '../db';

const describeDb = isPgEnabled() ? describe : describe.skip;

function buildApp() {
  const app = express();
  app.use(express.json());
  app.use(cookieParser());
  app.use('/api/auth', authRouter);
  app.use('/api/notifications', notificationsRouter);
  return app;
}

async function makeUserWithToken(role: UserRole, suffix: string) {
  const user = await createUserAndProfile({
    email: `nr-${suffix}-${Date.now()}-${Math.random().toString(36).slice(2, 7)}@example.com`,
    password: 'password123',
    displayName: `NR ${suffix}`,
    role,
  });
  const tokens = await generateTokens(user);
  return { id: user.id, token: tokens.accessToken };
}

describeDb('GET/PATCH /api/notifications', () => {
  let app: express.Express;
  beforeAll(() => { app = buildApp(); });
  afterAll(async () => { await pg?.end(); });

  it('GET returns only the caller\'s notifications + unreadCount (solo is NOT 403)', async () => {
    const solo = await makeUserWithToken(UserRole.SOLO, 'solo');
    await notificationService.create({ userId: solo.id, type: 'message', title: 'mine' });
    const res = await request(app).get('/api/notifications').set('Authorization', `Bearer ${solo.token}`);
    expect(res.status).toBe(200); // crucially NOT 403 -- the requireConsoleTier exemption
    expect(res.body.unreadCount).toBeGreaterThanOrEqual(1);
    expect(res.body.notifications.some((n: { title: string }) => n.title === 'mine')).toBe(true);
    expect(res.body.notifications[0]).toHaveProperty('actorId'); // camelCase serializer
  });

  it('GET does not leak another user\'s notifications', async () => {
    const a = await makeUserWithToken(UserRole.SOLO, 'a');
    const b = await makeUserWithToken(UserRole.SOLO, 'b');
    await notificationService.create({ userId: a.id, type: 'message', title: 'secret-a' });
    const res = await request(app).get('/api/notifications').set('Authorization', `Bearer ${b.token}`);
    expect(res.body.notifications.every((n: { title: string }) => n.title !== 'secret-a')).toBe(true);
  });

  it('PATCH :id/read marks the caller\'s notification read', async () => {
    const u = await makeUserWithToken(UserRole.SOLO, 'pr');
    const n = await notificationService.create({ userId: u.id, type: 'message', title: 't' });
    const res = await request(app).patch(`/api/notifications/${n.id}/read`).set('Authorization', `Bearer ${u.token}`);
    expect(res.status).toBe(200);
    expect(res.body).toEqual({ ok: true });
    expect(await notificationService.unreadCount(u.id)).toBe(0);
  });

  it('PATCH :id/read 404s when the notification is not the caller\'s', async () => {
    const owner = await makeUserWithToken(UserRole.SOLO, 'own');
    const other = await makeUserWithToken(UserRole.SOLO, 'oth');
    const n = await notificationService.create({ userId: owner.id, type: 'message', title: 't' });
    const res = await request(app).patch(`/api/notifications/${n.id}/read`).set('Authorization', `Bearer ${other.token}`);
    expect(res.status).toBe(404);
  });
});
