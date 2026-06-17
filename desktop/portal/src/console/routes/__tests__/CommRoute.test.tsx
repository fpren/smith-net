import { render, screen, fireEvent } from '@testing-library/react';
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
    organizationId: 'org-1',
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

  it('shows the "select a channel" prompt when none is selected', () => {
    useCommStore.getState().setChannels([makeChannel('ch-x', 'general')]);
    render(<MemoryRouter><CommRoute /></MemoryRouter>);
    expect(screen.getByText(/dial an id to start one/i)).toBeInTheDocument();
  });

  it('renders channel rows from seeded state', () => {
    useCommStore.getState().setChannels([makeChannel('ch-x', 'general')]);
    render(<MemoryRouter><CommRoute /></MemoryRouter>);
    expect(screen.getByText('general')).toBeInTheDocument();
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
    // The last message renders both in the thread bubble and as the activity-feed
    // preview, so it legitimately appears more than once.
    expect(screen.getAllByText('How are you').length).toBeGreaterThan(0);
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

  it('renders the typing footer when typingByChannel has another user', async () => {
    server.use(http.get('/api/channels/:id/messages', () => HttpResponse.json([])));
    useCommStore.getState().setChannels([makeChannel('ch-t', 'typers')]);
    useCommStore.getState().selectChannel('ch-t');
    useCommStore.getState().setTyping('ch-t', 'u-other', 'Alice', true);
    render(<MemoryRouter><CommRoute /></MemoryRouter>);
    expect(await screen.findByText(/alice is typing/i)).toBeInTheDocument();
  });

  it('renders "· seen by N" suffix on my own messages when others have read them', async () => {
    server.use(
      http.get('/api/channels/:id/messages', () =>
        HttpResponse.json([makeMessage('m-mine', 'ch-r', 'my message')]),
      ),
    );
    useCommStore.getState().setChannels([makeChannel('ch-r', 'readers')]);
    useCommStore.getState().selectChannel('ch-r');
    useCommStore.getState().setMessages('ch-r', [makeMessage('m-mine', 'ch-r', 'my message')]);
    useCommStore.getState().markRead('m-mine', 'u-other-1');
    useCommStore.getState().markRead('m-mine', 'u-other-2');
    render(<MemoryRouter><CommRoute /></MemoryRouter>);
    expect(await screen.findByText(/seen 2/i)).toBeInTheDocument();
  });

  it('does NOT render the mobile [← back] row when no channel is selected', () => {
    useCommStore.getState().setChannels([makeChannel('ch-x', 'general')]);
    render(<MemoryRouter><CommRoute /></MemoryRouter>);
    expect(screen.queryByLabelText(/back to channels/i)).not.toBeInTheDocument();
  });

  it('renders the [← back] row when a channel is selected, and clicking it clears selection', async () => {
    server.use(http.get('/api/channels/:id/messages', () => HttpResponse.json([])));
    useCommStore.getState().setChannels([makeChannel('ch-sel', 'general')]);
    useCommStore.getState().selectChannel('ch-sel');
    render(<MemoryRouter><CommRoute /></MemoryRouter>);
    const back = await screen.findByLabelText(/back to channels/i);
    fireEvent.click(back);
    expect(useCommStore.getState().selectedChannelId).toBeNull();
  });

  it('renders the [x] delete button on own messages without requiring hover state', async () => {
    server.use(http.get('/api/channels/:id/messages', () => HttpResponse.json([makeMessage('m1', 'ch-mine', 'hi')])));
    useCommStore.getState().setChannels([makeChannel('ch-mine', 'mine')]);
    useCommStore.getState().selectChannel('ch-mine');
    useCommStore.getState().setMessages('ch-mine', [makeMessage('m1', 'ch-mine', 'hi')]);
    render(<MemoryRouter><CommRoute /></MemoryRouter>);
    const del = await screen.findByLabelText(/delete message/i);
    // The button is in the DOM regardless of hover — touch users can reach it.
    // CSS dimming via opacity-40 is verified visually in M3; here we only
    // assert presence + a non-hover-gated class.
    expect(del).toBeInTheDocument();
    expect(del.className).not.toMatch(/opacity-0/);
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
