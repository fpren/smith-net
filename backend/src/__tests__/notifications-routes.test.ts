import express from 'express';
import request from 'supertest';
import cookieParser from 'cookie-parser';
import { notificationsRouter } from '../notificationsRoutes';
import { authRouter } from '../authRoutes';
import { notificationService } from '../notificationService';
import { createUserAndProfile } from '../jobsService';
import { generateTokens, UserRole } from '../auth';
import { pg, isPgEnabled } from '../db';
import { create as createJob, assignCrew } from '../jobsService';
import { channelRegistry } from '../channelRegistry';
import { channelsRouter } from '../channelsRoutes';
import { authenticateToken } from '../auth';

const describeDb = isPgEnabled() ? describe : describe.skip;

// Close the pg pool once, after EVERY describe block in this file has finished.
// (An inner-block afterAll would end() the pool before the later "producers"
// block runs, breaking it with "Cannot use a pool after calling end".)
afterAll(async () => { await pg?.end(); });

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

describeDb('notification producers', () => {
  it('assignCrew creates a job_assigned notification for the assignee', async () => {
    const foreman = await makeUserWithToken(UserRole.FOREMAN, 'pf');
    const crew = await makeUserWithToken(UserRole.SOLO, 'pc');
    const job = await createJob({ foremanId: foreman.id, title: 'Roof tear-off' });
    await assignCrew(job.id, crew.id);
    const list = await notificationService.listForUser(crew.id);
    const hit = list.find((n) => n.type === 'job_assigned');
    expect(hit).toBeTruthy();
    expect(hit!.title).toContain('Roof tear-off');
    expect(hit!.link).toBe(`/console/jobs/${job.id}`);
  });

  it('a failing notification insert does NOT fail assignCrew', async () => {
    const foreman = await makeUserWithToken(UserRole.FOREMAN, 'pf2');
    const crew = await makeUserWithToken(UserRole.SOLO, 'pc2');
    const job = await createJob({ foremanId: foreman.id, title: 'Job X' });
    const spy = jest.spyOn(notificationService, 'create').mockRejectedValueOnce(new Error('boom'));
    await expect(assignCrew(job.id, crew.id)).resolves.toBeTruthy();
    spy.mockRestore();
  });

  it('POST /messages/inject notifies other members, not the sender', async () => {
    const sender = await makeUserWithToken(UserRole.FOREMAN, 'ms');
    const recipient = await makeUserWithToken(UserRole.SOLO, 'mr');
    const chan = await channelRegistry.create('plan-team', 'group', sender.id, sender.id, [sender.id, recipient.id]);

    const injectApp = express();
    injectApp.use(express.json());
    injectApp.use(cookieParser());
    injectApp.use('/api', authenticateToken, channelsRouter);

    const res = await request(injectApp)
      .post('/api/messages/inject')
      .set('Authorization', `Bearer ${sender.token}`)
      .send({ channelId: chan.id, content: 'standup at 9' });
    expect(res.status).toBe(201);

    const recipList = await notificationService.listForUser(recipient.id);
    expect(recipList.some((n) => n.type === 'message' && n.title.includes('plan-team'))).toBe(true);
    const senderList = await notificationService.listForUser(sender.id);
    expect(senderList.some((n) => n.type === 'message')).toBe(false);
  });

  it('carries a media attachment through inject', async () => {
    const sender = await makeUserWithToken(UserRole.FOREMAN, 'md');
    const chan = await channelRegistry.create('media-team', 'group', sender.id, sender.id, [sender.id]);

    const injectApp = express();
    injectApp.use(express.json());
    injectApp.use(cookieParser());
    injectApp.use('/api', authenticateToken, channelsRouter);

    const res = await request(injectApp)
      .post('/api/messages/inject')
      .set('Authorization', `Bearer ${sender.token}`)
      .send({
        channelId: chan.id,
        content: '[▣] photo',
        media: { type: 'image', url: '/media/images/abc.jpg', mimeType: 'image/jpeg', size: 1234 },
      });
    expect(res.status).toBe(201);
    expect(res.body.media).toEqual(
      expect.objectContaining({ type: 'image', url: '/media/images/abc.jpg' }),
    );
  });

  it('rejects media with a non-local, non-http url', async () => {
    const sender = await makeUserWithToken(UserRole.FOREMAN, 'mb');
    const chan = await channelRegistry.create('media-bad', 'group', sender.id, sender.id, [sender.id]);

    const injectApp = express();
    injectApp.use(express.json());
    injectApp.use(cookieParser());
    injectApp.use('/api', authenticateToken, channelsRouter);

    const res = await request(injectApp)
      .post('/api/messages/inject')
      .set('Authorization', `Bearer ${sender.token}`)
      .send({ channelId: chan.id, content: 'x', media: { type: 'file', url: 'javascript:alert(1)' } });
    expect(res.status).toBe(400);
  });
});
