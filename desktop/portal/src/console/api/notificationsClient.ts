// desktop/portal/src/console/api/notificationsClient.ts
//
// REST wrapper for /api/notifications. Mirrors invoicesClient.ts
// (credentials:'include' cookie auth, ok/err result envelope).

import { httpCall } from './httpCall';

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
  const r = await httpCall<T>(path, {
    method: init.method ?? 'GET',
    headers: init.body !== undefined ? { 'Content-Type': 'application/json' } : undefined,
    body: init.body !== undefined ? JSON.stringify(init.body) : undefined,
  });
  if (!r.ok) {
    return { ok: false, status: r.status, error: r.error };
  }
  return { ok: true, ...((r.data ?? {}) as T) } as NotificationsResult<T>;
}

export const notificationsClient = {
  list: () => call<{ notifications: NotificationItem[]; unreadCount: number }>('/api/notifications'),
  markRead: (id: string) =>
    call<{ ok: true }>(`/api/notifications/${encodeURIComponent(id)}/read`, { method: 'PATCH' }),
};
