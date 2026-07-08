// desktop/portal/src/console/components/comm/MessageRow.tsx
//
// One row of a channel's message list. ALWAYS left-aligned — own messages are
// distinguished only by sender-name color (sn-accent), never by side or
// bubble color. See design/tokens + Design System v2 Plan 3.
import type { Message, MediaAttachment } from '../../../types';
import { Avatar } from '../ui/Avatar';
import { accentForId } from '../../lib/utils';
import { useToastStore } from '../../stores/toastStore';

interface Props {
  message: Message;
  firstOfGroup: boolean;
  mine: boolean;
  seenByOthers: number;
  onDelete: (messageId: string) => void;
  onRetry: (message: Message) => void;
}

const AVATAR_SIZE = 24;
// 24px avatar + the header row's gap-2 (8px) — grouped rows indent by the
// same amount so their bubble lines up under the first-of-group bubble.
const AVATAR_COLUMN_CLASS = 'pl-8';

function formatTime(ts: number): string {
  const d = new Date(ts);
  const hh = String(d.getHours()).padStart(2, '0');
  const mm = String(d.getMinutes()).padStart(2, '0');
  return `${hh}:${mm}`;
}

type StatusWord = 'PENDING' | 'SENT' | 'FAILED' | 'SEEN';

function statusWord(status: Message['status'], seenByOthers: number): StatusWord {
  if (status === 'failed') return 'FAILED';
  if (status === 'pending') return 'PENDING';
  if (seenByOthers > 0) return 'SEEN';
  return 'SENT';
}

function MediaBlock({ media }: { media: MediaAttachment }) {
  if (media.type === 'image') {
    return <img src={media.url} className="max-h-64 rounded-[10px]" alt={media.filename ?? 'photo'} />;
  }
  if (media.type === 'voice') {
    return (
      <a href={media.url} target="_blank" rel="noreferrer noopener" className="font-data text-xs underline">
        [▶] {media.duration ?? 0}s
      </a>
    );
  }
  // file or video
  return (
    <a href={media.url} target="_blank" rel="noreferrer noopener" className="font-data text-xs underline">
      [≡] {media.filename ?? 'file'}
    </a>
  );
}

function Bubble({ message }: { message: Message }) {
  return (
    <div className="rounded-[14px] bg-sn-bg-sunken text-sn-ink px-3 py-2 text-sm max-w-[75ch] whitespace-pre-wrap break-words">
      {message.content}
      {message.media && (
        <div className="mt-1.5">
          <MediaBlock media={message.media} />
        </div>
      )}
    </div>
  );
}

function StatusFooter({
  message,
  seenByOthers,
  onRetry,
}: {
  message: Message;
  seenByOthers: number;
  onRetry: (message: Message) => void;
}) {
  const word = statusWord(message.status, seenByOthers);
  return (
    <div className="flex justify-end mt-0.5">
      {word === 'FAILED' ? (
        <button
          type="button"
          onClick={() => onRetry(message)}
          className="font-data text-[10px] uppercase text-sn-attention"
        >
          FAILED · RETRY
        </button>
      ) : (
        <span className="font-data text-[10px] uppercase text-sn-ink-muted">{word}</span>
      )}
    </div>
  );
}

export function MessageRow({ message, firstOfGroup, mine, seenByOthers, onDelete, onRetry }: Props) {
  const isMesh = message.origin !== 'online';
  const canRetry = message.status === 'failed';

  const copyContent = async () => {
    try {
      if (navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(message.content);
      } else {
        // Legacy fallback for non-secure contexts
        const ta = document.createElement('textarea');
        ta.value = message.content;
        document.body.appendChild(ta);
        ta.select();
        document.execCommand('copy');
        ta.remove();
      }
      useToastStore.getState().push({ message: 'Copied', tone: 'info', duration: 2000 });
    } catch {
      useToastStore.getState().push({ message: 'Copy failed', tone: 'error', duration: 3000 });
    }
  };

  return (
    <li className="group relative flex flex-col items-start px-2 py-0.5">
      <div className="absolute right-2 top-0 flex items-center gap-2 opacity-0 group-hover:opacity-100 focus-within:opacity-100 transition-opacity">
        <button
          type="button"
          onClick={() => void copyContent()}
          className="font-data text-[10px] text-sn-ink-muted hover:text-sn-ink"
        >
          copy
        </button>
        {mine && (
          <button
            type="button"
            aria-label="Delete message"
            onClick={() => onDelete(message.id)}
            className="font-data text-[10px] text-sn-ink-muted hover:text-sn-attention"
          >
            [x]
          </button>
        )}
        {canRetry && (
          <button
            type="button"
            onClick={() => onRetry(message)}
            className="font-data text-[10px] text-sn-ink-muted hover:text-sn-ink"
          >
            retry
          </button>
        )}
      </div>

      {firstOfGroup ? (
        <div className="flex items-start gap-2 w-full">
          <Avatar name={message.senderName} color={accentForId(message.senderId)} size={AVATAR_SIZE} />
          <div className="flex flex-col min-w-0 flex-1">
            <div className="flex items-baseline gap-1.5">
              <span className={`font-semibold text-xs ${mine ? 'text-sn-accent' : 'text-sn-ink'}`}>
                {message.senderName}
              </span>
              <span className="font-data text-[10px] text-sn-ink-muted">{formatTime(message.timestamp)}</span>
              {isMesh && (
                <span className="font-data text-[9px] uppercase border border-sn-accent text-sn-accent rounded-full px-1.5">
                  mesh
                </span>
              )}
            </div>
            <Bubble message={message} />
            {mine && <StatusFooter message={message} seenByOthers={seenByOthers} onRetry={onRetry} />}
          </div>
        </div>
      ) : (
        <div className={`w-full ${AVATAR_COLUMN_CLASS}`}>
          <Bubble message={message} />
          {mine && <StatusFooter message={message} seenByOthers={seenByOthers} onRetry={onRetry} />}
        </div>
      )}
    </li>
  );
}
