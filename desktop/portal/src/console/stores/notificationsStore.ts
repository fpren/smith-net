// desktop/portal/src/console/stores/notificationsStore.ts
import { create } from 'zustand';
import type { NotificationItem } from '../api/notificationsClient';

interface NotificationsState {
  notifications: NotificationItem[];
  unreadCount: number;
  isLoading: boolean;
  isStale: boolean;
  setNotifications: (notifications: NotificationItem[], unreadCount: number) => void;
  markRead: (id: string) => void;
  markLoading: (b: boolean) => void;
  markStale: (b: boolean) => void;
  clear: () => void;
}

export const useNotificationsStore = create<NotificationsState>((set) => ({
  notifications: [],
  unreadCount: 0,
  isLoading: false,
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
  markLoading: (isLoading) => set({ isLoading }),
  markStale: (isStale) => set({ isStale }),
  clear: () => set({ notifications: [], unreadCount: 0, isLoading: false, isStale: false }),
}));
