// desktop/portal/src/console/hooks/useCommWebSocket.ts
//
// Mirrors Android ChatManager: WebSocket is the only ingest path for
// incoming events (messages, channel_*, typing_*, message_read,
// message_deleted). REST is used only for the initial reconcile fetch
// after auth_ok lands. wsClient is a singleton shared with the legacy
// Portal — this hook only subscribes and unsubscribes, it never disconnects.
import { useEffect } from 'react';
import { wsClient } from '../../websocket';
import { commClient } from '../api/commClient';
import { useCommStore } from '../stores/commStore';
import { useAuthStore } from '../auth/authStore';

const TYPING_SWEEP_MS = 1_000;

export function useCommWebSocket(): void {
  const user = useAuthStore((s) => s.user);

  useEffect(() => {
    if (!user) return;
    let alive = true;
    const subs: Array<() => void> = [];

    // Connect (idempotent). When auth_ok lands, reconcile state via REST so
    // anything missed during a disconnect is picked up — the WS only pushes
    // future events.
    wsClient
      .connect(user.id, user.displayName)
      .then(async () => {
        if (!alive) return;
        const list = await commClient.listChannels();
        if (!alive) return;
        if (list.ok) {
          useCommStore.getState().setChannels(list.channels);
        } else {
          useCommStore.getState().markStaleChannels(true);
        }
        const sel = useCommStore.getState().selectedChannelId;
        if (sel) {
          const msgs = await commClient.listMessages(sel);
          if (!alive) return;
          if (msgs.ok) {
            useCommStore.getState().setMessages(sel, msgs.messages);
          } else {
            useCommStore.getState().markStaleMessages(true);
          }
        }
      })
      .catch((err) => {
        // WS auth failed — surface as channels-stale so the [OFFLINE] banner
        // is visible. Mirrors how the old polling path flagged stale on REST
        // failure.
        useCommStore.getState().markStaleChannels(true);
        console.warn('[useCommWebSocket] connect failed:', err);
      });

    subs.push(
      wsClient.onMessage((m) => useCommStore.getState().appendMessage(m)),
    );
    subs.push(
      wsClient.onChannelCreated((c) => useCommStore.getState().addChannel(c)),
    );
    subs.push(
      wsClient.onChannelDeleted((id) => useCommStore.getState().removeChannel(id)),
    );
    subs.push(
      wsClient.onChannelCleared((id) => useCommStore.getState().clearChannelMessages(id)),
    );
    subs.push(
      wsClient.onTyping((evt, kind) =>
        useCommStore.getState().setTyping(evt.channelId, evt.userId, evt.userName, kind === 'start'),
      ),
    );
    subs.push(
      wsClient.onMessageRead((evt) =>
        useCommStore.getState().markRead(evt.messageId, evt.readBy),
      ),
    );
    subs.push(
      // Presence was parsed by wsClient but never consumed — wire it into the
      // store so the activity feed + screen-pop header can show online dots.
      wsClient.onPresence((list) => useCommStore.getState().setPresence(list)),
    );
    subs.push(
      wsClient.onMessageDeleted((messageId) => {
        const state = useCommStore.getState();
        for (const channelId of Object.keys(state.messagesByChannel)) {
          if (state.messagesByChannel[channelId].some((m) => m.id === messageId)) {
            state.removeMessage(channelId, messageId);
            break;
          }
        }
      }),
    );

    const sweepTimer = setInterval(
      () => useCommStore.getState().sweepTyping(),
      TYPING_SWEEP_MS,
    );

    return () => {
      alive = false;
      subs.forEach((u) => u());
      clearInterval(sweepTimer);
    };
  }, [user?.id, user?.displayName]);
}
