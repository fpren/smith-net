// desktop/portal/src/console/components/comm/IncomingRequestsFront.tsx
// Incoming front (UI-only). Stranger cross-org DMs auto-open (no backend gate),
// so this surfaces DM channels whose peer is NOT in your directory (out of
// network). "requests" = those with unread; "history" = the rest.

import { useMemo, useState } from 'react';
import type { Channel } from '../../../types';
import { useCommStore } from '../../stores/commStore';
import { useAuthStore } from '../../auth/authStore';
import { useDirectory } from '../../hooks/useDirectory';
import { ActivityRow } from './ActivityRow';

interface Props {
  channels: Channel[];
  selectedId: string | null;
  onSelect: (id: string) => void;
}

/** DM channels whose peer is not a known teammate. */
export function strangerDms(channels: Channel[], knownIds: Set<string>, selfId?: string): Channel[] {
  return channels.filter((c) => {
    if (c.type !== 'dm') return false;
    const peerId = c.memberIds.find((m) => m !== selfId);
    return peerId ? !knownIds.has(peerId) : false;
  });
}

export function IncomingRequestsFront({ channels, selectedId, onSelect }: Props) {
  const [tab, setTab] = useState<'requests' | 'history'>('requests');
  const self = useAuthStore((s) => s.user);
  const { byId } = useDirectory();
  const unreadByChannel = useCommStore((s) => s.unreadByChannel);
  const lastMessageByChannel = useCommStore((s) => s.lastMessageByChannel);
  const presenceByUser = useCommStore((s) => s.presenceByUser);

  const knownIds = useMemo(() => new Set(Object.keys(byId)), [byId]);
  const strangers = useMemo(() => strangerDms(channels, knownIds, self?.id), [channels, knownIds, self?.id]);
  const requests = strangers.filter((c) => (unreadByChannel[c.id] ?? 0) > 0);
  const history = strangers.filter((c) => (unreadByChannel[c.id] ?? 0) === 0);
  const shown = tab === 'requests' ? requests : history;

  return (
    <div className="comm-surface flex flex-col overflow-y-auto">
      <div className="flex gap-1 px-2 py-2">
        {(['requests', 'history'] as const).map((t) => (
          <button
            key={t}
            type="button"
            onClick={() => setTab(t)}
            className={
              'rounded-full font-commmono text-[10px] px-3 py-1 transition-colors ' +
              (tab === t ? 'bg-sn-accent text-sn-ink-on-accent' : 'border border-sn-line text-sn-ink-muted hover:text-sn-accent')
            }
          >
            {t}{t === 'requests' && requests.length > 0 ? ` ${requests.length}` : ''}
          </button>
        ))}
      </div>
      {shown.length === 0 ? (
        <div className="px-3 py-6 text-center text-sn-ink-muted text-sm">
          {tab === 'requests' ? 'No new requests.' : 'No past requests.'}
        </div>
      ) : (
        shown.map((c) => {
          const peerId = c.memberIds.find((m) => m !== self?.id);
          return (
            <div key={c.id} className={tab === 'requests' ? 'comm-ring-in' : undefined}>
              <ActivityRow
                channel={c}
                selfId={self?.id}
                selfName={self?.displayName}
                selected={selectedId === c.id}
                unread={unreadByChannel[c.id] ?? 0}
                last={lastMessageByChannel[c.id]}
                peerPresence={peerId ? presenceByUser[peerId] : undefined}
                onSelect={() => onSelect(c.id)}
              />
            </div>
          );
        })
      )}
    </div>
  );
}
