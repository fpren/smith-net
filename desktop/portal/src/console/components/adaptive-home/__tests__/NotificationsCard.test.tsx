import { render, screen } from '@testing-library/react';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import { NotificationsCard } from '../cards';
import { useNotificationsStore } from '../../../stores/notificationsStore';
import type { NotificationItem } from '../../../api/notificationsClient';

// The card mounts useNotificationsPolling on render; stub it so the test drives
// the store directly (no network, no timers). Returns { reload } (Task 8) to
// match the real hook's shape now that the card destructures it for retry.
vi.mock('../../../hooks/useNotificationsPolling', () => ({ useNotificationsPolling: () => ({ reload: () => {} }) }));

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
