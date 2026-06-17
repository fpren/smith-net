// desktop/portal/src/console/components/comm/ActivityFeed.tsx
// Left zone: DMs + channels in one activity list, sorted by last activity,
// split into DIRECT and CHANNELS sections.

import type { Channel } from '../../../types';
import { useCommStore } from '../../stores/commStore';
import { useAuthStore } from '../../auth/authStore';
import { ActivityRow } from './ActivityRow';

interface Props {
  channels: Channel[];
  selectedId: string | null;
  onSelect: (id: string) => void;
}

export function ActivityFeed({ channels, selectedId, onSelect }: Props) {
  const self = useAuthStore((s) => s.user);
  const unreadByChannel = useCommStore((s) => s.unreadByChannel);
  const lastMessageByChannel = useCommStore((s) => s.lastMessageByChannel);
  const presenceByUser = useCommStore((s) => s.presenceByUser);

  const byActivity = (a: Channel, b: Channel) =>
    (lastMessageByChannel[b.id]?.timestamp ?? 0) - (lastMessageByChannel[a.id]?.timestamp ?? 0);

  const dms = channels.filter((c) => c.type === 'dm').sort(byActivity);
  const rooms = channels.filter((c) => c.type !== 'dm').sort(byActivity);

  function rowFor(c: Channel) {
    const peerId = c.type === 'dm' ? c.memberIds.find((m) => m !== self?.id) : undefined;
    return (
      <ActivityRow
        key={c.id}
        channel={c}
        selfId={self?.id}
        selfName={self?.displayName}
        selected={selectedId === c.id}
        unread={unreadByChannel[c.id] ?? 0}
        last={lastMessageByChannel[c.id]}
        peerPresence={peerId ? presenceByUser[peerId] : undefined}
        onSelect={() => onSelect(c.id)}
      />
    );
  }

  return (
    <div className="comm-surface flex flex-col py-1 overflow-y-auto">
      {dms.length > 0 && (
        <>
          <div className="px-3 pt-2 pb-1 text-[10px] tracking-[0.15em] text-console-text-dim font-commmono">DIRECT</div>
          {dms.map(rowFor)}
        </>
      )}
      {rooms.length > 0 && (
        <>
          <div className="px-3 pt-3 pb-1 text-[10px] tracking-[0.15em] text-console-text-dim font-commmono">CHANNELS</div>
          {rooms.map(rowFor)}
        </>
      )}
      {channels.length === 0 && (
        <div className="px-3 py-6 text-center text-console-text-muted text-sm">
          No conversations yet. Dial an id to start one.
        </div>
      )}
    </div>
  );
}
