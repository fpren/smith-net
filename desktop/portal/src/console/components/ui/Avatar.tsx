// desktop/portal/src/console/components/ui/Avatar.tsx
// Ported + adapted from dashboard module. Caller provides name + a stable
// color (use accentForId() in lib/utils.ts for color generation).

import { darkenHex, initials } from '../../lib/utils';

interface Props {
  name: string;
  color: string;
  size?: number;
  /** Fallback character set if name is empty. */
  fallback?: string;
  /** Optional uploaded photo. When set, renders the image; falls back to
   *  initials if it's empty or fails to load. */
  photoUrl?: string | null;
  /** Optional presence ring color (e.g. online sage). */
  statusColor?: string | null;
}

export function Avatar({ name, color, size = 22, fallback = '?', photoUrl, statusColor }: Props) {
  const text = name ? initials(name) : fallback;
  const dark = darkenHex(color);
  // Circular for photos (Aircall-style); rounded-square for initials (console).
  const radius = photoUrl ? Math.round(size / 2) : Math.round(size * 0.28);

  if (photoUrl) {
    return (
      <div
        aria-label={name || fallback}
        style={{
          width: size,
          height: size,
          borderRadius: radius,
          flexShrink: 0,
          position: 'relative',
          boxShadow: statusColor
            ? `0 0 0 2px var(--console-surface, #FAFAF8), 0 0 0 3.5px ${statusColor}`
            : '0 1px 3px rgba(0,0,0,.18)',
        }}
      >
        <img
          src={photoUrl}
          alt={name || fallback}
          style={{ width: '100%', height: '100%', borderRadius: radius, objectFit: 'cover', display: 'block' }}
          onError={(e) => { (e.currentTarget as HTMLImageElement).style.display = 'none'; }}
        />
      </div>
    );
  }

  return (
    <div
      aria-label={name || fallback}
      style={{
        width: size,
        height: size,
        borderRadius: radius,
        background: `linear-gradient(145deg, ${color} 0%, ${dark} 100%)`,
        boxShadow: 'inset 0 1px 0 rgba(255,255,255,.18), inset 0 -1px 0 rgba(0,0,0,.18), 0 1px 3px rgba(0,0,0,.18)',
        fontSize: Math.round(size * 0.34),
        fontFamily: 'var(--font-mono)',
        fontWeight: 600,
        color: 'rgba(255,255,255,.95)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        flexShrink: 0,
        userSelect: 'none',
        letterSpacing: '-0.01em',
      }}
    >
      {text}
    </div>
  );
}
