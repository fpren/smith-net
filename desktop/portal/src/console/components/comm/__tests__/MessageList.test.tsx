import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { http, HttpResponse } from 'msw';
import { MessageList } from '../MessageList';
import { useCommStore } from '../../../stores/commStore';
import { useAuthStore } from '../../../auth/authStore';
import { commClient } from '../../../api/commClient';
import { server } from '../../../test/msw-server';
import type { Message } from '../../../../types';

function msg(id: string, senderId: string, content: string): Message {
  return {
    id,
    channelId: 'ch1',
    senderId,
    senderName: senderId === 'me' ? 'Me' : 'Other',
    content,
    timestamp: Date.now(),
    origin: 'online',
  };
}

describe('MessageList delete confirmation', () => {
  beforeEach(() => {
    useCommStore.getState().clear();
    useAuthStore.getState().setUser({
      id: 'me',
      email: 'me@example.com',
      displayName: 'Me',
      role: 'solo',
      emailVerified: true,
    });
  });

  it('message delete asks for confirmation first', async () => {
    useCommStore.getState().setMessages('ch1', [msg('m1', 'me', 'hello there')]);
    const deleteSpy = vi.spyOn(commClient, 'deleteMessage');

    render(<MessageList channelId="ch1" />);

    fireEvent.click(screen.getByRole('button', { name: 'Delete message' }));
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(deleteSpy).not.toHaveBeenCalled();

    fireEvent.click(screen.getByRole('button', { name: 'Delete' }));
    expect(deleteSpy).toHaveBeenCalledTimes(1);
    expect(deleteSpy).toHaveBeenCalledWith('m1');
  });

  it('cancel does not delete the message', () => {
    useCommStore.getState().setMessages('ch1', [msg('m1', 'me', 'hello there')]);
    const deleteSpy = vi.spyOn(commClient, 'deleteMessage');

    render(<MessageList channelId="ch1" />);

    fireEvent.click(screen.getByRole('button', { name: 'Delete message' }));
    expect(screen.getByRole('dialog')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(deleteSpy).not.toHaveBeenCalled();
    expect(screen.getByText('hello there')).toBeInTheDocument();
  });
});

describe('MessageList row alignment', () => {
  beforeEach(() => {
    useCommStore.getState().clear();
    useAuthStore.getState().setUser({
      id: 'me',
      email: 'me@example.com',
      displayName: 'Me',
      role: 'solo',
      emailVerified: true,
    });
  });

  it('own messages are not right-aligned (no items-end row container)', () => {
    useCommStore.getState().setMessages('ch1', [msg('m1', 'me', 'hello there')]);
    const { container } = render(<MessageList channelId="ch1" />);
    const row = container.querySelector('li');
    expect(row).not.toBeNull();
    expect(row!.className).not.toMatch(/items-end/);
    expect(row!.className).not.toMatch(/flex-row-reverse/);
  });
});

describe('MessageList NEW divider', () => {
  beforeEach(() => {
    useCommStore.getState().clear();
    useAuthStore.getState().setUser({
      id: 'me',
      email: 'me@example.com',
      displayName: 'Me',
      role: 'solo',
      emailVerified: true,
    });
  });

  it('renders a NEW divider before the 4th message when unreadAtSelect is 2 of 5', () => {
    const msgs = [1, 2, 3, 4, 5].map((n) => msg(`m${n}`, 'other', `msg ${n}`));
    useCommStore.getState().setMessages('ch1', msgs);
    useCommStore.setState({ unreadAtSelect: { ch1: 2 } });

    const { container } = render(<MessageList channelId="ch1" />);

    const items = container.querySelectorAll('ul > li');
    expect(items).toHaveLength(6); // 5 messages + 1 divider row
    expect(items[3].textContent).toContain('NEW');
    expect(items[4].textContent).toContain('msg 4');
  });

  it('renders no NEW divider when unreadAtSelect is 0', () => {
    const msgs = [1, 2, 3, 4, 5].map((n) => msg(`m${n}`, 'other', `msg ${n}`));
    useCommStore.getState().setMessages('ch1', msgs);
    useCommStore.setState({ unreadAtSelect: { ch1: 0 } });

    const { container } = render(<MessageList channelId="ch1" />);

    expect(screen.queryByText('NEW')).toBeNull();
    const items = container.querySelectorAll('ul > li');
    expect(items).toHaveLength(5);
  });
});

describe('MessageList stale banner', () => {
  beforeEach(() => {
    useCommStore.getState().clear();
    useAuthStore.getState().setUser({
      id: 'me',
      email: 'me@example.com',
      displayName: 'Me',
      role: 'solo',
      emailVerified: true,
    });
  });

  it('renders an ErrorState banner (with retry) above the still-visible messages when stale, and retry clears it', async () => {
    useCommStore.getState().setMessages('ch1', [msg('m1', 'me', 'hello there')]);
    useCommStore.setState({ isStaleMessages: true });

    render(<MessageList channelId="ch1" />);

    // Banner renders as an alert, and the message list stays visible below it.
    expect(screen.getByRole('alert')).toBeInTheDocument();
    expect(screen.getByText(/couldn't refresh messages/i)).toBeInTheDocument();
    expect(screen.getByText('hello there')).toBeInTheDocument();

    server.use(
      http.get('/api/channels/:id/messages', () =>
        HttpResponse.json([msg('m1', 'me', 'hello there'), msg('m2', 'other', 'fresh reply')]),
      ),
    );
    fireEvent.click(screen.getByRole('button', { name: /retry/i }));

    await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument());
    expect(screen.getByText('fresh reply')).toBeInTheDocument();
    expect(useCommStore.getState().isStaleMessages).toBe(false);
  });
});
