import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { describe, it, expect, beforeEach } from 'vitest';
import { http, HttpResponse } from 'msw';
import { MemoryRouter } from 'react-router-dom';
import { CommRoute } from '../CommRoute';
import { useCommStore } from '../../stores/commStore';
import { useAuthStore } from '../../auth/authStore';
import { server } from '../../test/msw-server';
import type { Channel, Message } from '../../../types';

function makeChannel(id: string, name: string, createdAt = 1716000000000): Channel {
  return {
    id,
    name,
    type: 'group',
    creatorId: 'user-1',
    createdAt,
    memberIds: ['user-1'],
    isArchived: false,
    isDeleted: false,
  };
}

function makeMessage(id: string, channelId: string, content: string, ts = 1716000001000): Message {
  return {
    id,
    channelId,
    senderId: 'user-1',
    senderName: 'Test Foreman',
    content,
    timestamp: ts,
    origin: 'online',
  };
}

describe('CommRoute', () => {
  beforeEach(() => {
    useCommStore.getState().clear();
    useAuthStore.setState({
      user: {
        id: 'user-1',
        email: 'foreman@example.com',
        displayName: 'Test Foreman',
        role: 'foreman',
        emailVerified: true,
      },
    });
  });

  it('shows the "select a channel" prompt when none is selected', async () => {
    render(<MemoryRouter><CommRoute /></MemoryRouter>);
    // Polling will pull in the msw fixture channel — wait for it, then assert
    // the right pane is still showing the prompt since no channel is selected.
    await waitFor(() => expect(useCommStore.getState().channels.length).toBeGreaterThan(0));
    expect(screen.getByText(/select a channel to start/i)).toBeInTheDocument();
  });

  it('renders channel rows from polled state', async () => {
    render(<MemoryRouter><CommRoute /></MemoryRouter>);
    // The msw handler returns a channel named "general".
    await waitFor(() => expect(screen.getByText('general')).toBeInTheDocument());
  });

  it('selecting a channel populates the store and renders messages', async () => {
    // Tailor MSW to echo the seeded messages so the 3s poll on selection
    // doesn't overwrite them with the default fixture.
    server.use(
      http.get('/api/channels/:id/messages', () =>
        HttpResponse.json([
          makeMessage('m-1', 'ch-general', 'Hello team'),
          makeMessage('m-2', 'ch-general', 'How are you', 1716000002000),
        ])
      ),
    );
    useCommStore.getState().setChannels([makeChannel('ch-general', 'general')]);
    useCommStore.getState().setMessages('ch-general', [
      makeMessage('m-1', 'ch-general', 'Hello team'),
      makeMessage('m-2', 'ch-general', 'How are you', 1716000002000),
    ]);
    render(<MemoryRouter><CommRoute /></MemoryRouter>);
    fireEvent.click(screen.getByText('general'));
    expect(useCommStore.getState().selectedChannelId).toBe('ch-general');
    expect(await screen.findByText('Hello team')).toBeInTheDocument();
    expect(screen.getByText('How are you')).toBeInTheDocument();
  });

  it('shows empty-channel state when a channel has no messages', async () => {
    server.use(
      http.get('/api/channels/:id/messages', () => HttpResponse.json([])),
    );
    useCommStore.getState().setChannels([makeChannel('ch-empty', 'empty')]);
    useCommStore.getState().selectChannel('ch-empty');
    useCommStore.getState().setMessages('ch-empty', []);
    render(<MemoryRouter><CommRoute /></MemoryRouter>);
    expect(await screen.findByText(/no messages yet/i)).toBeInTheDocument();
  });

  it('shows the [OFFLINE] banner when channel list fetch fails', async () => {
    server.use(
      http.get('/api/channels', () =>
        HttpResponse.json({ error: 'offline' }, { status: 500 }),
      ),
    );
    render(<MemoryRouter><CommRoute /></MemoryRouter>);
    expect(await screen.findByText(/couldn't refresh/i)).toBeInTheDocument();
  });
});
