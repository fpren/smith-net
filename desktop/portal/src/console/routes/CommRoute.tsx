// desktop/portal/src/console/routes/CommRoute.tsx
//
// Aircall-fused "softphone" comm. Three zones at lg:+ —
//   left   : front tabs (activity / incoming / people)
//   center : screen-pop header + conversation
//   right  : dial-an-id rail + your own id card
// Collapses to a single pane on mobile (one zone visible at a time), with an
// inline dial field at the top of the left column since the rail is lg-only.

import { useEffect, useMemo, useState } from 'react';
import { useCommWebSocket } from '../hooks/useCommWebSocket';
import { useCommStore } from '../stores/commStore';
import { useAuthStore } from '../auth/authStore';
import { useDirectory } from '../hooks/useDirectory';
import { commClient } from '../api/commClient';
import { ActivityFeed } from '../components/comm/ActivityFeed';
import { FrontTabs, type CommFront } from '../components/comm/FrontTabs';
import { IncomingRequestsFront, strangerDms } from '../components/comm/IncomingRequestsFront';
import { PeopleDirectoryFront } from '../components/comm/PeopleDirectoryFront';
import { ScreenPopHeader } from '../components/comm/ScreenPopHeader';
import { DialRail } from '../components/comm/DialRail';
import { DialField } from '../components/comm/DialField';
import { MessageList } from '../components/comm/MessageList';
import { MessageInput } from '../components/comm/MessageInput';

export function CommRoute() {
  useCommWebSocket();
  const channels = useCommStore((s) => s.channels);
  const selectedId = useCommStore((s) => s.selectedChannelId);
  const select = useCommStore((s) => s.selectChannel);
  const isStale = useCommStore((s) => s.isStaleChannels);
  const unreadByChannel = useCommStore((s) => s.unreadByChannel);
  const self = useAuthStore((s) => s.user);
  const { byId } = useDirectory();

  const [front, setFront] = useState<CommFront>('activity');

  // Badge: out-of-network DMs with unread messages.
  const incomingCount = useMemo(() => {
    const known = new Set(Object.keys(byId));
    return strangerDms(channels, known, self?.id).filter((c) => (unreadByChannel[c.id] ?? 0) > 0).length;
  }, [channels, byId, self?.id, unreadByChannel]);

  useEffect(() => {
    if (!selectedId) return;
    let alive = true;
    commClient.listMessages(selectedId).then((r) => {
      if (alive && r.ok) useCommStore.getState().setMessages(selectedId, r.messages);
    });
    return () => { alive = false; };
  }, [selectedId]);

  const selectedChannel = selectedId ? channels.find((c) => c.id === selectedId) : null;

  return (
    <div className="comm-surface h-full flex flex-col lg:flex-row">
      {/* LEFT ZONE */}
      <aside
        className={
          'w-full lg:w-80 lg:flex-shrink-0 flex flex-col border-b lg:border-b-0 lg:border-r border-console-border min-h-0 ' +
          (selectedId ? 'hidden lg:flex' : 'flex')
        }
      >
        <div className="lg:hidden px-3 pt-3"><DialField /></div>
        <FrontTabs front={front} onChange={setFront} incomingCount={incomingCount} />
        {isStale && (
          <div className="bg-console-surface border-y border-console-warn text-console-warn px-3 py-1 text-xs font-commmono">
            [OFFLINE] Couldn't refresh
          </div>
        )}
        <div className="flex-1 min-h-0">
          {front === 'activity' && <ActivityFeed channels={channels} selectedId={selectedId} onSelect={select} />}
          {front === 'incoming' && <IncomingRequestsFront channels={channels} selectedId={selectedId} onSelect={select} />}
          {front === 'people' && <PeopleDirectoryFront channels={channels} />}
        </div>
      </aside>

      {/* CENTER ZONE */}
      <main className={`flex-1 flex-col min-w-0 ${selectedId ? 'flex' : 'hidden lg:flex'}`}>
        {selectedChannel ? (
          <>
            <div className="lg:hidden border-b border-console-border bg-console-surface px-3 py-2">
              <button type="button" onClick={() => select(null)} className="text-console-accent text-sm font-commmono" aria-label="Back to channels">
                [← back]
              </button>
            </div>
            <ScreenPopHeader channel={selectedChannel} />
            <MessageList channelId={selectedChannel.id} />
            <MessageInput channelId={selectedChannel.id} />
          </>
        ) : (
          <div className="flex-1 flex items-center justify-center text-console-text-muted text-sm font-commsans">
            Select a conversation, or dial an id to start one.
          </div>
        )}
      </main>

      {/* RIGHT ZONE */}
      <DialRail />
    </div>
  );
}
