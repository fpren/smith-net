import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, beforeEach, vi } from 'vitest';
import { MessageList } from '../MessageList';
import { useCommStore } from '../../../stores/commStore';
import { useAuthStore } from '../../../auth/authStore';
import { commClient } from '../../../api/commClient';
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
