// desktop/portal/src/console/components/comm/MessageInput.tsx
import { useState, useRef, useEffect, KeyboardEvent } from 'react';
import { commClient } from '../../api/commClient';
import { useCommStore } from '../../stores/commStore';
import { useToastStore } from '../../stores/toastStore';
import { wsClient } from '../../../websocket';

interface Props {
  channelId: string;
}

const TYPING_IDLE_MS = 3_000;

export function MessageInput({ channelId }: Props) {
  const [text, setText] = useState('');
  const [sending, setSending] = useState(false);
  const appendMessage = useCommStore((s) => s.appendMessage);
  const pushToast = useToastStore((s) => s.push);

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
    if (!trimmed || sending) return;
    setSending(true);
    flagTyping(false);
    if (idleTimerRef.current) {
      clearTimeout(idleTimerRef.current);
      idleTimerRef.current = null;
    }
    const result = await commClient.send(channelId, trimmed);
    setSending(false);
    if (result.ok) {
      appendMessage(result.message);
      setText('');
    } else {
      pushToast({
        message: result.error || 'Send failed',
        tone: 'error',
        duration: 3000,
      });
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
    <div className="border-t border-console-border bg-console-surface px-3 py-2 flex items-center gap-2 font-mono">
      <input
        type="text"
        value={text}
        onChange={(e) => onChange(e.target.value)}
        onKeyDown={onKeyDown}
        onBlur={() => flagTyping(false)}
        placeholder="Type a message…"
        disabled={sending}
        className="flex-1 bg-transparent border border-console-border px-2 py-1 text-sm text-console-text placeholder-console-text-muted focus:outline-none focus:border-console-accent"
      />
      <button
        type="button"
        onClick={doSend}
        disabled={sending || text.trim().length === 0}
        className="px-3 py-1 text-xs uppercase tracking-wide text-console-accent border border-console-accent disabled:opacity-40 disabled:cursor-not-allowed hover:bg-console-accent hover:text-console-bg transition-colors"
      >
        {sending ? '[Sending…]' : '[Send]'}
      </button>
    </div>
  );
}
