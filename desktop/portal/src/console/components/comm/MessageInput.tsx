// desktop/portal/src/console/components/comm/MessageInput.tsx
import { useState, KeyboardEvent } from 'react';
import { commClient } from '../../api/commClient';
import { useCommStore } from '../../stores/commStore';
import { useToastStore } from '../../stores/toastStore';

interface Props {
  channelId: string;
}

export function MessageInput({ channelId }: Props) {
  const [text, setText] = useState('');
  const [sending, setSending] = useState(false);
  const appendMessage = useCommStore((s) => s.appendMessage);
  const pushToast = useToastStore((s) => s.push);

  async function doSend() {
    const trimmed = text.trim();
    if (!trimmed || sending) return;
    setSending(true);
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

  return (
    <div className="border-t border-console-border bg-console-surface px-3 py-2 flex items-center gap-2 font-mono">
      <input
        type="text"
        value={text}
        onChange={(e) => setText(e.target.value)}
        onKeyDown={onKeyDown}
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
