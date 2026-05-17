// desktop/portal/src/console/components/comm/MessageList.tsx
import { useEffect, useRef } from 'react';
import { useCommStore } from '../../stores/commStore';
import { useAuthStore } from '../../auth/authStore';
import { commClient } from '../../api/commClient';
import { useToastStore } from '../../stores/toastStore';
import { wsClient } from '../../../websocket';
import type { Message } from '../../../types';

interface Props {
  channelId: string;
}

// Stable fallback references so zustand selectors don't re-render in a loop
// when the channel hasn't been seen yet. `s.messagesByChannel[id] ?? []` would
// return a fresh `[]` per render, which zustand compares with Object.is and
// always considers "changed."
const EMPTY_MESSAGES: Message[] = [];
const EMPTY_TYPING: Record<string, { name: string; expiresAt: number }> = {};

function formatTime(ts: number): string {
  const d = new Date(ts);
  const hh = String(d.getHours()).padStart(2, '0');
  const mm = String(d.getMinutes()).padStart(2, '0');
  return `${hh}:${mm}`;
}

function typingLabel(names: string[]): string {
  if (names.length === 1) return `${names[0]} is typing…`;
  if (names.length === 2) return `${names[0]} and ${names[1]} are typing…`;
  return `${names.length} people are typing…`;
}

export function MessageList({ channelId }: Props) {
  const messages = useCommStore((s) => s.messagesByChannel[channelId] ?? EMPTY_MESSAGES);
  const removeMessage = useCommStore((s) => s.removeMessage);
  const isStale = useCommStore((s) => s.isStaleMessages);
  const typingMap = useCommStore((s) => s.typingByChannel[channelId] ?? EMPTY_TYPING);
  const readByMessage = useCommStore((s) => s.readByMessage);
  const selfId = useAuthStore((s) => s.user?.id);
  const pushToast = useToastStore((s) => s.push);
  const scrollRef = useRef<HTMLDivElement>(null);

  // Track which message ids we've already sent a read receipt for in this
  // session — keeps the WS quiet on re-renders.
  const sentReceipts = useRef<Set<string>>(new Set());

  // Auto-scroll on new messages, but only if the user is already near the
  // bottom (within 120px) — never steal scroll while they're reading history.
  useEffect(() => {
    const el = scrollRef.current;
    if (!el) return;
    const nearBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 120;
    if (nearBottom) {
      el.scrollTop = el.scrollHeight;
    }
  }, [messages.length]);

  // Send read receipts for messages that aren't mine, once per id per session.
  useEffect(() => {
    if (!selfId) return;
    for (const m of messages) {
      if (m.senderId !== selfId && !sentReceipts.current.has(m.id)) {
        sentReceipts.current.add(m.id);
        wsClient.sendReadReceipt(m.id, channelId);
      }
    }
  }, [messages, channelId, selfId]);

  // Channel switched — reset our sent-receipts set so when we come back the
  // tab re-emits read receipts for messages we've already loaded (small cost,
  // matches "I just re-read the channel").
  useEffect(() => {
    sentReceipts.current = new Set();
  }, [channelId]);

  async function doDelete(messageId: string) {
    const result = await commClient.deleteMessage(messageId);
    if (result.ok) {
      // Optimistically drop from store; the server's broadcast will be a no-op
      // for us since the message is already gone.
      removeMessage(channelId, messageId);
    } else {
      pushToast({
        message: result.error || 'Delete failed',
        tone: 'error',
        duration: 3000,
      });
    }
  }

  const typingNames = Object.entries(typingMap)
    .filter(([userId]) => userId !== selfId)
    .map(([, entry]) => entry.name || 'Someone');

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
              const readers = readByMessage[m.id];
              const seenCount = readers
                ? Array.from(readers).filter((id) => id !== selfId).length
                : 0;
              return (
                <li key={m.id} className="text-sm group">
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
                    {mine && (
                      <button
                        type="button"
                        onClick={() => doDelete(m.id)}
                        className="ml-auto text-xs text-console-text-muted opacity-0 group-hover:opacity-100 hover:text-console-danger transition-opacity"
                        aria-label="Delete message"
                      >
                        [x]
                      </button>
                    )}
                  </div>
                  <div className="text-console-text whitespace-pre-wrap break-words">
                    {m.content}
                  </div>
                  {mine && seenCount > 0 && (
                    <div className="text-[10px] text-console-text-muted mt-0.5">
                      · seen by {seenCount}
                    </div>
                  )}
                </li>
              );
            })}
          </ul>
        )}
      </div>
      {typingNames.length > 0 && (
        <div className="border-t border-console-border bg-console-surface px-4 py-1 text-xs text-console-text-muted">
          {typingLabel(typingNames)}
        </div>
      )}
    </div>
  );
}
