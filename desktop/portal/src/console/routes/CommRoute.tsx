// desktop/portal/src/console/routes/CommRoute.tsx
import { useEffect } from 'react';
import { useCommWebSocket } from '../hooks/useCommWebSocket';
import { useCommStore } from '../stores/commStore';
import { commClient } from '../api/commClient';
import { ChannelList } from '../components/comm/ChannelList';
import { MessageList } from '../components/comm/MessageList';
import { MessageInput } from '../components/comm/MessageInput';

export function CommRoute() {
  useCommWebSocket();
  const channels = useCommStore((s) => s.channels);
  const selectedId = useCommStore((s) => s.selectedChannelId);
  const select = useCommStore((s) => s.selectChannel);
  const isStale = useCommStore((s) => s.isStaleChannels);

  // The WS pushes future events only — fetch history once when the user
  // picks a channel. setMessages dedupes via the next appendMessage flow.
  useEffect(() => {
    if (!selectedId) return;
    let alive = true;
    commClient.listMessages(selectedId).then((r) => {
      if (alive && r.ok) {
        useCommStore.getState().setMessages(selectedId, r.messages);
      }
    });
    return () => {
      alive = false;
    };
  }, [selectedId]);

  return (
    <div className="font-mono h-full flex">
      <aside className="w-72 border-r border-console-border overflow-y-auto flex-shrink-0">
        <div className="px-3 py-2 text-console-text-muted text-xs uppercase tracking-wide">
          Channels
        </div>
        {isStale && (
          <div className="bg-console-surface border border-console-warn text-console-warn px-3 py-1 text-xs">
            [OFFLINE] Couldn't refresh
          </div>
        )}
        <ChannelList channels={channels} selectedId={selectedId} onSelect={select} />
      </aside>
      <main className="flex-1 flex flex-col min-w-0">
        {selectedId ? (
          <>
            <MessageList channelId={selectedId} />
            <MessageInput channelId={selectedId} />
          </>
        ) : (
          <div className="flex-1 flex items-center justify-center text-console-text-muted text-sm">
            Select a channel to start.
          </div>
        )}
      </main>
    </div>
  );
}
