// desktop/portal/src/console/components/comm/MessageInput.tsx
import { useState, useRef, useEffect, KeyboardEvent } from 'react';
import { commClient } from '../../api/commClient';
import { useCommStore } from '../../stores/commStore';
import { useAuthStore } from '../../auth/authStore';
import { wsClient } from '../../../websocket';
import type { Message } from '../../../types';

interface Props {
  channelId: string;
}

const TYPING_IDLE_MS = 3_000;

// Optimistic send: appends a `pending` message to the store immediately,
// fires the network request, then settles the same id to `sent` (merging the
// server's canonical fields) or `failed`. Exported standalone (not a hook) so
// Task 4's retry flow can re-invoke it with the same tempId without needing a
// mounted MessageInput.
export async function sendOptimistic(
  channelId: string,
  content: string,
  selfId: string,
  selfName: string
): Promise<void> {
  const trimmed = content.trim();
  if (!trimmed) return;
  const tempId = crypto.randomUUID();
  const optimistic: Message = {
    id: tempId,
    channelId,
    senderId: selfId,
    senderName: selfName,
    content: trimmed,
    timestamp: Date.now(),
    origin: 'online',
    status: 'pending',
  };
  useCommStore.getState().appendMessage(optimistic);
  const result = await commClient.send(channelId, trimmed, { id: tempId }).catch(() => ({ ok: false as const }));
  if (result.ok) {
    useCommStore.getState().updateMessage(channelId, tempId, { ...result.message, status: 'sent' });
  } else {
    useCommStore.getState().updateMessage(channelId, tempId, { status: 'failed' });
  }
}

export function MessageInput({ channelId }: Props) {
  const [text, setText] = useState('');
  const [sending, setSending] = useState(false);
  const selfId = useAuthStore((s) => s.user?.id);
  const selfName = useAuthStore((s) => s.user?.displayName);

  // Whether we have an active "I am typing" claim out. Cleared on idle, send,
  // blur, channel change, and unmount so peers don't see a stale indicator.
  const isTypingRef = useRef(false);
  const idleTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  function flagTyping(active: boolean) {
    if (active && !isTypingRef.current) {
      isTypingRef.current = true;
      wsClient.sendTyping(channelId, true);
    } else if (!active && isTypingRef.current) {
      isTypingRef.current = false;
      wsClient.sendTyping(channelId, false);
    }
  }

  function onChange(value: string) {
    setText(value);
    if (value.length > 0) {
      flagTyping(true);
      if (idleTimerRef.current) clearTimeout(idleTimerRef.current);
      idleTimerRef.current = setTimeout(() => flagTyping(false), TYPING_IDLE_MS);
    } else {
      flagTyping(false);
      if (idleTimerRef.current) {
        clearTimeout(idleTimerRef.current);
        idleTimerRef.current = null;
      }
    }
  }

  async function doSend() {
    const trimmed = text.trim();
    if (!trimmed || sending || !selfId) return;
    setSending(true);
    flagTyping(false);
    if (idleTimerRef.current) {
      clearTimeout(idleTimerRef.current);
      idleTimerRef.current = null;
    }
    setText('');
    try {
      await sendOptimistic(channelId, trimmed, selfId, selfName ?? '');
    } finally {
      setSending(false);
    }
  }

  function onKeyDown(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      doSend();
    }
  }

  // Drop the typing claim when the channel changes or the input unmounts so a
  // peer doesn't see "X is typing…" after the user navigated away.
  useEffect(() => {
    return () => {
      if (idleTimerRef.current) clearTimeout(idleTimerRef.current);
      flagTyping(false);
    };
    // channelId in deps ensures the cleanup-and-reset runs on switches.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [channelId]);

  return (
    <div className="comm-surface border-t border-console-border bg-console-surface px-3 py-2.5 flex items-center gap-2">
      {/* Attach affordance (wired to existing media path is a follow-up). */}
      <button
        type="button"
        aria-label="Attach"
        className="w-8 h-8 flex-shrink-0 rounded-full border border-console-border text-console-text-muted font-commmono grid place-items-center hover:border-console-accent hover:text-console-accent transition-colors"
      >
        [+]
      </button>
      <input
        type="text"
        value={text}
        onChange={(e) => onChange(e.target.value)}
        onKeyDown={onKeyDown}
        onBlur={() => flagTyping(false)}
        placeholder="Type a message…"
        disabled={sending}
        className="flex-1 rounded-full bg-console-bg border border-console-border px-4 py-2 text-sm text-console-text placeholder-console-text-dim font-commsans focus:outline-none focus:border-console-accent"
      />
      <button
        type="button"
        onClick={doSend}
        disabled={sending || text.trim().length === 0}
        className="flex-shrink-0 rounded-full bg-console-accent text-white font-commsans font-semibold text-sm px-5 py-2 disabled:opacity-40 disabled:cursor-not-allowed hover:opacity-90 transition-opacity"
      >
        {sending ? '…' : 'send'}
      </button>
    </div>
  );
}
