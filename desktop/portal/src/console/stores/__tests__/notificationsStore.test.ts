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
    useNotificationsStore.getState().setNotifications([n('a'), n('b', { readAt: new Date().toISOString() })], 2);
    useNotificationsStore.getState().markRead('a');
    let s = useNotificationsStore.getState();
    expect(s.notifications.find((x) => x.id === 'a')!.readAt).not.toBeNull();
    expect(s.unreadCount).toBe(1);
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

  it('markLoading toggles isLoading independently of isStale', () => {
    useNotificationsStore.getState().markLoading(true);
    expect(useNotificationsStore.getState().isLoading).toBe(true);
    expect(useNotificationsStore.getState().isStale).toBe(false);
    useNotificationsStore.getState().markLoading(false);
    expect(useNotificationsStore.getState().isLoading).toBe(false);
  });

  it('clear resets isLoading too', () => {
    useNotificationsStore.getState().markLoading(true);
    useNotificationsStore.getState().clear();
    expect(useNotificationsStore.getState().isLoading).toBe(false);
  });
});
