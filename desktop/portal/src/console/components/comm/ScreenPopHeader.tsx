// desktop/portal/src/console/components/comm/ScreenPopHeader.tsx
// Center-top "screen-pop" context card for the open conversation: avatar,
// name, public id + role (when known), and presence. Slides in on channel
// change (Aircall-style). Enriches same-org peers from the directory; cross-org
// peers fall back to channel-derived name + initials.

import type { Channel } from '../../../types';
import { useCommStore } from '../../stores/commStore';
import { useAuthStore } from '../../auth/authStore';
import { useDirectory } from '../../hooks/useDirectory';
import { Avatar } from '../ui/Avatar';
import { accentForId } from '../../lib/utils';
import { formatPublicId, presenceColor } from './commHelpers';

interface Props { channel: Channel; }

function peerNameFromChannel(channel: Channel, selfName?: string): string {
  const parts = channel.name.split('<>').map((s) => s.trim());
  if (parts.length === 2) return parts.find((p) => p && p !== selfName) ?? (parts[1] || channel.name);
  return channel.name;
}

export function ScreenPopHeader({ channel }: Props) {
  const self = useAuthStore((s) => s.user);
  const { byId } = useDirectory();
  const presenceByUser = useCommStore((s) => s.presenceByUser);

  const isDm = channel.type === 'dm';
  const peerId = isDm ? channel.memberIds.find((m) => m !== self?.id) : undefined;
  const peer = peerId ? byId[peerId] : undefined;
  const presence = peerId ? presenceByUser[peerId] : undefined;

  const name = isDm ? (peer?.displayName ?? peerNameFromChannel(channel, self?.displayName)) : channel.name;
  const online = presence?.status === 'online';
  const ring = presenceColor(presence);

  const sub = isDm
    ? [peer?.publicId ? `ID ${formatPublicId(peer.publicId)}` : null, peer?.role]
        .filter(Boolean)
        .join(' · ') || 'direct message'
    : `${channel.type} · ${channel.memberIds.length} members`;

  return (
    <div
      key={channel.id}
      className="comm-surface flex items-center gap-3 px-4 py-3 border-b border-sn-line bg-sn-bg-panel"
      style={{ animation: 'commPop .28s cubic-bezier(.2,.8,.2,1)' }}
    >
      {isDm ? (
        <Avatar name={name} color={accentForId(peerId ?? channel.id)} size={36} photoUrl={peer?.avatarUrl} statusColor={ring} />
      ) : (
        <span className="w-9 h-9 rounded-lg grid place-items-center bg-sn-line text-sn-ink-muted font-commmono">#</span>
      )}
      <div className="min-w-0">
        <div className="font-commsans font-semibold text-sn-ink truncate">
          {name}
          {isDm && (
            <span className="ml-2 font-commmono text-[10px]" style={{ color: online ? 'var(--sn-status-online)' : 'var(--sn-ink-muted)' }}>
              {online ? '[*] online' : 'offline'}
            </span>
          )}
        </div>
        <div className="font-commmono text-[11px] text-sn-ink-muted truncate">{sub}</div>
      </div>
    </div>
  );
}
