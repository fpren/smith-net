// desktop/portal/src/console/components/ui/Chip.tsx
// Ported from dashboard module. Like Badge but caller supplies an arbitrary
// accent color (Badge has tone presets). Use Chip for crew/role/status
// signals; use Badge for the existing tone-bound use cases.

import { cn } from '../../lib/utils';

interface Props {
  label: string;
  color: string;
  /** Extra-small variant — tighter padding + smaller text. */
  xs?: boolean;
  className?: string;
}

export function Chip({ label, color, xs, className }: Props) {
  return (
    <span
      className={cn('inline-flex items-center font-mono font-semibold whitespace-nowrap', className)}
      style={{
        padding: xs ? '1px 7px' : '2px 9px',
        borderRadius: 4,
        fontSize: xs ? 8 : 10,
        background: color + '16',
        color,
        border: `1px solid ${color}28`,
        letterSpacing: '0.02em',
      }}
    >
      {label}
    </span>
  );
}
