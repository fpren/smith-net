// desktop/portal/src/console/routes/CommRoute.tsx
//
// Responsive shell: stacks vertically on mobile and switches to a two-pane
// side-by-side at md:+ (768px). On mobile, exactly one pane is visible at a
// time — the channel list when nothing is selected, the message pane (with
// a [← back] row) once a channel is selected. Mirrors the Android
// ConversationScreen single-pane navigation pattern.

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

  const selectedChannel = selectedId ? channels.find((c) => c.id === selectedId) : null;

  return (
    <div className="font-mono h-full flex flex-col md:flex-row">
      <aside
        className={
          'w-full md:w-72 md:flex-shrink-0 border-b md:border-b-0 md:border-r border-console-border md:overflow-y-auto ' +
          (selectedId ? 'hidden md:block' : 'block')
        }
      >
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
      <main className={`flex-1 flex-col min-w-0 ${selectedId ? 'flex' : 'hidden md:flex'}`}>
        {selectedId ? (
          <>
            <div className="md:hidden border-b border-console-border bg-console-surface px-3 py-2 flex items-center gap-3">
              <button
                type="button"
                onClick={() => select(null)}
                className="text-console-accent text-sm font-mono"
                aria-label="Back to channels"
              >
                [← back]
              </button>
              <span className="text-console-text-muted text-xs uppercase tracking-wide truncate">
                {selectedChannel?.name ?? 'channel'}
              </span>
            </div>
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
