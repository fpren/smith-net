// desktop/portal/src/console/components/comm/MessageList.tsx
import { useEffect, useRef } from 'react';
import { useCommStore } from '../../stores/commStore';
import { useAuthStore } from '../../auth/authStore';

interface Props {
  channelId: string;
}

function formatTime(ts: number): string {
  const d = new Date(ts);
  const hh = String(d.getHours()).padStart(2, '0');
  const mm = String(d.getMinutes()).padStart(2, '0');
  return `${hh}:${mm}`;
}

export function MessageList({ channelId }: Props) {
  const messages = useCommStore((s) => s.messagesByChannel[channelId] ?? []);
  const isStale = useCommStore((s) => s.isStaleMessages);
  const selfId = useAuthStore((s) => s.user?.id);
  const scrollRef = useRef<HTMLDivElement>(null);

  // Auto-scroll to bottom on new messages — but only if the user is already
  // near the bottom (within 120px). Avoids stealing scroll while they read
  // history.
  useEffect(() => {
    const el = scrollRef.current;
    if (!el) return;
    const nearBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 120;
    if (nearBottom) {
      el.scrollTop = el.scrollHeight;
    }
  }, [messages.length]);

  return (
    <div className="flex-1 min-h-0 flex flex-col font-mono">
      {isStale && (
        <div className="bg-console-surface border-b border-console-warn text-console-warn px-3 py-1 text-xs">
          [OFFLINE] Couldn't refresh messages
        </div>
      )}
      <div ref={scrollRef} className="flex-1 overflow-y-auto px-4 py-3">
        {messages.length === 0 ? (
          <div className="text-console-text-muted text-sm">No messages yet.</div>
        ) : (
          <ul className="space-y-2">
            {messages.map((m) => {
              const mine = selfId === m.senderId;
              return (
                <li key={m.id} className="text-sm">
                  <div className="flex items-baseline gap-2">
                    <span
                      className={
                        'text-xs font-semibold ' +
                        (mine ? 'text-console-accent' : 'text-console-text')
                      }
                    >
                      {m.senderName || m.senderId}
                    </span>
                    <span className="text-xs text-console-text-muted tabular-nums">
                      {formatTime(m.timestamp)}
                    </span>
                  </div>
                  <div className="text-console-text whitespace-pre-wrap break-words">
                    {m.content}
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </div>
  );
}
