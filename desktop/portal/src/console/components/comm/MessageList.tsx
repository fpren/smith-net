// desktop/portal/src/console/components/comm/MessageList.tsx
import { Fragment, useEffect, useRef, useState } from 'react';
import { useCommStore } from '../../stores/commStore';
import { useAuthStore } from '../../auth/authStore';
import { commClient } from '../../api/commClient';
import { useToastStore } from '../../stores/toastStore';
import { wsClient } from '../../../websocket';
import { ConfirmDialog } from '../ui/SmithDialog';
import { groupMessages } from './messageGrouping';
import { MessageRow } from './MessageRow';
import { retryUpload } from './MessageInput';
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

function typingLabel(names: string[]): string {
  if (names.length === 1) return `${names[0]} is typing…`;
  if (names.length === 2) return `${names[0]} and ${names[1]} are typing…`;
  return `${names.length} people are typing…`;
}

export function MessageList({ channelId }: Props) {
  const messages = useCommStore((s) => s.messagesByChannel[channelId] ?? EMPTY_MESSAGES);
  const removeMessage = useCommStore((s) => s.removeMessage);
  const updateMessage = useCommStore((s) => s.updateMessage);
  const isStale = useCommStore((s) => s.isStaleMessages);
  const typingMap = useCommStore((s) => s.typingByChannel[channelId] ?? EMPTY_TYPING);
  const readByMessage = useCommStore((s) => s.readByMessage);
  const unreadAtSelect = useCommStore((s) => s.unreadAtSelect[channelId] ?? 0);
  const selfId = useAuthStore((s) => s.user?.id);
  const pushToast = useToastStore((s) => s.push);
  const scrollRef = useRef<HTMLDivElement>(null);
  const [confirmingId, setConfirmingId] = useState<string | null>(null);
  const [distanceFromBottom, setDistanceFromBottom] = useState(0);

  // Track which message ids we've already sent a read receipt for in this
  // session — keeps the WS quiet on re-renders.
  const sentReceipts = useRef<Set<string>>(new Set());

  // NEW-divider anchor: computed once, on the first render after messages
  // load for this channel, then frozen until channelId changes. Late
  // arrivals (appendMessage while the channel stays open) must not move it —
  // recomputing on every messages.length change would shove the divider
  // toward the bottom as new messages come in.
  // NOTE: the frozen value is an ABSOLUTE index, valid only while messages
  // load as a single full replace (appends afterward). If pagination ever
  // prepends older history to the front, this anchor must switch to a
  // message id or from-the-end offset.
  const dividerAnchorRef = useRef<{ channelId: string; index: number } | null>(null);
  if (dividerAnchorRef.current?.channelId !== channelId) {
    dividerAnchorRef.current =
      messages.length > 0
        ? {
            channelId,
            index: unreadAtSelect > 0 ? Math.max(0, messages.length - unreadAtSelect) : -1,
          }
        : null; // messages haven't loaded yet — try again next render
  }
  const dividerIndex = dividerAnchorRef.current?.index ?? -1;

  function measureDistanceFromBottom(el: HTMLDivElement): number {
    return el.scrollHeight - el.scrollTop - el.clientHeight;
  }

  // Auto-scroll on new messages, but only if the user is already near the
  // bottom (within 120px) — never steal scroll while they're reading history.
  // Also refreshes the jump-to-latest pill's distance state so it appears
  // immediately if new messages arrive while scrolled up.
  useEffect(() => {
    const el = scrollRef.current;
    if (!el) return;
    const distance = measureDistanceFromBottom(el);
    if (distance < 120) {
      el.scrollTop = el.scrollHeight;
      setDistanceFromBottom(0);
    } else {
      setDistanceFromBottom(distance);
    }
  }, [messages.length]);

  function handleScroll() {
    const el = scrollRef.current;
    if (!el) return;
    setDistanceFromBottom(measureDistanceFromBottom(el));
  }

  function jumpToLatest() {
    const el = scrollRef.current;
    if (!el) return;
    if (typeof el.scrollTo === 'function') {
      el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' });
    } else {
      // jsdom (tests) doesn't implement scrollTo.
      el.scrollTop = el.scrollHeight;
    }
  }

  const showJumpToLatest = distanceFromBottom > 300;

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

  function onRetry(m: Message) {
    // An attachment that never made it past the upload leg still points at
    // a blob: url the server has never seen — re-running upload+send (with
    // the File kept in MessageInput's module-level map) is the only retry
    // that can succeed. Falls back to a plain re-send if the File is gone
    // (e.g. after a page reload); that will fail again on the stale blob
    // url, settling back to failed.
    if (m.media?.url?.startsWith('blob:') && retryUpload(m.id)) {
      return;
    }
    updateMessage(m.channelId, m.id, { status: 'pending' });
    void commClient
      .send(m.channelId, m.content, { id: m.id, media: m.media })
      .then((r) => updateMessage(m.channelId, m.id, r.ok ? { ...r.message, status: 'sent' } : { status: 'failed' }));
  }

  const typingNames = Object.entries(typingMap)
    .filter(([userId]) => userId !== selfId)
    .map(([, entry]) => entry.name || 'Someone');

  return (
    <div className="comm-surface flex-1 min-h-0 flex flex-col">
      {isStale && (
        <div className="bg-console-surface border-b border-console-warn text-console-warn px-3 py-1 text-xs font-commmono">
          [OFFLINE] Couldn't refresh messages
        </div>
      )}
      <div className="relative flex-1 min-h-0 flex flex-col">
        <div
          ref={scrollRef}
          onScroll={handleScroll}
          className="flex-1 overflow-y-auto px-4 py-3 bg-console-bg"
        >
          {messages.length === 0 ? (
            <div className="text-console-text-muted text-sm font-commsans">No messages yet.</div>
          ) : (
            <ul className="flex flex-col gap-2">
              {groupMessages(messages).map(({ message: m, firstOfGroup }, index) => {
                const mine = selfId === m.senderId;
                const readers = readByMessage[m.id];
                const seenByOthers = readers
                  ? Array.from(readers).filter((id) => id !== selfId).length
                  : 0;
                return (
                  <Fragment key={m.id}>
                    {index === dividerIndex && (
                      <li className="flex items-center gap-2 my-1">
                        <span className="flex-1 border-t border-sn-attention" />
                        <span className="font-data text-[10px] uppercase text-sn-attention bg-sn-bg-base px-2">
                          NEW
                        </span>
                        <span className="flex-1 border-t border-sn-attention" />
                      </li>
                    )}
                    <MessageRow
                      message={m}
                      firstOfGroup={firstOfGroup}
                      mine={mine}
                      seenByOthers={seenByOthers}
                      onDelete={(messageId) => setConfirmingId(messageId)}
                      onRetry={onRetry}
                    />
                  </Fragment>
                );
              })}
            </ul>
          )}
        </div>
        {showJumpToLatest && (
          <button
            type="button"
            onClick={jumpToLatest}
            className="absolute bottom-3 left-1/2 -translate-x-1/2 font-data text-xs bg-sn-accent text-sn-ink-on-accent rounded-full px-3 py-1 shadow-sn-sm"
          >
            ↓ latest
          </button>
        )}
      </div>
      {typingNames.length > 0 && (
        <div className="border-t border-console-border bg-console-surface px-4 py-1 text-xs text-console-text-muted">
          {typingLabel(typingNames)}
        </div>
      )}
      <ConfirmDialog
        open={confirmingId !== null}
        title="Delete message?"
        body="It disappears for everyone in the channel."
        confirmLabel="Delete"
        onConfirm={() => {
          const id = confirmingId;
          setConfirmingId(null);
          if (id) void doDelete(id);
        }}
        onCancel={() => setConfirmingId(null)}
      />
    </div>
  );
}
