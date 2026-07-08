import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, beforeEach } from 'vitest';
import { http, HttpResponse } from 'msw';
import { MessageInput } from '../MessageInput';
import { useCommStore } from '../../../stores/commStore';
import { useToastStore } from '../../../stores/toastStore';
import { useAuthStore } from '../../../auth/authStore';
import { server } from '../../../test/msw-server';

describe('MessageInput optimistic send', () => {
  beforeEach(() => {
    useCommStore.getState().clear();
    useToastStore.setState({ toasts: [] });
    useAuthStore.getState().setUser({
      id: 'user-1',
      email: 'me@example.com',
      displayName: 'Me',
      role: 'solo',
      emailVerified: true,
    });
  });

  it('appends a pending message immediately and settles to sent', async () => {
    render(<MessageInput channelId="ch-general" />);
    fireEvent.change(screen.getByRole('textbox'), { target: { value: 'hello crew' } });
    fireEvent.click(screen.getByRole('button', { name: /send/i }));

    // pending appears synchronously
    const msgs = () => useCommStore.getState().messagesByChannel['ch-general'] ?? [];
    expect(msgs().some((m) => m.content === 'hello crew' && m.status === 'pending')).toBe(true);

    await waitFor(() =>
      expect(msgs().some((m) => m.content === 'hello crew' && m.status === 'sent')).toBe(true)
    );
  });

  it('marks the message failed when inject 500s', async () => {
    server.use(http.post('/api/messages/inject', () => HttpResponse.json({ error: 'x' }, { status: 500 })));
    render(<MessageInput channelId="ch-general" />);
    fireEvent.change(screen.getByRole('textbox'), { target: { value: 'doomed' } });
    fireEvent.click(screen.getByRole('button', { name: /send/i }));

    await waitFor(() => {
      const m = useCommStore.getState().messagesByChannel['ch-general']?.find((x) => x.content === 'doomed');
      expect(m?.status).toBe('failed');
      const errorToast = useToastStore.getState().toasts.find((t) => t.tone === 'error');
      expect(errorToast).toBeDefined();
    });
  });
});

describe('MessageInput attachments', () => {
  beforeEach(() => {
    useCommStore.getState().clear();
    useToastStore.setState({ toasts: [] });
    useAuthStore.getState().setUser({
      id: 'user-1',
      email: 'me@example.com',
      displayName: 'Me',
      role: 'solo',
      emailVerified: true,
    });
  });

  it('picking a file appends a pending message with media immediately, then settles sent with the server url', async () => {
    const { container } = render(<MessageInput channelId="ch-general" />);
    const fileInput = container.querySelector('input[type="file"]') as HTMLInputElement;
    expect(fileInput).not.toBeNull();
    const file = new File(['x'], 'photo.jpg', { type: 'image/jpeg' });

    fireEvent.change(fileInput, { target: { files: [file] } });

    const msgs = () => useCommStore.getState().messagesByChannel['ch-general'] ?? [];
    expect(
      msgs().some((m) => m.status === 'pending' && m.media?.type === 'image' && m.content === '')
    ).toBe(true);

    await waitFor(() => {
      const m = msgs().find((x) => x.media?.filename === 'photo.jpg' || x.media?.url === '/media/images/media-1.jpg');
      expect(m?.status).toBe('sent');
      expect(m?.media?.url).toBe('/media/images/media-1.jpg');
    });
  });

  it('marks the message failed when /api/media/upload 500s', async () => {
    server.use(http.post('/api/media/upload', () => HttpResponse.json({ error: 'x' }, { status: 500 })));
    const { container } = render(<MessageInput channelId="ch-general" />);
    const fileInput = container.querySelector('input[type="file"]') as HTMLInputElement;
    const file = new File(['x'], 'photo.jpg', { type: 'image/jpeg' });

    fireEvent.change(fileInput, { target: { files: [file] } });

    await waitFor(() => {
      const m = useCommStore
        .getState()
        .messagesByChannel['ch-general']?.find((x) => x.media?.type === 'image');
      expect(m?.status).toBe('failed');
      const errorToast = useToastStore.getState().toasts.find((t) => t.tone === 'error');
      expect(errorToast).toBeDefined();
    });
  });
});
