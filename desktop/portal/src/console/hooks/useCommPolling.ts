// desktop/portal/src/console/hooks/useCommPolling.ts
//
// Polls the user's channels every 30s and the selected channel's messages
// every 3s. Visibility-aware (pauses when the tab is hidden) and follows the
// same pattern as useJobsPolling (desktop/portal/src/console/hooks/useJobsPolling.ts).
import { useEffect, useRef } from 'react';
import { commClient } from '../api/commClient';
import { useCommStore } from '../stores/commStore';

const CHANNELS_INTERVAL_MS = 30_000;
const MESSAGES_INTERVAL_MS = 3_000;

export function useCommPolling(): void {
  const selectedId = useCommStore((s) => s.selectedChannelId);
  const channelsTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const messagesTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    const fetchChannels = async () => {
      useCommStore.getState().markLoadingChannels(true);
      const result = await commClient.listChannels();
      useCommStore.getState().markLoadingChannels(false);
      if (result.ok) {
        useCommStore.getState().setChannels(result.channels);
      } else {
        useCommStore.getState().markStaleChannels(true);
      }
    };

    const fetchMessages = async (channelId: string) => {
      useCommStore.getState().markLoadingMessages(true);
      const result = await commClient.listMessages(channelId);
      useCommStore.getState().markLoadingMessages(false);
      if (result.ok) {
        useCommStore.getState().setMessages(channelId, result.messages);
      } else {
        useCommStore.getState().markStaleMessages(true);
      }
    };

    const startChannels = () => {
      if (channelsTimerRef.current !== null) return;
      channelsTimerRef.current = setInterval(fetchChannels, CHANNELS_INTERVAL_MS);
    };
    const stopChannels = () => {
      if (channelsTimerRef.current !== null) {
        clearInterval(channelsTimerRef.current);
        channelsTimerRef.current = null;
      }
    };
    const startMessages = () => {
      if (messagesTimerRef.current !== null || !selectedId) return;
      messagesTimerRef.current = setInterval(() => fetchMessages(selectedId), MESSAGES_INTERVAL_MS);
    };
    const stopMessages = () => {
      if (messagesTimerRef.current !== null) {
        clearInterval(messagesTimerRef.current);
        messagesTimerRef.current = null;
      }
    };

    const onVisibility = () => {
      if (document.visibilityState === 'visible') {
        fetchChannels();
        if (selectedId) fetchMessages(selectedId);
        startChannels();
        startMessages();
      } else {
        stopChannels();
        stopMessages();
      }
    };

    // Initial fetch + start intervals.
    fetchChannels();
    if (selectedId) fetchMessages(selectedId);
    startChannels();
    startMessages();
    document.addEventListener('visibilitychange', onVisibility);

    return () => {
      stopChannels();
      stopMessages();
      document.removeEventListener('visibilitychange', onVisibility);
    };
  }, [selectedId]);
}
