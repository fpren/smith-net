# Notifications N-1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A per-user notifications backbone — store + service + dedicated un-gated endpoints + two producers (new-message, job-assigned) + a dashboard `NotificationsCard` that replaces the redundant `ShiftCard`.

**Architecture:** A `notifications` pg table (recipient-scoped) behind a `notificationService` (mirrors `crewPositionService`). A dedicated `/api/notifications` router uses `authenticateToken` ONLY (so solo users are not 403'd like `/shifts/today`), mounted before the broad `/api` `apiRouter`. Two best-effort producers create rows inside the existing message-send and crew-assign paths, awaited in-request (NOT fire-and-forget, per CLAUDE.md Rule 2) and wrapped so a failed notification never breaks the action. The portal polls the feed (15s, mirroring `useInvoicesPolling`) into a zustand store rendered by `NotificationsCard`. The service is built AI-ready; AI read/send is Phase-5 (navi spec), not built here.

**Tech Stack:** Backend Node/Express + pg + **Jest** (supertest, DB-gated via `isPgEnabled()`); portal Vite + React 18 + TS + zustand + Vitest/jsdom + @testing-library/react.

> **Test-runner note (verified):** the **backend uses Jest** (`npx jest <path>`), NOT vitest — vitest is not installed there. Backend test files use ambient jest globals (`describe`/`it`/`expect`); for spies use `jest.spyOn` (no import). Backend DB-gated tests need a reachable Postgres — export `DATABASE_URL` (dev: `postgres://fegensprenelon@127.0.0.1:5432/smithnet`); `psql` lives at `/opt/homebrew/Cellar/postgresql@17/17.7_1/bin/psql`. The **portal uses Vitest** (`npm run test:run` / `npx vitest run`), with `vi` imported from `vitest`. If the dev DB is missing recent columns (e.g. `users.tier`), apply the pending migrations (`migrations/021_users_tier.sql`, etc.) before running route tests that call `generateTokens`.

**Spec:** `docs/superpowers/specs/2026-05-25-notifications-n1-design.md`

---

## File Structure

**Backend (create):**
- `backend/migrations/027_notifications.sql` — the table + index.
- `backend/src/notificationService.ts` — `create` / `listForUser` / `markRead` / `unreadCount` (AI-ready).
- `backend/src/notificationsRoutes.ts` — `GET /` + `PATCH /:id/read`, `authenticateToken` only.
- `backend/src/__tests__/notificationService.test.ts` — service unit tests (DB-gated).
- `backend/src/__tests__/notifications-routes.test.ts` — endpoint + producer integration tests (DB-gated, supertest).

**Backend (modify):**
- `backend/src/server.ts` — mount `notificationsRouter` at `/api/notifications` before `app.use('/api', apiRouter)`.
- `backend/src/jobsService.ts` — job-assigned producer in `assignCrew` (after the audit log).
- `backend/src/channelsRoutes.ts` — new-message producer in the `POST /messages/inject` handler (make it `async`).

**Portal (create):**
- `desktop/portal/src/console/api/notificationsClient.ts` — REST wrapper + `NotificationItem` type.
- `desktop/portal/src/console/stores/notificationsStore.ts` — zustand store.
- `desktop/portal/src/console/hooks/useNotificationsPolling.ts` — 15s polling hook.
- `desktop/portal/src/console/stores/__tests__/notificationsStore.test.ts`
- `desktop/portal/src/console/hooks/__tests__/useNotificationsPolling.test.ts`
- `desktop/portal/src/console/components/adaptive-home/__tests__/NotificationsCard.test.tsx`

**Portal (modify):**
- `desktop/portal/src/console/components/adaptive-home/cards.tsx` — add `NotificationsCard` (keep `ShiftCard`).
- `desktop/portal/src/console/components/adaptive-home/AdaptiveDashboard.tsx` — swap `ShiftCard` -> `NotificationsCard` in both the carousel `PANELS` entry and the desktop status grid.

---

## Task 1: Table + service (backend)

**Files:**
- Create: `backend/migrations/027_notifications.sql`
- Create: `backend/src/notificationService.ts`
- Test: `backend/src/__tests__/notificationService.test.ts`

- [ ] **Step 1: Write the migration**

`backend/migrations/027_notifications.sql`:

```sql
-- Notifications N-1: per-user notification feed. One row = one alert for ONE
-- recipient (user_id). type/title/body/link describe it; actor_id is who/what
-- caused it (a user id, 'system', later 'smithai'). read_at NULL = unread.
-- user_id is TEXT to match users.id (consistent with prior tier sub-projects).
CREATE TABLE IF NOT EXISTS notifications (
  id         TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
  user_id    TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,  -- recipient
  type       TEXT NOT NULL,         -- 'message' | 'job_assigned' (later: 'invoice_viewed' | 'ai')
  title      TEXT NOT NULL,
  body       TEXT,
  link       TEXT,                  -- in-app target, e.g. /console/comm or /console/jobs/:id
  actor_id   TEXT,                  -- who/what caused it
  read_at    TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS notifications_user_created_idx
  ON notifications (user_id, created_at DESC);
```

- [ ] **Step 2: Apply the migration**

Run: `cd backend && psql "$DATABASE_URL" -f migrations/027_notifications.sql`
Expected: `CREATE TABLE` then `CREATE INDEX` (or no error if re-run — both are `IF NOT EXISTS`).

- [ ] **Step 3: Write the failing service test**

`backend/src/__tests__/notificationService.test.ts`:

```ts
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
```

- [ ] **Step 4: Run the test to verify it fails**

Run: `cd backend && npx vitest run src/__tests__/notificationService.test.ts`
Expected: FAIL (cannot import `../notificationService`) — or SKIP if `DATABASE_URL` is unset. If skipped, set `DATABASE_URL` to the dev DB before continuing; these tests must actually run.

- [ ] **Step 5: Write the service**

`backend/src/notificationService.ts`:

```ts
/**
 * Notifications N-1: per-user notification store.
 *
 * Mirrors crewPositionService (requirePg guard, class + singleton). AI-ready:
 * the Phase-5 SmithAI navi reuses `create` (send_notification) and
 * `listForUser` (read_notifications) verbatim. No AI code here.
 */
import { pg, isPgEnabled } from './db';

export interface Notification {
  id: string;
  user_id: string;
  type: string;
  title: string;
  body: string | null;
  link: string | null;
  actor_id: string | null;
  read_at: Date | null;
  created_at: Date;
}

export interface CreateNotificationInput {
  userId: string;
  type: string;
  title: string;
  body?: string;
  link?: string;
  actorId?: string;
}

function requirePg() {
  if (!isPgEnabled() || !pg) {
    throw new Error('[notificationService] DATABASE_URL is required; pg pool is not initialized');
  }
  return pg;
}

class NotificationService {
  async create(input: CreateNotificationInput): Promise<Notification> {
    const db = requirePg();
    const r = await db.query<Notification>(
      `INSERT INTO notifications (user_id, type, title, body, link, actor_id)
       VALUES ($1, $2, $3, $4, $5, $6)
       RETURNING id, user_id, type, title, body, link, actor_id, read_at, created_at`,
      [input.userId, input.type, input.title, input.body ?? null, input.link ?? null, input.actorId ?? null]
    );
    return r.rows[0];
  }

  async listForUser(userId: string, limit = 50): Promise<Notification[]> {
    const db = requirePg();
    const r = await db.query<Notification>(
      `SELECT id, user_id, type, title, body, link, actor_id, read_at, created_at
         FROM notifications
        WHERE user_id = $1
        ORDER BY created_at DESC
        LIMIT $2`,
      [userId, limit]
    );
    return r.rows;
  }

  // Scoped to the owner. COALESCE keeps read_at stable on re-mark (idempotent),
  // and rowCount distinguishes "owned + updated" from "not the user's / missing".
  async markRead(id: string, userId: string): Promise<boolean> {
    const db = requirePg();
    const r = await db.query(
      `UPDATE notifications
          SET read_at = COALESCE(read_at, NOW())
        WHERE id = $1 AND user_id = $2`,
      [id, userId]
    );
    return (r.rowCount ?? 0) > 0;
  }

  async unreadCount(userId: string): Promise<number> {
    const db = requirePg();
    const r = await db.query<{ count: number }>(
      `SELECT COUNT(*)::int AS count FROM notifications WHERE user_id = $1 AND read_at IS NULL`,
      [userId]
    );
    return Number(r.rows[0]?.count ?? 0);
  }
}

export const notificationService = new NotificationService();
```

- [ ] **Step 6: Run the test to verify it passes**

Run: `cd backend && npx vitest run src/__tests__/notificationService.test.ts`
Expected: PASS (5 tests).

- [ ] **Step 7: Commit**

```bash
git add backend/migrations/027_notifications.sql backend/src/notificationService.ts backend/src/__tests__/notificationService.test.ts
git commit -m "feat(notifications): N-1 table + service (create/list/markRead/unreadCount)"
```

---

## Task 2: Endpoints (backend)

**Files:**
- Create: `backend/src/notificationsRoutes.ts`
- Modify: `backend/src/server.ts`
- Test: `backend/src/__tests__/notifications-routes.test.ts`

- [ ] **Step 1: Write the failing route test**

`backend/src/__tests__/notifications-routes.test.ts`:

```ts
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
    expect(res.status).toBe(200); // crucially NOT 403 — the requireConsoleTier exemption
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
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `cd backend && npx jest src/__tests__/notifications-routes.test.ts` (with `DATABASE_URL` exported)
Expected: FAIL (cannot import `../notificationsRoutes`).

- [ ] **Step 3: Write the router**

`backend/src/notificationsRoutes.ts`:

```ts
/**
 * Notifications N-1: per-user notification feed.
 *
 *   GET   /api/notifications          -> { notifications, unreadCount } (last 50)
 *   PATCH /api/notifications/:id/read -> { ok: true } | 404
 *
 * authenticateToken ONLY. Do NOT add requireConsoleTier here -- every role
 * (incl. solo) must see their own notifications; requireConsoleTier is exactly
 * what 403s /shifts/today for solo users.
 */
import { Router, Response } from 'express';
import { authenticateToken, AuthenticatedRequest } from './auth';
import { notificationService, Notification } from './notificationService';

export const notificationsRouter = Router();

function serialize(n: Notification) {
  return {
    id: n.id,
    type: n.type,
    title: n.title,
    body: n.body,
    link: n.link,
    actorId: n.actor_id,
    readAt: n.read_at,
    createdAt: n.created_at,
  };
}

notificationsRouter.get('/', authenticateToken, async (req: AuthenticatedRequest, res: Response) => {
  const userId = req.user!.id;
  const [notifications, unreadCount] = await Promise.all([
    notificationService.listForUser(userId),
    notificationService.unreadCount(userId),
  ]);
  return res.status(200).json({ notifications: notifications.map(serialize), unreadCount });
});

notificationsRouter.patch('/:id/read', authenticateToken, async (req: AuthenticatedRequest, res: Response) => {
  const userId = req.user!.id;
  const ok = await notificationService.markRead(req.params.id, userId);
  if (!ok) return res.status(404).json({ error: 'not found' });
  return res.status(200).json({ ok: true });
});
```

- [ ] **Step 4: Mount the router in `server.ts`**

In `backend/src/server.ts`, add the import alongside the other resource routers (near line 27, after `shiftsRouter`):

```ts
import { notificationsRouter } from './notificationsRoutes';
```

Then mount it BEFORE `app.use('/api', apiRouter);` — add immediately after the shifts mount (`app.use('/api/shifts', shiftsRouter);`, ~line 156):

```ts
app.use('/api/notifications', notificationsRouter);
```

- [ ] **Step 5: Run the route test to verify it passes**

Run: `cd backend && npx jest src/__tests__/notifications-routes.test.ts` (with `DATABASE_URL` exported)
Expected: PASS (4 tests).

- [ ] **Step 6: Type-check the backend**

Run: `cd backend && npx tsc --noEmit`
Expected: clean (no errors).

- [ ] **Step 7: Commit**

```bash
git add backend/src/notificationsRoutes.ts backend/src/server.ts backend/src/__tests__/notifications-routes.test.ts
git commit -m "feat(notifications): N-1 endpoints (GET feed + PATCH read), un-gated router"
```

---

## Task 3: Producers (backend)

**Files:**
- Modify: `backend/src/jobsService.ts:289-321` (`assignCrew`)
- Modify: `backend/src/channelsRoutes.ts:227-285` (`POST /messages/inject`)
- Test: append to `backend/src/__tests__/notifications-routes.test.ts`

- [ ] **Step 1: Write the failing producer tests**

Append to `backend/src/__tests__/notifications-routes.test.ts` (add the imports `assignCrew`, `create as createJob` from `../jobsService`, and `channelRegistry` from `../channelRegistry` at the top of the file):

```ts
// add to the existing top-of-file imports:
// import { create as createJob, assignCrew } from '../jobsService';
// import { channelRegistry } from '../channelRegistry';
// import { channelsRouter } from '../channelsRoutes';

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
    // The producer is wrapped in try/catch; force its create() to reject and
    // assert the assignment still resolves (best-effort, never breaks the action).
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
    injectApp.use('/api', (await import('../auth')).authenticateToken, channelsRouter);

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
});
```

> No `vi` import — this is Jest; `jest.spyOn` is an ambient global. The `injectApp` is built inline because `channelsRouter` expects `authenticateToken` applied by its parent (in production it sits under `apiRouter`).

- [ ] **Step 2: Run to verify the producer tests fail**

Run: `cd backend && npx jest src/__tests__/notifications-routes.test.ts` (with `DATABASE_URL` exported)
Expected: FAIL — the new `producers` describe block fails (no notifications produced yet); the earlier endpoint tests still pass.

- [ ] **Step 3: Add the job-assigned producer**

In `backend/src/jobsService.ts`, add the import at the top (alongside the existing `auditLog` import):

```ts
import { notificationService } from './notificationService';
```

Then in `assignCrew`, right after the existing `auditLog.log(AuditAction.JOB_CREW_ASSIGNED, ...)` call and before `return assignment;` (~line 311):

```ts
    // N-1 producer: notify the assignee (best-effort -- a failed notification
    // must not fail the assignment). Awaited in-request, not fire-and-forget.
    try {
      await notificationService.create({
        userId: profileId,
        type: 'job_assigned',
        title: `You were assigned ${job.title}`,
        link: `/console/jobs/${jobId}`,
        actorId: job.foremanId,
      });
    } catch (err) {
      console.warn('[assignCrew] notification producer failed:', (err as Error).message);
    }
```

- [ ] **Step 4: Add the new-message producer**

In `backend/src/channelsRoutes.ts`, add the import at the top (alongside `channelRegistry`):

```ts
import { notificationService } from './notificationService';
```

Change the handler signature on the inject route (~line 227) from `(req: Request, res: Response) => {` to `async (req: Request, res: Response) => {`. Then, immediately before the final `res.status(201).json({ ... })` (~line 280), add:

```ts
  // N-1 producer: notify other channel members (best-effort; never fail the
  // send). Awaited in-request per CLAUDE.md Rule 2 (not fire-and-forget). Open
  // channels (memberIds empty) produce none -- we never fan out to a whole org.
  const ch = channelRegistry.get(channelId);
  if (ch) {
    const preview = content.length > 80 ? `${content.slice(0, 77)}...` : content;
    const recipients = ch.memberIds.filter((m) => m !== senderId);
    await Promise.all(
      recipients.map((m) =>
        notificationService
          .create({
            userId: m,
            type: 'message',
            title: `New message in ${ch.name}`,
            body: preview,
            link: '/console/comm',
            actorId: senderId,
          })
          .catch((e) => console.warn('[Inject] notify failed for', m, (e as Error).message))
      )
    );
  }
```

- [ ] **Step 5: Run the producer tests to verify they pass**

Run: `cd backend && npx jest src/__tests__/notifications-routes.test.ts` (with `DATABASE_URL` exported)
Expected: PASS (endpoint + producer blocks all green).

- [ ] **Step 6: Type-check + full backend suite**

Run: `cd backend && npx tsc --noEmit && npx jest` (with `DATABASE_URL` exported)
Expected: tsc clean; full suite green (no regressions in channels/jobs tests).

- [ ] **Step 7: Commit**

```bash
git add backend/src/jobsService.ts backend/src/channelsRoutes.ts backend/src/__tests__/notifications-routes.test.ts
git commit -m "feat(notifications): N-1 producers (job-assigned + new-message), best-effort"
```

---

## Task 4: Portal client + store + polling

**Files:**
- Create: `desktop/portal/src/console/api/notificationsClient.ts`
- Create: `desktop/portal/src/console/stores/notificationsStore.ts`
- Create: `desktop/portal/src/console/hooks/useNotificationsPolling.ts`
- Test: `desktop/portal/src/console/stores/__tests__/notificationsStore.test.ts`
- Test: `desktop/portal/src/console/hooks/__tests__/useNotificationsPolling.test.ts`

- [ ] **Step 1: Write the client**

`desktop/portal/src/console/api/notificationsClient.ts`:

```ts
// desktop/portal/src/console/api/notificationsClient.ts
//
// REST wrapper for /api/notifications. Mirrors invoicesClient.ts
// (credentials:'include' cookie auth, ok/err result envelope).

export interface NotificationItem {
  id: string;
  type: string;
  title: string;
  body: string | null;
  link: string | null;
  actorId: string | null;
  readAt: string | null;
  createdAt: string;
}

export type NotificationsResult<T> =
  | ({ ok: true } & T)
  | { ok: false; status: number; error: string };

interface JsonInit { method?: string; body?: unknown }

async function call<T>(path: string, init: JsonInit = {}): Promise<NotificationsResult<T>> {
  const res = await fetch(path, {
    method: init.method ?? 'GET',
    credentials: 'include',
    headers: init.body !== undefined ? { 'Content-Type': 'application/json' } : undefined,
    body: init.body !== undefined ? JSON.stringify(init.body) : undefined,
  });
  if (res.status === 204) return { ok: true } as NotificationsResult<T>;
  if (!res.ok) {
    const err = await res.json().catch(() => ({ error: res.statusText }));
    return { ok: false, status: res.status, error: err.error || 'Request failed' };
  }
  const data = (await res.json()) as T;
  return { ok: true, ...data } as NotificationsResult<T>;
}

export const notificationsClient = {
  list: () => call<{ notifications: NotificationItem[]; unreadCount: number }>('/api/notifications'),
  markRead: (id: string) =>
    call<{ ok: true }>(`/api/notifications/${encodeURIComponent(id)}/read`, { method: 'PATCH' }),
};
```

- [ ] **Step 2: Write the failing store test**

`desktop/portal/src/console/stores/__tests__/notificationsStore.test.ts`:

```ts
import { describe, it, expect, beforeEach } from 'vitest';
import { useNotificationsStore } from '../notificationsStore';
import type { NotificationItem } from '../../api/notificationsClient';

function n(id: string, overrides: Partial<NotificationItem> = {}): NotificationItem {
  return {
    id, type: 'message', title: `t-${id}`, body: null, link: '/console/comm',
    actorId: null, readAt: null, createdAt: '2026-05-25T10:00:00Z', ...overrides,
  };
}

describe('notificationsStore', () => {
  beforeEach(() => useNotificationsStore.getState().clear());

  it('setNotifications replaces list + count and clears stale', () => {
    useNotificationsStore.getState().markStale(true);
    useNotificationsStore.getState().setNotifications([n('a'), n('b')], 2);
    const s = useNotificationsStore.getState();
    expect(s.notifications.map((x) => x.id)).toEqual(['a', 'b']);
    expect(s.unreadCount).toBe(2);
    expect(s.isStale).toBe(false);
  });

  it('markRead flips readAt and decrements unreadCount once', () => {
    useNotificationsStore.getState().setNotifications([n('a'), n('b')], 2);
    useNotificationsStore.getState().markRead('a');
    let s = useNotificationsStore.getState();
    expect(s.notifications.find((x) => x.id === 'a')!.readAt).not.toBeNull();
    expect(s.unreadCount).toBe(1);
    // re-marking the same id does not double-decrement
    useNotificationsStore.getState().markRead('a');
    s = useNotificationsStore.getState();
    expect(s.unreadCount).toBe(1);
  });

  it('clear resets to empty', () => {
    useNotificationsStore.getState().setNotifications([n('a')], 1);
    useNotificationsStore.getState().clear();
    const s = useNotificationsStore.getState();
    expect(s.notifications).toEqual([]);
    expect(s.unreadCount).toBe(0);
  });
});
```

- [ ] **Step 3: Run the store test to verify it fails**

Run: `cd desktop/portal && npx vitest run src/console/stores/__tests__/notificationsStore.test.ts`
Expected: FAIL (cannot import `../notificationsStore`).

- [ ] **Step 4: Write the store**

`desktop/portal/src/console/stores/notificationsStore.ts`:

```ts
// desktop/portal/src/console/stores/notificationsStore.ts
import { create } from 'zustand';
import type { NotificationItem } from '../api/notificationsClient';

interface NotificationsState {
  notifications: NotificationItem[];
  unreadCount: number;
  isStale: boolean;
  setNotifications: (notifications: NotificationItem[], unreadCount: number) => void;
  markRead: (id: string) => void;
  markStale: (b: boolean) => void;
  clear: () => void;
}

export const useNotificationsStore = create<NotificationsState>((set) => ({
  notifications: [],
  unreadCount: 0,
  isStale: false,
  setNotifications: (notifications, unreadCount) => set({ notifications, unreadCount, isStale: false }),
  markRead: (id) => set((s) => {
    let changed = false;
    const notifications = s.notifications.map((x) => {
      if (x.id === id && x.readAt === null) {
        changed = true;
        return { ...x, readAt: new Date().toISOString() };
      }
      return x;
    });
    return changed ? { notifications, unreadCount: Math.max(0, s.unreadCount - 1) } : {};
  }),
  markStale: (isStale) => set({ isStale }),
  clear: () => set({ notifications: [], unreadCount: 0, isStale: false }),
}));
```

- [ ] **Step 5: Run the store test to verify it passes**

Run: `cd desktop/portal && npx vitest run src/console/stores/__tests__/notificationsStore.test.ts`
Expected: PASS (3 tests).

- [ ] **Step 6: Write the failing polling test**

`desktop/portal/src/console/hooks/__tests__/useNotificationsPolling.test.ts`:

```ts
import { describe, it, expect, beforeEach, afterEach, vi } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useNotificationsPolling } from '../useNotificationsPolling';
import { useNotificationsStore } from '../../stores/notificationsStore';
import * as client from '../../api/notificationsClient';

describe('useNotificationsPolling', () => {
  beforeEach(() => {
    useNotificationsStore.getState().clear();
    vi.useFakeTimers();
  });
  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('fetches on mount and stores the result', async () => {
    const spy = vi.spyOn(client.notificationsClient, 'list').mockResolvedValue({
      ok: true,
      notifications: [{ id: 'a', type: 'message', title: 't', body: null, link: null, actorId: null, readAt: null, createdAt: '2026-05-25T10:00:00Z' }],
      unreadCount: 1,
    });
    renderHook(() => useNotificationsPolling(15000));
    await act(async () => { await Promise.resolve(); });
    expect(spy).toHaveBeenCalledTimes(1);
    expect(useNotificationsStore.getState().unreadCount).toBe(1);
  });

  it('fetches again after the interval', async () => {
    const spy = vi.spyOn(client.notificationsClient, 'list').mockResolvedValue({ ok: true, notifications: [], unreadCount: 0 });
    renderHook(() => useNotificationsPolling(15000));
    await act(async () => { await Promise.resolve(); });
    await act(async () => { vi.advanceTimersByTime(15001); await Promise.resolve(); });
    expect(spy).toHaveBeenCalledTimes(2);
  });

  it('marks stale on fetch failure', async () => {
    vi.spyOn(client.notificationsClient, 'list').mockResolvedValue({ ok: false, status: 500, error: 'oops' });
    renderHook(() => useNotificationsPolling(15000));
    await act(async () => { await Promise.resolve(); });
    expect(useNotificationsStore.getState().isStale).toBe(true);
  });

  it('cleans up the interval on unmount', async () => {
    const spy = vi.spyOn(client.notificationsClient, 'list').mockResolvedValue({ ok: true, notifications: [], unreadCount: 0 });
    const { unmount } = renderHook(() => useNotificationsPolling(15000));
    await act(async () => { await Promise.resolve(); });
    unmount();
    await act(async () => { vi.advanceTimersByTime(60000); await Promise.resolve(); });
    expect(spy).toHaveBeenCalledTimes(1);
  });
});
```

- [ ] **Step 7: Run the polling test to verify it fails**

Run: `cd desktop/portal && npx vitest run src/console/hooks/__tests__/useNotificationsPolling.test.ts`
Expected: FAIL (cannot import `../useNotificationsPolling`).

- [ ] **Step 8: Write the polling hook**

`desktop/portal/src/console/hooks/useNotificationsPolling.ts`:

```ts
// desktop/portal/src/console/hooks/useNotificationsPolling.ts
import { useEffect, useRef } from 'react';
import { notificationsClient } from '../api/notificationsClient';
import { useNotificationsStore } from '../stores/notificationsStore';

export function useNotificationsPolling(intervalMs: number = 15_000): void {
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    const fetchOnce = async () => {
      const r = await notificationsClient.list();
      if (r.ok) useNotificationsStore.getState().setNotifications(r.notifications, r.unreadCount);
      else useNotificationsStore.getState().markStale(true);
    };
    const start = () => {
      if (intervalRef.current !== null) return;
      intervalRef.current = setInterval(fetchOnce, intervalMs);
    };
    const stop = () => {
      if (intervalRef.current !== null) {
        clearInterval(intervalRef.current);
        intervalRef.current = null;
      }
    };
    const onVisibility = () => {
      if (document.visibilityState === 'visible') {
        fetchOnce();
        start();
      } else {
        stop();
      }
    };

    fetchOnce();
    start();
    document.addEventListener('visibilitychange', onVisibility);

    return () => {
      stop();
      document.removeEventListener('visibilitychange', onVisibility);
    };
  }, [intervalMs]);
}
```

- [ ] **Step 9: Run the polling test to verify it passes**

Run: `cd desktop/portal && npx vitest run src/console/hooks/__tests__/useNotificationsPolling.test.ts`
Expected: PASS (4 tests).

- [ ] **Step 10: Commit**

```bash
git add desktop/portal/src/console/api/notificationsClient.ts desktop/portal/src/console/stores/notificationsStore.ts desktop/portal/src/console/hooks/useNotificationsPolling.ts desktop/portal/src/console/stores/__tests__/notificationsStore.test.ts desktop/portal/src/console/hooks/__tests__/useNotificationsPolling.test.ts
git commit -m "feat(notifications): N-1 portal client + store + polling hook"
```

---

## Task 5: NotificationsCard + dashboard swap

**Files:**
- Modify: `desktop/portal/src/console/components/adaptive-home/cards.tsx`
- Modify: `desktop/portal/src/console/components/adaptive-home/AdaptiveDashboard.tsx`
- Test: `desktop/portal/src/console/components/adaptive-home/__tests__/NotificationsCard.test.tsx`

- [ ] **Step 1: Write the failing card test**

`desktop/portal/src/console/components/adaptive-home/__tests__/NotificationsCard.test.tsx`:

```tsx
import { render, screen } from '@testing-library/react';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { NotificationsCard } from '../cards';
import { useNotificationsStore } from '../../../stores/notificationsStore';
import type { NotificationItem } from '../../../api/notificationsClient';

// The card mounts useNotificationsPolling on render; stub it so the test drives
// the store directly (no network, no timers).
vi.mock('../../../hooks/useNotificationsPolling', () => ({ useNotificationsPolling: () => {} }));

function n(id: string, o: Partial<NotificationItem> = {}): NotificationItem {
  return {
    id, type: 'message', title: `Title ${id}`, body: null, link: '/console/comm',
    actorId: null, readAt: null, createdAt: new Date().toISOString(), ...o,
  };
}

function renderCard() {
  return render(<MemoryRouter><NotificationsCard /></MemoryRouter>);
}

describe('NotificationsCard', () => {
  beforeEach(() => useNotificationsStore.getState().clear());

  it('renders the empty state when there are no notifications', () => {
    renderCard();
    expect(screen.getByText(/no notifications/i)).toBeInTheDocument();
  });

  it('renders items + the unread count in the header', () => {
    useNotificationsStore.getState().setNotifications([n('a'), n('b', { readAt: new Date().toISOString() })], 1);
    renderCard();
    expect(screen.getByText('Title a')).toBeInTheDocument();
    expect(screen.getByText('Title b')).toBeInTheDocument();
    expect(screen.getByText('1')).toBeInTheDocument(); // unread count badge
  });
});
```

- [ ] **Step 2: Run the card test to verify it fails**

Run: `cd desktop/portal && npx vitest run src/console/components/adaptive-home/__tests__/NotificationsCard.test.tsx`
Expected: FAIL (`NotificationsCard` is not exported from `../cards`).

- [ ] **Step 3: Add `NotificationsCard` to `cards.tsx`**

In `desktop/portal/src/console/components/adaptive-home/cards.tsx`, change the react-router-dom import (line 2) to add `useNavigate`:

```ts
import { NavLink, useNavigate } from 'react-router-dom';
```

Add these imports alongside the other hook/store imports near the top (after line 16):

```ts
import { useNotificationsPolling } from '../../hooks/useNotificationsPolling';
import { useNotificationsStore } from '../../stores/notificationsStore';
import { notificationsClient, type NotificationItem } from '../../api/notificationsClient';
```

Append this component to the end of the file (after `InvoicesCard`):

```tsx
// NOTIFICATIONS -- true alerts for the current user (job assigned, new message;
// later invoice-viewed + AI). Replaces the redundant SHIFT card on the dashboard
// (the shift clock lives in the console header now). Clicking an item marks it
// read (optimistic store + best-effort PATCH) and navigates to its in-app target.
function formatRelative(iso: string): string {
  const secs = Math.max(0, Math.floor((Date.now() - new Date(iso).getTime()) / 1000));
  if (secs < 60) return 'just now';
  const mins = Math.floor(secs / 60);
  if (mins < 60) return `${mins}m ago`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `${hrs}h ago`;
  return `${Math.floor(hrs / 24)}d ago`;
}

export function NotificationsCard() {
  useNotificationsPolling();
  const navigate = useNavigate();
  const notifications = useNotificationsStore((s) => s.notifications);
  const unreadCount = useNotificationsStore((s) => s.unreadCount);

  const onOpen = (item: NotificationItem) => {
    useNotificationsStore.getState().markRead(item.id);
    void notificationsClient.markRead(item.id);
    if (item.link) navigate(item.link);
  };

  return (
    <ModuleCard
      title="Notifications"
      right={
        unreadCount > 0
          ? <span className="font-mono text-[11px] text-console-accent tabular-nums">{unreadCount}</span>
          : undefined
      }
    >
      {notifications.length === 0 ? (
        <div className="text-console-text-muted">No notifications.</div>
      ) : (
        <div className="flex flex-col gap-1.5">
          {notifications.slice(0, 12).map((item) => (
            <button
              key={item.id}
              type="button"
              onClick={() => onOpen(item)}
              className="flex items-baseline gap-2 text-left w-full hover:bg-console-bg rounded px-1 -mx-1"
            >
              <span className={`text-[10px] leading-none ${item.readAt ? 'text-console-text-muted' : 'text-console-accent'}`}>●</span>
              <span className="truncate">{item.title}</span>
              <span className="text-console-text-muted text-[11px] ml-auto pl-2 whitespace-nowrap">
                {formatRelative(item.createdAt)}
              </span>
            </button>
          ))}
        </div>
      )}
    </ModuleCard>
  );
}
```

- [ ] **Step 4: Run the card test to verify it passes**

Run: `cd desktop/portal && npx vitest run src/console/components/adaptive-home/__tests__/NotificationsCard.test.tsx`
Expected: PASS (2 tests).

- [ ] **Step 5: Swap the card in `AdaptiveDashboard.tsx`**

In `desktop/portal/src/console/components/adaptive-home/AdaptiveDashboard.tsx`, update the import block (lines 6-14) to drop `ShiftCard` and add `NotificationsCard`:

```ts
import {
  NotificationsCard,
  OpenTasksCard,
  DispatchCard,
  SystemCard,
  MapPreview,
  JobsCard,
  InvoicesCard,
} from './cards';
```

Change the carousel `PANELS` entry (line 81) from:

```tsx
        { key: 'shift', el: <ShiftCard /> },
```

to:

```tsx
        { key: 'notifications', el: <NotificationsCard /> },
```

Change the desktop status-grid block (lines 120-122) from:

```tsx
              <Card className="h-[172px]">
                <ShiftCard />
              </Card>
```

to:

```tsx
              <Card className="h-[172px]">
                <NotificationsCard />
              </Card>
```

(Leave `ShiftCard` defined and exported in `cards.tsx` — it is just no longer used by the dashboard, per the spec.)

- [ ] **Step 6: Full portal gates**

Run: `cd desktop/portal && npx vitest run && npx tsc --noEmit && npm run build`
Expected: full test suite green; tsc clean; Vite build succeeds. (`ShiftCard` being unused is fine — it is still exported, so no unused-symbol error.)

- [ ] **Step 7: Commit**

```bash
git add desktop/portal/src/console/components/adaptive-home/cards.tsx desktop/portal/src/console/components/adaptive-home/AdaptiveDashboard.tsx desktop/portal/src/console/components/adaptive-home/__tests__/NotificationsCard.test.tsx
git commit -m "feat(notifications): N-1 NotificationsCard replaces dashboard shift card"
```

---

## Final verification (after all tasks)

- [ ] Backend: `cd backend && npx tsc --noEmit && npx vitest run` — clean + green.
- [ ] Portal: `cd desktop/portal && npx vitest run && npx tsc --noEmit && npm run build` — green + clean + builds.
- [ ] **Deferred-verify (live):** the running dev backend must be restarted to pick up the new `/api/notifications` mount + producers (the earlier `/api/shifts/today` route did not hot-reload). Once restarted: log in, assign yourself crew on a job / send a channel message, and confirm the dashboard `NotificationsCard` shows the alert and the unread count, and that clicking an item navigates + clears its unread dot.

## Notes / scope

- **AI is NOT built here.** `read_notifications` / `send_notification` are Phase-5 (navi spec). The service is AI-ready (`create` + `listForUser` reused verbatim).
- **invoice-viewed -> N-2** (needs an `invoice_links` owner column + public `/i/:uuid` hook + a new `INVOICE_VIEWED` audit action).
- **Real-time WS push, preferences, a full `/console/notifications` page -> N-3.**
- **Open channels (empty `memberIds`) produce no message notifications** — we never fan out to a whole org in N-1. Only explicit members (DMs / member-listed channels) are notified.
- Producers are **awaited in-request** (CLAUDE.md Rule 2 — not fire-and-forget) and wrapped so a failed notification never breaks the send/assign.
