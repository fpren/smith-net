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
import { Router, Response, NextFunction } from 'express';
import { authenticateToken, AuthenticatedRequest } from './auth';
import { notificationService, Notification } from './notificationService';

export const notificationsRouter = Router();

interface SerializedNotification {
  id: string;
  type: string;
  title: string;
  body: string | null;
  link: string | null;
  actorId: string | null;
  readAt: string | null;
  createdAt: string;
}

// Emit ISO strings (the documented wire contract) explicitly, rather than
// relying on res.json()'s implicit Date->ISO coercion, so the typed shape
// matches the portal's NotificationItem.
function serialize(n: Notification): SerializedNotification {
  return {
    id: n.id,
    type: n.type,
    title: n.title,
    body: n.body,
    link: n.link,
    actorId: n.actor_id,
    readAt: n.read_at ? n.read_at.toISOString() : null,
    createdAt: n.created_at.toISOString(),
  };
}

// Async handlers wrap in try/catch -> next(err): Express 4 does not forward a
// rejected promise from an async handler to the error middleware, so a DB error
// (e.g. requirePg throwing, a connectivity failure) would otherwise hang the
// request instead of returning a clean 500. Mirrors jobsRoutes.ts.
notificationsRouter.get('/', authenticateToken, async (req: AuthenticatedRequest, res: Response, next: NextFunction) => {
  try {
    const userId = req.user!.id;
    const [notifications, unreadCount] = await Promise.all([
      notificationService.listForUser(userId),
      notificationService.unreadCount(userId),
    ]);
    return res.status(200).json({ notifications: notifications.map(serialize), unreadCount });
  } catch (err) {
    next(err);
  }
});

notificationsRouter.patch('/:id/read', authenticateToken, async (req: AuthenticatedRequest, res: Response, next: NextFunction) => {
  try {
    const userId = req.user!.id;
    const ok = await notificationService.markRead(req.params.id, userId);
    if (!ok) return res.status(404).json({ error: 'not found' });
    return res.status(200).json({ ok: true });
  } catch (err) {
    next(err);
  }
});
