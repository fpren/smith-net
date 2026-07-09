// desktop/portal/src/console/components/ui/SectionHeader.tsx
// Ported from dashboard module. Uppercase JetBrains Mono label with optional
// right-side slot. Use for card/section titles to match Altara aesthetic.

import type { ReactNode } from 'react';

interface Props {
  label: string;
  right?: ReactNode;
}

export function SectionHeader({ label, right }: Props) {
  return (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        padding: '10px 14px 8px',
        background: 'var(--sn-bg-panel)',
        borderBottom: '0.5px solid color-mix(in srgb, var(--sn-ink) 6%, transparent)',
        flexShrink: 0,
      }}
    >
      <span
        style={{
          fontSize: 11,
          fontFamily: 'var(--font-mono)',
          fontWeight: 500,
          textTransform: 'uppercase',
          letterSpacing: '0.12em',
          color: 'var(--sn-ink-muted)',
        }}
      >
        {label}
      </span>
      {right}
    </div>
  );
}
