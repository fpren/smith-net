# Notifications -- Foundation (N-1) Design

> First slice of the notifications system. Status: design approved 2026-05-25.

**Goal:** A per-user notifications store with a feed endpoint and a dashboard
`NotificationsCard` (replacing the redundant shift card), populated by two
producers (new message, job assigned). The store/service is **AI-ready** so the
SmithAI navi can later read it (situational awareness) and write to it (AI
alerts) -- that AI integration is Phase-5 (captured in the navi spec), not built
here.

---

## 1. Context

The portal dashboard has a redundant shift card (the shift clock now lives in the
console header). It should be replaced by something useful; the user chose
**notifications** -- true alerts ("you were assigned a job", "new message for
you", later "a client viewed your invoice"). There is **no notifications store
today** (only a notification-preferences stub in settings). The **audit log**
already records events across the app (`JOB_*`, `SHIFT_*`, `MESSAGE_*`, ...) and is
the broad "what's happening" stream; **notifications are the user-facing,
read/unread subset**.

The bigger vision: notifications is the shared event/alert backbone, and **SmithAI
reads it (awareness) and sends to it (alerts)** -- the Android `AISupervisor`
pattern. But **AI is Phase-5-frozen** (no inline LLM; the navi builds on the
`llmWorker`), so the AI hooks are **design-only** here and captured in the navi
spec. This N-1 builds the backbone AI-ready.

Decomposition:
```
N-1 (this)  store + service (AI-ready) + endpoints + NotificationsCard
            + producers: new-message, job-assigned ; polled.
N-2         invoice-viewed producer (invoice_links owner column + public hook
            + INVOICE_VIEWED audit) + read/unread polish + a full notifications view.
N-3         real-time WS push + notification preferences + the SmithAI navi
            read_notifications / send_notification tools + supervisor (Phase-5).
```

Hard rules: per-profile isolation (a notification belongs to ONE recipient
`user_id`; never expose another user's); identity from `req.user`
(`authenticateToken`), never `X-User-Id`; parameterized pg queries; no emoji.

---

## 2. Scope

### In scope (N-1)
- `notifications` table (migration `027_notifications.sql`).
- `notificationService` (AI-ready: `create` / `listForUser` / `markRead` /
  `unreadCount`).
- A dedicated `/api/notifications` router (`authenticateToken` only -- NOT behind
  `requireConsoleTier`), mounted before the broad `/api` `apiRouter`.
- Producers: **new message** (notify channel members != sender) and **job
  assigned** (notify the assignee). Best-effort -- never break the underlying
  action.
- Frontend: `notificationsClient`, `useNotificationsPolling`,
  `notificationsStore`, and `NotificationsCard` replacing `ShiftCard` in
  `AdaptiveDashboard`.

### Out of scope
- **invoice-viewed** producer -> N-2 (needs an `invoice_links` owner column +
  public `/i/:uuid` hook + a new `INVOICE_VIEWED` audit action).
- Real-time WS push, a full `/console/notifications` page, preferences -> N-3.
- **SmithAI read/send + system-wide awareness** -> Phase-5 (design captured in the
  navi spec; built on the `llmWorker`). No AI code here.

---

## 3. Data model -- `backend/migrations/027_notifications.sql`

Idempotent, house style (`CREATE TABLE IF NOT EXISTS`, `DO $$ ... END $$` guards):
```sql
CREATE TABLE IF NOT EXISTS notifications (
  id         TEXT PRIMARY KEY DEFAULT gen_random_uuid()::text,
  user_id    TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,  -- recipient
  type       TEXT NOT NULL,         -- 'message' | 'job_assigned' (later: 'invoice_viewed' | 'ai')
  title      TEXT NOT NULL,
  body       TEXT,
  link       TEXT,                  -- in-app target, e.g. /console/comm or /console/jobs/:id
  actor_id   TEXT,                  -- who/what caused it (a user id, 'system', later 'smithai')
  read_at    TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS notifications_user_created_idx
  ON notifications (user_id, created_at DESC);
```
`user_id` is `TEXT` to match `users.id`. Applied manually:
`cd backend && psql "$DATABASE_URL" -f migrations/027_notifications.sql`.

---

## 4. Service -- `backend/src/notificationService.ts`

Mirrors `crewPositionService` (`requirePg()` guard, class + singleton,
`db.query<T>(sql, params)`). AI-ready surface (the Phase-5 navi reuses `create`
and `listForUser` verbatim):
- `create(input: { userId: string; type: string; title: string; body?: string; link?: string; actorId?: string }): Promise<Notification>`
- `listForUser(userId: string, limit = 50): Promise<Notification[]>` -- newest first.
- `markRead(id: string, userId: string): Promise<boolean>` -- scoped to the owner
  (`WHERE id=$1 AND user_id=$2`), sets `read_at = NOW()` if null.
- `unreadCount(userId: string): Promise<number>`.

`Notification` row type mirrors the table columns (camelCase in the API serializer).

---

## 5. Endpoints -- `backend/src/notificationsRoutes.ts`

A dedicated router, **`authenticateToken` only** (every role sees their own; do
NOT apply `requireConsoleTier` -- that is what 403s `/shifts/today` for solo).
Mount in `server.ts` near the other resource routers and BEFORE
`app.use('/api', apiRouter)` so it is not shadowed/gated:
```ts
app.use('/api/notifications', notificationsRouter);
```
Routes:
- `GET /api/notifications` -> `{ notifications: Notification[], unreadCount: number }`
  for `req.user!.id` (last 50).
- `PATCH /api/notifications/:id/read` -> mark one read (scoped to `req.user!.id`);
  returns `{ ok: true }` or 404 if not the user's.

Serialize to camelCase (`{ id, type, title, body, link, actorId, readAt, createdAt }`).

---

## 6. Producers (best-effort -- never throw into the underlying action)

Each producer wraps its insert in try/catch and logs on failure (a failed
notification must not fail the message send / assignment).

- **new message** -- `backend/src/channelsRoutes.ts` message handler
  (`POST /messages/inject`). After the message is accepted, for each
  `channelRegistry.get(channelId).memberIds` that is not the sender:
  `notificationService.create({ userId: member, type: 'message', title: 'New message in <channel>', body: <preview>, link: '/console/comm', actorId: senderId })`.
- **job assigned** -- `backend/src/jobsService.ts` `assignCrew`, right after the
  existing `auditLog.log(AuditAction.JOB_CREW_ASSIGNED, ...)` call (~line 306):
  `notificationService.create({ userId: profileId, type: 'job_assigned', title: 'You were assigned ' + job.title, link: '/console/jobs/' + jobId, actorId: job.foremanId })`.

---

## 7. Frontend

- `desktop/portal/src/console/api/notificationsClient.ts` -- `call<T>` wrapper
  (`credentials: 'include'`) like `invoicesClient`: `list()` ->
  `{ notifications, unreadCount }`; `markRead(id)` -> `PATCH .../:id/read`.
- `desktop/portal/src/console/stores/notificationsStore.ts` -- zustand:
  `{ notifications, unreadCount, isStale, setNotifications, markStale, clear }`.
- `desktop/portal/src/console/hooks/useNotificationsPolling.ts` -- mirrors
  `useInvoicesPolling` (15s interval, fetch on mount, pause on hidden, set store
  on success / `markStale(true)` on error).
- `desktop/portal/src/console/components/adaptive-home/cards.tsx` --
  `NotificationsCard` using the `ModuleCard` idiom: header "Notifications"
  (unread count as the `right` slot), a scrollable list of recent items (title +
  relative time, unread visually marked). Clicking an item navigates to
  `notification.link` and calls `markRead`.
- `desktop/portal/src/console/components/adaptive-home/AdaptiveDashboard.tsx` --
  **replace `ShiftCard`** with `NotificationsCard` in BOTH the phone-carousel
  `PANELS` entry (`{ key: 'shift', el: <ShiftCard /> }` -> `{ key: 'notifications', el: <NotificationsCard /> }`) and the desktop status-grid block
  (`<Card className="h-[172px]"><ShiftCard /></Card>`). `ShiftCard` itself stays in
  `cards.tsx` (unused by the dashboard now) -- do not delete it in this slice.

---

## 8. Per-profile isolation

Every read/write is scoped to `req.user!.id`: `listForUser` /
`unreadCount` filter by `user_id`; `markRead` matches `id AND user_id`; producers
set `user_id` to the specific recipient. No endpoint returns another user's
notifications. (The Hetzner per-profile rule.)

---

## 9. Testing / acceptance

- **Service** (`notificationService`): create -> listForUser returns it; markRead
  scoped to owner (another user cannot mark it); unreadCount reflects unread.
  (Follow the existing backend service test pattern.)
- **Endpoints**: `GET /api/notifications` returns only the caller's; a non-owner
  `PATCH :id/read` 404s; route is reachable by a solo user (NOT 403 -- the
  `requireConsoleTier` exemption).
- **Producers**: assigning crew creates a `job_assigned` notification for the
  assignee; a message creates `message` notifications for other members, not the
  sender; a failing notification insert does NOT fail the action.
- **Frontend**: `notificationsStore` set/clear; `NotificationsCard` renders a list
  + empty state + unread marker (store seeded); `useNotificationsPolling` mirrors
  the invoices polling test.
- **Gates**: full portal `npm run test:run` green, `tsc --noEmit` clean, build ok;
  backend `tsc` clean.

---

## 10. AI integration (Phase-5 -- design only, captured in the navi spec)

The navi spec (`2026-05-24-portal-smithai-navi-design.md`) gains two tools and a
supervisor note (built when AI is unfrozen, via the `llmWorker`):
- `read_notifications` -- the navi reads `notificationService.listForUser` for
  situational awareness ("what's going on"), alongside the audit-log event stream.
- `send_notification` -- the navi/supervisor calls `notificationService.create({ ... actorId: 'smithai' })` to raise an AI alert (suggestion-only; subject to the
  navi's tier gates + the determinism rules).
No AI code ships in N-1; the service is built so these plug in with zero rework.

---

## 11. Open questions

None. Store shape, the dedicated un-gated router (avoiding the `/shifts/today`
403), the two N-1 producers (invoice-viewed deferred to N-2), the card
replacement, and the Phase-5 AI boundary are all decided above.
