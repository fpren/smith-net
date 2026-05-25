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
