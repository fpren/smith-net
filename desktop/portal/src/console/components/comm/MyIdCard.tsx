// desktop/portal/src/console/components/comm/MyIdCard.tsx
// The user's own SmithNet id: photo/initials avatar + formatted id + copy /
// share / QR. Shared between the comm DialRail and Settings.

import { useEffect, useState } from 'react';
import QRCode from 'qrcode';
import { Avatar } from '../ui/Avatar';
import { Pill } from '../ui/Pill';
import { accentForId } from '../../lib/utils';
import { useMyProfile } from '../../hooks/useMyProfile';
import { useToastStore } from '../../stores/toastStore';
import { formatPublicId } from './commHelpers';

export function MyIdCard() {
  const me = useMyProfile();
  const pushToast = useToastStore((s) => s.push);
  const [qr, setQr] = useState<string | null>(null);
  const [showQr, setShowQr] = useState(false);

  const publicId = me?.publicId ?? null;
  const pretty = formatPublicId(publicId);

  // Encode as a smithnet: URI so a scanner can tell it apart from arbitrary text.
  useEffect(() => {
    if (!publicId) return;
    QRCode.toDataURL(`smithnet:${publicId}`, { margin: 1, width: 220 })
      .then(setQr)
      .catch(() => setQr(null));
  }, [publicId]);

  async function copy() {
    if (!publicId) return;
    try {
      await navigator.clipboard.writeText(pretty);
      pushToast({ message: 'Copied your id', tone: 'info', duration: 2000 });
    } catch {
      pushToast({ message: 'Copy failed', tone: 'error', duration: 2000 });
    }
  }

  async function share() {
    if (!publicId) return;
    const text = `Reach me on SmithNet: ${pretty}`;
    const nav = navigator as Navigator & { share?: (d: { text: string }) => Promise<void> };
    if (nav.share) {
      try { await nav.share({ text }); return; } catch { /* fall through to copy */ }
    }
    try {
      await navigator.clipboard.writeText(text);
      pushToast({ message: 'Share text copied', tone: 'info', duration: 2000 });
    } catch {
      pushToast({ message: 'Share unavailable', tone: 'error', duration: 2000 });
    }
  }

  return (
    <div className="border-t border-console-border pt-3">
      <div className="text-[10px] tracking-[0.15em] text-console-text-dim font-commmono">
        YOUR SMITHNET ID
      </div>
      <div className="flex items-center gap-2 mt-1.5">
        <Avatar
          name={me?.displayName || ''}
          color={accentForId(me?.id || 'me')}
          size={28}
          photoUrl={me?.avatarUrl}
        />
        <span className="font-commmono text-sm font-semibold tracking-[0.12em] text-console-text">
          {pretty}
        </span>
      </div>
      <div className="flex gap-1.5 mt-2">
        <Pill onClick={copy} disabled={!publicId} className="flex-1 justify-center">[copy]</Pill>
        <Pill onClick={share} disabled={!publicId} className="flex-1 justify-center">[share]</Pill>
        <Pill active={showQr} onClick={() => setShowQr((v) => !v)} disabled={!qr} className="flex-1 justify-center">[qr]</Pill>
      </div>
      {showQr && qr && (
        <div className="mt-2 flex flex-col items-center gap-1 rounded-xl bg-console-surface border border-console-border p-3">
          <img src={qr} alt={`QR for ${pretty}`} width={160} height={160} className="rounded" />
          <span className="font-commmono text-[10px] text-console-text-dim">scan to message me</span>
        </div>
      )}
    </div>
  );
}
