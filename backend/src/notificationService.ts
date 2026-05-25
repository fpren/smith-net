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
