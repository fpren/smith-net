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
}

export function Avatar({ name, color, size = 22, fallback = '?' }: Props) {
  const text = name ? initials(name) : fallback;
  const dark = darkenHex(color);
  const radius = Math.round(size * 0.28);

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
