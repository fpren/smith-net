// desktop/portal/src/console/components/comm/ActivityRow.tsx
import type { Channel, Message, Presence } from '../../../types';
import { Avatar } from '../ui/Avatar';
import { accentForId } from '../../lib/utils';
import { directionMarker, markerColor, presenceColor } from './commHelpers';

interface Props {
  channel: Channel;
  selfId?: string;
  selfName?: string;
  selected: boolean;
  unread: number;
  last?: Message;
  peerPresence?: Presence;
  onSelect: () => void;
}

/** Peer display name for a DM ("me <> them" -> "them"). */
function peerName(channel: Channel, selfName?: string): string {
  if (channel.type !== 'dm') return channel.name;
  const parts = channel.name.split('<>').map((s) => s.trim());
  if (parts.length === 2) return parts.find((p) => p && p !== selfName) ?? (parts[1] || channel.name);
  return channel.name;
}

function fmtTime(ts?: number): string {
  if (!ts) return '';
  const d = new Date(ts);
  const now = new Date();
  const sameDay = d.toDateString() === now.toDateString();
  if (sameDay) return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
  return `${d.getMonth() + 1}/${d.getDate()}`;
}

export function ActivityRow({ channel, selfId, selfName, selected, unread, last, peerPresence, onSelect }: Props) {
  const isDm = channel.type === 'dm';
  const title = peerName(channel, selfName);
  const peerId = isDm ? channel.memberIds.find((m) => m !== selfId) ?? channel.id : channel.id;
  const marker = directionMarker(last, selfId, unread);
  const ring = isDm ? presenceColor(peerPresence) : null;

  return (
    <button
      type="button"
      onClick={onSelect}
      className={
        'w-full flex items-center gap-2.5 px-2.5 py-2 rounded-xl mx-1 my-0.5 text-left transition-transform duration-150 hover:translate-x-0.5 ' +
        (selected ? 'bg-console-bg shadow-sm border-l-[3px] border-console-accent' : 'hover:bg-console-bg/60')
      }
    >
      {isDm ? (
        <Avatar name={title} color={accentForId(peerId)} size={30} statusColor={ring} />
      ) : (
        <span className="w-[30px] h-[30px] rounded-lg flex-shrink-0 grid place-items-center bg-console-border text-console-text-muted font-commmono text-sm">#</span>
      )}
      <span className="flex-1 min-w-0">
        <span className="flex items-baseline justify-between gap-2">
          <span
            className={
              'font-commsans text-sm text-console-text truncate ' +
              (unread > 0 ? 'font-bold' : 'font-medium')
            }
          >
            {title}
          </span>
          <span className="font-commmono text-[10px] text-console-text-dim flex-shrink-0">{fmtTime(last?.timestamp)}</span>
        </span>
        <span className="block text-[12px] text-console-text-muted truncate">
          {marker && <span className="font-commmono mr-1" style={{ color: markerColor(marker) }}>{marker}</span>}
          {last?.content ?? 'No messages'}
        </span>
      </span>
      {unread > 0 && (
        <span className="flex-shrink-0 bg-sn-attention text-sn-ink-on-accent rounded-full font-commmono text-[10px] px-1.5 min-w-[18px] text-center">
          {unread}
        </span>
      )}
    </button>
  );
}
