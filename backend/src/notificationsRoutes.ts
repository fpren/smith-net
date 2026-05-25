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
