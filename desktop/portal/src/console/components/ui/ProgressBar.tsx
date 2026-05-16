// desktop/portal/src/console/components/ui/ProgressBar.tsx
// Ported from dashboard module. Thin bar with a single accent fill.

interface Props {
  /** 0–100. */
  pct: number;
  color: string;
  /** Bar height in pixels (default 3). */
  height?: number;
}

export function ProgressBar({ pct, color, height = 3 }: Props) {
  const clamped = Math.max(0, Math.min(100, pct));
  return (
    <div
      role="progressbar"
      aria-valuenow={clamped}
      aria-valuemin={0}
      aria-valuemax={100}
      style={{
        width: '100%',
        height,
        background: 'var(--color-border)',
        borderRadius: 2,
        overflow: 'hidden',
        position: 'relative',
      }}
    >
      <div
        style={{
          height: '100%',
          width: `${clamped}%`,
          background: `linear-gradient(90deg, ${color} 0%, ${color}cc 100%)`,
          borderRadius: 2,
          minWidth: clamped > 0 ? 4 : 0,
          transition: 'width 0.5s cubic-bezier(0.4, 0, 0.2, 1)',
        }}
      />
    </div>
  );
}
