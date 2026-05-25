import { pg, isPgEnabled } from '../db';
import { notificationService } from '../notificationService';
import { createUserAndProfile } from '../jobsService';
import { UserRole } from '../auth';

const describeDb = isPgEnabled() ? describe : describe.skip;

async function makeUser(suffix: string): Promise<string> {
  const u = await createUserAndProfile({
    email: `notif-${suffix}-${Date.now()}-${Math.random().toString(36).slice(2, 7)}@example.com`,
    password: 'password123',
    displayName: `Notif ${suffix}`,
    role: UserRole.SOLO,
  });
  return u.id;
}

describeDb('notificationService', () => {
  it('create -> listForUser returns it (newest first), camelCase-able row', async () => {
    const userId = await makeUser('a');
    const n = await notificationService.create({
      userId, type: 'message', title: 'New message in general', body: 'hi', link: '/console/comm', actorId: 'someone',
    });
    expect(n.id).toBeTruthy();
    expect(n.read_at).toBeNull();
    const list = await notificationService.listForUser(userId);
    expect(list.map((x) => x.id)).toContain(n.id);
    expect(list[0].title).toBe('New message in general');
  });

  it('unreadCount reflects unread; markRead (owner) decrements it', async () => {
    const userId = await makeUser('b');
    const n = await notificationService.create({ userId, type: 'message', title: 't' });
    expect(await notificationService.unreadCount(userId)).toBeGreaterThanOrEqual(1);
    const before = await notificationService.unreadCount(userId);
    expect(await notificationService.markRead(n.id, userId)).toBe(true);
    expect(await notificationService.unreadCount(userId)).toBe(before - 1);
  });

  it('markRead is idempotent (re-marking an already-read row still returns true)', async () => {
    const userId = await makeUser('c');
    const n = await notificationService.create({ userId, type: 'message', title: 't' });
    expect(await notificationService.markRead(n.id, userId)).toBe(true);
    expect(await notificationService.markRead(n.id, userId)).toBe(true);
  });

  it('markRead scoped to owner: another user cannot mark it', async () => {
    const owner = await makeUser('d');
    const other = await makeUser('e');
    const n = await notificationService.create({ userId: owner, type: 'message', title: 't' });
    expect(await notificationService.markRead(n.id, other)).toBe(false);
    expect(await notificationService.unreadCount(owner)).toBeGreaterThanOrEqual(1);
  });

  it('listForUser only returns the caller\'s rows', async () => {
    const a = await makeUser('f');
    const b = await makeUser('g');
    await notificationService.create({ userId: a, type: 'message', title: 'for-a' });
    const listB = await notificationService.listForUser(b);
    expect(listB.every((x) => x.title !== 'for-a')).toBe(true);
  });

  afterAll(async () => {
    await pg?.end();
  });
});
