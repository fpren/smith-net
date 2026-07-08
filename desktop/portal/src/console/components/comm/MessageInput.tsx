// desktop/portal/src/console/components/comm/MessageInput.tsx
import { useState, useRef, useEffect, ChangeEvent, KeyboardEvent } from 'react';
import { commClient } from '../../api/commClient';
import { useCommStore } from '../../stores/commStore';
import { useToastStore } from '../../stores/toastStore';
import { useAuthStore } from '../../auth/authStore';
import { wsClient } from '../../../websocket';
import type { Message, MediaAttachment, MediaType } from '../../../types';

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
    useToastStore.getState().push({
      message: 'Send failed',
      tone: 'error',
      duration: 3000,
    });
  }
}

// tempId -> the File + addressing the retry step needs, so a page-lifetime
// retry can re-run from the upload step (not just re-inject). Module-level
// like sendOptimistic above, not component state, so MessageList's retry
// path can call retryUpload() without a mounted MessageInput.
const pendingUploads = new Map<string, { file: File; channelId: string; selfId: string }>();

function mediaTypeFromMime(mime: string): MediaType {
  if (mime.startsWith('image/')) return 'image';
  if (mime.startsWith('audio/')) return 'voice';
  if (mime.startsWith('video/')) return 'video';
  return 'file';
}

// The upload leg + send leg + settle, shared by the initial pick and by
// retryUpload. Assumes the optimistic message (status pending) already
// exists in the store at `tempId`.
async function runMediaUpload(file: File, tempId: string, channelId: string, selfId: string): Promise<void> {
  const uploadResult = await commClient
    .uploadMedia(file, tempId, channelId, selfId)
    .catch(() => ({ ok: false as const }));

  if (!uploadResult.ok) {
    useCommStore.getState().updateMessage(channelId, tempId, { status: 'failed' });
    useToastStore.getState().push({
      message: 'Upload failed',
      tone: 'error',
      duration: 3000,
    });
    return;
  }

  const media: MediaAttachment = {
    type: mediaTypeFromMime(file.type),
    url: uploadResult.url,
    filename: uploadResult.filename ?? file.name,
    mimeType: uploadResult.mimeType ?? file.type,
    size: uploadResult.size,
  };

  const sendResult = await commClient
    .send(channelId, '', { id: tempId, media })
    .catch(() => ({ ok: false as const }));

  if (sendResult.ok) {
    pendingUploads.delete(tempId);
    // The optimistic preview used a blob: url for instant display — release
    // it now that the server url has replaced it. jsdom doesn't implement
    // revokeObjectURL at all, hence the guard.
    const current = useCommStore.getState().messagesByChannel[channelId]?.find((m) => m.id === tempId);
    if (current?.media?.url?.startsWith('blob:')) {
      URL.revokeObjectURL?.(current.media.url);
    }
    useCommStore.getState().updateMessage(channelId, tempId, { ...sendResult.message, status: 'sent' });
  } else {
    useCommStore.getState().updateMessage(channelId, tempId, { status: 'failed' });
    useToastStore.getState().push({
      message: 'Send failed',
      tone: 'error',
      duration: 3000,
    });
  }
}

// Picking a file: append an optimistic pending message with an instant
// object-url preview, then run the upload+send flow. Exported standalone
// (not a hook) for the same reason as sendOptimistic.
export async function pickAndUploadMedia(
  file: File,
  channelId: string,
  selfId: string,
  selfName: string
): Promise<void> {
  const tempId = crypto.randomUUID();
  const optimistic: Message = {
    id: tempId,
    channelId,
    senderId: selfId,
    senderName: selfName,
    content: '',
    timestamp: Date.now(),
    origin: 'online',
    status: 'pending',
    media: {
      type: mediaTypeFromMime(file.type),
      url: URL.createObjectURL(file),
      filename: file.name,
    },
  };
  useCommStore.getState().appendMessage(optimistic);
  pendingUploads.set(tempId, { file, channelId, selfId });
  await runMediaUpload(file, tempId, channelId, selfId);
}

// Called from MessageList's retry path when a failed message carries a
// blob-url attachment. Returns true if a kept File was found and the retry
// is running (re-uploads from scratch); false if the File is gone (e.g.
// after a page reload) so the caller should fall back to a plain re-send —
// which will fail again on a blob: url the server has never seen, settling
// back to failed. Acceptable: the user still has "retry" as a visible
// affordance, it just won't succeed until they re-pick the file.
export function retryUpload(tempId: string): boolean {
  const entry = pendingUploads.get(tempId);
  if (!entry) return false;
  useCommStore.getState().updateMessage(entry.channelId, tempId, { status: 'pending' });
  void runMediaUpload(entry.file, tempId, entry.channelId, entry.selfId);
  return true;
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
  const fileInputRef = useRef<HTMLInputElement>(null);

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

  function onPickFile(e: ChangeEvent<HTMLInputElement>) {
    const file = e.target.files?.[0];
    // Reset so picking the same file again still fires a change event.
    e.target.value = '';
    if (!file || !selfId) return;
    void pickAndUploadMedia(file, channelId, selfId, selfName ?? '');
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
    <div className="comm-surface border-t border-sn-line bg-sn-bg-panel px-3 py-2.5 flex items-center gap-2">
      <input
        ref={fileInputRef}
        type="file"
        onChange={onPickFile}
        className="hidden"
        aria-hidden="true"
        tabIndex={-1}
      />
      <button
        type="button"
        aria-label="Attach"
        onClick={() => fileInputRef.current?.click()}
        className="w-8 h-8 flex-shrink-0 rounded-full border border-sn-line text-sn-ink-muted font-commmono grid place-items-center hover:border-sn-accent hover:text-sn-accent transition-colors"
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
        className="flex-1 rounded-full bg-sn-bg-base border border-sn-line px-4 py-2 text-sm text-sn-ink placeholder-sn-ink-muted font-commsans focus:outline-none focus:border-sn-accent"
      />
      <button
        type="button"
        onClick={doSend}
        disabled={sending || text.trim().length === 0}
        className="flex-shrink-0 rounded-full bg-sn-accent text-sn-ink-on-accent font-commsans font-semibold text-sm px-5 py-2 disabled:opacity-40 disabled:cursor-not-allowed hover:opacity-90 transition-opacity"
      >
        {sending ? '…' : 'send'}
      </button>
    </div>
  );
}
